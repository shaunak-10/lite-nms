package org.example.utils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

public class IpResolutionUtil {

    public static Future<String> resolveAndValidateIp(Vertx vertx, String hostOrIp)
    {
        var ipFuture = vertx.executeBlocking(() ->
        {
            try
            {
                if (hostOrIp == null || hostOrIp.isEmpty())
                {
                    return null;
                }

                var inetAddress = InetAddress.getByName(hostOrIp);

                return inetAddress.getHostAddress();
            }
            catch (Exception e)
            {
                return null;
            }
        }, false);

        return ipFuture.timeout(2000, TimeUnit.SECONDS);
    }
}
