package com.skymelloo.apiclient.auth;

/** Either a live {@link ModIdentity} (Ed25519, per-request signing) or a {@link PersonalApiKey} (testing only). */
public sealed interface Credentials permits ModIdentity, PersonalApiKey {
}
