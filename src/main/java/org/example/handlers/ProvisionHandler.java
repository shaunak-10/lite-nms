package org.example.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;

import static org.example.constants.AppConstants.AddressesAndPaths.*;
import static org.example.constants.AppConstants.DiscoveryQuery.*;
import static org.example.constants.AppConstants.ProvisionField.DISCOVERY_PROFILE_ID;
import static org.example.constants.AppConstants.ProvisionQuery.*;
import static org.example.constants.AppConstants.DiscoveryField.*;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;
import static org.example.constants.AppConstants.Headers.*;

import io.vertx.sqlclient.Tuple;
import org.example.db.DatabaseClient;
import org.example.utils.LoggerUtil;

import java.util.logging.Logger;

public class ProvisionHandler extends AbstractCrudHandler
{
    private static final ProvisionHandler INSTANCE = new ProvisionHandler();

    private static final Logger LOGGER = LoggerUtil.getDatabaseLogger();

    private static final SqlClient DATABASE_CLIENT = DatabaseClient.getClient();

    private ProvisionHandler() {}

    public static ProvisionHandler getInstance()
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
            Integer discoveryProfileId = body.getInteger(DISCOVERY_PROFILE_ID);

            if (discoveryProfileId == null)
            {
                handleMissingData(ctx, LOGGER, MISSING_FIELDS);

                return;
            }

            LOGGER.info("Fetching discovery profile with ID: " + discoveryProfileId);

            DATABASE_CLIENT
                    .preparedQuery(GET_DISCOVERY_BY_ID)
                    .execute(Tuple.of(discoveryProfileId), fetchRes ->
                    {
                        if (fetchRes.failed())
                        {
                            handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, fetchRes.cause());

                            return;
                        }

                        RowSet<Row> rows = fetchRes.result();

                        if (!rows.iterator().hasNext())
                        {
                            handleNotFound(ctx, LOGGER);

                            return;
                        }

                        Row row = rows.iterator().next();

                        String status = row.getString(STATUS);

                        if (!"active".equalsIgnoreCase(status))
                        {
                            handleInvalidData(ctx, LOGGER, DEVICE_NOT_DISCOVERED);

                            return;
                        }

                        String name = row.getString(NAME);

                        String ip = row.getString(IP);

                        Integer port = row.getInteger(PORT);

                        Integer credentialProfileId = row.getInteger(CREDENTIAL_PROFILE_ID);

                        LOGGER.info("Inserting provisioned device copied from discovery profile ID: " + discoveryProfileId);

                        DATABASE_CLIENT
                                .preparedQuery(ADD_PROVISION)
                                .execute(Tuple.of(name, ip, port, credentialProfileId), insertRes ->
                                {
                                    if (insertRes.succeeded())
                                    {
                                        int id = insertRes.result().iterator().next().getInteger(ID);

                                        LOGGER.info("Provisioned device added with ID: " + id);

                                        handleCreated(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS).put(ID, id));
                                    }
                                    else
                                    {
                                        handleDatabaseError(ctx, LOGGER, FAILED_TO_ADD, insertRes.cause());
                                    }
                                });

                    });

        }
        catch (Exception e)
        {
            LOGGER.warning("Exception in add(): " + e.getMessage());

            handleInvalidData(ctx, LOGGER, INVALID_JSON_BODY);
        }
    }


    @Override
    public void list(RoutingContext ctx)
    {
        LOGGER.info("Fetching provisioned device list");

        DATABASE_CLIENT
                .preparedQuery(GET_ALL_PROVISIONS)
                .execute(databaseResponse ->
                {
                    if (databaseResponse.succeeded())
                    {
                        JsonArray provisionList = new JsonArray();

                        for (Row row : databaseResponse.result())
                        {
                            JsonObject provision = new JsonObject()
                                    .put(ID, row.getInteger(ID))
                                    .put(NAME, row.getString(NAME))
                                    .put(IP, row.getString(IP))
                                    .put(PORT, row.getInteger(PORT))
                                    .put(CREDENTIAL_PROFILE_ID, row.getInteger(CREDENTIAL_PROFILE_ID));

                            JsonArray pollingResults = row.getJsonArray("polling_results");

                            provision.put("polling_results", pollingResults);

                            provisionList.add(provision);
                        }

                        LOGGER.info("Fetched " + provisionList.size() + " provisioned devices");

                        handleSuccess(ctx, new JsonObject().put("provisions", provisionList));
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

        LOGGER.info("Fetching provisioned device with ID: " + id);

        DATABASE_CLIENT
                .preparedQuery(GET_PROVISION_BY_ID)
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

                            JsonObject provision = new JsonObject()
                                    .put(ID, row.getInteger(ID))
                                    .put(NAME, row.getString(NAME))
                                    .put(IP, row.getString(IP))
                                    .put(PORT, row.getInteger(PORT))
                                    .put(CREDENTIAL_PROFILE_ID, row.getInteger(CREDENTIAL_PROFILE_ID));

                            JsonArray pollingResults = row.getJsonArray("polling_results");

                            provision.put("polling_results", pollingResults);

                            handleSuccess(ctx, provision);
                        }
                    }
                    else
                    {
                        handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, databaseResponse.cause());
                    }
                });

    }


    @Override
    public void update(RoutingContext ctx)
    {
        handleInvalidOperation(ctx, LOGGER, UPDATE_NOT_ALLOWED);
    }

    @Override
    public void delete(RoutingContext ctx)
    {
        int id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("Deleting provisioned device with ID: " + id);

        DATABASE_CLIENT
                .preparedQuery(DELETE_PROVISION)
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
                            LOGGER.info("Provisioned device deleted with ID: " + id);

                            handleSuccess(ctx, new JsonObject().put(MESSAGE, DELETED_SUCCESS));
                        }
                    }
                    else
                    {
                        handleDatabaseError(ctx, LOGGER, FAILED_TO_DELETE, databaseResponse.cause());
                    }
                });
    }

    public void startPolling(RoutingContext ctx)
    {
        JsonObject body = ctx.body().asJsonObject();

        int interval;

        if(body == null)
        {
            interval = 60000;
        }
        else
        {
            interval = body.getInteger("interval");
        }

        JsonObject message = new JsonObject().put("interval", interval);

        ctx.vertx().eventBus().request(POLLING_START, message, res ->
        {
            if (res.failed())
            {
                ctx.response()
                        .setStatusCode(400)
                        .putHeader(CONTENT_TYPE,APPLICATION_JSON)
                        .end(new JsonObject().put(ERROR, res.cause().getMessage()).encode());
            }
            else
            {
                JsonObject reply = (JsonObject) res.result().body();

                ctx.response()
                        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .end(reply.encodePrettily());
            }
        });
    }

    public void stopPolling(RoutingContext ctx) {
        ctx.vertx().eventBus().request(POLLING_STOP, null, res ->
        {
            if (res.failed())
            {
                ctx.response()
                        .setStatusCode(400)
                        .putHeader(CONTENT_TYPE,APPLICATION_JSON)
                        .end(new JsonObject().put(ERROR, res.cause().getMessage()).encode());
            }
            else
            {
                JsonObject reply = (JsonObject) res.result().body();

                ctx.response()
                        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .end(reply.encodePrettily());
            }
        });
    }

}
