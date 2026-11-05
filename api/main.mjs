import { readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { SnapshotReader } from './snapshot.mjs';
import { createRankServer } from './server.mjs';
import { MODES } from './projection.mjs';

const config = JSON.parse(readFileSync(process.env.RANK_API_CONFIG ?? 'api/config.local.json', 'utf8'));
if (!config.database || !/^\d+$/.test(config.rootGuildId) || !Array.isArray(config.sources) || !config.sources.length) throw new Error('Invalid rank API configuration');
const published = new Set();
for (const source of config.sources) {
  if (!/^\d+$/.test(source.guildId) || !Array.isArray(source.modes) || !source.modes.length) throw new Error('Invalid rank source');
  for (const mode of source.modes) {if (!MODES.includes(mode) || published.has(mode)) throw new Error('Invalid or duplicate public mode'); published.add(mode);}
}
const file = resolve(config.database), reader = new SnapshotReader(file, config.rootGuildId, config.sources);
reader.fileIdentity = () => {const s = statSync(file, {bigint: true}); return `${s.dev}:${s.ino}:${s.birthtimeNs}`;};
let failed = false;
function refresh() {try {reader.refresh(); if (failed) console.info('Rank snapshot recovered'); failed = false;} catch {if (!failed) console.error('Rank snapshot unavailable; serving will stop until recovery'); failed = true;}}
refresh();
const timer = setInterval(refresh, 5000); timer.unref();
const port = Number(process.env.RANK_API_PORT ?? 8797);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid API port');
const server = createRankServer(reader, {trustProxy: config.trustProxy === true});
server.listen(port, '127.0.0.1', () => console.info('Lunar rank API listening on loopback'));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => {clearInterval(timer); server.close(); setTimeout(() => process.exit(0), 6000).unref();});
