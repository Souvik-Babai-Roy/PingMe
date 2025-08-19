package com.pingme.android.utils;

import android.util.Base64;
import android.util.Log;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionUtil {
    private static final String TAG = "EncryptionUtil";
    private static final String ALGORITHM = "RSA";
    private static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    /**
     * Generate RSA key pair for user
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM);
            keyPairGenerator.initialize(2048, new SecureRandom());
            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            Log.e(TAG, "Error generating key pair", e);
            return null;
        }
    }

    /**
     * Generate AES key for message encryption
     */
    public static SecretKey generateAESKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
            keyGenerator.init(AES_KEY_SIZE);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            Log.e(TAG, "Error generating AES key", e);
            return null;
        }
    }

    /**
     * Encrypt message with AES key
     */
    public static String encryptMessage(String message, SecretKey secretKey) {
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);
            
            byte[] encryptedData = cipher.doFinal(message.getBytes());
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
            
            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error encrypting message", e);
            return null;
        }
    }

    /**
     * Decrypt message with AES key
     */
    public static String decryptMessage(String encryptedMessage, SecretKey secretKey) {
        try {
            byte[] combined = Base64.decode(encryptedMessage, Base64.DEFAULT);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[combined.length - GCM_IV_LENGTH];
            
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedData, 0, encryptedData.length);
            
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData);
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting message", e);
            return null;
        }
    }

    /**
     * Encrypt AES key with RSA public key
     */
    public static String encryptAESKey(SecretKey secretKey, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedKey = cipher.doFinal(secretKey.getEncoded());
            return Base64.encodeToString(encryptedKey, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error encrypting AES key", e);
            return null;
        }
    }

    /**
     * Decrypt AES key with RSA private key
     */
    public static SecretKey decryptAESKey(String encryptedKey, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedKeyBytes = cipher.doFinal(Base64.decode(encryptedKey, Base64.DEFAULT));
            return new SecretKeySpec(decryptedKeyBytes, AES_ALGORITHM);
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting AES key", e);
            return null;
        }
    }

    /**
     * Convert public key to string
     */
    public static String publicKeyToString(PublicKey publicKey) {
        return Base64.encodeToString(publicKey.getEncoded(), Base64.DEFAULT);
    }

    /**
     * Convert private key to string
     */
    public static String privateKeyToString(PrivateKey privateKey) {
        return Base64.encodeToString(privateKey.getEncoded(), Base64.DEFAULT);
    }

    /**
     * Convert string to public key
     */
    public static PublicKey stringToPublicKey(String keyString) {
        try {
            byte[] keyBytes = Base64.decode(keyString, Base64.DEFAULT);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance(ALGORITHM);
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            Log.e(TAG, "Error converting string to public key", e);
            return null;
        }
    }

    /**
     * Convert string to private key
     */
    public static PrivateKey stringToPrivateKey(String keyString) {
        try {
            byte[] keyBytes = Base64.decode(keyString, Base64.DEFAULT);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance(ALGORITHM);
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            Log.e(TAG, "Error converting string to private key", e);
            return null;
        }
    }

    /**
     * Generate a secure random string for key exchange
     */
    public static String generateSecureRandomString(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }
}