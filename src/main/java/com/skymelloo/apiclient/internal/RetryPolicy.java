package com.skymelloo.apiclient.internal;

import java.util.Random;

/**
 * Backoff schedules with jitter. The 429 schedule matches DEVELOPER_API.md section 5's own
 * recommended handling (~2-3s, ~5-7s, ~10-15s, then give up); transient network/5xx failures get a
 * shorter schedule since those aren't the documented abuse-control path.
 */
public final class RetryPolicy {
	private static final Random JITTER = new Random();

	private final int maxAttempts;
	private final long[] baseDelaysMs;

	private RetryPolicy(int maxAttempts, long[] baseDelaysMs) {
		this.maxAttempts = maxAttempts;
		this.baseDelaysMs = baseDelaysMs;
	}

	public static RetryPolicy rateLimitDefault() {
		return new RetryPolicy(3, new long[]{2500, 6000, 12500});
	}

	public static RetryPolicy transientErrorDefault() {
		return new RetryPolicy(2, new long[]{1000, 3000});
	}

	public static RetryPolicy none() {
		return new RetryPolicy(0, new long[0]);
	}

	public boolean canRetry(int attemptNumber) {
		return attemptNumber < maxAttempts;
	}

	/** attemptNumber is 0-based (0 = the delay before the first retry). */
	public long delayMillis(int attemptNumber) {
		long base = baseDelaysMs[Math.min(attemptNumber, baseDelaysMs.length - 1)];
		long jitter = (long) (base * 0.2 * JITTER.nextDouble());
		return base + jitter;
	}
}
