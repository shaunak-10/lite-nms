package org.example.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;

import java.util.Arrays;

import static org.example.constants.AppConstants.DiscoveryField.IP;
import static org.example.constants.AppConstants.PortConstants.*;

public class PortUtil {
    public static Future<JsonArray> filterReachableDevices(Vertx vertx, JsonArray devices)
    {
        var portConfig = ConfigLoader.get().getJsonObject(PORT);

        return ConnectivityUtil.filterReachableDevices(vertx, devices, device ->
                Arrays.asList(NC_COMMAND, ZERO_IO, TIMEOUT_OPTION, String.valueOf(portConfig.getInteger(TIMEOUT)),
                        device.getString(IP),
                        String.valueOf(device.getInteger(PORT, 22)))
        );
    }
}

