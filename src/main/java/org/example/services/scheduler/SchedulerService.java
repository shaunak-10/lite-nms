package org.example.services.scheduler;

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

    Future<String> startPolling(int interval);
}
