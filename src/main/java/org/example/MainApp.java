package org.example;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import org.example.services.db.DatabaseClient;
import org.example.services.db.DatabaseVerticle;
import org.example.services.discovery.DiscoveryVerticle;
import org.example.services.scheduler.SchedulerVerticle;
import org.example.services.server.HttpServerVerticle;
import org.example.utils.ConfigLoader;

import java.util.List;

import static org.example.constants.AppConstants.AddressesAndPaths.CONFIG_FILE_PATH;

public class MainApp
{
    private static final int VERTX_WORKER_POOL_SIZE = 10;

    private static final Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(VERTX_WORKER_POOL_SIZE));

    private static final Logger LOGGER = LoggerFactory.getLogger(MainApp.class);

    public static Vertx getVertx()
    {
        return vertx;
    }

    public static void main(String[] args)
    {
        try
        {
            ConfigLoader.init(CONFIG_FILE_PATH);

            DatabaseClient.testConnection(dbRes ->
            {
                try
                {
                    if (dbRes.failed())
                    {
                        LOGGER.error("❌ Failed to connect to DB: " + dbRes.cause());

                        vertx.close();

                        return;
                    }

                    LOGGER.info("✅ Database connected successfully!");

                    DatabaseClient.createTablesIfNotExist(tableRes ->
                    {
                        try
                        {
                            if (tableRes.failed())
                            {
                                LOGGER.error("❌ Failed to create tables: " + tableRes.cause().getMessage());

                                vertx.close();

                                return;
                            }

                            LOGGER.info("📦 Tables created or already exist.");

                            deployAllVerticles()
                                    .onSuccess(v -> LOGGER.info("🚀 All verticles deployed successfully!"))
                                    .onFailure(err ->
                                    {
                                        LOGGER.error("❌ Failed to deploy verticles: " + err.getMessage());

                                        vertx.close();
                                    });
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("❌ Failed to create tables: " + e.getMessage());

                            vertx.close();
                        }
                    });
                }
                catch (Exception e)
                {
                    LOGGER.error("❌ Failed to load config file: " + e.getMessage());

                    vertx.close();
                }
            });
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage());
        }
    }

    private static Future<Object> deployAllVerticles()
    {
        var verticles = List.of(
                DatabaseVerticle.class,
                SchedulerVerticle.class,
                DiscoveryVerticle.class,
                HttpServerVerticle.class
        );

        var chain = Future.succeededFuture();

        for (var verticle : verticles)
        {
            chain = chain.compose(ignored ->
                    MainApp.vertx.deployVerticle(verticle.getName())
                            .onSuccess(id ->
                                    LOGGER.info("✅ Deployed: " + verticle.getSimpleName()))
                            .mapEmpty()
            );
        }
        return chain;
    }
}
