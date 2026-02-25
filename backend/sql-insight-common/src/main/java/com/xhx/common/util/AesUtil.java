package com.xhx.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具
 *
 * <p>每次加密生成随机 IV，防止相同明文产生相同密文。
 * 输出格式：Base64(IV[12字节] + 密文 + GCM认证标签[16字节])
 *
 * @author master
 */
public class AesUtil {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH    = 12;
    private static final int    TAG_BIT_LEN  = 128;

    private AesUtil() {}

    /**
     * 加密
     *
     * @param plaintext   明文
     * @param base64Key   32字节 AES 密钥（Base64 编码），可用 {@code openssl rand -base64 32} 生成
     * @return Base64 编码的密文（含 IV 前缀）
     */
    public static String encrypt(String plaintext, String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BIT_LEN, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 将 IV 拼接在密文前
            byte[] result = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密
     *
     * @param cipherBase64 Base64 编码的密文（由 {@link #encrypt} 生成）
     * @param base64Key    同加密时使用的密钥
     * @return 原始明文
     */
    public static String decrypt(String cipherBase64, String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] raw = Base64.getDecoder().decode(cipherBase64);

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);

            byte[] encrypted = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BIT_LEN, iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
