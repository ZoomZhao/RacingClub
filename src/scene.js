import { stackLevels, roadNumberLayout, entryCenter, entryOutward, horsePose, dropCandidate } from './rules.js';

export const BASE_DEPTH = .34;
export const TILE = .16;
export const SELECTED_LIFT = .07;
export const DRAG_LIFT = .55;
const SIGN_WIDTH = .46, SIGN_BOTTOM = .05, SIGN_TOP = .42;
const HORSE_LENGTH = 1, RAIL_CLEARANCE = .14, JUMP_FROM = .27, JUMP_TO = 1.3;
const tileTop = level => TILE * (level + 1);

const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const mul = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const normalize = a => { const l = Math.max(Math.sqrt(dot(a, a)), 1e-6); return [a[0] / l, a[1] / l, a[2] / l]; };
const LIGHT = normalize([-.38, 1, .62]);

const rgb = hex => parseInt(hex.slice(1), 16);
const css = (color, alpha = 1) => `rgba(${(color >> 16) & 255},${(color >> 8) & 255},${color & 255},${alpha})`;
const mix = (from, to, t) => {
  const c = shift => Math.round(((from >> shift) & 255) + (((to >> shift) & 255) - ((from >> shift) & 255)) * t);
  return (c(16) << 16) | (c(8) << 8) | c(0);
};
const shadeValue = (color, normal, ambient) => {
  const k = Math.min(Math.max(ambient + (1 - ambient) * Math.max(0, dot(normal, LIGHT)), 0), 1.08);
  const c = shift => Math.min(255, Math.max(0, Math.round(((color >> shift) & 255) * k)));
  return (c(16) << 16) | (c(8) << 8) | c(0);
};
const shade = (color, normal, ambient = .56) => css(shadeValue(color, normal, ambient));
const GREEN = 0x254c3b, WHITE = 0xffffff, BLACK = 0;

export class Camera {
  constructor(width, height, { pitch = 56, fov = 28, distance = 13, topInset = 0 } = {}) {
    this.width = width; this.height = height;
    const target = [2.85, 0, 2.3], angle = pitch * Math.PI / 180;
    this.eye = add(target, mul([0, Math.sin(angle), Math.cos(angle)], distance));
    this.forward = normalize(sub(target, this.eye));
    this.right = [1, 0, 0];
    this.up = [0, Math.cos(angle), -Math.sin(angle)];
    this.focal = 1 / Math.tan(fov * Math.PI / 360);
    this.scale = 1; this.ox = 0; this.oy = 0;
    const points = [];
    for (const x of [-.05, 5.75]) for (const z of [-.05, 4.75]) for (const y of [-BASE_DEPTH, .5]) points.push(this.ndc([x, y, z]));
    const minX = Math.min(...points.map(p => p[0])), maxX = Math.max(...points.map(p => p[0]));
    const minY = Math.min(...points.map(p => p[1])), maxY = Math.max(...points.map(p => p[1]));
    const room = Math.max(height - topInset, height * .6);
    this.scale = Math.min(width / (maxX - minX), room / (maxY - minY));
    this.ox = width / 2 - (minX + maxX) / 2 * this.scale;
    this.oy = height - room / 2 - (minY + maxY) / 2 * this.scale;
  }
  ndc(p) { const v = sub(p, this.eye), depth = dot(v, this.forward); return [dot(v, this.right) / depth * this.focal, -dot(v, this.up) / depth * this.focal]; }
  project(p) { const [x, y] = this.ndc(p); return [x * this.scale + this.ox, y * this.scale + this.oy]; }
  depth(p) { return dot(sub(p, this.eye), this.forward); }
  facing(normal, point) { return dot(normal, sub(this.eye, point)) > 0; }
  pixelsPerUnit(p) { return this.project(add(p, [.5, 0, 0]))[0] - this.project(sub(p, [.5, 0, 0]))[0]; }
  toFrame([sx, sy], h) {
    const xn = (sx - this.ox) / this.scale / this.focal, yn = -(sy - this.oy) / this.scale / this.focal;
    const dir = add(add(this.forward, mul(this.right, xn)), mul(this.up, yn));
    if (Math.abs(dir[1]) < 1e-6) return [-99, -99];
    const t = (h - this.eye[1]) / dir[1];
    return [this.eye[0] + dir[0] * t, this.eye[2] + dir[2] * t];
  }
}

function roundedOutline(x0, z0, x1, z1, radius, segments = 5) {
  const r = Math.min(radius, Math.min(x1 - x0, z1 - z0) / 2);
  const corners = [[[x1 - r, z1 - r], 90], [[x1 - r, z0 + r], 360], [[x0 + r, z0 + r], 270], [[x0 + r, z1 - r], 180]];
  const out = [];
  for (const [[cx, cz], start] of corners) for (let i = 0; i <= segments; i++) {
    const a = (start - 90 * i / segments) * Math.PI / 180;
    out.push([cx + Math.cos(a) * r, cz + Math.sin(a) * r]);
  }
  return out;
}
function orientedOutline([cx, cz], [ax, az], halfAlong, halfAcross) {
  const nx = -az, nz = ax;
  return [[cx + ax * halfAlong + nx * halfAcross, cz + az * halfAlong + nz * halfAcross], [cx + ax * halfAlong - nx * halfAcross, cz + az * halfAlong - nz * halfAcross],
    [cx - ax * halfAlong - nx * halfAcross, cz - az * halfAlong - nz * halfAcross], [cx - ax * halfAlong + nx * halfAcross, cz - az * halfAlong + nz * halfAcross]];
}
function roundedOriented([cx, cz], [ax, az], halfAlong, halfAcross, radius) {
  const nx = -az, nz = ax;
  return roundedOutline(-halfAlong, -halfAcross, halfAlong, halfAcross, radius, 3).map(([u, v]) => [cx + ax * u + nx * v, cz + az * u + nz * v]);
}

function roundRectPath(ctx, x, y, w, h, r) {
  const radius = Math.max(0, Math.min(r, w / 2, h / 2));
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + w, y, x + w, y + h, radius);
  ctx.arcTo(x + w, y + h, x, y + h, radius);
  ctx.arcTo(x, y + h, x, y, radius);
  ctx.arcTo(x, y, x + w, y, radius);
  ctx.closePath();
}
function fillRound(ctx, x, y, w, h, r, style) { roundRectPath(ctx, x, y, w, h, r); ctx.fillStyle = style; ctx.fill(); }
function strokeRound(ctx, x, y, w, h, r, style, width) { roundRectPath(ctx, x, y, w, h, r); ctx.strokeStyle = style; ctx.lineWidth = width; ctx.stroke(); }
function line(ctx, x0, y0, x1, y1, style, width, cap = 'butt') { ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.strokeStyle = style; ctx.lineWidth = width; ctx.lineCap = cap; ctx.stroke(); }
function textureRect(ctx, image, x, y, w, h, r) {
  ctx.save(); roundRectPath(ctx, x, y, w, h, r); ctx.clip();
  ctx.drawImage(image, x, y, Math.max(1, w), Math.max(1, h));
  ctx.restore();
}
const FONT = '"PingFang SC","Hiragino Sans GB","Noto Sans SC","Microsoft YaHei",system-ui,sans-serif';
function makeCanvas(width, height) {
  return typeof OffscreenCanvas !== 'undefined' ? new OffscreenCanvas(width, height) : Object.assign(document.createElement('canvas'), { width, height });
}
function centeredText(ctx, text, x, y, size, color, weight = 700) {
  ctx.font = `${weight} ${size}px ${FONT}`;
  ctx.fillStyle = color; ctx.textAlign = 'center'; ctx.textBaseline = 'alphabetic';
  const m = ctx.measureText(text);
  const ascent = m.actualBoundingBoxAscent ?? size * .72, descent = m.actualBoundingBoxDescent ?? 0;
  ctx.fillText(text, x, y + (ascent - descent) / 2);
}
const ROAD_NUMBER_FACE = '#fff0d2', ROAD_NUMBER_INK = '#214334';
export function paintRoadNumber(ctx, id, color, label, u, cx, cy) {
  const w = label.width * u, h = label.height * u, x = cx - w / 2, y = cy - h / 2, r = h * .2;
  fillRound(ctx, x, y, w, h, r, ROAD_NUMBER_FACE);
  strokeRound(ctx, x, y, w, h, r, 'rgba(184,150,101,.48)', Math.max(.6, u * .006));
  const accentY = cy + h * .35;
  line(ctx, cx - w * .24, accentY, cx + w * .24, accentY, color, h * .065, 'round');
  centeredText(ctx, String(id), cx, cy - h * .08, label.font * u, ROAD_NUMBER_INK);
}
export function paintTile(ctx, rules, p, u, ox, oy, materials, options = {}) {
  const { selected = false, number = false, numberAvoid = [], minimumFont = 0, numberBounds = null, shadow = true, rail = true } = options;
  const [w, h] = rules.dimensions(p.r), tx = ox + p.x * u, ty = oy + p.y * u;
  const fx = tx + u * .025, fy = ty + u * .025, fw = w * u - u * .05, fh = h * u - u * .055, radius = u * .07;
  if (shadow) fillRound(ctx, fx, fy + u * .045, fw, fh, radius, 'rgba(128,96,63,.26)');
  fillRound(ctx, fx, fy, fw, fh, radius, '#a87943');
  textureRect(ctx, materials.wood, fx, fy, fw, fh, radius);
  const sheen = ctx.createLinearGradient(0, fy, 0, fy + fh);
  sheen.addColorStop(0, 'rgba(255,255,255,.25)'); sheen.addColorStop(.5, 'rgba(255,255,255,0)'); sheen.addColorStop(1, 'rgba(149,99,62,.12)');
  fillRound(ctx, fx, fy, fw, fh, radius, sheen);
  const piece = rules.pieces.get(p.id);
  ctx.save(); roundRectPath(ctx, fx, fy, fw, fh, radius); ctx.clip();
  const trace = path => { ctx.beginPath(); path.samples.forEach((pt, i) => { const [qx, qy] = rules.rotate(pt, p.r); const x = tx + qx * u, y = ty + qy * u; if (i) ctx.lineTo(x, y); else ctx.moveTo(x, y); }); };
  ctx.lineCap = 'butt'; ctx.lineJoin = 'round';
  const road = ctx.createLinearGradient(tx, ty, tx + w * u, ty + h * u);
  road.addColorStop(0, '#fff3d8'); road.addColorStop(.5, '#f5dfb6'); road.addColorStop(1, '#ffefd0');
  for (const [style, width] of [['rgba(185,146,97,.45)', .64], ['#f8e5be', .59], [road, .55]]) for (const path of piece.paths) { trace(path); ctx.strokeStyle = style; ctx.lineWidth = u * width; ctx.stroke(); }
  ctx.restore();
  strokeRound(ctx, fx, fy, fw, fh, radius, '#b07e48', u * .014);
  strokeRound(ctx, fx + u * .025, fy + u * .025, fw - u * .05, fh - u * .05, radius * .85, 'rgba(255,241,206,.76)', u * .017);
  if (rail) {
    const [gx, gy] = rules.rotate([piece.gate[0] / 100, piece.gate[1] / 100], p.r);
    ctx.save(); ctx.translate(tx + gx * u, ty + gy * u); ctx.rotate((piece.gate[2] + p.r * 90) * Math.PI / 180);
    fillRound(ctx, -u * .035, -u * .31, u * .13, u * .69, u * .02, 'rgba(118,81,53,.24)');
    line(ctx, 0, -u * .29, 0, u * .29, piece.color, u * .075);
    for (let i = 1; i < 7; i += 2) { const y = -.29 + .58 * i / 7; line(ctx, 0, y * u, 0, (y + .58 / 7) * u, '#fffaf0', u * .076); }
    line(ctx, -u * .018, -u * .27, -u * .018, u * .27, 'rgba(255,255,255,.38)', u * .013);
    for (const sign of [-1, 1]) {
      const footX = -u * .095, footY = sign * u * .3 - u * .045;
      fillRound(ctx, footX + u * .025, footY + u * .035, u * .19, u * .1, u * .025, 'rgba(91,58,39,.18)');
      fillRound(ctx, footX, footY, u * .19, u * .1, u * .025, piece.color);
      fillRound(ctx, footX + u * .018, footY + u * .009, u * .15, u * .022, u * .01, 'rgba(255,255,255,.25)');
    }
    ctx.restore();
  }
  if (selected) {
    strokeRound(ctx, fx - u * .012, fy - u * .012, fw + u * .024, fh + u * .024, radius, '#ffefbd', u * .05);
    strokeRound(ctx, fx - u * .012, fy - u * .012, fw + u * .024, fh + u * .024, radius, css(GREEN), u * .027);
  }
  if (number) {
    const label = roadNumberLayout(rules, p, numberAvoid, minimumFont / u, numberBounds);
    paintRoadNumber(ctx, p.id, piece.color, label, u, tx + label.center[0] * u, ty + label.center[1] * u);
  }
}
function paintBoardSurface(ctx, u, materials, width, height) {
  const ox = u * .35, oy = u * .35;
  const x = u * .025, y = u * .025, w = width - u * .05, h = height - u * .065, rad = u * .28;
  textureRect(ctx, materials.wood, x, y, w, h, rad);
  const glow = ctx.createLinearGradient(0, y, 0, y + h);
  glow.addColorStop(0, 'rgba(255,225,173,.35)'); glow.addColorStop(.5, 'rgba(255,225,173,0)'); glow.addColorStop(1, 'rgba(161,108,53,.16)');
  fillRound(ctx, x, y, w, h, rad, glow);
  strokeRound(ctx, x, y, w, h, rad, '#c08d52', u * .025);
  strokeRound(ctx, x + u * .04, y + u * .04, w - u * .08, h - u * .08, rad * .88, '#fff0cc', u * .035);
  strokeRound(ctx, x + u * .1, y + u * .1, w - u * .2, h - u * .2, rad * .74, '#b57e41', u * .018);
  fillRound(ctx, ox - u * .025, oy - u * .025, 5.05 * u, 4.05 * u, u * .14, '#a37845');
  for (let row = 0; row < 4; row++) for (let col = 0; col < 5; col++) {
    const cx = ox + col * u + u * .018, cy = oy + row * u + u * .018, size = u * .964;
    textureRect(ctx, materials.sand, cx, cy, size, size, u * .13);
    strokeRound(ctx, cx, cy, size, size, u * .13, 'rgba(143,105,61,.2)', u * .02);
    const cell = ctx.createLinearGradient(0, cy, 0, cy + size);
    cell.addColorStop(0, 'rgba(255,255,255,.14)'); cell.addColorStop(.5, 'rgba(255,255,255,0)'); cell.addColorStop(1, 'rgba(140,93,49,.1)');
    fillRound(ctx, cx, cy, size, size, u * .13, cell);
  }
  strokeRound(ctx, ox - u * .025, oy - u * .025, 5.05 * u, 4.05 * u, u * .14, '#f9e2b2', u * .028);
}
function paintEntryMarker(ctx, c, outward, u, start, finish) {
  const horizontal = outward[1] !== 0;
  const box = horizontal ? [u * .57, u * .29] : [u * .3, u * .52];
  const terracotta = '#a4472f';
  if (finish) {
    const tangent = [-outward[1], outward[0]];
    const reach = (horizontal ? box[1] : box[0]) / 2 - u * .04;
    const band = [c[0] + outward[0] * reach, c[1] + outward[1] * reach];
    for (let i = 0; i <= 5; i++) for (let row = 0; row <= 1; row++) {
      const x = band[0] + tangent[0] * ((i - 2.5) * u * .068) + outward[0] * ((row - .5) * u * .03);
      const y = band[1] + tangent[1] * ((i - 2.5) * u * .068) + outward[1] * ((row - .5) * u * .03);
      ctx.fillStyle = (i + row) % 2 === 0 ? terracotta : '#fffaf0';
      if (horizontal) ctx.fillRect(x - u * .034, y - u * .015, u * .068, u * .03); else ctx.fillRect(x - u * .015, y - u * .034, u * .03, u * .068);
    }
  }
  if (start) {
    const tangent = [-outward[1], outward[0]];
    const at = (base, along, across) => [base[0] + outward[0] * along + tangent[0] * across, base[1] + outward[1] * along + tangent[1] * across];
    const tip = at(c, u * .2, 0), tail = at(c, u * .46, 0);
    const points = [tip, at(tip, u * .14, u * .105), at(tip, u * .14, u * .045), at(tail, 0, u * .045), at(tail, 0, -u * .045), at(tip, u * .14, -u * .045), at(tip, u * .14, -u * .105)];
    ctx.beginPath(); points.forEach(([x, y], i) => i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)); ctx.closePath();
    ctx.lineJoin = 'round'; ctx.strokeStyle = '#fffaf0'; ctx.lineWidth = u * .045; ctx.stroke();
    ctx.fillStyle = css(GREEN); ctx.fill();
  }
}
function paintSignFace(ctx, name, width, height, start, finish) {
  const r = height * .16;
  fillRound(ctx, 0, 0, width, height, r, start ? css(GREEN) : finish ? '#fff3df' : '#ffeccb');
  strokeRound(ctx, 4, 4, width - 8, height - 8, r - 3, start ? '#fff1cc' : finish ? '#a4472f' : '#c99a5e', 5);
  if (finish) {
    const cell = width / 12, top = height - cell * 2.2;
    for (let i = 0; i < 10; i++) for (let row = 0; row <= 1; row++) { ctx.fillStyle = (i + row) % 2 === 0 ? '#a4472f' : '#fffaf0'; ctx.fillRect(cell + i * cell, top + row * cell * .7, cell, cell * .7); }
  }
  centeredText(ctx, name, width / 2, height * (finish ? .42 : .5), height * .62, start ? '#ffffff' : '#342718');
}
export function createTextures(rules, materials) {
  const cell = 192, boardCell = 200;
  const tiles = new Map(), labels = new Map(), signs = new Map();
  let board = null;
  return {
    tile(id) {
      if (!tiles.has(id)) {
        const canvas = makeCanvas(2 * cell, cell);
        paintTile(canvas.getContext('2d'), rules, { id, x: 0, y: 0, r: 0 }, cell, 0, 0, materials, { shadow: false, rail: false });
        tiles.set(id, canvas);
      }
      return tiles.get(id);
    },
    label(id, layout) {
      const k = `${id}:${layout.width}:${layout.height}`;
      if (!labels.has(k)) {
        const canvas = makeCanvas(Math.ceil(layout.width * cell), Math.ceil(layout.height * cell));
        paintRoadNumber(canvas.getContext('2d'), id, rules.pieces.get(id).color, layout, cell, canvas.width / 2, canvas.height / 2);
        labels.set(k, canvas);
      }
      return labels.get(k);
    },
    sign(name, start, finish) {
      const k = `${name}:${start}:${finish}`;
      if (!signs.has(k)) {
        const canvas = makeCanvas(184, 148);
        paintSignFace(canvas.getContext('2d'), name, 184, 148, start, finish);
        signs.set(k, canvas);
      }
      return signs.get(k);
    },
    board(start, finish) {
      if (board && board.start === start && board.finish === finish) return board.canvas;
      const canvas = makeCanvas(Math.round(5.7 * boardCell), Math.round(4.7 * boardCell));
      const ctx = canvas.getContext('2d');
      paintBoardSurface(ctx, boardCell, materials, canvas.width, canvas.height);
      for (const [name, pt] of rules.gates) {
        const [cx, cy] = entryCenter(pt);
        paintEntryMarker(ctx, [cx * boardCell, cy * boardCell], entryOutward(pt), boardCell, name === start, name === finish);
      }
      board = { start, finish, canvas };
      return canvas;
    },
  };
}
export function paintTrayTile(canvas, rules, id, r, materials, selected, dpr) {
  const ctx = canvas.getContext('2d');
  const width = canvas.width / dpr, height = canvas.height / dpr;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);
  const [w, h] = rules.dimensions(r), u = Math.min(width / w, height / h);
  if (u <= 0) return;
  const ox = (width - w * u) / 2, oy = (height - h * u) / 2;
  const numberBounds = { left: -ox / u + .04, top: -oy / u + .04, right: (width - ox) / u - .04, bottom: (height - oy) / u - .04 };
  paintTile(ctx, rules, { id, x: 0, y: 0, r }, u, ox, oy, materials, { selected, number: true, minimumFont: 11, numberBounds });
}

export class Painter {
  constructor(camera, ctx, dpr) { this.camera = camera; this.ctx = ctx; this.dpr = dpr; }
  reset() { this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0); }
  path(points) {
    const ctx = this.ctx;
    ctx.beginPath();
    points.forEach((p, i) => { const [x, y] = this.camera.project(p); if (i) ctx.lineTo(x, y); else ctx.moveTo(x, y); });
    ctx.closePath();
  }
  polygon(points, style) { this.path(points); this.ctx.fillStyle = style; this.ctx.fill(); }
  outline(points, style, width) { this.path(points); this.ctx.strokeStyle = style; this.ctx.lineWidth = width; this.ctx.lineJoin = 'round'; this.ctx.stroke(); }
  shadow(outline, y, [dx, dz], alpha, blur) {
    const ctx = this.ctx, far = 4000;
    ctx.save();
    ctx.beginPath();
    outline.forEach(([x, z], i) => { const [sx, sy] = this.camera.project([x + dx, y, z + dz]); if (i) ctx.lineTo(sx - far, sy); else ctx.moveTo(sx - far, sy); });
    ctx.closePath();
    ctx.shadowColor = `rgba(58,36,18,${alpha})`;
    ctx.shadowBlur = Math.max(0, blur) * this.dpr;
    ctx.shadowOffsetX = far * this.dpr;
    ctx.fillStyle = '#000';
    ctx.fill();
    ctx.restore();
  }
  affine(image, src, dst) {
    const [[sx0, sy0], [sx1, sy1], [sx2, sy2]] = src, [[dx0, dy0], [dx1, dy1], [dx2, dy2]] = dst;
    const ux = sx1 - sx0, uy = sy1 - sy0, vx = sx2 - sx0, vy = sy2 - sy0;
    const det = ux * vy - vx * uy;
    if (Math.abs(det) < 1e-9) return;
    const ix = [vy / det, -uy / det], iy = [-vx / det, ux / det];
    const bigU = [dx1 - dx0, dy1 - dy0], bigV = [dx2 - dx0, dy2 - dy0];
    const a = bigU[0] * ix[0] + bigV[0] * ix[1], b = bigU[1] * ix[0] + bigV[1] * ix[1];
    const c = bigU[0] * iy[0] + bigV[0] * iy[1], d = bigU[1] * iy[0] + bigV[1] * iy[1];
    const e = dx0 - a * sx0 - c * sy0, f = dy0 - b * sx0 - d * sy0;
    const k = this.dpr;
    this.ctx.setTransform(a * k, b * k, c * k, d * k, e * k, f * k);
    this.ctx.drawImage(image, 0, 0);
    this.reset();
  }
  quad(image, corners, alpha = 1) {
    const w = image.width, h = image.height;
    const [tl, tr, br, bl] = corners.map(p => this.camera.project(p));
    const old = this.ctx.globalAlpha;
    this.ctx.globalAlpha = old * alpha;
    const blend = (m, n) => [(m[0] + n[0]) / 2, (m[1] + n[1]) / 2];
    this.affine(image, [[0, 0], [w, 0], [0, h]], [tl, blend(tr, [br[0] - bl[0] + tl[0], br[1] - bl[1] + tl[1]]), blend(bl, [br[0] - tr[0] + tl[0], br[1] - tr[1] + tl[1]])]);
    this.ctx.globalAlpha = old;
  }
  meshTexture(image, corners, columns, rows) {
    const ctx = this.ctx, w = image.width, h = image.height;
    const [tl, tr, br, bl] = corners;
    const lerp = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
    const grid = [];
    for (let j = 0; j <= rows; j++) { grid.push([]); for (let i = 0; i <= columns; i++) grid[j].push(this.camera.project(lerp(lerp(tl, tr, i / columns), lerp(bl, br, i / columns), j / rows))); }
    const triangle = (s, d) => {
      const cx = (d[0][0] + d[1][0] + d[2][0]) / 3, cy = (d[0][1] + d[1][1] + d[2][1]) / 3;
      ctx.save();
      ctx.beginPath();
      d.forEach(([x, y], i) => { const l = Math.hypot(x - cx, y - cy) || 1, gx = x + (x - cx) / l * .8, gy = y + (y - cy) / l * .8; if (i) ctx.lineTo(gx, gy); else ctx.moveTo(gx, gy); });
      ctx.closePath(); ctx.clip();
      this.affine(image, s, d);
      ctx.restore();
    };
    for (let j = 0; j < rows; j++) for (let i = 0; i < columns; i++) {
      const s00 = [i / columns * w, j / rows * h], s10 = [(i + 1) / columns * w, j / rows * h], s01 = [i / columns * w, (j + 1) / rows * h], s11 = [(i + 1) / columns * w, (j + 1) / rows * h];
      triangle([s00, s10, s11], [grid[j][i], grid[j][i + 1], grid[j + 1][i + 1]]);
      triangle([s00, s11, s01], [grid[j][i], grid[j + 1][i + 1], grid[j + 1][i]]);
    }
  }
  prism(outline, y0, y1, side, top = null, ambient = .56) {
    const n = outline.length;
    let area = 0;
    for (let i = 0; i < n; i++) { const a = outline[i], b = outline[(i + 1) % n]; area += a[0] * b[1] - b[0] * a[1]; }
    const sign = area > 0 ? 1 : -1;
    for (let i = 0; i < n; i++) {
      const a = outline[i], b = outline[(i + 1) % n], ex = b[0] - a[0], ez = b[1] - a[1];
      if (Math.hypot(ex, ez) < 1e-5) continue;
      const normal = normalize([ez * sign, 0, -ex * sign]);
      if (!this.camera.facing(normal, [(a[0] + b[0]) / 2, (y0 + y1) / 2, (a[1] + b[1]) / 2])) continue;
      const style = shade(side, normal, ambient);
      this.path([[a[0], y1, a[1]], [b[0], y1, b[1]], [b[0], y0, b[1]], [a[0], y0, a[1]]]);
      this.ctx.fillStyle = style; this.ctx.fill();
      this.ctx.strokeStyle = style; this.ctx.lineWidth = .5; this.ctx.stroke();
    }
    if (top !== null) this.polygon(outline.map(([x, z]) => [x, y1, z]), shade(top, [0, 1, 0], ambient));
  }
  tube(a, b, radius, color) {
    const ctx = this.ctx, p = this.camera.project(a), q = this.camera.project(b);
    const length = Math.hypot(q[0] - p[0], q[1] - p[1]);
    const d = length < .01 ? [1, 0] : [(q[0] - p[0]) / length, (q[1] - p[1]) / length];
    let n = [-d[1], d[0]];
    if (n[0] * -.38 + n[1] * -1 < 0) n = [-n[0], -n[1]];
    const wa = this.camera.pixelsPerUnit(a) * radius, wb = this.camera.pixelsPerUnit(b) * radius, w = (wa + wb) / 2;
    const m = [(p[0] + q[0]) / 2, (p[1] + q[1]) / 2];
    const gradient = ctx.createLinearGradient(m[0] + n[0] * w, m[1] + n[1] * w, m[0] - n[0] * w, m[1] - n[1] * w);
    [[0, mix(color, BLACK, .04)], [.24, mix(color, WHITE, .42)], [.5, color], [.82, mix(color, BLACK, .22)], [1, mix(color, BLACK, .4)]].forEach(([at, c]) => gradient.addColorStop(at, css(c)));
    ctx.beginPath();
    ctx.moveTo(p[0] + n[0] * wa, p[1] + n[1] * wa); ctx.lineTo(q[0] + n[0] * wb, q[1] + n[1] * wb);
    ctx.lineTo(q[0] - n[0] * wb, q[1] - n[1] * wb); ctx.lineTo(p[0] - n[0] * wa, p[1] - n[1] * wa);
    ctx.closePath(); ctx.fillStyle = gradient; ctx.fill();
  }
  tubeCap(top, radius, color) {
    const ctx = this.ctx, c = this.camera.project(top), rx = this.camera.pixelsPerUnit(top) * radius;
    const ry = Math.max(.1, Math.abs(this.camera.project(add(top, [0, 0, radius]))[1] - c[1]));
    const gradient = ctx.createRadialGradient(c[0] - rx * .3, c[1] - ry * .35, 0, c[0] - rx * .3, c[1] - ry * .35, rx * 1.3);
    gradient.addColorStop(0, css(mix(color, WHITE, .5))); gradient.addColorStop(.55, css(mix(color, WHITE, .12))); gradient.addColorStop(1, css(mix(color, BLACK, .12)));
    ctx.beginPath(); ctx.ellipse(c[0], c[1], rx, ry, 0, 0, Math.PI * 2); ctx.fillStyle = gradient; ctx.fill();
  }
  ball(at, radius, color) {
    const ctx = this.ctx, c = this.camera.project(at), r = this.camera.pixelsPerUnit(at) * radius;
    const gradient = ctx.createRadialGradient(c[0] - r * .35, c[1] - r * .4, 0, c[0] - r * .35, c[1] - r * .4, r * 1.45);
    gradient.addColorStop(0, '#fff'); gradient.addColorStop(.45, css(color)); gradient.addColorStop(1, css(mix(color, BLACK, .3)));
    ctx.beginPath(); ctx.arc(c[0], c[1], r, 0, Math.PI * 2); ctx.fillStyle = gradient; ctx.fill();
  }
  rail(rules, p, x, z, y) {
    const piece = rules.pieces.get(p.id);
    const [lx, lz] = rules.rotate([piece.gate[0] / 100, piece.gate[1] / 100], p.r);
    const center = [x + lx, z + lz];
    const angle = (piece.gate[2] + p.r * 90) * Math.PI / 180;
    const along = [-Math.sin(angle), Math.cos(angle)], across = [along[1], -along[0]];
    const color = rgb(piece.color), cream = 0xfffaf0;
    const px = this.camera.pixelsPerUnit([center[0], y, center[1]]);
    const footTop = y + .05, poleY = y + .22, postTop = y + .36, half = .3;
    this.shadow(roundedOriented(center, along, half + .06, .11, .05), y, [.035, .06], .2, px * .035);
    const feet = [-1, 1].map(k => [center[0] + along[0] * half * k, center[1] + along[1] * half * k]).sort((m, n) => this.camera.depth([n[0], y, n[1]]) - this.camera.depth([m[0], y, m[1]]));
    for (const foot of feet) {
      this.prism(roundedOriented(foot, across, .095, .052, .03), y, footTop, color, mix(color, WHITE, .08), .6);
      this.outline(roundedOriented(foot, across, .075, .034, .022).map(([fx, fz]) => [fx, footTop + .001, fz]), css(cream, .27), px * .012);
    }
    const parts = feet.map(foot => [this.camera.depth([foot[0], y, foot[1]]), () => {
      this.tube([foot[0], footTop - .005, foot[1]], [foot[0], postTop, foot[1]], .04, color);
      this.tubeCap([foot[0], postTop, foot[1]], .04, color);
      this.ball([foot[0], postTop + .028, foot[1]], .032, cream);
    }]);
    parts.push([this.camera.depth([center[0], y, center[1]]) + .001, () => {
      const a = [center[0] - along[0] * half, center[1] - along[1] * half], b = [center[0] + along[0] * half, center[1] + along[1] * half];
      this.tube([a[0], poleY, a[1]], [b[0], poleY, b[1]], .033, color);
      for (let i = 1; i < 7; i += 2) {
        const s0 = [a[0] + (b[0] - a[0]) * i / 7, a[1] + (b[1] - a[1]) * i / 7], s1 = [a[0] + (b[0] - a[0]) * (i + 1) / 7, a[1] + (b[1] - a[1]) * (i + 1) / 7];
        this.tube([s0[0], poleY, s0[1]], [s1[0], poleY, s1[1]], .033 * 1.01, cream);
      }
    }]);
    parts.sort((m, n) => n[0] - m[0]).forEach(([, draw]) => draw());
  }
  fence({ rails, posts }) {
    const cream = 0xfff4d7, items = [];
    for (const [a, b] of rails) {
      const mid = [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2], length = Math.hypot(b[0] - a[0], b[1] - a[1]);
      if (length < .01) continue;
      const axis = [(b[0] - a[0]) / length, (b[1] - a[1]) / length];
      items.push([this.camera.depth([mid[0], .1, mid[1]]) + .05, () => this.prism(orientedOutline(mid, axis, length / 2, .022), .1, .155, cream, cream, .66)]);
    }
    for (const [px, pz] of posts) items.push([this.camera.depth([px, .1, pz]), () => this.prism(roundedOutline(px - .065, pz - .065, px + .065, pz + .065, .03, 2), 0, .24, cream, cream, .66)]);
    items.sort((m, n) => n[0] - m[0]).forEach(([, draw]) => draw());
  }
  sign(name, [x, z], textures, start, finish) {
    const w = SIGN_WIDTH / 2, h = SIGN_TOP - SIGN_BOTTOM;
    this.shadow(orientedOutline([x, z + .04], [1, 0], w * .9, .06), 0, [.02, .03], .24, this.camera.pixelsPerUnit([x, 0, z]) * .04);
    const edge = start ? GREEN : finish ? 0xa4472f : 0xc99a5e;
    const up = mul(this.camera.up, h), base = [x, SIGN_BOTTOM, z];
    this.prism(roundedOutline(x - .03, z - .03, x + .03, z + .03, .012, 2), 0, SIGN_BOTTOM + h * .3, 0xc99a5e, 0xc99a5e, .6);
    const drop = [0, -.035, 0];
    this.polygon([add(add(add(base, [-w, 0, 0]), up), drop), add(add(add(base, [w, 0, 0]), up), drop), add(add(base, [w, 0, 0]), drop), add(add(base, [-w, 0, 0]), drop)], shade(edge, [0, 0, 1], .5));
    this.quad(textures.sign(name, start, finish), [add(add(base, [-w, 0, 0]), up), add(add(base, [w, 0, 0]), up), add(base, [w, 0, 0]), add(base, [-w, 0, 0])]);
  }
  tile(rules, p, x, z, base, textures, selected, alpha = 1) {
    const ctx = this.ctx, top = base + TILE;
    const [w, h] = rules.dimensions(p.r);
    ctx.save(); ctx.globalAlpha = alpha;
    this.prism(roundedOutline(x + .025, z + .025, x + w - .025, z + h - .03, .07, 3), base, top, 0x9b6c3c);
    const corners = [[0, 0], [2, 0], [2, 1], [0, 1]].map(c => { const [qx, qz] = rules.rotate(c, p.r); return [x + qx, top, z + qz]; });
    this.quad(textures.tile(p.id), corners);
    const label = roadNumberLayout(rules, p), b = label.bounds;
    this.quad(textures.label(p.id, label), [[x + b.left, top, z + b.top], [x + b.right, top, z + b.top], [x + b.right, top, z + b.bottom], [x + b.left, top, z + b.bottom]]);
    if (selected) {
      const ring = roundedOutline(x + .005, z + .005, x + w - .005, z + h - .01, .08, 3).map(([rx, rz]) => [rx, top + .002, rz]);
      const px = this.camera.pixelsPerUnit([x + w / 2, top, z + h / 2]);
      this.outline(ring, '#ffefbd', px * .07);
      this.outline(ring, css(GREEN), px * .035);
    }
    this.rail(rules, p, x, z, top);
    ctx.restore();
  }
  board(state, textures, fence, ghost) {
    const { rules } = state;
    const frame = roundedOutline(.025, .025, 5.675, 4.66, .28, 6);
    const px = this.camera.pixelsPerUnit([2.85, 0, 2.35]);
    this.shadow(frame, -BASE_DEPTH, [.06, .12], .3, px * .18);
    this.prism(frame, -BASE_DEPTH, 0, 0xa8743f, null, .5);
    this.meshTexture(textures.board(state.startGate, state.finishGate), [[0, 0, 0], [5.7, 0, 0], [5.7, 0, 4.7], [0, 0, 4.7]], 6, 5);
    const levels = stackLevels(rules, state.placed);
    const depthOf = p => { const [w, h] = rules.dimensions(p.r); return this.camera.depth([p.x + .35 + w / 2, 0, p.y + .35 + h / 2]); };
    const order = [...state.placed].sort((m, n) => levels.get(m.id) - levels.get(n.id) || depthOf(n) - depthOf(m));
    const backRails = fence.rails.filter(([a, b]) => a[1] < .5 && b[1] < .5), backPosts = fence.posts.filter(p => p[1] < .5);
    const rest = { rails: fence.rails.filter(r => !backRails.includes(r)), posts: fence.posts.filter(p => !backPosts.includes(p)) };
    const signs = far => [...rules.gates].map(([name, pt]) => [name, entryCenter(pt)]).filter(([, c]) => (c[1] < .5) === far)
      .sort((m, n) => this.camera.depth([n[1][0], 0, n[1][1]]) - this.camera.depth([m[1][0], 0, m[1][1]]))
      .forEach(([name, c]) => this.sign(name, c, textures, name === state.startGate, name === state.finishGate));
    this.fence({ rails: backRails, posts: backPosts });
    signs(true);
    const top = Math.max(-1, ...levels.values());
    for (let level = 0; level <= top; level++) {
      const tier = order.filter(p => levels.get(p.id) === level), base = level === 0 ? 0 : tileTop(level - 1);
      for (const p of tier) {
        const lift = state.selected === p.id && !state.running ? SELECTED_LIFT : 0;
        const [w, h] = rules.dimensions(p.r), x = p.x + .35, z = p.y + .35;
        this.shadow(roundedOutline(x + .025, z + .025, x + w - .025, z + h - .03, .07, 3), base, [.035 * (1 + lift * 8), .06 * (1 + lift * 8)], lift ? .3 : .24, px * (.05 + lift * .6));
      }
      for (const p of tier) {
        const chosen = state.selected === p.id && !state.running;
        this.tile(rules, p, p.x + .35, p.y + .35, base + (chosen ? SELECTED_LIFT : 0), textures, chosen, p.id === ghost ? .43 : 1);
      }
    }
    this.fence(rest);
    signs(false);
  }
  dragPreview(state, piece, frame, grab, textures, problem) {
    const { rules } = state;
    const others = state.placed.filter(o => o.id !== piece.id), levels = stackLevels(rules, others);
    const candidate = dropCandidate(piece, frame, grab);
    const [w, h] = rules.dimensions(piece.r);
    const landing = Math.max(0, ...others.filter(o => rules.overlaps({ ...candidate, id: -1 }, o)).map(o => tileTop(levels.get(o.id))));
    const color = problem ? 0xc04835 : others.some(o => rules.overlaps(candidate, o)) ? 0xa96b1e : GREEN;
    const px = this.camera.pixelsPerUnit([frame[0], 0, frame[1]]);
    if (!problem) {
      const cell = roundedOutline(candidate.x + .35, candidate.y + .35, candidate.x + .35 + w, candidate.y + .35 + h, .06, 3).map(([cx, cz]) => [cx, landing + .004, cz]);
      this.polygon(cell, css(color, .33));
      this.outline(cell, css(color), px * .04);
    }
    const x = frame[0] - grab[0], z = frame[1] - grab[1];
    this.shadow(roundedOutline(x + .025, z + .025, x + w - .025, z + h - .03, .07, 3), landing, [.08, .14], .22, px * .16);
    this.tile(rules, piece, x, z, landing + DRAG_LIFT, textures, true);
    if (problem) this.outline(roundedOutline(x, z, x + w, z + h, .08, 3).map(([cx, cz]) => [cx, landing + DRAG_LIFT + TILE + .003, cz]), css(color), px * .04);
  }
  runEffects(rules, route, progress) {
    if (!route.length) return;
    const ctx = this.ctx, end = route.length - .0001, cursor = Math.min(Math.max(progress * route.length, 0), end), ground = TILE + .004;
    const world = t => { const c = Math.min(Math.max(t, 0), end), [x, y] = rules.position(route[Math.floor(c)], c % 1); return [x + .35, ground, y + .35]; };
    const px = this.camera.pixelsPerUnit([2.85, ground, 2.35]);
    const trace = (from, to) => {
      ctx.beginPath();
      for (let t = from, first = true; ; first = false) {
        const [x, y] = this.camera.project(world(t));
        if (first) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        if (t >= to) break;
        t = Math.min(to, t + .06);
      }
    };
    ctx.save(); ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    if (cursor < end) { trace(cursor, end); ctx.setLineDash([px * .06, px * .09]); ctx.strokeStyle = 'rgba(255,252,242,.84)'; ctx.lineWidth = px * .035; ctx.stroke(); ctx.setLineDash([]); }
    if (cursor > .02) for (const [style, width] of [['rgba(239,188,84,.41)', .2], ['rgba(226,164,52,.94)', .07], ['rgba(255,246,216,.86)', .022]]) { trace(0, cursor); ctx.strokeStyle = style; ctx.lineWidth = px * width; ctx.stroke(); }
    for (let k = 1; k <= 6; k++) {
      const back = cursor - k * .05;
      if (back <= 0) break;
      const [x, y] = this.camera.project(add(world(back), [0, .03 + k * .012, 0]));
      ctx.beginPath(); ctx.arc(x + Math.sin(k * 2.1 + cursor * 9) * px * .025, y, px * (.028 + k * .013), 0, Math.PI * 2);
      ctx.fillStyle = `rgba(205,172,122,${Math.max(0, 92 - k * 13) / 255})`; ctx.fill();
    }
    route.forEach((step, i) => {
      if (step.jump === null) return;
      const path = rules.road(step), at = path.jumpAt ?? .5;
      const crossing = i + (step.from === path.from ? at : 1 - at), t = (cursor - crossing) / .5;
      if (t < 0 || t > 1) return;
      const color = rgb(rules.pieces.get(step.p.id).color), [cx, cy] = this.camera.project(add(world(crossing), [0, .22, 0])), fade = 1 - t;
      ctx.beginPath(); ctx.arc(cx, cy, px * (.12 + .42 * t), 0, Math.PI * 2);
      ctx.strokeStyle = css(color, fade * .75); ctx.lineWidth = px * .035 * (1.2 - t); ctx.stroke();
      for (let n = 0; n < 8; n++) {
        const angle = n * Math.PI / 4 + i, reach = px * (.16 + .5 * t);
        ctx.beginPath(); ctx.arc(cx + Math.cos(angle) * reach, cy + Math.sin(angle) * reach * .7 - px * .12 * t, Math.max(.1, px * .035 * (1.1 - t)), 0, Math.PI * 2);
        ctx.fillStyle = n % 2 === 0 ? css(color, fade) : `rgba(255,226,140,${fade})`; ctx.fill();
      }
    });
    ctx.restore();
  }
}

export function parseHorse(buffer) {
  const view = new DataView(buffer);
  if (String.fromCharCode(...new Uint8Array(buffer, 0, 4)) !== 'HRS1') throw new Error('Not a baked horse');
  let offset = 4;
  const vertices = view.getUint16(offset, true), triangleCount = view.getUint16(offset + 2, true);
  const colorCount = view.getUint8(offset + 4), clipCount = view.getUint8(offset + 5);
  offset += 6;
  const colors = [];
  for (let i = 0; i < colorCount; i++, offset += 4) colors.push(view.getUint32(offset, true) & 0xffffff);
  const triangles = new Uint16Array(triangleCount * 3), materials = new Uint8Array(triangleCount);
  for (let t = 0; t < triangleCount; t++) {
    for (let c = 0; c < 3; c++, offset += 2) triangles[t * 3 + c] = view.getUint16(offset, true);
    materials[t] = view.getUint8(offset); offset += 1;
  }
  const clips = {};
  for (let c = 0; c < clipCount; c++) {
    const length = view.getUint8(offset); offset += 1;
    const name = String.fromCharCode(...new Uint8Array(buffer, offset, length)); offset += length;
    const frameCount = view.getUint16(offset, true), seconds = view.getFloat32(offset + 2, true); offset += 6;
    const origin = [view.getFloat32(offset, true), view.getFloat32(offset + 4, true), view.getFloat32(offset + 8, true)];
    const step = view.getFloat32(offset + 12, true); offset += 16;
    const frames = [];
    for (let f = 0; f < frameCount; f++) {
      const frame = new Float32Array(vertices * 3);
      for (let i = 0; i < vertices * 3; i++, offset += 2) frame[i] = origin[i % 3] + (view.getInt16(offset, true) + 32500) * step;
      frames.push(frame);
    }
    clips[name] = { seconds, frames };
  }
  const first = clips.Gallop.frames[0];
  let minZ = Infinity, maxZ = -Infinity;
  for (let v = 0; v < vertices; v++) { minZ = Math.min(minZ, first[v * 3 + 2]); maxZ = Math.max(maxZ, first[v * 3 + 2]); }
  return { vertices, triangleCount, triangles, materials, colors, clips, length: maxZ - minZ };
}

const HORSE_COLORS = [0xb8662f, 0x8c4622, 0xd38c50, 0x3a2a20, 0x3f2317, 0x5c3524, 0xf4eee2, 0x1a1410];
const smooth = x => x * x * (3 - 2 * x);
function sampleClip(clip, phase, out) {
  const n = clip.frames.length, at = ((phase % 1) + 1) % 1 * n, i = Math.floor(at);
  const a = clip.frames[i % n], b = clip.frames[(i + 1) % n], k = at - i;
  for (let j = 0; j < out.length; j++) out[j] = a[j] + (b[j] - a[j]) * k;
}

export class HorseRenderer {
  constructor(model) {
    this.model = model;
    const v = model.vertices;
    this.local = new Float32Array(v * 3); this.scratch = new Float32Array(v * 3); this.world = new Float32Array(v * 3);
    this.screen = new Float32Array(v * 2); this.shadowPoints = new Float32Array(v * 2);
    this.visible = new Uint16Array(model.triangleCount); this.depths = new Float32Array(model.triangleCount);
    this.shadowCanvas = null;
  }
  pose(stride, jumpPhase) {
    const { clips } = this.model;
    sampleClip(clips.Gallop, stride, this.local);
    if (jumpPhase === null) return;
    const jump = clips.Gallop_Jump;
    sampleClip(jump, (JUMP_FROM + (JUMP_TO - JUMP_FROM) * Math.min(Math.max(jumpPhase, 0), 1)) / jump.seconds, this.scratch);
    const w = smooth(Math.min(Math.max(jumpPhase / .14, 0), 1)) * smooth(Math.min(Math.max((1 - jumpPhase) / .14, 0), 1));
    for (let i = 0; i < this.local.length; i++) this.local[i] += (this.scratch[i] - this.local[i]) * w;
  }
  paint(painter, pose) {
    const { camera, ctx, dpr } = painter, model = this.model;
    this.pose(pose.stride, pose.jumpPhase);
    const k = HORSE_LENGTH / model.length, ground = TILE;
    const [fx, fz] = pose.direction, ox = pose.at[0] + .35, oz = pose.at[1] + .35, lift = pose.jump * RAIL_CLEARANCE;
    const { local, world, screen, shadowPoints } = this;
    let left = Infinity, top = Infinity, right = -Infinity, bottom = -Infinity;
    for (let v = 0; v < model.vertices; v++) {
      const mx = local[v * 3] * k, my = local[v * 3 + 1] * k, mz = local[v * 3 + 2] * k;
      const wx = ox + fx * mz + fz * mx, wy = ground + my + lift, wz = oz + fz * mz - fx * mx;
      world[v * 3] = wx; world[v * 3 + 1] = wy; world[v * 3 + 2] = wz;
      const q = camera.project([wx, wy, wz]); screen[v * 2] = q[0]; screen[v * 2 + 1] = q[1];
      const h = wy - ground, s = camera.project([wx + h * .12, ground, wz + h * .2]);
      shadowPoints[v * 2] = s[0]; shadowPoints[v * 2 + 1] = s[1];
      left = Math.min(left, q[0], s[0]); right = Math.max(right, q[0], s[0]); top = Math.min(top, q[1], s[1]); bottom = Math.max(bottom, q[1], s[1]);
    }
    const tri = model.triangles;
    left = Math.floor(left - 4); top = Math.floor(top - 4); right = Math.ceil(right + 4); bottom = Math.ceil(bottom + 4);
    const width = Math.max(1, Math.ceil((right - left) * dpr)), height = Math.max(1, Math.ceil((bottom - top) * dpr));
    if (!this.shadowCanvas || this.shadowCanvas.width < width || this.shadowCanvas.height < height) this.shadowCanvas = makeCanvas(Math.max(width, this.shadowCanvas?.width ?? 0) + 32, Math.max(height, this.shadowCanvas?.height ?? 0) + 32);
    const sctx = this.shadowCanvas.getContext('2d');
    sctx.setTransform(1, 0, 0, 1, 0, 0); sctx.clearRect(0, 0, width + 2, height + 2);
    sctx.setTransform(dpr, 0, 0, dpr, -left * dpr, -top * dpr);
    sctx.beginPath();
    for (let t = 0; t < model.triangleCount; t++) {
      const a = tri[t * 3] * 2, b = tri[t * 3 + 1] * 2, c = tri[t * 3 + 2] * 2;
      sctx.moveTo(shadowPoints[a], shadowPoints[a + 1]); sctx.lineTo(shadowPoints[b], shadowPoints[b + 1]); sctx.lineTo(shadowPoints[c], shadowPoints[c + 1]); sctx.closePath();
    }
    sctx.fillStyle = 'rgb(58,36,18)'; sctx.fill('nonzero');
    ctx.save();
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.globalAlpha = pose.jumpPhase !== null ? .2 : .28;
    ctx.drawImage(this.shadowCanvas, 0, 0, width, height, left * dpr, top * dpr, width, height);
    ctx.restore();
    let count = 0;
    const eye = camera.eye, forward = camera.forward;
    for (let t = 0; t < model.triangleCount; t++) {
      const a = tri[t * 3] * 3, b = tri[t * 3 + 1] * 3, c = tri[t * 3 + 2] * 3;
      const ux = world[b] - world[a], uy = world[b + 1] - world[a + 1], uz = world[b + 2] - world[a + 2];
      const vx = world[c] - world[a], vy = world[c + 1] - world[a + 1], vz = world[c + 2] - world[a + 2];
      const nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
      const mx = (world[a] + world[b] + world[c]) / 3, my = (world[a + 1] + world[b + 1] + world[c + 1]) / 3, mz = (world[a + 2] + world[b + 2] + world[c + 2]) / 3;
      if (nx * (eye[0] - mx) + ny * (eye[1] - my) + nz * (eye[2] - mz) <= 0) continue;
      this.depths[t] = (mx - eye[0]) * forward[0] + (my - eye[1]) * forward[1] + (mz - eye[2]) * forward[2];
      this.visible[count++] = t;
    }
    const order = Array.from(this.visible.subarray(0, count)).sort((m, n) => this.depths[n] - this.depths[m]);
    ctx.save();
    ctx.lineJoin = 'round'; ctx.lineWidth = .6;
    for (const t of order) {
      const a = tri[t * 3], b = tri[t * 3 + 1], c = tri[t * 3 + 2];
      const ux = world[b * 3] - world[a * 3], uy = world[b * 3 + 1] - world[a * 3 + 1], uz = world[b * 3 + 2] - world[a * 3 + 2];
      const vx = world[c * 3] - world[a * 3], vy = world[c * 3 + 1] - world[a * 3 + 1], vz = world[c * 3 + 2] - world[a * 3 + 2];
      const normal = normalize([uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx]);
      const style = shade(HORSE_COLORS[model.materials[t]] ?? model.colors[model.materials[t]], normal, .44);
      ctx.beginPath();
      ctx.moveTo(screen[a * 2], screen[a * 2 + 1]); ctx.lineTo(screen[b * 2], screen[b * 2 + 1]); ctx.lineTo(screen[c * 2], screen[c * 2 + 1]); ctx.closePath();
      ctx.fillStyle = style; ctx.strokeStyle = style; ctx.fill(); ctx.stroke();
    }
    ctx.restore();
  }
}

export function renderBoard(canvas, camera, dpr, state, textures, fence, ghost) {
  const ctx = canvas.getContext('2d');
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const painter = new Painter(camera, ctx, dpr);
  painter.reset();
  painter.board(state, textures, fence, ghost);
}
export function renderOverlay(canvas, camera, dpr, state, textures, horse, drag) {
  const ctx = canvas.getContext('2d');
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const painter = new Painter(camera, ctx, dpr);
  painter.reset();
  if (drag) painter.dragPreview(state, drag.piece, drag.frame, drag.grab, textures, drag.problem);
  if (state.running) {
    painter.runEffects(state.rules, state.activeRoute, state.runProgress);
    const pose = horsePose(state.rules, state.activeRoute, state.runProgress);
    if (pose && horse) horse.paint(painter, pose);
  }
}
export const signScreenCenter = (camera, pt) => {
  const [x, z] = entryCenter(pt);
  return camera.project(add([x, SIGN_BOTTOM, z], mul(camera.up, (SIGN_TOP - SIGN_BOTTOM) / 2)));
};
