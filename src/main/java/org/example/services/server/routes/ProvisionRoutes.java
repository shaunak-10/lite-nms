package org.example.services.server.routes;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.example.services.server.handlers.ProvisionHandler;

import static org.example.constants.AppConstants.Routes.*;

public class ProvisionRoutes
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionRoutes.class);

    private final ProvisionHandler handler = ProvisionHandler.getInstance();

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
        catch (Exception e)
        {
            LOGGER.error(e.getMessage());
        }

    }
}
