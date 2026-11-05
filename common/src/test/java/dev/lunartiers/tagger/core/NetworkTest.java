package dev.lunartiers.tagger.core;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.Test;
import javax.net.ssl.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class NetworkTest {
    private static SSLContext tls() throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(Path.of(System.getProperty("lunar.testKeystore")))) {keys.load(input, "test-only-local".toCharArray());}
        KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); km.init(keys, "test-only-local".toCharArray());
        TrustManagerFactory tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); tm.init(keys);
        SSLContext ssl = SSLContext.getInstance("TLS"); ssl.init(km.getKeyManagers(), tm.getTrustManagers(), null); return ssl;
    }
    private static void until(BooleanSupplier predicate) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (!predicate.getAsBoolean() && System.nanoTime() < end) Thread.sleep(5);
        assertTrue(predicate.getAsBoolean(), "Background operation completed in time");
    }
    private static String response(HttpExchange exchange, String tier) throws Exception {
        JsonObject body = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, Object> players = new HashMap<>();
        assertTrue(body.getAsJsonArray("uuids").size() <= 100);
        for (var id : body.getAsJsonArray("uuids")) players.put(id.getAsString(), tier == null ? Map.of() : Map.of("sword", tier));
        return new Gson().toJson(Map.of("schemaVersion", 1, "cacheSeconds", 60, "players", players));
    }
    private static final class Fixture implements AutoCloseable {
        final HttpsServer server; final RankClient client;
        Fixture(HttpHandler handler) throws Exception {
            SSLContext ssl = tls(); server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(ssl)); server.createContext("/v1/ranks/lookup", handler); server.start();
            client = new RankClient(HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(2)).build());
            client.endpoint("https://localhost:" + server.getAddress().getPort() + "/v1/ranks/lookup");
        }
        public void close() {server.stop(0); client.close();}
    }
    private static void send(HttpExchange e, int status, String text) throws java.io.IOException {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8); e.sendResponseHeaders(status, bytes.length); try (var output = e.getResponseBody()) {output.write(bytes);}
    }
    @Test void batchesAndCoalescesWithoutBlockingCaller() throws Exception {
        AtomicInteger requests = new AtomicInteger(); CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try (Fixture f = new Fixture(e -> {
            try {requests.incrementAndGet(); started.countDown(); release.await(5, TimeUnit.SECONDS); send(e, 200, response(e, "HT3"));} catch (Exception error) {throw new RuntimeException(error);}
        })) {
            List<UUID> ids = java.util.stream.IntStream.range(0, 250).mapToObj(n -> UUID.randomUUID()).toList();
            long start = System.nanoTime(); f.client.refresh(ids, false);
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(100), "Refresh is asynchronous");
            assertTrue(started.await(5, TimeUnit.SECONDS)); f.client.refresh(ids, false); release.countDown();
            until(() -> !f.client.working()); assertEquals(3, requests.get());
            for (UUID id : ids) assertEquals("HT3", f.client.ranks(id).get("sword"));
            f.client.refresh(ids, false); assertFalse(f.client.working()); assertEquals(3, requests.get());
        } finally {release.countDown();}
    }
    @Test void rateLimitsBackOffAndRetainPreviouslyCachedRanks() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (Fixture f = new Fixture(e -> {
            int n = requests.incrementAndGet();
            try {if (n == 1) send(e, 200, response(e, "HT2")); else {e.getResponseHeaders().add("Retry-After", "60"); send(e, 429, "{}");}} catch (Exception error) {throw new RuntimeException(error);}
        })) {
            UUID id = UUID.randomUUID(); f.client.refresh(List.of(id), false); until(() -> !f.client.working());
            UUID unknown = UUID.randomUUID(); f.client.refresh(List.of(unknown), false); until(() -> !f.client.working());
            assertEquals("HT2", f.client.ranks(id).get("sword")); assertTrue(f.client.ranks(unknown).isEmpty());
            f.client.refresh(List.of(unknown), true); assertFalse(f.client.working()); assertEquals(2, requests.get());
        }
    }
    @Test void disconnectDiscardsInFlightResponses() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try (Fixture f = new Fixture(e -> {
            try {String result = response(e, "HT1"); started.countDown(); release.await(5, TimeUnit.SECONDS); send(e, 200, result);} catch (Exception error) {throw new RuntimeException(error);}
        })) {
            UUID id = UUID.randomUUID(); f.client.refresh(List.of(id), false); assertTrue(started.await(5, TimeUnit.SECONDS));
            f.client.disconnect(); release.countDown(); until(() -> !f.client.working()); assertTrue(f.client.ranks(id).isEmpty());
        } finally {release.countDown();}
    }
    @Test void oversizedResponseIsRejected() throws Exception {
        try (Fixture f = new Fixture(e -> {e.getRequestBody().readAllBytes(); send(e, 200, "x".repeat(300_000));})) {
            UUID id = UUID.randomUUID(); f.client.refresh(List.of(id), false); until(() -> !f.client.working());
            assertTrue(f.client.ranks(id).isEmpty()); assertTrue(f.client.status().contains("unavailable"));
        }
    }
}
