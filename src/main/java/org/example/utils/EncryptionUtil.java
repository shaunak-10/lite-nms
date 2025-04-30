package org.example.utils;

import io.github.cdimascio.dotenv.Dotenv;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class EncryptionUtil
{

    private static final Dotenv dotenv = Dotenv.load();

    private static final String SECRET_KEY = dotenv.get("ENCRYPTION_SECRET");

    private static final SecretKeySpec KEY_SPEC = new SecretKeySpec(
            Base64.getDecoder().decode(SECRET_KEY),
            "AES"
    );

    public static String encrypt(String plainText) throws Exception
    {
        var cipher = Cipher.getInstance("AES");

        cipher.init(Cipher.ENCRYPT_MODE, KEY_SPEC);

        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes()));
    }
}
