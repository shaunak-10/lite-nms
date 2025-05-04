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
        var request = message.body();

        var action = request.getString("action");

        switch (action)
        {
            case "startDiscovery":

                var device = request.getJsonObject("device");

                if (device == null)
                {
                    message.fail(400, "Missing device data");

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
                message.fail(400, "Unknown action: " + action);
        }
    }

    private void startDiscoveryPipeline(JsonArray devices, JsonArray defaultResults, Message<JsonObject> message)
    {
        PingUtil.filterReachableDevicesAsync(vertx, devices)
                .onFailure(cause -> message.fail(500, "Ping check failed: " + cause.getMessage()))
                .onSuccess(pingedDevices ->
                {
                    if (pingedDevices.isEmpty())
                    {
                        LOGGER.info("Device not reachable via ping. Marking as inactive.");

                        updateDiscoveryStatus(defaultResults, message);

                        return;
                    }

                    PortUtil.filterReachableDevicesAsync(vertx, pingedDevices)
                            .onFailure(cause -> message.fail(500, "Port check failed: " + cause.getMessage()))
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
                                        {
                                            LOGGER.error("SSH plugin call failed: " + err.getMessage());

                                            message.fail(500, "SSH plugin call failed: " + err.getMessage());
                                        })
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

    private void updateDiscoveryStatus(JsonArray defaultResults, Message<JsonObject> message)
    {
        if (defaultResults.isEmpty())
        {
            LOGGER.warn("No device to update discovery status.");

            message.fail(400, "No device to update discovery status");

            return;
        }

        var result = defaultResults.getJsonObject(0);

        var id = result.getInteger(ID);

        executeQuery(UPDATE_DISCOVERY_STATUS, List.of(result.getBoolean("reachable") ? ACTIVE : INACTIVE, id))
                .onSuccess(res ->
                {
                    LOGGER.info("Discovery status updated for device ID: " + id);

                    message.reply(new JsonObject().put("results", defaultResults));
                })
                .onFailure(err ->
                {
                    LOGGER.error("Failed to update status for device ID " + id + ": " + err.getMessage());

                    message.fail(500, "Status update failed: " + err.getMessage());
                });
    }

    private void fetchDeviceDetailsAndRunDiscovery(int id, String ip, int port, int credentialProfileId, Message<JsonObject> message)
    {
        executeQuery(FETCH_CREDENTIAL_FROM_ID, List.of(credentialProfileId))
                .onSuccess(result ->
                {
                    var rows = result.getJsonArray("rows", new JsonArray());

                    if (rows.isEmpty())
                    {
                        LOGGER.warn("No credentials found for credentialProfileId: " + credentialProfileId);

                        message.fail(404, "No credentials found for the given profile ID");

                        return;
                    }

                    var row = rows.getJsonObject(0);

                    var password = "";

                    try
                    {
                        password = DecryptionUtil.decrypt(row.getString(PASSWORD));
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Failed to decrypt password: " + e.getMessage());

                        message.fail(500, "Failed to decrypt password: " + e.getMessage());

                        return;
                    }

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
                })
                .onFailure(cause ->
                {
                    LOGGER.error("Failed to fetch credentials for device ID " + id + ": " + cause.getMessage());

                    message.fail(500, "Failed to fetch credentials: " + cause.getMessage());
                });
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