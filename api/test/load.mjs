import { performance, monitorEventLoopDelay } from 'node:perf_hooks';
import { mkdirSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { MODES, project } from '../projection.mjs';
import { SnapshotReader } from '../snapshot.mjs';
import { createRankServer } from '../server.mjs';

const count = Number(process.env.LOAD_PLAYERS ?? 10_000);
if (!Number.isInteger(count) || count < 100 || count > 50_000) throw new Error('LOAD_PLAYERS must be 100..50000');
const root = {version: 1, guildId: '1', defaultModeId: 'crystal', players: {}, restrictions: [], servers: {'2': {version: 1, guildId: '2', defaultModeId: 'sword', players: {}, modes: {}, restrictions: [], queues: {NA: [], EU: [], AS: []}}}, jobs: {}};
const other = root.servers['2']; for (const mode of MODES.slice(2)) other.modes[mode] = {players: {}, queues: {NA: [], EU: [], AS: []}};
const ids = [];
for (let i = 0; i < count; i++) {
  const id = String(i), uuid = i.toString(16).padStart(32, '0'); ids.push(uuid);
  const p = {id, uuid, tier: 'HT3', ign: 'SyntheticPlayer', region: 'NA', preferredServer: 'synthetic', cooldownUntil: 0, waitlist: true};
  root.players[id] = p; other.players[id] = {...p, tier: 'LT2'};
  for (const mode of MODES.slice(2)) other.modes[mode].players[id] = {...p, tier: 'HT2'};
  for (const part of [other, ...Object.values(other.modes)]) part.queues[['NA', 'EU', 'AS'][i % 3]].push(id);
}
// Large unrelated archive/job payloads must not be copied into public responses.
for (let i = 0; i < 1000; i++) root.jobs[i] = {kind: 'archive', data: {transcript: 'private synthetic transcript '.repeat(300)}};
const dir = mkdtempSync(join(tmpdir(), 'lunar-load-')), file = join(dir, 'state.sqlite');
const db = new DatabaseSync(file); db.exec('CREATE TABLE crystal_state(guild TEXT PRIMARY KEY,revision INTEGER,body TEXT)');
const body = JSON.stringify(root); db.prepare('INSERT INTO crystal_state VALUES(?,0,?)').run('1', body); db.close();
const reader = new SnapshotReader(file, '1', [{guildId: '1', modes: ['crystal']}, {guildId: '2', modes: MODES.slice(1)}]);
const before = performance.now(); reader.refresh(); const projectionMs = performance.now() - before;
const unchangedStart = performance.now(); for (let n = 0; n < 100; n++) reader.refresh(); const unchangedReadMeanMs = (performance.now() - unchangedStart) / 100;
const server = createRankServer(reader, {requestsPerMinute: 100000}); server.listen(0, '127.0.0.1'); await new Promise(resolve => server.once('listening', resolve));
const delay = monitorEventLoopDelay({resolution: 10}); delay.enable(); const timings = []; let completed = 0, bytes = 0;
const started = performance.now();
try {
  await Promise.all(Array.from({length: 24}, async (_, worker) => {
    for (let index = worker; index < 400; index += 24) {
      const batch = Array.from({length: 100}, (_, n) => ids[(index * 100 + n) % count]);
      const t = performance.now(), response = await fetch(`http://127.0.0.1:${server.address().port}/v1/ranks/lookup`, {method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify({uuids: batch})});
      const text = await response.text(); if (response.status !== 200 || Object.keys(JSON.parse(text).players).length !== 100) throw new Error(`Unexpected response ${response.status}`);
      timings.push(performance.now() - t); completed++; bytes += Buffer.byteLength(text);
    }
  }));
  const durationMs = performance.now() - started; timings.sort((a, b) => a - b); delay.disable();
  const report = {at: new Date().toISOString(), node: process.version, platform: process.platform, syntheticPlayers: count, modes: 9, modeRegionQueues: 24, stateBytes: Buffer.byteLength(body), projectionMs, unchangedReadMeanMs, projectionReloads: reader.reloads, concurrentClients: 24, requests: completed, lookups: completed * 100, durationMs, p50Ms: timings[Math.floor(timings.length * .5)], p95Ms: timings[Math.floor(timings.length * .95)], responseBytes: bytes, eventLoopP99Ms: delay.percentile(99) / 1e6, rssMiB: process.memoryUsage().rss / 1024 / 1024};
  mkdirSync('build', {recursive: true}); writeFileSync('build/load-results.json', JSON.stringify(report, null, 2) + '\n'); console.log(JSON.stringify(report, null, 2));
} finally {server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); rmSync(dir, {recursive: true, force: true});}
