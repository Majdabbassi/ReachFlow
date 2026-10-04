package com.majd.reachflow.config;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeEncryptorTest {

    private final AttributeEncryptor encryptor = newEncryptor("unit-test-key");

    private static AttributeEncryptor newEncryptor(String key) {
        try {
            return new AttributeEncryptor(key);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void roundTripsIncludingUnicode() {
        for (String secret : List.of("abcd efgh ijkl mnop", "pässwörd-€-🔑", "x")) {
            assertEquals(secret, encryptor.convertToEntityAttribute(encryptor.convertToDatabaseColumn(secret)));
        }
    }

    @Test
    void nullStaysNull() {
        assertNull(encryptor.convertToDatabaseColumn(null));
        assertNull(encryptor.convertToEntityAttribute(null));
    }

    @Test
    void sameInputGivesDifferentCiphertext() {
        // The first version used plain AES/ECB, which maps equal passwords to equal ciphertext.
        String a = encryptor.convertToDatabaseColumn("same-password");
        String b = encryptor.convertToDatabaseColumn("same-password");
        assertNotEquals(a, b);
        assertTrue(a.startsWith("v2:"));
    }

    @Test
    void tamperedOrWrongKeyIsRejected() {
        String stored = encryptor.convertToDatabaseColumn("secret");
        char last = stored.charAt(stored.length() - 2);
        String tampered = stored.substring(0, stored.length() - 2) + (last == 'A' ? 'B' : 'A') + stored.charAt(stored.length() - 1);
        assertThrows(IllegalStateException.class, () -> encryptor.convertToEntityAttribute(tampered));
        assertThrows(IllegalStateException.class, () -> newEncryptor("another-key").convertToEntityAttribute(stored));
    }

    @Test
    void stillReadsValuesWrittenByTheFirstVersion() throws Exception {
        // Reproduces how the original converter encrypted: AES/ECB, key = first 16 bytes of the secret.
        byte[] keyBytes = new byte[16];
        byte[] secretBytes = "unit-test-key".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, 16));
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
        String legacy = Base64.getEncoder().encodeToString(cipher.doFinal("old-password".getBytes(StandardCharsets.UTF_8)));

        assertEquals("old-password", encryptor.convertToEntityAttribute(legacy));
    }

    // Regression: the converter used to share one Cipher between threads. Campaign sends decrypt the
    // Gmail password while web requests read clients, and a shared Cipher corrupts under that.
    @Test
    void isSafeUnderConcurrentUse() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                final int id = t;
                Callable<Boolean> task = () -> {
                    for (int i = 0; i < 500; i++) {
                        String secret = "thread-" + id + "-secret-" + i;
                        if (!secret.equals(encryptor.convertToEntityAttribute(encryptor.convertToDatabaseColumn(secret)))) {
                            return false;
                        }
                    }
                    return true;
                };
                results.add(pool.submit(task));
            }
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
