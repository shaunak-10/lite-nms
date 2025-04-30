package org.example.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.example.utils.EncryptionUtil;
import org.example.utils.LoggerUtil;

import java.util.List;
import java.util.logging.Logger;

import static org.example.constants.AppConstants.CredentialQuery.*;
import static org.example.constants.AppConstants.CredentialField.*;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;

public class CredentialHandler extends AbstractCrudHandler
{
    private static final Logger LOGGER = LoggerUtil.getMainLogger();

    private static final CredentialHandler INSTANCE = new CredentialHandler();

    private CredentialHandler() {}

    public static CredentialHandler getInstance()
    {
        return INSTANCE;
    }

    @Override
    public void add(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        if (notValidateCredentialFields(ctx, body)) return;

        LOGGER.info("Adding new credential: " + body.encode());

        var name = body.getString(NAME);

        var username = body.getString(USERNAME);

        var password = body.getString(PASSWORD);

        try
        {
            password = EncryptionUtil.encrypt(password);
        }
        catch (Exception e)
        {
            LOGGER.severe(e.getMessage());
        }

        executeQuery(ADD_CREDENTIAL, List.of(name, username, password))
                .onSuccess(result ->
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
                        handleSuccess(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS));
                    }
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_ADD, cause));
    }

    @Override
    public void list(RoutingContext ctx)
    {
        LOGGER.info("Fetching credential list");

        executeQuery(GET_ALL_CREDENTIALS)
                .onSuccess(result ->
                {
                    var rows = result.getJsonArray("rows", new JsonArray());

                    var credentialList = new JsonArray();

                    for (var i = 0; i < rows.size(); i++)
                    {
                        var row = rows.getJsonObject(i);

                        credentialList.add(new JsonObject()
                                .put(ID, row.getInteger(ID))
                                .put(NAME, row.getString(NAME))
                                .put(USERNAME, row.getString(USERNAME)));
                    }

                    LOGGER.info("Fetched " + credentialList.size() + " credentials");

                    handleSuccess(ctx, new JsonObject().put(CREDENTIALS, credentialList));
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, cause));
    }

    @Override
    public void getById(RoutingContext ctx)
    {
        var id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("Fetching credential with ID: " + id);

        executeQuery(GET_CREDENTIAL_BY_ID, List.of(id))
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
                                .put(USERNAME, row.getString(USERNAME)));
                    }
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, cause));
    }

    @Override
    public void update(RoutingContext ctx)
    {
        var id = validateIdFromPath(ctx);

        if (id == -1) return;

        var body = ctx.body().asJsonObject();

        if (notValidateCredentialFields(ctx, body)) return;

        var name = body.getString(NAME);

        var username = body.getString(USERNAME);

        var password = body.getString(PASSWORD);

        try
        {
            password = EncryptionUtil.encrypt(password);
        }
        catch (Exception e)
        {
            LOGGER.severe(e.getMessage());
        }

        LOGGER.info("Updating credential ID " + id + " with data: " + body.encode());

        executeQuery(UPDATE_CREDENTIAL, List.of(name, username, password, id))
                .onSuccess(result ->
                {
                    var rowCount = result.getInteger("rowCount", 0);

                    if (rowCount == 0)
                    {
                        handleNotFound(ctx, LOGGER);
                    }
                    else
                    {
                        LOGGER.info("Credential updated successfully for ID " + id);

                        handleSuccess(ctx, new JsonObject().put(MESSAGE, UPDATED_SUCCESS));
                    }
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_UPDATE, cause));
    }

    @Override
    public void delete(RoutingContext ctx)
    {
        var id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("Deleting credential ID: " + id);

        executeQuery(DELETE_CREDENTIAL, List.of(id))
                .onSuccess(result ->
                {
                    var rowCount = result.getInteger("rowCount", 0);

                    if (rowCount == 0)
                    {
                        handleNotFound(ctx, LOGGER);
                    }
                    else
                    {
                        LOGGER.info("Credential deleted for ID: " + id);

                        handleSuccess(ctx, new JsonObject().put(MESSAGE, DELETED_SUCCESS));
                    }
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_DELETE, cause));
    }

    private boolean notValidateCredentialFields(RoutingContext ctx, JsonObject body)
    {
        if (body == null)
        {
            handleMissingData(ctx, LOGGER, INVALID_JSON_BODY);

            return true;
        }

        var name = body.getString(NAME);

        var username = body.getString(USERNAME);

        var password = body.getString(PASSWORD);

        if (name == null || username == null || password == null)
        {
            handleMissingData(ctx, LOGGER, MISSING_FIELDS);

            return true;
        }

        return false;
    }
}