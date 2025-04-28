package org.example.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import org.example.db.DatabaseClient;
import org.example.utils.EncryptionUtil;
import org.example.utils.LoggerUtil;

import java.util.logging.Logger;

import static org.example.constants.AppConstants.CredentialQuery.*;
import static org.example.constants.AppConstants.CredentialField.*;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;

public class CredentialHandler extends AbstractCrudHandler
{
    private static final CredentialHandler INSTANCE = new CredentialHandler();

    private static final Logger LOGGER = LoggerUtil.getDatabaseLogger();

    private static final SqlClient DATABASE_CLIENT = DatabaseClient.getClient();

    private CredentialHandler() {}

    public static CredentialHandler getInstance()
    {
        return INSTANCE;
    }

    @Override
    public void add(RoutingContext ctx)
    {
        JsonObject body = ctx.body().asJsonObject();

        if (notValidateCredentialFields(ctx, body)) return;

        LOGGER.info("Adding new credential: " + body.encode());

        String name = body.getString(NAME);

        String username = body.getString(USERNAME);

        String password = body.getString(PASSWORD);

        try
        {
            password = EncryptionUtil.encrypt(password);
        }
        catch (Exception e)
        {
            LOGGER.severe(e.getMessage());
        }

        DATABASE_CLIENT
                .preparedQuery(ADD_CREDENTIAL)
                .execute(Tuple.of(name, username, password), databaseResponse ->
                {
                    if (databaseResponse.succeeded())
                    {
                        int id = databaseResponse.result().iterator().next().getInteger(ID);

                        LOGGER.info("Credential added with ID: " + id);

                        handleCreated(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS).put(ID, id));
                    }
                    else
                    {
                        handleDatabaseError(ctx, LOGGER, FAILED_TO_ADD, databaseResponse.cause());
                    }
                });
    }

    @Override
    public void list(RoutingContext ctx)
    {
        LOGGER.info("Fetching credential list");

        DATABASE_CLIENT
                .preparedQuery(GET_ALL_CREDENTIALS)
                .execute(databaseResponse ->
                {
                    if (databaseResponse.succeeded())
                    {
                        JsonArray credentialList = new JsonArray();

                        for (Row row : databaseResponse.result())
                        {
                            credentialList.add(new JsonObject()
                                    .put(ID, row.getInteger(ID))
                                    .put(NAME, row.getString(NAME))
                                    .put(USERNAME, row.getString(USERNAME)));
                        }

                        LOGGER.info("Fetched " + credentialList.size() + " credentials");

                        handleSuccess(ctx, new JsonObject().put(CREDENTIALS, credentialList));
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

        LOGGER.info("Fetching credential with ID: " + id);

        DATABASE_CLIENT
                .preparedQuery(GET_CREDENTIAL_BY_ID)
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
                                    .put(USERNAME, row.getString(USERNAME)));
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
        int id = validateIdFromPath(ctx);

        if (id == -1) return;

        JsonObject body = ctx.body().asJsonObject();

        if (notValidateCredentialFields(ctx, body)) return;

        String name = body.getString(NAME);
        String username = body.getString(USERNAME);
        String password = body.getString(PASSWORD);

        try
        {
            password = EncryptionUtil.encrypt(password);
        }
        catch (Exception e)
        {
            LOGGER.severe(e.getMessage());
        }

        LOGGER.info("Updating credential ID " + id + " with data: " + body.encode());

        DATABASE_CLIENT
                .preparedQuery(UPDATE_CREDENTIAL)
                .execute(Tuple.of(name, username, password, id), updateRes ->
                {
                    if (updateRes.succeeded())
                    {
                        if (updateRes.result().rowCount() == 0)
                        {
                            handleNotFound(ctx, LOGGER);
                        }
                        else
                        {
                            LOGGER.info("Credential updated successfully for ID " + id);

                            handleSuccess(ctx, new JsonObject().put(MESSAGE, UPDATED_SUCCESS));
                        }
                    }
                    else
                    {
                        handleDatabaseError(ctx, LOGGER, FAILED_TO_UPDATE, updateRes.cause());
                    }
                });
    }

    @Override
    public void delete(RoutingContext ctx)
    {
        int id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("Deleting credential ID: " + id);

        DATABASE_CLIENT
                .preparedQuery(DELETE_CREDENTIAL)
                .execute(Tuple.of(id), res ->
                {
                    if (res.succeeded())
                    {
                        if (res.result().rowCount() == 0)
                        {
                            handleNotFound(ctx, LOGGER);
                        }
                        else
                        {
                            LOGGER.info("Credential deleted for ID: " + id);

                            handleSuccess(ctx, new JsonObject().put(MESSAGE, DELETED_SUCCESS));
                        }
                    }
                    else
                    {
                        handleDatabaseError(ctx, LOGGER, FAILED_TO_DELETE, res.cause());
                    }
                });
    }

    private boolean notValidateCredentialFields(RoutingContext ctx, JsonObject body)
    {
        if (body == null)
        {
            handleMissingData(ctx, LOGGER, INVALID_JSON_BODY);

            return true;
        }

        String name = body.getString(NAME);

        String username = body.getString(USERNAME);

        String password = body.getString(PASSWORD);

        if (name == null || username == null || password == null)
        {
            handleMissingData(ctx, LOGGER, MISSING_FIELDS);

            return true;
        }

        return false;
    }

}