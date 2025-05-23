package org.example.services.scheduler;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.services.db.DatabaseService;
import org.example.services.db.DatabaseVerticle;
import org.example.utils.ConfigLoader;
import org.example.utils.ConnectivityUtil;
import org.example.utils.PluginOperationsUtil;
import org.example.utils.DecryptionUtil;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.example.constants.AppConstants.FALSE;
import static org.example.constants.AppConstants.JsonKey.*;
import static org.example.constants.AppConstants.ProvisionField.*;
import static org.example.constants.AppConstants.CredentialField.USERNAME;
import static org.example.constants.AppConstants.CredentialField.PASSWORD;
import static org.example.constants.AppConstants.CredentialField.SYSTEM_TYPE_RESPONSE;
import static org.example.constants.AppConstants.CredentialField.SYSTEM_TYPE;
import static org.example.constants.AppConstants.ProvisionQuery.*;
import static org.example.utils.ConnectivityUtil.CheckType;

/**
 * Implementation of {@link SchedulerService} that manages
 * periodic polling of provisioned devices.
 * This class handles retrieving devices from the database,
 * running connectivity checks (ping and port), performing SSH
 * metric collection using the plugin, and writing results back
 * to the database.
 */
public class SchedulerServiceImpl implements SchedulerService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerServiceImpl.class);

    private final DatabaseService databaseService;

    private long pollingTimerId = -1;

    private final Vertx vertx;

    private static final Map<Integer, Long> deviceLastPolledTimes = new HashMap<>();

    // New query to fetch all device IDs from provisioned_device table
    public static final String GET_ALL_DEVICE_IDS = "SELECT id FROM provisioned_device WHERE is_deleted = false";

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

            // Initialize the device last polled times map
            return initializeDeviceMap().compose(result ->
            {
                // Set up the periodic timer after initialization
                pollingTimerId = vertx.setPeriodic(interval, id -> runPollingTask());

                LOGGER.info("Polling scheduled every " + interval + "ms");

                return Future.succeededFuture("Polling scheduled");

            }).recover(error ->
            {
                LOGGER.error("Failed to initialize device map: " + error.getMessage());

                return Future.failedFuture("Failed to start polling: " + error.getMessage());
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to start polling: " + exception.getMessage());

            return Future.failedFuture("Failed to start polling: " + exception.getMessage());
        }
    }

    @Override
    public Future<Void> addEntry(int id)
    {
        try
        {
            deviceLastPolledTimes.put(id, System.currentTimeMillis());

            LOGGER.info("Added device ID " + id + " to map");

            return Future.succeededFuture();
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to add device ID " + id + ": " + exception.getMessage());

            return Future.failedFuture(exception);
        }
    }

    @Override
    public Future<Void> removeEntry(int id)
    {
        try
        {
            if (deviceLastPolledTimes.remove(id) != null)
            {
                LOGGER.info("Removed device ID " + id + " from map");
            }
            else
            {
                LOGGER.warn("Device ID " + id + " not found in map");
            }

            return Future.succeededFuture();
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to remove device ID " + id + ": " + exception.getMessage());

            return Future.failedFuture(exception);
        }
    }

    /**
     * Initialize the device map with all device IDs and current timestamp
     */
    private Future<Void> initializeDeviceMap()
    {
        try
        {
            return databaseService.executeQuery(new JsonObject()
                            .put(QUERY, GET_ALL_DEVICE_IDS)
                            .put(PARAMS, Collections.emptyList()))
                    .compose(dbResponse ->
                    {
                        try
                        {
                            if (!dbResponse.getBoolean(SUCCESS))
                            {
                                return Future.failedFuture("DB query failed: " + dbResponse.getString(ERROR));
                            }

                            for (var rowObj : dbResponse.getJsonArray(ROWS, new JsonArray()))
                            {
                                try
                                {
                                    deviceLastPolledTimes.put(((JsonObject) rowObj).getInteger(ID), System.currentTimeMillis());
                                }
                                catch (Exception exception)
                                {
                                    LOGGER.error("Failed to process device ID: " + exception.getMessage());
                                }
                            }

                            LOGGER.info("Initialized device map with " + deviceLastPolledTimes.size() + " devices");

                            return Future.succeededFuture();
                        }
                        catch (Exception exception)
                        {
                            LOGGER.error("Failed to process DB response: " + exception.getMessage());

                            return Future.failedFuture("Failed to process DB response: " + exception.getMessage());
                        }
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to initialize device map: " + exception.getMessage());

            return Future.failedFuture("Failed to initialize device map: " + exception.getMessage());
        }
    }

    /**
     * Run the polling task for eligible devices
     */
    private void runPollingTask()
    {
        LOGGER.info("Running scheduled polling task");

        // Get eligible devices for polling (last polled > 1 minute ago)
        var eligibleDeviceIds = getEligibleDeviceIds();

        if (eligibleDeviceIds.isEmpty())
        {
            LOGGER.info("No devices eligible for polling at this time");

            return;
        }

        LOGGER.info("Found " + eligibleDeviceIds.size() + " devices eligible for polling");

        databaseService.executeQuery(new JsonObject()
                        .put(QUERY, String.format(
                                "SELECT p.id, p.port, p.ip, c.username, c.password, c.system_type " +
                                        "FROM provisioned_device p " +
                                        "JOIN credential_profile c ON p.credential_profile_id = c.id " +
                                        "WHERE p.id IN (%s)", IntStream.range(1, eligibleDeviceIds.size() + 1)
                                        .mapToObj(i -> "$" + i)
                                        .collect(Collectors.joining(","))))
                        .put(PARAMS, new JsonArray(eligibleDeviceIds)))
                .onSuccess(dbResponse ->
                {
                    try
                    {
                        if (!dbResponse.getBoolean(SUCCESS))
                        {
                            LOGGER.warn("DB query failed: " + dbResponse.getString(ERROR));

                            return;
                        }

                        var devices = new JsonArray();

                        for (var rowObj : dbResponse.getJsonArray(ROWS, new JsonArray()))
                        {
                            var row = (JsonObject) rowObj;

                            try
                            {
                                var device = new JsonObject()
                                        .put(ID, row.getInteger(ID))
                                        .put(PORT, row.getInteger(PORT))
                                        .put(IP, row.getString(IP))
                                        .put(USERNAME, row.getString(USERNAME))
                                        .put(PASSWORD, DecryptionUtil.decrypt(row.getString(PASSWORD)))
                                        .put(SYSTEM_TYPE_RESPONSE, row.getString(SYSTEM_TYPE));

                                devices.add(device);
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Failed to process device: " + exception.getMessage());
                            }
                        }

                        if (devices.isEmpty())
                        {
                            LOGGER.info("No devices found for polling");

                            return;
                        }

                        // Update the last polled time for these devices
                        updateLastPolledTimes(devices);

                        // Process PING and PORT checks concurrently for each device
                        var deviceFutures = devices.stream()
                                .map(obj -> (JsonObject) obj)
                                .map(device -> vertx.executeBlocking(
                                        () ->
                                        {
                                            try
                                            {
                                                // Perform PING check
                                                var pingResult = ConnectivityUtil.filterReachableDevices(new JsonArray().add(device), CheckType.PING);

                                                if (pingResult.isEmpty())
                                                {
                                                    return null;
                                                }

                                                // Perform PORT check
                                                var portResult = ConnectivityUtil.filterReachableDevices(pingResult, CheckType.PORT);

                                                if (portResult.isEmpty())
                                                {
                                                    return null;
                                                }

                                                // Return the device that passed both checks
                                                return portResult.getJsonObject(0);

                                            }
                                            catch (Exception exception)
                                            {
                                                LOGGER.error("Error processing device ID " + device.getInteger(ID) + ": " + exception.getMessage());

                                                return null;
                                            }
                                        },
                                        FALSE // Ordered execution not required
                                ))
                                .collect(Collectors.toList());

                        // Wait for all PING and PORT checks to complete
                        Future.all(deviceFutures)
                                .compose(composite ->
                                {
                                    // Collect devices that passed PING and PORT checks
                                    var reachableDevices = new JsonArray();

                                    for (var i = 0; i < composite.size(); i++)
                                    {
                                        try
                                        {
                                            var result = composite.resultAt(i);

                                            if (result instanceof JsonObject)
                                            {
                                                reachableDevices.add(result);
                                            }
                                        }
                                        catch (Exception exception)
                                        {
                                            LOGGER.error("Error processing result for device ID " + devices.getJsonObject(i).getInteger(ID) + ": " + exception.getMessage());
                                        }
                                    }

                                    // Prepare availability params
                                    var availabilityParams = new ArrayList<>();

                                    var reachableIds = reachableDevices.stream()
                                            .map(dev -> ((JsonObject) dev).getInteger(ID))
                                            .collect(Collectors.toSet());

                                    for (var i = 0; i < devices.size(); i++)
                                    {
                                        try
                                        {
                                            availabilityParams.add(List.of(devices.getJsonObject(i).getInteger(ID), reachableIds.contains(devices.getJsonObject(i).getInteger(ID))));
                                        }
                                        catch (Exception exception)
                                        {
                                            LOGGER.error("Error processing device ID " + devices.getJsonObject(i).getInteger(ID) + ": " + exception.getMessage());
                                        }
                                    }

                                    if (reachableDevices.isEmpty())
                                    {
                                        LOGGER.info("No devices passed PING and PORT checks.");

                                        return Future.succeededFuture(new JsonObject()
                                                .put(AVAILABILITY_PARAMS, new JsonArray(availabilityParams))
                                                .put(METRICS_RESULTS, new JsonArray()));
                                    }

                                    // Perform SSH metrics collection in executeBlocking
                                    return vertx.executeBlocking(
                                            () ->
                                            {
                                                try
                                                {
                                                    return PluginOperationsUtil.runSSHMetrics(reachableDevices);
                                                }
                                                catch (Exception exception)
                                                {
                                                    LOGGER.error("SSH metrics collection failed: " + exception.getMessage());

                                                    return new JsonArray();
                                                }
                                            },
                                            FALSE
                                    ).map(metricsResults -> new JsonObject()
                                            .put(AVAILABILITY_PARAMS, new JsonArray(availabilityParams))
                                            .put(METRICS_RESULTS, metricsResults));
                                })
                                .onSuccess(result ->
                                {
                                    // Process and insert metrics results
                                    try
                                    {
                                        var availabilityParams = result.getJsonArray(AVAILABILITY_PARAMS);

                                        var metricsResults = result.getJsonArray(METRICS_RESULTS);

                                        // Update availability data in database
                                        databaseService.executeBatch(new JsonObject()
                                                        .put(QUERY, ADD_AVAILABILITY_DATA)
                                                        .put(PARAMS, availabilityParams))
                                                .onSuccess(res -> LOGGER.info("Availability records inserted: " + availabilityParams.size()))
                                                .onFailure(error -> LOGGER.error("Availability insert failed: " + error.getMessage()));

                                        if (metricsResults.isEmpty())
                                        {
                                            LOGGER.info("No metrics results to process.");

                                            return;
                                        }

                                        LOGGER.info("Polling completed. Received " + metricsResults.size() + " results.");

                                        var batchParams = new ArrayList<>();

                                        for (var i = 0; i < metricsResults.size(); i++)
                                        {
                                            try
                                            {
                                                batchParams.add(List.of(metricsResults.getJsonObject(i).remove(ID), metricsResults.getJsonObject(i)));
                                            }
                                            catch (Exception exception)
                                            {
                                                LOGGER.error("Error processing metrics result: " + exception.getMessage());
                                            }
                                        }

                                        databaseService.executeBatch(new JsonObject()
                                                        .put(QUERY, INSERT_POLLING_RESULT)
                                                        .put(PARAMS, new JsonArray(batchParams)))
                                                .onSuccess(batchResponse ->
                                                {
                                                    if (batchResponse.getBoolean(SUCCESS))
                                                    {
                                                        LOGGER.info("Successfully inserted " + batchParams.size() + " polling results.");
                                                    }
                                                    else
                                                    {
                                                        LOGGER.warn("Batch insert failed: " + batchResponse.getString(ERROR));
                                                    }
                                                })
                                                .onFailure(error -> LOGGER.error("Batch insert failed: " + error.getMessage()));
                                    }
                                    catch (Exception exception)
                                    {
                                        LOGGER.error("Failed to process metrics results: " + exception.getMessage());
                                    }
                                })
                                .onFailure(error ->
                                {
                                    LOGGER.error("Polling pipeline failed: " + error.getMessage());

                                    // Update availability as false for all devices on failure
                                    var availabilityParams = new JsonArray(
                                            devices.stream()
                                                    .map(device -> List.of(((JsonObject) device).getInteger(ID), FALSE))
                                                    .toList()
                                    );

                                    databaseService.executeBatch(new JsonObject()
                                                    .put(QUERY, ADD_AVAILABILITY_DATA)
                                                    .put(PARAMS, availabilityParams))
                                            .onSuccess(res -> LOGGER.info("Availability records inserted: " + availabilityParams.size()))
                                            .onFailure(exception -> LOGGER.error("Availability insert failed: " + exception.getMessage()));
                                });
                    }
                    catch (Exception exception)
                    {
                        LOGGER.error("Failed to process DB response: " + exception.getMessage());
                    }
                })
                .onFailure(error -> LOGGER.error("DB query failed: " + error.getMessage()));
    }

    /**
     * Get device IDs that are eligible for polling (last polled > 1 minute ago)
     */
    private List<Integer> getEligibleDeviceIds()
    {
        try
        {
            var eligibleDevices = new ArrayList<Integer>();

            for (Map.Entry<Integer, Long> entry : deviceLastPolledTimes.entrySet())
            {
                try
                {
                    if (System.currentTimeMillis() - entry.getValue() >= ConfigLoader.get().getInteger("polling.interval",30000))
                    {
                        eligibleDevices.add(entry.getKey());
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Failed to process device ID " + entry.getKey() + ": " + exception.getMessage());
                }
            }
            return eligibleDevices;
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to get eligible device IDs: " + exception.getMessage());

            return Collections.emptyList();
        }
    }

    /**
     * Update the last polled time for devices
     */
    private void updateLastPolledTimes(JsonArray devices)
    {
        try
        {
            for (int i = 0; i < devices.size(); i++)
            {
                try
                {
                    Integer deviceId = devices.getJsonObject(i).getInteger(ID);

                    deviceLastPolledTimes.put(deviceId, System.currentTimeMillis());
                }
                catch (Exception exception)
                {
                    LOGGER.error("Failed to update last polled time: " + exception.getMessage());
                }
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to update last polled times: " + exception.getMessage());
        }
    }
}