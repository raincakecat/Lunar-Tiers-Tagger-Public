# Public rank API v1

## Lookup

`POST https://YOUR_HOST/v1/ranks/lookup`

Required content type: `application/json`. No login or embedded API key is required; the returned ranks are deliberately public.

```json
{"uuids":["123456781234423482341234567890ab"]}
```

Accepts 1–100 UUIDs. UUIDs may be 32 hex characters or standard hyphenated form and are normalized to lowercase without hyphens. Duplicate UUIDs collapse to one result. Unknown keys and malformed UUIDs are rejected. Maximum request body: **8 KiB**.

```json
{
  "schemaVersion": 1,
  "cacheSeconds": 60,
  "players": {
    "123456781234423482341234567890ab": {
      "crystal": "HT3",
      "sword": "LT2"
    }
  }
}
```

Every requested UUID is present. Missing, unlinked, restricted, conflicted, or unranked modes are omitted; a UUID with no visible ranks returns `{}`. An unavailable source returns **503**, never a successful empty projection that would falsely erase ranks.

Only the nine supported mode keys and HT1–LT5 ranks are returned. There are no Discord IDs, names, region settings, restriction reasons, history, cooldowns, server identifiers, or database metadata in responses.

## Sources and restrictions

Host-only `api/config.local.json` explicitly maps each Discord guild to its public modes. Each mode may be published once. Canonical UUIDs come from the guild profile, never a possibly stale copy in a mode partition. Same-UUID/different-Discord and same-Discord/different-UUID conflicts hide affected associations.

The guild's default mode uses its root players; other modes use the matching mode partition's rank. The current saved tier is authoritative, including LT3 assigned during evaluation progression. Leaving Discord does not hide earned ranks. Region does not change the UUID lookup result.

Active restrictions are matched by Discord member or Minecraft UUID within the affected guild. Permanent restrictions remain hidden; timed restrictions are evaluated at lookup time, so expiry does not require a new state revision. Unlink, rank, restriction, and restore changes normally reach the projection within five seconds and clients at their next refresh. During an outage, clients may show their last result for at most five minutes from its successful fetch.

## Errors and limits

- **400** invalid JSON, unknown fields, invalid UUIDs, or batch outside 1–100.
- **404** unknown path; **405** wrong method; **408** request timeout.
- **413** body too large; **415** content type unsupported.
- **429** limit exceeded; **503** unavailable snapshot or concurrency capacity.

Defaults: **120 requests/minute/IP**, **500 requests/second globally**, 32 simultaneous body reads, 128 connections, 5-second body-read timeout. Limit tracking is bounded to 10,000 IP entries. `Retry-After` accompanies 429/503 responses. These limits are per API process; run one service instance unless a shared edge limiter is added.

Forwarded IP headers are ignored by default. Enable `trustProxy` only behind a trusted loopback reverse proxy that overwrites `X-Forwarded-For` with one validated client IP. The supplied Caddy example does this. Do not pass arbitrary user-supplied forwarding chains through a tunnel configuration.

`GET /health` is loopback-only through the supplied proxy configuration and returns only `{"ready":true|false}`. A snapshot becomes unavailable after 15 seconds without a successful database check. No diagnostic path exposes database locations or exceptions.

## Privacy and identity limits

The mod sends multiplayer UUIDs to the configured API and displays the returned ranks. It does not send server addresses, chat, position, usernames, Discord credentials, or game session tokens. API logs contain service health transitions only, not request bodies or player records. IP rate-limit state is held in memory for short windows.

UUID association relies on the existing bot's account linking. IGN lookup validates account existence and does not prove ownership. The tagger does not introduce a new ownership-verification claim. Offline-mode servers that supply noncanonical UUIDs will normally have no matching tags; name-based fallback is intentionally absent.
