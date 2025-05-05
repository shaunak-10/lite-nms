package org.example.services.db;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Service interface for executing database queries and batch operations asynchronously using Vert.x.
 * This service is designed to be accessed via the Vert.x Event Bus using service proxies.
 */
@ProxyGen
public interface DatabaseService
{
    /**
     * Creates a new instance of the {@link DatabaseService} implementation.
     * This method is used on the provider side (usually inside the DatabaseVerticle).
     *
     * @return a new instance of {@link DatabaseServiceImpl}.
     */
    static DatabaseService create()
    {
        return new DatabaseServiceImpl();
    }

    /**
     * Creates a proxy for the {@link DatabaseService} that communicates over the Vert.x Event Bus.
     * This is typically used by clients that want to interact with the service via message passing.
     *
     * @param vertx   the Vertx instance
     * @param address the event bus address where the service is published
     * @return a proxy to the {@link DatabaseService}
     */
    static DatabaseService createProxy(Vertx vertx, String address)
    {
        return new DatabaseServiceVertxEBProxy(vertx, address);
    }

    /**
     * Executes a single SQL query using the provided request object.
     * The request should contain necessary fields such as `query` and optionally `params`.
     *
     * Example request:
     * <pre>
     * {
     *   "query": "SELECT * FROM credential_profile WHERE id = $1",
     *   "params": [1]
     * }
     * </pre>
     *
     * @param request a JsonObject containing the query and parameters
     * @return a future with the result of the query as a JsonObject
     */
    Future<JsonObject> executeQuery(JsonObject request);

    /**
     * Executes multiple SQL statements as a batch using the provided request object.
     * The request should include a batch query and an array of parameter sets.
     *
     * Example request:
     * <pre>
     * {
     *   "query": "INSERT INTO polling_result (provisioned_device_id, metrics) VALUES ($1, $2)",
     *   "paramsList": [
     *     [1, {"cpu": 70, "mem": 60}],
     *     [2, {"cpu": 80, "mem": 50}]
     *   ]
     * }
     * </pre>
     *
     * @param request a JsonObject containing the batch query and parameters list
     * @return a future with the batch execution result as a JsonObject
     */
    Future<JsonObject> executeBatch(JsonObject request);
}