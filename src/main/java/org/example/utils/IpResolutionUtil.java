package org.example.utils;

import java.net.InetAddress;

public class IpResolutionUtil {

    /**
     * Resolves and validates an IP address or hostname.
     * This method performs synchronous hostname resolution and should be called from within an executeBlocking context.
     *
     * @param hostOrIp The hostname or IP address to resolve
     * @return The resolved IP address as a string, or null if resolution fails
     */
    public static String resolveAndValidateIp(String hostOrIp)
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
        catch (Exception exception)
        {
            return null;
        }
    }
}