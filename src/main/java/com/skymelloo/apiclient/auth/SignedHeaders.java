package com.skymelloo.apiclient.auth;

/** The 4 headers a signed request must carry - see {@link ModIdentity#sign}. */
public record SignedHeaders(String uuid, String timestamp, String nonce, String signature) {
}
