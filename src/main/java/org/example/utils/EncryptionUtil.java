package org.example.utils;

import io.github.cdimascio.dotenv.Dotenv;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
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
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        // Generate random IV
        var iv = new byte[16];

        new SecureRandom().nextBytes(iv);

        // Encrypt
        cipher.init(Cipher.ENCRYPT_MODE, KEY_SPEC, new IvParameterSpec(iv));

        var encrypted = cipher.doFinal(plainText.getBytes());

        // Prepend IV to ciphertext
        var byteBuffer = ByteBuffer.allocate(16 + encrypted.length);

        byteBuffer.put(iv);

        byteBuffer.put(encrypted);

        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }
}
