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
                var body = ctx.body().asJsonObject();

                var username = body.getString("username");

                var password = body.getString("password");

                if ("shaunak".equals(username) && "Mind@123".equals(password))
                {
                    var claims = new JsonObject()
                            .put("username", username)
                            .put("role", "admin");

                    // Access Token - short-lived
                    var accessToken = jwtAuth.generateToken(claims, new JWTOptions().setExpiresInMinutes(15));

                    // Refresh Token - long-lived
                    var refreshToken = jwtAuth.generateToken(claims, new JWTOptions().setExpiresInMinutes(60 * 24 * 7)); // 7 days

                    ctx.response()
                            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                            .putHeader("Set-Cookie", "refresh_token=" + refreshToken +
                                    "; HttpOnly; SameSite=Strict; Path=/refresh") // Secure in production
                            .end(new JsonObject().put("access_token", accessToken).encodePrettily());
                }
                else
                {
                    ctx.response().setStatusCode(401).end("Invalid credentials");
                }
            });

            router.post("/refresh").handler(ctx ->
            {
                var cookies = ctx.request().cookies();

                var refreshCookie = cookies.stream()
                        .filter(c -> c.getName().equals("refresh_token"))
                        .findFirst();

                if (refreshCookie.isEmpty())
                {
                    ctx.response().setStatusCode(401).end("Missing refresh token");

                    return;
                }

                var refreshToken = refreshCookie.get().getValue();

                jwtAuth.authenticate(new JsonObject().put("token", refreshToken)).onSuccess(user ->
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
                }).onFailure(err -> {
                    ctx.response().setStatusCode(401).end("Invalid or expired refresh token");
                });
            });


            router.route("/credentials/*").handler(JWTAuthHandler.create(jwtAuth));

            router.route("/discovery/*").handler(JWTAuthHandler.create(jwtAuth));

            router.route("/provision/*").handler(JWTAuthHandler.create(jwtAuth));

            new CredentialRoutes().init(router);

            new DiscoveryRoutes().init(router);

            new ProvisionRoutes().init(router);

            router.route().failureHandler(ctx ->
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
                    ctx.next(); // let Vert.x handle other errors as usual
                }
            });

            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(8888, http ->
                    {
                        if (http.succeeded())
                        {
                            startPromise.complete();
                        }
                        else
                        {
                            startPromise.fail(http.cause());
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
