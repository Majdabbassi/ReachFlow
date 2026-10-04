package com.majd.reachflow.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Transparent field-level encryption for secrets stored in the database (Gmail app passwords).
 *
 * <p>New values are written as {@code v2:} + Base64(IV || ciphertext) using AES-256-GCM with a
 * fresh random IV, so equal passwords produce different ciphertext and tampering is detected.
 * Values written by the first version of this class (plain AES/ECB, no prefix) are still read,
 * and are upgraded the next time the entity is saved.
 *
 * <p>A {@link Cipher} is not thread-safe, so one is created per call (campaign sends and web
 * requests decrypt concurrently).
 */
@Converter
@Component
public class AttributeEncryptor implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(AttributeEncryptor.class);

    static final String DEFAULT_DEV_KEY = "my-secret-key-123";
    private static final String PREFIX = "v2:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec gcmKey;
    private final SecretKeySpec legacyKey;
    private final SecureRandom random = new SecureRandom();

    public AttributeEncryptor(@Value("${encryption.key:" + DEFAULT_DEV_KEY + "}") String secret) throws Exception {
        if (DEFAULT_DEV_KEY.equals(secret)) {
            log.warn("encryption.key is not set: stored app passwords use the public development key. "
                    + "Fine for a local run; set ENCRYPTION_KEY for anything else.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.gcmKey = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secretBytes), "AES");

        // Same derivation as the original implementation, kept only to read old rows.
        byte[] legacyBytes = new byte[16];
        System.arraycopy(secretBytes, 0, legacyBytes, 0, Math.min(secretBytes.length, 16));
        this.legacyKey = new SecretKeySpec(legacyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, gcmKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] out = Arrays.copyOf(iv, IV_BYTES + encrypted.length);
            System.arraycopy(encrypted, 0, out, IV_BYTES, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            if (dbData.startsWith(PREFIX)) {
                byte[] in = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, gcmKey, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
                return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
            }
            Cipher legacy = Cipher.getInstance("AES");
            legacy.init(Cipher.DECRYPT_MODE, legacyKey);
            return new String(legacy.doFinal(Base64.getDecoder().decode(dbData)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt value", e);
        }
    }
}
