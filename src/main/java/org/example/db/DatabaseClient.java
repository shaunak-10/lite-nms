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

                var poolOptions = new PoolOptions()
                        .setMaxSize(10);

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
}
