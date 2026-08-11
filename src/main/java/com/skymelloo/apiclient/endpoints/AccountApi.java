// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.endpoints;

import com.google.gson.JsonObject;
import com.skymelloo.apiclient.internal.HttpEngine;

import java.util.concurrent.CompletableFuture;

import static com.skymelloo.apiclient.internal.JsonUtil.str;

/** Sections 9-10. Whether/how the verified Minecraft account is linked to a sky.melloo.me website account. */
public final class AccountApi {
	private final HttpEngine engine;

	public AccountApi(HttpEngine engine) {
		this.engine = engine;
	}

	/** Despite the historical endpoint name, this is not an authorization oracle for staff/admin actions - it only reports link status. */
	public CompletableFuture<Boolean> isAccountLinked() {
		return engine.get("/permissions", true).thenApply(root -> root.getAsJsonObject().get("accountLinked").getAsBoolean());
	}

	/** A short-lived linking token - open {@code https://sky.melloo.me/link/<token>} in the system browser rather than reimplementing website login. */
	public CompletableFuture<String> startLinking() {
		return engine.post("/link/start", new JsonObject(), true).thenApply(root -> str(root.getAsJsonObject(), "token"));
	}

	public CompletableFuture<Void> unlink() {
		return engine.post("/unlink", new JsonObject(), true).thenApply(root -> null);
	}

	public record VerifyResult(String username) {
	}

	/** The in-game verification-code flow - {@code code} is the code shown on the website. */
	public CompletableFuture<VerifyResult> verify(String code) {
		JsonObject body = new JsonObject();
		body.addProperty("code", code);
		return engine.post("/verify", body, true).thenApply(root -> new VerifyResult(str(root.getAsJsonObject(), "username")));
	}
}
