package com.skymelloo.apiclient.internal;

/**
 * Token bucket, client-side pacing so a bug or busy loop in the embedding mod can't blast the
 * documented server ceilings (600 req/min/UUID on v1 - see DEVELOPER_API.md section 5). Default is
 * deliberately conservative, well under that ceiling, per the doc's own "safety ceilings, not
 * polling targets" guidance.
 */
public final class RateLimiter {
	public static final double DEFAULT_SUSTAINED_PER_SECOND = 8.0;
	public static final double DEFAULT_BURST_CAPACITY = 10.0;

	private final double capacity;
	private final double refillPerSecond;
	private double tokens;
	private long lastRefillNanos;

	public RateLimiter(double sustainedPerSecond, double burstCapacity) {
		this.refillPerSecond = sustainedPerSecond;
		this.capacity = burstCapacity;
		this.tokens = burstCapacity;
		this.lastRefillNanos = System.nanoTime();
	}

	public static RateLimiter withDefaults() {
		return new RateLimiter(DEFAULT_SUSTAINED_PER_SECOND, DEFAULT_BURST_CAPACITY);
	}

	/** 0 if a token is available right now, otherwise how long to wait before one will be. */
	public synchronized long millisUntilNextToken() {
		refill();
		if (tokens >= 1.0) {
			return 0;
		}
		double needed = 1.0 - tokens;
		return (long) Math.ceil((needed / refillPerSecond) * 1000.0);
	}

	public synchronized void consume() {
		refill();
		tokens = Math.max(0, tokens - 1.0);
	}

	private void refill() {
		long now = System.nanoTime();
		double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
		tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
		lastRefillNanos = now;
	}
}
