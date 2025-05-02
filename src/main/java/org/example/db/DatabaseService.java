package org.example.db;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface DatabaseService
{
    static DatabaseService create()
    {
        return new DatabaseServiceImpl();
    }

    static DatabaseService createProxy(Vertx vertx, String address)
    {
        return new DatabaseServiceVertxEBProxy(vertx, address);
    }

    Future<JsonObject> executeQuery(JsonObject request);

    Future<JsonObject> executeBatch(JsonObject request);
}