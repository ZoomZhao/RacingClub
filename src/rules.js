export const COLUMNS = 5;
export const ROWS = 4;
export const RUN_MS_PER_STEP = 1350;
export const AUTO_RUN_DELAY = 250;
export const JUMP_WINDOW = .2;
export const TURN_MODES = ['BOTH', 'CLOCKWISE', 'COUNTERCLOCKWISE'];

const key = (x, y) => `${x},${y}`;

export function createRules(data) {
  const pieces = new Map(data.pieces.map(piece => [piece.id, {
    ...piece,
    paths: (piece.paths?.length ? piece.paths : [{ from: 0, to: 1, jump: piece.id, samples: piece.samples, jumpAt: .5, length: 1.5 }])
      .map(path => ({ ...path, jumpAt: path.jump === null ? null : (path.jumpAt ?? .5) })),
  }]));
  const gates = new Map(Object.entries(data.gates));
  const levels = data.levels.map(level => {
    const start = level.start ?? '', finish = level.finish ?? '';
    return {
      ...level, start, finish,
      solutionStart: level.solutionStart ?? start, solutionFinish: level.solutionFinish ?? finish,
      requiredPieces: [...new Set(level.sequence)],
      solveEndpoints: !start && !finish,
      number: String(level.id).padStart(2, '0'),
    };
  });
  const difficulties = data.difficulties ?? [];
  const inventory = data.inventory;

  const rotate = ([x, y], r) => r === 1 ? [1 - y, x] : r === 2 ? [2 - x, 1 - y] : r === 3 ? [y, 2 - x] : [x, y];
  const dimensions = r => r % 2 === 0 ? [2, 1] : [1, 2];
  const ports = p => pieces.get(p.id).ports.map(pt => { const [x, y] = rotate(pt, p.r); return [x + p.x, y + p.y]; });
  const fits = p => {
    if (!p || !pieces.has(p.id) || !Number.isInteger(p.r) || p.r < 0 || p.r > 3 || !Number.isInteger(p.x) || !Number.isInteger(p.y)) return false;
    const [w, h] = dimensions(p.r);
    return p.x >= 0 && p.y >= 0 && p.x + w <= COLUMNS && p.y + h <= ROWS;
  };
  const overlaps = (p, o) => {
    const [w, h] = dimensions(p.r), [ow, oh] = dimensions(o.r);
    return p.id !== o.id && p.x < o.x + ow && o.x < p.x + w && p.y < o.y + oh && o.y < p.y + h;
  };
  const hasOverlap = all => all.some((p, i) => all.slice(i + 1).some(o => overlaps(p, o)));

  const originForCorner = (p, sign, [ox, oy]) => ({
    id: p.id,
    x: sign >= 0 ? p.x + oy : p.x - oy,
    y: sign >= 0 ? p.y - ox : p.y + ox,
    r: (p.r + sign + 4) % 4,
  });
  const inside = p => { const [w, h] = dimensions(p.r); return p.x >= 0 && p.x <= COLUMNS - w && p.y >= 0 && p.y <= ROWS - h; };
  const quarterAboutCorner = (p, sign) => {
    const [w, h] = dimensions(p.r);
    const corners = [[0, 0], [w, 0], [0, h], [w, h]];
    for (const corner of corners) { const next = originForCorner(p, sign, corner); if (inside(next)) return next; }
    const next = originForCorner(p, sign, corners[0]);
    const [nw, nh] = dimensions(next.r);
    return { ...next, x: Math.min(Math.max(next.x, 0), COLUMNS - nw), y: Math.min(Math.max(next.y, 0), ROWS - nh) };
  };
  const turnedInside = (p, quarterTurns) => {
    const sign = quarterTurns >= 0 ? 1 : -1;
    let current = p;
    for (let turn = 0; turn < Math.abs(quarterTurns) % 4; turn++) current = quarterAboutCorner(current, sign);
    return current;
  };
  const initialPlacement = p => pieces.has(p.id) && p.r >= 0 && p.r <= 3 && p.x >= 0 && p.x <= 4 && p.y >= 0 && p.y <= 3 && !fits(p) ? turnedInside(p, 1) : p;
  const hit = (all, x, y) => {
    for (let i = all.length - 1; i >= 0; i--) {
      const p = all[i], [w, h] = dimensions(p.r);
      if (x >= p.x && x < p.x + w && y >= p.y && y < p.y + h) return p;
    }
    return null;
  };
  const coveredBy = (p, all) => { const i = all.findIndex(o => o.id === p.id); return i < 0 ? [] : all.slice(i + 1).filter(o => overlaps(p, o)); };
  const beneath = (p, all) => { const i = all.findIndex(o => o.id === p.id); return (i < 0 ? all : all.slice(0, i)).filter(o => overlaps(p, o)); };
  const stackMarker = (p, all) => {
    const [w, h] = dimensions(p.r);
    for (let y = p.y; y < p.y + h; y++) for (let x = p.x + w - 1; x >= p.x; x--) if (hit(all, x, y)?.id === p.id) return [x + .92, y + .82];
    return null;
  };
  const gateAt = ([x, y]) => { for (const [name, [gx, gy]] of gates) if (gx === x && gy === y) return name; return null; };

  function trace(level, all, start = '', finish = '') {
    if (all.length !== level.requiredPieces.length || new Set(all.map(p => p.id)).size !== all.length
      || all.some(p => !level.sequence.includes(p.id) || !fits(p)) || hasOverlap(all)) return null;
    const startGate = level.start || start || '', finishGate = level.finish || finish || '';
    const starts = startGate ? [startGate] : [...gates.keys()];
    const byPoint = new Map();
    for (const p of all) ports(p).forEach((pt, i) => {
      const k = key(...pt);
      if (!byPoint.has(k)) byPoint.set(k, []);
      byPoint.get(k).push([p, i]);
    });
    for (const gate of starts) {
      const point = gates.get(gate);
      if (!point) continue;
      const entries = byPoint.get(key(...point));
      if (!entries || entries.length !== 1) continue;
      const seen = new Set();
      const visit = (p, from, index) => {
        const state = `${p.id}:${from}:${index}`;
        if (seen.has(state)) return null;
        seen.add(state);
        const piece = pieces.get(p.id);
        for (const [pathIndex, path] of piece.paths.entries()) {
          if (path.from !== from && path.to !== from) continue;
          if (path.jump !== null && (index >= level.sequence.length || path.jump !== level.sequence[index])) continue;
          const nextIndex = index + (path.jump === null ? 0 : 1);
          const to = path.from === from ? path.to : path.from;
          const step = { p, from, to, pathIndex, jump: path.jump };
          const end = ports(p)[to];
          const reached = gateAt(end);
          if (reached !== null) {
            if (nextIndex === level.sequence.length && (!finishGate || reached === finishGate)) return [step];
          } else {
            const candidates = (byPoint.get(key(...end)) ?? []).filter(([o]) => o.id !== p.id);
            if (candidates.length !== 1) continue;
            const rest = visit(candidates[0][0], candidates[0][1], nextIndex);
            if (rest) return [step, ...rest];
          }
        }
        return null;
      };
      const route = visit(entries[0][0], entries[0][1], 0);
      if (route) return route;
    }
    return null;
  }
  const road = step => pieces.get(step.p.id).paths[step.pathIndex];
  function position(step, progress) {
    const path = road(step), samples = path.samples;
    const t = Math.min(Math.max(step.from === path.from ? progress : 1 - progress, 0), 1) * (samples.length - 1);
    const i = Math.min(Math.floor(t), samples.length - 2), f = t - i, a = samples[i], b = samples[i + 1];
    const [x, y] = rotate([a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f], step.p.r);
    return [step.p.x + x, step.p.y + y];
  }
  function endpoint(route, finish) {
    const step = finish ? route.at(-1) : route[0];
    return step ? gateAt(ports(step.p)[finish ? step.to : step.from]) : null;
  }
  return { pieces, gates, levels, difficulties, inventory, rotate, dimensions, ports, fits, overlaps, hasOverlap, turnedInside, initialPlacement, hit, coveredBy, beneath, stackMarker, trace, road, position, endpoint, gateAt };
}

export const clock = ms => {
  const value = Math.max(0, Math.floor(ms));
  const pad = n => String(n).padStart(2, '0');
  return `${pad(Math.floor(value / 60000))}:${pad(Math.floor(value / 1000) % 60)}.${pad(Math.floor(value % 1000 / 10))}`;
};

export const stars = (hints, pieceCount) => hints === 0 ? 3 : hints * 2 <= pieceCount ? 2 : 1;

export function stackLevels(rules, all) {
  const levels = new Map();
  all.forEach((p, i) => {
    let level = 0;
    for (const o of all.slice(0, i)) if (rules.overlaps(p, o)) level = Math.max(level, levels.get(o.id) + 1);
    levels.set(p.id, level);
  });
  return levels;
}

export function crossingFeedback(rules, route, progress) {
  const cursor = Math.min(Math.max(progress, 0), 1) * route.length;
  let crossed = 0, lastCrossing = null;
  route.forEach((step, i) => {
    if (step.jump === null) return;
    const path = rules.road(step), at = path.jumpAt ?? .5;
    const crossing = i + (step.from === path.from ? at : 1 - at);
    if (cursor >= crossing) { crossed++; lastCrossing = crossing; }
  });
  const pulse = lastCrossing === null ? 0 : Math.max(0, Math.sin(Math.PI * Math.min(Math.max((cursor - lastCrossing) * RUN_MS_PER_STEP / 600, 0), 1)));
  return { crossed, pulse };
}

export function horsePose(rules, route, progress) {
  if (!route.length) return null;
  const end = route.length - .0001;
  const onRoute = at => { const t = Math.min(Math.max(at, 0), end); return rules.position(route[Math.floor(t)], t % 1); };
  const cursor = Math.min(Math.max(progress * route.length, 0), end);
  const step = route[Math.floor(cursor)];
  const at = rules.position(step, cursor % 1);
  const before = onRoute(cursor - .025), after = onRoute(cursor + .025);
  const length = Math.max(Math.hypot(after[0] - before[0], after[1] - before[1]), .0001);
  const direction = [(after[0] - before[0]) / length, (after[1] - before[1]) / length];
  const path = rules.road(step), along = step.from === path.from ? cursor % 1 : 1 - cursor % 1;
  const jumpAt = path.jumpAt ?? .5;
  const jump = step.jump !== null ? Math.sin(Math.PI * Math.max(0, 1 - Math.abs(along - jumpAt) * 8)) : 0;
  const fromRail = along - jumpAt;
  const jumpPhase = step.jump !== null && Math.abs(fromRail) < JUMP_WINDOW ? (fromRail + JUMP_WINDOW) / (2 * JUMP_WINDOW) : null;
  return { at, direction, jump, stride: (cursor * 3) % 1, jumpPhase };
}

export function entryCenter([x, y]) {
  if (y === 0) return [.35 + x, .18];
  if (y === 4) return [.35 + x, 4.5];
  if (x === 0) return [.18, .35 + y];
  return [5.5, .35 + y];
}
export function entryOutward([x, y]) {
  if (y === 0) return [0, -1];
  if (y === 4) return [0, 1];
  if (x === 0) return [-1, 0];
  return [1, 0];
}
export const onEntryLetter = (rules, x, y) => [...rules.gates.values()].some(pt => { const [cx, cy] = entryCenter(pt); return Math.abs(x - cx) <= .35 && Math.abs(y - cy) <= .35; });

export function fenceLayout(rules) {
  const rails = [], posts = [];
  const edges = [[[.46, .18], [5.22, .18]], [[.46, 4.5], [5.22, 4.5]], [[.18, .46], [.18, 4.24]], [[5.5, .46], [5.5, 4.24]]];
  for (const [a, b] of edges) {
    const horizontal = a[1] === b[1];
    const openings = [...rules.gates.values()].filter(([px, py]) => horizontal ? py === (a[1] < 1 ? 0 : 4) : px === (a[0] < 1 ? 0 : 5))
      .map(([px, py]) => horizontal ? .35 + px : .35 + py).sort((m, n) => m - n);
    const lo = horizontal ? a[0] : a[1], hi = horizontal ? b[0] : b[1];
    const at = position => horizontal ? [position, a[1]] : [a[0], position];
    const rail = (from, to) => { if (to > from) rails.push([at(from), at(to)]); };
    let cursor = lo;
    for (const center of openings) { rail(cursor, Math.max(lo, center - .4)); cursor = Math.min(hi, center + .4); }
    rail(cursor, hi);
    for (const center of openings) { posts.push(at(Math.max(lo, center - .4))); posts.push(at(Math.min(hi, center + .4))); }
    const count = horizontal ? 7 : 5;
    for (let i = 0; i <= count; i++) {
      const position = lo + (hi - lo) * i / count;
      if (openings.every(center => Math.abs(position - center) >= .58)) posts.push(at(position));
    }
  }
  return { rails, posts };
}

export function roadNumberLayout(rules, p, avoid = [], minimumFont = 0, available = null) {
  const piece = rules.pieces.get(p.id), [w, h] = rules.dimensions(p.r);
  const font = Math.max(.22, minimumFont);
  const width = Math.max(p.id === 10 ? .33 : .30, font * (p.id === 10 ? 1.4 : 1.05));
  const height = Math.max(.28, font * 1.3);
  const bounds = available ?? { left: .04, top: .04, right: w - .04, bottom: h - .04 };
  const [cx, cy] = rules.rotate([piece.gate[0] / 100, piece.gate[1] / 100], p.r);
  const angle = (piece.gate[2] + p.r * 90) * Math.PI / 180;
  const normal = [Math.cos(angle), Math.sin(angle)], tangent = [-normal[1], normal[0]];
  const points = jumping => piece.paths.filter(road => (road.jump === p.id) === jumping)
    .flatMap(road => road.samples.filter((_, i) => i % 4 === 0).map(pt => rules.rotate(pt, p.r)));
  const own = points(true), other = points(false);
  const distanceSquared = (q, list) => list.length ? Math.min(...list.map(([x, y]) => (x - q[0]) ** 2 + (y - q[1]) ** 2)) : Number.MAX_VALUE;
  const label = q => ({ center: q, width, height, font, bounds: { left: q[0] - width / 2, top: q[1] - height / 2, right: q[0] + width / 2, bottom: q[1] + height / 2 } });
  const intersects = (m, n) => m.left < n.right && n.left < m.right && m.top < n.bottom && n.top < m.bottom;
  const valid = q => {
    const box = label(q).bounds;
    if (box.left < bounds.left - .0001 || box.top < bounds.top - .0001 || box.right > bounds.right + .0001 || box.bottom > bounds.bottom + .0001) return false;
    const grown = { left: box.left - .01, top: box.top - .01, right: box.right + .01, bottom: box.bottom + .01 };
    if (avoid.some(rect => intersects(grown, rect))) return false;
    const separate = [[1, 0], [0, 1], normal, tangent].some(([ax, ay]) => {
      const span = width / 2 * Math.abs(ax) + height / 2 * Math.abs(ay) + .13 * Math.abs(ax * normal[0] + ay * normal[1]) + .375 * Math.abs(ax * tangent[0] + ay * tangent[1]);
      return Math.abs((q[0] - cx) * ax + (q[1] - cy) * ay) + .0001 >= span;
    });
    if (!separate) return false;
    const otherDistance = distanceSquared(q, other);
    return otherDistance >= .28 ** 2 || Math.sqrt(otherDistance) + .02 >= Math.sqrt(distanceSquared(q, own));
  };
  const score = q => (q[0] - cx) ** 2 + (q[1] - cy) ** 2 + .1 * distanceSquared(q, own) + .001 * (q[0] + q[1]);
  let best = null, bestScore = Infinity;
  if (bounds.right - bounds.left >= width && bounds.bottom - bounds.top >= height) {
    for (const [axis, half] of [[normal, .105], [tangent, .35]]) {
      const cross = [-axis[1], axis[0]], projection = width / 2 * Math.abs(axis[0]) + height / 2 * Math.abs(axis[1]);
      for (const sign of [-1, 1]) for (const extra of [0, .06, .12, .24, .32, .40]) for (const shift of [0, -.08, .08, -.16, .16, -.24, .24, -.32, .32]) {
        const d = (half + projection + .025 + extra) * sign;
        const q = [
          Math.min(Math.max(cx + axis[0] * d + cross[0] * shift, bounds.left + width / 2), bounds.right - width / 2),
          Math.min(Math.max(cy + axis[1] * d + cross[1] * shift, bounds.top + height / 2), bounds.bottom - height / 2),
        ];
        if (valid(q)) { const s = score(q); if (s < bestScore) { bestScore = s; best = q; } }
      }
    }
  }
  if (best) return label(best);
  if (avoid.length) return roadNumberLayout(rules, p, [], minimumFont, available);
  return label([cx, cy]);
}

const NUMBER_FACES = { 1: 0xaa346a, 2: 0xe5bd47, 3: 0xa7ba54, 4: 0xae362e, 5: 0x245f91, 6: 0x236648, 7: 0xe09b59, 8: 0x75418e, 9: 0x75452b, 10: 0x1b6278 };
const hex6 = rgb => '#' + rgb.toString(16).padStart(6, '0');
function luminance(rgb) {
  const channel = shift => { const v = ((rgb >> shift) & 255) / 255; return v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4; };
  return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0);
}
export const contrast = (a, b) => { const x = luminance(a), y = luminance(b); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05); };
export const numberFace = id => hex6(NUMBER_FACES[id] ?? 0x254c3b);
export const numberInk = id => { const lum = luminance(NUMBER_FACES[id] ?? 0x254c3b); return (lum + .05) / .05 > 1.05 / (lum + .05) ? '#000000' : '#ffffff'; };
export function readableInk(color, ...backgrounds) {
  const ink = parseInt(color.slice(1), 16);
  let mixed = ink;
  for (let t = 0; backgrounds.some(bg => contrast(mixed, bg) < 4.5) && t < 1;) {
    t += .02;
    const channel = shift => Math.round(((ink >> shift) & 255) * (1 - t));
    mixed = (channel(16) << 16) | (channel(8) << 8) | channel(0);
  }
  return hex6(mixed);
}

export function courseRows(nodeCount, width, minBadge = 22) {
  const columns = Math.min(Math.max(Math.floor(width / (minBadge + 8)), 2), 10);
  const rowCount = Math.ceil(nodeCount / columns);
  let index = 0;
  return Array.from({ length: rowCount }, (_, row) => {
    const count = Math.floor(nodeCount / rowCount) + (row < nodeCount % rowCount ? 1 : 0);
    const indices = Array.from({ length: count }, (_, i) => index + i);
    index += count;
    return indices;
  });
}

const isCount = value => Number.isInteger(value) && value >= 0;
const isPlacement = p => p && typeof p === 'object' && [p.id, p.x, p.y, p.r].every(Number.isInteger);

export function createStore(backing, storageKey = 'racingclub.web.v1') {
  let data = { progress: {}, completed: {}, scores: {}, turnMode: 'BOTH' };
  try {
    const parsed = JSON.parse(backing?.getItem(storageKey) ?? 'null');
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      for (const name of ['progress', 'completed', 'scores']) if (parsed[name] && typeof parsed[name] === 'object' && !Array.isArray(parsed[name])) data[name] = parsed[name];
      if (TURN_MODES.includes(parsed.turnMode)) data.turnMode = parsed.turnMode;
    }
  } catch { }
  for (const [level, list] of Object.entries(data.scores)) {
    data.scores[level] = Array.isArray(list) ? list.filter(s => s && typeof s === 'object' && Number(s.level) === Number(level) && isCount(s.millis) && isCount(s.moves) && isCount(s.hints)) : [];
  }
  const persist = () => { try { backing?.setItem(storageKey, JSON.stringify(data)); } catch { } };
  return {
    load(level) {
      const saved = data.progress[level];
      if (!saved || typeof saved !== 'object') return null;
      return {
        placed: Array.isArray(saved.placed) ? saved.placed.filter(isPlacement) : [],
        moves: isCount(saved.moves) ? saved.moves : 0, hints: isCount(saved.hints) ? saved.hints : 0,
        elapsed: isCount(saved.elapsed) ? saved.elapsed : 0,
        start: typeof saved.start === 'string' ? saved.start : '', finish: typeof saved.finish === 'string' ? saved.finish : '',
        finished: saved.finished === true,
      };
    },
    save(level, progress) { data.progress[level] = progress; persist(); },
    completed: level => data.completed[level] === true,
    markCompleted(level) { data.completed[level] = true; persist(); },
    clearAll() { data.progress = {}; data.completed = {}; persist(); },
    scores: level => [...(data.scores[level] ?? [])].sort((a, b) => a.millis - b.millis || a.moves - b.moves || a.timestamp - b.timestamp),
    saveScore(score) { (data.scores[score.level] ??= []).push(score); persist(); },
    get turnMode() { return data.turnMode; },
    set turnMode(mode) { if (TURN_MODES.includes(mode)) { data.turnMode = mode; persist(); } },
  };
}

export class GameSession {
  constructor(rules, store, now = () => performance.now(), wallClock = () => Date.now()) {
    this.rules = rules; this.store = store; this.now = now; this.wallClock = wallClock;
    this.level = rules.levels[0]; this.screen = 'home'; this.placed = []; this.selected = null; this.rotation = 0;
    this.moves = 0; this.hints = 0; this.message = ''; this.running = false; this.dialog = ''; this.elapsed = 0;
    this.started = null; this.history = []; this.activeRoute = []; this.chosenStart = ''; this.chosenFinish = '';
    this.selectedDifficultyId = rules.difficulties[0]?.id ?? ''; this.runProgress = 0; this.foreground = true;
    this.listeners = new Set();
  }
  get turnMode() { return this.store.turnMode; }
  subscribe(listener) { this.listeners.add(listener); return () => this.listeners.delete(listener); }
  changed() { for (const listener of this.listeners) listener(this); }
  difficulty(level = this.level) { return this.rules.difficulties.find(d => d.id === level.difficultyId); }
  get startGate() { return this.level.start || this.chosenStart; }
  get finishGate() { return this.level.finish || this.chosenFinish; }
  solved(id) { return this.store.completed(id) || this.store.scores(id).length > 0; }
  inProgress(id) { const saved = this.store.load(id); return !!saved && !saved.finished && saved.placed.length > 0; }
  best(id) { return this.store.scores(id)[0] ?? null; }
  time() { return this.elapsed + (this.started === null ? 0 : this.now() - this.started); }
  pause() { if (this.started !== null) { this.elapsed += this.now() - this.started; this.started = null; } this.save(); }
  resume() { if (this.foreground && this.screen === 'game' && !this.running && !this.dialog && this.started === null) this.started = this.now(); }
  enterBackground() { this.foreground = false; this.pause(); this.changed(); }
  enterForeground() { this.foreground = true; this.resume(); this.changed(); }
  save() {
    if (this.screen === 'home') return;
    this.store.save(this.level.id, { placed: this.placed, moves: this.moves, hints: this.hints, elapsed: Math.round(this.time()), start: this.chosenStart, finish: this.chosenFinish, finished: this.running || this.screen === 'win' });
  }
  start(level) {
    this.pause();
    Object.assign(this, { level, screen: 'game', running: false, runProgress: 0, dialog: '', placed: [], moves: 0, hints: 0, elapsed: 0, history: [], activeRoute: [], chosenStart: '', chosenFinish: '', selectedDifficultyId: level.difficultyId });
    const saved = this.store.load(level.id);
    if (saved && !saved.finished) {
      for (const p of saved.placed) if (level.sequence.includes(p.id) && this.rules.fits(p)) this.placed = [...this.placed.filter(o => o.id !== p.id), { id: p.id, x: p.x, y: p.y, r: p.r }];
      this.moves = saved.moves; this.hints = saved.hints; this.elapsed = saved.elapsed;
      this.chosenStart = this.rules.gates.has(saved.start) ? saved.start : '';
      this.chosenFinish = this.rules.gates.has(saved.finish) ? saved.finish : '';
    }
    this.selected = level.requiredPieces.find(id => !this.placed.some(p => p.id === id)) ?? null;
    this.rotation = 0; this.message = '';
    this.resume(); this.changed();
  }
  home() { this.pause(); Object.assign(this, { screen: 'home', running: false, runProgress: 0, dialog: '' }); this.changed(); }
  chooseDifficulty(id) { if (this.rules.difficulties.some(d => d.id === id)) { this.selectedDifficultyId = id; this.changed(); } }
  select(id) {
    if (this.running) return;
    const p = this.placed.find(o => o.id === id);
    if (p) {
      this.rotation = p.r;
      if (this.placed.at(-1)?.id !== id) { this.placed = [...this.placed.filter(o => o.id !== id), p]; this.save(); }
    } else if (this.selected !== id) this.rotation = 0;
    this.selected = id; this.message = ''; this.changed();
  }
  clearSelection() { if (this.running) return; this.selected = null; this.rotation = 0; this.changed(); }
  remember() { this.history.push({ placed: this.placed, moves: this.moves, hints: this.hints, start: this.chosenStart, finish: this.chosenFinish }); }
  placePiece(p) {
    if (this.running) return false;
    if (!this.rules.fits(p)) { this.message = '超出棋盘边界，已保留原位'; this.changed(); return false; }
    this.remember();
    this.placed = [...this.placed.filter(o => o.id !== p.id), { id: p.id, x: p.x, y: p.y, r: p.r }];
    this.selected = p.id; this.rotation = p.r; this.moves++; this.message = '';
    this.save(); this.changed(); return true;
  }
  place(x, y) {
    const id = this.selected;
    if (id === null) return;
    const p = { id, x, y, r: this.rotation };
    this.placePiece(this.placed.some(o => o.id === id) ? p : this.rules.initialPlacement(p));
  }
  rotatePiece(id, quarterTurns) {
    if (this.running) return;
    const old = this.placed.find(o => o.id === id);
    if (old) {
      const p = this.rules.turnedInside(old, quarterTurns);
      this.remember();
      this.placed = [...this.placed.filter(o => o.id !== id), p];
      this.selected = id; this.rotation = p.r; this.moves++;
    } else {
      if (this.selected !== id) { this.selected = id; this.rotation = 0; }
      this.rotation = ((this.rotation + quarterTurns) % 4 + 4) % 4;
    }
    this.message = ''; this.save(); this.changed();
  }
  rotate(quarterTurns = 1) { if (this.selected !== null) this.rotatePiece(this.selected, quarterTurns); }
  movePiece(id, dx, dy) {
    const p = this.placed.find(o => o.id === id);
    if (p) this.placePiece({ ...p, x: p.x + dx, y: p.y + dy });
  }
  removePiece(id) {
    if (this.running || !this.placed.some(o => o.id === id)) return;
    this.remember();
    this.placed = this.placed.filter(o => o.id !== id);
    this.selected = id; this.rotation = 0; this.moves++; this.message = '';
    this.save(); this.changed();
  }
  undo() {
    if (this.running || !this.history.length) return;
    const snapshot = this.history.pop();
    this.placed = snapshot.placed; this.moves = snapshot.moves; this.hints = Math.max(this.hints, snapshot.hints);
    this.chosenStart = snapshot.start; this.chosenFinish = snapshot.finish;
    this.rotation = this.placed.find(o => o.id === this.selected)?.r ?? 0; this.message = '';
    this.save(); this.changed();
  }
  reset() {
    if (this.running) return;
    this.remember();
    this.placed = []; this.chosenStart = ''; this.chosenFinish = ''; this.rotation = 0; this.moves++; this.message = '';
    this.selected = this.level.requiredPieces[0] ?? null;
    this.save(); this.changed();
  }
  chooseTurnMode(mode) { if (TURN_MODES.includes(mode) && this.turnMode !== mode) { this.store.turnMode = mode; this.changed(); } }
  clearAllProgress() {
    if (this.running) return;
    this.store.clearAll();
    if (this.screen !== 'home') {
      Object.assign(this, { history: [], placed: [], chosenStart: '', chosenFinish: '', selected: this.level.requiredPieces[0] ?? null, rotation: 0, moves: 0, hints: 0, elapsed: 0, started: null, message: '' });
      if (this.screen === 'win') this.screen = 'game';
      this.save(); this.resume();
    }
    this.changed();
  }
  hint() {
    if (this.running) return;
    const same = (a, b) => a.id === b.id && a.x === b.x && a.y === b.y && a.r === b.r;
    const p = this.level.solution.find(s => !this.placed.some(o => same(o, s)));
    if (!p) {
      if (this.level.solveEndpoints && (this.chosenStart !== this.level.solutionStart || this.chosenFinish !== this.level.solutionFinish)) {
        this.remember();
        this.chosenStart = this.level.solutionStart; this.chosenFinish = this.level.solutionFinish;
        this.moves++; this.hints++; this.message = ''; this.save(); this.changed();
      }
      return;
    }
    this.remember();
    this.placed = [...this.placed.filter(o => o.id !== p.id && !this.rules.overlaps(p, o)), { ...p }];
    this.selected = p.id; this.rotation = p.r; this.moves++; this.hints++; this.message = '';
    this.save(); this.changed();
  }
  chooseEndpoint(letter, finish) {
    if (this.running || !this.level.solveEndpoints || !this.rules.gates.has(letter)) return;
    this.remember();
    if (finish) this.chosenFinish = letter; else this.chosenStart = letter;
    this.moves++; this.message = '';
    this.dialog = ''; this.resume(); this.save(); this.changed();
  }
  trace() {
    if (!this.level.solveEndpoints) return this.rules.trace(this.level, this.placed, this.startGate, this.finishGate);
    const routes = this.endpointRoutes();
    return routes.length === 1 ? routes[0] : null;
  }
  endpointRoutes() {
    if (!this.level.solveEndpoints || this.placed.length !== this.level.requiredPieces.length) return [];
    const gates = [...this.rules.gates.keys()], routes = [];
    for (const start of this.chosenStart ? [this.chosenStart] : gates) for (const finish of this.chosenFinish ? [this.chosenFinish] : gates) {
      const route = this.rules.trace(this.level, this.placed, start, finish);
      if (route && this.rules.endpoint(route, false) === start && this.rules.endpoint(route, true) === finish) routes.push(route);
    }
    return routes;
  }
  canAutoRun() { return this.screen === 'game' && !this.running && !this.dialog && this.foreground; }
  beginRun(route) {
    if (!this.canAutoRun() || !route?.length) return false;
    this.pause();
    this.elapsed = Math.round(this.elapsed);
    this.running = true; this.runProgress = 0; this.activeRoute = route; this.message = ''; this.selected = null;
    if (this.level.solveEndpoints) { this.chosenStart = this.rules.endpoint(route, false) ?? this.chosenStart; this.chosenFinish = this.rules.endpoint(route, true) ?? this.chosenFinish; }
    this.store.saveScore({ level: this.level.id, millis: this.elapsed, moves: this.moves, hints: this.hints, timestamp: this.wallClock() });
    this.store.markCompleted(this.level.id);
    this.save(); this.changed(); return true;
  }
  beginRunIfSolved() { if (!this.canAutoRun()) return false; const route = this.trace(); return !!route && this.beginRun(route); }
  finishRun() { if (!this.running) return; this.running = false; this.runProgress = 1; this.screen = 'win'; this.save(); this.changed(); }
  showDialog(which) { if (this.running && which !== 'help') return; this.pause(); this.dialog = which; this.changed(); }
  closeDialog() { if (!this.dialog) return; this.dialog = ''; this.resume(); this.changed(); }
  coveredCount() { return this.placed.filter(p => this.rules.coveredBy(p, this.placed).length).length; }
  winSummary() {
    const records = this.store.scores(this.level.id);
    const place = records.findIndex(r => r.millis === this.elapsed && r.moves === this.moves) + 1;
    const tag = place === 1 && records.length > 1 ? '新纪录' : records.length <= 1 ? '首次通关' : place > 0 ? `第 ${place} 名` : '';
    return { records, place, tag, stars: stars(this.hints, this.level.requiredPieces.length), next: this.rules.levels.find(l => l.id > this.level.id) ?? null };
  }
}

export const tapTurn = mode => mode === 'COUNTERCLOCKWISE' ? -1 : 1;
export const turnActions = mode => mode === 'CLOCKWISE' ? [1, 0] : mode === 'COUNTERCLOCKWISE' ? [-1, 0] : [-1, 1, 0];

export function pressTile(session, id) {
  const was = session.selected === id && (!session.placed.some(p => p.id === id) || session.placed.at(-1).id === id);
  session.select(id);
  return was;
}
export function releaseBoardTap(session, hit, wasSelected, x, y, onLetter) {
  if (session.running) return;
  if (hit) { if (wasSelected) session.rotatePiece(hit.id, tapTurn(session.turnMode)); return; }
  if (x >= 0 && x <= 4 && y >= 0 && y <= 3) {
    const id = session.selected;
    if (id !== null && !session.placed.some(p => p.id === id)) session.place(x, y); else session.clearSelection();
    return;
  }
  if (!onLetter) session.clearSelection();
}
export function releaseRackTap(session, id, wasSelected) { if (!session.running && wasSelected) session.rotatePiece(id, tapTurn(session.turnMode)); }
export const rackPiece = (session, id) => session.placed.find(p => p.id === id) ?? { id, x: 0, y: 0, r: session.selected === id ? session.rotation : 0 };
export const dropCandidate = (piece, frame, grab) => ({ ...piece, x: Math.round(frame[0] - .35 - grab[0]), y: Math.round(frame[1] - .35 - grab[1]) });
export const overFrame = ([x, y]) => x >= 0 && x <= 5.7 && y >= 0 && y <= 4.7;
