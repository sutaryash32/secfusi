package com.secufusion.events.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.stream.Collectors;

/**
 * ApiKeyUtil
 *
 * Utility class for API key generation and hashing.
 */
public class ApiKeyUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CANDIDATE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                                                   "abcdefghijklmnopqrstuvwxyz" +
                                                   "0123456789" +
                                                   "!@#$%^&*()=+[]{};:,.<>?";

    /**
     * Generate a secure random API key
     *
     * @param length Length of the key (recommended: 64)
     * @return Random API key
     */
    public static String generateApiKey(int length) {
        return SECURE_RANDOM.ints(length, 0, CANDIDATE_CHARS.length())
            .mapToObj(CANDIDATE_CHARS::charAt)
            .map(Object::toString)
            .collect(Collectors.joining());
    }

    /**
     * Hash API key using SHA-256
     *
     * @param rawKey Raw API key
     * @return SHA-256 hash in hex format
     */
    public static String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();

        } catch (Exception e) {
            throw new RuntimeException("Error hashing API key", e);
        }
    }

    /**
     * Extract prefix from raw key (first 12 characters)
     *
     * @param rawKey Raw API key
     * @return Key prefix
     */
    public static String extractPrefix(String rawKey) {
        return rawKey.substring(0, Math.min(12, rawKey.length()));
    }
}
