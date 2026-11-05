import { DatabaseSync } from 'node:sqlite';
import { project } from './projection.mjs';

export class SnapshotReader {
  constructor(file, rootGuildId, sources, clock = Date.now) {
    this.file = file; this.rootGuildId = rootGuildId; this.sources = sources; this.clock = clock;
    this.revision = null; this.data = new Map(); this.checkedAt = null; this.reloads = 0;
  }
  refresh() {
    // Reopen briefly so atomic database restoration/replacement is detected too.
    const stamp = this.stamp();
    const db = new DatabaseSync(this.file, {readOnly: true});
    try {
      db.exec('PRAGMA query_only=ON; PRAGMA busy_timeout=100;');
      const row = db.prepare('SELECT revision FROM crystal_state WHERE guild=?').get(this.rootGuildId);
      if (!row) throw new Error('Root state unavailable');
      // data_version alone cannot detect file replacements between connections. Compare revision and file metadata externally.
      if (this.revision !== Number(row.revision) || this.fileStamp !== stamp) {
        const saved = db.prepare('SELECT revision,body FROM crystal_state WHERE guild=?').get(this.rootGuildId);
        const next = project(JSON.parse(saved.body), this.sources);
        if (stamp !== this.stamp()) throw new Error('Database changed during refresh');
        this.data = next; this.revision = Number(saved.revision); this.fileStamp = stamp; this.reloads++;
      }
      this.checkedAt = this.clock();
    } finally { db.close(); }
  }
  stamp() { return this.fileIdentity?.() ?? ''; }
  ready() { return this.checkedAt !== null && this.clock() - this.checkedAt < 15_000; }
}
