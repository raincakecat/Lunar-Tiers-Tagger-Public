import { createServer } from 'node:http';
import { isIP } from 'node:net';
import { lookup, uuid } from './projection.mjs';

export function createRankServer(reader, {clock = Date.now, trustProxy = false, requestsPerMinute = 120, maxConcurrent = 32, maxTrackedIps = 10_000} = {}) {
  const buckets = new Map(); let active = 0, globalStart = clock(), globalCount = 0;
  const server = createServer(async (req, res) => {
    const reply = (status, value, extra = {}) => {
      if (!res.destroyed) res.writeHead(status, {'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', ...extra}).end(JSON.stringify(value));
    };
    const address = req.socket.remoteAddress;
    const forwarded = req.headers['x-forwarded-for'];
    const ip = trustProxy && ['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(address) && typeof forwarded === 'string' && isIP(forwarded) ? forwarded : address;
    const now = clock();
    if (now - globalStart >= 1000) {globalStart = now; globalCount = 0;}
    if (++globalCount > 500) return reply(429, {error: 'busy'}, {'Retry-After': '5'});
    if (buckets.size >= maxTrackedIps) for (const [key, value] of buckets) if (value.until <= now) buckets.delete(key);
    let bucket = buckets.get(ip);
    if (!bucket || bucket.until <= now) {
      if (buckets.size >= maxTrackedIps && !buckets.has(ip)) return reply(429, {error: 'busy'}, {'Retry-After': '60'});
      bucket = {count: 0, until: now + 60_000}; buckets.set(ip, bucket);
    }
    if (++bucket.count > requestsPerMinute) return reply(429, {error: 'rate_limited'}, {'Retry-After': String(Math.max(1, Math.ceil((bucket.until - now) / 1000)))});
    if (req.method === 'GET' && req.url === '/health') return reply(reader.ready() ? 200 : 503, {ready: reader.ready()});
    if (req.url !== '/v1/ranks/lookup') return reply(404, {error: 'not_found'});
    if (req.method !== 'POST') return reply(405, {error: 'method_not_allowed'}, {Allow: 'POST'});
    if (req.headers['content-type']?.split(';')[0].trim().toLowerCase() !== 'application/json') return reply(415, {error: 'json_required'});
    if (Number(req.headers['content-length'] ?? 0) > 8192) {req.resume(); return reply(413, {error: 'body_too_large'});}
    if (active >= maxConcurrent) return reply(503, {error: 'busy'}, {'Retry-After': '5'});
    active++;
    let timeout;
    try {
      timeout = setTimeout(() => {reply(408, {error: 'request_timeout'}); req.destroy();}, 5000); timeout.unref();
      const chunks = []; let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > 8192) {reply(413, {error: 'body_too_large'}); req.destroy(); return;}
        chunks.push(chunk);
      }
      let body; try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { return reply(400, {error: 'invalid_json'}); }
      if (!body || Object.keys(body).some(k => k !== 'uuids') || !Array.isArray(body.uuids) || body.uuids.length < 1 || body.uuids.length > 100 || body.uuids.some(v => !uuid(v))) return reply(400, {error: 'invalid_uuids'});
      if (!reader.ready()) return reply(503, {error: 'ranks_unavailable'}, {'Retry-After': '5'});
      return reply(200, {schemaVersion: 1, cacheSeconds: 60, players: lookup(reader.data, [...new Set(body.uuids.map(uuid))], now)});
    } catch { if (!res.headersSent) reply(400, {error: 'invalid_request'}); }
    finally {clearTimeout(timeout); active--;}
  });
  server.requestTimeout = 6000; server.headersTimeout = 6000; server.keepAliveTimeout = 5000; server.maxConnections = 128;
  return server;
}
