package org.example.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;

import java.util.Arrays;

public class PingUtil {
    public static Future<JsonArray> filterReachableDevicesAsync(Vertx vertx, JsonArray devices)
    {
        return ConnectivityUtil.filterReachableDevicesAsync(vertx, devices, device ->
                Arrays.asList("ping", "-c", "3", "-W", "2","-i","0.5", device.getString("ip"))
        );
    }
}
