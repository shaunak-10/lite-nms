package org.example.routes;

import io.vertx.ext.web.Router;
import org.example.handlers.ProvisionHandler;

import static org.example.constants.AppConstants.Routes.*;

public class ProvisionRoutes
{

    private final ProvisionHandler handler = ProvisionHandler.getInstance();

    public void init(Router router)
    {
        router.post(PROVISIONS).handler(handler::add);

        router.get(PROVISIONS).handler(handler::list);

        router.get(PROVISION_BY_ID).handler(handler::getById);

        router.put(PROVISION_BY_ID).handler(handler::update);

        router.delete(PROVISION_BY_ID).handler(handler::delete);
    }
}
