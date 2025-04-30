package org.example.utils;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class ConfigLoader
{
    static Logger logger = LoggerUtil.getMainLogger();

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
            logger.severe("Failed to load config file: " + e.getMessage());
        }
    }

    public static JsonObject get()
    {
        return config;
    }
}
