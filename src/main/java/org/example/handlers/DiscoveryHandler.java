package org.example.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.example.plugin.PluginService;
import org.example.plugin.PluginVerticle;
import org.example.utils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.example.constants.AppConstants.DiscoveryQuery.*;
import static org.example.constants.AppConstants.DiscoveryField.*;
import static org.example.constants.AppConstants.CredentialField.USERNAME;
import static org.example.constants.AppConstants.CredentialField.PASSWORD;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;

public class DiscoveryHandler extends AbstractCrudHandler
{
    private static final DiscoveryHandler INSTANCE = new DiscoveryHandler();

    private static final Logger LOGGER = LoggerUtil.getMainLogger();

    private DiscoveryHandler() {}

    public static DiscoveryHandler getInstance()
    {
        return INSTANCE;
    }

    @Override
    public void add(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if (body == null)
        {
            handleMissingData(ctx, LOGGER, INVALID_JSON_BODY);

            return;
        }

        try
        {
            var name = body.getString(NAME);

            var ip = body.getString(IP);

            var port = body.getInteger(PORT, 22);

            var credentialProfileId = body.getInteger(CREDENTIAL_PROFILE_ID);

            if (name == null || ip == null || credentialProfileId == 0)
            {
                handleMissingData(ctx, LOGGER, MISSING_FIELDS);

                return;
            }

            LOGGER.info("Adding new discovery profile: " + body.encode());

            IpResolutionUtil.resolveAndValidateIp(ctx.vertx(), ip)
                    .onSuccess(validIp ->
                    {
                        if (validIp == null)
                        {
                            handleInvalidData(ctx, LOGGER, INVALID_IP);

                            return;
                        }

                        if (isNotValidPort(port))
                        {
                            handleInvalidData(ctx, LOGGER, INVALID_PORT);

                            return;
                        }

                        executeQuery(ADD_DISCOVERY, List.of(name, validIp, port, INACTIVE, credentialProfileId))
                                .onSuccess(result ->
                                {
                                    var rows = result.getJsonArray("rows");

                                    if (rows != null && !rows.isEmpty())
                                    {
                                        var id = rows.getJsonObject(0).getInteger(ID);

                                        LOGGER.info("Discovery profile added with ID: " + id);

                                        // Fetch device details and trigger discovery
                                        fetchDeviceDetailsAndRunDiscovery(ctx, id, validIp, port, credentialProfileId);

                                        // Respond to the client immediately
                                        handleCreated(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS).put(ID, id));
                                    }
                                    else
                                    {
                                        handleSuccess(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS));
                                    }
                                })
                                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_ADD, cause));
                    })
                    .onFailure(err ->
                    {
                        LOGGER.warning("Error during IP resolution: " + err.getMessage());

                        handleInvalidData(ctx, LOGGER, INVALID_IP);
                    });
        }
        catch (Exception e)
        {
            LOGGER.warning("Invalid input in add(): " + e.getMessage());

            handleInvalidData(ctx, LOGGER, INVALID_JSON_BODY);
        }
    }

    private void fetchDeviceDetailsAndRunDiscovery(RoutingContext ctx, int id, String ip, int port, int credentialProfileId)
    {
        // Query to fetch username and password based on credentialProfileId
        String query = "SELECT username, password FROM credential_profile WHERE id = $1";
        executeQuery(query, List.of(credentialProfileId))
                .onSuccess(result ->
                {
                    var rows = result.getJsonArray("rows", new JsonArray());

                    if (rows.isEmpty())
                    {
                        LOGGER.warning("No credentials found for credentialProfileId: " + credentialProfileId);

                        return;
                    }

                    var row = rows.getJsonObject(0);

                    var username = row.getString(USERNAME);

                    var password = "";

                    try
                    {
                        password = DecryptionUtil.decrypt(row.getString(PASSWORD));
                    }
                    catch (Exception e)
                    {
                        LOGGER.warning("Failed to decrypt password: " + e.getMessage());

                        return;
                    }

                    // Construct device JSON object
                    var device = new JsonObject()
                            .put(ID, id)
                            .put(IP, ip)
                            .put(PORT, port)
                            .put(USERNAME, username)
                            .put(PASSWORD, password);

                    var devices = new JsonArray().add(device);

                    var defaultResults = new JsonArray().add(new JsonObject()
                            .put(ID, id)
                            .put("reachable", false));

                    // Run discovery for the single device
                    startDiscoveryPipeline(ctx, devices, defaultResults);
                })
                .onFailure(cause ->
                {
                    LOGGER.warning("Failed to fetch credentials for device ID " + id + ": " + cause.getMessage());
                });
    }

    @Override
    public void list(RoutingContext ctx)
    {
        LOGGER.info("Fetching discovery profile list");

        executeQuery(GET_ALL_DISCOVERY)
                .onSuccess(result ->
                {
                    var rows = result.getJsonArray("rows", new JsonArray());

                    var discoveryList = new JsonArray();

                    for (var i = 0; i < rows.size(); i++)
                    {
                        var row = rows.getJsonObject(i);

                        discoveryList.add(new JsonObject()
                                .put(ID, row.getInteger(ID))
                                .put(NAME, row.getString(NAME))
                                .put(IP, row.getString(IP))
                                .put(PORT, row.getInteger(PORT))
                                .put(STATUS, row.getString(STATUS))
                                .put(CREDENTIAL_PROFILE_ID, row.getInteger(CREDENTIAL_PROFILE_ID)));
                    }

                    LOGGER.info("Fetched " + discoveryList.size() + " discovery profiles");

                    handleSuccess(ctx, new JsonObject().put(DISCOVERIES, discoveryList));
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, cause));
    }

    @Override
    public void getById(RoutingContext ctx)
    {
        var id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("Fetching discovery profile with ID: " + id);

        executeQuery(GET_DISCOVERY_BY_ID, List.of(id))
                .onSuccess(result ->
                {
                    var rows = result.getJsonArray("rows", new JsonArray());

                    if (rows.isEmpty())
                    {
                        handleNotFound(ctx, LOGGER);
                    }
                    else
                    {
                        var row = rows.getJsonObject(0);

                        handleSuccess(ctx, new JsonObject()
                                .put(ID, row.getInteger(ID))
                                .put(NAME, row.getString(NAME))
                                .put(IP, row.getString(IP))
                                .put(PORT, row.getInteger(PORT))
                                .put(STATUS, row.getString(STATUS))
                                .put(CREDENTIAL_PROFILE_ID, row.getInteger(CREDENTIAL_PROFILE_ID)));
                    }
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, cause));
    }

    public void update(RoutingContext ctx)
    {
        var id = validateIdFromPath(ctx);

        if (id == -1) return;

        var body = ctx.body().asJsonObject();

        if (body == null || body.isEmpty())
        {
            handleMissingData(ctx, LOGGER, NO_DATA_TO_UPDATE);

            return;
        }

        LOGGER.info("Updating discovery profile ID " + id + " with data: " + body.encode());

        try
        {
            var name = body.getString(NAME);

            var ip = body.getString(IP);

            var port = body.getInteger(PORT, 22);

            var credentialProfileId = body.getInteger(CREDENTIAL_PROFILE_ID);

            if (name == null || ip == null || credentialProfileId == 0)
            {
                handleMissingData(ctx, LOGGER, MISSING_FIELDS);

                return;
            }

            if (isNotValidPort(port))
            {
                handleInvalidData(ctx, LOGGER, INVALID_PORT);

                return;
            }

            IpResolutionUtil.resolveAndValidateIp(ctx.vertx(), ip)
                    .onSuccess(validIp ->
                    {
                        if (validIp == null)
                        {
                            handleInvalidData(ctx, LOGGER, INVALID_IP);

                            return;
                        }

                        executeQuery(UPDATE_DISCOVERY, List.of(name, validIp, port, credentialProfileId, id))
                                .onSuccess(result ->
                                {
                                    var rowCount = result.getInteger("rowCount", 0);

                                    if (rowCount == 0)
                                    {
                                        handleNotFound(ctx, LOGGER);
                                    }
                                    else
                                    {
                                        LOGGER.info("Discovery profile updated successfully for ID " + id);

                                        handleSuccess(ctx, new JsonObject().put(MESSAGE, UPDATED_SUCCESS));
                                    }
                                })
                                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_UPDATE, cause));
                    })
                    .onFailure(err ->
                    {
                        LOGGER.warning("IP resolution failed during update: " + err.getMessage());

                        handleInvalidData(ctx, LOGGER, INVALID_IP);
                    });
        }
        catch (Exception e)
        {
            LOGGER.warning("Invalid input in update(): " + e.getMessage());

            handleInvalidData(ctx, LOGGER, INVALID_JSON_BODY);
        }
    }

    @Override
    public void delete(RoutingContext ctx)
    {
        int id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("Deleting discovery profile ID: " + id);

        executeQuery(DELETE_DISCOVERY, List.of(id))
                .onSuccess(result ->
                {
                    int rowCount = result.getInteger("rowCount", 0);

                    if (rowCount == 0)
                    {
                        handleNotFound(ctx, LOGGER);
                    }
                    else
                    {
                        LOGGER.info("Discovery profile deleted for ID: " + id);

                        handleSuccess(ctx, new JsonObject().put(MESSAGE, DELETED_SUCCESS));
                    }
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_DELETE, cause));
    }

    private void startDiscoveryPipeline(RoutingContext ctx, JsonArray devices, JsonArray defaultResults)
    {
        PingUtil.filterReachableDevicesAsync(ctx.vertx(), devices)
                .onFailure(cause -> ctx.fail(500, cause))
                .onSuccess(pingedDevices ->
                {

                    if (pingedDevices.isEmpty())
                    {
                        LOGGER.info("All devices lost after ping. Marking all as inactive.");

                        updateDiscoveryStatus(ctx, defaultResults);

                        return;
                    }

                    PortUtil.filterReachableDevicesAsync(ctx.vertx(), pingedDevices)
                            .onFailure(cause -> ctx.fail(500, cause))
                            .onSuccess(portFilteredDevices ->
                            {

                                if (portFilteredDevices.isEmpty())
                                {
                                    LOGGER.info("All devices lost after port check. Marking all as inactive.");

                                    updateDiscoveryStatus(ctx, defaultResults);

                                    return;
                                }

                                PluginService pluginService = PluginService.createProxy(ctx.vertx(), PluginVerticle.SERVICE_ADDRESS);

                                pluginService.runSSHReachability(portFilteredDevices)
                                        .onFailure(err ->
                                        {
                                            LOGGER.warning("SSH plugin call failed: " + err.getMessage());

                                            ctx.response()
                                                    .setStatusCode(500)
                                                    .end(new JsonObject().put(ERROR, PLUGIN_EXECUTION_FAILED).encode());
                                        })
                                        .onSuccess(sshResults ->
                                        {

                                            Map<Integer, Boolean> sshReachabilityMap = new HashMap<>();

                                            for (int i = 0; i < sshResults.size(); i++)
                                            {
                                                JsonObject pluginResult = sshResults.getJsonObject(i);

                                                sshReachabilityMap.put(pluginResult.getInteger(ID), pluginResult.getBoolean("reachable"));
                                            }

                                            for (int i = 0; i < defaultResults.size(); i++)
                                            {
                                                JsonObject deviceResult = defaultResults.getJsonObject(i);

                                                int id = deviceResult.getInteger(ID);

                                                if (sshReachabilityMap.containsKey(id))
                                                {
                                                    deviceResult.put("reachable", sshReachabilityMap.get(id));
                                                }
                                            }

                                            LOGGER.info("Discovery completed. Updating status for " + defaultResults.size() + " devices.");

                                            updateDiscoveryStatus(ctx, defaultResults);
                                        });
                            });
                });
    }

    public void runDiscovery(RoutingContext ctx)
    {
        LOGGER.info("Starting discovery run for all devices");

        executeQuery(DATA_TO_PLUGIN_FOR_DISCOVERY)
                .onSuccess(dbRes ->
                {
                    JsonArray rows = dbRes.getJsonArray("rows", new JsonArray());

                    JsonArray devices = new JsonArray();

                    JsonArray defaultResults = new JsonArray();

                    for (int i = 0; i < rows.size(); i++)
                    {
                        try
                        {
                            JsonObject row = rows.getJsonObject(i);

                            JsonObject device = new JsonObject()
                                    .put(ID, row.getInteger(ID))
                                    .put(PORT, row.getInteger(PORT))
                                    .put(IP, row.getString(IP))
                                    .put(USERNAME, row.getString(USERNAME))
                                    .put(PASSWORD, DecryptionUtil.decrypt(row.getString(PASSWORD)));

                            devices.add(device);

                            defaultResults.add(new JsonObject()
                                    .put(ID, row.getInteger(ID))
                                    .put("reachable", false));
                        }
                        catch (Exception e)
                        {
                            LOGGER.warning("Failed to process device row: " + e.getMessage());
                        }
                    }

                    if(handleIfEmpty(ctx,devices)) return;

                    startDiscoveryPipeline(ctx, devices, defaultResults);
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, cause));
    }

    private void updateDiscoveryStatus(RoutingContext ctx, JsonArray defaultResults)
    {
        var batchParams = new ArrayList<List<Object>>();

        for (var i = 0; i < defaultResults.size(); i++)
        {
            var result = defaultResults.getJsonObject(i);

            var id = result.getInteger(ID);

            var reachable = result.getBoolean("reachable");

            var status = reachable ? ACTIVE : INACTIVE;

            var paramSet = new ArrayList<>();

            paramSet.add(status);

            paramSet.add(id);

            batchParams.add(paramSet);
        }

        executeBatch(UPDATE_DISCOVERY_STATUS, batchParams)
                .onSuccess(res ->
                {
                    LOGGER.info("Batch status update successful for " + defaultResults.size() + " devices.");
                    // Send response only for runDiscovery (multiple devices)
                    if (defaultResults.size() > 1)
                    {
                        ctx.json(new JsonObject().put("results", defaultResults));
                    }
                })
                .onFailure(err ->
                {
                    LOGGER.warning("Batch update failed: " + err.getMessage());
                    // Send error response only for runDiscovery (multiple devices)
                    if (defaultResults.size() > 1)
                    {
                        ctx.response().setStatusCode(500)
                                .end(new JsonObject().put(ERROR, "Status update failed").encode());
                    }
                });
    }

    public static boolean isNotValidPort(int port)
    {
        return !(port >= 1) || !(port <= 65535);
    }

    private boolean handleIfEmpty(RoutingContext ctx, JsonArray devices)
    {
        if (devices.isEmpty())
        {
            ctx.json(new JsonObject().put(MESSAGE, NO_DEVICES_FOR_DISCOVERY));

            return true;
        }
        return false;
    }
}
