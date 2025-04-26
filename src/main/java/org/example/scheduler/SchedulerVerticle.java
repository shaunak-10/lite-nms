package org.example.scheduler;

import io.vertx.core.AbstractVerticle;
import io.vertx.serviceproxy.ServiceBinder;

public class SchedulerVerticle extends AbstractVerticle
{
    public static final String SERVICE_ADDRESS = "scheduler.service";

    @Override
    public void start()
    {
        SchedulerService service = SchedulerService.create(vertx);

        new ServiceBinder(vertx)
                .setAddress(SERVICE_ADDRESS)
                .register(SchedulerService.class, service);
    }
}
