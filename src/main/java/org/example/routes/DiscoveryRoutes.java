package org.example.routes;

import io.vertx.ext.web.Router;
import org.example.handlers.DiscoveryHandler;
import static org.example.constants.AppConstants.Routes.*;

public class DiscoveryRoutes
{
    private final DiscoveryHandler handler = DiscoveryHandler.getInstance();

    public void init(Router router)
    {
        router.post(DISCOVERIES).handler(handler::add);

        router.get(DISCOVERIES).handler(handler::list);

        router.get(DISCOVERY_RUN).handler(handler::runDiscovery);

        router.get(DISCOVERY_BY_ID).handler(handler::getById);

        router.put(DISCOVERY_BY_ID).handler(handler::update);

        router.delete(DISCOVERY_BY_ID).handler(handler::delete);
    }
}
