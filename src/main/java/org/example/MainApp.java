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
    static
    {
        ConfigLoader.init(CONFIG_FILE_PATH);
    }

    private static final Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(ConfigLoader.get().getInteger("vertx.worker.pool.size")));

    private static final Logger LOGGER = LoggerFactory.getLogger(MainApp.class);

    public static Vertx getVertx()
    {
        return vertx;
    }

    public static void main(String[] args)
    {
        try
        {
            DatabaseClient.testConnection(dbRes ->
            {
                try
                {
                    if (dbRes.failed())
                    {
                        LOGGER.error("❌ Failed to connect to DB: " + dbRes.cause());

                        DatabaseClient.close()
                                .compose(v -> vertx.close());

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

                                DatabaseClient.close()
                                        .compose(v -> vertx.close());

                                return;
                            }

                            LOGGER.info("📦 Tables created or already exist.");

                            deployAllVerticles()
                                    .onSuccess(v -> LOGGER.info("🚀 All verticles deployed successfully!"))
                                    .onFailure(error ->
                                    {
                                        LOGGER.error("❌ Failed to deploy verticles: " + error.getMessage());

                                        DatabaseClient.close()
                                                .compose(v -> vertx.close());
                                    });
                        }
                        catch (Exception exception)
                        {
                            LOGGER.error("❌ Failed to create tables: " + exception.getMessage());

                            DatabaseClient.close()
                                    .compose(v -> vertx.close());
                        }
                    });
                }
                catch (Exception exception)
                {
                    LOGGER.error(exception.getMessage());

                    DatabaseClient.close()
                            .compose(v -> vertx.close());
                }
            });
        }
        catch (Exception exception)
        {
            LOGGER.error(exception.getMessage());

            DatabaseClient.close()
                    .compose(v -> vertx.close());
        }
    }

    private static Future<Object> deployAllVerticles()
    {
        try
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
        catch (Exception exception)
        {
            return Future.failedFuture(exception);
        }

    }
}
