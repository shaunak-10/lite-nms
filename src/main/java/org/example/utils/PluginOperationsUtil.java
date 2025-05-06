package org.example.utils;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import java.io.*;
import java.util.concurrent.TimeUnit;

import static org.example.constants.AppConstants.AddressesAndPaths.PLUGIN_PATH;
import static org.example.constants.AppConstants.ConfigKeys.PROCESS;
import static org.example.constants.AppConstants.PingConstants.TIMEOUT;

import io.vertx.core.json.JsonObject;

public class PluginOperationsUtil
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginOperationsUtil.class);

    /**
     * Synchronously checks SSH reachability of the provided devices
     *
     * @param devices The JSON array of device data
     * @return A JSON array with reachability results
     */
    public static JsonArray runSSHReachability(JsonArray devices) throws Exception
    {
        return executePlugin(devices, "reachability");
    }

    /**
     * Synchronously collects SSH metrics from the provided devices
     *
     * @param devices The JSON array of device data
     * @return A JSON array with metrics results
     */
    public static JsonArray runSSHMetrics(JsonArray devices) throws Exception
    {
        return executePlugin(devices, "metrics");
    }

    /**
     * Executes the plugin command synchronously and returns the results
     *
     * @param devices The JSON array of device data
     * @param command The command to execute (reachability or metrics)
     * @return A JSON array with the command results
     * @throws Exception if the plugin execution fails
     */
    private static JsonArray executePlugin(JsonArray devices, String command) throws Exception
    {
        var timeout = ConfigLoader.get().getJsonObject(PROCESS).getInteger(TIMEOUT);

        Process process = null;

        try
        {
            var pb = new ProcessBuilder(PLUGIN_PATH, command);

            process = pb.start();

            try (var writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream())))
            {
                var encryptedInput = EncryptionUtil.encrypt(devices.encode());

                writer.write(encryptedInput);

                writer.flush();
            }

            var devicesFromPlugin = new JsonArray();

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
            {
                var line = "";

                while ((line = reader.readLine()) != null)
                {
                    try
                    {
                        var decrypted = DecryptionUtil.decrypt(line);

                        devicesFromPlugin.add(new JsonObject(decrypted));
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Decryption failed for plugin output: " + e.getMessage());
                    }
                }
            }

            var exitCode = process.waitFor(timeout, TimeUnit.SECONDS) ? process.exitValue() : -1;

            if (exitCode == 0)
            {
                return devicesFromPlugin;
            }
            else
            {
                var errorOutput = new StringBuilder();

                try (var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream())))
                {
                    var errLine = "";

                    while ((errLine = errorReader.readLine()) != null)
                    {
                        errorOutput.append(errLine);
                    }
                }

                var errorMsg = "Plugin error (exit code " + exitCode + "): " + errorOutput;

                LOGGER.error(errorMsg);

                throw new Exception(errorMsg);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Plugin execution error: " + e.getMessage());

            throw new Exception(e);
        }
        finally
        {
            if (process != null)
            {
                process.destroy();
            }
        }
    }
}