import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import * as rules from '../src/game.js';

const source = (await readFile(new URL('../src/app.js', import.meta.url), 'utf8')).replace(/^import[^\n]*\n/, '');

// Run the actual browser controller with a manual clock. Dialog close is a queued
// task, matching the browser ordering that caused a newly opened modal to resume.
function browser(saved = {}, hash = '#3') {
 let now = 0, next = 0;
 const jobs = new Map(), closeTasks = [], intervals = [], events = new Map(), dialogs = [];
 class Element {
  constructor() {
   this.style = { setProperty(name, value) { this[name] = value; } };
   this.dataset = {}; this.classList = { add() {}, remove() {} }; this.listeners = new Map(); this.buttons = new Map();
  }
  addEventListener(name, fn) { this.listeners.set(name, fn); }
  querySelector(selector) { if (!this.buttons.has(selector)) this.buttons.set(selector, new Element()); return this.buttons.get(selector); }
  querySelectorAll(selector) {
   if (selector !== '[data-ranking]') return [];
   return rules.LEVELS.map(l => { const b = this.querySelector(`rank-${l.id}`); b.dataset.ranking = String(l.id); return b; });
  }
  showModal() { this.open = true; }
  close() { this.open = false; closeTasks.push(() => this.listeners.get('close')?.()); }
  remove() { const i = dialogs.indexOf(this); if (i !== -1) dialogs.splice(i, 1); }
 }
 const app = new Element(), pawn = new Element(), timer = new Element(), announcement = new Element();
 const document = {
  hidden: false, body: { dataset: {}, append(dialog) { dialogs.push(dialog); } },
  createElement() { return new Element(); },
  querySelector(selector) {
   if (selector === 'dialog[open]') return dialogs.find(d => d.open) || null;
   if (selector === 'dialog') return dialogs[0] || null;
   return ({ '#app': app, '#horse-pawn': pawn, '#elapsed-time': timer, '#announcement': announcement, '#game-status': new Element() })[selector] || null;
  },
  querySelectorAll() { return []; },
  addEventListener(name, fn) { events.set(name, fn); },
 };
 const context = vm.createContext({ ...rules, document, window: { addEventListener() {} }, localStorage: { getItem: () => JSON.stringify(saved), setItem() {} }, location: { hash, pathname: '/', search: '' }, history: { replaceState() {} }, performance: { now: () => now }, structuredClone, matchMedia: () => ({ matches: true }),
  setInterval(fn) { intervals.push(fn); },
  setTimeout(fn, delay = 0) { const id = ++next; jobs.set(id, { at: now + delay, fn }); return id; },
  clearTimeout(id) { jobs.delete(id); },
 });
 vm.runInContext(source, context, { filename: 'src/app.js' });
 return {
  read: code => vm.runInContext(code, context),
  dialog: () => dialogs.find(d => d.open),
  flushClose() { while (closeTasks.length) closeTasks.shift()(); },
  visibility(hidden) { document.hidden = hidden; events.get('visibilitychange')(); },
  advance(ms) {
   const end = now + ms;
   while (true) {
    const hit = [...jobs].filter(([, j]) => j.at <= end).sort((a, b) => a[1].at - b[1].at)[0];
    if (!hit) break;
    jobs.delete(hit[0]); now = hit[1].at; hit[1].fn();
   }
   now = end; intervals.forEach(fn => fn());
  },
 };
}

test('ranking replacement and tab visibility keep the clock and solved board paused until all dialogs close', () => {
 const page = browser();
 page.advance(1000);
 page.read('showRanking(3)');
 page.read('session().placements = structuredClone(level.solution)');
 page.dialog().querySelectorAll('[data-ranking]')[1].onclick();
 page.flushClose(); // Old close callback runs after the replacement was opened.
 page.advance(1000);
 assert.equal(page.read('clockStarted'), null);
 assert.equal(page.read('elapsedTime()'), 1000);
 assert.equal(page.read('scores.length'), 0);
 assert.equal(page.read('busy'), false);
 page.visibility(true); page.visibility(false); page.advance(1000);
 assert.equal(page.read('clockStarted'), null);
 assert.equal(page.read('elapsedTime()'), 1000);
 assert.equal(page.read('busy'), false);
 page.dialog().close(); page.flushClose(); page.advance(301);
 assert.equal(page.read('busy'), true);
 assert.equal(page.read('scores.length'), 1);
 assert.equal(page.read('scores[0].time'), 1300);
});

test('browser initialization discards malformed stored roots and score entries without losing valid records', () => {
 for (const saved of [null, 3, [], 'broken']) assert.doesNotThrow(() => browser(saved, ''));
 const page = browser({ __scores: [null, 3, false, 'x', [], {}, { level: 3, time: 12, moves: -1, hints: 0 }, { level: 3, time: 12, moves: 1, hints: 0 }] }, '');
 assert.equal(page.read('scores.length'), 1);
 assert.equal(page.read('scores[0].time'), 12);
});
