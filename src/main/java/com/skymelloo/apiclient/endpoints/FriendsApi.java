// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.endpoints;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skymelloo.apiclient.internal.HttpEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.skymelloo.apiclient.internal.JsonUtil.longVal;
import static com.skymelloo.apiclient.internal.JsonUtil.str;

/** Section 11. SkyMelloo's own friends list, separate from Hypixel's - mutations always act as the verified Minecraft account. */
public final class FriendsApi {
	private final HttpEngine engine;

	public FriendsApi(HttpEngine engine) {
		this.engine = engine;
	}

	public record Friend(String uuid, String username) {
	}

	public record IncomingRequest(String uuid, String username, long at) {
	}

	public record FriendsList(List<Friend> friends, List<IncomingRequest> requests) {
	}

	public CompletableFuture<FriendsList> list() {
		return engine.get("/friends", true).thenApply(root -> {
			JsonObject obj = root.getAsJsonObject();
			List<Friend> friends = new ArrayList<>();
			if (obj.has("friends") && obj.get("friends").isJsonArray()) {
				for (JsonElement el : obj.getAsJsonArray("friends")) {
					JsonObject f = el.getAsJsonObject();
					friends.add(new Friend(str(f, "uuid"), str(f, "username")));
				}
			}
			List<IncomingRequest> requests = new ArrayList<>();
			if (obj.has("requests") && obj.get("requests").isJsonArray()) {
				for (JsonElement el : obj.getAsJsonArray("requests")) {
					JsonObject r = el.getAsJsonObject();
					requests.add(new IncomingRequest(str(r, "uuid"), str(r, "username"), longVal(r, "at", 0)));
				}
			}
			return new FriendsList(friends, requests);
		});
	}

	/** {@code status}: "pending", "accepted", "already_friends", "self", or "limit" - all normal outcomes, not error conditions. */
	public record RequestResult(String username, String status) {
	}

	public CompletableFuture<RequestResult> request(String username) {
		return usernameCall("/friends/request", username).thenApply(root -> {
			JsonObject obj = root.getAsJsonObject();
			return new RequestResult(str(obj, "username"), str(obj, "status"));
		});
	}

	public CompletableFuture<Void> accept(String username) {
		return usernameCall("/friends/accept", username).thenApply(root -> null);
	}

	public CompletableFuture<Void> decline(String username) {
		return usernameCall("/friends/decline", username).thenApply(root -> null);
	}

	public CompletableFuture<Void> remove(String username) {
		return usernameCall("/friends/remove", username).thenApply(root -> null);
	}

	private CompletableFuture<JsonElement> usernameCall(String path, String username) {
		JsonObject body = new JsonObject();
		body.addProperty("username", username);
		return engine.post(path, body, true);
	}
}
