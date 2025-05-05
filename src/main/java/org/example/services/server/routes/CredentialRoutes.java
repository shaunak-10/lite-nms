package org.example.services.server.routes;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.example.services.server.handlers.AbstractCrudHandler;
import org.example.services.server.handlers.CredentialHandler;
import static org.example.constants.AppConstants.Routes.*;

/**
 * Defines the routes and handlers for managing credential profiles in the system.
 *
 * This class configures the HTTP routes related to the `credential_profile` resource.
 * The routes are mapped to the corresponding handler methods in the `CredentialHandler` class
 * for performing CRUD operations on credential profiles.
 */
public class CredentialRoutes
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialRoutes.class);

    private final CredentialHandler handler = CredentialHandler.getInstance();

    /**
     * Initializes the routes for credential profile CRUD operations.
     *
     * This method sets up the HTTP routes for performing Create, Read, Update, and Delete (CRUD)
     * operations on credential profiles. The routes are mapped to the corresponding handler methods
     * in the `CredentialHandler` class. Any errors during route initialization are logged.
     *
     * @param router the Vert.x router instance used to define the routes
     */
    public void init(Router router)
    {
        try
        {
            router.post(CREDENTIALS).handler(handler::add);

            router.get(CREDENTIALS).handler(handler::list);

            router.get(CREDENTIAL_BY_ID).handler(handler::getById);

            router.put(CREDENTIAL_BY_ID).handler(handler::update);

            router.delete(CREDENTIAL_BY_ID).handler(handler::delete);
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage());
        }
    }
}
