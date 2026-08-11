// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.endpoints;

import com.google.gson.JsonObject;
import com.skymelloo.apiclient.internal.HttpEngine;

import java.util.concurrent.CompletableFuture;

import static com.skymelloo.apiclient.internal.JsonUtil.longVal;
import static com.skymelloo.apiclient.internal.JsonUtil.str;

/**
 * The bootstrap handshake (section 2.1). This library cannot perform the Mojang {@code joinServer}
 * step itself - that needs a real Minecraft session. The usual flow: generate an Ed25519 key pair,
 * call {@link #requestChallenge}, complete {@code joinServer(serverId)} with the game's own session
 * service, then call {@link #verify} and build a {@code ModIdentity} from the result.
 */
public final class AuthApi {
	private final HttpEngine engine;

	public AuthApi(HttpEngine engine) {
		this.engine = engine;
	}

	public record Challenge(String serverId, long serverTime) {
	}

	public CompletableFuture<Challenge> requestChallenge() {
		return engine.get("/auth/challenge", false).thenApply(root -> {
			JsonObject obj = root.getAsJsonObject();
			return new Challenge(str(obj, "serverId"), longVal(obj, "serverTime", 0));
		});
	}

	public record VerifyResult(long expiresAt, long serverTime) {
	}

	public CompletableFuture<VerifyResult> verify(String serverId, String username, String uuid, String publicKeyBase64) {
		JsonObject body = new JsonObject();
		body.addProperty("serverId", serverId);
		body.addProperty("username", username);
		body.addProperty("uuid", uuid);
		body.addProperty("publicKey", publicKeyBase64);
		return engine.post("/auth/verify", body, false).thenApply(root -> {
			JsonObject obj = root.getAsJsonObject();
			return new VerifyResult(longVal(obj, "expiresAt", 0), longVal(obj, "serverTime", 0));
		});
	}
}
