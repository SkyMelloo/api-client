// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

	@Test
	void burstCapacityAllowsImmediateConsumptionUpToCapacity() {
		RateLimiter limiter = new RateLimiter(5.0, 3.0);
		for (int i = 0; i < 3; i++) {
			assertEquals(0, limiter.millisUntilNextToken());
			limiter.consume();
		}
		assertTrue(limiter.millisUntilNextToken() > 0);
	}

	@Test
	void waitTimeShrinksAsRateIncreases() {
		RateLimiter slow = new RateLimiter(1.0, 1.0);
		RateLimiter fast = new RateLimiter(100.0, 1.0);
		slow.consume();
		fast.consume();
		assertTrue(slow.millisUntilNextToken() >= fast.millisUntilNextToken());
	}
}
