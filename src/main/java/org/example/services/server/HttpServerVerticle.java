package org.example.services.server;

import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import org.example.services.server.handlers.AuthHandler;
import org.example.services.server.routes.CredentialRoutes;
import org.example.services.server.routes.DiscoveryRoutes;
import org.example.services.server.routes.ProvisionRoutes;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.example.utils.ConfigLoader;

/**
 * Verticle responsible for initializing and starting the HTTP server.
 * This server supports:
 * <ul>
 *     <li>Login and token-based authentication using JWT (handled by {@link AuthHandler}).</li>
 *     <li>Protected REST endpoints for credentials, discovery, and provision resources.</li>
 *     <li>Support for access and refresh tokens with secure cookie handling.</li>
 * </ul>
 *
 * Route handlers are initialized through corresponding route
 * classes like {@link CredentialRoutes}, {@link DiscoveryRoutes}, and {@link ProvisionRoutes}.
 */
public class HttpServerVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            // Create the router
            var router = Router.router(vertx);

            router.route().handler(BodyHandler.create());

            // Set up authentication
            new AuthHandler().setupAuth(router);

            // Initialize route handlers
            new CredentialRoutes().init(router);

            new DiscoveryRoutes().init(router);

            new ProvisionRoutes().init(router);

            // Start the HTTP server
            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(ConfigLoader.get().getInteger("http.server.port",8888), http ->
                    {
                        try
                        {
                            if (http.succeeded())
                            {
                                LOGGER.info("HTTP server started on port 8888");

                                startPromise.complete();
                            }
                            else
                            {
                                LOGGER.error("HTTP server failed to start", http.cause());

                                startPromise.fail(http.cause());
                            }
                        }
                        catch (Exception exception)
                        {
                            LOGGER.error("Error while starting HTTP server", exception);

                            startPromise.fail(exception);
                        }
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Fatal error during server initialization", exception);

            startPromise.fail(exception);
        }
    }

    @Override
    public void stop()
    {
        LOGGER.info("Stopping HttpVerticle");
    }
}