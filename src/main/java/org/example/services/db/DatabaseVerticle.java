package org.example.services.db;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.serviceproxy.ServiceBinder;

public class DatabaseVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseVerticle.class);

    public static final String SERVICE_ADDRESS = "database.service";

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            new ServiceBinder(vertx)
                    .setAddress(SERVICE_ADDRESS)
                    .register(DatabaseService.class, DatabaseService.create());

            startPromise.complete();
        }
        catch (Exception e)
        {

            LOGGER.error(e.getMessage());

            startPromise.fail(e);
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise)
    {
        LOGGER.info("Stopping DatabaseVerticle");

        stopPromise.complete();
    }
}