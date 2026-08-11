// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.endpoints;

import com.google.gson.JsonObject;
import com.skymelloo.apiclient.auth.ModNamespace;
import com.skymelloo.apiclient.internal.HttpEngine;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static com.skymelloo.apiclient.internal.JsonUtil.bool;
import static com.skymelloo.apiclient.internal.JsonUtil.str;

/**
 * Public, unauthenticated bootstrap/metadata (section 6). Automatically picks the SkyMelloo or
 * MellooEssentials path variant based on the client's configured {@link ModNamespace} - a client is
 * always built for one mod, so callers never need to specify it per call.
 */
public final class MetaApi {
	private final HttpEngine engine;

	public MetaApi(HttpEngine engine) {
		this.engine = engine;
	}

	private String prefix() {
		return engine.namespace() == ModNamespace.MELLOOESSENTIALS ? "/mellooessentials" : "";
	}

	/** {@code unofficialBuildMessage} is null whenever {@code integrityOk} is true; when non-null, a compatible client must surface it (compatibility rule 9). */
	public record VersionCheckResult(boolean compatible, boolean integrityOk, String buildKind, String minVersion,
			String latestVersion, String latestPublicVersion, boolean upToDate, String maintainerUsername,
			String message, String updateAvailableMessage, String unofficialBuildMessage) {
	}

	public CompletableFuture<VersionCheckResult> versionCheck(String version, String buildHash) {
		StringBuilder path = new StringBuilder(prefix()).append("/version-check?version=").append(encode(version));
		if (buildHash != null) {
			path.append("&hash=").append(encode(buildHash));
		}
		return engine.get(path.toString(), false).thenApply(root -> {
			JsonObject obj = root.getAsJsonObject();
			return new VersionCheckResult(
					bool(obj, "compatible", true), bool(obj, "integrityOk", true), str(obj, "buildKind", "unknown"),
					str(obj, "minVersion"), str(obj, "latestVersion"), str(obj, "latestPublicVersion"),
					bool(obj, "upToDate", true), str(obj, "maintainerUsername"),
					str(obj, "message"), str(obj, "updateAvailableMessage"), str(obj, "unofficialBuildMessage")
			);
		});
	}

	/** Shape isn't pinned down by the doc - returned as raw parsed JSON rather than a typed record. */
	public CompletableFuture<com.google.gson.JsonElement> changelog() {
		return engine.get(prefix() + "/changelog", false);
	}

	public record Dependencies(java.util.List<String> required, java.util.List<String> recommended) {
	}

	public CompletableFuture<Dependencies> dependencies() {
		return engine.get(prefix() + "/dependencies", false).thenApply(root -> {
			JsonObject obj = root.getAsJsonObject();
			return new Dependencies(stringList(obj, "required"), stringList(obj, "recommended"));
		});
	}

	private java.util.List<String> stringList(JsonObject obj, String key) {
		java.util.List<String> result = new java.util.ArrayList<>();
		if (obj.has(key) && obj.get(key).isJsonArray()) {
			for (var el : obj.getAsJsonArray(key)) {
				result.add(el.getAsString());
			}
		}
		return result;
	}

	public CompletableFuture<Boolean> downloadAvailable() {
		return engine.get(prefix() + "/download-status", false).thenApply(root -> bool(root.getAsJsonObject(), "available", false));
	}

	/** Direct download link for the latest published release. Not fetched by this library - hand it to your own downloader. */
	public String downloadUrl() {
		return engine.baseUrl() + prefix() + "/download";
	}

	public String downloadUrl(String version) {
		return engine.baseUrl() + prefix() + "/download/" + encode(version);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
