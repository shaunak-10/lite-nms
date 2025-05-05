package org.example.utils;

import io.vertx.core.json.JsonArray;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.example.constants.AppConstants.ConfigKeys.PROCESS;
import static org.example.constants.AppConstants.DiscoveryField.IP;
import static org.example.constants.AppConstants.DiscoveryField.PORT;
import static org.example.constants.AppConstants.PingConstants.*;
import static org.example.constants.AppConstants.PortConstants.*;

public class ConnectivityUtil
{
    public enum CheckType {
        PING,
        PORT
    }

    /**
     * Filters devices that are reachable based on the specified check type
     *
     * @param devices The devices to check
     * @param checkType The type of check to perform (PING or PORT)
     * @return JsonArray containing only the reachable devices
     */
    public static JsonArray filterReachableDevices(JsonArray devices, CheckType checkType)
    {
        var reachableDevices = new JsonArray();

        for (var i = 0; i < devices.size(); i++)
        {
            var device = devices.getJsonObject(i);

            List<String> command;

            if (checkType == CheckType.PING)
            {
                var pingConfig = ConfigLoader.get().getJsonObject(PING_COMMAND);

                command = Arrays.asList(
                        PING_COMMAND,
                        PACKETS_OPTION, String.valueOf(pingConfig.getInteger(COUNT)),
                        PING_TIMEOUT_OPTION, String.valueOf(pingConfig.getInteger(TIMEOUT)),
                        INTERVAL_OPTION, String.valueOf(pingConfig.getDouble(INTERVAL)),
                        device.getString(IP)
                );
            }
            else
            { // PORT check
                var portConfig = ConfigLoader.get().getJsonObject(PORT);

                command = Arrays.asList(
                        NC_COMMAND, ZERO_IO, PORT_TIMEOUT_OPTION, String.valueOf(portConfig.getInteger(TIMEOUT)),
                        device.getString(IP),
                        String.valueOf(device.getInteger(PORT, 22))
                );
            }

            Process process = null;
            try
            {
                process = new ProcessBuilder(command).start();

                // Get timeout from config
                var timeout = ConfigLoader.get().getJsonObject(PROCESS).getInteger(TIMEOUT);

                var exitCode = process.waitFor(timeout, TimeUnit.SECONDS) ? process.exitValue() : -1;

                if (exitCode == 0)
                {
                    reachableDevices.add(device);
                }
            }
            catch (Exception e)
            {
                // Log error or handle exception if needed
                System.err.println("Error checking connectivity: " + e.getMessage());
            }
            finally
            {
                if (process != null)
                {
                    process.destroy();
                }
            }
        }

        return reachableDevices;
    }
}