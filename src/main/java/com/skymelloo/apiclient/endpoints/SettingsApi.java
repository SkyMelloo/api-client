// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.endpoints;

import com.google.gson.JsonObject;
import com.skymelloo.apiclient.ApiException;
import com.skymelloo.apiclient.internal.HttpEngine;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.skymelloo.apiclient.internal.JsonUtil.longVal;

/** Section 8. Same routes for both mods, kept as separate blobs server-side via the namespace header. Max serialized size ~32 KiB. */
public final class SettingsApi {
	private final HttpEngine engine;

	public SettingsApi(HttpEngine engine) {
		this.engine = engine;
	}

	public record CloudSettings(JsonObject settings, long updatedAt) {
	}

	/** Empty (not a failed future) when nothing has been saved yet - a 404 here is documented normal behavior, not an error. */
	public CompletableFuture<Optional<CloudSettings>> get() {
		return engine.get("/settings", true)
				.thenApply(root -> {
					JsonObject obj = root.getAsJsonObject();
					return Optional.of(new CloudSettings(obj.getAsJsonObject("settings"), longVal(obj, "updatedAt", 0)));
				})
				.exceptionally(error -> {
					if (unwrap(error) instanceof ApiException api && api.statusCode() == 404) {
						return Optional.empty();
					}
					throw new java.util.concurrent.CompletionException(error);
				});
	}

	/** Use {@link #get}'s updatedAt to decide whether local or cloud data is newer before overwriting either. */
	public CompletableFuture<Void> set(JsonObject settings) {
		JsonObject body = new JsonObject();
		body.add("settings", settings);
		return engine.post("/settings", body, true).thenApply(root -> null);
	}

	private static Throwable unwrap(Throwable error) {
		return error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
	}
}
