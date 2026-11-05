package dev.lunartiers.tagger.core;

import com.google.gson.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** One bounded background worker; Minecraft render and tick threads never perform network IO. */
public final class RankClient implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {Thread t = new Thread(r, "lunar-rank-fetch"); t.setDaemon(true); return t;});
    private final HttpClient http;
    private final RankCache cache = new RankCache(RankClient::now);
    private volatile String status = "API not configured";
    private volatile boolean working;
    private long retryAt = Long.MIN_VALUE, lastManualRefresh = Long.MIN_VALUE;
    private int failures, generation;
    private URI endpoint;
    public RankClient() {this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());}
    RankClient(HttpClient http) {this.http = http;}
    private static long now() {return System.nanoTime() / 1_000_000;}
    public synchronized void endpoint(String value) {
        URI next = validateEndpoint(value);
        if (!Objects.equals(next, endpoint)) {generation++; endpoint = next; cache.clear(); failures = 0; retryAt = Long.MIN_VALUE; status = next == null ? "API not configured" : "Not connected to a server";}
        if (next == null) status = "API not configured";
    }
    public static URI validateEndpoint(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || !"/v1/ranks/lookup".equals(uri.getPath())) return null;
            return uri;
        } catch (IllegalArgumentException ignored) {return null;}
    }
    public Map<String, String> ranks(UUID id) {return cache.get(id);}
    public String status() {return status;}
    public boolean working() {return working;}
    public synchronized void disconnect() {generation++; cache.clear(); failures = 0; retryAt = Long.MIN_VALUE; status = endpoint == null ? "API not configured" : "Not connected to a server";}
    public synchronized void refresh(Collection<UUID> ids, boolean manual) {
        if (endpoint == null || working || now() < retryAt) return;
        if (manual && lastManualRefresh != Long.MIN_VALUE && now() - lastManualRefresh < 5000) return;
        List<UUID> due = cache.due(ids, manual);
        if (due.isEmpty()) return;
        if (manual) lastManualRefresh = now();
        final URI target = endpoint; final int epoch = generation;
        working = true; status = "Updating ranks…";
        worker.execute(() -> {
            try {
                for (int offset = 0; offset < due.size(); offset += 100) {
                    synchronized (this) {if (epoch != generation) return;}
                    List<UUID> batch = due.subList(offset, Math.min(offset + 100, due.size()));
                    String body = GSON.toJson(Map.of("uuids", batch.stream().map(id -> id.toString().replace("-", "")).toList()));
                    HttpRequest request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json").header("Accept", "application/json").header("User-Agent", "Lunar-Tiers-Tagger/0.1.0").POST(HttpRequest.BodyPublishers.ofString(body)).build();
                    HttpResponse<String> response = http.send(request, info -> new LimitedBody());
                    if (response.statusCode() != 200) throw new RetryFailure(response.statusCode() == 429 || response.statusCode() == 503 ? retryDelay(response.headers().firstValue("Retry-After").orElse("5")) : 0);
                    Map<UUID, Map<String, String>> parsed = decode(response.body(), batch);
                    synchronized (this) {if (epoch != generation) return; cache.put(parsed);}
                }
                synchronized (this) {if (epoch == generation) {failures = 0; retryAt = Long.MIN_VALUE; status = "Connected · updates every 60s";}}
            } catch (Exception error) {
                synchronized (this) {if (epoch == generation) {
                    failures = Math.min(6, failures + 1);
                    long delay = Math.max(Math.min(300_000L, 5000L << (failures - 1)), error instanceof RetryFailure r ? r.delay : 0);
                    retryAt = now() + delay; status = "API unavailable · cache lasts 5m";
                }}
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally {working = false;}
        });
    }
    static Map<UUID, Map<String, String>> decode(String body, List<UUID> expected) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (root.get("schemaVersion").getAsInt() != 1 || root.get("cacheSeconds").getAsInt() != 60) throw new IllegalArgumentException("Invalid schema");
        JsonObject players = root.getAsJsonObject("players");
        Set<String> allowed = new HashSet<>(); for (UUID id : expected) allowed.add(id.toString().replace("-", ""));
        if (!players.keySet().equals(allowed)) throw new IllegalArgumentException("Incomplete response");
        Map<UUID, Map<String, String>> result = new HashMap<>();
        for (UUID id : expected) {
            JsonObject value = players.getAsJsonObject(id.toString().replace("-", "")); Map<String, String> ranks = new HashMap<>();
            for (var item : value.entrySet()) {
                if (!Ranks.MODES.contains(item.getKey()) || !item.getValue().isJsonPrimitive() || !item.getValue().getAsJsonPrimitive().isString() || !Ranks.TIERS.contains(item.getValue().getAsString())) throw new IllegalArgumentException("Invalid rank");
                ranks.put(item.getKey(), item.getValue().getAsString());
            }
            result.put(id, ranks);
        }
        return result;
    }
    static long retryDelay(String value) {
        try {return Math.clamp(Long.parseLong(value), 1, 300) * 1000;} catch (Exception ignored) {
            try {return Math.clamp(Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis(), 1000, 300_000);} catch (Exception invalid) {return 5000;}
        }
    }
    private static final class RetryFailure extends Exception {final long delay; RetryFailure(long delay) {this.delay = delay;}}
    private static final class LimitedBody implements HttpResponse.BodySubscriber<String> {
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(); private Flow.Subscription subscription;
        public CompletionStage<String> getBody() {return body;}
        public void onSubscribe(Flow.Subscription value) {subscription = value; value.request(1);}
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 256 * 1024) {subscription.cancel(); body.completeExceptionally(new IllegalArgumentException("Response too large")); return;}
                byte[] next = new byte[buffer.remaining()]; buffer.get(next); bytes.writeBytes(next);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) {body.completeExceptionally(error);}
        public void onComplete() {body.complete(bytes.toString(StandardCharsets.UTF_8));}
    }
    @Override public void close() {worker.shutdownNow(); http.close();}
}
