package com.common.Encryption;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Base64;

import static com.common.Constant.constant.*;

/**
 * AES 加密工具（确定性密钥：SHA-256 派生，同一种子在任何 JVM/环境下结果一致）
 */
public class Aes {

    public static final String encrypt(String plainText) {
        Key secretKey = getKey(AES_SORT);
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] result = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static final String decrypt(String cipherText) {
        Key secretKey = getKey(AES_SORT);
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] c = Base64.getDecoder().decode(cipherText);
            byte[] result = cipher.doFinal(c);
            return new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 使用 SHA-256 从种子派生固定 16 字节 AES-128 密钥。
     */
    public static Key getKey(String keySeed) {
        if (keySeed == null) {
            keySeed = System.getenv("RENTAL_PRODUCT_AES_SECRET");
        }
        if (keySeed == null) {
            keySeed = System.getProperty("rental.product.aes-secret");
        }
        if (keySeed == null || keySeed.trim().length() == 0) {
            throw new IllegalStateException("AES key seed is empty");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(keySeed.getBytes(StandardCharsets.UTF_8));
            byte[] keyBytes = new byte[16];
            System.arraycopy(hash, 0, keyBytes, 0, 16);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
