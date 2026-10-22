import { COLS, ROWS, PIECES, GATES, LEVELS, INVENTORY, size, ports, rotatePoint, pathPoint, canPlace, validate, restorePlacements } from './game.js';

const app = document.querySelector('#app');
const STORE = 'racingclub:v1';
let saved = {};
try { saved = JSON.parse(localStorage.getItem(STORE)) || {}; } catch { /* A private browser can still play. */ }
const sessions = Object.fromEntries(LEVELS.map(level => {
  const record = saved[level.id] || {};
  return [level.id, { placements: restorePlacements(level, record.placements), moves: Number.isInteger(record.moves) && record.moves >= 0 ? record.moves : 0, completed: record.completed === true, elapsed: Number.isFinite(record.elapsed) && record.elapsed >= 0 ? record.elapsed : 0, hints: Number.isInteger(record.hints) && record.hints >= 0 ? record.hints : 0, finished: record.finished === true, history: [] }];
}));
let level = LEVELS.find(l => l.id === Number(location.hash.slice(1))) || LEVELS[0];
let screen = location.hash ? 'game' : 'home';
let selected = null;
let rotation = 0;
let status = '';
let statusType = 'neutral';
let busy = false;
let runToken = 0;
let drag = null;
let suppressClickUntil = 0;
let clockStarted = null;
let autoCheck = null;
const scores = Array.isArray(saved.__scores) ? saved.__scores.filter(s => LEVELS.some(l => l.id === s.level) && Number.isFinite(s.time) && s.time >= 0 && Number.isInteger(s.moves) && Number.isInteger(s.hints)).slice(-200) : [];

const icons = {
  back: '<path d="m14 6-6 6 6 6"/>',
  arrow: '<path d="M5 12h14m-5-5 5 5-5 5"/>',
  rotate: '<path d="M20 10a8 8 0 1 0-1 8M20 4v6h-6"/>',
  undo: '<path d="m9 4-5 5 5 5M4 9h9a7 7 0 0 1 0 14"/>',
  hint: '<path d="M9 18h6m-5 3h4M8 14a6 6 0 1 1 8 0c-1 1-1 2-1 2H9s0-1-1-2Z"/>',
  help: '<circle cx="12" cy="12" r="9"/><path d="M9 9a3 3 0 1 1 4 3c-1 .5-1 1-1 2m0 3h.01"/>',
  remove: '<path d="M5 7h14M9 7V4h6v3M7 7l1 14h8l1-14M10 11v6m4-6v6"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  reset: '<path d="M4 10a8 8 0 1 1 1 8M4 4v6h6"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  landscape: '<rect x="2" y="5" width="20" height="14" rx="3"/><path d="M18 11v2"/>',
  trophy: '<path d="M8 3h8v6a4 4 0 0 1-8 0V3Zm0 2H4v3a4 4 0 0 0 4 4m8-7h4v3a4 4 0 0 1-4 4m-4 1v6m-4 2h8m-6-2h4"/>',
  clock: '<circle cx="12" cy="13" r="8"/><path d="M12 9v5l3 2M9 2h6m-3 0v3"/>',
  inventory: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
};
const icon = (name, cls = '') => `<svg class="icon ${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name] || icons.arrow}</svg>`;
const horse = (cls = '') => `<svg class="horse-icon ${cls}" viewBox="0 0 80 72" aria-hidden="true"><path d="m23 32 10-12 2-14 9 10 6-8 5 15 15 12-4 10-16-2-4 12-2 13H33l-3-14-8 13H11l9-23-9 2-5-9 6-9 4 8Z" fill="currentColor"/><path d="m32 35 17 4-3 13-17-3Z" fill="#f6d389"/><circle cx="52" cy="27" r="2.2" fill="#fff7e8"/><path d="m20 42-5-10" fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round"/></svg>`;
const brand = () => `<a class="brand" href="#" data-action="home" aria-label="RacingClub 训练场">${horse()}<span>RacingClub<small>THE LITTLE EQUESTRIAN CLUB</small></span></a>`;
const badge = id => `<span class="number" style="--obstacle:${INVENTORY.find(p=>p.id===id).color}">${id}</span>`;
const routeStrip = l => `<div class="route-strip" aria-label="从 ${l.start} 出发，依次经过 ${l.sequence.join('、')} 号障碍，到达 ${l.finish}"><span class="gate-number">${l.start}</span>${l.sequence.map(id => `<span class="route-arrow" aria-hidden="true">→</span>${badge(id)}`).join('')}<span class="route-arrow" aria-hidden="true">→</span><span class="gate-number finish-number">${l.finish}</span></div>`;

function tileSVG(id, r = 0, active = false) {
  const piece = PIECES[id];
  const [w, h] = size(r);
  const transform = ['','translate(100 0) rotate(90)','translate(200 100) rotate(180)','translate(0 200) rotate(270)'][r];
  const [gx, gy, gr] = piece.gate;
  return `<svg viewBox="0 0 ${w * 100} ${h * 100}" class="tile-svg" aria-hidden="true"><g transform="${transform}"><rect x="2" y="2" width="196" height="96" rx="10" fill="#d7b68a" stroke="${active ? '#254c3b' : '#aa8255'}" stroke-width="3"/><path d="M10 14h180M10 86h180" stroke="#edd6ac" opacity=".55"/><path d="${piece.path}" fill="none" stroke="#a18055" stroke-width="44" opacity=".16"/><path d="${piece.path}" fill="none" stroke="#fff2d6" stroke-width="37"/><g transform="translate(${gx} ${gy}) rotate(${gr})"><path d="M0-25V25" stroke="#755039" stroke-width="10" opacity=".15" transform="translate(3 4)"/><path d="M0-25V25" stroke="${piece.color}" stroke-width="9"/><path d="M0-13v9m0 8v9" stroke="#fff9e9" stroke-width="9"/><rect x="-9" y="-30" width="18" height="9" rx="3" fill="${piece.color}"/><rect x="-9" y="21" width="18" height="9" rx="3" fill="${piece.color}"/></g></g></svg>`;
}

function persist() {
  try {
    localStorage.setItem(STORE, JSON.stringify({ ...Object.fromEntries(LEVELS.map(l => {
      const { placements, moves, completed, hints, finished } = sessions[l.id];
      const elapsed = sessions[l.id].elapsed + (l.id === level.id && clockStarted !== null ? performance.now() - clockStarted : 0);
      return [l.id, { placements, moves, completed, elapsed, hints, finished }];
    })), __scores: scores }));
  } catch { /* Storage is optional. */ }
}

const session = () => sessions[level.id];
function announce(message) { document.querySelector('#announcement').textContent = message; }
function setStatus(message, type = 'neutral') {
  status = message; statusType = type;
  const node = document.querySelector('#game-status');
  if (node) { node.textContent = message; node.className = `game-status ${type}`; }
  announce(message);
}
function remember() {
  session().history.push({ placements: structuredClone(session().placements), moves: session().moves, hints: session().hints });
  if (session().history.length > 60) session().history.shift();
}

function showHome() {
  stopClock(); clearTimeout(autoCheck); runToken++; busy = false; screen = 'home'; selected = null;
  history.replaceState(null, '', location.pathname + location.search);
  render();
}
function startLevel(id) {
  stopClock(); clearTimeout(autoCheck); runToken++; busy = false; level = LEVELS.find(l => l.id === id); screen = 'game';
  if (session().finished) Object.assign(session(), { placements: [], moves: 0, elapsed: 0, hints: 0, finished: false, history: [] });
  selected = level.sequence.find(id => !session().placements.some(p => p.id === id)) ?? null;
  rotation = 0; status = ''; statusType = 'neutral';
  history.replaceState(null, '', `#${id}`);
  startClock(); render(); persist();
}

function formatTime(ms, precise = false) {
  const seconds = Math.floor(ms / 1000);
  return `${String(Math.floor(seconds/60)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}${precise ? '.' + String(Math.floor(ms%1000/10)).padStart(2,'0') : ''}`;
}
function elapsedTime() { return session().elapsed + (clockStarted === null ? 0 : performance.now() - clockStarted); }
function startClock() { if (clockStarted === null && screen === 'game' && !busy && !document.hidden && !session().finished) clockStarted = performance.now(); }
function stopClock() { if (clockStarted !== null) { session().elapsed += performance.now() - clockStarted; clockStarted = null; persist(); } }
setInterval(() => { const el = document.querySelector('#elapsed-time'); if (el) el.textContent = formatTime(elapsedTime()); }, 200);
document.addEventListener('visibilitychange', () => document.hidden ? stopClock() : startClock());
window.addEventListener('pagehide', () => { stopClock(); persist(); });

function scheduleCheck() {
  clearTimeout(autoCheck);
  if (!busy && !session().finished && validate(level,session().placements).ok) autoCheck = setTimeout(() => run(), 300);
}

function render() {
  document.body.dataset.screen = screen;
  if (screen === 'home') renderHome();
  else if (screen === 'win') renderWin();
  else renderGame();
}

function renderHome() {
  const completed = LEVELS.filter(l => sessions[l.id].completed).length;
  const next = LEVELS.find(l => !sessions[l.id].completed) || LEVELS[0];
  app.innerHTML = `<main class="home-shell"><header class="home-header">${brand()}<button class="text-button" data-action="help">玩法 ${icon('help')}</button></header>
    <section class="home-layout"><div class="hero"><img src="/public/assets/horse-arena.png" alt="晨光下的栗色马儿与马术训练场" fetchpriority="high"><div class="hero-copy"><p class="eyebrow">A LITTLE THINKING. A LOVELY RIDE.</p><h1>今天，<br>跃过新的挑战<span class="sun-stroke"></span></h1><p>转动拼板，连起一条路。<br>让每一次跳跃，都恰到好处。</p></div><button class="primary hero-cta" data-level="${next.id}">继续挑战 ${String(next.id).padStart(2, '0')} ${icon('arrow')}</button></div>
    <div class="training"><div class="section-heading"><div><p class="eyebrow">YOUR TRAINING GROUND</p><h2>马术训练场</h2></div><span class="progress-pill">${completed} / 2 已完成</span></div><p class="section-description">不必着急，下一段好路线就在眼前。</p>
    <div class="challenge-list">${LEVELS.map(l => `<button class="challenge-card" data-level="${l.id}"><div class="challenge-top"><span class="challenge-name">挑战 <b>${String(l.id).padStart(2, '0')}</b></span><span class="level-pill ${sessions[l.id].completed ? 'done' : ''}">${sessions[l.id].completed ? '已通关 ✓' : '入门'}</span></div><h3>${l.title}</h3><div class="challenge-bottom">${routeStrip(l)}<span class="play-circle">${icon('arrow')}</span></div><span class="challenge-description">${l.sequence.length} 块拼板 · ${l.description}</span></button>`).join('')}</div>
    <div class="club-note"><span class="horseshoe">♧</span><div><strong>每一步，都有新发现</strong><p>先看顺序，再连路线。想一想，也可以试一试。</p></div></div><p class="prototype-note">照片关卡练习版 · 进度保存在此浏览器</p></div></section><footer class="home-footer"><span>RACINGCLUB</span><span>与马同行 · 每一步都是进步</span></footer></main>`;
}

function boardMarkup(l, placements, mini = false) {
  return `<div class="board-frame ${mini ? 'mini-board' : ''}" aria-label="5 列 4 行棋盘"><div class="fence-rail top"></div><div class="fence-rail bottom"></div><div class="fence-rail left"></div><div class="fence-rail right"></div>
    ${Object.entries(GATES).map(([letter, [x,y]]) => `<span class="board-gate ${letter === l.start ? 'start-gate' : ''} ${letter === l.finish ? 'end-gate' : ''}" style="--x:${x};--y:${y}" aria-label="${letter === l.start ? '起点' : letter === l.finish ? '终点' : '入口'} ${letter}">${letter}${letter === l.start ? '<small>起点</small>' : letter === l.finish ? '<small>终点</small>' : ''}</span>`).join('')}
    <div class="board-grid" id="${mini ? 'mini-grid' : 'board-grid'}">${Array.from({length: ROWS * COLS}, (_, i) => `<button class="board-cell" ${mini ? 'tabindex="-1"' : ''} data-cell="${i}" aria-label="第 ${Math.floor(i / COLS) + 1} 行第 ${i % COLS + 1} 列" ${mini ? 'disabled' : ''}></button>`).join('')}
    ${placements.map(p => { const [w,h] = size(p.r); return `<button class="placed-tile ${!mini && selected === p.id ? 'selected' : ''}" data-piece="${p.id}" style="left:${p.x * 20}%;top:${p.y * 25}%;width:${w * 20}%;height:${h * 25}%;--obstacle:${PIECES[p.id].color}" aria-label="${p.id} 号${PIECES[p.id].name}，第 ${p.y + 1} 行第 ${p.x + 1} 列，${p.r * 90} 度${selected === p.id ? '，已选中' : ''}" aria-pressed="${selected === p.id}" ${mini ? 'disabled tabindex="-1"' : ''}>${tileSVG(p.id,p.r,selected === p.id)}<span class="tile-id">${p.id}</span></button>`; }).join('')}
    ${mini ? '' : '<div id="placement-ghost" class="placement-ghost" hidden></div>'}<div class="horse-pawn ${mini ? 'small-pawn' : ''}" id="${mini ? 'mini-horse' : 'horse-pawn'}" aria-label="马儿"><img src="/public/assets/horse-pawn.png" alt=""><span class="horse-shadow"></span></div>
    <div class="finish-arch" style="left:${GATES[l.finish][0] * 20}%;top:${GATES[l.finish][1] * 25}%" aria-hidden="true"><i></i><i></i><b></b></div></div></div>`;
}

function renderGame() {
 const placed=session().placements.find(p=>p.id===selected); if(placed)rotation=placed.r;
 app.innerHTML=`<main class="game-shell"><header class="game-header"><button class="icon-button" data-action="home" aria-label="返回训练场">${icon('back')}</button><div class="game-title"><span>挑战 <b>${String(level.id).padStart(2,'0')}</b></span><span class="level-pill">入门</span></div><div class="header-route">${routeStrip(level)}</div><span class="game-timer">${icon('clock')}<span id="elapsed-time">${formatTime(elapsedTime())}</span></span><div class="compact-actions"><button class="icon-button" data-action="ranking" title="本关排行榜" aria-label="本关排行榜">${icon('trophy')}</button><button class="icon-button" data-action="help" aria-label="查看玩法">${icon('help')}</button></div></header><div class="portrait-route">${routeStrip(level)}</div><div class="landscape-note">${icon('landscape')} 横过手机，棋盘和工作台并排更顺手</div><div class="game-layout"><section class="arena"><div class="arena-heading"><h1>连起路线，跃向终点<span class="sun-stroke"></span></h1><span class="placed-count">${session().placements.length} / ${level.sequence.length} 已放置</span></div>${boardMarkup(level,session().placements)}<p class="board-caption">点选拼板后旋转或移动 · 正确连接后自动出发</p></section><aside class="workbench" aria-label="拼板工作台"><div class="workbench-heading"><strong style="font-size:12px">拼板</strong><div class="tool-grid">${[['rotate','rotate','旋转 90°',selected===null],['remove','remove','移回拼板区',!placed],['undo','undo','撤销',!session().history.length],['hint','hint','提示',false],['reset','reset','重新摆放',!session().placements.length]].map(([action,ico,label,disabled])=>`<button class="tool-button" data-action="${action}" title="${label}" aria-label="${label}" ${disabled?'disabled':''}>${icon(ico)}</button>`).join('')}<button class="tool-button" data-action="inventory" title="全部 10 块" aria-label="全部 10 块">${icon('inventory')}</button></div></div><div class="piece-rack">${[...level.sequence].sort((a,b)=>a-b).map(id=>{const onBoard=session().placements.some(p=>p.id===id);const r=selected===id?rotation:0;return `<button class="rack-piece ${selected===id?'selected':''} ${onBoard?'on-board':''}" data-rack="${id}" style="--obstacle:${PIECES[id].color}" aria-label="选择 ${id} 号${PIECES[id].name}${onBoard?'，已放置':''}" aria-pressed="${selected===id}"><span class="rack-label">${badge(id)}<small>${onBoard?'已放置':'待放置'}</small></span><span class="rack-tile">${tileSVG(id,r)}</span></button>`;}).join('')}</div><div class="selection-info"><span class="selection-dot" style="background:${selected?PIECES[selected].color:'#b9b1a1'}"></span><strong>${selected?`已选中 ${selected} 号拼板`:'选择一块拼板'}</strong><span>${selected?`${rotation*90}°`:''}</span></div><p id="game-status" class="game-status ${statusType}" role="status">${status||'选择拼板，再点棋盘左上格放置；也可以拖放。'}</p><p class="auto-note">${icon('check')} 路线正确后，马儿会自动出发</p></aside></div></main>`;
 positionHorseAtStart(); scheduleCheck();
}

function renderWin() {
  const next = LEVELS.find(l => l.id > level.id);
  app.innerHTML = `<main class="win-shell"><header class="home-header">${brand()}<span class="level-pill done">挑战 ${String(level.id).padStart(2,'0')} ✓</span></header><section class="win-layout"><div class="win-art"><img src="/public/assets/horse-arena.png" alt="完成训练的马儿"><div class="rosette"><span>♞</span></div></div><div class="win-content"><p class="eyebrow">A BEAUTIFUL CLEAR ROUND</p><h1>漂亮，<br>顺利通关！<span class="sun-stroke"></span></h1><p>每一道障碍，都跃得恰到好处。</p><div class="time-result">${formatTime(session().elapsed,true)}<small>完成用时</small></div><div class="result-row"><span>${icon('check')} 路线正确</span><span>${session().moves} 次摆放</span></div><div class="win-board">${boardMarkup(level, session().placements, true)}</div><button class="primary" ${next ? `data-level="${next.id}"` : 'data-action="home"'}>${next ? '挑战下一关' : '返回训练场'} ${icon('arrow')}</button><button class="text-button replay-button" data-action="replay">再看马儿跑一遍</button><button class="text-button" data-action="ranking">${icon('trophy')} 查看本关排行榜</button><button class="text-button" data-action="home">返回训练场</button></div></section></main>`;
  const pawn = document.querySelector('#mini-horse');
  if (pawn) { pawn.style.left = `${GATES[level.finish][0] * 20}%`; pawn.style.top = `${GATES[level.finish][1] * 25}%`; }
}

function selectPiece(id) {
  if (busy) return;
  selected = id;
  rotation = session().placements.find(p => p.id === id)?.r || 0;
  status = session().placements.some(p => p.id === id) ? `已选中 ${id} 号。点空格移动，或使用旋转按钮。` : `选中了 ${id} 号。点棋盘左上格放置，或先旋转。`;
  statusType = 'neutral'; renderGame();
  announce(status);
}

function placeAt(x,y,id = selected,r = rotation) {
  if (busy || id === null) { if (!busy) setStatus('先在工作台选择一块拼板。'); return; }
  const p = { id,x,y,r };
  if (!canPlace(p,session().placements)) {
    setStatus('这里放不下：拼板不能重叠，也不能超出棋盘。', 'error'); return;
  }
  remember();
  session().placements = [...session().placements.filter(p => p.id !== id), p];
  session().moves++; selected = id; rotation = r;
  status = `${id} 号已放好。点另一块继续，路线正确会自动出发。`; statusType = 'neutral';
  persist(); renderGame(); announce(status);
}

function rotateSelected() {
  if (selected === null || busy) return;
  const next = (rotation + 1) % 4;
  const existing = session().placements.find(p => p.id === selected);
  if (existing) {
    const rotated = { ...existing,r:next };
    if (!canPlace(rotated,session().placements)) { setStatus('旋转后会重叠或超出棋盘，已保留原位。可先移到空处。','error'); return; }
    remember(); session().placements = session().placements.map(p => p.id === selected ? rotated : p); session().moves++;
  }
  rotation = next; status = `${selected} 号已旋转至 ${next * 90}°。`; statusType = 'neutral';
  persist(); renderGame(); announce(status);
}

function showHelp() {
  if (document.querySelector('dialog')) return;
  stopClock(); clearTimeout(autoCheck);
  const dialog = document.createElement('dialog');
  dialog.className = 'help-dialog';
  dialog.innerHTML = `<div class="dialog-top"><p class="eyebrow">A LITTLE GUIDE</p><button class="icon-button" data-close aria-label="关闭玩法">${icon('close')}</button></div><h2>一条路，刚刚好的顺序</h2><p class="help-intro">让马儿从起点出发，按顺序跃过彩色障碍，最后抵达终点门。</p><ol class="help-steps"><li><b>01</b><div><strong>看看本关顺序</strong><p>上方圆圈是经过障碍的顺序，字母是起终点。</p></div></li><li><b>02</b><div><strong>选择、旋转、放置</strong><p>点工作台拼板，再点棋盘上的左上格放置。支持拖放；每块拼板占两格。</p></div></li><li><b>03</b><div><strong>已放好的，也能再调整</strong><p>点棋盘拼板，高亮后可旋转、点空格移动，或移回工作台。旋转不能重叠或越界。</p></div></li><li><b>04</b><div><strong>自动出发</strong><p>每次调整后自动检查。路线正确即停止计时，马儿会沿路奔跑和跳跃。</p></div></li></ol><p class="help-footnote">推荐横屏操作 · R 旋转 · Delete 移回 · Ctrl / ⌘ Z 撤销<br>当前提供照片中的挑战 03、04，两关均无岔路。</p><button class="primary" data-close>开始训练 ${icon('arrow')}</button>`;
  document.body.append(dialog); dialog.showModal();
  dialog.querySelectorAll('[data-close]').forEach(button => button.onclick = () => dialog.close());
  dialog.addEventListener('click', event => { if (event.target === dialog) { const b = dialog.getBoundingClientRect(); if (event.clientX < b.left || event.clientX > b.right || event.clientY < b.top || event.clientY > b.bottom) dialog.close(); } });
  dialog.addEventListener('close', () => { dialog.remove(); startClock(); if(screen==='game')scheduleCheck(); });
}

function popup(title, content) {
  stopClock(); clearTimeout(autoCheck);
  const d = document.createElement('dialog'); d.className = 'help-dialog';
  d.innerHTML = `<div class="dialog-top"><p class="eyebrow">RACINGCLUB</p><button class="icon-button" aria-label="关闭">${icon('close')}</button></div><h2>${title}</h2>${content}`;
  document.body.append(d); d.showModal(); d.querySelector('.icon-button').onclick = () => d.close();
  d.addEventListener('close', () => { d.remove(); startClock(); if(screen==='game')scheduleCheck(); });
  return d;
}
function showInventory() {
  const d = popup('全部 10 块拼板', `<p class="help-intro">按编号排列。当前关卡只使用高亮的拼板，其余留在盒中。</p><div class="inventory-grid">${INVENTORY.map(p=>`<button class="inventory-item ${level.sequence.includes(p.id)?'active':''}" data-inventory="${p.id}" ${level.sequence.includes(p.id)?'':'disabled'}>${badge(p.id)}<small>${level.sequence.includes(p.id)?'本关使用':'本关不用'}</small></button>`).join('')}</div>`);
  d.querySelectorAll('[data-inventory]:not(:disabled)').forEach(b=>b.onclick=()=>{ d.close(); selectPiece(Number(b.dataset.inventory)); });
}
function showRanking(id) {
  const records = scores.filter(s=>s.level===id).sort((a,b)=>a.hints-b.hints || a.time-b.time || a.moves-b.moves).slice(0,10);
  const d=popup('本机排行榜', `<div class="ranking-levels">${LEVELS.map(l=>`<button class="${l.id===id?'active':''}" data-ranking="${l.id}">挑战 ${String(l.id).padStart(2,'0')}</button>`).join('')}</div>${records.length?`<table class="ranking-table"><thead><tr><th>名次</th><th>完成时间</th><th>步数</th></tr></thead><tbody>${records.map((r,i)=>`<tr><td>${i+1}</td><td class="rank-time">${formatTime(r.time,true)}<small class="score-kind">${r.hints?'使用提示 · 练习成绩':'独立完成'}</small></td><td>${r.moves}</td></tr>`).join('')}</tbody></table>`:'<p class="ranking-empty">还没有成绩，来完成第一次训练吧。</p>'}<p class="ranking-caption">本机训练记录，保存在当前浏览器。独立完成优先，再按用时、步数排列。当前还没有连接线上榜。</p>`);
  d.querySelectorAll('[data-ranking]').forEach(b=>b.onclick=()=>{d.close();showRanking(Number(b.dataset.ranking));});
}

function positionHorseAtStart() {
  const node = document.querySelector('#horse-pawn');
  if (!node) return;
  node.style.left = `${GATES[level.start][0] * 20}%`;
  node.style.top = `${GATES[level.start][1] * 25}%`;
  node.classList.add('at-start');
}

async function run(replay = false) {
  if (busy) return;
  const result = validate(level,session().placements);
  if (!result.ok) { setStatus(result.message, 'error'); return; }
  clearTimeout(autoCheck);
  if (!replay) { stopClock(); session().finished=true; session().completed=true; scores.push({level:level.id,time:Math.round(session().elapsed),moves:session().moves,hints:session().hints,date:Date.now()}); persist(); }
  const token = ++runToken;
  busy = true;
  setStatus('路线正确！马儿准备出发，跟着它看看每一次跳跃。','success');
  const node = document.querySelector('#horse-pawn');
  node.classList.remove('at-start'); node.classList.add('running');
  document.querySelectorAll('.workbench button, .board-grid button').forEach(b => b.disabled = true);

  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const route = result.route;
  const paths = route.map(p => ({p}));
  const duration = reduced ? 700 : route.length * 1350;
  const start = performance.now();
  await new Promise(resolve => {
    function frame(now) {
      if (token !== runToken) { resolve(); return; }
      const total = Math.max(0,Math.min((now - start) / duration, 1));
      const cursor = Math.min(total * paths.length, paths.length - .000001);
      const segment = paths[Math.floor(cursor)];
      const local = cursor % 1;
      const along = segment.p.from === 0 ? local : 1 - local;
      const [rx,ry] = rotatePoint(pathPoint(segment.p.id,along),segment.p.r);
      node.style.left = `${(segment.p.x + rx) * 20}%`;
      node.style.top = `${(segment.p.y + ry) * 25}%`;
      const gateT = segment.p.id === 5 ? .33 : segment.p.id === 6 ? .45 : .5;
      const jump = reduced ? 0 : Math.sin(Math.PI * Math.max(0,1 - Math.abs(along - gateT) * 8)) * 15;
      node.style.setProperty('--jump', `${jump}px`);
      const pp = ports(segment.p);
      node.style.setProperty('--flip', pp[1-segment.p.from][0] < pp[segment.p.from][0] ? '-1' : '1');
      node.dataset.jumping = jump > 2;
      if (total < 1) setTimeout(() => frame(performance.now()),16); else resolve();
    }
    frame(performance.now());
  });
  if (token !== runToken) return;
  busy = false;
  session().completed = true; persist();
  screen = 'win'; render(); announce('漂亮，顺利通关！');
}



const actions = {
  home: showHome, help: showHelp, rotate: rotateSelected, inventory: showInventory, ranking: () => showRanking(level.id),
  remove() {
    if (selected === null || busy || !session().placements.some(p => p.id === selected)) return;
    remember(); session().placements = session().placements.filter(p => p.id !== selected); session().moves++;
    status = `${selected} 号已移回工作台。`; statusType = 'neutral'; persist(); renderGame();
  },
  undo() {
    if (busy || !session().history.length) return;
    const old = session().history.pop(); const hints = session().hints; Object.assign(session(),old); session().hints = Math.max(hints,old.hints || 0);
    status = '已撤销上一步。'; statusType = 'neutral'; persist(); renderGame();
  },
  reset() {
    if (busy || !session().placements.length) return;
    remember(); session().placements = []; session().moves++;
    selected = level.sequence[0]; rotation = 0; status = '棋盘已清空，一起重新规划。可以撤销这次操作。'; statusType = 'neutral'; persist(); renderGame();
  },
  hint() {
    if (busy) return;
    const target = level.solution.find(s => !session().placements.some(p => p.id === s.id && p.x === s.x && p.y === s.y && p.r === s.r));
    if (!target) { setStatus('路线已正确连接，马儿即将出发！','success'); scheduleCheck(); return; }
    remember();
    const remaining = session().placements.filter(p => p.id !== target.id && canPlace(target,[p]));
    const returned = session().placements.length - remaining.length - (session().placements.some(p=>p.id===target.id) ? 1 : 0);
    session().placements = [...remaining,{...target}]; session().moves++; session().hints++;
    selected = target.id; rotation = target.r;
    status = `帮你摆好了 ${target.id} 号。${returned ? '占位的拼板已移回工作台。' : '观察它的路口如何连接。'}`; statusType = 'success'; persist(); renderGame(); announce(status);
  },
  replay() { screen = 'game'; selected = null; status = ''; renderGame(); run(true); },
};

app.addEventListener('click',event => {
  if (performance.now() < suppressClickUntil) { event.preventDefault(); return; }
  const levelButton = event.target.closest('[data-level]');
  if (levelButton) { startLevel(Number(levelButton.dataset.level)); return; }
  const action = event.target.closest('[data-action]');
  if (action) { event.preventDefault(); actions[action.dataset.action]?.(); return; }
  if (busy) return;
  const piece = event.target.closest('[data-piece], [data-rack]');
  if (piece) { selectPiece(Number(piece.dataset.piece || piece.dataset.rack)); return; }
  const cell = event.target.closest('[data-cell]');
  if (cell) { const i = Number(cell.dataset.cell); placeAt(i % COLS,Math.floor(i/COLS)); }
});

app.addEventListener('pointerdown',event => {
  if (busy || event.button !== 0 || screen !== 'game') return;
  const button = event.target.closest('[data-piece], [data-rack]');
  if (!button) return;
  const id = Number(button.dataset.piece || button.dataset.rack);
  const p = session().placements.find(p => p.id === id);
  const grid = document.querySelector('#board-grid').getBoundingClientRect();
  const offset = button.dataset.piece ? [Math.floor((event.clientX - grid.left)/(grid.width/COLS))-p.x,Math.floor((event.clientY - grid.top)/(grid.height/ROWS))-p.y] : [0,0];
  drag = { id, r: p?.r ?? (id === selected ? rotation : 0), startX:event.clientX,startY:event.clientY,offset,moving:false,pointerId:event.pointerId };
  button.setPointerCapture(event.pointerId);
});

document.addEventListener('pointermove',event => {
  if (!drag || drag.pointerId !== event.pointerId) return;
  if (Math.hypot(event.clientX-drag.startX,event.clientY-drag.startY) < 8 && !drag.moving) return;
  drag.moving = true;
  const grid = document.querySelector('#board-grid');
  if (!grid) return;
  const rect = grid.getBoundingClientRect();
  const x = Math.floor((event.clientX-rect.left)/(rect.width/COLS))-drag.offset[0];
  const y = Math.floor((event.clientY-rect.top)/(rect.height/ROWS))-drag.offset[1];
  const [w,h] = size(drag.r);
  const ghost = document.querySelector('#placement-ghost');
  ghost.hidden = false; ghost.innerHTML = tileSVG(drag.id,drag.r,true);
  ghost.style.cssText = `left:${x*20}%;top:${y*25}%;width:${w*20}%;height:${h*25}%;`;
  ghost.classList.toggle('invalid', !canPlace({id:drag.id,x,y,r:drag.r},session().placements));
  drag.x = x; drag.y = y;
});

document.addEventListener('pointerup',event => {
  if (!drag || drag.pointerId !== event.pointerId) return;
  const current = drag; drag = null;
  const ghost = document.querySelector('#placement-ghost'); if (ghost) ghost.hidden = true;
  if (!current.moving) return;
  suppressClickUntil = performance.now() + 350;
  placeAt(current.x,current.y,current.id,current.r);
});
document.addEventListener('pointercancel',() => { drag = null; const ghost = document.querySelector('#placement-ghost'); if (ghost) ghost.hidden = true; });
document.addEventListener('keydown',event => {
  if (screen !== 'game' || busy || document.querySelector('dialog') || event.target.matches('input,textarea,select')) return;
  if (event.key.toLowerCase() === 'r' && !event.ctrlKey && !event.metaKey) { event.preventDefault(); rotateSelected(); }
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'z') { event.preventDefault(); actions.undo(); }
  if (event.key === 'Delete' || event.key === 'Backspace') { event.preventDefault(); actions.remove(); }
});
window.addEventListener('hashchange', () => {
  const id = Number(location.hash.slice(1));
  if (LEVELS.some(l=>l.id === id)) startLevel(id); else showHome();
});

if (screen === 'game') startLevel(level.id); else render();
