package org.example.services.server.handlers;

import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.example.MainApp;
import org.example.utils.ConfigLoader;

import static org.example.constants.AppConstants.Headers.*;
import static org.example.constants.AppConstants.JsonKey.ERROR;

/**
 * Handles authentication-related operations including:
 * <ul>
 *     <li>JWT authentication configuration and setup</li>
 *     <li>Login route setup</li>
 *     <li>Refresh token route setup</li>
 *     <li>Protection of secured routes</li>
 * </ul>
 */
public class AuthHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthHandler.class);

    private static final Dotenv dotenv = Dotenv.load();

    private final JWTAuth jwtAuth;

    /**
     * Creates a new AuthHandler with the specified Vertx instance
     */
    public AuthHandler()
    {
        var jwtAuthOptions = new JWTAuthOptions()
                .setKeyStore(new KeyStoreOptions()
                        .setPath("keystore.jceks")
                        .setType("jceks")
                        .setPassword(dotenv.get("KEYSTORE_PASSWORD")));

        this.jwtAuth = JWTAuth.create(MainApp.getVertx(), jwtAuthOptions);
    }

    /**
     * Sets up authentication routes and secures protected routes
     * @param router The router to add routes to
     */
    public void setupAuth(Router router)
    {
        try
        {
            setupLoginRoute(router);

            setupRefreshRoute(router);

            secureRoutes(router);
        }
        catch (Exception exception)
        {
            LOGGER.error("Error setting up authentication routes", exception);
        }

    }

    /**
     * Sets up the login route for user authentication
     * @param router The router to add the login route to
     */
    private void setupLoginRoute(Router router)
    {
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
                            .putHeader("Set-Cookie", "refresh_token=" + jwtAuth.generateToken(
                                    claims,
                                    new JWTOptions().setExpiresInMinutes(ConfigLoader.get().getInteger("jwt.refresh.token.expire",60*24*7))) +
                                    "; HttpOnly; SameSite=Strict; Path=/refresh")
                            .end(new JsonObject()
                                    .put("access_token", jwtAuth.generateToken(
                                            claims,
                                            new JWTOptions().setExpiresInMinutes(ConfigLoader.get().getInteger("jwt.access.token.expire", 15))))
                                    .encodePrettily());
                }
                else
                {
                    ctx.response().setStatusCode(401).end("Invalid credentials");
                }
            }
            catch (Exception exception)
            {
                LOGGER.error("Error in login handler", exception);

                ctx.response().setStatusCode(500).end("Internal server error");
            }
        });
    }

    /**
     * Sets up the refresh token route for token renewal
     * @param router The router to add the refresh route to
     */
    private void setupRefreshRoute(Router router)
    {
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

                jwtAuth.authenticate(new TokenCredentials(refreshCookie.get().getValue()))
                        .onSuccess(user ->
                        {
                            try
                            {
                                var claims = user.principal();

                                var newAccessToken = jwtAuth.generateToken(
                                        new JsonObject()
                                                .put("username", claims.getString("username"))
                                                .put("role", claims.getString("role")),
                                        new JWTOptions().setExpiresInMinutes(ConfigLoader.get().getInteger("jwt.access.token.expire", 15))
                                );

                                ctx.response()
                                        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                                        .end(new JsonObject()
                                                .put("access_token", newAccessToken)
                                                .encodePrettily());
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Error generating new access token", exception);

                                ctx.response().setStatusCode(500).end("Internal server error");
                            }
                        })
                        .onFailure(error -> ctx.response()
                                .setStatusCode(401)
                                .end("Invalid or expired refresh token"));
            }
            catch (Exception exception)
            {
                LOGGER.error("Error in refresh token handler", exception);

                ctx.response().setStatusCode(500).end("Internal server error");
            }
        });
    }

    /**
     * Protects routes that require authentication
     * @param router The router to add protection to
     */
    private void secureRoutes(Router router)
    {
        // Protect API routes with JWT authentication
        router.route("/credentials/*").handler(JWTAuthHandler.create(jwtAuth));

        router.route("/discovery/*").handler(JWTAuthHandler.create(jwtAuth));

        router.route("/provision/*").handler(JWTAuthHandler.create(jwtAuth));

        // Add a failure handler for authentication errors
        router.route().failureHandler(ctx ->
        {
            try
            {
                if (ctx.statusCode() == 401)
                {
                    ctx.response()
                            .setStatusCode(401)
                            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                            .end(new JsonObject()
                                    .put(ERROR, "Unauthorized: Please login first")
                                    .encodePrettily());
                }
                else
                {
                    ctx.next();
                }
            }
            catch (Exception exception)
            {
                LOGGER.error("Error in failure handler", exception);
            }
        });
    }
}