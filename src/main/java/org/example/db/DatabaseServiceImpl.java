package org.example.db;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import org.example.utils.LoggerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class DatabaseServiceImpl implements DatabaseService
{

    private static final Logger LOGGER = LoggerUtil.getMainLogger();

    private final SqlClient dbClient;

    public DatabaseServiceImpl(Vertx vertx)
    {
        this.dbClient = DatabaseClient.getClient();
    }

    @Override
    public Future<JsonObject> executeQuery(JsonObject request)
    {
        String query = request.getString("query");

        LOGGER.info("Executing query: " + query);

        if (request.containsKey("params"))
        {
            JsonArray paramsArray = request.getJsonArray("params");

            Tuple params = Tuple.tuple();

            for (Object param : paramsArray)
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
        String query = request.getString("query");

        JsonArray paramsArray = request.getJsonArray("params");

        if (paramsArray == null || paramsArray.isEmpty())
        {
            return Future.failedFuture(
                    String.valueOf(new JsonObject()
                            .put("success", false)
                            .put("error", "No parameters provided"))
            );
        }

        List<Tuple> batchParams = new ArrayList<>();

        for (Object param : paramsArray)
        {
            JsonArray paramArray = (JsonArray) param;

            Tuple tuple = Tuple.tuple();

            for (Object value : paramArray)
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
        JsonObject response = new JsonObject()
                .put("success", true)
                .put("rowCount", result.rowCount());

        // check if the result is empty and add try catch in the loop

        JsonArray rows = new JsonArray();

        for (Row row : result)
        {
            JsonObject jsonRow = new JsonObject();

            for (int i = 0; i < row.size(); i++)
            {
                String columnName = row.getColumnName(i);

                Object value = row.getValue(i);

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
        LOGGER.severe("Database query failed: " + error.getMessage());

        return Future.failedFuture(
                String.valueOf(new JsonObject()
                        .put("success", false)
                        .put("error", error.getMessage()))
        );
    }
}