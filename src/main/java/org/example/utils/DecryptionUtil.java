package org.example.utils;

import io.github.cdimascio.dotenv.Dotenv;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
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
        var data = Base64.getDecoder().decode(encryptedText);

        // Extract IV and ciphertext
        var byteBuffer = ByteBuffer.wrap(data);

        var iv = new byte[16];

        byteBuffer.get(iv);

        var ciphertext = new byte[byteBuffer.remaining()];

        byteBuffer.get(ciphertext);

        // Decrypt
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        cipher.init(Cipher.DECRYPT_MODE, KEY_SPEC, new IvParameterSpec(iv));

        return new String(cipher.doFinal(ciphertext));
    }
}
