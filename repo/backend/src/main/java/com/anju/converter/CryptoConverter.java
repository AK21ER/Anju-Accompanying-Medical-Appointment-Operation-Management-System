package com.anju.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BIT_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final SecretKeySpec SECRET_KEY = buildSecretKey();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, new GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            ByteBuffer payload = ByteBuffer.allocate(iv.length + encrypted.length);
            payload.put(iv);
            payload.put(encrypted);
            return Base64.getEncoder().encodeToString(payload.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt sensitive field.");
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        try {
            byte[] encryptedPayload = Base64.getDecoder().decode(dbData);
            ByteBuffer payload = ByteBuffer.wrap(encryptedPayload);

            byte[] iv = new byte[GCM_IV_LENGTH];
            payload.get(iv);

            byte[] encrypted = new byte[payload.remaining()];
            payload.get(encrypted);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, new GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt sensitive field.");
        }
    }

    private static SecretKeySpec buildSecretKey() {
        String rawKey = System.getenv("ANJU_CRYPTO_KEY");
        if (rawKey == null || rawKey.isBlank()) {
            rawKey = System.getProperty("anju.crypto.key");
        }

        if (rawKey == null || rawKey.isBlank()) {
            String profiles = System.getenv("SPRING_PROFILES_ACTIVE");
            boolean testProfile = profiles != null && profiles.toLowerCase().contains("test");
            if (!testProfile) {
                throw new IllegalStateException("Missing encryption key. Set ANJU_CRYPTO_KEY or anju.crypto.key.");
            }
            rawKey = "local-test-only-key";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = Arrays.copyOf(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)), 16);
            return new SecretKeySpec(key, AES_ALGORITHM);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize encryption key.");
        }
    }
}
