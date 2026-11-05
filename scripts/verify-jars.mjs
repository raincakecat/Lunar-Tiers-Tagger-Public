import { readdirSync, readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';

const expected = new Set(['1.21.11', '26.2', '26.3']);
for (const file of readdirSync('build/release').filter(name => name.endsWith('.jar'))) {
  const path = `build/release/${file}`, listing = execFileSync('jar', ['tf', path], {encoding: 'utf8'}).split(/\r?\n/);
  for (const item of ['fabric.mod.json', 'lunartiers.mixins.json', 'dev/lunartiers/tagger/LunarTagger.class', 'dev/lunartiers/tagger/mixin/TabNameMixin.class', 'dev/lunartiers/tagger/mixin/PlayerNameMixin.class', 'assets/lunartiers/font/modes.json']) assert.ok(listing.includes(item), `${file}: missing ${item}`);
  assert.ok(!listing.some(name => /LunarClientTest|config\.local|\.sqlite|\.env|test-keystore/.test(name)), 'Private/test artifact in JAR');
  const bytes = readFileSync(path); assert.ok(!bytes.includes(Buffer.from('SUPABASE_SERVICE_ROLE_KEY')), 'Credential configuration in mod');
  for (const item of ['dev/lunartiers/tagger/ProfileScreen.class', 'dev/lunartiers/tagger/BaseProfileScreen.class', 'assets/lunartiers/lang/en_us.json']) assert.ok(listing.includes(item), `${file}: missing profile/controls resource ${item}`);
  const version = [...expected].find(version => file.includes(`-${version}-`)); assert.ok(version, `Unexpected artifact ${file}`);
  const dir = mkdtempSync(join(tmpdir(), 'lunar-jar-'));
  try {
    execFileSync('jar', ['xf', resolve(path), 'fabric.mod.json', 'lunartiers.mixins.json', 'assets/lunartiers/font/modes.json', 'lunar-endpoint.properties'], {cwd: dir});
    const mod = JSON.parse(readFileSync(join(dir, 'fabric.mod.json'), 'utf8'));
    assert.equal(mod.id, 'lunartiers_tagger'); assert.equal(mod.depends.minecraft, version); assert.equal(mod.environment, 'client');
    assert.equal(mod.depends.java, version === '1.21.11' ? '>=21' : '>=25');
    const mixins = JSON.parse(readFileSync(join(dir, 'lunartiers.mixins.json'), 'utf8'));
    assert.deepEqual(mixins.client, ['PlayerNameMixin', 'TabNameMixin']); assert.equal(mixins.injectors.defaultRequire, 1);
    const font = JSON.parse(readFileSync(join(dir, 'assets/lunartiers/font/modes.json'), 'utf8'));
    assert.equal(font.providers.length, 9);
    for (const provider of font.providers) assert.ok(listing.includes(`assets/${provider.file.replace(':', '/textures/')}`));
    const endpoint = readFileSync(join(dir, 'lunar-endpoint.properties'), 'utf8').trim().slice(4);
    if (process.env.LUNAR_EXPECTED_API_URL) assert.equal(endpoint, process.env.LUNAR_EXPECTED_API_URL, `${file}: incorrect embedded API endpoint`);
    if (endpoint) {const uri = new URL(endpoint); assert.equal(uri.protocol, 'https:'); assert.equal(uri.username + uri.password + uri.search + uri.hash, ''); assert.equal(uri.pathname, '/v1/ranks/lookup');}
  } finally {rmSync(dir, {recursive: true, force: true});}
  expected.delete(version);
  console.log(`Verified ${file}`);
}
assert.equal(expected.size, 0, 'Missing version JARs');
