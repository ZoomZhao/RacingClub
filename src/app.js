import {
  createRules, createStore, GameSession, clock, crossingFeedback, numberFace, numberInk, readableInk,
  pressTile, releaseBoardTap, releaseRackTap, rackPiece, dropCandidate, overFrame, onEntryLetter, fenceLayout,
  turnActions, AUTO_RUN_DELAY, RUN_MS_PER_STEP,
} from './rules.js';
import { Camera, TILE, createTextures, paintTrayTile, renderBoard, renderOverlay, parseHorse, HorseRenderer, signScreenCenter } from './scene.js';

const ICONS = {
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  forward: 'M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z',
  check: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  clock: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z',
  undo: 'M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z',
  reset: 'M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
  hint: 'M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z',
  cw: 'M15.55 5.55L11 1v3.07C7.06 4.56 4 7.92 4 12s3.05 7.44 7 7.93v-2.02c-2.84-.48-5-2.94-5-5.91s2.16-5.43 5-5.91V10l4.55-4.45zM19.93 11c-.17-1.39-.72-2.73-1.62-3.89l-1.42 1.42c.54.75.88 1.6 1.02 2.47h2.02zM13 17.9v2.02c1.39-.17 2.74-.71 3.9-1.61l-1.44-1.44c-.75.54-1.59.89-2.46 1.03zm3.89-2.42l1.42 1.41c.9-1.16 1.45-2.5 1.62-3.89h-2.02c-.14.87-.48 1.72-1.02 2.48z',
  ccw: 'M7.11 8.53L5.7 7.11C4.8 8.27 4.24 9.61 4.07 11h2.02c.14-.87.49-1.72 1.02-2.47zM6.09 13H4.07c.17 1.39.72 2.73 1.62 3.89l1.41-1.42c-.52-.75-.87-1.59-1.01-2.47zm1.01 5.32c1.16.9 2.51 1.44 3.9 1.61V17.9c-.87-.15-1.71-.49-2.46-1.03L7.1 18.32zM13 4.07V1L8.45 5.55 13 10V6.09c2.84.48 5 2.94 5 5.91s-2.16 5.43-5 5.91v2.02c3.95-.49 7-3.85 7-7.93s-3.05-7.44-7-7.93z',
  remove: 'M20.55 5.22l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.15.55L3.46 5.22C3.17 5.57 3 6.01 3 6.5V19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.49-.17-.93-.45-1.28zM12 9.5l5.5 5.5H14v2h-4v-2H6.5L12 9.5zM5.12 5l.82-1h12l.93 1H5.12z',
  skip: 'M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z',
  trophy: 'M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94.63 1.5 1.98 2.63 3.61 2.96V19H7v2h10v-2h-4v-3.1c1.63-.33 2.98-1.46 3.61-2.96C19.08 12.63 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z',
  settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  help: 'M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-2.21 0-4 1.79-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5 0-2.21-1.79-4-4-4z',
  star: 'M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z',
  layers: 'M11.99 18.54l-7.37-5.73L3 14.07l9 7 9-7-1.63-1.27-7.38 5.74zM12 16l7.36-5.73L21 9l-9-7-9 7 1.63 1.27L12 16z',
  info: 'M11 7h2v2h-2zm0 4h2v6h-2zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z',
  close: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
};
const icon = (name, className = 'icon') => `<svg class="${className}" viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[name]}"/></svg>`;
const escapeHtml = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const ROMAN = ['I', 'II', 'III', 'IV', 'V'];
const PANEL = 0xfffbf3, WELL = 0xf4ead6, PAPER = 0xf8f5ed;
const SLOP = 12;
const HELP = [
  '看路线顺序：马儿要从起点出发，按顺序跃过每个编号的障碍，最后到达终点。',
  '点拼板选中，再点一次就旋转 90°；托盘里的拼板也能先转好再放。选中托盘拼板后点空棋格即可放下，也可以直接把拼板拖进棋盘或在棋盘上拖动。',
  '操作栏固定在棋盘下方：旋转、移回、撤销、提示、重置都在这里，不会遮住棋格。点空棋格或木框取消选择。',
  '棋盘内允许临时叠放，后放的在上面；点托盘里的编号可把下层拼板置顶。消除所有重叠后才会判定通关。',
  '岔路须顺着道路平顺转向，不能在两个分支之间锐角转弯。7 号的两条路互不相通。经过无障碍支路不算跨障，同一障碍可能再次跨越。',
  '第 65–80 关还要点入口字母设置起点和终点：绿色箭头指向起点，棋格标记代表终点，两端可以相同。',
  '电脑上可用键盘：数字键选拼板，R / E 顺时针、Q 逆时针，方向键移动，Delete 移回，Ctrl+Z 撤销，H 提示。',
];

const app = document.querySelector('#app');
const announcer = document.querySelector('#announcement');
const dialog = document.createElement('dialog');
dialog.className = 'club-dialog';
document.body.append(dialog);

function announce(text) { if (announcer) { announcer.textContent = ''; requestAnimationFrame(() => { announcer.textContent = text; }); } }
function loadImage(src) { return new Promise((resolve, reject) => { const image = new Image(); image.onload = () => resolve(image); image.onerror = reject; image.src = src; }); }

app.innerHTML = `<div class="loading"><div class="loading-mark">♞</div><p>正在布置训练场…</p></div>`;

const [data, wood, sand, horseBuffer] = await Promise.all([
  fetch('./shared/game-data.json').then(r => r.json()),
  loadImage('./public/assets/web/wood.jpg'),
  loadImage('./public/assets/web/sand.jpg'),
  fetch('./public/assets/web/horse.bin').then(r => r.arrayBuffer()),
]);
let storage = null;
try { storage = window.localStorage; } catch { }
const rules = createRules(data);
const store = createStore(storage);
const session = new GameSession(rules, store);
const materials = { wood, sand };
const textures = createTextures(rules, materials);
const fence = fenceLayout(rules);
const horse = new HorseRenderer(parseHorse(horseBuffer));

const view = { screen: null, level: null, camera: null, dpr: 1, boardDirty: true, gesture: null, drag: null, autoRun: null, lastFrame: 0, crossed: -1, confetti: null };

function routeLevel() {
  const match = location.hash.match(/^#\/level\/(\d+)/);
  return match ? rules.levels.find(l => l.id === Number(match[1])) ?? null : null;
}
function openLevel(level) {
  if (routeLevel()?.id === level.id) session.start(level);
  else location.hash = `#/level/${level.id}`;
}
function goHome() { if (location.hash && location.hash !== '#/') location.hash = '#/'; else session.home(); }
window.addEventListener('hashchange', () => {
  const level = routeLevel();
  if (level) { if (session.screen === 'home' || session.level.id !== level.id) session.start(level); }
  else if (session.screen !== 'home') session.home();
});
document.addEventListener('visibilitychange', () => { if (document.hidden) session.enterBackground(); else session.enterForeground(); });

function nextLevel() {
  const unfinished = rules.levels.find(l => session.inProgress(l.id));
  return unfinished ?? rules.levels.find(l => !session.solved(l.id)) ?? rules.levels[0];
}
function difficultyInk(d) { return readableInk(d.color, PANEL, WELL); }

function renderHome() {
  const solved = rules.levels.filter(l => session.solved(l.id)).length;
  const next = nextLevel();
  const continuing = session.inProgress(next.id);
  const active = rules.difficulties.find(d => d.id === session.selectedDifficultyId) ?? rules.difficulties[0];
  const levels = rules.levels.filter(l => l.difficultyId === active.id);
  const solvedHere = levels.filter(l => session.solved(l.id)).length;
  const tabs = rules.difficulties.map((d, i) => {
    const on = d.id === active.id;
    return `<button class="tab${on ? ' is-active' : ''}" role="tab" aria-selected="${on}" data-difficulty="${d.id}" style="--tint:${d.color};--ink:${difficultyInk(d)}">
      <span class="tab-roman">${ROMAN[i]}</span><span class="tab-name">${d.label}</span><span class="tab-range">${String(d.firstLevel).padStart(2, '0')}–${d.lastLevel}</span></button>`;
  }).join('');
  const cards = levels.map(l => {
    const done = session.solved(l.id), progress = !done && session.inProgress(l.id), best = session.best(l.id);
    const detail = best ? clock(best.millis) : `${l.requiredPieces.length} 块 · ${l.sequence.length} 跨`;
    const status = done ? `<span class="status done">${icon('check', 'icon tiny')}已完成</span><span class="detail">${best ? detail : ''}</span>`
      : progress ? `<span class="status progress">${icon('clock', 'icon tiny')}进行中</span><span class="detail">${detail}</span>`
        : `<span class="detail">${detail}</span>`;
    return `<button class="level-card${done ? ' is-done' : ''}${progress ? ' is-progress' : ''}" data-level="${l.id}" style="--tint:${active.color}" aria-label="进入挑战 ${l.number}，${done ? '已完成' : progress ? '进行中' : '未开始'}">
      <span class="level-number">${l.number}</span><span class="level-meta">${status}</span></button>`;
  }).join('');
  app.innerHTML = `<main class="home">
    <header class="topbar">
      <div class="brand"><span class="brand-mark" aria-hidden="true">♞</span><span class="brand-text"><b>RacingClub</b><small>马术训练场</small></span></div>
      <span class="pill" aria-label="已完成 ${solved} / 80">${icon('trophy', 'icon tiny')}${solved} / ${rules.levels.length}<span class="pill-word"> 已完成</span></span>
      <button class="icon-button" data-action="settings" aria-label="设置">${icon('settings')}</button>
    </header>
    <div class="home-body">
      <section class="hero" aria-label="继续训练">
        <div class="hero-copy">
          <p class="eyebrow">今日训练 · ${escapeHtml(session.difficulty(next)?.label ?? '')}</p>
          <h1>今天，<br>跃过新的挑战</h1>
          <p class="hero-text">转动拼板连通道路，让马儿按顺序跃过每一道障碍。</p>
          <button class="button primary hero-button" data-level="${next.id}">${continuing ? '继续挑战' : solved ? '挑战' : '开始'} ${next.number}${icon('forward')}</button>
        </div>
      </section>
      <section class="panel levels-panel" aria-label="关卡">
        <div class="tabs" role="tablist">${tabs}</div>
        <div class="progress-row"><span class="progress-title" style="color:${difficultyInk(active)}">${active.label} · ${active.originalLabel}</span><span class="progress-count">${solvedHere} / ${levels.length} 已完成</span></div>
        <div class="track" aria-hidden="true"><i style="width:${solvedHere / levels.length * 100}%;background:${active.color}"></i></div>
        <div class="level-grid">${cards}</div>
      </section>
    </div>
  </main>`;
}

function buildGame() {
  app.innerHTML = `<main class="game">
    <header class="game-head card">
      <button class="icon-button" data-action="home" aria-label="返回">${icon('back')}</button>
      <div class="game-title"></div>
      <div class="head-actions">
        <button class="icon-button" data-action="rank" aria-label="本关排行榜">${icon('trophy')}</button>
        <button class="icon-button" data-action="settings" aria-label="设置">${icon('settings')}</button>
        <button class="icon-button" data-action="help" aria-label="玩法">${icon('help')}</button>
      </div>
    </header>
    <section class="route card" aria-label="路线顺序"></section>
    <section class="stage" aria-label="棋盘">
      <canvas class="board-canvas" role="img" aria-label="棋盘"></canvas>
      <canvas class="overlay-canvas" aria-hidden="true"></canvas>
      <div class="clock-chip" aria-label="用时">${icon('clock', 'icon tiny')}<span class="clock-text">00:00.00</span></div>
    </section>
    <section class="dock card" aria-label="棋盘操作"></section>
    <section class="tray card" aria-label="本关拼板"></section>
  </main>`;
  const stage = app.querySelector('.stage');
  const board = stage.querySelector('.board-canvas');
  board.addEventListener('pointerdown', boardPointerDown);
  new ResizeObserver(() => layoutBoard()).observe(stage);
  layoutBoard();
}

function layoutBoard() {
  const stage = app.querySelector('.stage');
  if (!stage) return;
  const rect = stage.getBoundingClientRect();
  const dpr = Math.min(window.devicePixelRatio || 1, 2.5);
  const width = Math.max(1, Math.round(rect.width)), height = Math.max(1, Math.round(rect.height));
  for (const canvas of stage.querySelectorAll('canvas')) {
    canvas.width = Math.round(width * dpr); canvas.height = Math.round(height * dpr);
    canvas.style.width = `${width}px`; canvas.style.height = `${height}px`;
  }
  const chip = stage.querySelector('.clock-chip');
  view.camera = new Camera(width, height, { topInset: chip ? chip.offsetHeight + 10 : 0 });
  view.dpr = dpr;
  view.boardDirty = true;
  drawOverlay();
  renderRoute();
}

function renderGameHead() {
  const level = session.level, d = session.difficulty();
  app.querySelector('.game-title').innerHTML = `<small>挑战</small><b>${level.number}</b><span class="badge" style="color:${readableInk(d.color, WELL)}">${d.label}</span>`;
  for (const button of app.querySelectorAll('.head-actions button, .game-head [data-action=home]')) button.disabled = session.running && button.dataset.action !== 'home' && button.dataset.action !== 'help';
}

function badge(id, extra = '', label = '') {
  return `<span class="num ${extra}" style="--face:${numberFace(id)};--ink:${numberInk(id)}"${label ? ` aria-label="${label}"` : ''}>${id}</span>`;
}
function renderRoute() {
  const host = app.querySelector('.route');
  if (!host) return;
  const level = session.level, crossed = session.running ? crossingFeedback(rules, session.activeRoute, session.runProgress).crossed : -1;
  const endpoint = (letter, finish) => {
    const pick = level.solveEndpoints && !session.running;
    const content = `<span class="endpoint-letter">${letter || '?'}</span><span class="endpoint-word">${finish ? '终点' : '起点'}</span>`;
    return pick ? `<button class="endpoint ${finish ? 'finish' : 'start'} pickable" data-action="${finish ? 'finish' : 'start'}" aria-label="选择${finish ? '终点' : '起点'}，当前 ${letter || '未设置'}">${content}</button>`
      : `<span class="endpoint ${finish ? 'finish' : 'start'}" aria-label="${finish ? '终点' : '起点'} ${letter}">${content}</span>`;
  };
  const nodes = [endpoint(session.startGate, false)];
  level.sequence.forEach((id, i) => {
    const state = !session.running ? '' : i < crossed ? 'passed' : i === crossed ? 'active' : '';
    nodes.push(badge(id, state, `目标 ${i + 1}：${id} 号${state === 'passed' ? '，已跨越' : ''}`));
  });
  nodes.push(endpoint(session.finishGate, true));
  host.innerHTML = `<div class="section-label"><i></i><span>路线顺序</span><em>${level.sequence.length} 次跨越${level.solveEndpoints ? ' · 自选起终点' : ''}</em></div>
    <ol class="course">${nodes.map((node, i) => `<li>${node}${i < nodes.length - 1 ? '<span class="arrow" aria-hidden="true">›</span>' : ''}</li>`).join('')}</ol>`;
  view.crossed = crossed;
}

function dockButton(action, iconName, label, description = label, disabled = false) {
  return `<button class="dock-button${action === 'hint' ? ' hint' : ''}" data-action="${action}" aria-label="${description}"${disabled ? ' disabled' : ''}>${icon(iconName)}<span>${label}</span></button>`;
}
function renderDock() {
  const host = app.querySelector('.dock');
  if (session.running) {
    host.innerHTML = `<button class="skip-button" data-action="skip">${icon('skip')}<span>跳过动画</span></button>
      <div class="run-meter"><div class="meter-line">${icon('trophy', 'icon tiny gold')}<b class="meter-count"></b><span class="meter-cheer"></span></div><div class="meter-track"><i></i></div></div>`;
    updateMeter();
    return;
  }
  const id = session.selected, placed = id !== null && session.placed.some(p => p.id === id);
  const left = id === null
    ? `<div class="dock-empty">${icon('info', 'icon tiny')}<span>点拼板选择 · 再点一次旋转</span></div>`
    : `<div class="dock-selected" aria-label="已选 ${id} 号">${badge(id, 'large')}</div>` + turnActions(session.turnMode).map(action =>
      action === -1 ? dockButton('ccw', 'ccw', '逆时针', '逆时针旋转 90 度') : action === 1 ? dockButton('cw', 'cw', '顺时针', '顺时针旋转 90 度') : placed ? dockButton('remove', 'remove', '移回', '移回托盘') : '').join('');
  host.innerHTML = `<div class="dock-group piece-actions">${left}</div><span class="dock-divider" aria-hidden="true"></span>
    <div class="dock-group">${dockButton('undo', 'undo', '撤销', '撤销', !session.history.length)}${dockButton('hint', 'hint', '提示')}${dockButton('reset', 'reset', '重置', '重置本关')}</div>`;
}
function updateMeter() {
  const host = app.querySelector('.run-meter');
  if (!host) return;
  const total = session.activeRoute.filter(step => step.jump !== null).length;
  const { crossed } = crossingFeedback(rules, session.activeRoute, session.runProgress);
  host.querySelector('.meter-count').textContent = `已跨越 ${crossed} / ${total}`;
  host.querySelector('.meter-cheer').textContent = crossed >= total ? '冲向终点！' : '加油，继续跳！';
  host.querySelector('.meter-track i').style.width = `${Math.min(session.runProgress, 1) * 100}%`;
}

function feedbackText() {
  if (view.drag) {
    if (view.drag.problem) return { error: true, text: view.drag.problem };
    const candidate = dropCandidate(view.drag.piece, view.drag.frame, view.drag.grab);
    const ids = session.placed.filter(p => rules.overlaps(candidate, p)).map(p => p.id).sort((a, b) => a - b);
    return { error: false, text: ids.length ? `将叠放在 ${ids.join('、')} 号上方` : '' };
  }
  if (session.message) return { error: true, text: session.message };
  if (!session.running && session.endpointRoutes().length > 1) return { error: false, text: '这条路线有多种起终点，点字母木牌选择起点和终点' };
  const covered = session.coveredCount();
  return { error: false, text: covered ? `${covered} 块被遮挡 · 点托盘编号置顶` : '' };
}
function renderFeedback() {
  const host = app.querySelector('.feedback');
  if (!host) return;
  const { error, text } = feedbackText();
  host.className = `feedback${error ? ' is-error' : ''}`;
  host.innerHTML = text ? `${error ? icon('info', 'icon tiny') : icon('layers', 'icon tiny')}<span>${escapeHtml(text)}</span>` : '';
}
function renderTray() {
  const host = app.querySelector('.tray');
  const needed = session.level.requiredPieces;
  const placedCount = needed.filter(id => session.placed.some(p => p.id === id)).length;
  host.innerHTML = `<div class="section-label"><i></i><span>本关拼板</span><em>放置 ${placedCount} / ${needed.length}</em></div>
    <div class="tray-grid" style="--count:${needed.length}">${needed.map(id => {
      const placed = session.placed.find(p => p.id === id), covered = placed && rules.coveredBy(placed, session.placed).length > 0;
      const active = session.selected === id;
      const r = placed ? placed.r : active ? session.rotation : 0;
      const state = covered ? '被遮挡' : placed ? '已放置' : '待放置';
      return `<button class="piece-card${active ? ' is-active' : ''}${placed ? ' is-placed' : ''}${covered ? ' is-covered' : ''}" data-piece="${id}" data-turn="${r}"
        aria-label="拼板 ${id}，${state}，${r * 90} 度${active ? '，已选中' : ''}" aria-pressed="${active}">
        <canvas aria-hidden="true"></canvas>${placed ? `<span class="tag" title="${covered ? '被遮挡' : '已放置'}">${covered ? icon('layers', 'icon tiny') : icon('check', 'icon tiny')}<span>${covered ? '被遮挡' : '已放置'}</span></span>` : ''}</button>`;
    }).join('')}</div>
    <p class="feedback" role="status" aria-live="polite"></p>`;
  for (const card of host.querySelectorAll('.piece-card')) {
    card.addEventListener('pointerdown', trayPointerDown);
    card.addEventListener('click', event => { if (event.detail === 0) { const id = Number(card.dataset.piece); releaseRackTap(session, id, pressTile(session, id)); } });
  }
  requestAnimationFrame(paintTray);
  renderFeedback();
}
function paintTray() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2.5);
  for (const card of app.querySelectorAll('.piece-card')) {
    const canvas = card.querySelector('canvas'), rect = canvas.getBoundingClientRect();
    if (!rect.width) continue;
    canvas.width = Math.round(rect.width * dpr); canvas.height = Math.round(rect.height * dpr);
    const id = Number(card.dataset.piece);
    paintTrayTile(canvas, rules, id, Number(card.dataset.turn), materials, card.classList.contains('is-active'), dpr);
  }
}

function renderGame() {
  if (view.screen !== 'game' || view.level !== session.level.id) buildGame();
  app.querySelector('.game').classList.toggle('is-running', session.running);
  renderGameHead(); renderRoute(); renderDock(); renderTray();
  const board = app.querySelector('.board-canvas');
  board.setAttribute('aria-label', session.placed.length ? `棋盘：${session.placed.map(p => `${p.id} 号，第 ${p.y + 1} 行第 ${p.x + 1} 列，${p.r * 90} 度`).join('；')}` : '空棋盘');
  view.boardDirty = true;
}

function rosette() {
  const petals = Array.from({ length: 22 }, (_, i) => { const a = i * Math.PI * 2 / 22; return `<circle cx="${100 + Math.cos(a) * 58}" cy="${84 + Math.sin(a) * 58}" r="12" fill="#d29a3c"/>`; }).join('');
  return `<svg viewBox="0 0 200 200" class="rosette-art" aria-hidden="true">
    <path d="M78 84h22l-2 104-20-14-20 10z" fill="#2f6b45"/><path d="M100 84h22l18 100-20-10-20 14z" fill="#24583a"/>
    ${petals}<circle cx="100" cy="84" r="60" fill="#efbc54"/><circle cx="100" cy="84" r="51" fill="none" stroke="#f6d48a" stroke-width="3"/>
    <circle cx="100" cy="84" r="44" fill="#254c3b"/><circle cx="100" cy="84" r="39" fill="none" stroke="#efbc54" stroke-width="1.5"/></svg>`;
}
function renderWin() {
  const summary = session.winSummary(), level = session.level, d = session.difficulty();
  const starIcons = [0, 1, 2].map(k => `<span class="star${k < summary.stars ? ' earned' : ''}" style="--delay:${380 + k * 170}ms">${icon('star')}</span>`).join('');
  app.innerHTML = `<main class="win">
    <section class="win-hero">
      <div class="rosette">${rosette()}<div class="rosette-label"><small>挑战</small><b>${level.number}</b></div></div>
      <p class="win-sub">${d.label} · 顺利完赛</p>
    </section>
    <section class="win-card card">
      <h1>漂亮，顺利通关！</h1>
      <div class="stars" aria-label="${summary.stars} 星">${starIcons}</div>
      <div class="win-time"><span class="time">${clock(session.elapsed)}</span>${summary.tag ? `<span class="tag-pill${summary.place === 1 ? ' gold' : ''}">${summary.tag}</span>` : ''}</div>
      <dl class="stats">
        <div><dt>步数</dt><dd>${session.moves}</dd></div>
        <div><dt>提示</dt><dd>${session.hints ? `${session.hints} 次` : '独立完成'}</dd></div>
        <div><dt>本关最佳</dt><dd>${clock(summary.records[0]?.millis ?? session.elapsed)}</dd></div>
      </dl>
      <div class="win-actions">
        <button class="button outline" data-action="again">${icon('reset')}再来一次</button>
        <button class="button primary" data-action="next">${summary.next ? '挑战下一关' : '返回训练场'}${icon('forward')}</button>
      </div>
      <div class="win-links"><button class="link-button" data-action="rank">${icon('trophy', 'icon tiny gold')}查看本关排行榜</button><button class="link-button" data-action="home">${icon('back', 'icon tiny')}返回关卡列表</button></div>
    </section>
    <canvas class="confetti" aria-hidden="true"></canvas>
  </main>`;
  view.confetti = { start: performance.now() };
  announce(`挑战 ${level.number} 通关，用时 ${clock(session.elapsed)}，${summary.stars} 星`);
}
function drawConfetti(now) {
  const canvas = app.querySelector('.confetti');
  if (!canvas || !view.confetti) return;
  const t = (now - view.confetti.start) / 2600;
  const rect = canvas.getBoundingClientRect(), dpr = Math.min(window.devicePixelRatio || 1, 2);
  if (canvas.width !== Math.round(rect.width * dpr)) { canvas.width = Math.round(rect.width * dpr); canvas.height = Math.round(rect.height * dpr); }
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, rect.width, rect.height);
  if (t >= 1 || matchMedia('(prefers-reduced-motion: reduce)').matches) { view.confetti = null; return; }
  const colors = ['#efbc54', '#2f6b45', '#c04835', '#3c74a8', '#a4c45a', '#b44c86'];
  let seed = 7;
  const random = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
  for (let i = 0; i < 90; i++) {
    const x = random() * rect.width, delay = random() * .35, speed = .75 + random() * .6, size = 6 + random() * 7;
    const k = Math.min(Math.max((t - delay) / (1 - delay), 0), 1);
    if (k <= 0) continue;
    const y = -20 + k * speed * rect.height * 1.1, sway = Math.sin(k * 9 + i) * 22;
    ctx.globalAlpha = 1 - Math.min(Math.max((k - .75) / .25, 0), 1);
    ctx.fillStyle = colors[i % colors.length];
    const h = size * (.4 + .4 * Math.abs(Math.sin(k * 12 + i)));
    ctx.fillRect(x + sway - size / 2, y - h / 2, size, h);
  }
  ctx.globalAlpha = 1;
}

function renderDialog() {
  const which = session.dialog;
  if (!which) { if (dialog.open) dialog.close(); return; }
  const level = session.level;
  let title = '', body = '', actions = '';
  if (which === 'help') {
    title = '一条路，刚刚好的顺序';
    body = HELP.map(p => `<p>${p}</p>`).join('');
    actions = `<button class="button primary" data-action="close">知道了</button>`;
  } else if (which === 'rank') {
    const records = store.scores(level.id);
    title = `挑战 ${level.number} · 本机排行榜`;
    body = records.length ? `<ol class="rank-list">${records.slice(0, 10).map((r, i) => `<li><span class="rank-place${i < 3 ? ` top${i + 1}` : ''}">${i + 1}</span><b>${clock(r.millis)}</b><span>${r.moves} 步</span><span class="rank-tag${r.hints ? '' : ' clean'}">${r.hints ? '使用提示' : '独立完成'}</span></li>`).join('')}</ol>`
      : `<p class="empty">还没有成绩，完成一次训练就会记录。</p>`;
    body += `<p class="note">成绩保存在本机浏览器里。</p>`;
    actions = `<button class="button primary" data-action="close">完成</button>`;
  } else if (which === 'settings') {
    title = '设置';
    const chip = (mode, label) => `<button class="chip${session.turnMode === mode ? ' is-on' : ''}" data-turn-mode="${mode}" aria-pressed="${session.turnMode === mode}">${label}</button>`;
    body = `<h3>旋转按钮</h3><p>决定棋盘下方显示哪些旋转按钮；再点一次拼板时也按这个方向转。双向时再点为顺时针。</p>
      <div class="chips">${chip('BOTH', '双向')}${chip('CLOCKWISE', '仅顺时针')}${chip('COUNTERCLOCKWISE', '仅逆时针')}</div>
      <h3>进度</h3><button class="button danger-outline" data-action="confirm-clear">清除所有进度</button>`;
    actions = `<button class="button primary" data-action="close">完成</button>`;
  } else if (which === 'confirm-clear') {
    title = '清除所有进度？';
    body = `<p>会清除全部分关进度、未完成的摆放和完成标记。本机最佳成绩会保留。此操作不能撤销。</p>`;
    actions = `<button class="button outline" data-action="close">取消</button><button class="button danger" data-action="clear">清除</button>`;
  } else if (which === 'confirm-reset') {
    title = '重置本关？';
    body = `<p>清空本关已摆放的拼板和起终点，重新开始这一关。本机最佳成绩会保留，也可以用撤销恢复。</p>`;
    actions = `<button class="button outline" data-action="close">取消</button><button class="button danger" data-action="reset-confirmed">确认重置</button>`;
  } else if (which === 'start' || which === 'finish') {
    const finish = which === 'finish', current = finish ? session.chosenFinish : session.chosenStart;
    title = finish ? '选择终点' : '选择起点';
    body = `<p>按字母选择棋盘边界，可与另一端相同。</p><div class="letters">${[...rules.gates.keys()].map(letter => `<button class="letter${letter === current ? ' is-on' : ''}" data-endpoint="${letter}" data-finish="${finish}">${letter}</button>`).join('')}</div>`;
    actions = `<button class="button outline" data-action="close">取消</button>`;
  } else if (which.startsWith('gate:')) {
    const letter = which.slice(5);
    title = `入口 ${letter}`;
    body = `<p>将 ${letter} 设为起点或终点；两端可以使用同一入口。</p>`;
    actions = `<button class="button outline" data-endpoint="${letter}" data-finish="false">设为起点</button><button class="button primary" data-endpoint="${letter}" data-finish="true">设为终点</button>`;
  }
  dialog.innerHTML = `<form method="dialog" class="dialog-body"><header><h2>${title}</h2><button class="icon-button" value="cancel" aria-label="关闭">${icon('close')}</button></header><div class="dialog-content">${body}</div><footer>${actions}</footer></form>`;
  if (!dialog.open) dialog.showModal();
}
dialog.addEventListener('close', () => { if (session.dialog) session.closeDialog(); });
dialog.addEventListener('click', event => {
  if (event.target === dialog) { dialog.close(); return; }
  const button = event.target.closest('button');
  if (!button) return;
  if (button.value === 'cancel') return;
  event.preventDefault();
  if (button.dataset.turnMode) { session.chooseTurnMode(button.dataset.turnMode); return; }
  if (button.dataset.endpoint) { session.chooseEndpoint(button.dataset.endpoint, button.dataset.finish === 'true'); return; }
  const action = button.dataset.action;
  if (action === 'close') session.closeDialog();
  else if (action === 'confirm-clear') session.showDialog('confirm-clear');
  else if (action === 'clear') { session.closeDialog(); session.clearAllProgress(); announce('已清除所有进度'); }
  else if (action === 'reset-confirmed') { session.closeDialog(); session.reset(); }
});

app.addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!button || button.disabled) return;
  if (button.dataset.difficulty) { session.chooseDifficulty(button.dataset.difficulty); return; }
  if (button.dataset.level) { openLevel(rules.levels.find(l => l.id === Number(button.dataset.level))); return; }
  const id = session.selected;
  switch (button.dataset.action) {
    case 'home': goHome(); break;
    case 'settings': case 'help': case 'rank': case 'start': case 'finish': session.showDialog(button.dataset.action); break;
    case 'ccw': if (id !== null) session.rotatePiece(id, -1); break;
    case 'cw': if (id !== null) session.rotatePiece(id, 1); break;
    case 'remove': if (id !== null) session.removePiece(id); break;
    case 'undo': session.undo(); break;
    case 'hint': session.hint(); break;
    case 'reset': session.showDialog('confirm-reset'); break;
    case 'skip': session.finishRun(); break;
    case 'again': session.start(session.level); break;
    case 'next': { const next = session.winSummary().next; if (next) openLevel(next); else goHome(); break; }
  }
});

function localPoint(event) {
  const rect = app.querySelector('.board-canvas').getBoundingClientRect();
  return [event.clientX - rect.left, event.clientY - rect.top];
}
function signAt(point) {
  if (!view.camera) return null;
  const radius = Math.max(22, view.camera.pixelsPerUnit([2.85, 0, 2.35]) * .3);
  for (const [name, pt] of rules.gates) {
    const [x, y] = signScreenCenter(view.camera, pt);
    if (Math.abs(point[0] - x) <= radius && Math.abs(point[1] - y) <= radius) return name;
  }
  return null;
}
function boardPointerDown(event) {
  if (session.running || view.gesture || !view.camera || event.button > 0) return;
  event.preventDefault();
  const point = localPoint(event), frame = view.camera.toFrame(point, TILE);
  const x = Math.floor(frame[0] - .35), y = Math.floor(frame[1] - .35);
  const hit = overFrame(frame) ? rules.hit(session.placed, x, y) : null;
  const sign = session.level.solveEndpoints ? signAt(point) : null;
  view.gesture = { kind: 'board', pointerId: event.pointerId, start: [event.clientX, event.clientY], moved: false, hit, x, y, frame, sign, wasSelected: false };
  if (hit && !sign) view.gesture.wasSelected = pressTile(session, hit.id);
}
function trayPointerDown(event) {
  if (session.running || view.gesture || event.button > 0) return;
  event.preventDefault();
  const id = Number(event.currentTarget.dataset.piece);
  const wasSelected = pressTile(session, id);
  view.gesture = { kind: 'tray', pointerId: event.pointerId, start: [event.clientX, event.clientY], moved: false, id, wasSelected };
}
function beginDrag(gesture, event) {
  if (gesture.kind === 'board') {
    if (!gesture.hit) return;
    const piece = session.placed.find(p => p.id === gesture.hit.id) ?? gesture.hit;
    view.drag = { piece, grab: [gesture.frame[0] - .35 - piece.x, gesture.frame[1] - .35 - piece.y], ghost: piece.id };
  } else {
    const piece = rackPiece(session, gesture.id), [w, h] = rules.dimensions(piece.r);
    view.drag = { piece, grab: [w / 2, h / 2], ghost: session.placed.some(p => p.id === piece.id) ? piece.id : null };
  }
  app.querySelector('.game')?.classList.add('is-dragging');
  updateDrag(event);
  view.boardDirty = true;
}
function updateDrag(event) {
  const drag = view.drag;
  drag.frame = view.camera.toFrame(localPoint(event), TILE);
  drag.over = overFrame(drag.frame);
  drag.problem = !drag.over ? '拖入棋盘内放置' : rules.fits(dropCandidate(drag.piece, drag.frame, drag.grab)) ? '' : '超出棋盘边界';
  renderFeedback();
  drawOverlay();
}
function endGesture(event, cancelled = false) {
  const gesture = view.gesture;
  view.gesture = null;
  const drag = view.drag;
  if (drag) {
    view.drag = null;
    app.querySelector('.game')?.classList.remove('is-dragging');
    if (!cancelled) {
      const candidate = dropCandidate(drag.piece, drag.frame, drag.grab);
      if (drag.problem) { session.message = drag.over ? `${drag.problem}，已保留原位` : '未放入棋盘，已保留原位'; session.changed(); }
      else {
        const current = session.placed.find(p => p.id === candidate.id);
        if (!current || current.x !== candidate.x || current.y !== candidate.y || current.r !== candidate.r) session.placePiece(candidate);
        else session.changed();
      }
    } else session.changed();
    drawOverlay();
    return;
  }
  if (cancelled || gesture.moved) return;
  if (gesture.kind === 'tray') releaseRackTap(session, gesture.id, gesture.wasSelected);
  else if (gesture.sign && !session.running) session.showDialog(`gate:${gesture.sign}`);
  else releaseBoardTap(session, gesture.hit, gesture.wasSelected, gesture.x, gesture.y, onEntryLetter(rules, gesture.frame[0], gesture.frame[1]));
}
window.addEventListener('pointermove', event => {
  const gesture = view.gesture;
  if (!gesture || event.pointerId !== gesture.pointerId) return;
  if (!gesture.moved && Math.hypot(event.clientX - gesture.start[0], event.clientY - gesture.start[1]) > SLOP) {
    gesture.moved = true;
    if (!session.running) beginDrag(gesture, event);
  }
  if (view.drag) { event.preventDefault(); updateDrag(event); }
}, { passive: false });
window.addEventListener('pointerup', event => { if (view.gesture && event.pointerId === view.gesture.pointerId) endGesture(event); });
window.addEventListener('pointercancel', event => { if (view.gesture && event.pointerId === view.gesture.pointerId) endGesture(event, true); });

document.addEventListener('keydown', event => {
  if (session.screen !== 'game' || session.dialog || session.running || event.altKey) return;
  if (event.target.closest?.('input, textarea')) return;
  const id = session.selected, key = event.key;
  const command = event.metaKey || event.ctrlKey;
  if (command && key.toLowerCase() === 'z') { session.undo(); event.preventDefault(); return; }
  if (command) return;
  const pieces = session.level.requiredPieces;
  const digit = key === '0' ? 10 : Number(key);
  if (Number.isInteger(digit) && digit >= 1 && pieces.includes(digit)) { session.select(digit); event.preventDefault(); return; }
  if (id === null) return;
  const placed = session.placed.some(p => p.id === id);
  const moves = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
  if (moves[key] && placed) { session.movePiece(id, ...moves[key]); event.preventDefault(); }
  else if (key === 'r' || key === 'e' || key === 'R' || key === 'E') session.rotatePiece(id, 1);
  else if (key === 'q' || key === 'Q') session.rotatePiece(id, -1);
  else if ((key === 'Delete' || key === 'Backspace') && placed) { session.removePiece(id); event.preventDefault(); }
  else if (key === 'h' || key === 'H') session.hint();
  else if (key === 'Enter' && !placed) {
    for (let y = 0; y < 4; y++) for (let x = 0; x < 5; x++) if (!rules.hit(session.placed, x, y) && rules.fits(rules.initialPlacement({ id, x, y, r: session.rotation }))) { session.place(x, y); return; }
  }
});

function drawOverlay() {
  const canvas = app.querySelector('.overlay-canvas');
  if (!canvas || !view.camera) return;
  renderOverlay(canvas, view.camera, view.dpr, session, textures, horse, view.drag && view.drag.over ? view.drag : null);
}

function scheduleAutoRun() {
  clearTimeout(view.autoRun);
  if (!session.canAutoRun() || view.drag || !session.trace()) return;
  view.autoRun = setTimeout(() => {
    if (!view.drag && session.beginRunIfSolved()) { view.lastFrame = performance.now(); announce('路线正确！马儿出发了'); }
  }, AUTO_RUN_DELAY);
}

function render() {
  if (session.screen === 'home') { renderHome(); }
  else if (session.screen === 'game') renderGame();
  else if (view.screen !== 'win' || view.level !== session.level.id) renderWin();
  view.screen = session.screen; view.level = session.level.id;
  document.title = session.screen === 'home' ? 'RacingClub · 马术训练场' : `挑战 ${session.level.number} · RacingClub`;
  renderDialog();
  scheduleAutoRun();
}
session.subscribe(render);

function frame(now) {
  if (session.screen === 'game') {
    if (view.boardDirty && view.camera) {
      view.boardDirty = false;
      renderBoard(app.querySelector('.board-canvas'), view.camera, view.dpr, session, textures, fence, view.drag?.ghost ?? null);
    }
    const text = app.querySelector('.clock-text');
    if (text) text.textContent = clock(session.running ? session.elapsed : session.time());
    if (session.running) {
      if (session.foreground) session.runProgress = Math.min(1, session.runProgress + Math.min(now - view.lastFrame, 100) / (session.activeRoute.length * RUN_MS_PER_STEP));
      drawOverlay();
      updateMeter();
      const { crossed } = crossingFeedback(rules, session.activeRoute, session.runProgress);
      if (crossed !== view.crossed) renderRoute();
      if (session.runProgress >= 1) session.finishRun();
    }
  } else if (session.screen === 'win' && view.confetti) drawConfetti(now);
  view.lastFrame = now;
  requestAnimationFrame(frame);
}

const initial = routeLevel();
if (initial) session.start(initial); else render();
requestAnimationFrame(frame);
window.racingClub = { session, rules, view };
