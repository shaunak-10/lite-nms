package org.example.db;

import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import io.vertx.pgclient.PgBuilder;
import org.example.utils.LoggerUtil;

public class DatabaseClient
{
    private static final Dotenv dotenv = Dotenv.load();

    private static SqlClient client;

    public static SqlClient getClient()
    {
        if (client == null)
        {
            try
            {
                var connectOptions = new PgConnectOptions()
                        .setPort(Integer.parseInt(dotenv.get("DB_PORT")))
                        .setHost(dotenv.get("DB_HOST"))
                        .setDatabase(dotenv.get("DB_NAME"))
                        .setUser(dotenv.get("DB_USER"))
                        .setPassword(dotenv.get("DB_PASSWORD"))
                        .setConnectTimeout(5000)
                        .setIdleTimeout(5 * 60 * 1000);

                var poolOptions = new PoolOptions().setMaxSize(10);

                client = PgBuilder.client()
                        .with(poolOptions)
                        .connectingTo(connectOptions)
                        .build();
            }
            catch (Exception ex)
            {
                LoggerUtil.getMainLogger().severe(ex.getMessage());
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
        var statements = new String[]{
                """
            CREATE TABLE IF NOT EXISTS credential_profile (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) UNIQUE NOT NULL,
                username VARCHAR(100) NOT NULL,
                password VARCHAR(100) NOT NULL
            )
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
            )
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
            )
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
            )
            """
        };

        SqlClient sqlClient = getClient();

        executeSequentially(sqlClient, statements, 0, resultHandler);
    }

    private static void executeSequentially(SqlClient client, String[] queries, int index, Handler<AsyncResult<Void>> resultHandler)
    {
        if (index >= queries.length)
        {
            resultHandler.handle(Future.succeededFuture());

            return;
        }

        client.query(queries[index]).execute(ar ->
        {
            if (ar.succeeded())
            {
                executeSequentially(client, queries, index + 1, resultHandler);
            }
            else
            {
                LoggerUtil.getMainLogger().severe("Failed to execute query: " + ar.cause().getMessage());
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }
}
