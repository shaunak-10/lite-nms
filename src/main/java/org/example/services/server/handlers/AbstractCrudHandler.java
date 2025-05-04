package org.example.services.server.handlers;

import io.vertx.core.Future;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.example.services.db.DatabaseService;
import org.example.services.db.DatabaseVerticle;

import java.util.Collections;
import java.util.List;

import static org.example.MainApp.vertx;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;
import static org.example.constants.AppConstants.Headers.*;

public abstract class AbstractCrudHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCrudHandler.class);

    DatabaseService databaseService = DatabaseService.createProxy(vertx, DatabaseVerticle.SERVICE_ADDRESS);

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
            ctx.response()
                    .setStatusCode(400)
                    .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .end(new JsonObject().put(ERROR, INVALID_ID_IN_PATH).encodePrettily());

            return -1;
        }

        try
        {
            return Integer.parseInt(idParam);
        }
        catch (Exception e)
        {
            ctx.response()
                    .setStatusCode(400)
                    .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .end(new JsonObject().put(ERROR, INVALID_ID_IN_PATH).encode());

            return -1;
        }
    }

    protected void handleDatabaseError(RoutingContext ctx, String message, Throwable cause)
    {
        LOGGER.error(message + ": " + cause.getMessage());

        ctx.response()
                .setStatusCode(500)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(new JsonObject()
                        .put(ERROR, message)
                        .put(DETAILS, cause.getMessage())
                        .encodePrettily());
    }

    protected void handleNotFound(RoutingContext ctx)
    {
        LOGGER.warn(org.example.constants.AppConstants.Message.NOT_FOUND);

        ctx.response()
                .setStatusCode(404)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(new JsonObject().put(ERROR, org.example.constants.AppConstants.Message.NOT_FOUND).encodePrettily());
    }

    protected void handleMissingData(RoutingContext ctx, String message)
    {
        LOGGER.warn(message);

        ctx.response()
                .setStatusCode(400)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(new JsonObject().put(ERROR, message).encodePrettily());
    }

    protected void handleSuccess(RoutingContext ctx, JsonObject response)
    {
        ctx.response()
                .setStatusCode(200)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(response.encodePrettily());
    }

    protected void handleCreated(RoutingContext ctx, JsonObject response)
    {
        ctx.response()
                .setStatusCode(201)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(response.encodePrettily());
    }

    protected void handleInvalidData(RoutingContext ctx, String message)
    {
        LOGGER.warn(message);

        ctx.response()
                .setStatusCode(400)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(new JsonObject().put(ERROR, message).encodePrettily());
    }

    protected void handleInvalidOperation(RoutingContext ctx, String message)
    {
        LOGGER.warn(message);

        ctx.response()
                .setStatusCode(405)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(new JsonObject().put(ERROR, message).encodePrettily());
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

    public Future<JsonObject> executeBatch(String query, List<List<Object>> paramsList)
    {
        var request = new JsonObject()
                .put("query", query);

        var paramsArray = new JsonArray();

        for (var params : paramsList)
        {
            var paramArray = new JsonArray(params);

            paramsArray.add(paramArray);
        }

        request.put("params", paramsArray);

        return databaseService.executeBatch(request);
    }
}
