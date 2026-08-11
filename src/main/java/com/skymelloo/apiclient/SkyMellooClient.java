// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient;

import com.google.gson.JsonElement;
import com.skymelloo.apiclient.auth.Credentials;
import com.skymelloo.apiclient.auth.ModNamespace;
import com.skymelloo.apiclient.auth.PersonalApiKey;
import com.skymelloo.apiclient.batch.PresenceBatcher;
import com.skymelloo.apiclient.endpoints.AccountApi;
import com.skymelloo.apiclient.endpoints.AuthApi;
import com.skymelloo.apiclient.endpoints.FriendsApi;
import com.skymelloo.apiclient.endpoints.MetaApi;
import com.skymelloo.apiclient.endpoints.PresenceApi;
import com.skymelloo.apiclient.endpoints.ReadApi;
import com.skymelloo.apiclient.endpoints.RelayApi;
import com.skymelloo.apiclient.endpoints.SettingsApi;
import com.skymelloo.apiclient.endpoints.StaffEncountersApi;
import com.skymelloo.apiclient.internal.HttpEngine;
import com.skymelloo.apiclient.internal.RateLimiter;
import com.skymelloo.apiclient.internal.RequestQueue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Entry point for the SkyMelloo/MellooEssentials Developer API (see
 * https://sky.melloo.me/developer-api/reference). Every call goes through one shared, rate-limited,
 * retrying {@link RequestQueue} - build one client per Minecraft session and reuse it, rather than
 * constructing a new one per request.
 *
 * <pre>{@code
 * SkyMellooClient bootstrap = SkyMellooClient.builder()
 *     .namespace(ModNamespace.SKYMELLOO)
 *     .build();
 *
 * var challenge = bootstrap.auth().requestChallenge().join();
 * // ... complete Minecraft's own joinServer(challenge.serverId()) here ...
 * var session = bootstrap.auth().verify(challenge.serverId(), username, uuid, publicKeyBase64).join();
 *
 * ModIdentity identity = new ModIdentity(uuid, username, ephemeralPrivateKey,
 *     session.serverTime() - System.currentTimeMillis());
 * SkyMellooClient client = bootstrap.withCredentials(identity);
 * }</pre>
 */
public final class SkyMellooClient implements AutoCloseable {
	private final String host;
	private final String basePath;
	private final ModNamespace namespace;
	private final HttpClient httpClient;
	private final RequestQueue queue;
	private final Duration timeout;
	private final HttpEngine engine;

	private final AuthApi auth;
	private final MetaApi meta;
	private final PresenceApi presence;
	private final SettingsApi settings;
	private final FriendsApi friends;
	private final RelayApi relay;
	private final StaffEncountersApi staffEncounters;
	private final AccountApi account;
	private final ReadApi read;

	private SkyMellooClient(String host, String basePath, ModNamespace namespace, Credentials credentials,
			HttpClient httpClient, RequestQueue queue, Duration timeout) {
		this.host = host;
		this.basePath = basePath;
		this.namespace = namespace;
		this.httpClient = httpClient;
		this.queue = queue;
		this.timeout = timeout;
		this.engine = new HttpEngine(host, basePath, credentials, namespace, httpClient, queue, timeout);

		this.auth = new AuthApi(engine);
		this.meta = new MetaApi(engine);
		this.presence = new PresenceApi(engine);
		this.settings = new SettingsApi(engine);
		this.friends = new FriendsApi(engine);
		this.relay = new RelayApi(engine);
		this.staffEncounters = new StaffEncountersApi(engine);
		this.account = new AccountApi(engine);
		this.read = new ReadApi(engine);
	}

	public static Builder builder() {
		return new Builder();
	}

	public AuthApi auth() {
		return auth;
	}

	public MetaApi meta() {
		return meta;
	}

	public PresenceApi presence() {
		return presence;
	}

	public SettingsApi settings() {
		return settings;
	}

	public FriendsApi friends() {
		return friends;
	}

	public RelayApi relay() {
		return relay;
	}

	public StaffEncountersApi staffEncounters() {
		return staffEncounters;
	}

	public AccountApi account() {
		return account;
	}

	public ReadApi read() {
		return read;
	}

	public PresenceBatcher newPresenceBatcher() {
		return new PresenceBatcher(presence);
	}

	public PresenceBatcher newPresenceBatcher(long debounceMillis) {
		return new PresenceBatcher(presence, debounceMillis);
	}

	/** Escape hatch for an endpoint this library hasn't wrapped yet, or a documented-evolvable shape you'd rather parse yourself. */
	public CompletableFuture<JsonElement> rawGet(String path, boolean requiresAuth) {
		return engine.get(path, requiresAuth);
	}

	public CompletableFuture<JsonElement> rawPost(String path, JsonElement body, boolean requiresAuth) {
		return engine.post(path, body, requiresAuth);
	}

	/**
	 * A new client using the same connection, thread, and rate-limiter budget but different
	 * credentials - the normal way to go from an unauthenticated bootstrap client (challenge/verify)
	 * to an authenticated one. {@link #close()} on either instance shuts down the shared queue for
	 * both - only close the one you actually intend to shut down for good (e.g. on disconnect/mod unload).
	 */
	public SkyMellooClient withCredentials(Credentials credentials) {
		return new SkyMellooClient(host, basePath, namespace, credentials, httpClient, queue, timeout);
	}

	public SkyMellooClient withPersonalApiKey(String key) {
		return withCredentials(new PersonalApiKey(key));
	}

	@Override
	public void close() {
		queue.close();
	}

	public static final class Builder {
		private String host = "https://sky.melloo.me";
		private String basePath = HttpEngine.DEFAULT_BASE_PATH;
		private ModNamespace namespace;
		private Credentials credentials;
		private Duration timeout = Duration.ofSeconds(10);
		private double sustainedPerSecond = RateLimiter.DEFAULT_SUSTAINED_PER_SECOND;
		private double burstCapacity = RateLimiter.DEFAULT_BURST_CAPACITY;
		private HttpClient httpClient;

		/** Override for testing against a non-production deployment. Defaults to https://sky.melloo.me. */
		public Builder host(String host) {
			this.host = host;
			return this;
		}

		/** Required - which mod this client is for (section 4). Picks the right X-SkyMelloo-Client header and meta-endpoint path variant. */
		public Builder namespace(ModNamespace namespace) {
			this.namespace = namespace;
			return this;
		}

		public Builder credentials(Credentials credentials) {
			this.credentials = credentials;
			return this;
		}

		public Builder personalApiKey(String key) {
			this.credentials = new PersonalApiKey(key);
			return this;
		}

		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		/** Client-side pacing, well under the server's own ceiling by default - see {@link RateLimiter}. */
		public Builder rateLimit(double sustainedPerSecond, double burstCapacity) {
			this.sustainedPerSecond = sustainedPerSecond;
			this.burstCapacity = burstCapacity;
			return this;
		}

		/** Supply your own HttpClient (e.g. to share a connection pool with the rest of your mod). */
		public Builder httpClient(HttpClient httpClient) {
			this.httpClient = httpClient;
			return this;
		}

		public SkyMellooClient build() {
			if (namespace == null) {
				throw new IllegalStateException("namespace(...) is required - ModNamespace.SKYMELLOO or ModNamespace.MELLOOESSENTIALS");
			}
			HttpClient client = httpClient != null ? httpClient : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
			RequestQueue queue = new RequestQueue(client, new RateLimiter(sustainedPerSecond, burstCapacity));
			return new SkyMellooClient(host, basePath, namespace, credentials, client, queue, timeout);
		}
	}
}
