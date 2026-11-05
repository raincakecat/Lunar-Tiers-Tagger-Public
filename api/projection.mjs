export const MODES = Object.freeze(['crystal', 'sword', 'axe', 'smp', 'pot', 'mace', 'uhc', 'nethpot', 'spear-mace']);
export const TIERS = Object.freeze(['HT1', 'LT1', 'HT2', 'LT2', 'HT3', 'LT3', 'HT4', 'LT4', 'HT5', 'LT5']);
export function uuid(value) {
  if (typeof value !== 'string' || !/^(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i.test(value)) return null;
  return value.replaceAll('-', '').toLowerCase();
}
/** Only explicitly configured guild/mode pairs can become public. No names or Discord IDs are serialized. */
export function project(root, sources) {
  if (root?.version !== 1 || !root.players || !root.guildId) throw new Error('Unsupported saved state');
  const entries = new Map(), owners = new Map(), identities = new Map(), conflicts = new Set();
  const guilds = [root, ...Object.values(root.servers ?? {})];
  // Fail closed on corrupt shared identity ownership, even in an unpublished server.
  for (const guild of guilds) for (const [id, profile] of Object.entries(guild.players ?? {})) {
    const key = uuid(profile.uuid); if (!key) continue;
    if (owners.has(key) && owners.get(key) !== id) conflicts.add(key);
    owners.set(key, id);
    const old = identities.get(id);
    if (old && old !== key) { conflicts.add(key); conflicts.add(old); }
    identities.set(id, key);
  }
  for (const source of sources) {
    const guild = source.guildId === root.guildId ? root : root.servers?.[source.guildId];
    if (!guild) throw new Error('Configured rank source is missing');
    for (const [id, profile] of Object.entries(guild.players ?? {})) {
      const key = uuid(profile.uuid); if (!key || conflicts.has(key)) continue;
      const restrictions = (guild.restrictions ?? []).filter(r => r.playerId === id || uuid(r.uuid) === key);
      const hiddenUntil = restrictions.some(r => r.until === null) ? Infinity : Math.max(0, ...restrictions.map(r => Number.isFinite(r.until) ? r.until : Infinity));
      const ranks = [...(entries.get(key) ?? [])];
      for (const mode of source.modes) {
        const part = mode === (guild.defaultModeId ?? 'crystal') ? guild : guild.modes?.[mode];
        const tier = part?.players?.[id]?.tier;
        if (TIERS.includes(tier)) ranks.push(Object.freeze({mode, tier, hiddenUntil}));
      }
      entries.set(key, Object.freeze(ranks));
    }
  }
  return entries;
}
export function lookup(projection, uuids, now = Date.now()) {
  return Object.fromEntries(uuids.map(key => [key, Object.fromEntries((projection.get(key) ?? [])
    .filter(rank => rank.hiddenUntil <= now).map(rank => [rank.mode, rank.tier]))]));
}
