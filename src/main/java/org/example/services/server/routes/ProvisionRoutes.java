package org.example.services.server.routes;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.example.services.server.handlers.ProvisionHandler;

import static org.example.constants.AppConstants.Routes.*;

/**
 * Defines the routes and handlers for managing provisioned devices in the system.
 *
 * This class sets up the HTTP endpoints related to the `provisioned_device` resource.
 * Each route is connected to its corresponding method in the {@link ProvisionHandler}
 * for performing CRUD operations on provisioned devices.
 */
public class ProvisionRoutes
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionRoutes.class);

    private final ProvisionHandler handler = ProvisionHandler.getInstance();

    /**
     * Initializes the routes for provisioned device management.
     *
     * This method registers the HTTP routes for Create, Read, Update, and Delete (CRUD)
     * operations on provisioned devices. The actual logic for each operation is handled
     * by methods in the {@link ProvisionHandler} class. If an exception occurs during
     * initialization, it is logged.
     *
     * @param router the Vert.x {@link Router} used to define the HTTP routes
     */
    public void init(Router router)
    {
        try
        {
            router.post(PROVISIONS).handler(handler::add);

            router.get(PROVISIONS).handler(handler::list);

            router.get(PROVISION_BY_ID).handler(handler::getById);

            router.put(PROVISION_BY_ID).handler(handler::update);

            router.delete(PROVISION_BY_ID).handler(handler::delete);
        }
        catch (Exception exception)
        {
            LOGGER.error(exception.getMessage());
        }
    }
}
