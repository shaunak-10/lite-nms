package org.example.services.server.handlers;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.example.services.discovery.DiscoveryVerticle;
import org.example.utils.*;

import java.util.*;

import static org.example.constants.AppConstants.CredentialField.NAME;
import static org.example.constants.AppConstants.DiscoveryQuery.*;
import static org.example.constants.AppConstants.DiscoveryField.*;
import static org.example.constants.AppConstants.CredentialField.USERNAME;
import static org.example.constants.AppConstants.CredentialField.PASSWORD;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;

public class DiscoveryHandler extends AbstractCrudHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryHandler.class);

    private static final DiscoveryHandler INSTANCE = new DiscoveryHandler();

    private DiscoveryHandler() {}

    public static DiscoveryHandler getInstance()
    {
        return INSTANCE;
    }

    @Override
    public void add(RoutingContext ctx)
    {
        try
        {
            var body = ctx.body().asJsonObject();

            if(notValidateDiscoveryFields(ctx, body)) return;

            var port = body.getInteger(PORT, 22);

            var credentialProfileId = body.getInteger(CREDENTIAL_PROFILE_ID);

            LOGGER.info("Adding new discovery profile: " + body.encode());

            IpResolutionUtil.resolveAndValidateIp(ctx.vertx(), body.getString(IP))
                    .onSuccess(validIp ->
                    {
                        if (validIp == null)
                        {
                            handleInvalidData(ctx, INVALID_IP);

                            return;
                        }

                        if (isNotValidPort(port))
                        {
                            handleInvalidData(ctx, INVALID_PORT);

                            return;
                        }

                        executeQuery(ADD_DISCOVERY, List.of(body.getString(NAME), validIp, port, INACTIVE, credentialProfileId))
                                .onSuccess(result ->
                                {
                                    var rows = result.getJsonArray("rows");

                                    if (rows != null && !rows.isEmpty())
                                    {
                                        var id = rows.getJsonObject(0).getInteger(ID);

                                        LOGGER.info("Discovery profile added with ID: " + id);

                                        var discoveryRequest = new JsonObject()
                                                .put("action", "fetchDeviceDetailsAndRunDiscovery")
                                                .put("discoveryId", id)
                                                .put("ip", validIp)
                                                .put("port", port)
                                                .put("credentialProfileId", credentialProfileId);

                                        ctx.vertx().eventBus().request(DiscoveryVerticle.SERVICE_ADDRESS, discoveryRequest,
                                                ar ->
                                                {
                                                    if (ar.failed())
                                                    {
                                                        LOGGER.error("Error during discovery process: " + ar.cause().getMessage());
                                                    }
                                                    else
                                                    {
                                                        LOGGER.info("Discovery process initiated successfully for ID: " + id);
                                                    }
                                                });

                                        handleCreated(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS).put(ID, id));
                                    }
                                    else
                                    {
                                        LOGGER.error("Insert succeeded but no ID returned.");

                                        handleMissingData(ctx,"Insert succeeded but no ID returned.");
                                    }
                                })
                                .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_ADD, cause));
                    })
                    .onFailure(err ->
                    {
                        LOGGER.warn("Error during IP resolution: " + err.getMessage());

                        handleInvalidData(ctx, INVALID_IP);
                    });
        }
        catch (Exception e)
        {
            LOGGER.error("Invalid input in add(): " + e.getMessage());

            handleInvalidData(ctx, INVALID_JSON_BODY);
        }
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
                .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_FETCH, cause));
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
                        handleNotFound(ctx);
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
                .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_FETCH, cause));
    }

    public void update(RoutingContext ctx)
    {
        try
        {
            var id = validateIdFromPath(ctx);

            if (id == -1) return;

            var body = ctx.body().asJsonObject();

            if(notValidateDiscoveryFields(ctx, body)) return;

            LOGGER.info("Updating discovery profile ID " + id + " with data: " + body.encode());

            var name = body.getString(NAME);

            var ip = body.getString(IP);

            var port = body.getInteger(PORT, 22);

            var credentialProfileId = body.getInteger(CREDENTIAL_PROFILE_ID);

            if (name == null || ip == null || credentialProfileId == 0)
            {
                handleMissingData(ctx, MISSING_FIELDS);

                return;
            }

            if (isNotValidPort(port))
            {
                handleInvalidData(ctx, INVALID_PORT);

                return;
            }

            IpResolutionUtil.resolveAndValidateIp(ctx.vertx(), ip)
                    .onSuccess(validIp ->
                    {
                        if (validIp == null)
                        {
                            handleInvalidData(ctx, INVALID_IP);

                            return;
                        }

                        executeQuery(UPDATE_DISCOVERY, List.of(name, validIp, port, credentialProfileId, id))
                                .onSuccess(result ->
                                {
                                    var rowCount = result.getInteger("rowCount", 0);

                                    if (rowCount == 0)
                                    {
                                        handleNotFound(ctx);
                                    }
                                    else
                                    {
                                        LOGGER.info("Discovery profile updated successfully for ID " + id);

                                        handleSuccess(ctx, new JsonObject().put(MESSAGE, UPDATED_SUCCESS));
                                    }
                                })
                                .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_UPDATE, cause));
                    })
                    .onFailure(err ->
                    {
                        LOGGER.error("IP resolution failed during update: " + err.getMessage());

                        handleInvalidData(ctx, INVALID_IP);
                    });
        }
        catch (Exception e)
        {
            LOGGER.error("Invalid input in update(): " + e.getMessage());

            handleInvalidData(ctx, INVALID_JSON_BODY);
        }
    }

    @Override
    public void delete(RoutingContext ctx)
    {
        var id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("Deleting discovery profile ID: " + id);

        executeQuery(DELETE_DISCOVERY, List.of(id))
                .onSuccess(result ->
                {
                    var rowCount = result.getInteger("rowCount", 0);

                    if (rowCount == 0)
                    {
                        handleNotFound(ctx);
                    }
                    else
                    {
                        LOGGER.info("Discovery profile deleted for ID: " + id);

                        handleSuccess(ctx, new JsonObject().put(MESSAGE, DELETED_SUCCESS));
                    }
                })
                .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_DELETE, cause));
    }

    public void runDiscovery(RoutingContext ctx)
    {
        var id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("running discovery profile ID: " + id);

        executeQuery(DATA_TO_PLUGIN_FOR_DISCOVERY, List.of(id))
                .onSuccess(dbRes ->
                {
                    try
                    {
                        var rows = dbRes.getJsonArray("rows", new JsonArray());

                        if (rows.isEmpty())
                        {
                            handleNotFound(ctx);
                        }
                        else
                        {
                            try
                            {
                                var row = rows.getJsonObject(0);

                                // Send data to DiscoveryVerticle to process
                                var discoveryData = new JsonObject()
                                        .put("action", "startDiscovery")
                                        .put("device", new JsonObject()
                                                .put(ID, row.getInteger(ID))
                                                .put(PORT, row.getInteger(PORT))
                                                .put(IP, row.getString(IP))
                                                .put(USERNAME, row.getString(USERNAME))
                                                .put(PASSWORD, DecryptionUtil.decrypt(row.getString(PASSWORD))));

                                ctx.vertx().eventBus().request(DiscoveryVerticle.SERVICE_ADDRESS, discoveryData,
                                        ar ->
                                        {
                                            if (ar.failed())
                                            {
                                                LOGGER.error("Error during discovery process: " + ar.cause().getMessage());

                                                ctx.response()
                                                        .setStatusCode(500)
                                                        .end(new JsonObject().put(ERROR, PLUGIN_EXECUTION_FAILED).encode());
                                            }
                                            else
                                            {
                                                JsonObject response = (JsonObject) ar.result().body();

                                                ctx.json(response);
                                            }
                                        });
                            }
                            catch (Exception e)
                            {
                                LOGGER.error(e.getMessage());
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Failed to process device: " + e.getMessage());
                    }
                })
                .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_FETCH, cause));
    }

    public static boolean isNotValidPort(int port)
    {
        return !(port >= 1) || !(port <= 65535);
    }

    private boolean notValidateDiscoveryFields(RoutingContext ctx, JsonObject body)
    {
        if (body == null)
        {
            handleMissingData(ctx, INVALID_JSON_BODY);

            return true;
        }

        var name = body.getString(NAME);

        var ip = body.getString(IP);

        var credentialProfileId = body.getInteger(CREDENTIAL_PROFILE_ID);

        if (name == null || ip == null || credentialProfileId == 0)
        {
            handleMissingData(ctx, MISSING_FIELDS);

            return true;
        }

        return false;
    }

}
