package org.example.services.server.handlers;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.example.utils.EncryptionUtil;

import java.util.List;

import static org.example.constants.AppConstants.CredentialQuery.*;
import static org.example.constants.AppConstants.CredentialField.*;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;

public class CredentialHandler extends AbstractCrudHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialHandler.class);

    private static final CredentialHandler INSTANCE = new CredentialHandler();

    private CredentialHandler() {}

    public static CredentialHandler getInstance()
    {
        return INSTANCE;
    }

    @Override
    public void add(RoutingContext ctx)
    {
        try
        {
            var body = ctx.body().asJsonObject();

            if (notValidateCredentialFields(ctx, body)) return;

            LOGGER.info("Adding new credential: " + body.encode());

            var name = body.getString(NAME);

            var username = body.getString(USERNAME);

            var password = EncryptionUtil.encrypt(body.getString(PASSWORD));

            executeQuery(ADD_CREDENTIAL, List.of(name, username, password))
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rows = result.getJsonArray("rows");

                            if (rows != null && !rows.isEmpty())
                            {
                                var id = rows.getJsonObject(0).getInteger(ID);

                                LOGGER.info("Credential added with ID: " + id);

                                handleCreated(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS).put(ID, id));
                            }
                            else
                            {
                                LOGGER.error("Insert succeeded but no ID returned.");

                                handleMissingData(ctx,"Insert succeeded but no ID returned.");
                            }
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Error while processing result: " + e.getMessage());
                        }

                    })
                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_ADD, cause));
        }
        catch (Exception e)
        {
            LOGGER.error("Error while adding credential: " + e.getMessage());
        }

    }

    @Override
    public void list(RoutingContext ctx)
    {
        try
        {
            LOGGER.info("Fetching credential list");

            executeQuery(GET_ALL_CREDENTIALS)
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rows = result.getJsonArray("rows", new JsonArray());

                            var credentialList = new JsonArray();

                            for (var i = 0; i < rows.size(); i++)
                            {
                                try
                                {
                                    var row = rows.getJsonObject(i);

                                    credentialList.add(new JsonObject()
                                            .put(ID, row.getInteger(ID))
                                            .put(NAME, row.getString(NAME))
                                            .put(USERNAME, row.getString(USERNAME)));
                                }
                                catch (Exception e)
                                {
                                    LOGGER.error("Failed to process row: " + e.getMessage());
                                }
                            }

                            LOGGER.info("Fetched " + credentialList.size() + " credentials");

                            handleSuccess(ctx, new JsonObject().put(CREDENTIALS, credentialList));
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Error while processing result: " + e.getMessage());
                        }
                    })
                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_FETCH, cause));
        }
        catch (Exception e)
        {
            LOGGER.error("Error while fetching credentials: " + e.getMessage());
        }

    }

    @Override
    public void getById(RoutingContext ctx)
    {
        try
        {
            var id = validateIdFromPath(ctx);

            if (id == -1) return;

            LOGGER.info("Fetching credential with ID: " + id);

            executeQuery(GET_CREDENTIAL_BY_ID, List.of(id))
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rows = result.getJsonArray("rows", new JsonArray());

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
                                        .put(USERNAME, row.getString(USERNAME)));
                            }
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Error while processing result: " + e.getMessage());
                        }
                    })
                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_FETCH, cause));
        }
        catch (Exception e)
        {
            LOGGER.error("Error while fetching credential by ID: " + e.getMessage());
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

            if (notValidateCredentialFields(ctx, body)) return;

            var password = EncryptionUtil.encrypt(body.getString(PASSWORD));

            LOGGER.info("Updating credential ID " + id + " with data: " + body.encode());

            executeQuery(UPDATE_CREDENTIAL, List.of(body.getString(NAME), body.getString(USERNAME), password, id))
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rowCount = result.getInteger("rowCount", 0);

                            if (rowCount == 0)
                            {
                                handleNotFound(ctx,new JsonObject().put(ERROR, NOT_FOUND));
                            }
                            else
                            {
                                LOGGER.info("Credential updated successfully for ID " + id);

                                handleSuccess(ctx, new JsonObject().put(MESSAGE, UPDATED_SUCCESS));
                            }
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Error while processing result: " + e.getMessage());
                        }
                    })
                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_UPDATE, cause));
        }
        catch (Exception e)
        {
            LOGGER.error("Error while updating credential: " + e.getMessage());
        }
    }

    @Override
    public void delete(RoutingContext ctx)
    {
        try
        {
            var id = validateIdFromPath(ctx);

            if (id == -1) return;

            LOGGER.info("Deleting credential ID: " + id);

            executeQuery(DELETE_CREDENTIAL, List.of(id))
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rowCount = result.getInteger("rowCount", 0);

                            if (rowCount == 0)
                            {
                                handleNotFound(ctx,new JsonObject().put(ERROR, NOT_FOUND));
                            }
                            else
                            {
                                LOGGER.info("Credential deleted for ID: " + id);

                                handleSuccess(ctx, new JsonObject().put(MESSAGE, DELETED_SUCCESS));
                            }
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Error while processing result: " + e.getMessage());
                        }

                    })
                    .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_DELETE, cause));
        }
        catch (Exception e)
        {
            LOGGER.error("Error while deleting credential: " + e.getMessage());
        }
    }

    private boolean notValidateCredentialFields(RoutingContext ctx, JsonObject body)
    {
        try
        {
            if (body == null)
            {
                handleMissingData(ctx, INVALID_JSON_BODY);

                return true;
            }

            if (body.getString(NAME) == null || body.getString(USERNAME) == null || body.getString(PASSWORD) == null)
            {
                handleMissingData(ctx, MISSING_FIELDS);

                return true;
            }

            return false;
        }
        catch (Exception e)
        {
            LOGGER.error("Error while validating credential fields: " + e.getMessage());

            return true;
        }
    }
}