import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {matchReferenceSequence} from '../tools/analyze-reference-routes.mjs';
const read=p=>JSON.parse(fs.readFileSync(new URL('../'+p,import.meta.url),'utf8'));
const data=read('shared/game-data.json'),catalog=read('references/level-catalog.json'),topology=read('references/piece-topology.json');
test('80 native levels retain every instruction, endpoint mode, and unique physical tile',()=>{
 assert.equal(data.schemaVersion,2);assert.equal(data.levels.length,80);assert.equal(data.pieces.length,10);
 for(const [i,l]of data.levels.entries()){
  const c=catalog.levels[i];assert.equal(l.id,i+1);
  assert.deepEqual(l.sequence,c.challenge.obstacleSequence??c.referenceTrace.obstacleSequence);
  assert.equal(l.start,c.startGate);assert.equal(l.finish,c.finishGate);
  assert.equal(l.endpointMode,c.endpointMode);
  assert.deepEqual(l.solution.map(p=>p.id).sort((a,b)=>a-b),[...new Set(l.sequence)].sort((a,b)=>a-b));
  assert.equal(l.solutionSource,l.id<=76?'reviewed_photo':'computed_from_verified_topology');
  const replay=matchReferenceSequence({referenceLayout:l.solution.map(p=>({...p,pieceId:p.id})),referenceStartGate:l.solutionStart,referenceFinishGate:l.solutionFinish},l.sequence);
  assert.ok(replay,`independent reference replay failed: ${l.id}`);
  if(l.id>=65){assert.equal(l.start,null);assert.equal(l.finish,null);}
 }
 for(const path of ['native/android/app/src/main/assets/game-data.json','native/ios/RacingClub/Resources/game-data.json'])assert.equal(fs.readFileSync(new URL('../'+path,import.meta.url),'utf8'),fs.readFileSync(new URL('../shared/game-data.json',import.meta.url),'utf8'));
});
test('all native road paths preserve reviewed legal turns and exact animation endpoints',()=>{
 for(const p of data.pieces){
  const t=topology.pieces.find(t=>t.id===p.id);
  assert.deepEqual(p.ports,t.ports);
  assert.deepEqual(p.paths.map(({from,to,jump})=>({from,to,jump})),t.paths);
  for(const path of p.paths){
   assert.deepEqual(path.samples[0],p.ports[path.from]);assert.deepEqual(path.samples.at(-1),p.ports[path.to]);
   assert.equal(path.samples.length,101);assert.ok(path.length>0);
   if(path.jump===null)assert.equal(path.jumpAt,null);
   else {
    const pt=path.samples[Math.round(path.jumpAt*100)];
    assert.ok(Math.hypot(pt[0]-p.gate[0]/100,pt[1]-p.gate[1]/100)<.04,`rail must lie on path ${p.id}`);
   }
  }
 }
});
