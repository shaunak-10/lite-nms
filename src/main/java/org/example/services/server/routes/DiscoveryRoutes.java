package org.example.services.server.routes;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.example.services.server.handlers.DiscoveryHandler;
import static org.example.constants.AppConstants.Routes.*;

public class DiscoveryRoutes
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryRoutes.class);

    private final DiscoveryHandler handler = DiscoveryHandler.getInstance();

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
