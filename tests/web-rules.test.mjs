import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRules, createStore, GameSession, stars, clock, dropCandidate, overFrame } from '../src/rules.js';

const data = JSON.parse(fs.readFileSync(new URL('../shared/game-data.json', import.meta.url), 'utf8'));
const rules = createRules(data);

const memoryBacking = () => {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)) };
};
const newSession = (backing = memoryBacking()) => {
  let clockMs = 0;
  const session = new GameSession(rules, createStore(backing), () => clockMs, () => 1000);
  return { session, backing, advance: ms => { clockMs += ms; } };
};

test('every level solution traces into a full route through its sequence', () => {
  assert.equal(rules.levels.length, 80);
  for (const level of rules.levels) {
    const route = rules.trace(level, level.solution, level.solutionStart, level.solutionFinish);
    assert.ok(route?.length, `level ${level.id} solution traces`);
    assert.ok(level.solution.every(p => rules.fits(p)), `level ${level.id} fits the board`);
    assert.equal(rules.hasOverlap(level.solution), false, `level ${level.id} has no overlaps`);
  }
});

test('rotation keeps pieces inside the 5 x 4 board', () => {
  assert.deepEqual(rules.dimensions(0), [2, 1]);
  assert.deepEqual(rules.dimensions(1), [1, 2]);
  const corner = rules.turnedInside({ id: 1, x: 3, y: 3, r: 0 }, 1);
  assert.ok(rules.fits(corner));
  assert.equal(rules.fits({ id: 1, x: 4, y: 0, r: 0 }), false);
  assert.ok(rules.fits(rules.initialPlacement({ id: 1, x: 4, y: 0, r: 0 })));
});

test('hints alone solve every level and start the run once', () => {
  for (const level of rules.levels) {
    const { session } = newSession();
    session.start(level);
    for (let i = 0; i < level.solution.length + 2; i++) session.hint();
    assert.ok(session.beginRunIfSolved(), `level ${level.id} runs`);
    assert.equal(session.beginRunIfSolved(), false);
    session.finishRun();
    assert.equal(session.screen, 'win');
    assert.equal(session.store.scores(level.id).length, 1);
  }
});

test('open-endpoint levels run as soon as the board allows only one start and finish', () => {
  const open = rules.levels.filter(level => level.solveEndpoints);
  assert.equal(open.length, 16);
  for (const level of open) {
    const { session } = newSession();
    session.start(level);
    for (const piece of level.solution.slice(0, -1)) session.placePiece({ ...piece });
    assert.equal(session.beginRunIfSolved(), false, `level ${level.id} waits for the last piece`);
    session.placePiece({ ...level.solution.at(-1) });
    assert.equal(session.hints, 0);
    assert.ok(session.beginRunIfSolved(), `level ${level.id} runs without picking endpoints`);
    assert.equal(session.chosenStart, level.solutionStart);
    assert.equal(session.chosenFinish, level.solutionFinish);
  }
});

test('a manually chosen endpoint that contradicts the board blocks the run', () => {
  const level = rules.levels.find(l => l.solveEndpoints);
  const { session } = newSession();
  session.start(level);
  const wrong = [...rules.gates.keys()].find(gate => gate !== level.solutionStart);
  session.chooseEndpoint(wrong, false);
  for (const piece of level.solution) session.placePiece({ ...piece });
  assert.equal(session.beginRunIfSolved(), false);
  session.chooseEndpoint(level.solutionStart, false);
  assert.ok(session.beginRunIfSolved());
});

test('progress survives a reload and garbage storage is ignored', () => {
  const { session, backing, advance } = newSession();
  session.start(rules.levels[2]);
  session.hint();
  advance(1500);
  session.pause();
  const reloaded = newSession(backing).session;
  reloaded.start(rules.levels[2]);
  assert.equal(reloaded.placed.length, 1);
  assert.equal(reloaded.hints, 1);
  assert.equal(reloaded.time(), 1500);
  const broken = memoryBacking();
  broken.setItem('racingclub.web.v1', '{"progress":[1],"scores":{"3":[{"level":3,"millis":-1}]},"turnMode":"SIDEWAYS"}');
  const store = createStore(broken);
  assert.equal(store.load(3), null);
  assert.deepEqual(store.scores(3), []);
  assert.equal(store.turnMode, 'BOTH');
});

test('undo, reset and dialogs keep the clock and history consistent', () => {
  const { session, advance } = newSession();
  session.start(rules.levels[0]);
  session.hint();
  session.undo();
  assert.equal(session.placed.length, 0);
  assert.equal(session.hints, 1);
  advance(500);
  session.showDialog('help');
  advance(10000);
  session.closeDialog();
  assert.equal(session.time(), 500);
  session.hint();
  session.reset();
  assert.equal(session.placed.length, 0);
});

test('backgrounding pauses the clock and blocks the automatic run', () => {
  const { session, advance } = newSession();
  const level = rules.levels[0];
  session.start(level);
  for (const piece of level.solution) session.hint();
  session.enterBackground();
  advance(5000);
  assert.equal(session.beginRunIfSolved(), false);
  session.enterForeground();
  assert.ok(session.beginRunIfSolved());
});

test('scoring helpers and drag drop mapping', () => {
  assert.equal(stars(0, 5), 3);
  assert.equal(stars(2, 5), 2);
  assert.equal(stars(3, 5), 1);
  assert.equal(clock(61234), '01:01.23');
  assert.deepEqual(dropCandidate({ id: 1, x: 0, y: 0, r: 0 }, [2.4, 1.4], [1, .5]), { id: 1, x: 1, y: 1, r: 0 });
  assert.equal(overFrame([6, 1]), false);
  assert.equal(overFrame([2, 2]), true);
});
