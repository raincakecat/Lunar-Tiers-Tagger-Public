import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync, copyFileSync, readdirSync, existsSync, rmSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve, join } from 'node:path';
import { createHash } from 'node:crypto';

// Source comes from Git's allowlist; ignored host config, database files and build caches cannot enter.
const files = execFileSync('git', ['ls-files', '-z'], {encoding: 'utf8'}).split('\0').filter(Boolean);
if (!files.includes('README.md')) throw new Error('Stage source files before packaging');
const root = resolve('build/package-stage');
if (!root.startsWith(resolve('build') + '/'.replace('/', process.platform === 'win32' ? '\\' : '/'))) throw new Error('Invalid staging path');
if (existsSync(root)) rmSync(root, {recursive: true, force: true});
mkdirSync(root, {recursive: true});
const manifest = {schemaVersion: 1, candidate: true, version: '0.1.0', createdAt: new Date().toISOString(), files: {}};
function add(source, target) {
  if (/(^|\/)(?:\.env|\.git|config\.local\.json|deployment\.local\.json|tunnel\.token)(?:\/|$)|\.sqlite|test-keystore|\.p12$/.test(target)) throw new Error(`Private file rejected: ${target}`);
  const data = readFileSync(source); mkdirSync(dirname(join(root, target)), {recursive: true}); copyFileSync(source, join(root, target));
  manifest.files[target] = {bytes: data.length, sha256: createHash('sha256').update(data).digest('hex')};
}
for (const file of files) add(file, `source/${file}`);
for (const file of readdirSync('build/release').filter(file => file.endsWith('.jar'))) add(`build/release/${file}`, `mods/${file}`);
if (Object.keys(manifest.files).filter(file => file.startsWith('mods/')).length !== 3) throw new Error('Expected three version builds');
add('README.md', 'README.md'); add('docs/DEPLOYMENT.md', 'DEPLOYMENT.md'); add('docs/RELEASE-STATUS.md', 'RELEASE-STATUS.md');
writeFileSync(join(root, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
for (const [file, expected] of Object.entries(manifest.files)) {
  const bytes = readFileSync(join(root, file)); if (bytes.length !== expected.bytes || createHash('sha256').update(bytes).digest('hex') !== expected.sha256) throw new Error(`Integrity mismatch: ${file}`);
}
const zip = resolve('build/Lunar-Tiers-Tagger-0.1.0-candidate.zip');
execFileSync('jar', ['--create', '--file', zip, '--no-manifest', '-C', root, '.']);
const check = mkdtempSync(join(tmpdir(), 'lunar-package-'));
try {
  const entries = execFileSync('jar', ['tf', zip], {encoding: 'utf8'}).trim().split(/\r?\n/);
  if (entries.some(entry => entry.startsWith('/') || entry.includes('..') || entry.includes('\\'))) throw new Error('Unsafe archive paths');
  execFileSync('jar', ['xf', zip], {cwd: check});
  const saved = JSON.parse(readFileSync(join(check, 'manifest.json'), 'utf8'));
  for (const [file, expected] of Object.entries(saved.files)) {
    const data = readFileSync(join(check, file));
    if (data.length !== expected.bytes || createHash('sha256').update(data).digest('hex') !== expected.sha256) throw new Error(`ZIP manifest mismatch: ${file}`);
  }
} finally {rmSync(check, {recursive: true, force: true});}
const sha = createHash('sha256').update(readFileSync(zip)).digest('hex'); writeFileSync(zip + '.sha256', `${sha}  Lunar-Tiers-Tagger-0.1.0-candidate.zip\n`);
console.log(`Packaged ${Object.keys(manifest.files).length} files; verified manifest hashes\n${zip}\nSHA256 ${sha}`);
