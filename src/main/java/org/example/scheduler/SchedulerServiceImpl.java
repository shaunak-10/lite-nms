package org.example.scheduler;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import org.example.db.DatabaseClient;
import org.example.plugin.PluginService;
import org.example.plugin.PluginVerticle;
import org.example.utils.DecryptionUtil;
import org.example.utils.LoggerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.example.constants.AppConstants.ProvisionQuery.DATA_TO_PLUGIN_FOR_POLLING;
import static org.example.constants.AppConstants.ProvisionQuery.INSERT_POLLING_RESULT;
import static org.example.constants.AppConstants.ProvisionField.*;
import static org.example.constants.AppConstants.CredentialField.USERNAME;
import static org.example.constants.AppConstants.CredentialField.PASSWORD;

public class SchedulerServiceImpl implements SchedulerService
{

    private static final Logger LOGGER = LoggerUtil.getDatabaseLogger();

    private static final SqlClient DATABASE_CLIENT = DatabaseClient.getClient();

    private final PluginService pluginService;

    private long pollingTimerId = -1;

    private final Vertx vertx;

    public SchedulerServiceImpl(Vertx vertx)
    {
        this.pluginService = PluginService.createProxy(vertx, PluginVerticle.SERVICE_ADDRESS);

        this.vertx = vertx;
    }

    @Override
    public Future<String> startPolling(int interval)
    {
        if (pollingTimerId != -1)
        {
            LOGGER.warning("Polling already running. Ignoring new start request.");

            return Future.failedFuture("Polling already running");
        }

        pollingTimerId = vertx.setPeriodic(interval, id ->
        {
            LOGGER.info("Running scheduled polling task");

            DATABASE_CLIENT
                    .preparedQuery(DATA_TO_PLUGIN_FOR_POLLING)
                    .execute(dbRes ->
                    {
                        if (dbRes.failed())
                        {
                            LOGGER.warning("DB error during polling: " + dbRes.cause().getMessage());

                            return;
                        }

                        JsonArray devices = new JsonArray();

                        for (Row row : dbRes.result())
                        {
                            try
                            {
                                JsonObject device = new JsonObject()
                                        .put(ID, row.getInteger(ID))
                                        .put(PORT, row.getInteger(PORT))
                                        .put(IP, row.getString(IP))
                                        .put(USERNAME, row.getString(USERNAME))
                                        .put(PASSWORD, DecryptionUtil.decrypt(row.getString(PASSWORD)));

                                devices.add(device);
                            }
                            catch (Exception e)
                            {
                                LOGGER.warning("Failed to process device: " + e.getMessage());
                            }
                        }

                        if (devices.isEmpty())
                        {
                            LOGGER.info("No devices found for polling");

                            return;
                        }

                        pluginService.runSSHMetrics(devices)
                                .onSuccess(metricsResults ->
                                {
                                    LOGGER.info("Polling completed. Received " + metricsResults.size() + " results.");

                                    List<Tuple> batchParams = new ArrayList<>();

                                    for (int i = 0; i < metricsResults.size(); i++)
                                    {
                                        JsonObject result = metricsResults.getJsonObject(i);

                                        int deviceId = result.getInteger("id");

                                        JsonObject metrics = result.copy();

                                        metrics.remove("id");

                                        batchParams.add(Tuple.of(deviceId, metrics.encode()));
                                    }

                                    DATABASE_CLIENT
                                            .preparedQuery(INSERT_POLLING_RESULT)
                                            .executeBatch(batchParams, batchRes ->
                                            {
                                                if (batchRes.failed())
                                                {
                                                    LOGGER.warning("Batch insert of polling results failed: " + batchRes.cause().getMessage());
                                                }
                                                else
                                                {
                                                    LOGGER.info("Successfully inserted " + batchParams.size() + " polling results.");
                                                }
                                            });

                                })
                                .onFailure(err -> LOGGER.warning("Plugin polling failed: " + err.getMessage()));
                    });
        });

        LOGGER.info("Polling scheduled every " + interval + "ms");

        return Future.succeededFuture("Polling scheduled");
    }

    @Override
    public Future<String> stopPolling()
    {
        if (pollingTimerId != -1)
        {
            vertx.cancelTimer(pollingTimerId);

            pollingTimerId = -1;

            LOGGER.info("Polling timer cancelled");

            return Future.succeededFuture("Polling stopped");
        }
        else
        {
            LOGGER.info("No polling timer to stop");

            return Future.failedFuture("No polling to stop");
        }
    }
}
