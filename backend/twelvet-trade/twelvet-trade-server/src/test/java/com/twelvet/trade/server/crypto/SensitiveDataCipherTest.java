package com.twelvet.trade.server.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 敏感字段加解密测试
 * <p>
 * 锁定的不变量：明文不出现在密文里；同一明文两次加密结果不同（随机 IV）；密文被篡改解密必须失败；空密钥启动即失败。
 * </p>
 */
class SensitiveDataCipherTest {

	private final SensitiveDataCipher cipher = new SensitiveDataCipher(
			Base64.getEncoder().encodeToString(new byte[32]));

	@Test
	@DisplayName("加密后可解回原文")
	void encrypt_thenDecrypt_roundTrips() {
		String plain = "110101199001011234";

		String enc = cipher.encrypt(plain);

		assertNotEquals(plain, enc);
		assertEquals(plain, cipher.decrypt(enc));
	}

	@Test
	@DisplayName("同一明文两次加密密文不同（随机 IV）")
	void encrypt_samePlainTwice_producesDifferentCiphertext() {
		assertNotEquals(cipher.encrypt("abc"), cipher.encrypt("abc"));
	}

	@Test
	@DisplayName("密文被篡改时解密失败")
	void decrypt_tamperedCiphertext_throws() {
		String enc = cipher.encrypt("abc");
		// 翻转 tag 的最后一个字节：只截断 Base64 尾部会先撞上 padding，字节不变、解密仍成功
		byte[] raw = Base64.getDecoder().decode(enc);
		raw[raw.length - 1] ^= 0x01;
		String tampered = Base64.getEncoder().encodeToString(raw);

		assertThrows(RuntimeException.class, () -> cipher.decrypt(tampered));
	}

	@Test
	@DisplayName("空密钥启动即失败（不静默降级）")
	void blankKey_failsFast() {
		assertThrows(IllegalStateException.class, () -> new SensitiveDataCipher(""));
	}

}