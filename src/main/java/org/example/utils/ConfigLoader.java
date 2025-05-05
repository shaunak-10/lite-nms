package org.example.utils;

import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility class for loading and accessing configuration settings from a JSON file.
 * <p>
 * The configuration is read once using {@link #init(String)} and stored internally as a {@link JsonObject}.
 * Other components can retrieve the loaded configuration using {@link #get()}.
 * <p>
 * Example usage:
 * <pre>
 *     ConfigLoader.init("config.json");
 *     int port = ConfigLoader.get().getInteger("http-port");
 * </pre>
 */
public class ConfigLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);

    private static JsonObject config;

    /**
     * Initializes the configuration by reading a JSON file from the specified path.
     * <p>
     * This method should be called once at application startup before any call to {@link #get()}.
     *
     * @param path the file system path to the JSON configuration file
     */
    public static void init(String path)
    {
        try
        {
            var content = Files.readString(Paths.get(path));

            config = new JsonObject(content);
        }
        catch (Exception e)
        {
           LOGGER.error("Failed to load config file: " + e.getMessage());
        }
    }

    /**
     * Retrieves the loaded configuration as a {@link JsonObject}.
     * <p>
     * This method returns the configuration that was loaded using the {@link #init(String)} method.
     * It should only be called after the configuration has been successfully initialized.
     *
     * @return the {@link JsonObject} containing the loaded configuration
     */
    public static JsonObject get()
    {
        return config;
    }
}
