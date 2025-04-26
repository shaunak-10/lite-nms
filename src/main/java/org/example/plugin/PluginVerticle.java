package org.example.plugin;

import io.vertx.core.AbstractVerticle;
import io.vertx.serviceproxy.ServiceBinder;

public class PluginVerticle extends AbstractVerticle
{

    public static final String SERVICE_ADDRESS = "plugin.service";

    @Override
    public void start()
    {
        PluginService service = PluginService.create(vertx);

        new ServiceBinder(vertx)
                .setAddress(SERVICE_ADDRESS)
                .register(PluginService.class, service);
    }
}
