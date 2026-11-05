package dev.lunartiers.tagger.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class RanksTest {
    @Test void highestAndTiesAreDeterministic() {
        assertEquals(new Ranks.Tag("sword", "HT1"), Ranks.select(Map.of("crystal", "HT3", "sword", "HT1"), "auto"));
        assertEquals(new Ranks.Tag("crystal", "HT3"), Ranks.select(Map.of("sword", "HT3", "crystal", "HT3"), "auto"));
        for (String tier : Ranks.TIERS) assertEquals(tier, Ranks.select(Map.of("spear-mace", tier), "auto").tier());
    }
    @Test void manualSelectionDoesNotFallBack() {
        assertNull(Ranks.select(Map.of("crystal", "HT3"), "sword"));
        assertNull(Ranks.select(Map.of("crystal", "Unranked"), "auto"));
        assertNull(Ranks.select(Map.of("unknown", "HT1"), "auto"));
    }
    @Test void cacheRefreshAndFiveMinuteExpiry() {
        AtomicLong time = new AtomicLong(); RankCache cache = new RankCache(time::get); UUID id = UUID.randomUUID();
        assertEquals(List.of(id), cache.due(List.of(id, id), false));
        cache.put(Map.of(id, Map.of("crystal", "HT3"))); assertTrue(cache.due(List.of(id), false).isEmpty());
        time.set(60_000); assertEquals(List.of(id), cache.due(List.of(id), false)); assertFalse(cache.get(id).isEmpty());
        time.set(299_999); assertFalse(cache.get(id).isEmpty()); time.set(300_000); assertTrue(cache.get(id).isEmpty());
    }
    @Test void successfulUnlinkAndRestrictionClearCachedTagImmediately() {
        RankCache cache = new RankCache(() -> 0); UUID id = UUID.randomUUID();
        cache.put(Map.of(id, Map.of("sword", "HT1"))); cache.put(Map.of(id, Map.of()));
        assertTrue(cache.get(id).isEmpty()); assertTrue(cache.due(List.of(id), false).isEmpty());
    }
    @Test void malformedAndIncompleteResponsesAreRejected() {
        UUID id = UUID.fromString("12345678-1234-4234-8234-1234567890ab"); String key = id.toString().replace("-", "");
        assertEquals("HT3", RankClient.decode("{\"schemaVersion\":1,\"cacheSeconds\":60,\"players\":{\"" + key + "\":{\"crystal\":\"HT3\"}}}", List.of(id)).get(id).get("crystal"));
        for (String body : List.of("{}", "{\"schemaVersion\":2}", "{\"schemaVersion\":1,\"cacheSeconds\":60,\"players\":{}}", "{\"schemaVersion\":1,\"cacheSeconds\":60,\"players\":{\"" + key + "\":{\"discordId\":\"secret\"}}}")) assertThrows(Exception.class, () -> RankClient.decode(body, List.of(id)));
    }
    @Test void endpointMustUseHttpsAndContainNoCredentialsOrExtraParameters() {
        assertNotNull(RankClient.validateEndpoint("https://ranks.example.com/v1/ranks/lookup"));
        for (String value : List.of("", "http://example.com/v1/ranks/lookup", "https://user:pass@example.com/v1/ranks/lookup", "https://example.com/v1/ranks/lookup?token=secret", "https://example.com/elsewhere", "file:///secret")) assertNull(RankClient.validateEndpoint(value));
    }
    @Test void retryAfterIsBounded() {
        assertEquals(1000, RankClient.retryDelay("0")); assertEquals(300_000, RankClient.retryDelay("999999")); assertEquals(5000, RankClient.retryDelay("invalid"));
    }
    @Test void cacheIsBoundedAndClearWorks() {
        RankCache cache = new RankCache(() -> 0); List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {UUID id = UUID.randomUUID(); ids.add(id); cache.put(Map.of(id, Map.of("smp", "LT1")));}
        assertTrue(cache.get(ids.getFirst()).isEmpty()); assertFalse(cache.get(ids.getLast()).isEmpty()); cache.clear(); assertTrue(cache.get(ids.getLast()).isEmpty());
    }
    @Test void configuredEndpointReportsConnectionStateBeforeJoining() {
        try (RankClient client = new RankClient()) {
            assertEquals("API not configured", client.status());
            client.endpoint("https://ranks.example.com/v1/ranks/lookup"); assertEquals("Not connected to a server", client.status());
            client.endpoint(""); assertEquals("API not configured", client.status());
        }
    }
}
