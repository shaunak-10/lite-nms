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

import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;
import static org.example.constants.AppConstants.Headers.*;

public abstract class AbstractCrudHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCrudHandler.class);

    DatabaseService databaseService = DatabaseService.createProxy(MainApp.getVertx(), DatabaseVerticle.SERVICE_ADDRESS);

    public abstract void add(RoutingContext ctx);

    public abstract void list(RoutingContext ctx);

    public abstract void getById(RoutingContext ctx);

    public abstract void update(RoutingContext ctx);

    public abstract void delete(RoutingContext ctx);


    protected int validateIdFromPath(RoutingContext ctx)
    {
        var idParam = ctx.pathParam("id");

        if (idParam == null)
        {
            sendJsonResponse(ctx, 400, new JsonObject().put(ERROR, INVALID_ID_IN_PATH));

            return -1;
        }

        try
        {
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

    Future<JsonObject> executeQuery(String query, List<Object> params)
    {
        var request = new JsonObject()
                .put("query", query);

        if (params != null && !params.isEmpty())
        {
            request.put("params", new JsonArray(params));
        }

        return databaseService.executeQuery(request);
    }

    Future<JsonObject> executeQuery(String query)
    {
        return executeQuery(query, Collections.emptyList());
    }
}
