package org.example.utils;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class KeyGenUtil
{

    public static void main(String[] args) throws Exception
    {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");

        keyGen.init(128);

        SecretKey secretKey = keyGen.generateKey();

        String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());

        System.out.println("Generated AES Key (store securely!): " + encodedKey);
    }
}
