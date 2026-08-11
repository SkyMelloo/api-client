// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.endpoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skymelloo.apiclient.internal.HttpEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.skymelloo.apiclient.internal.JsonUtil.bool;
import static com.skymelloo.apiclient.internal.JsonUtil.elem;
import static com.skymelloo.apiclient.internal.JsonUtil.intVal;
import static com.skymelloo.apiclient.internal.JsonUtil.str;

/** Section 7. Presence entries expire ~20s after a client stops reporting. */
public final class PresenceApi {
	/** The server accepts at most this many UUIDs per query() call - see section 7. */
	public static final int MAX_QUERY_UUIDS = 128;

	private final HttpEngine engine;

	public PresenceApi(HttpEngine engine) {
		this.engine = engine;
	}

	public record PresenceEntry(String uuid, String username, List<String> cosmetics, String status,
			JsonElement dungeonSync, boolean afk, boolean accountLinked, boolean skymelloo, String role) {
	}

	/** {@code dungeonSync} may be null; it's opaque to this library (SkyMelloo-specific, up to ~300 KB serialized). */
	public CompletableFuture<Void> report(String status, JsonElement dungeonSync, boolean afk, boolean accountLinked, String location, List<String> cosmetics) {
		JsonObject body = new JsonObject();
		JsonArray cosmeticsArr = new JsonArray();
		if (cosmetics != null) {
			cosmetics.forEach(cosmeticsArr::add);
		}
		body.add("cosmetics", cosmeticsArr);
		body.addProperty("status", status);
		if (dungeonSync != null) {
			body.add("dungeonSync", dungeonSync);
		}
		body.addProperty("afk", afk);
		body.addProperty("accountLinked", accountLinked);
		if (location != null) {
			body.addProperty("location", location);
		}
		return engine.post("/presence", body, true).thenApply(root -> null);
	}

	/** Prefer {@link com.skymelloo.apiclient.batch.PresenceBatcher} over calling this once per UUID - see section 16's own guidance to batch. */
	public CompletableFuture<List<PresenceEntry>> query(List<String> uuids) {
		JsonObject body = new JsonObject();
		JsonArray uuidsArr = new JsonArray();
		uuids.stream().limit(MAX_QUERY_UUIDS).forEach(uuidsArr::add);
		body.add("uuids", uuidsArr);
		return engine.post("/presence/query", body, true).thenApply(root -> {
			List<PresenceEntry> result = new ArrayList<>();
			JsonObject obj = root.getAsJsonObject();
			if (obj.has("present") && obj.get("present").isJsonArray()) {
				for (JsonElement el : obj.getAsJsonArray("present")) {
					if (!el.isJsonObject()) {
						continue;
					}
					JsonObject entry = el.getAsJsonObject();
					List<String> cosmetics = new ArrayList<>();
					if (entry.has("cosmetics") && entry.get("cosmetics").isJsonArray()) {
						entry.getAsJsonArray("cosmetics").forEach(c -> cosmetics.add(c.getAsString()));
					}
					result.add(new PresenceEntry(
							str(entry, "uuid"), str(entry, "username"), cosmetics, str(entry, "status"),
							elem(entry, "dungeonSync"), bool(entry, "afk", false), bool(entry, "accountLinked", false),
							bool(entry, "skymelloo", false), str(entry, "role")
					));
				}
			}
			return result;
		});
	}

	public record Count(int online, int skymelloo, int mellooessentials) {
	}

	public CompletableFuture<Count> count() {
		return engine.get("/presence/count", false).thenApply(root -> {
			JsonObject obj = root.getAsJsonObject();
			JsonObject byMod = obj.has("byMod") && obj.get("byMod").isJsonObject() ? obj.getAsJsonObject("byMod") : new JsonObject();
			return new Count(intVal(obj, "online", 0), intVal(byMod, "skymelloo", 0), intVal(byMod, "mellooessentials", 0));
		});
	}
}
