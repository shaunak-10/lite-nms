package org.example.services.server.routes;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.example.services.server.handlers.AbstractCrudHandler;
import org.example.services.server.handlers.CredentialHandler;
import static org.example.constants.AppConstants.Routes.*;

public class CredentialRoutes
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialRoutes.class);

    private final CredentialHandler handler = CredentialHandler.getInstance();

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
