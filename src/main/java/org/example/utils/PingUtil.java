package org.example.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;

import java.util.Arrays;

import static org.example.constants.AppConstants.DiscoveryField.IP;
import static org.example.constants.AppConstants.PingConstants.*;

public class PingUtil
{
    public static Future<JsonArray> filterReachableDevicesAsync(Vertx vertx, JsonArray devices)
    {
        var pingConfig = ConfigLoader.get().getJsonObject(PING_COMMAND);

        return ConnectivityUtil.filterReachableDevicesAsync(vertx, devices, device ->
                Arrays.asList(PING_COMMAND,
                        PACKETS_OPTION, String.valueOf(pingConfig.getInteger(COUNT)),
                        TIMEOUT_OPTION, String.valueOf(pingConfig.getInteger(TIMEOUT)),
                        INTERVAL_OPTION, String.valueOf(pingConfig.getDouble(INTERVAL)),
                        device.getString(IP))
        );
    }
}
