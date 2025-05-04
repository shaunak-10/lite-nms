package org.example.services.plugin;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.serviceproxy.ServiceBinder;

public class PluginVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginVerticle.class);

    public static final String SERVICE_ADDRESS = "plugin.service";

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            new ServiceBinder(vertx)
                    .setAddress(SERVICE_ADDRESS)
                    .register(PluginService.class, PluginService.create());

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
        LOGGER.info("PluginVerticle stopped");

        stopPromise.complete();
    }
}
