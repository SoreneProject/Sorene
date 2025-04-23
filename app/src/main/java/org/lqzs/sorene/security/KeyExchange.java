package org.lqzs.sorene.security;

import android.util.Log;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import javax.crypto.KeyAgreement;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;

public class KeyExchange {
    private static final String LOG_TAG = "KeyExchange";
    private static final String KEY_ALGORITHM = "EC";
    private static final String KEY_AGREEMENT_ALGORITHM = "ECDH";

    private final KeyPair keyPair;
    private final SecureRandom secureRandom;

    public KeyExchange() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        keyPairGenerator.initialize(256);
        this.keyPair = keyPairGenerator.generateKeyPair();
        this.secureRandom = new SecureRandom();
    }

    public byte[] getPublicKey() {
        return keyPair.getPublic().getEncoded();
    }

    public byte[] generateSharedSecret(byte[] peerPublicKeyBytes) throws IOException {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(peerPublicKeyBytes);
            PublicKey peerPublicKey = keyFactory.generatePublic(keySpec);

            KeyAgreement keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM);
            keyAgreement.init(keyPair.getPrivate());
            keyAgreement.doPhase(peerPublicKey, true);

            byte[] sharedSecret = keyAgreement.generateSecret();
            return deriveAESKey(sharedSecret);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error generating shared secret", e);
            throw new IOException("Failed to generate shared secret", e);
        }
    }

    private byte[] deriveAESKey(byte[] sharedSecret) {
        byte[] aesKey = new byte[32];
        System.arraycopy(sharedSecret, 0, aesKey, 0, Math.min(sharedSecret.length, 32));
        return aesKey;
    }
} 