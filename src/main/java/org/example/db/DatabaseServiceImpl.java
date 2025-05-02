package org.example.db;

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
import java.util.List;

public class DatabaseServiceImpl implements DatabaseService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseServiceImpl.class);

    private static final SqlClient dbClient = DatabaseClient.getClient();

    @Override
    public Future<JsonObject> executeQuery(JsonObject request)
    {
        var query = request.getString("query");

        LOGGER.trace("Executing query: " + query);

        if (request.containsKey("params"))
        {
            var paramsArray = request.getJsonArray("params");

            var params = Tuple.tuple();

            for (var param : paramsArray)
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

    @Override
    public Future<JsonObject> executeBatch(JsonObject request)
    {
        var query = request.getString("query");

        var paramsArray = request.getJsonArray("params");

        if (paramsArray == null || paramsArray.isEmpty())
        {
            return Future.failedFuture(
                    String.valueOf(new JsonObject()
                            .put("success", false)
                            .put("error", "No parameters provided"))
            );
        }

        List<Tuple> batchParams = new ArrayList<>();

        for (var param : paramsArray)
        {
            var paramArray = (JsonArray) param;

            var tuple = Tuple.tuple();

            for (var value : paramArray)
            {
                tuple.addValue(value);
            }

            batchParams.add(tuple);
        }

        return dbClient.preparedQuery(query)
                .executeBatch(batchParams)
                .map(this::processQueryResult)
                .recover(this::handleQueryError);
    }

    private JsonObject processQueryResult(RowSet<Row> result)
    {
        var response = new JsonObject()
                .put("success", true)
                .put("rowCount", result.rowCount());

        if (result.size() == 0)
        {
            return response;
        }

        var rows = new JsonArray();

        for (var row : result)
        {
            var jsonRow = new JsonObject();

            for (var i = 0; i < row.size(); i++)
            {
                var columnName = row.getColumnName(i);

                var value = row.getValue(i);

                jsonRow.put(columnName, value);
            }

            rows.add(jsonRow);
        }

        if (!rows.isEmpty())
        {
            response.put("rows", rows);
        }

        return response;
    }

    private Future<JsonObject> handleQueryError(Throwable error)
    {
        LOGGER.error("Database query failed: " + error.getMessage());

        return Future.failedFuture(
                String.valueOf(new JsonObject()
                        .put("success", false)
                        .put("error", error.getMessage()))
        );
    }
}