package com.skymelloo.apiclient.endpoints;

import com.google.gson.JsonElement;
import com.skymelloo.apiclient.internal.HttpEngine;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Section 14: read-only website data, available to authenticated mods. Response shapes for these
 * aren't pinned down by the doc (rule 12: "treat read-API response shapes as evolvable") - returned
 * as raw parsed JSON rather than guessed-at records. Icon/avatar/skin routes return image data, not
 * JSON - exposed as URL builders instead of a fetch method, since how you want that data (bytes,
 * cached, streamed) is up to your client.
 */
public final class ReadApi {
	private final HttpEngine engine;

	public ReadApi(HttpEngine engine) {
		this.engine = engine;
	}

	public CompletableFuture<JsonElement> player(String username) {
		return engine.get("/player/" + encode(username), true);
	}

	public CompletableFuture<JsonElement> playerInventory(String username) {
		return engine.get("/player/" + encode(username) + "/inventory", true);
	}

	public CompletableFuture<JsonElement> playerMuseum(String username) {
		return engine.get("/player/" + encode(username) + "/museum", true);
	}

	public CompletableFuture<JsonElement> playerAuctions(String username) {
		return engine.get("/player/" + encode(username) + "/auctions", true);
	}

	/** Server-side cooldown-limited (shared across every caller of that account, not per-caller) - treat any failure here as ignorable, fire-and-forget. */
	public CompletableFuture<Void> requestPlayerRefresh(String username) {
		return engine.post("/player/" + encode(username) + "/request-refresh", new com.google.gson.JsonObject(), true).thenApply(root -> null);
	}

	/** Param names aren't pinned down by the doc - pass whatever the current site search accepts. */
	public CompletableFuture<JsonElement> searchTop(Map<String, String> params) {
		return engine.get("/search/top" + queryString(params), true);
	}

	public CompletableFuture<JsonElement> searchSuggest(Map<String, String> params) {
		return engine.get("/search/suggest" + queryString(params), true);
	}

	/** A sky.melloo.me account's public profile (avatar, bio, roles, linked accounts) - not a Minecraft player lookup, use {@link #player} for that. */
	public CompletableFuture<JsonElement> user(String publicId) {
		return engine.get("/user/" + encode(publicId), true);
	}

	public String itemIconUrl(String itemId) {
		return engine.baseUrl() + "/item-icon/" + encode(itemId);
	}

	public String avatarUrl(String username) {
		return engine.baseUrl() + "/avatar/" + encode(username);
	}

	public String skinUrl(String username) {
		return engine.baseUrl() + "/skin/" + encode(username);
	}

	public String petIconUrl(String petType) {
		return engine.baseUrl() + "/pet-icon/" + encode(petType);
	}

	public String minionIconUrl(String minionType) {
		return engine.baseUrl() + "/minion-icon/" + encode(minionType);
	}

	public String bestiaryIconUrl(String bestiaryType) {
		return engine.baseUrl() + "/bestiary-icon/" + encode(bestiaryType);
	}

	/** Check for a known outage before assuming your own request failed. */
	public CompletableFuture<JsonElement> statusServices() {
		return engine.get("/status/services", true);
	}

	public CompletableFuture<JsonElement> statusIncidents() {
		return engine.get("/status/incidents", true);
	}

	public CompletableFuture<JsonElement> health() {
		return engine.get("/health", true);
	}

	private static String queryString(Map<String, String> params) {
		if (params == null || params.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("?");
		params.forEach((k, v) -> {
			if (sb.length() > 1) {
				sb.append('&');
			}
			sb.append(encode(k)).append('=').append(encode(v));
		});
		return sb.toString();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
