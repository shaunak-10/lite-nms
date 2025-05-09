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
import org.example.utils.ConfigLoader;


/**
 * Utility class to manage the PostgreSQL database client using Vert.x.
 * Provides methods to get a shared client, test the connection, and
 * create necessary tables on application startup.
 */
public class DatabaseClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseClient.class);

    private static final Dotenv dotenv = Dotenv.load();

    private static SqlClient client;

    /**
     * Retrieves the singleton instance of {@link SqlClient}.
     * Initializes the client if not already created, using environment variables for configuration.
     *
     * @return The initialized {@link SqlClient} instance for executing queries.
     */
    public static SqlClient getClient()
    {
        if (client == null)
        {
            try
            {
                client = PgBuilder.client()
                        .with(new PoolOptions().setMaxSize(ConfigLoader.get().getInteger("database.pool.size", 5)))
                        .connectingTo(new PgConnectOptions()
                                .setPort(Integer.parseInt(dotenv.get("DB_PORT")))
                                .setHost(dotenv.get("DB_HOST"))
                                .setDatabase(dotenv.get("DB_NAME"))
                                .setUser(dotenv.get("DB_USER"))
                                .setPassword(dotenv.get("DB_PASSWORD"))
                                .setConnectTimeout(ConfigLoader.get().getInteger("database.connection.timeout", 5))
                                .setIdleTimeout(ConfigLoader.get().getInteger("database.idle.timeout", 300)))
                        .build();
            }
            catch (Exception ex)
            {
                LOGGER.error(ex.getMessage());
            }
        }

        return client;
    }

    /**
     * Tests the database connection by executing a simple `SELECT 1` query.
     * Useful for validating connectivity at startup or during health checks.
     *
     * @param resultHandler A handler to process the result: succeeded if connection is OK, failed otherwise.
     */
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

    /**
     * Creates all necessary database tables if they do not already exist.
     * This includes tables for credential profiles, discovery profiles,
     * provisioned devices, polling results, and availability checks.
     *
     * @param resultHandler A handler to process the result once all table creation queries are executed.
     */
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
                password VARCHAR(100) NOT NULL,
                system_type VARCHAR(50) NOT NULL
            );
            """,
                    """
            CREATE TABLE IF NOT EXISTS discovery_profile (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) UNIQUE NOT NULL,
                ip VARCHAR(45) NOT NULL,
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
                  ip VARCHAR(45) UNIQUE NOT NULL,
                  port INTEGER DEFAULT 22,
                  credential_profile_id INTEGER NOT NULL,
                  is_deleted BOOLEAN DEFAULT FALSE,
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
