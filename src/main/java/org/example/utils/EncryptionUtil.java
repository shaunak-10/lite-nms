package org.example.utils;

import io.github.cdimascio.dotenv.Dotenv;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for encrypting plaintext strings using AES encryption in CBC mode with PKCS5 padding.
 * <p>
 * The encryption key is loaded from a `.env` file using the key {@code ENCRYPTION_SECRET}, which must be Base64-encoded.
 * The generated ciphertext is Base64-encoded with the 16-byte IV prepended.
 * </p>
 */
public class EncryptionUtil
{

    private static final Dotenv dotenv = Dotenv.load();

    private static final String SECRET_KEY = dotenv.get("ENCRYPTION_SECRET");

    private static final SecretKeySpec KEY_SPEC = new SecretKeySpec(
            Base64.getDecoder().decode(SECRET_KEY),
            "AES"
    );

    /**
     * Encrypts a plaintext string using AES/CBC/PKCS5Padding.
     * <p>
     * A random 16-byte IV is generated for each encryption. The resulting encrypted bytes
     * are prefixed with the IV and Base64-encoded into a single string.
     * </p>
     *
     * @param plainText The plaintext string to encrypt.
     * @return The Base64-encoded string containing the IV and ciphertext.
     * @throws Exception If encryption fails due to key configuration or cipher error.
     */
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
