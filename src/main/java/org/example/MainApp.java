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

        DatabaseClient.testConnection(dbRes ->
        {
            if (dbRes.failed())
            {
                System.err.println("❌ Failed to connect to DB: " + dbRes.cause());

                vertx.close();

                return;
            }

            System.out.println("✅ Database connected successfully!");

            vertx.deployVerticle(PluginVerticle.class.getName(), pluginRes ->
            {
                if (pluginRes.failed())
                {
                    System.err.println("❌ Plugin verticle failed: " + pluginRes.cause());

                    vertx.close();

                    return;
                }

                vertx.deployVerticle(SchedulerVerticle.class.getName(), schedRes ->
                {
                    if (schedRes.failed())
                    {
                        System.err.println("❌ Plugin verticle failed: " + schedRes.cause());

                        vertx.close();

                        return;
                    }

                    vertx.deployVerticle(new HttpServerVerticle(), httpRes ->
                    {
                        if (httpRes.failed())
                        {
                            System.err.println("❌ HTTP server verticle failed: " + httpRes.cause());

                            vertx.close();
                        }
                        else
                        {
                            System.out.println("🚀 All verticles deployed successfully!");
                        }
                    });
                });
            });
        });

    }
}
