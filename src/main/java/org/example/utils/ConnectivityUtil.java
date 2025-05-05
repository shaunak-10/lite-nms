package org.example.utils;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.example.constants.AppConstants.ConfigKeys.PROCESS;
import static org.example.constants.AppConstants.PingConstants.TIMEOUT;

public class ConnectivityUtil
{
    public static Future<JsonArray> filterReachableDevices(Vertx vertx, JsonArray devices, Function<JsonObject, List<String>> commandProvider)
    {
        var timeout = ConfigLoader.get().getJsonObject(PROCESS).getInteger(TIMEOUT);

        var futures = new ArrayList<Future<Boolean>>();

        var reachableDevices = new CopyOnWriteArrayList<JsonObject>();

        for (var i = 0; i < devices.size(); i++)
        {
            var device = devices.getJsonObject(i);

            var future = vertx.executeBlocking(() ->
            {
                Process process = null;

                try
                {
                    process = new ProcessBuilder(commandProvider.apply(device)).start();

                    var exitCode = process.waitFor(timeout, TimeUnit.SECONDS) ? process.exitValue() : -1;

                    if (exitCode == 0)
                    {
                        reachableDevices.add(device);

                        return true;
                    }

                    return false;
                }
                catch (Exception e)
                {
                    throw new Exception(e.getMessage());
                }
                finally
                {
                    if (process != null)
                    {
                        process.destroy();
                    }
                }
            }, false);

            futures.add(future);
        }

        // Join all the futures together
        return Future.join(futures)
                .map(v -> new JsonArray(reachableDevices));
    }
}