package org.example.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;

import java.util.Arrays;

public class PortUtil {
    public static Future<JsonArray> filterReachableDevicesAsync(Vertx vertx, JsonArray devices)
    {
        return ConnectivityUtil.filterReachableDevicesAsync(vertx, devices, device ->
                Arrays.asList("nc", "-zv", "-w", "1", device.getString("ip"), String.valueOf(device.getInteger("port", 22)))
        );
    }
}
