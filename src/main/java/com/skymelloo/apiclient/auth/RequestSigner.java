package com.skymelloo.apiclient.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Builds and signs the canonical message from DEVELOPER_API.md section 3.1:
 * {@code uuid\nMETHOD\npath\ntimestamp\nnonce\nsha256Hex(body)}, signed with Ed25519. Kept as a
 * standalone, testable class separate from {@link ModIdentity} so the exact byte-for-byte protocol
 * can be verified against the doc's worked example independently of key management.
 */
public final class RequestSigner {
	private static final SecureRandom RANDOM = new SecureRandom();

	private RequestSigner() {
	}

	public static String normalizeUuid(String uuid) {
		return uuid.toLowerCase(Locale.ROOT).replace("-", "");
	}

	public static String randomNonce() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	public static String sha256Hex(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	/** Strips any query string - only the path itself is ever signed. */
	public static String pathWithoutQuery(String path) {
		int queryStart = path.indexOf('?');
		return queryStart < 0 ? path : path.substring(0, queryStart);
	}

	public static String canonicalMessage(String normalizedUuid, String method, String path, long timestamp, String nonce, String bodyHashHex) {
		return String.join("\n", normalizedUuid, method.toUpperCase(Locale.ROOT), pathWithoutQuery(path),
				String.valueOf(timestamp), nonce, bodyHashHex);
	}

	/** Signs {@code message} (UTF-8 bytes) with the given Ed25519 private key, base64-encoded. */
	public static String sign(PrivateKey privateKey, String message) {
		try {
			Signature signer = Signature.getInstance("Ed25519");
			signer.initSign(privateKey);
			signer.update(message.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(signer.sign());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Ed25519 signing failed", e);
		}
	}
}
