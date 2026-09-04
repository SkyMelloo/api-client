// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.batch;

import com.skymelloo.apiclient.auth.ModNamespace;
import com.skymelloo.apiclient.auth.PersonalApiKey;
import com.skymelloo.apiclient.endpoints.PresenceApi;
import com.skymelloo.apiclient.internal.HttpEngine;
import com.skymelloo.apiclient.internal.RateLimiter;
import com.skymelloo.apiclient.internal.RequestQueue;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PresenceBatcherTest {

	@Test
	void closeFailsPendingQueriesInsteadOfHangingForever() throws Exception {
		PresenceApi presenceApi = newPresenceApi();
		// A debounce window long enough that the scheduled flush() has no chance to fire before
		// close() runs below - proves close() itself drains and fails pending entries, not that a
		// real flush happened to beat it.
		PresenceBatcher batcher = new PresenceBatcher(presenceApi, 60_000);

		CompletableFuture<Optional<PresenceApi.PresenceEntry>> a = batcher.query("uuid-a");
		CompletableFuture<Optional<PresenceApi.PresenceEntry>> b = batcher.query("uuid-b");

		batcher.close();

		for (CompletableFuture<Optional<PresenceApi.PresenceEntry>> future : List.of(a, b)) {
			ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
			assertInstanceOf(IllegalStateException.class, ex.getCause());
		}
	}

	@Test
	void queryAfterCloseFailsImmediatelyInsteadOfSchedulingAFlush() {
		PresenceBatcher batcher = new PresenceBatcher(newPresenceApi());
		batcher.close();

		CompletableFuture<Optional<PresenceApi.PresenceEntry>> future = batcher.query("uuid-a");

		assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
	}

	// Never actually invoked in these tests - both prove close() acts before any real query() call
	// would go out, so the engine just needs to exist, not work.
	private static PresenceApi newPresenceApi() {
		RequestQueue queue = new RequestQueue(HttpClient.newHttpClient(), RateLimiter.withDefaults());
		HttpEngine engine = new HttpEngine("http://127.0.0.1:1", HttpEngine.DEFAULT_BASE_PATH,
				new PersonalApiKey("unused-test-key"), ModNamespace.SKYMELLOO, HttpClient.newHttpClient(), queue, Duration.ofSeconds(5));
		return new PresenceApi(engine);
	}
}
