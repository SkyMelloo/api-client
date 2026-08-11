// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient;

/** A failed API call - either a real HTTP error status (see {@link #statusCode}) or a network-level failure ({@code statusCode == -1}). */
public class ApiException extends RuntimeException {
	private final int statusCode;

	public ApiException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public ApiException(String message, Throwable cause) {
		super(message, cause);
		this.statusCode = -1;
	}

	/** The HTTP status code, or -1 for a network/timeout failure with no response at all. */
	public int statusCode() {
		return statusCode;
	}

	public boolean isRateLimited() {
		return statusCode == 429;
	}

	public boolean isAuthFailure() {
		return statusCode == 401;
	}

	public boolean isNetworkFailure() {
		return statusCode == -1;
	}
}
