package org.example.services.db;

import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import io.vertx.pgclient.PgBuilder;


public class DatabaseClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseClient.class);

    private static final Dotenv dotenv = Dotenv.load();

    private static SqlClient client;

    public static SqlClient getClient()
    {
        if (client == null)
        {
            try
            {
                client = PgBuilder.client()
                        .with(new PoolOptions().setMaxSize(10))
                        .connectingTo(new PgConnectOptions()
                                .setPort(Integer.parseInt(dotenv.get("DB_PORT")))
                                .setHost(dotenv.get("DB_HOST"))
                                .setDatabase(dotenv.get("DB_NAME"))
                                .setUser(dotenv.get("DB_USER"))
                                .setPassword(dotenv.get("DB_PASSWORD"))
                                .setConnectTimeout(5000)
                                .setIdleTimeout(5 * 60 * 1000))
                        .build();
            }
            catch (Exception ex)
            {
                LOGGER.error(ex.getMessage());
            }
        }

        return client;
    }

    public static void testConnection(Handler<AsyncResult<Void>> resultHandler)
    {
        try
        {
            var client = getClient().query("SELECT 1");

            if (client != null)
            {
                client.execute(ar ->
                {
                    if (ar.succeeded())
                    {
                        resultHandler.handle(Future.succeededFuture());
                    }
                    else
                    {
                        resultHandler.handle(Future.failedFuture(ar.cause()));
                    }
                });
            }
        }
        catch (Exception e)
        {
            resultHandler.handle(Future.failedFuture(e));
        }
    }

    public static void createTablesIfNotExist(Handler<AsyncResult<Void>> resultHandler)
    {
        try
        {
            var future = Future.succeededFuture((Void)null);

            for (var query : new String[]{
                    """
            CREATE TABLE IF NOT EXISTS credential_profile (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) UNIQUE NOT NULL,
                username VARCHAR(100) NOT NULL,
                password VARCHAR(100) NOT NULL
            );
            """,
                    """
            CREATE TABLE IF NOT EXISTS discovery_profile (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) UNIQUE NOT NULL,
                ip VARCHAR(45) UNIQUE NOT NULL,
                port INTEGER DEFAULT 22,
                status VARCHAR(50) DEFAULT 'inactive',
                credential_profile_id INTEGER NOT NULL,
                FOREIGN KEY (credential_profile_id)
                    REFERENCES credential_profile(id)
                    ON DELETE RESTRICT
            );
            """,
                    """
            CREATE TABLE IF NOT EXISTS provisioned_device (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) UNIQUE NOT NULL,
                ip VARCHAR(45) NOT NULL,
                port INTEGER DEFAULT 22,
                credential_profile_id INTEGER NOT NULL,
                FOREIGN KEY (credential_profile_id)
                    REFERENCES credential_profile(id)
                    ON DELETE RESTRICT
            );
            """,
                    """
            CREATE TABLE IF NOT EXISTS polling_result (
                id SERIAL PRIMARY KEY,
                provisioned_device_id INTEGER NOT NULL,
                polled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                metrics JSONB NOT NULL,
                FOREIGN KEY (provisioned_device_id)
                    REFERENCES provisioned_device(id)
                    ON DELETE CASCADE
            );
            """,
                    """
            CREATE TABLE IF NOT EXISTS availability (
                 id SERIAL PRIMARY KEY,
                 provisioned_device_id INTEGER NOT NULL,
                 checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                 was_available BOOLEAN NOT NULL,
                 FOREIGN KEY (provisioned_device_id)
                     REFERENCES provisioned_device(id)
                     ON DELETE CASCADE
             );
            """
            })
            {
                future = future.compose(v -> getClient().query(query).execute().mapEmpty());
            }

            future.onComplete(resultHandler);
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage());
        }

    }
}
