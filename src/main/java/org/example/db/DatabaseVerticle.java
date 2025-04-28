package org.example.db;

import io.vertx.core.AbstractVerticle;
import io.vertx.serviceproxy.ServiceBinder;

public class DatabaseVerticle extends AbstractVerticle
{

    public static final String SERVICE_ADDRESS = "database.service";

    @Override
    public void start()
    {
        DatabaseService service = DatabaseService.create(vertx);

        new ServiceBinder(vertx)
                .setAddress(SERVICE_ADDRESS)
                .register(DatabaseService.class, service);
    }
}