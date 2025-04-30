package org.example.utils;

import java.util.logging.*;

public class LoggerUtil {

    private static final Logger mainLogger = createLogger("LiteNMS.DatabaseLogger", "logs/main-events.log");

    private static final Logger pluginLogger = createLogger("LiteNMS.PluginLogger", "logs/plugin-events.log");

    private static final Logger consoleLogger = createConsoleLogger();

    private static Logger createLogger(String name, String filePath)
    {
        var logger = Logger.getLogger(name);

        logger.setUseParentHandlers(false);

        logger.setLevel(Level.ALL);

        try
        {
            var fileHandler = new FileHandler(filePath, true);

            fileHandler.setFormatter(new SimpleFormatter());

            logger.addHandler(fileHandler);
        }
        catch (Exception e)
        {
            System.err.println("Failed to setup file logger for " + name + ": " + e.getMessage());
        }

        return logger;
    }

    private static Logger createConsoleLogger()
    {
        var logger = Logger.getLogger("LiteNMS.ConsoleLogger");

        logger.setUseParentHandlers(false);

        logger.setLevel(Level.ALL);

        var consoleHandler = new ConsoleHandler();

        consoleHandler.setFormatter(new SimpleFormatter());

        consoleHandler.setLevel(Level.ALL);

        logger.addHandler(consoleHandler);

        return logger;
    }

    public static Logger getMainLogger()
    {
        return mainLogger;
    }

    public static Logger getPluginLogger()
    {
        return pluginLogger;
    }

    public static Logger getConsoleLogger()
    {
        return consoleLogger;
    }
}
