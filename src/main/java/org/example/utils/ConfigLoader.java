package org.example.utils;

import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);

    private static JsonObject config;

    public static void init(String path)
    {
        try
        {
            var content = Files.readString(Paths.get(path));

            config = new JsonObject(content);
        }
        catch (IOException e)
        {
           LOGGER.error("Failed to load config file: " + e.getMessage());
        }
    }

    public static JsonObject get()
    {
        return config;
    }
}
