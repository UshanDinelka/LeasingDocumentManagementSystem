package com.leasingdocument.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Port of the existing integrity-backend AES-256-GCM implementation.
 * The key is created once and reused from secure-storage/aes.key.
 */
@Service
public class EncryptionService {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final Path keyFile;
    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(
            @Value("${app.secure-storage.key-file:secure-storage/aes.key}")
            String keyFilePath
    ) {
        this.keyFile = Paths.get(keyFilePath)
                .toAbsolutePath()
                .normalize();

        try {
            this.secretKey = loadOrCreateKey();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Unable to initialize AES-256-GCM encryption key",
                    e
            );
        }
    }

    public EncryptedDocument encrypt(byte[] documentBytes) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec =
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    gcmParameterSpec
            );

            byte[] encryptedBytes =
                    cipher.doFinal(documentBytes);

            return new EncryptedDocument(
                    encryptedBytes,
                    iv
            );

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Document encryption failed",
                    e
            );
        }
    }

    public byte[] decrypt(
            byte[] encryptedData,
            byte[] iv
    ) throws GeneralSecurityException {

        Cipher cipher =
                Cipher.getInstance(AES_TRANSFORMATION);

        GCMParameterSpec gcmParameterSpec =
                new GCMParameterSpec(
                        GCM_TAG_LENGTH_BITS,
                        iv
                );

        cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                gcmParameterSpec
        );

        return cipher.doFinal(encryptedData);
    }

    private SecretKey loadOrCreateKey()
            throws IOException, GeneralSecurityException {

        if (Files.exists(keyFile)) {
            String encodedKey =
                    Files.readString(
                            keyFile,
                            StandardCharsets.UTF_8
                    ).trim();

            byte[] decodedKey =
                    Base64.getDecoder()
                            .decode(encodedKey);

            if (decodedKey.length != 32) {
                throw new GeneralSecurityException(
                        "Stored AES key is not 256 bits"
                );
            }

            return new SecretKeySpec(
                    decodedKey,
                    AES_ALGORITHM
            );
        }

        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(AES_ALGORITHM);

        keyGenerator.init(256);

        SecretKey newKey =
                keyGenerator.generateKey();

        Path parent = keyFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String encodedKey =
                Base64.getEncoder()
                        .encodeToString(
                                newKey.getEncoded()
                        );

        Files.writeString(
                keyFile,
                encodedKey,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );

        return newKey;
    }

    public static class EncryptedDocument {

        private final byte[] encryptedData;
        private final byte[] iv;

        public EncryptedDocument(
                byte[] encryptedData,
                byte[] iv
        ) {
            this.encryptedData = encryptedData;
            this.iv = iv;
        }

        public byte[] getEncryptedData() {
            return encryptedData;
        }

        public byte[] getIv() {
            return iv;
        }
    }
}
