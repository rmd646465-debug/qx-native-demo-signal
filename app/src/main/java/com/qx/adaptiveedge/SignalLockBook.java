package com.qx.adaptiveedge;

import java.util.HashMap;
import java.util.Map;

/** Pure-Java per-asset lock that refuses replacement during an active expiry. */
final class SignalLockBook<T> {
    static final class Entry<T> {
        final String assetKey;
        final String displayAsset;
        final T payload;
        final long issuedAt;
        final long expiresAt;
        boolean rated;

        Entry(String assetKey, String displayAsset, T payload, long issuedAt,
              long expiresAt) {
            this.assetKey = assetKey;
            this.displayAsset = displayAsset;
            this.payload = payload;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Entry<T>> entries = new HashMap<>();

    Entry<T> issueOrKeep(String assetKey, String displayAsset, T payload,
                         long now, long expiresAt) {
        Entry<T> active = active(assetKey, now);
        if (active != null) return active;
        Entry<T> created = new Entry<>(assetKey, displayAsset, payload, now,
                expiresAt);
        entries.put(assetKey, created);
        return created;
    }

    Entry<T> active(String assetKey, long now) {
        Entry<T> entry = entries.get(assetKey);
        return entry != null && entry.expiresAt > now ? entry : null;
    }

    Entry<T> latest(String assetKey) {
        return entries.get(assetKey);
    }

    void clear() {
        entries.clear();
    }
}
