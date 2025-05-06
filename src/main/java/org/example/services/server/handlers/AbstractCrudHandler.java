package org.example.services.server.handlers;

import io.vertx.core.Future;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.example.MainApp;
import org.example.services.db.DatabaseService;
import org.example.services.db.DatabaseVerticle;

import java.util.Collections;
import java.util.List;

import static org.example.constants.AppConstants.DiscoveryField.ID;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;
import static org.example.constants.AppConstants.Headers.*;

/**
 * Abstract base class for handling CRUD operations in a REST API.
 * Provides utility methods for handling common request and response operations
 */
public abstract class AbstractCrudHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCrudHandler.class);

    DatabaseService databaseService = DatabaseService.createProxy(MainApp.getVertx(), DatabaseVerticle.SERVICE_ADDRESS);

    /**
     * Handles adding a new resource.
     *
     * @param ctx the routing context
     */
    public abstract void add(RoutingContext ctx);

    /**
     * Handles retrieving a list of all resources.
     *
     * @param ctx the routing context
     */
    public abstract void list(RoutingContext ctx);

    /**
     * Handles retrieving a resource by its ID.
     *
     * @param ctx the routing context
     */
    public abstract void getById(RoutingContext ctx);

    /**
     * Handles updating a resource by its ID.
     *
     * @param ctx the routing context
     */
    public abstract void update(RoutingContext ctx);

    /**
     * Handles deleting a resource by its ID.
     *
     * @param ctx the routing context
     */
    public abstract void delete(RoutingContext ctx);


    /**
     * Validates and parses the ID from the path parameters.
     *
     * @param ctx the RoutingContext containing the request data
     * @return the parsed ID, or -1 if the ID is invalid
     */
    protected int validateIdFromPath(RoutingContext ctx)
    {
        try
        {
            var idParam = ctx.pathParam(ID);

            if (idParam == null)
            {
                sendJsonResponse(ctx, 400, new JsonObject().put(ERROR, INVALID_ID_IN_PATH));

                return -1;
            }

            return Integer.parseInt(idParam);
        }
        catch (Exception e)
        {
            sendJsonResponse(ctx, 400, new JsonObject().put(ERROR, INVALID_ID_IN_PATH));

            return -1;
        }
    }

    protected void handleDatabaseError(RoutingContext ctx, String message, Throwable cause)
    {
        LOGGER.error(message + ": " + cause.getMessage());

        sendJsonResponse(ctx, 500, new JsonObject()
                .put(ERROR, message)
                .put(DETAILS, cause.getMessage()));
    }

    protected void handleNotFound(RoutingContext ctx, JsonObject response)
    {
        LOGGER.warn(NOT_FOUND);

        sendJsonResponse(ctx, 404, response);
    }

    protected void handleMissingData(RoutingContext ctx, String message)
    {
        LOGGER.warn(message);

        sendJsonResponse(ctx, 400, new JsonObject().put(ERROR, message));
    }

    protected void handleSuccess(RoutingContext ctx, JsonObject response) {
        sendJsonResponse(ctx, 200, response);
    }

    protected void handleCreated(RoutingContext ctx, JsonObject response)
    {
        sendJsonResponse(ctx, 201, response);
    }

    protected void handleInvalidData(RoutingContext ctx, String message)
    {
        LOGGER.warn(message);

        sendJsonResponse(ctx, 400, new JsonObject().put(ERROR, message));
    }

    protected void handleInvalidOperation(RoutingContext ctx, String message)
    {
        LOGGER.warn(message);

        sendJsonResponse(ctx, 405, new JsonObject().put(ERROR, message));
    }

    /**
     * Sends a JSON response to the client with the specified status code and body.
     *
     * @param ctx the RoutingContext containing the request data
     * @param statusCode the HTTP status code to send
     * @param body the JSON body to send in the response
     */
    protected void sendJsonResponse(RoutingContext ctx, int statusCode, JsonObject body)
    {
        try
        {
            ctx.response()
                    .setStatusCode(statusCode)
                    .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .end(body.encodePrettily());
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to send JSON response: " + e.getMessage());
        }
    }

    /**
     * Executes a query with the specified SQL query and parameters.
     *
     * @param query the SQL query to execute
     * @param params the parameters to bind to the query
     * @return a Future representing the result of the query execution
     */
    Future<JsonObject> executeQuery(String query, List<Object> params)
    {
        var request = new JsonObject()
                .put(QUERY, query);

        if (params != null && !params.isEmpty())
        {
            request.put(PARAMS, new JsonArray(params));
        }

        return databaseService.executeQuery(request);
    }

    /**
     * Executes a query with the specified SQL query.
     *
     * @param query the SQL query to execute
     * @return a Future representing the result of the query execution
     */
    Future<JsonObject> executeQuery(String query)
    {
        return executeQuery(query, Collections.emptyList());
    }
}
