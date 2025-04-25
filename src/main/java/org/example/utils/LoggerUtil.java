package org.example.utils;

import java.util.logging.*;

public class LoggerUtil {

    private static final Logger databaseLogger = Logger.getLogger("LiteNMS.DatabaseLogger");

    private static final Logger pluginLogger = Logger.getLogger("LiteNMS.PluginLogger");

    static
    {
        try
        {

            Level level = Level.ALL;

            Formatter formatter = new SimpleFormatter();

            FileHandler dbHandler = new FileHandler("logs/database-events.log", true);

            dbHandler.setFormatter(formatter);

            databaseLogger.setUseParentHandlers(false);

            databaseLogger.setLevel(level);

            databaseLogger.addHandler(dbHandler);

            FileHandler pluginHandler = new FileHandler("logs/plugin-events.log", true);

            pluginHandler.setFormatter(formatter);

            pluginLogger.setUseParentHandlers(false);

            pluginLogger.setLevel(level);

            pluginLogger.addHandler(pluginHandler);

        }
        catch (Exception e)
        {
            System.err.println("Failed to setup loggers: " + e.getMessage());
        }
    }

    public static Logger getDatabaseLogger()
    {
        return databaseLogger;
    }

    public static Logger getPluginLogger()
    {
        return pluginLogger;
    }
}
