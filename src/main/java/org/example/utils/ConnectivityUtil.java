package org.example.utils;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.example.constants.AppConstants.ConfigKeys.PROCESS;
import static org.example.constants.AppConstants.PingConstants.TIMEOUT;

public class ConnectivityUtil
{
    public static Future<JsonArray> filterReachableDevicesAsync(Vertx vertx, JsonArray devices, Function<JsonObject, List<String>> commandProvider)
    {
        var timeout = ConfigLoader.get().getJsonObject(PROCESS).getInteger(TIMEOUT);

        var futures = new ArrayList<Future>();

        var reachableDevices = new ArrayList<>();

        for (int i = 0; i < devices.size(); i++)
        {
            var device = devices.getJsonObject(i);

            var command = commandProvider.apply(device);

            var promise = Promise.promise();

            futures.add(promise.future());

            vertx.executeBlocking(execPromise ->
            {
                Process process = null;

                try
                {
                    ProcessBuilder builder = new ProcessBuilder(command);

                    process = builder.start();

                    var exitCode = process.waitFor(timeout, TimeUnit.SECONDS) ? process.exitValue() : -1;

                    if (exitCode == 0)
                    {
                        synchronized (reachableDevices)
                        {
                            reachableDevices.add(device);
                        }
                    }

                    execPromise.complete();
                }
                catch (Exception e)
                {
                    execPromise.fail(e.getMessage());
                }
                finally
                {
                    if (process != null)
                    {
                        process.destroy();
                    }
                }
            }, false, res ->
            {
                if (res.failed())
                {
                    promise.fail(res.cause());
                }

                promise.complete();
            });
        }

        return CompositeFuture.all(futures)
                .map(v -> new JsonArray(reachableDevices));
    }
}
