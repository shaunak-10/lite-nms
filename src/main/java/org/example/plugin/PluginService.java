package org.example.plugin;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;

@ProxyGen
public interface PluginService
{

    static PluginService create(Vertx vertx)
    {
        return new PluginServiceImpl(vertx);
    }

    static PluginService createProxy(Vertx vertx, String address)
    {
        return new PluginServiceVertxEBProxy(vertx, address);
    }

    Future<JsonArray> runSSHReachability(JsonArray devices);

    Future<JsonArray> runSSHMetrics(JsonArray devices);
}
