# SkyMelloo API Client

Java client library for the [SkyMelloo/MellooEssentials Developer API](https://sky.melloo.me/developer-api/reference) ([DEVELOPER_API.md](https://github.com/SkyMelloo/developer-api/blob/main/DEVELOPER_API.md)). Handles request signing, client-side rate-limit pacing, retry/backoff on `429`/`5xx`, and batching where the API supports it (`presence/query`), so a mod using the API doesn't have to hand-roll any of that.

Requires Java 17+. Not tied to Minecraft/Fabric - the only game-specific step (Mojang's `joinServer` proof) is the embedding mod's own responsibility; everything else is plain Java.

Not published to a Maven repository yet - build and publish to your local Maven repo:

```
./gradlew publishToMavenLocal
```

```gradle
repositories {
    mavenLocal()
}
dependencies {
    implementation 'com.skymelloo:skymelloo-api-client:0.1.0'
}
```

## Quickstart: real mod auth

The library builds the ephemeral Ed25519 key pair and signs every request; your mod supplies the live Minecraft session for the one step that needs it.

```java
KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

SkyMellooClient bootstrap = SkyMellooClient.builder()
    .namespace(ModNamespace.SKYMELLOO) // or MELLOOESSENTIALS
    .build();

AuthApi.Challenge challenge = bootstrap.auth().requestChallenge().join();

// The one step this library can't do for you - a real Minecraft session's own join proof:
sessionService.joinServer(profileId, accessToken, challenge.serverId());

AuthApi.VerifyResult verified = bootstrap.auth()
    .verify(challenge.serverId(), username, uuid, publicKeyBase64)
    .join();

long clockOffsetMs = verified.serverTime() - System.currentTimeMillis();
ModIdentity identity = new ModIdentity(uuid, username, keyPair.getPrivate(), clockOffsetMs);

SkyMellooClient client = bootstrap.withCredentials(identity);
```

`withCredentials` reuses the same underlying connection, request queue, and rate-limit budget as `bootstrap` - it's a cheap call, not a second client. Keep one `SkyMellooClient` per game session and reuse it; don't build a new one per request.

## Quickstart: personal API key (testing only)

No Minecraft session needed - good for local development, CI, or standalone tooling. Generate one at [sky.melloo.me/account](https://sky.melloo.me/account) (see section 2.2). **Never ship a test key in a distributed build.**

```java
SkyMellooClient client = SkyMellooClient.builder()
    .namespace(ModNamespace.SKYMELLOO)
    .personalApiKey(System.getenv("SKYMELLOO_TEST_KEY"))
    .build();
```

## Usage

```java
// Cloud settings
Optional<SettingsApi.CloudSettings> saved = client.settings().get().join();
client.settings().set(mySettingsAsJsonObject).join();

// Presence
client.presence().report("online", null, false, true, "Hub", List.of()).join();
List<PresenceApi.PresenceEntry> nearby = client.presence().query(nearbyUuids).join();

// Friends
client.friends().request("SomeUsername").join();
FriendsApi.FriendsList list = client.friends().list().join();

// Relay
client.relay().sendDirect("FriendName", "hello :3").join();
List<RelayApi.InboxMessage> inbox = client.relay().pollInbox().join();

// Version check (auto-picks the SkyMelloo/MellooEssentials path variant from your client's namespace)
MetaApi.VersionCheckResult version = client.meta().versionCheck(modVersion, jarHash).join();
if (version.unofficialBuildMessage() != null) {
    showToUser(version.unofficialBuildMessage()); // required by compatibility rule 9
}
```

Every call returns a `CompletableFuture` - none of them block the calling thread. Exceptions surface as [`ApiException`](src/main/java/com/skymelloo/apiclient/ApiException.java) (check `statusCode()`, or `isRateLimited()`/`isAuthFailure()`/`isNetworkFailure()`), wrapped in the usual `CompletionException` if you access the future via `.join()`/`.get()` rather than `.exceptionally()`/`.handle()`.

An endpoint the library hasn't wrapped yet (or whose shape is explicitly documented as evolvable) is reachable via `client.rawGet(path, requiresAuth)` / `client.rawPost(path, body, requiresAuth)`, returning parsed but untyped `JsonElement`.

## Batching presence lookups

Calling `presence().query(List.of(uuid))` once per nearby player defeats the point of the batch endpoint. `PresenceBatcher` coalesces individual lookups made within a short window into one request:

```java
PresenceBatcher batcher = client.newPresenceBatcher(); // default 75ms debounce
Optional<PresenceApi.PresenceEntry> entry = batcher.query(someUuid).join();
```

Every `query()` call made within the debounce window - regardless of which part of your mod calls it - shares one `presence/query` request. Close it (`batcher.close()`) when you're done, e.g. on disconnect.

## Rate limiting and retries

Every request goes through one shared queue (`SkyMellooClient.Builder#rateLimit`, default 8 req/s sustained / burst 10) - well under the documented `600 req/min/UUID` v1 ceiling (section 5), so a busy loop elsewhere in your mod can't blow through the server's own limit. On `429`, the queue honors a numeric `Retry-After` header if the server sends one, otherwise backs off on the same schedule the doc recommends (~2-3s, ~5-7s, ~10-15s, then gives up and fails the call). Transient `5xx`/network failures get a shorter two-attempt retry with jitter. A retried signed request is re-signed with a fresh nonce and timestamp each attempt, never resent as-is.

## What this library does not do

- The Mojang `joinServer` proof itself (needs a real Minecraft session - see the quickstart above).
- Server-Sent Events consumption for `presence/stream` (documented as a website-facing implementation detail, not meant for mod clients).
- Anything the [compatibility rules](https://github.com/SkyMelloo/developer-api/blob/main/DEVELOPER_API.md#18-compatibility-rules-for-forks) leave to the embedding mod - most notably rule 9: **your mod is responsible for actually showing the user `unofficialBuildMessage`/`updateAvailableMessage` when non-null.** This library surfaces the field; it doesn't render UI.

## Questions

Using this library and have a question? Start a thread on [sky.melloo.me/community](https://sky.melloo.me/community), or if you'd rather ask privately: [sky.melloo.me/contact/ask](https://sky.melloo.me/contact/ask).

## License

[MIT](LICENSE). Copyright (c) 2026 Maja Bekurdts (hexedmaya).
