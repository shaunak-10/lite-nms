package org.example.services.server;

import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.example.services.server.routes.CredentialRoutes;
import org.example.services.server.routes.DiscoveryRoutes;
import org.example.services.server.routes.ProvisionRoutes;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import static org.example.constants.AppConstants.Headers.*;

/**
 * Verticle responsible for initializing and starting the HTTP server.
 *
 * This server supports:
 * <ul>
 *     <li>Login and token-based authentication using JWT.</li>
 *     <li>Protected REST endpoints for credentials, discovery, and provision resources.</li>
 *     <li>Support for access and refresh tokens with secure cookie handling.</li>
 * </ul>
 *
 * Routes are protected with {@link JWTAuthHandler}, and the server uses a keystore
 * for signing JWTs. All route handlers are initialized through corresponding route
 * classes like {@link CredentialRoutes}, {@link DiscoveryRoutes}, and {@link ProvisionRoutes}.
 */
public class HttpServerVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

    private static final Dotenv dotenv = Dotenv.load();

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            var router = Router.router(vertx);

            router.route().handler(BodyHandler.create());

            var jwtAuthOptions = new JWTAuthOptions()
                    .setKeyStore(new KeyStoreOptions()
                            .setPath("keystore.jceks")
                            .setType("jceks")
                            .setPassword(dotenv.get("KEYSTORE_PASSWORD")));

            var jwtAuth = JWTAuth.create(vertx, jwtAuthOptions);

            //demo login
            router.post("/login").handler(ctx ->
            {
                try
                {
                    var body = ctx.body().asJsonObject();

                    var username = body.getString("username");

                    if ("shaunak".equals(username) && "Mind@123".equals(body.getString("password")))
                    {
                        var claims = new JsonObject()
                                .put("username", username)
                                .put("role", "admin");

                        ctx.response()
                                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                                .putHeader("Set-Cookie", "refresh_token=" + jwtAuth.generateToken(claims, new JWTOptions().setExpiresInMinutes(60 * 24 * 7)) +
                                        "; HttpOnly; SameSite=Strict; Path=/refresh") // Secure in production
                                .end(new JsonObject().put("access_token", jwtAuth.generateToken(claims, new JWTOptions().setExpiresInMinutes(15))).encodePrettily());
                    }
                    else
                    {
                        ctx.response().setStatusCode(401).end("Invalid credentials");
                    }
                }
                catch (Exception e)
                {
                    LOGGER.error(e.getMessage());
                }
            });

            router.post("/refresh").handler(ctx ->
            {
                try
                {
                    var refreshCookie = ctx.request().cookies().stream()
                            .filter(c -> c.getName().equals("refresh_token"))
                            .findFirst();

                    if (refreshCookie.isEmpty())
                    {
                        ctx.response().setStatusCode(401).end("Missing refresh token");

                        return;
                    }

                    jwtAuth.authenticate(new JsonObject().put("token", refreshCookie.get().getValue())).onSuccess(user ->
                    {
                        try
                        {
                            var claims = user.principal();

                            var newAccessToken = jwtAuth.generateToken(
                                    new JsonObject()
                                            .put("username", claims.getString("username"))
                                            .put("role", claims.getString("role")),
                                    new JWTOptions().setExpiresInMinutes(15)
                            );

                            ctx.response()
                                    .putHeader("Content-Type", "application/json")
                                    .end(new JsonObject().put("access_token", newAccessToken).encodePrettily());
                        }
                        catch (Exception e)
                        {
                            LOGGER.error(e.getMessage());
                        }
                    }).onFailure(err -> ctx.response().setStatusCode(401).end("Invalid or expired refresh token"));
                }
                catch (Exception e)
                {
                    LOGGER.error(e.getMessage());
                }

            });


            router.route("/credentials/*").handler(JWTAuthHandler.create(jwtAuth));

            router.route("/discovery/*").handler(JWTAuthHandler.create(jwtAuth));

            router.route("/provision/*").handler(JWTAuthHandler.create(jwtAuth));

            new CredentialRoutes().init(router);

            new DiscoveryRoutes().init(router);

            new ProvisionRoutes().init(router);

            router.route().failureHandler(ctx ->
            {
                try
                {
                    if (ctx.statusCode() == 401)
                    {
                        ctx.response()
                                .setStatusCode(401)
                                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                                .end(new JsonObject().put("error", "Unauthorized: Please login first").encodePrettily());
                    }
                    else
                    {
                        ctx.next();
                    }
                }
                catch (Exception e)
                {
                    LOGGER.error(e.getMessage());
                }
            });

            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(8888, http ->
                    {
                        try
                        {
                            if (http.succeeded())
                            {
                                startPromise.complete();
                            }
                            else
                            {
                                startPromise.fail(http.cause());
                            }
                        }
                        catch (Exception e)
                        {
                            LOGGER.error(e.getMessage());

                            startPromise.fail(e.getMessage());
                        }
                    });
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage());

            startPromise.fail(e.getMessage());
        }

    }
}
