package org.example.utils;

import io.github.cdimascio.dotenv.Dotenv;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * Utility class for decrypting AES-encrypted strings using a secret key loaded from environment variables.
 * <p>
 * This utility expects the encrypted input to be Base64-encoded with the IV prepended to the ciphertext.
 * The AES key is loaded from a `.env` file using the key {@code ENCRYPTION_SECRET}, which must be Base64-encoded.
 * </p>
 */
public class DecryptionUtil
{

    private static final Dotenv dotenv = Dotenv.load();

    private static final String SECRET_KEY = dotenv.get("ENCRYPTION_SECRET");

    private static final SecretKeySpec KEY_SPEC = new SecretKeySpec(
            Base64.getDecoder().decode(SECRET_KEY),
            "AES"
    );

    /**
     * Decrypts a Base64-encoded AES-encrypted string using CBC mode with PKCS5 padding.
     * <p>
     * The input must have the 16-byte IV prepended to the ciphertext, all Base64-encoded.
     * The AES key is expected to be provided as a Base64-encoded string via an environment variable.
     * </p>
     *
     * @param encryptedText The encrypted text, Base64-encoded with IV plus ciphertext.
     * @return The decrypted plaintext string.
     * @throws Exception If decryption fails due to invalid input, key, or cipher configuration.
     */
    public static String decrypt(String encryptedText) throws Exception
    {
        // Extract IV and ciphertext
        var byteBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(encryptedText));

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
