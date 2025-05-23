package org.example.services.db;

import io.vertx.core.Future;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;

import static org.example.constants.AppConstants.FALSE;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.TRUE;

public class DatabaseServiceImpl implements DatabaseService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseServiceImpl.class);

    private static final SqlClient dbClient = DatabaseClient.getClient();

    @Override
    public Future<JsonObject> executeQuery(JsonObject request)
    {
        if(dbClient == null)
        {
            LOGGER.error("Database client is not initialized.");

            return Future.failedFuture(
                    String.valueOf(new JsonObject()
                            .put(SUCCESS, FALSE)
                            .put(ERROR, "Database client is not initialized"))
            );
        }
        else
        {
            try
            {
                var query = request.getString(QUERY);

                LOGGER.trace("Executing query: " + query);

                if (request.containsKey(PARAMS))
                {
                    var params = Tuple.tuple();

                    for (var param : request.getJsonArray(PARAMS))
                    {
                        params.addValue(param);
                    }

                    return dbClient.preparedQuery(query)
                            .execute(params)
                            .map(this::processQueryResult)
                            .recover(this::handleQueryError);
                }
                else
                {
                    return dbClient.preparedQuery(query)
                            .execute()
                            .map(this::processQueryResult)
                            .recover(this::handleQueryError);
                }
            }
            catch (Exception exception)
            {
                LOGGER.error("Error executing query: " + exception.getMessage());

                return Future.failedFuture(
                        String.valueOf(new JsonObject()
                                .put(SUCCESS, FALSE)
                                .put(ERROR, exception.getMessage()))
                );
            }

        }
    }

    @Override
    public Future<JsonObject> executeBatch(JsonObject request)
    {
        try
        {
            if (request.getJsonArray(PARAMS) == null || request.getJsonArray(PARAMS).isEmpty())
            {
                return Future.failedFuture(
                        String.valueOf(new JsonObject()
                                .put(SUCCESS, FALSE)
                                .put(ERROR, "No parameters provided"))
                );
            }

            var batchParams = new ArrayList<Tuple>();

            for (var param : request.getJsonArray(PARAMS))
            {
                var tuple = Tuple.tuple();

                for (var value : (JsonArray) param)
                {
                    tuple.addValue(value);
                }

                batchParams.add(tuple);
            }

            return dbClient.preparedQuery(request.getString(QUERY))
                    .executeBatch(batchParams)
                    .map(this::processQueryResult)
                    .recover(this::handleQueryError);
        }
        catch (Exception exception)
        {
            LOGGER.error("Error executing batch query: " + exception.getMessage());

            return Future.failedFuture(
                    String.valueOf(new JsonObject()
                            .put(SUCCESS, FALSE)
                            .put(ERROR, exception.getMessage()))
            );
        }

    }

    /**
     * Processes the result of a SQL query and converts it to a JSON object.
     *
     * @param result the result set from the database query
     * @return a JsonObject containing "success", "rowCount", and optionally a "rows" array with row data
     */
    private JsonObject processQueryResult(RowSet<Row> result)
    {
        var response = new JsonObject()
                .put(SUCCESS, TRUE)
                .put(ROW_COUNT, result.rowCount());

        if (result.size() == 0)
        {
            return response;
        }

        var rows = new JsonArray();

        for (var row : result)
        {
            try
            {
                var jsonRow = new JsonObject();

                for (var i = 0; i < row.size(); i++)
                {
                    try
                    {
                        jsonRow.put(row.getColumnName(i), row.getValue(i));
                    }
                    catch (Exception exception)
                    {
                        LOGGER.error(exception.getMessage());
                    }

                }

                rows.add(jsonRow);
            }
            catch (Exception exception)
            {
                LOGGER.error(exception.getMessage());
            }
        }

        if (!rows.isEmpty())
        {
            response.put(ROWS, rows);
        }

        return response;
    }

    /**
     * Handles errors encountered during database query execution.
     *
     * @param error the Throwable representing the cause of the failure
     * @return a failed Future containing a JSON-formatted error response
     */
    private Future<JsonObject> handleQueryError(Throwable error)
    {
        LOGGER.error("Database query failed: " + error.getMessage());

        return Future.failedFuture(
                String.valueOf(new JsonObject()
                        .put(SUCCESS, FALSE)
                        .put(ERROR, error.getMessage()))
        );
    }
}