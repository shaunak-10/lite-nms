package org.example;

import io.vertx.core.Vertx;
import org.example.db.DatabaseClient;
import org.example.verticles.HttpServerVerticle;
import org.example.verticles.PluginVerticle;
import org.example.verticles.SchedulerVerticle;

public class MainApp {
    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

        DatabaseClient.testConnection(databaseResponse ->
        {
            if (databaseResponse.succeeded())
            {
                System.out.println("✅ Database connected successfully!");

                vertx.deployVerticle(SchedulerVerticle.class.getName(), res ->{
                    if (res.succeeded())
                    {
                        System.out.println("🚀 Scheduler verticle deployed!");
                    }
                    else
                    {
                        System.err.println("❌ Failed to deploy verticle: " + res.cause());
                    }
                });

                vertx.deployVerticle(PluginVerticle.class.getName(), res ->
                {
                    if (res.succeeded())
                    {
                        System.out.println("🚀 Plugin verticle deployed!");
                    }
                    else
                    {
                        System.err.println("❌ Failed to deploy verticle: " + res.cause());
                    }
                });

                vertx.deployVerticle(HttpServerVerticle.class.getName(), res ->
                {
                    if (res.succeeded())
                    {
                        System.out.println("🚀 HTTP server verticle deployed!");
                    }
                    else
                    {
                        System.err.println("❌ Failed to deploy verticle: " + res.cause());
                    }
                });
            }
            else
            {
                System.err.println("❌ Failed to connect to database: " + databaseResponse.cause().getMessage());

                vertx.close();
            }
        });
    }
}
