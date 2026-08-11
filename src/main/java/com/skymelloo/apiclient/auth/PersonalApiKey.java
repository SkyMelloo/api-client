package com.skymelloo.apiclient.auth;

/**
 * A personal API key from sky.melloo.me/account (section 2.2) - sent as {@code X-SkyMelloo-Test-Key},
 * no request signing needed. Testing/development only; never ship one in a distributed build.
 */
public record PersonalApiKey(String key) implements Credentials {
}
