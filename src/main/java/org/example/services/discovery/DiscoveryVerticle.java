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
import org.example.utils.ConnectivityUtil;
import org.example.utils.ConnectivityUtil.CheckType;

import java.util.List;

import static org.example.constants.AppConstants.DiscoveryQuery.*;
import static org.example.constants.AppConstants.DiscoveryField.*;
import static org.example.constants.AppConstants.CredentialField.USERNAME;
import static org.example.constants.AppConstants.CredentialField.PASSWORD;

/**
 * Verticle responsible for running discovery operations.
 * <p>
 * Handles device reachability checks (ping, port, SSH) and updates
 * discovery status in the database. Communicates over the event bus
 * at {@code discovery.service}.
 */
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

    /**
     * Handles incoming discovery-related requests from the Event Bus.
     * Supports actions like "startDiscovery" and "fetchDeviceDetailsAndRunDiscovery".
     *
     * @param message the message containing the action and payload.
     */
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
                            .put("reachable", false)));

                    break;

                case "fetchDeviceDetailsAndRunDiscovery":

                    fetchDeviceDetailsAndRunDiscovery(request.getInteger("discoveryId"),
                            request.getString("ip"),
                            request.getInteger("port"),
                            request.getInteger("credentialProfileId"));

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

    /**
     * Executes the discovery pipeline for a given list of devices:
     * 1. PING check
     * 2. PORT check
     * 3. SSH reachability check via external plugin
     * Updates the database with the final reachability status.
     *
     * @param devices         a JsonArray of device details including IP, port, and credentials.
     * @param defaultResults  a JsonArray containing initial reachability status (usually false).
     */
    private void startDiscoveryPipeline(JsonArray devices, JsonArray defaultResults)
    {
        try
        {
            vertx.executeBlocking(() ->
                    {
                        try
                        {
                            var pingResults = ConnectivityUtil.filterReachableDevices(devices, CheckType.PING);

                            if (pingResults.isEmpty())
                            {
                                return new JsonObject().put("reachable", false);
                            }

                            var portResults = ConnectivityUtil.filterReachableDevices(pingResults, CheckType.PORT);

                            if (portResults.isEmpty())
                            {
                                return new JsonObject().put("reachable", false);
                            }

                            var sshResults = PluginOperationsUtil.runSSHReachability(portResults);

                            if (sshResults.isEmpty())
                            {
                                return new JsonObject().put("reachable", false);
                            }

                            var sshReachable = sshResults.getJsonObject(0).getBoolean("reachable");

                            return new JsonObject()
                                    .put("reachable", sshReachable)
                                    .put("sshResults", sshResults);
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("SSH reachability check failed: " + e.getMessage());

                            return new JsonObject().put("reachable", false);
                        }
                    })
                    .onFailure(cause ->
                    {
                        LOGGER.error("Connectivity checks failed: " + cause.getMessage());

                        updateDiscoveryStatus(defaultResults);
                    })
                    .onSuccess(result ->
                    {
                        try
                        {
                            if (!result.getBoolean("reachable"))
                            {
                                LOGGER.info("Device not reachable. Marking as inactive.");

                                updateDiscoveryStatus(defaultResults);

                                return;
                            }

                            var deviceResult = defaultResults.getJsonObject(0);

                            deviceResult.put("reachable", result.getJsonArray("sshResults").getJsonObject(0).getBoolean("reachable"));

                            LOGGER.info("Discovery completed. Updating status for device ID: " + deviceResult.getInteger(ID));

                            updateDiscoveryStatus(defaultResults);
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Error while processing discovery results: " + e.getMessage());
                        }

                    });
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to start run discovery: " + e.getMessage());

            updateDiscoveryStatus(defaultResults);
        }
    }

    /**
     * Updates the discovery_profile status in the database based on SSH reachability results.
     * Sets the status to ACTIVE or INACTIVE accordingly.
     *
     * @param defaultResults  a JsonArray containing the device ID and its reachability result.
     */
    private void updateDiscoveryStatus(JsonArray defaultResults)
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
                            LOGGER.info("Discovery status updated for device ID: " + id))
                    .onFailure(err ->
                            LOGGER.error("Failed to update status for device ID " + id + ": " + err.getMessage()));
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage());
        }

    }

    /**
     * Fetches credential details for a specific credential profile ID,
     * constructs a full device JSON object (with decrypted password),
     * and initiates the discovery pipeline for that device.
     *
     * @param id                   the ID of the discovery profile/device.
     * @param ip                   the IP address of the device.
     * @param port                 the port number to check connectivity.
     * @param credentialProfileId  the associated credential_profile ID.
     */
    private void fetchDeviceDetailsAndRunDiscovery(int id, String ip, int port, int credentialProfileId)
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
                                            .put("reachable", false)));
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

    /**
     * Sends a SQL query along with optional parameters to the DatabaseVerticle
     * using a Vert.x service proxy and returns a Future of the result.
     *
     * @param query   the SQL query to execute.
     * @param params  the list of query parameters (can be null or empty).
     * @return        a Future containing the query result as a JsonObject.
     */
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