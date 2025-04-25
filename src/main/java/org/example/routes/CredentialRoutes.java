package org.example.routes;

import io.vertx.ext.web.Router;
import org.example.handlers.CredentialHandler;
import static org.example.constants.AppConstants.Routes.*;

public class CredentialRoutes
{

    private final CredentialHandler handler = CredentialHandler.getInstance();


    public void init(Router router)
    {
        router.post(CREDENTIALS).handler(handler::add);

        router.get(CREDENTIALS).handler(handler::list);

        router.get(CREDENTIAL_BY_ID).handler(handler::getById);

        router.put(CREDENTIAL_BY_ID).handler(handler::update);

        router.delete(CREDENTIAL_BY_ID).handler(handler::delete);
    }
}
