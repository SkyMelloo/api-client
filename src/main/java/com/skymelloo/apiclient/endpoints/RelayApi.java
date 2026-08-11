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

import static com.skymelloo.apiclient.internal.JsonUtil.longVal;
import static com.skymelloo.apiclient.internal.JsonUtil.str;

/**
 * Section 12. Ephemeral message relay - not persistent chat history. Max 50 queued messages per
 * inbox, ~2 minute TTL, 256 character max per message.
 */
public final class RelayApi {
	/** The server accepts at most this many recipient UUIDs per {@link #sendToParty} call. */
	public static final int MAX_PARTY_RECIPIENTS = 20;

	private final HttpEngine engine;

	public RelayApi(HttpEngine engine) {
		this.engine = engine;
	}

	/** Recipient must already be a confirmed SkyMelloo friend, or the future fails with a 403 {@link com.skymelloo.apiclient.ApiException}. */
	public CompletableFuture<Void> sendDirect(String toUsername, String text) {
		JsonObject body = new JsonObject();
		body.addProperty("toUsername", toUsername);
		body.addProperty("text", text);
		return engine.post("/relay/message", body, true).thenApply(root -> null);
	}

	/** The server can't see your actual Hypixel party - you supply the recipient UUIDs (e.g. from your own party tracking). */
	public CompletableFuture<Integer> sendToParty(List<String> toUuids, String text) {
		JsonObject body = new JsonObject();
		JsonArray arr = new JsonArray();
		toUuids.stream().limit(MAX_PARTY_RECIPIENTS).forEach(arr::add);
		body.add("toUuids", arr);
		body.addProperty("text", text);
		return engine.post("/relay/party", body, true).thenApply(root -> root.getAsJsonObject().get("recipients").getAsInt());
	}

	public record InboxMessage(String fromUuid, String fromUsername, String text, String scope, long at) {
	}

	/** Draining, not peeking - a successful poll removes these messages server-side. Don't poll the same inbox from two independent places. */
	public CompletableFuture<List<InboxMessage>> pollInbox() {
		return engine.get("/relay/inbox", true).thenApply(root -> {
			List<InboxMessage> result = new ArrayList<>();
			JsonObject obj = root.getAsJsonObject();
			if (obj.has("messages") && obj.get("messages").isJsonArray()) {
				for (JsonElement el : obj.getAsJsonArray("messages")) {
					JsonObject m = el.getAsJsonObject();
					result.add(new InboxMessage(str(m, "from"), str(m, "fromUsername"), str(m, "text"), str(m, "scope"), longVal(m, "at", 0)));
				}
			}
			return result;
		});
	}
}
