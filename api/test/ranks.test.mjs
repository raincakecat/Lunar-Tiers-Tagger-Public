import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, renameSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { MODES, uuid, project, lookup } from '../projection.mjs';
import { SnapshotReader } from '../snapshot.mjs';
import { createRankServer } from '../server.mjs';

const u = '123456781234423482341234567890ab', v = 'aaaaaaaaaaaa4aaa8aaaaaaaaaaaaaaa';
const sources = [{guildId: '1', modes: ['crystal']}, {guildId: '2', modes: MODES.slice(1)}];
function fixture() {
  const player = {id: '42', uuid: u, ign: 'private-name', tier: 'HT3', cooldownUntil: 999, preferredServer: 'private-server'};
  return {version: 1, guildId: '1', defaultModeId: 'crystal', players: {'42': {...player}}, restrictions: [], servers: {'2': {
    version: 1, guildId: '2', defaultModeId: 'sword', players: {'42': {...player, tier: 'LT2'}}, restrictions: [],
    modes: Object.fromEntries(MODES.slice(2).map(mode => [mode, {players: {'42': {...player, tier: 'HT2'}}}]))
  }}};
}
test('UUID validation accepts canonical forms only', () => {
  assert.equal(uuid(u.toUpperCase()), u); assert.equal(uuid('12345678-1234-4234-8234-1234567890ab'), u);
  for (const value of [null, 1, '0', '-'.repeat(32), u + ' ', u.slice(0, 8) + '-' + u.slice(8)]) assert.equal(uuid(value), null);
});
test('all nine modes use canonical profiles and exclude private fields', () => {
  const root = fixture(), data = lookup(project(root, sources), [u, v], 1000);
  assert.equal(Object.keys(data[u]).length, 9); assert.equal(data[u].crystal, 'HT3'); assert.equal(data[u].sword, 'LT2'); assert.equal(data[u].axe, 'HT2'); assert.deepEqual(data[v], {});
  for (const secret of ['private-name', 'private-server', 'cooldown', '"42"', 'reason', 'discord']) assert.ok(!JSON.stringify(data).includes(secret));
  assert.equal(Object.hasOwn(root.servers['2'], 'modes'), true);
});
test('restrictions isolate servers and expire without snapshot refresh', () => {
  const root = fixture(); root.servers['2'].restrictions = [{playerId: '42', uuid: u, until: 2000, reason: 'secret'}];
  const data = project(root, sources);
  assert.deepEqual(lookup(data, [u], 1000)[u], {crystal: 'HT3'});
  assert.equal(Object.keys(lookup(data, [u], 2000)[u]).length, 9);
  root.restrictions = [{playerId: '42', uuid: u, until: null}];
  assert.equal(lookup(project(root, sources), [u], 3000)[u].crystal, undefined);
});
test('unlink hides only that guild and ignores stale mode identity copies', () => {
  const root = fixture(); root.servers['2'].players['42'].uuid = null;
  assert.deepEqual(lookup(project(root, sources), [u])[u], {crystal: 'HT3'});
  root.players['42'].uuid = null; assert.deepEqual(lookup(project(root, sources), [u])[u], {});
});
test('identity collisions fail closed; departed players retain saved ranks', () => {
  const root = fixture(); root.departures = ['42']; assert.equal(lookup(project(root, sources), [u])[u].crystal, 'HT3');
  root.players['43'] = {...root.players['42'], id: '43'}; assert.deepEqual(lookup(project(root, sources), [u])[u], {});
  delete root.players['43']; root.servers['2'].players['42'].uuid = v;
  assert.deepEqual(lookup(project(root, sources), [u, v]), {[u]: {}, [v]: {}});
});
test('unknown tiers and modes are never public', () => {
  const root = fixture(); root.players['42'].tier = 'Unranked'; root.servers['2'].modes.axe.players['42'].tier = 'INVALID';
  const data = lookup(project(root, sources), [u])[u]; assert.equal(data.crystal, undefined); assert.equal(data.axe, undefined);
});
test('snapshot reloads only changed revisions and recovers after DB replacement or failure', t => {
  const dir = mkdtempSync(join(tmpdir(), 'lunar-api-')); t.after(() => rmSync(dir, {recursive: true, force: true}));
  const file = join(dir, 'state.sqlite');
  function write(file, state) {const db = new DatabaseSync(file); db.exec('CREATE TABLE crystal_state(guild TEXT PRIMARY KEY,revision INTEGER,body TEXT)'); db.prepare('INSERT INTO crystal_state VALUES(?,0,?)').run('1', JSON.stringify(state)); db.close();}
  write(file, fixture()); let now = 1000;
  const reader = new SnapshotReader(file, '1', sources, () => now); reader.fileIdentity = () => String(statSync(file, {bigint: true}).ino);
  reader.refresh(); reader.refresh(); assert.equal(reader.reloads, 1);
  const db = new DatabaseSync(file); const root = fixture(); root.players['42'].tier = 'HT1'; db.prepare('UPDATE crystal_state SET revision=1,body=?').run(JSON.stringify(root)); db.close();
  reader.refresh(); assert.equal(reader.reloads, 2); assert.equal(lookup(reader.data, [u])[u].crystal, 'HT1');
  const replacement = join(dir, 'replacement.sqlite'); write(replacement, fixture()); renameSync(file, join(dir, 'old.sqlite')); renameSync(replacement, file);
  reader.refresh(); assert.equal(reader.reloads, 3); assert.equal(lookup(reader.data, [u])[u].crystal, 'HT3');
  renameSync(file, join(dir, 'gone.sqlite')); assert.throws(() => reader.refresh()); now += 15000; assert.equal(reader.ready(), false);
  renameSync(join(dir, 'gone.sqlite'), file); reader.refresh(); assert.equal(reader.ready(), true);
});
async function running(t, options = {}) {
  const reader = {data: project(fixture(), sources), ready: () => true}; const server = createRankServer(reader, options);
  server.listen(0, '127.0.0.1'); await new Promise(resolve => server.once('listening', resolve));
  t.after(() => {server.closeAllConnections(); server.close();});
  return {reader, server, url: `http://127.0.0.1:${server.address().port}`, post: body => fetch(`http://127.0.0.1:${server.address().port}/v1/ranks/lookup`, {method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify(body)})};
}
test('HTTP validates UUID batches and reports source failure safely', async t => {
  const {post, reader, url} = await running(t);
  let response = await post({uuids: [u, v]}); assert.equal(response.status, 200);
  const result = await response.json(); assert.equal(result.schemaVersion, 1); assert.equal(result.cacheSeconds, 60); assert.equal(result.players[u].sword, 'LT2');
  for (const body of [{uuids: []}, {uuids: Array(101).fill(u)}, {uuids: ['oops']}, {uuids: [u], guild: '2'}, null]) assert.equal((await post(body)).status, 400);
  assert.equal((await post({uuids: Array(100).fill(u)})).status, 200);
  reader.ready = () => false; response = await post({uuids: [u]}); assert.equal(response.status, 503); assert.deepEqual(await response.json(), {error: 'ranks_unavailable'});
  assert.equal((await fetch(url + '/health')).status, 503);
});
test('HTTP rejects oversized bodies, wrong methods and content types', async t => {
  const {url} = await running(t);
  assert.equal((await fetch(url + '/v1/ranks/lookup')).status, 405);
  assert.equal((await fetch(url + '/unknown')).status, 404);
  assert.equal((await fetch(url + '/v1/ranks/lookup', {method: 'POST', body: '{}'})).status, 415);
  assert.equal((await fetch(url + '/v1/ranks/lookup', {method: 'POST', headers: {'content-type': 'application/json'}, body: ' '.repeat(9000)})).status, 413);
});
test('rate limit cannot be bypassed by spoofing forwarded headers', async t => {
  const {url} = await running(t, {requestsPerMinute: 2});
  for (let i = 0; i < 3; i++) {
    const response = await fetch(url + '/health', {headers: {'x-forwarded-for': `1.2.3.${i}`}});
    assert.equal(response.status, i < 2 ? 200 : 429); if (i === 2) assert.ok(response.headers.has('retry-after'));
  }
});
test('concurrent reads do not mutate snapshots', async t => {
  const {post} = await running(t, {requestsPerMinute: 1000});
  const responses = await Promise.all(Array.from({length: 24}, () => post({uuids: [u]})));
  for (const response of responses) {assert.equal(response.status, 200); assert.equal(Object.keys((await response.json()).players[u]).length, 9);}
});
test('full IP tracking capacity recovers after old windows expire', async t => {
  let now = 1000;
  const {url} = await running(t, {clock: () => now, trustProxy: true, maxTrackedIps: 2});
  const request = ip => fetch(url + '/health', {headers: {'x-forwarded-for': ip}});
  assert.equal((await request('1.2.3.1')).status, 200); assert.equal((await request('1.2.3.2')).status, 200);
  assert.equal((await request('1.2.3.3')).status, 429);
  now += 60_000; assert.equal((await request('1.2.3.3')).status, 200);
});
