package org.example.services.server.handlers;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.example.MainApp;
import org.example.services.discovery.DiscoveryVerticle;
import org.example.utils.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.example.constants.AppConstants.*;
import static org.example.constants.AppConstants.CredentialField.NAME;
import static org.example.constants.AppConstants.DiscoveryQuery.*;
import static org.example.constants.AppConstants.DiscoveryField.*;
import static org.example.constants.AppConstants.CredentialField.USERNAME;
import static org.example.constants.AppConstants.CredentialField.PASSWORD;
import static org.example.constants.AppConstants.CredentialField.SYSTEM_TYPE_RESPONSE;
import static org.example.constants.AppConstants.CredentialField.SYSTEM_TYPE;
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

            if(!isBodyValid(ctx,body,DISCOVERY)) return;

            var port = body.getInteger(PORT, 22);

            var credentialProfileId = body.getInteger(CREDENTIAL_PROFILE_ID);

            LOGGER.info("Adding new discovery profile: " + body.encode());

            if (isNotValidPort(port))
            {
                handleInvalidData(ctx, INVALID_PORT);

                return;
            }

            MainApp.getVertx().executeBlocking(() -> IpResolutionUtil.resolveAndValidateIp(body.getString(IP)))
                    .timeout(ConfigLoader.get().getInteger("ip.resolution.timeout"), TimeUnit.MILLISECONDS)
                    .onSuccess(validIp ->
                    {
                        try
                        {
                            if (validIp == null)
                            {
                                handleInvalidData(ctx, INVALID_IP);

                                return;
                            }

                            executeQuery(ADD_DISCOVERY, List.of(body.getString(NAME), validIp, port, INACTIVE, credentialProfileId))
                                    .onSuccess(result ->
                                    {
                                        try
                                        {
                                            var rows = result.getJsonArray(ROWS);

                                            if (rows != null && !rows.isEmpty())
                                            {
                                                var id = rows.getJsonObject(0).getInteger(ID);

                                                LOGGER.info("Discovery profile added with ID: " + id);

                                                ctx.vertx().eventBus().send(DiscoveryVerticle.SERVICE_ADDRESS, new JsonObject()
                                                        .put(ACTION, SAVE_AND_RUN_DISCOVERY)
                                                        .put(ID, id)
                                                        .put(IP, validIp)
                                                        .put(PORT, port)
                                                        .put(CREDENTIAL_PROFILE_ID, credentialProfileId));

                                                handleCreated(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS).put(ID, id));
                                            }
                                            else
                                            {
                                                LOGGER.error("Insert succeeded but no ID returned.");

                                                handleMissingData(ctx, "Insert succeeded but no ID returned.");
                                            }
                                        }
                                        catch (Exception exception)
                                        {
                                            LOGGER.error("Error while processing result: " + exception.getMessage());
                                        }
                                    })
                                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_ADD, cause));
                        }
                        catch (Exception exception)
                        {
                            LOGGER.error("Error while resolving IP: " + exception.getMessage());
                        }
                    })
                    .onFailure(err ->
                    {
                        LOGGER.warn("Error during IP resolution: " + err.getMessage());

                        handleInvalidData(ctx, INVALID_IP);
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Invalid input in add(): " + exception.getMessage());
        }
    }

    @Override
    public void list(RoutingContext ctx)
    {
        try
        {
            LOGGER.info("Fetching discovery profile list");

            executeQuery(GET_ALL_DISCOVERY)
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rows = result.getJsonArray(ROWS, new JsonArray());

                            var discoveryList = new JsonArray();

                            for (var i = 0; i < rows.size(); i++)
                            {
                                try
                                {
                                    var row = rows.getJsonObject(i);

                                    discoveryList.add(new JsonObject()
                                            .put(ID, row.getInteger(ID))
                                            .put(NAME, row.getString(NAME))
                                            .put(IP, row.getString(IP))
                                            .put(PORT, row.getInteger(PORT))
                                            .put(STATUS, row.getString(STATUS))
                                            .put(CREDENTIAL_PROFILE_ID_RESPONSE, row.getInteger(CREDENTIAL_PROFILE_ID)));
                                }
                                catch (Exception exception)
                                {
                                    LOGGER.error("Error while processing row: " + exception.getMessage());
                                }
                            }

                            LOGGER.info("Fetched " + discoveryList.size() + " discovery profiles");

                            handleSuccess(ctx, new JsonObject().put(DISCOVERIES, discoveryList));
                        }
                        catch (Exception exception)
                        {
                            LOGGER.error("Error while processing result: " + exception.getMessage());
                        }
                    })
                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_FETCH, cause));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error while fetching discovery profiles: " + exception.getMessage());
        }

    }

    @Override
    public void getById(RoutingContext ctx)
    {
        try
        {
            var id = validateIdFromPath(ctx);

            if (id == -1) return;

            LOGGER.info("Fetching discovery profile with ID: " + id);

            executeQuery(GET_DISCOVERY_BY_ID, List.of(id))
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rows = result.getJsonArray(ROWS, new JsonArray());

                            if (rows.isEmpty())
                            {
                                handleNotFound(ctx,new JsonObject().put(ERROR, NOT_FOUND));
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
                                        .put(CREDENTIAL_PROFILE_ID_RESPONSE, row.getInteger(CREDENTIAL_PROFILE_ID)));
                            }
                        }
                        catch (Exception exception)
                        {
                            LOGGER.error("Error while processing result: " + exception.getMessage());
                        }
                    })
                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_FETCH, cause));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error while fetching discovery profile: " + exception.getMessage());
        }

    }

    @Override
    public void update(RoutingContext ctx)
    {
        try
        {
            var id = validateIdFromPath(ctx);

            if (id == -1) return;

            var body = ctx.body().asJsonObject();

            if(!isBodyValid(ctx,body,DISCOVERY)) return;

            LOGGER.info("Updating discovery profile ID " + id + " with data: " + body.encode());

            var port = body.getInteger(PORT, 22);

            if (isNotValidPort(port))
            {
                handleInvalidData(ctx, INVALID_PORT);

                return;
            }

            MainApp.getVertx().executeBlocking(() -> IpResolutionUtil.resolveAndValidateIp(body.getString(IP)))
                    .timeout(ConfigLoader.get().getInteger("ip.resolution.timeout"), TimeUnit.MILLISECONDS)
                    .onSuccess(validIp ->
                    {
                        try
                        {
                            if (validIp == null)
                            {
                                handleInvalidData(ctx, INVALID_IP);

                                return;
                            }

                            executeQuery(UPDATE_DISCOVERY, List.of(body.getString(NAME), validIp, port, body.getInteger(CREDENTIAL_PROFILE_ID), id))
                                    .onSuccess(result ->
                                    {
                                        try
                                        {
                                            var rowCount = result.getInteger(ROW_COUNT, 0);

                                            if (rowCount == 0)
                                            {
                                                handleNotFound(ctx, new JsonObject().put(ERROR, NOT_FOUND));
                                            }
                                            else
                                            {
                                                LOGGER.info("Discovery profile updated successfully for ID " + id);

                                                handleSuccess(ctx, new JsonObject().put(MESSAGE, UPDATED_SUCCESS));
                                            }
                                        }
                                        catch (Exception exception)
                                        {
                                            LOGGER.error("Error while processing result: " + exception.getMessage());
                                        }
                                    })
                                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_UPDATE, cause));
                        }
                        catch (Exception exception)
                        {
                            LOGGER.error("Error while resolving IP: " + exception.getMessage());
                        }
                    })
                    .onFailure(err ->
                    {
                        LOGGER.error("IP resolution failed during update: " + err.getMessage());

                        handleInvalidData(ctx, INVALID_IP);
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Invalid input in update(): " + exception.getMessage());
        }
    }

    @Override
    public void delete(RoutingContext ctx)
    {
        try
        {
            var id = validateIdFromPath(ctx);

            if (id == -1) return;

            LOGGER.info("Deleting discovery profile ID: " + id);

            executeQuery(DELETE_DISCOVERY, List.of(id))
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rowCount = result.getInteger(ROW_COUNT, 0);

                            if (rowCount == 0)
                            {
                                handleNotFound(ctx,new JsonObject().put(ERROR, NOT_FOUND));
                            }
                            else
                            {
                                LOGGER.info("Discovery profile deleted for ID: " + id);

                                handleSuccess(ctx, new JsonObject().put(MESSAGE, DELETED_SUCCESS));
                            }
                        }
                        catch (Exception exception)
                        {
                            LOGGER.error("Error while processing result: " + exception.getMessage());
                        }
                    })
                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_DELETE, cause));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error while deleting discovery profile: " + exception.getMessage());
        }

    }

    /**
     * Runs the discovery process for a given discovery profile ID.
     * This method validates the discovery profile ID from the URL path, fetches the relevant data from the database,
     * and then sends the data to the DiscoveryVerticle for processing. If the profile data is not found or there is an error,
     * it sends an appropriate response to the client.
     *
     * @param ctx the RoutingContext containing the request data
     */
    public void runDiscovery(RoutingContext ctx)
    {
        try
        {
            var id = validateIdFromPath(ctx);

            if (id == -1) return;

            LOGGER.info("running discovery profile ID: " + id);

            executeQuery(DATA_TO_PLUGIN_FOR_DISCOVERY, List.of(id))
                    .onSuccess(dbRes ->
                    {
                        try
                        {
                            var rows = dbRes.getJsonArray(ROWS, new JsonArray());

                            if (rows.isEmpty())
                            {
                                handleNotFound(ctx,new JsonObject().put(ERROR, NOT_FOUND));
                            }
                            else
                            {
                                var row = rows.getJsonObject(0);

                                // Send data to DiscoveryVerticle to process
                                var discoveryData = new JsonObject()
                                        .put(ACTION, START_DISCOVERY)
                                        .put(DEVICE, new JsonObject()
                                                .put(ID, row.getInteger(ID))
                                                .put(PORT, row.getInteger(PORT))
                                                .put(IP, row.getString(IP))
                                                .put(USERNAME, row.getString(USERNAME))
                                                .put(PASSWORD, DecryptionUtil.decrypt(row.getString(PASSWORD)))
                                                .put(SYSTEM_TYPE_RESPONSE, row.getString(SYSTEM_TYPE)));

                                ctx.vertx().eventBus().send(DiscoveryVerticle.SERVICE_ADDRESS, discoveryData);

                                handleSuccess(ctx,new JsonObject().put(MESSAGE,"Discovery run successfully started"));
                            }
                        }
                        catch (Exception exception)
                        {
                            LOGGER.error("Failed to process device: " + exception.getMessage());
                        }
                    })
                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_FETCH, cause));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error while running discovery: " + exception.getMessage());
        }
    }

    /**
     * Validates if the provided port number is within the valid range (1-65535).
     *
     * @param port the port number to validate
     * @return true if the port is invalid (not in the valid range), false otherwise
     */
    public static boolean isNotValidPort(int port)
    {
        try
        {
            return !(port >= 1) || !(port <= 65535);
        }
        catch (Exception exception)
        {
            LOGGER.error("Invalid port: " + exception.getMessage());

            return TRUE;
        }

    }

    /**
     * Validates the required fields for the discovery profile in the request body.
     * Checks if the body is present and contains the necessary fields: name, IP, and credentialProfileId.
     * If any of the fields are missing, an error response is sent to the client.
     *
     * @param ctx the RoutingContext containing the request data
     * @param body the JSON object containing the fields to validate
     * @return true if validation fails (due to missing data or invalid fields), false otherwise
     */
    private boolean notValidateDiscoveryFields(RoutingContext ctx, JsonObject body)
    {
        try
        {
            if (body == null)
            {
                handleMissingData(ctx, INVALID_JSON_BODY);

                return TRUE;
            }

            var name = body.getString(NAME);

            var ip = body.getString(IP);

            var credentialProfileId = body.getInteger(CREDENTIAL_PROFILE_ID);

            if (name == null || ip == null || credentialProfileId == 0)
            {
                handleMissingData(ctx, MISSING_FIELDS);

                return TRUE;
            }

            return FALSE;
        }
        catch (Exception exception)
        {
            LOGGER.error("Error while validating discovery fields: " + exception.getMessage());

            return TRUE;
        }

    }

}
