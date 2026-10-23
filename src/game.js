export const COLS = 5;
export const ROWS = 4;

// Browser copy of shared/piece-palette.json. Static pages cannot rely on JSON
// module imports in every browser; tests assert both lists stay identical.
export const INVENTORY = [
  { id: 1, color: '#cf4f8a' }, { id: 2, color: '#d9ad32' },
  { id: 3, color: '#8a9d38' }, { id: 4, color: '#c54436' },
  { id: 5, color: '#2d72a8' }, { id: 6, color: '#318663' },
  { id: 7, color: '#d67432' }, { id: 8, color: '#8b5aac' },
  { id: 9, color: '#925c39' }, { id: 10, color: '#24839d' },
];

// Coordinates are in tile units. Every tile occupies two full cells.
export const PIECES = {
  1: { name: '直线路', color: '#cf4f8a', ports: [[0, .5], [2, .5]], path: 'M0 50 H200', gate: [100, 50, 0] },
  2: { name: '弯道路', color: '#d9ad32', ports: [[.5, 0], [1.5, 1]], path: 'M50 0 C50 62 150 38 150 100', gate: [100, 50, -45] },
  3: { name: '反向弯道', color: '#8a9d38', ports: [[.5, 1], [1.5, 0]], path: 'M50 100 C50 38 150 62 150 0', gate: [100, 50, 45] },
  5: { name: '左转路', color: '#2d72a8', ports: [[0, .5], [1.5, 0]], path: 'M0 50 H100 Q150 50 150 0', gate: [55, 50, 0] },
  6: { name: '右转路', color: '#318663', ports: [[0, .5], [1.5, 1]], path: 'M0 50 H100 Q150 50 150 100', gate: [75, 50, 0] },
};

export const GATES = {
  A: [2.5, 0], B: [5, 1.5], C: [4.5, 4],
  D: [1.5, 4], E: [0, 3.5], F: [0, .5],
};

export const LEVELS = [
  {
    id: 3, title: '第一次轻盈转弯', start: 'E', finish: 'F', sequence: [5, 1, 6],
    description: '三块拼板，一次漂亮的回转。',
    solution: [{ id: 5, x: 0, y: 3, r: 0 }, { id: 1, x: 1, y: 1, r: 1 }, { id: 6, x: 0, y: 0, r: 0 }],
  },
  {
    id: 4, title: '多一个弯，也从容', start: 'B', finish: 'A', sequence: [2, 1, 5, 3],
    description: '沿着四道障碍，找到通往终点的路。',
    solution: [{ id: 2, x: 4, y: 1, r: 1 }, { id: 1, x: 2, y: 2, r: 0 }, { id: 5, x: 1, y: 1, r: 1 }, { id: 3, x: 1, y: 0, r: 0 }],
  },
];

export const size = r => r % 2 ? [1, 2] : [2, 1];

export function pathPoint(id,t) {
  const mix=(a,b,t)=>a.map((v,i)=>v*(1-t)+b[i]*t);
  const bez=(ps,t)=>ps.length===1?ps[0]:bez(ps.slice(0,-1).map((p,i)=>mix(p,ps[i+1],t)),t);
  if(id===1)return [2*t,.5];
  if(id===2)return bez([[.5,0],[.5,.62],[1.5,.38],[1.5,1]],t);
  if(id===3)return bez([[.5,1],[.5,.38],[1.5,.62],[1.5,0]],t);
  if(t<.6)return[t/.6,.5];
  return bez([[1,.5],[1.5,.5],[1.5,id===5?0:1]],(t-.6)/.4);
}

export function rotatePoint([x, y], r) {
  switch (((r % 4) + 4) % 4) {
    case 1: return [1 - y, x];
    case 2: return [2 - x, 1 - y];
    case 3: return [y, 2 - x];
    default: return [x, y];
  }
}

export function ports(p) {
  return PIECES[p.id].ports.map(point => {
    const [x, y] = rotatePoint(point, p.r);
    return [p.x + x, p.y + y];
  });
}

export function canPlace(p, placements = []) {
  if (!PIECES[p.id] || ![p.x, p.y, p.r].every(Number.isInteger) || p.r < 0 || p.r > 3) return false;
  const [w, h] = size(p.r);
  if (p.x < 0 || p.y < 0 || p.x + w > COLS || p.y + h > ROWS) return false;
  return placements.every(other => {
    if (p.id === other.id) return true;
    const [ow, oh] = size(other.r);
    return p.x + w <= other.x || other.x + ow <= p.x || p.y + h <= other.y || other.y + oh <= p.y;
  });
}

const same = (a, b) => a[0] === b[0] && a[1] === b[1];

// Trace actual tile ports; a matching saved solution is never used to judge play.
export function validate(level, placements) {
  if (!Array.isArray(placements) || new Set(placements.map(p => p.id)).size !== placements.length ||
    placements.some(p => !level.sequence.includes(p.id) || !canPlace(p, placements))) {
    return { ok: false, message: '拼板有重叠或超出棋盘，请重新摆放。', route: [] };
  }
  if (placements.length !== new Set(level.sequence).size) {
    return { ok: false, message: '先把本关的拼板都放进棋盘，再出发吧。', route: [] };
  }
  let current = GATES[level.start];
  let previous = null;
  const route = [];
  const seen = new Set();
  while (route.length <= placements.length * 2) {
    const next = placements.find(p => p.id !== previous && ports(p).some(pt => same(pt, current)));
    if (!next) {
      return { ok: false, message: route.length ? `跃过 ${route.at(-1).id} 号后路线断开了。检查相邻拼板的路口。` : `还没有路接到起点 ${level.start}。把路口对准入口试试。`, route };
    }
    const pp = ports(next);
    const from = pp.findIndex(pt => same(pt, current));
    const key = `${next.id}:${from}`;
    if (seen.has(key)) return { ok: false, message: '路线绕回了原处，还没有抵达终点。', route };
    seen.add(key);
    route.push({ ...next, from });
    const expected = level.sequence[route.length - 1];
    if (next.id !== expected) return { ok: false, message: `这里遇到了 ${next.id} 号障碍，接下来应该是 ${expected} 号。调整顺序试试。`, route };
    current = pp[1 - from];
    if (same(current, GATES[level.finish])) {
      const ok = route.length === level.sequence.length;
      return { ok, message: ok ? '路线正确，所有障碍都按顺序通过！' : '太早到达终点啦，还有障碍没有经过。', route };
    }
    previous = next.id;
  }
  return { ok: false, message: '路线还没有连到终点。', route };
}

export function restorePlacements(level, value) {
  if (!Array.isArray(value)) return [];
  const restored = [];
  for (const candidate of value) {
    if (!candidate || !level.sequence.includes(candidate.id) || restored.some(p => p.id === candidate.id)) continue;
    const p = { id: candidate.id, x: candidate.x, y: candidate.y, r: candidate.r };
    if (canPlace(p, restored)) restored.push(p);
  }
  return restored;
}
