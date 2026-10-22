import test from 'node:test';
import assert from 'node:assert/strict';
import { LEVELS, canPlace, validate, ports, restorePlacements, rotatePoint, pathPoint, INVENTORY, PIECES } from '../src/game.js';

test('both photographed solutions form a continuous ordered route', () => {
  for (const l of LEVELS) { const result = validate(l,l.solution); assert.equal(result.ok,true,result.message); assert.deepEqual(result.route.map(p=>p.id),l.sequence); }
});
test('rotating symmetric straight piece accepts equivalent physical solution', () => {
  const l = LEVELS[0]; const ps = l.solution.map(p=>p.id === 1 ? {...p,r:3} : p);
  assert.equal(validate(l,ps).ok,true);
});
test('rotation rejects overlap and board boundary; moving ignores own previous footprint', () => {
  const l = LEVELS[0]; assert.equal(canPlace({...l.solution[0],r:1},l.solution),false);
  assert.equal(canPlace({id:1,x:0,y:0,r:0},[{id:1,x:0,y:0,r:0}]),true);
  assert.equal(canPlace({id:1,x:0,y:0,r:0},[{id:5,x:1,y:0,r:0}]),false);
});
test('missing, disconnected, overlapping, duplicated and wrong-order pieces cannot win', () => {
  const l=LEVELS[0]; assert.equal(validate(l,[]).ok,false);
  assert.equal(validate(l,l.solution.slice(1)).ok,false);
  assert.equal(validate(l,l.solution.map(p=>p.id===1?{...p,x:2}:p)).ok,false);
  assert.equal(validate(l,[...l.solution,l.solution[0]]).ok,false);
  assert.equal(validate({...l,sequence:[6,1,5]},l.solution).ok,false);
  assert.equal(validate(l,l.solution.map(p=>p.id===1?{...p,y:0}:p)).ok,false);
});
test('restore sanitizes invalid, duplicate, overlapping and foreign pieces', () => {
  const l=LEVELS[0]; assert.deepEqual(restorePlacements(l,[null,{id:1,x:9,y:3,r:1},{id:4,x:0,y:0,r:0},...l.solution,l.solution[0]]),l.solution);
});
test('rotated endpoints remain on the actual piece boundary', () => {
  assert.deepEqual(rotatePoint([0,.5],1),[.5,0]); assert.deepEqual(ports({id:2,x:4,y:1,r:1}),[[5,1.5],[4,2.5]]);
  assert.equal(INVENTORY.length,10);
});
test('animation samples begin and end at the route ports',()=>{
 for(const [id,piece] of Object.entries(PIECES))for(const [index,t] of [[0,0],[1,1]]) {
  pathPoint(Number(id),t).forEach((v,i)=>assert.ok(Math.abs(v-piece.ports[index][i])<1e-8));
 }
});
