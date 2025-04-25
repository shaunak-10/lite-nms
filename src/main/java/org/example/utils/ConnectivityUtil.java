package org.example.utils;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ConnectivityUtil
{
    public static Future<JsonArray> filterReachableDevicesAsync(Vertx vertx, JsonArray devices, Function<JsonObject, List<String>> commandProvider)
    {
        List<Future> futures = new ArrayList<>();

        List<JsonObject> reachableDevices = new ArrayList<>();

        for (int i = 0; i < devices.size(); i++)
        {
            JsonObject device = devices.getJsonObject(i);

            List<String> command = commandProvider.apply(device);

            Promise<Void> promise = Promise.promise();

            futures.add(promise.future());

            vertx.executeBlocking(execPromise ->
            {
                Process process = null;

                try
                {
                    ProcessBuilder builder = new ProcessBuilder(command);

                    process = builder.start();

                    int exitCode = process.waitFor();

                    if (exitCode == 0)
                    {
                        synchronized (reachableDevices)
                        {
                            reachableDevices.add(device);
                        }
                    }

                    execPromise.complete();
                }
                catch (IOException | InterruptedException e)
                {
                    execPromise.fail(e);
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
