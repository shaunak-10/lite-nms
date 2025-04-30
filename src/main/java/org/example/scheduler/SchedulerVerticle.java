package org.example.scheduler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.serviceproxy.ServiceBinder;
import org.example.utils.ConfigLoader;
import org.example.utils.LoggerUtil;

import static org.example.constants.AppConstants.PingConstants.PING_COMMAND;

public class SchedulerVerticle extends AbstractVerticle
{
    public static final String SERVICE_ADDRESS = "scheduler.service";

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            new ServiceBinder(vertx)
                    .setAddress(SERVICE_ADDRESS)
                    .register(SchedulerService.class, SchedulerService.create(vertx));

            SchedulerService.create(vertx).startPolling(ConfigLoader.get().getInteger("polling-interval"));

            startPromise.complete();
        }
        catch (Exception e)
        {
            LoggerUtil.getMainLogger().severe(e.getMessage());

            startPromise.fail(e);
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise)
    {
        LoggerUtil.getConsoleLogger().info("Stopping SchedulerVerticle");

        stopPromise.complete();
    }
}
