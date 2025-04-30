package org.example.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import static org.example.constants.AppConstants.DiscoveryQuery.*;
import static org.example.constants.AppConstants.ProvisionField.DISCOVERY_PROFILE_ID;
import static org.example.constants.AppConstants.ProvisionQuery.*;
import static org.example.constants.AppConstants.DiscoveryField.*;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;
import org.example.utils.LoggerUtil;

import java.util.List;
import java.util.logging.Logger;

public class ProvisionHandler extends AbstractCrudHandler
{
    private static final ProvisionHandler INSTANCE = new ProvisionHandler();

    private static final Logger LOGGER = LoggerUtil.getMainLogger();

    private ProvisionHandler() {}

    public static ProvisionHandler getInstance()
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

        var discoveryProfileId = body.getInteger(DISCOVERY_PROFILE_ID);

        if (discoveryProfileId == null)
        {
            handleMissingData(ctx, LOGGER, MISSING_FIELDS);

            return;
        }

        LOGGER.info("Fetching discovery profile with ID: " + discoveryProfileId);

        executeQuery(GET_DISCOVERY_BY_ID, List.of(discoveryProfileId))
                .onSuccess(result ->
                {
                    var rows = result.getJsonArray("rows");

                    if (rows == null || rows.isEmpty())
                    {
                        handleNotFound(ctx, LOGGER);

                        return;
                    }

                    var discoveryProfile = rows.getJsonObject(0);

                    var status = discoveryProfile.getString(STATUS);

                    if (!ACTIVE.equalsIgnoreCase(status))
                    {
                        handleInvalidData(ctx, LOGGER, DEVICE_NOT_DISCOVERED);

                        return;
                    }

                    var name = discoveryProfile.getString(NAME);

                    var ip = discoveryProfile.getString(IP);

                    var port = discoveryProfile.getInteger(PORT);

                    var credentialProfileId = discoveryProfile.getInteger(CREDENTIAL_PROFILE_ID);

                    LOGGER.info("Inserting provisioned device copied from discovery profile ID: " + discoveryProfileId);

                    executeQuery(ADD_PROVISION, List.of(name, ip, port, credentialProfileId))
                            .onSuccess(insertResult ->
                            {
                                var insertRows = insertResult.getJsonArray("rows");

                                if (insertRows != null && !insertRows.isEmpty())
                                {
                                    var id = insertRows.getJsonObject(0).getInteger(ID);

                                    LOGGER.info("Provisioned device added with ID: " + id);

                                    handleCreated(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS).put(ID, id));
                                }
                                else
                                {
                                    handleSuccess(ctx, new JsonObject().put(MESSAGE, ADDED_SUCCESS));
                                }
                            })
                            .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_ADD, cause));
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, cause));
    }

    @Override
    public void list(RoutingContext ctx)
    {
        LOGGER.info("Fetching provisioned device list");

        executeQuery(GET_ALL_PROVISIONS)
                .onSuccess(result ->
                {
                    var rows = result.getJsonArray("rows", new JsonArray());

                    var provisionList = new JsonArray();

                    for (var i = 0; i < rows.size(); i++)
                    {
                        var row = rows.getJsonObject(i);

                        var provision = new JsonObject()
                                .put(ID, row.getInteger(ID))
                                .put(NAME, row.getString(NAME))
                                .put(IP, row.getString(IP))
                                .put(PORT, row.getInteger(PORT))
                                .put(CREDENTIAL_PROFILE_ID, row.getInteger(CREDENTIAL_PROFILE_ID))
                                .put("polling_results", row.getJsonArray("polling_results", new JsonArray()));

                        provisionList.add(provision);
                    }

                    LOGGER.info("Fetched " + provisionList.size() + " provisioned devices");

                    handleSuccess(ctx, new JsonObject().put("provisions", provisionList));
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, cause));
    }

    @Override
    public void getById(RoutingContext ctx)
    {
        var id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("Fetching provisioned device with ID: " + id);

        executeQuery(GET_PROVISION_BY_ID, List.of(id))
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

                        var provision = new JsonObject()
                                .put(ID, row.getInteger(ID))
                                .put(NAME, row.getString(NAME))
                                .put(IP, row.getString(IP))
                                .put(PORT, row.getInteger(PORT))
                                .put(CREDENTIAL_PROFILE_ID, row.getInteger(CREDENTIAL_PROFILE_ID))
                                .put("polling_results", row.getJsonArray("polling_results", new JsonArray()));

                        handleSuccess(ctx, provision);
                    }
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_FETCH, cause));
    }



    @Override
    public void update(RoutingContext ctx)
    {
        handleInvalidOperation(ctx, LOGGER, UPDATE_NOT_ALLOWED);
    }

    @Override
    public void delete(RoutingContext ctx)
    {
        var id = validateIdFromPath(ctx);

        if (id == -1) return;

        LOGGER.info("Deleting provisioned device with ID: " + id);

        executeQuery(DELETE_PROVISION, List.of(id))
                .onSuccess(result ->
                {
                    var rowCount = result.getInteger("rowCount", 0);

                    if (rowCount == 0)
                    {
                        handleNotFound(ctx, LOGGER);
                    }
                    else
                    {
                        LOGGER.info("Provisioned device deleted with ID: " + id);

                        handleSuccess(ctx, new JsonObject().put(MESSAGE, DELETED_SUCCESS));
                    }
                })
                .onFailure(cause -> handleDatabaseError(ctx, LOGGER, FAILED_TO_DELETE, cause));
    }
}
