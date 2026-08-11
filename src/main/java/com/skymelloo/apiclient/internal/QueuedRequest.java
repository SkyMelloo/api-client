// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.internal;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * One pending call in the {@link RequestQueue}. {@code requestBuilder} is a supplier, not a built
 * request, because a retried signed request needs a fresh nonce and timestamp each attempt - see
 * DEVELOPER_API.md section 3.1 on nonce reuse.
 */
final class QueuedRequest implements Delayed {
	private final Supplier<HttpRequest> requestBuilder;
	private final RetryPolicy retryPolicy;
	private final int attempt;
	private final long readyAtNanos;
	final CompletableFuture<HttpResponse<String>> future;

	QueuedRequest(Supplier<HttpRequest> requestBuilder, RetryPolicy retryPolicy, int attempt,
			CompletableFuture<HttpResponse<String>> future, long delayMillis) {
		this.requestBuilder = requestBuilder;
		this.retryPolicy = retryPolicy;
		this.attempt = attempt;
		this.future = future;
		this.readyAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
	}

	Supplier<HttpRequest> requestBuilder() {
		return requestBuilder;
	}

	RetryPolicy retryPolicy() {
		return retryPolicy;
	}

	int attempt() {
		return attempt;
	}

	QueuedRequest nextAttempt(long delayMillis) {
		return new QueuedRequest(requestBuilder, retryPolicy, attempt + 1, future, delayMillis);
	}

	@Override
	public long getDelay(TimeUnit unit) {
		return unit.convert(readyAtNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
	}

	@Override
	public int compareTo(Delayed other) {
		return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
	}
}
