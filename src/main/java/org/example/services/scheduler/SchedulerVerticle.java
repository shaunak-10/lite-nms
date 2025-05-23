package org.example.services.scheduler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.serviceproxy.ServiceBinder;
import org.example.utils.ConfigLoader;

/**
 * Verticle responsible for scheduling periodic polling tasks.
 */
public class SchedulerVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerVerticle.class);

    public static final String SERVICE_ADDRESS = "scheduler.service";

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            new ServiceBinder(vertx)
                    .setAddress(SERVICE_ADDRESS)
                    .register(SchedulerService.class, SchedulerService.create(vertx));

            SchedulerService.create(vertx).startPolling(ConfigLoader.get().getInteger("scheduler.interval",10000));

            startPromise.complete();
        }
        catch (Exception exception)
        {
            LOGGER.error(exception.getMessage());

            startPromise.fail(exception);
        }
    }

    @Override
    public void stop()
    {
        LOGGER.info("Stopping SchedulerVerticle");
    }
}
