// Copyright (c) 2026 Maja Bekurdts (hexedmaya)
// SPDX-License-Identifier: MIT

package com.skymelloo.apiclient.auth;

/** Distinguishes SkyMelloo from MellooEssentials on shared endpoints (settings namespace, presence marker) - see section 4. */
public enum ModNamespace {
	SKYMELLOO("mod"),
	MELLOOESSENTIALS(null);

	private final String headerValue;

	ModNamespace(String headerValue) {
		this.headerValue = headerValue;
	}

	/** Null means "send no X-SkyMelloo-Client header at all" - the server treats anything other than the literal "mod" as MellooEssentials. */
	public String headerValue() {
		return headerValue;
	}
}
