package com.cvs.orchestrator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption/decryption for the secrets store.
 *
 * Key is provided via the SECRETS_ENCRYPTION_KEY environment variable
 * (32 bytes base64-encoded = 256-bit key). A random 12-byte IV is
 * prepended to every ciphertext so the same plaintext produces different
 * output.
 *
 * Quickstart — generate a key:
 * openssl rand -base64 32
 */
@Slf4j
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;

    public EncryptionService(
            @Value("${orchestrator.secrets.encryption-key:}") String base64Key) {

        if (base64Key == null || base64Key.isBlank()) {
            // Auto-generate an ephemeral key if none is configured.
            // Secrets encrypted with it survive only for the container lifetime.
            // Warn loudly so operators know to set a persistent key.
            byte[] ephemeral = new byte[32];
            new SecureRandom().nextBytes(ephemeral);
            this.secretKey = new SecretKeySpec(ephemeral, "AES");
            log.warn("⚠️  SECRETS_ENCRYPTION_KEY not set — using ephemeral key. " +
                    "Secrets will be lost on container restart. " +
                    "Generate a persistent key with: openssl rand -base64 32");
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException(
                        "SECRETS_ENCRYPTION_KEY must be 32 bytes base64-encoded (got " +
                                keyBytes.length + " bytes). Generate with: openssl rand -base64 32");
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("Encryption service initialised with configured 256-bit key");
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     * Returns base64(IV || ciphertext || GCM-tag).
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // Prepend IV so decryption can recover it
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt secret", e);
        }
    }

    /**
     * Decrypts base64(IV || ciphertext || GCM-tag) back to plaintext.
     */
    public String decrypt(String base64Encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(base64Encrypted);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt secret", e);
        }
    }
}
