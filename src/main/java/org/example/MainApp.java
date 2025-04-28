package org.example;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.example.db.DatabaseClient;
import org.example.verticles.HttpServerVerticle;
import org.example.plugin.PluginVerticle;
import org.example.scheduler.SchedulerVerticle;

import java.util.ArrayList;
import java.util.List;

public class MainApp
{
    public static Vertx vertx = Vertx.vertx();

    public static void main(String[] args)
    {
        DatabaseClient.testConnection(dbRes ->
        {
            if (dbRes.failed())
            {
                System.err.println("❌ Failed to connect to DB: " + dbRes.cause());

                vertx.close();

                return;
            }

            System.out.println("✅ Database connected successfully!");

            deployAllVerticles(vertx)
                    .onSuccess(v -> System.out.println("🚀 All verticles deployed successfully!"))
                    .onFailure(err ->
                    {
                        System.err.println("❌ Failed to deploy verticles: " + err);

                        vertx.close();
                    });
        });
    }

    private static Future<Void> deployAllVerticles(Vertx vertx)
    {
        final List<String> deployedIds = new ArrayList<>();

        return vertx.deployVerticle(PluginVerticle.class.getName())
                .compose(pluginId ->
                {
                    deployedIds.add(pluginId);

                    return vertx.deployVerticle(SchedulerVerticle.class.getName());
                })
                .compose(schedulerId ->
                {
                    deployedIds.add(schedulerId);

                    return vertx.deployVerticle(HttpServerVerticle.class.getName());
                })
                .compose(httpId ->
                {
                    deployedIds.add(httpId);

                    return Future.succeededFuture();

                })
                .recover(error ->
                {
                    System.err.println("Deployment failed: " + error.getMessage());

                    System.out.println("Undeploying " + deployedIds.size() + " previously deployed verticles...");

                    Future<Void> undeployFuture = Future.succeededFuture();

                    for (String id : deployedIds)
                    {
                        undeployFuture = undeployFuture.compose(v -> vertx.undeploy(id)
                                .onSuccess(v2 -> System.out.println("Undeployed verticle: " + id))
                                .onFailure(err -> System.err.println("Failed to undeploy verticle " + id + ": " + err.getMessage())));
                    }

                    return undeployFuture.compose(v -> Future.failedFuture(error));
                })
                .mapEmpty();
    }
}
