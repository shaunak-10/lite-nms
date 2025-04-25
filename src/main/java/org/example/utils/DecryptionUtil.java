package org.example.utils;

import io.github.cdimascio.dotenv.Dotenv;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class DecryptionUtil
{
    private static final Dotenv dotenv = Dotenv.load();

    private static final String SECRET_KEY = dotenv.get("ENCRYPTION_SECRET");

    private static final SecretKeySpec KEY_SPEC = new SecretKeySpec(
            Base64.getDecoder().decode(SECRET_KEY),
            "AES"
    );

    public static String decrypt(String encryptedText) throws Exception
    {
        Cipher cipher = Cipher.getInstance("AES");

        cipher.init(Cipher.DECRYPT_MODE, KEY_SPEC);

        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));

        return new String(decrypted);
    }
}
