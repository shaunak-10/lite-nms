package org.example.scheduler;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

@ProxyGen
public interface SchedulerService
{

    static SchedulerService create(Vertx vertx)
    {
        return new SchedulerServiceImpl(vertx);
    }

    static SchedulerService createProxy(Vertx vertx, String address)
    {
        return new SchedulerServiceVertxEBProxy(vertx, address);
    }

    Future<String> startPolling(int interval);

    Future<String> stopPolling();
}
