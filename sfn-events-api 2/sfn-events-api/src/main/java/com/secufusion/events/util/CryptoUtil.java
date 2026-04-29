package com.secufusion.events.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * CryptoUtil
 *
 * Utility for encrypting and decrypting sensitive data using AES-256.
 * Used for encrypting Keycloak client secrets before storage.
 */
public class CryptoUtil {

    private static final String SECRET = "MySuperSecretKey12345678901234!@"; // 32 bytes — AES-256

    private static final String ALGORITHM = "AES";

    /**
     * Encrypt a plain text value using AES-256
     *
     * @param value Plain text to encrypt
     * @return Base64-encoded encrypted string
     */
    public static String encrypt(String value) {
        try {
            SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Encryption error", e);
        }
    }

    /**
     * Decrypt an encrypted value using AES-256
     *
     * @param encrypted Base64-encoded encrypted string
     * @return Decrypted plain text
     */
    public static String decrypt(String encrypted) {
        try {
            SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
        } catch (Exception e) {
            throw new RuntimeException("Decryption error", e);
        }
    }
}
