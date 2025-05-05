package org.example.services.server.routes;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.example.services.server.handlers.DiscoveryHandler;
import static org.example.constants.AppConstants.Routes.*;

/**
 * Defines the routes and handlers for managing discovery profiles in the system.
 *
 * This class configures the HTTP routes related to the `discovery_profile` resource.
 * The routes are mapped to the corresponding handler methods in the `DiscoveryHandler` class
 * for performing CRUD operations on discovery profiles, as well as starting a discovery process.
 */
public class DiscoveryRoutes
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryRoutes.class);

    private final DiscoveryHandler handler = DiscoveryHandler.getInstance();

    /**
     * Initializes the routes for discovery profile CRUD operations and discovery process.
     *
     * This method sets up the HTTP routes for performing Create, Read, Update, and Delete (CRUD)
     * operations on discovery profiles. Additionally, it configures a route to trigger the
     * discovery process. The routes are mapped to the corresponding handler methods
     * in the `DiscoveryHandler` class. Any errors during route initialization are logged.
     *
     * @param router the Vert.x router instance used to define the routes
     */
    public void init(Router router)
    {
        try
        {
            router.post(DISCOVERIES).handler(handler::add);

            router.get(DISCOVERIES).handler(handler::list);

            router.get(DISCOVERY_RUN).handler(handler::runDiscovery);

            router.get(DISCOVERY_BY_ID).handler(handler::getById);

            router.put(DISCOVERY_BY_ID).handler(handler::update);

            router.delete(DISCOVERY_BY_ID).handler(handler::delete);
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage());
        }

    }
}
