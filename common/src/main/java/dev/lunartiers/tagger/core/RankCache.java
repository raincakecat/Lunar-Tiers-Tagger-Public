package dev.lunartiers.tagger.core;

import java.util.*;
import java.util.function.LongSupplier;

/** Network-free, synchronized cache. Missing ranks are cached too. Never kept on disk. */
public final class RankCache {
    private record Entry(Map<String, String> ranks, long at) {}
    private final Map<UUID, Entry> entries = new LinkedHashMap<>(128, .75f, true);
    private final LongSupplier clock;
    public RankCache(LongSupplier clock) {this.clock = clock;}
    public synchronized Map<String, String> get(UUID id) {
        Entry entry = entries.get(id);
        return entry == null || clock.getAsLong() - entry.at >= 300_000 ? Map.of() : entry.ranks;
    }
    public synchronized List<UUID> due(Collection<UUID> ids, boolean force) {
        long now = clock.getAsLong();
        return ids.stream().distinct().limit(4096).filter(id -> force || !entries.containsKey(id) || now - entries.get(id).at >= 60_000).toList();
    }
    public synchronized void put(Map<UUID, Map<String, String>> values) {
        long now = clock.getAsLong();
        values.forEach((id, ranks) -> entries.put(id, new Entry(Map.copyOf(ranks), now)));
        while (entries.size() > 4096) entries.remove(entries.keySet().iterator().next());
    }
    public synchronized void clear() {entries.clear();}
}
