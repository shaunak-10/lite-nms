package org.example.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import org.example.db.DatabaseClient;
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

    private static final Logger LOGGER = LoggerUtil.getDatabaseLogger();

    private static final SqlClient DATABASE_CLIENT = DatabaseClient.getClient();

    private DiscoveryHandler() {}

    public static DiscoveryHandler getInstance()
    {
        return INSTANCE;
    }

    @Override
    public void add(RoutingContext ctx)
    {
        JsonObject body = ctx.body().asJsonObject();

        if (body == null)
        {
            handleMissingData(ctx, LOGGER, INVALID_JSON_BODY);

            return;
        }

        try
        {
            String name = body.getString(NAME);

            String ip = body.getString(IP);

            int port = body.getInteger(PORT, 22);

            String status = body.getString(STATUS, "inactive");

            int credentialProfileId = body.getInteger(CREDENTIAL_PROFILE_ID);

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

                        DATABASE_CLIENT
                                .preparedQuery(ADD_DISCOVERY)
                                .execute(Tuple.of(name, validIp, port, status, credentialProfileId), databaseResponse ->
                                {
                                    if (databaseResponse.succeeded())
                                    {
                                        int id = databaseResponse.result().iterator().next().getInteger(ID);

                                        LOGGER.info("Discovery profile added with ID: " + id);

                                        handleCreated(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS).put(ID, id));
                                    }
                                    else
                                    {
                                        handleDatabaseError(ctx, LOGGER, FAILED_TO_ADD, databaseResponse.cause());
                                    }
                                });
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

    @Override
    public void list(RoutingContext ctx)
    {
        LOGGER.info("Fetching discovery profile list");

        DATABASE_CLIENT
                .preparedQuery(GET_ALL_DISCOVERY)
                .execute(databaseResponse ->
                {
                    if (databaseResponse.succeeded())
                    {
                        JsonArray discoveryList = new JsonArray();

                        for (Row row : databaseResponse.result())
                        {
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
                    }
                    else
                    {
                        handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, databaseResponse.cause());
                    }
                });
    }

    @Override
    public void getById(RoutingContext ctx)
    {
        int id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("Fetching discovery profile with ID: " + id);

        DATABASE_CLIENT
                .preparedQuery(GET_DISCOVERY_BY_ID)
                .execute(Tuple.of(id), databaseResponse ->
                {
                    if (databaseResponse.succeeded())
                    {
                        RowSet<Row> result = databaseResponse.result();

                        if (result.size() == 0)
                        {
                            handleNotFound(ctx, LOGGER);
                        }
                        else
                        {
                            Row row = result.iterator().next();

                            handleSuccess(ctx, new JsonObject()
                                    .put(ID, row.getInteger(ID))
                                    .put(NAME, row.getString(NAME))
                                    .put(IP, row.getString(IP))
                                    .put(PORT, row.getInteger(PORT))
                                    .put(STATUS, row.getString(STATUS))
                                    .put(CREDENTIAL_PROFILE_ID, row.getInteger(CREDENTIAL_PROFILE_ID)));
                        }
                    }
                    else
                    {
                        handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, databaseResponse.cause());
                    }
                });
    }

    public void update(RoutingContext ctx)
    {
        int id = validateIdFromPath(ctx);

        if (id == -1) return;

        JsonObject body = ctx.body().asJsonObject();

        if (body == null || body.isEmpty())
        {
            handleMissingData(ctx, LOGGER, NO_DATA_TO_UPDATE);

            return;
        }

        LOGGER.info("Updating discovery profile ID " + id + " with data: " + body.encode());

        try
        {
            String name = body.getString(NAME);

            String ip = body.getString(IP);

            int port = body.getInteger(PORT, 22);

            int credentialProfileId = body.getInteger(CREDENTIAL_PROFILE_ID);

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

                        DATABASE_CLIENT
                                .preparedQuery(UPDATE_DISCOVERY)
                                .execute(Tuple.of(name, validIp, port, credentialProfileId, id), updateRes ->
                                {
                                    if (updateRes.succeeded())
                                    {
                                        if (updateRes.result().rowCount() == 0)
                                        {
                                            handleNotFound(ctx, LOGGER);
                                        }
                                        else
                                        {
                                            LOGGER.info("Discovery profile updated successfully for ID " + id);

                                            handleSuccess(ctx, new JsonObject().put(MESSAGE, UPDATED_SUCCESS));
                                        }
                                    }
                                    else
                                    {
                                        handleDatabaseError(ctx, LOGGER, FAILED_TO_UPDATE, updateRes.cause());
                                    }
                                });
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

        DATABASE_CLIENT
                .preparedQuery(DELETE_DISCOVERY)
                .execute(Tuple.of(id), databaseResponse ->
                {
                    if (databaseResponse.succeeded())
                    {
                        if (databaseResponse.result().rowCount() == 0)
                        {
                            handleNotFound(ctx, LOGGER);
                        }
                        else
                        {
                            LOGGER.info("Discovery profile deleted for ID: " + id);

                            handleSuccess(ctx, new JsonObject().put(MESSAGE, DELETED_SUCCESS));
                        }
                    }
                    else
                    {
                        handleDatabaseError(ctx, LOGGER, FAILED_TO_DELETE, databaseResponse.cause());
                    }
                });
    }

    public void runDiscovery(RoutingContext ctx)
    {
        LOGGER.info("Starting discovery run for all devices");

        DATABASE_CLIENT
                .preparedQuery(DATA_TO_PLUGIN_FOR_DISCOVERY)
                .execute(dbRes ->
                {
                    if (dbRes.failed())
                    {
                        handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, dbRes.cause());

                        return;
                    }

                    JsonArray devices = new JsonArray();

                    JsonArray defaultResults = new JsonArray();

                    for (Row row : dbRes.result())
                    {
                        try
                        {
                            JsonObject device = new JsonObject()
                                    .put(ID, row.getInteger(ID))
                                    .put(PORT, row.getInteger(PORT))
                                    .put(IP, row.getString(IP))
                                    .put(USERNAME, row.getString(USERNAME))
                                    .put(PASSWORD, DecryptionUtil.decrypt(row.getString(PASSWORD)));

                            devices.add(device);

                            defaultResults.add(new JsonObject().put(ID, row.getInteger(ID)).put("reachable", false));
                        }
                        catch (Exception e)
                        {
                            LOGGER.warning("Failed to process device row: " + e.getMessage());
                        }
                    }

                    if (handleIfEmpty(ctx, devices)) return;

                    PingUtil.filterReachableDevicesAsync(ctx.vertx(), devices).onComplete(pingRes ->
                    {
                        if (pingRes.failed())
                        {
                            ctx.fail(500, pingRes.cause());

                            return;
                        }

                        JsonArray pingedDevices = pingRes.result();

                        if (handleIfEmpty(ctx, pingedDevices)) return;

                        PortUtil.filterReachableDevicesAsync(ctx.vertx(), pingedDevices).onComplete(portRes ->
                        {
                            if (portRes.failed())
                            {
                                ctx.fail(500, portRes.cause());

                                return;
                            }

                            JsonArray finalDevices = portRes.result();

                            if (handleIfEmpty(ctx, finalDevices)) return;

                            PluginService pluginService = PluginService.createProxy(ctx.vertx(), PluginVerticle.SERVICE_ADDRESS);

                            pluginService.runSSHReachability(finalDevices)
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

                                        LOGGER.info("Discovery completed with " + defaultResults.size() + " results");

                                        List<Tuple> batchParams = new ArrayList<>();

                                        for (int i = 0; i < defaultResults.size(); i++)
                                        {
                                            JsonObject result = defaultResults.getJsonObject(i);

                                            int id = result.getInteger(ID);

                                            boolean reachable = result.getBoolean("reachable");

                                            String status = reachable ? ACTIVE : INACTIVE;

                                            batchParams.add(Tuple.of(status, id));
                                        }

                                        DATABASE_CLIENT
                                                .preparedQuery(UPDATE_DISCOVERY_STATUS)
                                                .executeBatch(batchParams)
                                                .onSuccess(res ->
                                                {
                                                    LOGGER.info("Batch status update successful for " + defaultResults.size() + " devices.");

                                                    ctx.json(new JsonObject().put("results", defaultResults));
                                                })
                                                .onFailure(err ->
                                                {
                                                    LOGGER.warning("Batch update failed: " + err.getMessage());

                                                    ctx.response().setStatusCode(500)
                                                            .end(new JsonObject().put(ERROR, "Status update failed").encode());
                                                });

                                    })
                                    .onFailure(err ->
                                    {
                                        LOGGER.warning("SSH plugin call failed: " + err.getMessage());

                                        ctx.response()
                                                .setStatusCode(500)
                                                .end(new JsonObject().put(ERROR, PLUGIN_EXECUTION_FAILED).encode());
                                    });

                        });
                    });
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
