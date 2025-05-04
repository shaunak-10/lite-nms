package org.example;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import org.example.db.DatabaseClient;
import org.example.db.DatabaseVerticle;
import org.example.plugin.PluginVerticle;
import org.example.scheduler.SchedulerVerticle;
import org.example.server.HttpServerVerticle;
import org.example.utils.ConfigLoader;

import java.util.List;

import static org.example.constants.AppConstants.AddressesAndPaths.CONFIG_FILE_PATH;

public class MainApp
{
    public static Vertx vertx = Vertx.vertx();

    private static final Logger LOGGER = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args)
    {
        try
        {
            DatabaseClient.testConnection(dbRes ->
            {
                ConfigLoader.init(CONFIG_FILE_PATH);

                if (dbRes.failed())
                {
                    LOGGER.error("❌ Failed to connect to DB: " + dbRes.cause());

                    vertx.close();

                    return;
                }

                LOGGER.info("✅ Database connected successfully!");

                DatabaseClient.createTablesIfNotExist(tableRes ->
                {
                    if (tableRes.failed())
                    {
                        LOGGER.error("❌ Failed to create tables: " + tableRes.cause().getMessage());

                        vertx.close();

                        return;
                    }

                    LOGGER.info("📦 Tables created or already exist.");

                    deployAllVerticles(vertx)
                            .onSuccess(v -> LOGGER.info("🚀 All verticles deployed successfully!"))
                            .onFailure(err -> {
                                LOGGER.error("❌ Failed to deploy verticles: " + err.getMessage());
                                vertx.close();
                            });
                });
            });
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage());
        }
    }

    private static Future<Object> deployAllVerticles(Vertx vertx)
    {
        var verticles = List.of(
                DatabaseVerticle.class,
                PluginVerticle.class,
                SchedulerVerticle.class,
                HttpServerVerticle.class
        );

        var chain = Future.succeededFuture();

        for (var verticle : verticles)
        {
            chain = chain.compose(ignored ->
                    vertx.deployVerticle(verticle.getName())
                            .onSuccess(id ->
                                    LOGGER.info("✅ Deployed: " + verticle.getSimpleName()))
                            .mapEmpty()
            );
        }
        return chain;
    }
}
