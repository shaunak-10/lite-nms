package org.example.services.plugin;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import java.io.*;
import java.util.concurrent.TimeUnit;

import static org.example.constants.AppConstants.AddressesAndPaths.PLUGIN_PATH;
import static org.example.constants.AppConstants.ConfigKeys.PROCESS;
import static org.example.constants.AppConstants.PingConstants.TIMEOUT;

import io.vertx.core.json.JsonObject;
import org.example.MainApp;
import org.example.utils.ConfigLoader;
import org.example.utils.DecryptionUtil;
import org.example.utils.EncryptionUtil;

public class PluginServiceImpl implements PluginService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginServiceImpl.class);

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
                    var encryptedInput = EncryptionUtil.encrypt(devices.encode());

                    writer.write(encryptedInput);

                    writer.flush();
                }

                var devicesFromPlugin = new JsonArray();

                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
                {
                    var line = "";

                    while ((line = reader.readLine()) != null) {
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
