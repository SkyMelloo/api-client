package com.skymelloo.apiclient.batch;

import com.skymelloo.apiclient.endpoints.PresenceApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Coalesces individual per-UUID presence lookups into batched {@code presence/query} calls - per
 * section 16's own "use one batched request rather than one request per UUID" guidance. Calls made
 * within the debounce window (default 75ms) share one request; results are split back out to each
 * caller. Chunks automatically if more UUIDs accumulate than the server accepts in one call.
 */
public final class PresenceBatcher implements AutoCloseable {
	private final PresenceApi presenceApi;
	private final long debounceMillis;
	private final ScheduledExecutorService scheduler;
	private final Object lock = new Object();
	private Map<String, List<CompletableFuture<Optional<PresenceApi.PresenceEntry>>>> pending = new LinkedHashMap<>();
	private ScheduledFuture<?> flushTask;

	public PresenceBatcher(PresenceApi presenceApi) {
		this(presenceApi, 75);
	}

	public PresenceBatcher(PresenceApi presenceApi, long debounceMillis) {
		this.presenceApi = presenceApi;
		this.debounceMillis = debounceMillis;
		this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "skymelloo-api-client-presence-batcher");
			t.setDaemon(true);
			return t;
		});
	}

	/** Resolves to empty if the UUID isn't currently present, never fails just because someone's offline. */
	public CompletableFuture<Optional<PresenceApi.PresenceEntry>> query(String uuid) {
		CompletableFuture<Optional<PresenceApi.PresenceEntry>> future = new CompletableFuture<>();
		synchronized (lock) {
			pending.computeIfAbsent(uuid, k -> new ArrayList<>()).add(future);
			if (flushTask == null) {
				flushTask = scheduler.schedule(this::flush, debounceMillis, TimeUnit.MILLISECONDS);
			}
		}
		return future;
	}

	private void flush() {
		Map<String, List<CompletableFuture<Optional<PresenceApi.PresenceEntry>>>> batch;
		synchronized (lock) {
			batch = pending;
			pending = new LinkedHashMap<>();
			flushTask = null;
		}
		if (batch.isEmpty()) {
			return;
		}
		List<String> uuids = new ArrayList<>(batch.keySet());
		for (int start = 0; start < uuids.size(); start += PresenceApi.MAX_QUERY_UUIDS) {
			List<String> chunk = uuids.subList(start, Math.min(start + PresenceApi.MAX_QUERY_UUIDS, uuids.size()));
			resolveChunk(batch, chunk);
		}
	}

	private void resolveChunk(Map<String, List<CompletableFuture<Optional<PresenceApi.PresenceEntry>>>> batch, List<String> chunk) {
		presenceApi.query(chunk).whenComplete((entries, error) -> {
			if (error != null) {
				chunk.forEach(u -> batch.get(u).forEach(f -> f.completeExceptionally(error)));
				return;
			}
			Map<String, PresenceApi.PresenceEntry> byUuid = entries.stream()
					.collect(Collectors.toMap(PresenceApi.PresenceEntry::uuid, e -> e, (a, b) -> a));
			chunk.forEach(u -> {
				Optional<PresenceApi.PresenceEntry> result = Optional.ofNullable(byUuid.get(u));
				batch.get(u).forEach(f -> f.complete(result));
			});
		});
	}

	@Override
	public void close() {
		scheduler.shutdownNow();
	}
}
