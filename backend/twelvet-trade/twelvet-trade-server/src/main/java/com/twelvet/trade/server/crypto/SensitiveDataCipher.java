package com.twelvet.trade.server.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段加解密（AES/GCM/NoPadding）
 * <p>
 * 密钥来自 {@code trade.settlement.crypto-key}（Base64，解码后必须 32 字节）。空密钥/长度不符在构造期直接失败，
 * 绝不静默降级为明文。输出格式 {@code Base64(iv || ciphertext || tag)}，IV 每次随机 12 字节，因此同一明文两次加密结果不同。
 * </p>
 *
 * @author twelvet
 */
@Component
public class SensitiveDataCipher {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int IV_LENGTH = 12;

	private static final int TAG_BITS = 128;

	private final SecureRandom random = new SecureRandom();

	private final SecretKeySpec key;

	public SensitiveDataCipher(@Value("${trade.settlement.crypto-key:}") String keyBase64) {
		if (keyBase64 == null || keyBase64.isBlank()) {
			throw new IllegalStateException("trade.settlement.crypto-key 未配置，敏感字段无法加密");
		}
		byte[] raw = Base64.getDecoder().decode(keyBase64);
		if (raw.length != 32) {
			throw new IllegalStateException("trade.settlement.crypto-key 必须是 32 字节的 Base64");
		}
		this.key = new SecretKeySpec(raw, "AES");
	}

	/**
	 * 加密明文
	 * @param plain 明文，null/空原样返回
	 * @return Base64(iv || ciphertext || tag)
	 */
	public String encrypt(String plain) {
		if (plain == null || plain.isEmpty()) {
			return plain;
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, out, 0, iv.length);
			System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
			return Base64.getEncoder().encodeToString(out);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("敏感字段加密失败", e);
		}
	}

	/**
	 * 解密密文
	 * @param encrypted Base64(iv || ciphertext || tag)，null/空原样返回
	 * @return 明文
	 * @throws IllegalStateException 密文损坏、密钥不匹配或格式非法
	 */
	public String decrypt(String encrypted) {
		if (encrypted == null || encrypted.isEmpty()) {
			return encrypted;
		}
		try {
			byte[] raw = Base64.getDecoder().decode(encrypted);
			if (raw.length <= IV_LENGTH) {
				throw new IllegalStateException("敏感字段解密失败");
			}
			byte[] iv = new byte[IV_LENGTH];
			System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] plain = cipher.doFinal(raw, IV_LENGTH, raw.length - IV_LENGTH);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("敏感字段解密失败", e);
		}
	}

}