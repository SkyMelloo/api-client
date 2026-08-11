// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the canonical message format against DEVELOPER_API.md section 3.2's worked example. */
class RequestSignerTest {

	@Test
	void canonicalMessageMatchesDocExample() {
		String normalizedUuid = RequestSigner.normalizeUuid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
		assertEquals("aaaaaaaabbbbccccddddeeeeeeeeeeee", normalizedUuid);

		byte[] body = "{\"settings\":{\"showScore\":true}}".getBytes(StandardCharsets.UTF_8);
		String bodyHash = RequestSigner.sha256Hex(body);

		String message = RequestSigner.canonicalMessage(normalizedUuid, "POST", "/api/public/mod/v1/settings",
				1786390001234L, "6c901c25f61b4ef89cb98ad5a6f49d63", bodyHash);

		String expected = String.join("\n",
				"aaaaaaaabbbbccccddddeeeeeeeeeeee",
				"POST",
				"/api/public/mod/v1/settings",
				"1786390001234",
				"6c901c25f61b4ef89cb98ad5a6f49d63",
				bodyHash);
		assertEquals(expected, message);
	}

	@Test
	void pathWithoutQueryStripsQueryString() {
		assertEquals("/api/public/mod/v1/version-check", RequestSigner.pathWithoutQuery("/api/public/mod/v1/version-check?version=1.0.0&hash=abc"));
		assertEquals("/api/public/mod/v1/settings", RequestSigner.pathWithoutQuery("/api/public/mod/v1/settings"));
	}

	@Test
	void emptyBodyHashesTheEmptyByteArray() throws Exception {
		// A GET with no body must hash zero bytes, not e.g. an empty JSON object - section 3.1.
		String expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(new byte[0]));
		assertEquals(expected, RequestSigner.sha256Hex(new byte[0]));
	}

	@Test
	void signatureVerifiesAgainstTheMatchingPublicKey() throws Exception {
		KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		String message = "some-canonical-message";
		String signatureBase64 = RequestSigner.sign(keyPair.getPrivate(), message);

		Signature verifier = Signature.getInstance("Ed25519");
		verifier.initVerify(keyPair.getPublic());
		verifier.update(message.getBytes(StandardCharsets.UTF_8));
		assertTrue(verifier.verify(java.util.Base64.getDecoder().decode(signatureBase64)));
	}

	@Test
	void randomNonceIsAlwaysDifferent() {
		assertTrue(!RequestSigner.randomNonce().equals(RequestSigner.randomNonce()));
	}
}
