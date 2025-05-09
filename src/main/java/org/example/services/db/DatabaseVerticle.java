package org.example.services.db;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.serviceproxy.ServiceBinder;

/**
 * Verticle responsible for registering the {@link DatabaseService} on the event bus.
 *
 * <p>
 * This verticle uses Vert.x Service Proxy mechanism to expose the {@link DatabaseService}
 * implementation, allowing other verticles to communicate with the database layer
 * in a decoupled and asynchronous manner via the event bus.
 * </p>
 *
 * <p>
 * The service is registered at the address {@link #SERVICE_ADDRESS}, and consumers
 * can access it using {@code DatabaseService.createProxy(vertx, SERVICE_ADDRESS)}.
 * </p>
 */
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