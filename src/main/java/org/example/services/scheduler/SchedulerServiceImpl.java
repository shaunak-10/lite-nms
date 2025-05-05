package org.example.services.scheduler;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.services.db.DatabaseService;
import org.example.services.db.DatabaseVerticle;
import org.example.utils.ConnectivityUtil;
import org.example.utils.PluginOperationsUtil;
import org.example.utils.DecryptionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.example.constants.AppConstants.ProvisionField.*;
import static org.example.constants.AppConstants.CredentialField.USERNAME;
import static org.example.constants.AppConstants.CredentialField.PASSWORD;
import static org.example.constants.AppConstants.ProvisionQuery.*;
import static org.example.utils.ConnectivityUtil.CheckType;

public class SchedulerServiceImpl implements SchedulerService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerServiceImpl.class);

    private final DatabaseService databaseService;

    private long pollingTimerId = -1;

    private final Vertx vertx;

    /**
     * Implementation of {@link SchedulerService} that manages
     * periodic polling of provisioned devices.
     * This class handles retrieving devices from the database,
     * running connectivity checks (ping and port), performing SSH
     * metric collection using the plugin, and writing results back
     * to the database.
     */
    public SchedulerServiceImpl(Vertx vertx)
    {
        this.databaseService = DatabaseService.createProxy(vertx, DatabaseVerticle.SERVICE_ADDRESS);

        this.vertx = vertx;
    }

    @Override
    public Future<String> startPolling(int interval)
    {
        try
        {
            if (pollingTimerId != -1)
            {
                LOGGER.warn("Polling already running. Ignoring new start request.");

                return Future.failedFuture("Polling already running");
            }

            pollingTimerId = vertx.setPeriodic(interval, id ->
            {
                LOGGER.info("Running scheduled polling task");

                var request = new JsonObject()
                        .put("query", DATA_TO_PLUGIN_FOR_POLLING)
                        .put("params", Collections.emptyList());

                databaseService.executeQuery(request)
                        .onSuccess(dbResponse ->
                        {
                            if (!dbResponse.getBoolean("success"))
                            {
                                LOGGER.warn("DB query failed: " + dbResponse.getString("error"));

                                return;
                            }

                            var devices = new JsonArray();

                            for (var rowObj : dbResponse.getJsonArray("rows", new JsonArray()))
                            {
                                var row = (JsonObject) rowObj;

                                try
                                {
                                    var device = new JsonObject()
                                            .put(ID, row.getInteger(ID))
                                            .put(PORT, row.getInteger(PORT))
                                            .put(IP, row.getString(IP))
                                            .put(USERNAME, row.getString(USERNAME))
                                            .put(PASSWORD, DecryptionUtil.decrypt(row.getString(PASSWORD)));

                                    devices.add(device);
                                }
                                catch (Exception e)
                                {
                                    LOGGER.error("Failed to process device: " + e.getMessage());
                                }
                            }

                            if (devices.isEmpty())
                            {
                                LOGGER.info("No devices found for polling");

                                return;
                            }

                            vertx.executeBlocking(() ->
                                    {
                                        try
                                        {
                                            // PING check
                                            var pingResults = ConnectivityUtil.filterReachableDevices(devices, CheckType.PING);

                                            if (pingResults.isEmpty())
                                            {
                                                LOGGER.info("No devices responded to ping. Skipping further checks.");

                                                // Create availability params for unreachable devices
                                                return getEntries(devices);
                                            }

                                            // PORT check
                                            var portResults = ConnectivityUtil.filterReachableDevices(pingResults, CheckType.PORT);

                                            if (portResults.isEmpty())
                                            {
                                                LOGGER.info("No devices have open ports. Skipping SSH metrics.");

                                                return getEntries(devices);
                                            }

                                            var availabilityParams = new ArrayList<>();

                                            var reachableIds = portResults.stream()
                                                    .map(dev -> ((JsonObject) dev).getInteger(ID))
                                                    .collect(Collectors.toSet());

                                            for (var i = 0; i < devices.size(); i++)
                                            {
                                                var deviceId = devices.getJsonObject(i).getInteger(ID);

                                                availabilityParams.add(List.of(deviceId, reachableIds.contains(deviceId)));
                                            }

                                            // SSH metrics - now returns directly instead of Future
                                            var metricsResults = PluginOperationsUtil.runSSHMetrics(portResults);

                                            if (metricsResults.isEmpty())
                                            {
                                                LOGGER.info("SSH metrics collection returned no results.");
                                            }

                                            return new JsonObject()
                                                    .put("availabilityParams", new JsonArray(availabilityParams))
                                                    .put("metricsResults", metricsResults);
                                        }
                                        catch (Exception e)
                                        {
                                            LOGGER.error("Connectivity or SSH checks failed: " + e.getMessage());

                                            // Return empty results on error
                                            return getEntries(devices);
                                        }
                                    })
                                    .onFailure(err -> LOGGER.error("Connectivity checks failed: " + err.getMessage()))
                                    .onSuccess(result ->
                                    {
                                        var availabilityParams = result.getJsonArray("availabilityParams");
                                        var metricsResults = result.getJsonArray("metricsResults");

                                        // Update availability data in database
                                        databaseService.executeBatch(new JsonObject()
                                                        .put("query", ADD_AVAILABILITY_DATA)
                                                        .put("params", availabilityParams))
                                                .onSuccess(res -> LOGGER.info("Availability records inserted: " + availabilityParams.size()))
                                                .onFailure(err -> LOGGER.error("Availability insert failed: " + err.getMessage()));

                                        if (metricsResults.isEmpty())
                                        {
                                            LOGGER.info("No metrics results to process.");
                                            return;
                                        }

                                        // Process and insert metrics results
                                        try
                                        {
                                            LOGGER.info("Polling completed. Received " + metricsResults.size() + " results.");

                                            var batchParams = new ArrayList<>();

                                            for (var i = 0; i < metricsResults.size(); i++)
                                            {
                                                var metricResult = metricsResults.getJsonObject(i);
                                                var deviceId = metricResult.getInteger("id");
                                                var metrics = metricResult.copy();
                                                metrics.remove("id");
                                                batchParams.add(List.of(deviceId, metrics.encode()));
                                            }

                                            databaseService.executeBatch(new JsonObject()
                                                            .put("query", INSERT_POLLING_RESULT)
                                                            .put("params", new JsonArray(batchParams)))
                                                    .onSuccess(batchResponse ->
                                                    {
                                                        if (batchResponse.getBoolean("success"))
                                                        {
                                                            LOGGER.info("Successfully inserted " + batchParams.size() + " polling results.");
                                                        }
                                                        else
                                                        {
                                                            LOGGER.warn("Batch insert failed: " + batchResponse.getString("error"));
                                                        }
                                                    })
                                                    .onFailure(err -> LOGGER.error("Batch insert failed: " + err.getMessage()));
                                        }
                                        catch (Exception e)
                                        {
                                            LOGGER.error("Failed to process metrics results: " + e.getMessage());
                                        }
                                    });
                        })
                        .onFailure(err -> LOGGER.error("DB query failed: " + err.getMessage()));
            });

            LOGGER.info("Polling scheduled every " + interval + "ms");

            return Future.succeededFuture("Polling scheduled");
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to start polling: " +  e.getMessage());

            return Future.failedFuture("Failed to start polling: " + e.getMessage());
        }
    }

    private JsonObject getEntries(JsonArray devices)
    {
        var availabilityParams = new ArrayList<>();

        for (var i = 0; i < devices.size(); i++)
        {
            var deviceId = devices.getJsonObject(i).getInteger(ID);

            availabilityParams.add(List.of(deviceId, false));
        }

        return new JsonObject()
                .put("availabilityParams", new JsonArray(availabilityParams))
                .put("metricsResults", new JsonArray());
    }
}