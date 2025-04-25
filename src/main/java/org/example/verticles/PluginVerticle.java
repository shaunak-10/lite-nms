package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import org.example.utils.LoggerUtil;

import java.io.*;
import java.util.logging.Logger;

import static org.example.constants.AppConstants.AddressesAndPaths.*;

public class PluginVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerUtil.getPluginLogger();

    @Override
    public void start(Promise<Void> startPromise)
    {
        vertx.eventBus().consumer(SSH_DISCOVERY, this::handleSSHCheck);

        vertx.eventBus().consumer(SSH_METRICS, this::handleSSHMetrics);

        LOGGER.info("PluginVerticle deployed and listening on event bus...");

        startPromise.complete();
    }

    private void handleSSHMetrics(Message<JsonArray> devices)
    {
        executePlugin(devices, "metrics");
    }

    private void handleSSHCheck(Message<JsonArray> devices)
    {
        executePlugin(devices, "reachability");
    }

    private void executePlugin(Message<JsonArray> devices, String command)
    {
        vertx.executeBlocking(() ->
        {
            Process process = null;

            try
            {
                ProcessBuilder pb = new ProcessBuilder(PLUGIN_PATH, command);

                process = pb.start();

                // Send JSON input to plugin via stdin
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream())))
                {
                    writer.write(devices.body().encode());

                    writer.flush();
                }

                // Read stdout (plugin output)
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

                    LOGGER.severe("Plugin error (exit code " + exitCode + "): " + errorOutput);

                    return null;
                }
            }
            catch (Exception e)
            {
                LOGGER.severe("Plugin execution error: " + e.getMessage());

                return null;
            }
            finally
            {
                if (process != null)
                {
                    process.destroy();
                }
            }
        },false, res ->
        {
            if (res.succeeded() && res.result() != null)
            {
                devices.reply(res.result());
            }
            else
            {
                String errorMsg = res.cause() != null ? res.cause().getMessage() : "Plugin failed to return data";

                devices.fail(500, errorMsg);
            }
        });
    }
}
