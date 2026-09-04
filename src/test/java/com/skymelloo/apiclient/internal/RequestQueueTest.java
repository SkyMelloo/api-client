// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.internal;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestQueueTest {

	@Test
	void closeFailsHeldAndStillQueuedFuturesInsteadOfHangingForever() throws Exception {
		// Never refills within the test's lifetime, so the worker takes the first item and then
		// sleeps in millisUntilNextToken()'s wait - that item is "held" (already off the queue, not
		// yet dispatched). Every other submitted item never even gets taken - that's "still queued".
		// Before the fix, both groups were silently dropped on close() and their futures hung forever.
		RateLimiter neverRefills = new RateLimiter(0.001, 0.0);
		RequestQueue queue = new RequestQueue(HttpClient.newHttpClient(), neverRefills);

		List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			futures.add(queue.submit(RequestQueueTest::unusedRequest, RetryPolicy.none()));
		}
		// Let the worker thread actually take() the first item and enter its rate-limit sleep.
		Thread.sleep(200);

		queue.close();

		for (CompletableFuture<HttpResponse<String>> future : futures) {
			ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
			assertInstanceOf(IllegalStateException.class, ex.getCause());
		}
	}

	@Test
	void submitAfterCloseFailsImmediatelyInsteadOfEnqueueing() {
		RequestQueue queue = new RequestQueue(HttpClient.newHttpClient(), RateLimiter.withDefaults());
		queue.close();

		CompletableFuture<HttpResponse<String>> future = queue.submit(RequestQueueTest::unusedRequest, RetryPolicy.none());

		assertTrue(future.isCompletedExceptionally());
	}

	private static HttpRequest unusedRequest() {
		// Never actually dispatched in these tests (rate limiter keeps it queued until close()) - the
		// URI just needs to parse.
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:1/unused")).build();
	}
}
