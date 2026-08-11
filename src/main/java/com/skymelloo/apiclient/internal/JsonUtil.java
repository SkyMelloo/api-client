package com.skymelloo.apiclient.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Null-safe field extraction - the API can send a member as a literal JSON null, not just an absent key. */
public final class JsonUtil {
	private JsonUtil() {
	}

	public static boolean has(JsonObject obj, String key) {
		return obj != null && obj.has(key) && !obj.get(key).isJsonNull();
	}

	public static String str(JsonObject obj, String key) {
		return has(obj, key) ? obj.get(key).getAsString() : null;
	}

	public static String str(JsonObject obj, String key, String fallback) {
		return has(obj, key) ? obj.get(key).getAsString() : fallback;
	}

	public static boolean bool(JsonObject obj, String key, boolean fallback) {
		return has(obj, key) ? obj.get(key).getAsBoolean() : fallback;
	}

	public static int intVal(JsonObject obj, String key, int fallback) {
		return has(obj, key) ? obj.get(key).getAsInt() : fallback;
	}

	public static long longVal(JsonObject obj, String key, long fallback) {
		return has(obj, key) ? obj.get(key).getAsLong() : fallback;
	}

	public static JsonObject obj(JsonObject parent, String key) {
		return has(parent, key) ? parent.getAsJsonObject(key) : null;
	}

	public static JsonElement elem(JsonObject obj, String key) {
		return has(obj, key) ? obj.get(key) : null;
	}
}
