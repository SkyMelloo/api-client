// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.endpoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skymelloo.apiclient.internal.HttpEngine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Section 13. The server verifies staff status itself - reported players aren't trusted just because a client claims them. */
public final class StaffEncountersApi {
	/** The server considers at most this many reported players per {@link #report} call. */
	public static final int MAX_REPORTED_PLAYERS = 128;

	private final HttpEngine engine;

	public StaffEncountersApi(HttpEngine engine) {
		this.engine = engine;
	}

	public record PlayerRef(String uuid, String username) {
	}

	public CompletableFuture<Integer> report(List<PlayerRef> players) {
		JsonObject body = new JsonObject();
		JsonArray arr = new JsonArray();
		players.stream().limit(MAX_REPORTED_PLAYERS).forEach(p -> {
			JsonObject obj = new JsonObject();
			obj.addProperty("uuid", p.uuid());
			obj.addProperty("username", p.username());
			arr.add(obj);
		});
		body.add("players", arr);
		return engine.post("/staff-encounters", body, true).thenApply(root -> root.getAsJsonObject().get("recorded").getAsInt());
	}

	/** Shape isn't pinned down by the doc - returned as raw parsed JSON rather than a typed record. */
	public CompletableFuture<JsonElement> myEncounters() {
		return engine.get("/staff-encounters", true);
	}
}
