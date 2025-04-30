package org.example.server;

import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.example.routes.CredentialRoutes;
import org.example.routes.DiscoveryRoutes;
import org.example.routes.ProvisionRoutes;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.example.utils.LoggerUtil;

import java.util.logging.Logger;

import static org.example.constants.AppConstants.Headers.*;

public class HttpServerVerticle extends AbstractVerticle
{
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

                    var token = jwtAuth.generateToken(claims, new JWTOptions().setExpiresInMinutes(1000));

                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("token", token).encodePrettily());
                }
                else
                {
                    ctx.response().setStatusCode(401).end("Invalid credentials");
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
            LoggerUtil.getMainLogger().severe(e.getMessage());

            startPromise.fail(e.getMessage());
        }

    }
}
