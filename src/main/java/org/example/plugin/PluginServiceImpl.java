package org.example.plugin;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.example.constants.AppConstants.AddressesAndPaths.PLUGIN_PATH;
import static org.example.constants.AppConstants.ConfigKeys.PROCESS;
import static org.example.constants.AppConstants.PingConstants.TIMEOUT;

import io.vertx.core.json.JsonObject;
import org.example.MainApp;
import org.example.utils.ConfigLoader;
import org.example.utils.DecryptionUtil;
import org.example.utils.EncryptionUtil;
import org.example.utils.LoggerUtil;

public class PluginServiceImpl implements PluginService
{
    private static final Logger LOGGER = LoggerUtil.getPluginLogger();

    private final Vertx vertx;

    public PluginServiceImpl()
    {
        this.vertx = MainApp.vertx;
    }

    @Override
    public Future<JsonArray> runSSHReachability(JsonArray devices)
    {
        return executePlugin(devices, "reachability");
    }

    @Override
    public Future<JsonArray> runSSHMetrics(JsonArray devices)
    {
        return executePlugin(devices, "metrics");
    }

    private Future<JsonArray> executePlugin(JsonArray devices, String command)
    {
        var timeout = ConfigLoader.get().getJsonObject(PROCESS).getInteger(TIMEOUT);

        return vertx.executeBlocking(() ->
        {
            Process process = null;

            try
            {
                var pb = new ProcessBuilder(PLUGIN_PATH, command);

                process = pb.start();


                try (var writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream())))
                {
                    System.out.println(devices.encode());

                    var encryptedInput = EncryptionUtil.encrypt(devices.encode());

                    System.out.println(encryptedInput);

                    writer.write(encryptedInput);

                    writer.flush();
                }

                var devicesFromPlugin = new JsonArray();

                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
                {
                    String line;

                    while ((line = reader.readLine()) != null) {
                        try
                        {
                            System.out.println(line);

                            String decrypted = DecryptionUtil.decrypt(line);

                            System.out.println(decrypted);

                            devicesFromPlugin.add(new JsonObject(decrypted));
                        }
                        catch (Exception e)
                        {
                            LOGGER.severe("Decryption failed for plugin output: " + e.getMessage());
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
                        String errLine;

                        while ((errLine = errorReader.readLine()) != null)
                        {
                            errorOutput.append(errLine);
                        }
                    }

                    var errorMsg = "Plugin error (exit code " + exitCode + "): " + errorOutput;

                    LOGGER.severe(errorMsg);

                    throw new Exception(errorMsg);
                }
            }
            catch (Exception e)
            {
                LOGGER.severe("Plugin execution error: " + e.getMessage());

                throw new RuntimeException(e);
            }
            finally
            {
                if (process != null)
                {
                    process.destroy();
                }
            }
        }, false);
    }
}
