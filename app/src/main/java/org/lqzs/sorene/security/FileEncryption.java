package org.lqzs.sorene.security;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

public class FileEncryption {
    private static final String LOG_TAG = "FileEncryption";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom;

    public FileEncryption(byte[] keyBytes) {
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    public InputStream getDecryptingInputStream(InputStream inputStream) throws IOException {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            if (inputStream.read(iv) != GCM_IV_LENGTH) {
                throw new IOException("Failed to read IV");
            }

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            return new CipherInputStream(inputStream, cipher);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException e) {
            Log.e(LOG_TAG, "Error initializing decryption", e);
            throw new IOException("Failed to initialize decryption", e);
        }
    }

    public OutputStream getEncryptingOutputStream(OutputStream outputStream) throws IOException {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            outputStream.write(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            return new CipherOutputStream(outputStream, cipher);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException e) {
            Log.e(LOG_TAG, "Error initializing encryption", e);
            throw new IOException("Failed to initialize encryption", e);
        }
    }
} 