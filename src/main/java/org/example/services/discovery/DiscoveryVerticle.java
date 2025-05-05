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
import java.util.Objects;
import java.util.stream.Collectors;

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
     * 1. PING check (concurrent)
     * 2. PORT check (concurrent)
     * 3. SSH reachability check via external plugin (all reachable devices together, in executeBlocking)
     * Updates the database with the final reachability status.
     *
     * @param devices         a JsonArray of device details including IP, port, and credentials.
     * @param defaultResults  a JsonArray containing initial reachability status (usually false).
     */
    private void startDiscoveryPipeline(JsonArray devices, JsonArray defaultResults)
    {
        // Process PING and PORT checks concurrently for each device
        var deviceFutures = devices.stream()
                .map(obj -> (JsonObject) obj)
                .map(device -> vertx.executeBlocking(
                        () ->
                        {
                            try
                            {
                                // Perform PING check
                                var pingResult = ConnectivityUtil.filterReachableDevices(new JsonArray().add(device), CheckType.PING);

                                if (pingResult.isEmpty())
                                {
                                    return null;
                                }

                                // Perform PORT check
                                var portResult = ConnectivityUtil.filterReachableDevices(pingResult, CheckType.PORT);

                                if (portResult.isEmpty())
                                {
                                    return null;
                                }

                                // Return the device that passed both checks
                                return portResult.getJsonObject(0);
                            }
                            catch (Exception e)
                            {
                                LOGGER.error("Error processing device ID " + device.getInteger(ID) + ": " + e.getMessage());

                                return null;
                            }
                        },
                        false // Ordered execution not required
                ))
                .collect(Collectors.toList());

        // Wait for all PING and PORT checks to complete
        Future.all(deviceFutures)
                .compose(composite ->
                {
                    // Collect devices that passed PING and PORT checks
                    var reachableDevices = new JsonArray();

                    for (var i = 0; i < composite.size(); i++)
                    {
                        var result = composite.resultAt(i);

                        if (result instanceof JsonObject)
                        {
                            reachableDevices.add(result);
                        }
                    }

                    if (reachableDevices.isEmpty())
                    {
                        LOGGER.info("No devices passed PING and PORT checks.");

                        return Future.succeededFuture(new JsonArray());
                    }

                    // Perform SSH check for all reachable devices in executeBlocking
                    return vertx.executeBlocking(
                            () -> {
                                try
                                {
                                    return PluginOperationsUtil.runSSHReachability(reachableDevices);
                                }
                                catch (Exception e)
                                {
                                    LOGGER.error("SSH reachability check failed: " + e.getMessage());

                                    return new JsonArray();
                                }
                            },
                            false
                    );
                })
                .onSuccess(sshResults -> {
                    try
                    {
                        // Prepare updated results based on SSH outcomes
                        var updatedResults = new JsonArray();

                        for (var i = 0; i < defaultResults.size(); i++)
                        {
                            var defaultResult = defaultResults.getJsonObject(i);

                            var updatedResult = new JsonObject()
                                    .put(ID, defaultResult.getInteger(ID))
                                    .put("reachable", false);

                            // Check if this device passed SSH
                            for (var j = 0; j < sshResults.size(); j++)
                            {
                                var sshResult = sshResults.getJsonObject(j);

                                if (Objects.equals(sshResult.getInteger(ID), defaultResult.getInteger(ID)))
                                {
                                    updatedResult.put("reachable", sshResult.getBoolean("reachable"));

                                    break;
                                }
                            }

                            updatedResults.add(updatedResult);
                        }

                        LOGGER.info("Discovery completed. Updating status for devices.");

                        updateDiscoveryStatus(updatedResults);
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Error while processing discovery results: " + e.getMessage());

                        updateDiscoveryStatus(defaultResults);
                    }
                })
                .onFailure(cause ->
                {
                    LOGGER.error("Discovery pipeline failed: " + cause.getMessage());

                    updateDiscoveryStatus(defaultResults);
                });
    }

    /**
     * Updates the discovery_profile status in the database based on SSH reachability results.
     * Sets the status to ACTIVE or INACTIVE accordingly.
     *
     * @param results  a JsonArray containing the device ID and its reachability result.
     */
    private void updateDiscoveryStatus(JsonArray results)
    {
        try
        {
            if (results.isEmpty())
            {
                LOGGER.warn("No devices to update discovery status.");

                return;
            }

            // Update status for each device concurrently
            var updateFutures = results.stream()
                    .map(obj -> (JsonObject) obj)
                    .map(result ->
                    {
                        var id = result.getInteger(ID);

                        return executeQuery(UPDATE_DISCOVERY_STATUS, List.of(result.getBoolean("reachable") ? ACTIVE : INACTIVE, id))
                                .onSuccess(res -> LOGGER.info("Discovery status updated for device ID: " + id))
                                .onFailure(err -> LOGGER.error("Failed to update status for device ID " + id + ": " + err.getMessage()));
                    })
                    .collect(Collectors.toList());

            // Wait for all updates to complete
            Future.all(updateFutures)
                    .onFailure(err -> LOGGER.error("Failed to update some discovery statuses: " + err.getMessage()));
        }
        catch (Exception e)
        {
            LOGGER.error("Error updating discovery status: " + e.getMessage());
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
                    .onSuccess(result -> {
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
                    .onFailure(cause -> LOGGER.error("Failed to fetch credentials for device ID " + id + ": " + cause.getMessage()));
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