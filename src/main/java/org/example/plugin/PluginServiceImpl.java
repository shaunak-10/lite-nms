package org.example.plugin;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import java.io.*;
import java.util.logging.Logger;

import static org.example.constants.AppConstants.AddressesAndPaths.PLUGIN_PATH;

import org.example.MainApp;
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
        return vertx.executeBlocking(() ->
        {
            Process process = null;

            try
            {
                ProcessBuilder pb = new ProcessBuilder(PLUGIN_PATH, command);

                process = pb.start();

                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream())))
                {
                    writer.write(devices.encode());

                    writer.flush();
                }

                StringBuilder output = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
                {
                    String line;

                    while ((line = reader.readLine()) != null)
                    {
                        output.append(line);
                    }
                }

                int exitCode = process.waitFor();

                if (exitCode == 0)
                {
                    return new JsonArray(output.toString());
                }
                else
                {
                    StringBuilder errorOutput = new StringBuilder();

                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream())))
                    {
                        String errLine;

                        while ((errLine = errorReader.readLine()) != null)
                        {
                            errorOutput.append(errLine);
                        }
                    }

                    String errorMsg = "Plugin error (exit code " + exitCode + "): " + errorOutput;

                    LOGGER.severe(errorMsg);

                    throw new RuntimeException(errorMsg);
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
