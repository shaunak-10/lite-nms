package org.example.services.discovery;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.services.db.DatabaseService;
import org.example.services.db.DatabaseVerticle;
import org.example.utils.PluginOperationsUtil;
import org.example.utils.DecryptionUtil;
import org.example.utils.PingUtil;
import org.example.utils.PortUtil;

import java.util.List;

import static org.example.constants.AppConstants.DiscoveryQuery.*;
import static org.example.constants.AppConstants.DiscoveryField.*;
import static org.example.constants.AppConstants.CredentialField.USERNAME;
import static org.example.constants.AppConstants.CredentialField.PASSWORD;

public class DiscoveryVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryVerticle.class);

    public static final String SERVICE_ADDRESS = "discovery.service";

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            vertx.eventBus().localConsumer(SERVICE_ADDRESS, this::handleDiscoveryRequests);

            startPromise.complete();
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to start Discovery Verticle: " + e.getMessage());

            startPromise.fail(e);
        }
    }

    private void handleDiscoveryRequests(Message<JsonObject> message)
    {
        try
        {
            var request = message.body();

            var action = request.getString("action");

            switch (action)
            {
                case "startDiscovery":

                    var device = request.getJsonObject("device");

                    if (device == null)
                    {
                        LOGGER.error("Device information is missing in the request.");

                        return;
                    }

                    startDiscoveryPipeline(new JsonArray().add(device), new JsonArray().add(new JsonObject()
                            .put(ID, device.getInteger(ID))
                            .put("reachable", false)), message);

                    break;

                case "fetchDeviceDetailsAndRunDiscovery":

                    fetchDeviceDetailsAndRunDiscovery(request.getInteger("discoveryId"),
                            request.getString("ip"),
                            request.getInteger("port"),
                            request.getInteger("credentialProfileId"),
                            message);

                    break;

                default:
                    LOGGER.warn("Unknown action: " + action);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to handle discovery requests: " + e.getMessage());
        }
        
    }

    private void startDiscoveryPipeline(JsonArray devices, JsonArray defaultResults, Message<JsonObject> message)
    {
        try
        {
            PingUtil.filterReachableDevices(vertx, devices)
                    .onFailure(cause -> LOGGER.error("Ping check failed: " + cause.getMessage()))
                    .onSuccess(pingedDevices ->
                    {
                        if (pingedDevices.isEmpty())
                        {
                            LOGGER.info("Device not reachable via ping. Marking as inactive.");

                            updateDiscoveryStatus(defaultResults, message);

                            return;
                        }

                        PortUtil.filterReachableDevices(vertx, pingedDevices)
                                .onFailure(cause -> LOGGER.error("Port check failed: " + cause.getMessage()))
                                .onSuccess(portFilteredDevices ->
                                {
                                    if (portFilteredDevices.isEmpty())
                                    {
                                        LOGGER.info("All devices lost after port check. Marking all as inactive.");

                                        updateDiscoveryStatus(defaultResults, message);

                                        return;
                                    }

                                    PluginOperationsUtil.runSSHReachability(portFilteredDevices)
                                            .onFailure(err ->
                                                    LOGGER.error("SSH plugin call failed: " + err.getMessage()))
                                            .onSuccess(sshResults ->
                                            {
                                                var deviceResult = defaultResults.getJsonObject(0);

                                                deviceResult.put("reachable", sshResults.getJsonObject(0).getBoolean("reachable"));

                                                LOGGER.info("Discovery completed. Updating status for device ID: " + deviceResult.getInteger(ID));

                                                updateDiscoveryStatus(defaultResults, message);
                                            });
                                });
                    });
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to start run discovery: " + e.getMessage());
        }
    }

    private void updateDiscoveryStatus(JsonArray defaultResults, Message<JsonObject> message)
    {
        try
        {
            if (defaultResults.isEmpty())
            {
                LOGGER.warn("No device to update discovery status.");

                return;
            }

            var result = defaultResults.getJsonObject(0);

            var id = result.getInteger(ID);

            executeQuery(UPDATE_DISCOVERY_STATUS, List.of(result.getBoolean("reachable") ? ACTIVE : INACTIVE, id))
                    .onSuccess(res ->
                    {
                        LOGGER.info("Discovery status updated for device ID: " + id);
                    })
                    .onFailure(err ->
                    {
                        LOGGER.error("Failed to update status for device ID " + id + ": " + err.getMessage());
                    });
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage());
        }

    }

    private void fetchDeviceDetailsAndRunDiscovery(int id, String ip, int port, int credentialProfileId, Message<JsonObject> message)
    {
        try
        {
            executeQuery(FETCH_CREDENTIAL_FROM_ID, List.of(credentialProfileId))
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rows = result.getJsonArray("rows", new JsonArray());

                            if (rows.isEmpty())
                            {
                                LOGGER.warn("No credentials found for credentialProfileId: " + credentialProfileId);

                                return;
                            }

                            var row = rows.getJsonObject(0);

                            var password = DecryptionUtil.decrypt(row.getString(PASSWORD));

                            // Construct device JSON object
                            var device = new JsonObject()
                                    .put(ID, id)
                                    .put(IP, ip)
                                    .put(PORT, port)
                                    .put(USERNAME, row.getString(USERNAME))
                                    .put(PASSWORD, password);

                            // Run discovery for the single device
                            startDiscoveryPipeline(new JsonArray().add(device),
                                    new JsonArray().add(new JsonObject()
                                            .put(ID, id)
                                            .put("reachable", false)), message);
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Error while processing result: " + e.getMessage());
                        }
                    })
                    .onFailure(cause ->
                            LOGGER.error("Failed to fetch credentials for device ID " + id + ": " + cause.getMessage()));
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to fetch device details and run discovery: " + e.getMessage());
        }

    }

    Future<JsonObject> executeQuery(String query, List<Object> params)
    {
        var request = new JsonObject()
                .put("query", query);

        if (params != null && !params.isEmpty())
        {
            request.put("params", new JsonArray(params));
        }

        return DatabaseService.createProxy(vertx, DatabaseVerticle.SERVICE_ADDRESS).executeQuery(request);
    }
}