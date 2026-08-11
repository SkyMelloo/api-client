// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.auth;

import java.security.PrivateKey;

/**
 * A live, per-launch identity: the Minecraft account this session proved ownership of via the
 * challenge/joinServer/verify flow (see {@code AuthApi}), its ephemeral Ed25519 private key, and
 * the clock offset from {@code serverTime} so every signed timestamp reflects the server's clock.
 *
 * <p>This library does not perform the Mojang {@code joinServer} call itself - that requires a real
 * Minecraft session and is the embedding mod's responsibility. Generate the key pair, call
 * {@code AuthApi#requestChallenge}, complete {@code joinServer} with the returned {@code serverId},
 * then call {@code AuthApi#verify} and build a {@code ModIdentity} from the result.
 */
public record ModIdentity(String uuid, String username, PrivateKey signingKey, long clockOffsetMs) implements Credentials {

	/** Signs one specific request. A fresh nonce and clock-corrected timestamp every call, so no two signatures are ever identical. */
	public SignedHeaders sign(String method, String path, byte[] bodyBytes) {
		String normalizedUuid = RequestSigner.normalizeUuid(uuid);
		long timestamp = System.currentTimeMillis() + clockOffsetMs;
		String nonce = RequestSigner.randomNonce();
		String bodyHash = RequestSigner.sha256Hex(bodyBytes);
		String message = RequestSigner.canonicalMessage(normalizedUuid, method, path, timestamp, nonce, bodyHash);
		String signature = RequestSigner.sign(signingKey, message);
		return new SignedHeaders(uuid, String.valueOf(timestamp), nonce, signature);
	}
}
