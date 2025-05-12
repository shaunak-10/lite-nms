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
import static org.example.constants.AppConstants.DiscoveryField.PORT;
import static org.example.constants.AppConstants.DiscoveryField.IP;
import static org.example.constants.AppConstants.DiscoveryField.CREDENTIAL_PROFILE_ID;
import static org.example.constants.AppConstants.DiscoveryField.DISCOVERY;
import static org.example.constants.AppConstants.ProvisionField.DISCOVERY_PROFILE_ID;
import static org.example.constants.AppConstants.CredentialField.*;
import static org.example.constants.AppConstants.FALSE;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.Message.*;
import static org.example.constants.AppConstants.Headers.*;
import static org.example.constants.AppConstants.ProvisionField.PROVISION;
import static org.example.constants.AppConstants.TRUE;

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
     * Gets a string value from a JsonObject, strictly checking for String type.
     *
     * @param json The JsonObject to extract from
     * @param field The field name to retrieve
     * @return The String value, or null if not found or not a String
     */
    private String getStringValue(JsonObject json, String field)
    {
        try
        {
            var value = json.getValue(field);

            if (value instanceof String stringValue && !stringValue.isEmpty())
            {
                return stringValue;
            }

            return null;
        }
        catch (Exception exception)
        {
            LOGGER.warn("Error extracting string value for field " + field + ": " + exception.getMessage());

            return null;
        }
    }

    /**
     * Gets an integer value from a JsonObject, strictly checking for Integer type.
     *
     * @param json The JsonObject to extract from
     * @param field The field name to retrieve
     * @return The Integer value, or null if not found or not an Integer
     */
    private Integer getIntegerValue(JsonObject json, String field)
    {
        try
        {
            var value = json.getValue(field);

            if (value instanceof Integer intValue)
            {
                return intValue;
            }

            return null;
        }
        catch (Exception exception)
        {
            LOGGER.warn("Error extracting integer value for field " + field + ": " + exception.getMessage());

            return null;
        }
    }

    /**
     * Validates the JSON body based on the specified type.
     * Performs robust validation checks for required fields and data types according to the schema.
     * Handles type mismatches and conversion errors gracefully.
     *
     * @param ctx the routing context for handling error responses
     * @param body the JSON body to validate
     * @param type the type of validation to perform (e.g., "credential" or "discovery")
     * @return false if validation passes, true if validation fails (to maintain compatibility with existing methods)
     */
    protected boolean isBodyValid(RoutingContext ctx, JsonObject body, String type)
    {
        try
        {
            if (body == null)
            {
                handleMissingData(ctx, INVALID_JSON_BODY);

                return FALSE;
            }

            switch (type.toLowerCase())
            {
                case CREDENTIAL:
                    // Validate credential fields: name, username, password, system_type (all required and must be strings)

                    var name = getStringValue(body, NAME);

                    var username = getStringValue(body, USERNAME);

                    var password = getStringValue(body, PASSWORD);

                    var systemType = getStringValue(body, SYSTEM_TYPE);

                    if (name == null || username == null || password == null || systemType == null)
                    {
                        handleMissingData(ctx, MISSING_FIELDS);

                        return FALSE;
                    }

                    break;

                case DISCOVERY:
                    // Validate discovery fields: name (string), ip (string), credential_profile_id (integer)

                    var discoveryName = getStringValue(body, NAME);

                    var ip = getStringValue(body, IP);

                    var credentialProfileId = getIntegerValue(body, CREDENTIAL_PROFILE_ID);

                    if (discoveryName == null || ip == null)
                    {
                        handleMissingData(ctx, MISSING_FIELDS);

                        return FALSE;
                    }

                    if (credentialProfileId == null || credentialProfileId <= 0)
                    {
                        handleInvalidData(ctx, "Invalid credential profile ID");

                        return FALSE;
                    }

                    // Validate port if present
                    if (body.containsKey(PORT))
                    {
                        var port = getIntegerValue(body, PORT);

                        if (port == null || port < 1 || port > 65535)
                        {
                            handleInvalidData(ctx, INVALID_PORT);

                            return FALSE;
                        }
                    }

                    break;

                case PROVISION:
                    // Validate provision fields: discovery_profile_id (integer)

                    var discoveryProfileId = getIntegerValue(body, DISCOVERY_PROFILE_ID);

                    if (discoveryProfileId == null || discoveryProfileId <= 0)
                    {
                        handleInvalidData(ctx, "Invalid discovery profile ID");

                        return FALSE;
                    }

                    break;

                default:

                    LOGGER.error("Unknown validation type: " + type);

                    handleMissingData(ctx, "Unknown validation type");

                    return FALSE;
            }

            return TRUE;
        }
        catch (Exception exception)
        {
            LOGGER.error("Error while validating " + type + " fields: " + exception.getMessage());

            handleMissingData(ctx, "Error during validation: " + exception.getMessage());

            return FALSE;
        }
    }


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
        catch (Exception exception)
        {
            LOGGER.error(exception.getMessage());

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
        catch (Exception exception)
        {
            LOGGER.error("Failed to send JSON response: " + exception.getMessage());
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
        try
        {
            var request = new JsonObject()
                    .put(QUERY, query);

            if (params != null && !params.isEmpty())
            {
                request.put(PARAMS, new JsonArray(params));
            }

            return databaseService.executeQuery(request);
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to execute query: " + exception.getMessage());

            return Future.failedFuture(
                    String.valueOf(new JsonObject()
                            .put(SUCCESS, FALSE)
                            .put(ERROR, exception.getMessage()))
            );
        }

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
