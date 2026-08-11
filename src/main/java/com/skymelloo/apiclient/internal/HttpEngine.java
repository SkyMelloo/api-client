package com.skymelloo.apiclient.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.skymelloo.apiclient.ApiException;
import com.skymelloo.apiclient.auth.Credentials;
import com.skymelloo.apiclient.auth.ModIdentity;
import com.skymelloo.apiclient.auth.ModNamespace;
import com.skymelloo.apiclient.auth.PersonalApiKey;
import com.skymelloo.apiclient.auth.SignedHeaders;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Shared plumbing behind every endpoint-group class: builds signed/keyed/public requests, routes
 * them through the {@link RequestQueue} for pacing and retry, and parses JSON responses. Every
 * endpoint-group class (PresenceApi, FriendsApi, ...) holds one of these rather than talking to
 * HttpClient directly.
 */
public final class HttpEngine {
	public static final String DEFAULT_BASE_PATH = "/api/public/mod/v1";

	private final String baseUrl;
	private final String basePath;
	private final Credentials credentials;
	private final ModNamespace namespace;
	private final HttpClient httpClient;
	private final RequestQueue queue;
	private final Duration timeout;

	public HttpEngine(String host, String basePath, Credentials credentials, ModNamespace namespace,
			HttpClient httpClient, RequestQueue queue, Duration timeout) {
		this.baseUrl = host + basePath;
		this.basePath = basePath;
		this.credentials = credentials;
		this.namespace = namespace;
		this.httpClient = httpClient;
		this.queue = queue;
		this.timeout = timeout;
	}

	public ModNamespace namespace() {
		return namespace;
	}

	public String baseUrl() {
		return baseUrl;
	}

	public Credentials credentials() {
		return credentials;
	}

	public CompletableFuture<JsonElement> get(String path, boolean requiresAuth) {
		return execute("GET", path, null, requiresAuth);
	}

	public CompletableFuture<JsonElement> post(String path, JsonElement body, boolean requiresAuth) {
		byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
		return execute("POST", path, bodyBytes, requiresAuth);
	}

	private CompletableFuture<JsonElement> execute(String method, String path, byte[] bodyBytes, boolean requiresAuth) {
		Supplier<HttpRequest> requestBuilder = () -> buildRequest(method, path, bodyBytes, requiresAuth);
		return queue.submit(requestBuilder, RetryPolicy.rateLimitDefault()).thenApply(this::parseResponse);
	}

	private HttpRequest buildRequest(String method, String path, byte[] bodyBytes, boolean requiresAuth) {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + path))
				.timeout(timeout);
		if (namespace != null && namespace.headerValue() != null) {
			builder.header("X-SkyMelloo-Client", namespace.headerValue());
		}
		if ("GET".equals(method)) {
			builder.GET();
		} else {
			builder.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes != null ? bodyBytes : new byte[0]));
		}
		if (requiresAuth) {
			attachAuth(builder, method, path, bodyBytes != null ? bodyBytes : new byte[0]);
		}
		return builder.build();
	}

	private void attachAuth(HttpRequest.Builder builder, String method, String path, byte[] bodyBytes) {
		if (credentials == null) {
			throw new IllegalStateException("This endpoint requires credentials - build the client with .modIdentity(...) or .personalApiKey(...)");
		}
		if (credentials instanceof PersonalApiKey key) {
			builder.header("X-SkyMelloo-Test-Key", key.key());
		} else if (credentials instanceof ModIdentity identity) {
			SignedHeaders headers = identity.sign(method, basePath + path, bodyBytes);
			builder.header("X-SkyMelloo-UUID", headers.uuid())
					.header("X-SkyMelloo-Timestamp", headers.timestamp())
					.header("X-SkyMelloo-Nonce", headers.nonce())
					.header("X-SkyMelloo-Signature", headers.signature());
		}
	}

	private JsonElement parseResponse(java.net.http.HttpResponse<String> response) {
		String body = response.body();
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new ApiException(response.statusCode(), extractError(body, response.statusCode()));
		}
		if (body == null || body.isBlank()) {
			return JsonNull.INSTANCE;
		}
		try {
			return JsonParser.parseString(body);
		} catch (JsonSyntaxException e) {
			throw new ApiException(response.statusCode(), "Malformed JSON response");
		}
	}

	/** Every documented error response is JSON {@code { "error": "message" }} - surface that instead of a bare status code. */
	private String extractError(String body, int statusCode) {
		try {
			JsonElement parsed = JsonParser.parseString(body);
			if (parsed.isJsonObject() && parsed.getAsJsonObject().has("error") && !parsed.getAsJsonObject().get("error").isJsonNull()) {
				return parsed.getAsJsonObject().get("error").getAsString();
			}
		} catch (Exception ignored) {
			// fall back to the generic message below
		}
		return "HTTP " + statusCode;
	}
}
