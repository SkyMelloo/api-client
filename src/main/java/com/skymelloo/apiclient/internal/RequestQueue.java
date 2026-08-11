// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.internal;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;
import java.util.function.Supplier;

/**
 * Single background worker draining a delay queue, so every caller of the client shares one
 * rate-limited, retrying pipeline instead of each hand-rolling its own pacing. Requests become
 * eligible for dispatch once their delay (initially zero, or a backoff on retry) elapses; the
 * worker then waits for a rate-limiter token before actually sending.
 */
public final class RequestQueue implements AutoCloseable {
	private final DelayQueue<QueuedRequest> queue = new DelayQueue<>();
	private final RateLimiter rateLimiter;
	private final HttpClient httpClient;
	private final Thread worker;
	private volatile boolean closed = false;

	public RequestQueue(HttpClient httpClient, RateLimiter rateLimiter) {
		this.httpClient = httpClient;
		this.rateLimiter = rateLimiter;
		this.worker = new Thread(this::runLoop, "skymelloo-api-client-queue");
		this.worker.setDaemon(true);
		this.worker.start();
	}

	/**
	 * @param requestBuilder rebuilt fresh on every attempt (signing needs a new nonce/timestamp each time)
	 * @param retryPolicy governs 429/5xx retries for this call; {@link RetryPolicy#none()} disables retrying
	 */
	public CompletableFuture<HttpResponse<String>> submit(Supplier<HttpRequest> requestBuilder, RetryPolicy retryPolicy) {
		CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
		if (closed) {
			future.completeExceptionally(new IllegalStateException("RequestQueue is closed"));
			return future;
		}
		queue.put(new QueuedRequest(requestBuilder, retryPolicy, 0, future, 0));
		return future;
	}

	private void runLoop() {
		while (!closed) {
			QueuedRequest item;
			try {
				item = queue.take();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			long waitMs = rateLimiter.millisUntilNextToken();
			if (waitMs > 0) {
				try {
					Thread.sleep(waitMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			rateLimiter.consume();
			dispatch(item);
		}
	}

	private void dispatch(QueuedRequest item) {
		HttpRequest request;
		try {
			request = item.requestBuilder().get();
		} catch (RuntimeException e) {
			item.future.completeExceptionally(e);
			return;
		}
		httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
			if (error != null) {
				retryOrFail(item, null, error);
				return;
			}
			if ((response.statusCode() == 429 || response.statusCode() >= 500) && item.retryPolicy().canRetry(item.attempt())) {
				retryOrFail(item, response, null);
				return;
			}
			item.future.complete(response);
		});
	}

	private void retryOrFail(QueuedRequest item, HttpResponse<String> response, Throwable networkError) {
		if (!item.retryPolicy().canRetry(item.attempt())) {
			if (response != null) {
				item.future.complete(response);
			} else {
				item.future.completeExceptionally(networkError);
			}
			return;
		}
		long delay = response != null
				? retryAfterMillis(response).orElseGet(() -> item.retryPolicy().delayMillis(item.attempt()))
				: item.retryPolicy().delayMillis(item.attempt());
		queue.put(item.nextAttempt(delay));
	}

	/** Honors a numeric (seconds) Retry-After header if the server sends one; falls back to our own schedule otherwise. */
	private Optional<Long> retryAfterMillis(HttpResponse<String> response) {
		return response.headers().firstValue("Retry-After").flatMap(value -> {
			try {
				return Optional.of(Long.parseLong(value.trim()) * 1000);
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		});
	}

	@Override
	public void close() {
		closed = true;
		worker.interrupt();
	}
}
