package org.example.services.server.handlers;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.example.MainApp;
import org.example.services.scheduler.SchedulerService;
import org.example.services.scheduler.SchedulerVerticle;

import static org.example.constants.AppConstants.DiscoveryQuery.*;
import static org.example.constants.AppConstants.ProvisionField.AVAILABILITY_PERCENT_RESPONSE;
import static org.example.constants.AppConstants.ProvisionField.AVAILABILITY_PERCENT;
import static org.example.constants.AppConstants.ProvisionField.POLLING_RESULTS_RESPONSE;
import static org.example.constants.AppConstants.ProvisionField.POLLING_RESULTS;
import static org.example.constants.AppConstants.ProvisionField.DISCOVERY_PROFILE_ID;
import static org.example.constants.AppConstants.ProvisionField.IS_DELETED;
import static org.example.constants.AppConstants.ProvisionField.IS_POLLING;
import static org.example.constants.AppConstants.ProvisionQuery.*;
import static org.example.constants.AppConstants.DiscoveryField.*;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;

import java.util.List;

public class ProvisionHandler extends AbstractCrudHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionHandler.class);

    private static final ProvisionHandler INSTANCE = new ProvisionHandler();

    SchedulerService schedulerService;

    private ProvisionHandler()
    {
        schedulerService = SchedulerService.createProxy(MainApp.getVertx(), SchedulerVerticle.SERVICE_ADDRESS);
    }

    public static ProvisionHandler getInstance()
    {
        return INSTANCE;
    }

    @Override
    public void add(RoutingContext ctx)
    {
        try
        {
            var body = ctx.body().asJsonObject();

            if (body == null)
            {
                handleMissingData(ctx, INVALID_JSON_BODY);

                return;
            }

            var discoveryProfileId = body.getInteger(DISCOVERY_PROFILE_ID);

            if (discoveryProfileId == null)
            {
                handleMissingData(ctx, MISSING_FIELDS);

                return;
            }

            LOGGER.info("Fetching discovery profile with ID: " + discoveryProfileId);

            executeQuery(GET_DISCOVERY_BY_ID, List.of(discoveryProfileId))
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rows = result.getJsonArray(ROWS);

                            if (rows == null || rows.isEmpty())
                            {
                                handleNotFound(ctx,new JsonObject().put(ERROR, NOT_FOUND));

                                return;
                            }

                            var discoveryProfile = rows.getJsonObject(0);

                            if (!ACTIVE.equalsIgnoreCase(discoveryProfile.getString(STATUS)))
                            {
                                handleInvalidData(ctx, DEVICE_NOT_DISCOVERED);

                                return;
                            }

                            LOGGER.info("Inserting provisioned device copied from discovery profile ID: " + discoveryProfileId);

                            executeQuery("SELECT id, is_deleted FROM provisioned_device WHERE ip = $1", List.of(discoveryProfile.getString(IP)))
                                    .onSuccess(checkDevice ->
                                    {
                                        try
                                        {
                                            if(checkDevice.getInteger(ROW_COUNT)==0)
                                            {
                                                executeQuery(ADD_PROVISION, List.of(discoveryProfile.getString(NAME),
                                                        discoveryProfile.getString(IP),
                                                        discoveryProfile.getInteger(PORT),
                                                        discoveryProfile.getInteger(CREDENTIAL_PROFILE_ID)))
                                                        .onSuccess(insertResult ->
                                                                databaseAddSuccess(ctx, insertResult))
                                                        .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_ADD, cause));
                                            }
                                            else if(!checkDevice.getJsonArray(ROWS).getJsonObject(0).getBoolean(IS_DELETED))
                                            {
                                                handleInvalidOperation(ctx,"Device already provisioned");
                                            }
                                            else
                                            {
                                                executeQuery(RE_PROVISION, List.of(discoveryProfile.getString(NAME),
                                                        discoveryProfile.getInteger(PORT),
                                                        discoveryProfile.getInteger(CREDENTIAL_PROFILE_ID),
                                                        discoveryProfile.getString(IP)))
                                                        .onSuccess(insertResult ->
                                                                databaseAddSuccess(ctx, insertResult))
                                                        .onFailure(cause -> handleDatabaseError(ctx, FAILED_TO_ADD, cause));
                                            }
                                        }
                                        catch (Exception e)
                                        {
                                            LOGGER.error("Error while processing result: " + e.getMessage());
                                        }
                                    });


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
            LOGGER.error("Error while adding provisioned device: " + e.getMessage());
        }

    }

    @Override
    public void list(RoutingContext ctx)
    {
        try
        {
            LOGGER.info("Fetching provisioned device list");

            executeQuery(GET_ALL_PROVISIONS)
                    .onSuccess(result ->
                    {
                        try
                        {
                            var rows = result.getJsonArray(ROWS, new JsonArray());

                            var provisionList = new JsonArray();

                            for (var i = 0; i < rows.size(); i++)
                            {
                                try
                                {
                                    var row = rows.getJsonObject(i);

                                    var provision = new JsonObject()
                                            .put(ID, row.getInteger(ID))
                                            .put(NAME, row.getString(NAME))
                                            .put(IP, row.getString(IP))
                                            .put(PORT, row.getInteger(PORT))
                                            .put(IS_POLLING, !row.getBoolean(IS_DELETED))
                                            .put(CREDENTIAL_PROFILE_ID_RESPONSE, row.getInteger(CREDENTIAL_PROFILE_ID))
                                            .put(AVAILABILITY_PERCENT_RESPONSE, row.getDouble(AVAILABILITY_PERCENT, 0.0))
                                            .put(POLLING_RESULTS_RESPONSE, row.getJsonArray(POLLING_RESULTS, new JsonArray()));

                                    provisionList.add(provision);
                                }
                                catch (Exception e)
                                {
                                    LOGGER.error("Failed to process provisioned device: " + e.getMessage());
                                }
                            }

                            LOGGER.info("Fetched " + provisionList.size() + " provisioned devices");

                            handleSuccess(ctx, new JsonObject().put("provisions", provisionList));
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
            LOGGER.error("Error while fetching provisioned devices: " + e.getMessage());
        }

    }

    @Override
    public void getById(RoutingContext ctx)
    {
        try
        {
            var id = validateIdFromPath(ctx);

            if (id == -1) return;

            LOGGER.info("Fetching provisioned device with ID: " + id);

            executeQuery(GET_PROVISION_BY_ID, List.of(id))
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

                                var provision = new JsonObject()
                                        .put(ID, row.getInteger(ID))
                                        .put(NAME, row.getString(NAME))
                                        .put(IP, row.getString(IP))
                                        .put(PORT, row.getInteger(PORT))
                                        .put(IS_POLLING, !row.getBoolean(IS_DELETED))
                                        .put(CREDENTIAL_PROFILE_ID_RESPONSE, row.getInteger(CREDENTIAL_PROFILE_ID))
                                        .put(AVAILABILITY_PERCENT_RESPONSE, row.getDouble(AVAILABILITY_PERCENT, 0.0))
                                        .put(POLLING_RESULTS_RESPONSE, row.getJsonArray(POLLING_RESULTS, new JsonArray()));

                                handleSuccess(ctx, provision);
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
            LOGGER.error("Error while fetching provisioned device by ID: " + e.getMessage());
        }

    }

    @Override
    public void update(RoutingContext ctx)
    {
        handleInvalidOperation(ctx, UPDATE_NOT_ALLOWED);
    }

    @Override
    public void delete(RoutingContext ctx)
    {
        try
        {
            var id = validateIdFromPath(ctx);

            if (id == -1) return;

            LOGGER.info("Deleting provisioned device with ID: " + id);

            executeQuery(DELETE_PROVISION, List.of(id))
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
                                LOGGER.info("Provisioned device deleted with ID: " + id);

                                schedulerService.removeEntry(id);

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
            LOGGER.error("Error while deleting provisioned device: " + e.getMessage());
        }
    }

    private void databaseAddSuccess(RoutingContext ctx, JsonObject insertResult)
    {
        try
        {
            var insertRows = insertResult.getJsonArray(ROWS);

            if (insertRows != null && !insertRows.isEmpty())
            {
                var id = insertRows.getJsonObject(0).getInteger(ID);

                LOGGER.info("Provisioned device added with ID: " + id);

                schedulerService.addEntry(id);

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
    }
}
