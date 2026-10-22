import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createHash } from 'node:crypto';
import { analyzeCatalog, buildGraph, oriented, matchReferenceSequence } from '../tools/analyze-reference-routes.mjs';

const root=new URL('../',import.meta.url);
const read=p=>JSON.parse(fs.readFileSync(new URL(p,root),'utf8'));
const catalog=read('references/level-catalog.json');
const saved=read('references/route-audit.json');
const analysis=analyzeCatalog();
const byId=new Map(analysis.map(a=>[a.id,a]));
const level=id=>catalog.levels.find(l=>l.id===id);

test('all original photos and both kinds of crops match the manifests',()=>{
  const sources=read('references/index.json').items;
  const crops=read('references/levels/index.json');
  assert.equal(sources.length,61);
  assert.equal(crops.levels.length,80);
  assert.equal(crops.supplementalChallengeCrops.length,60);
  assert.equal(crops.totalCropCount,140);
  assert.deepEqual(crops.supplementalChallengeCrops.map(c=>c.levelId),Array.from({length:60},(_,i)=>i+17));
  for(const item of [...sources,...crops.levels,...crops.supplementalChallengeCrops]) {
    const bytes=fs.readFileSync(new URL(`references/${item.file}`,root));
    assert.equal(bytes.length,item.bytes,item.file);
    assert.equal(createHash('sha256').update(bytes).digest('hex'),item.sha256,item.file);
    if(item.contentType) {
      assert.equal(bytes.readUInt32BE(16),item.width,item.file);
      assert.equal(bytes.readUInt32BE(20),item.height,item.file);
      const source=sources.find(s=>s.file===item.sourceFile);
      assert.equal(source.sha256,item.sourceSha256,item.file);
      const [left,top,right,bottom]=item.cropBoxPixelsUpright;
      assert.equal(right-left,item.width);assert.equal(bottom-top,item.height);
    }
  }
});

test('76 reference layouts fit the board and have reproducible orientations',()=>{
  assert.deepEqual(catalog.levels.map(l=>l.id),Array.from({length:80},(_,i)=>i+1));
  assert.deepEqual(analysis,saved.levels);
  assert.equal(analysis.length,76);
  for(const a of analysis) {
    const l=level(a.id),cells=new Set();
    assert.equal(a.orientationCandidateCount,1,`level ${a.id}`);
    assert.equal(a.start,l.referenceStartGate);
    assert.equal(a.finish,l.referenceFinishGate);
    for(const p of l.referenceLayout) {
      assert.equal(p.width*p.height,2);
      assert.equal(p.r,a.placements.find(q=>q.pieceId===p.pieceId).r);
      for(let x=p.x;x<p.x+p.width;x++)for(let y=p.y;y<p.y+p.height;y++) {
        assert.ok(x>=0 && x<5 && y>=0 && y<4);
        const k=`${x},${y}`;assert.ok(!cells.has(k),`overlap in ${a.id}`);cells.add(k);
      }
    }
  }
});

test('every published candidate replays real ports and only counts obstacles',()=>{
  for(const a of analysis) {
    const placements=level(a.id).referenceLayout.map(p=>oriented(p,p.r));
    const {graph,openings}=buildGraph(placements);
    const unique=new Set();
    for(const sample of a.sampleRoutes) {
      let state=openings.find(g=>g.gate===a.start).state;
      const jumps=[];
      for(const step of sample.steps) {
        assert.equal(state,`${step.pieceId}:${step.from}`,`level ${a.id}`);
        const edge=graph.get(state).find(e=>e.out===step.to && e.jump===step.jump);
        assert.ok(edge,`illegal fork in ${a.id}`);
        if(edge.jump!==null)jumps.push(edge.jump);
        state=edge.to;
      }
      assert.equal(state,`gate:${a.finish}`);
      assert.deepEqual(jumps,sample.sequence);
      assert.deepEqual([...new Set(jumps)].sort((a,b)=>a-b),level(a.id).visibleObstacleIds);
      unique.add(jumps.join(','));
    }
    assert.equal(unique.size,a.sampleRoutes.length);
    if(!a.cyclic)assert.equal(a.routeCount,unique.size);
    else {
      assert.ok(a.repeatableObstacleCycle.obstacleSequence.length>0);
      assert.equal(a.repeatableObstacleCycle.states[0],a.repeatableObstacleCycle.states.at(-1));
      assert.ok(unique.size>=2);
    }
  }
});

test('levels 1–16 reproduce the independently reviewed photo sequences',()=>{
  const fixtures=[
    ['E','D',[6]],['B','A',[1,5]],['E','F',[5,1,6]],['B','A',[2,1,5,3]],
    ['A','C',[6,5,1]],['C','D',[7,4,5]],['C','A',[2,6,3,4,5]],['F','B',[3,5,4,6,1]],
    ['F','E',[3,5,4,6,2,1]],['A','C',[6,4,3,2,5]],['D','B',[5,4,2,1,3]],['C','F',[7,4,6,5,3]],
    ['D','B',[4,7,3,5,6]],['E','D',[2,6,1,4,5,7,3]],['A','B',[6,4,5,7,1,2]],['A','E',[5,4,7,6,3,1,2]]
  ];
  fixtures.forEach(([start,finish,sequence],i)=>{
    const a=byId.get(i+1);
    assert.equal(a.start,start);assert.equal(a.finish,finish);
    assert.deepEqual(a.sequences,[sequence]);assert.equal(a.routeCount,1);
  });
  const thirteen=byId.get(13).sampleRoutes[0];
  assert.equal(thirteen.steps[0].pieceId,7);
  assert.equal(thirteen.steps[0].jump,null);
  assert.equal(thirteen.sequence[0],4);
});

test('new challenge photos resolve levels 17–24 including repeated jumps',()=>{
  const fixtures=[
    ['A',[5,10,4,5]],['E',[1,5,6,3,2,10]],['A',[5,4,1,3,6,2,10]],['B',[3,2,10,6,5,2,3]],
    ['B',[3,1,4,6,8,1,3]],['F',[9,6,2,4,1,7]],['C',[5,7,3,2,4,1,10]],['E',[2,4,5,7,10,2]]
  ];
  fixtures.forEach(([gate,sequence],i)=>{
    const l=level(i+17),a=byId.get(l.id);
    assert.equal(l.challenge.start,gate);assert.equal(l.challenge.finish,gate);
    assert.deepEqual(l.challenge.obstacleSequence,sequence);
    assert.deepEqual(l.referenceTrace.obstacleSequence,sequence);
    assert.ok(a.sequences.some(s=>JSON.stringify(s)===JSON.stringify(sequence)));
    assert.equal(a.routeCount,2); // ambiguity in the picture is resolved by its challenge
    assert.ok(l.referenceAnalysis.challengeSequenceConfirmed);
    assert.ok(fs.existsSync(new URL(`references/${l.challenge.cropFile}`,root)));
  });
  const steps=level(17).referenceTrace.pieceTraversals;
  assert.equal(steps.filter(s=>s.pieceId===10).length,2);
  assert.equal(steps.filter(s=>s.jump===10).length,1);
  assert.equal(steps.filter(s=>s.jump===5).length,2);
});

test('edge-adjacent obstacle 8 is not a B gate in levels 57, 59, 72',()=>{
  for(const [id,gate] of [[57,'E'],[59,'C'],[72,'E']]) {
    const a=byId.get(id),l=level(id);
    assert.deepEqual(a.openGates,[gate]);assert.equal(a.start,gate);assert.equal(a.finish,gate);
    const p=l.referenceLayout.find(p=>p.pieceId===8);
    assert.equal(p.r,2);
    assert.ok(!oriented(p,p.r).ports.some(([x,y])=>x===5 && y===1.5));
  }
});

test('forks prohibit leaf-to-leaf turns and piece 7 has disconnected paths',()=>{
  for(const [id,levelId] of [[8,21],[9,22],[10,17]]) {
    const {graph}=buildGraph(level(levelId).referenceLayout.map(p=>oriented(p,p.r)));
    assert.deepEqual(graph.get(`${id}:0`).map(e=>e.out).sort(),[1,2]);
    assert.deepEqual(graph.get(`${id}:1`).map(e=>e.out),[0]);
    assert.deepEqual(graph.get(`${id}:2`).map(e=>e.out),[0]);
  }
  const {graph}=buildGraph(level(6).referenceLayout.map(p=>oriented(p,p.r)));
  assert.deepEqual(graph.get('7:0').map(e=>[e.out,e.jump]),[[1,null]]);
  assert.deepEqual(graph.get('7:2').map(e=>[e.out,e.jump]),[[3,7]]);
});

test('the level 46 photo resolves a repeatable lap without erasing its ambiguity evidence',()=>{
  const a=byId.get(46);
  assert.equal(a.cyclic,true);assert.equal(a.routeCount,null);
  assert.deepEqual(a.sequences[0],[4,5,6,8,4,9,3,2]);
  assert.deepEqual(a.sequences[1],[4,5,6,8,4,5,6,8,4,9,3,2]);
  assert.deepEqual(level(46).challenge.obstacleSequence,a.sequences[0]);
  assert.deepEqual(level(46).referenceTrace.obstacleSequence,a.sequences[0]);
  assert.equal(level(46).referenceAnalysis.resolution.matchesReferenceRoute,true);
  // The topology permits both, but the printed challenge chooses only the first.
  assert.ok(matchReferenceSequence(level(46),a.sequences[1]));
});

test('65 onward keeps player-determined endpoints after the challenge photos are confirmed',()=>{
  for(const l of catalog.levels.filter(l=>l.id>=65)) {
    assert.equal(l.endpointMode,'solve_both');
    assert.equal(l.challenge.start,null);assert.equal(l.challenge.finish,null);
    assert.equal(l.startGate,null);assert.equal(l.finishGate,null);
    assert.equal(l.challenge.startMode,'player_determined');assert.equal(l.challenge.finishMode,'player_determined');
  }
  assert.deepEqual(level(66).referenceTrace.obstacleSequence,[1,3,4,7,5,6]);
  assert.deepEqual(level(68).referenceTrace.obstacleSequence,[6,7,5,4,2,3]);
  for(const id of [66,68])assert.deepEqual(level(id).challenge.obstacleSequence,level(id).referenceTrace.obstacleSequence);
});

test('challenge facts, inference, and unresolved data stay distinct',()=>{
  assert.deepEqual(catalog.levels.filter(l=>l.challenge.obstacleSequence).map(l=>l.id),[3,4,...Array.from({length:64},(_,i)=>i+17)]);
  assert.deepEqual(catalog.levels.filter(l=>!l.challenge.obstacleSequence && l.referenceTrace).map(l=>l.id),[1,2,...Array.from({length:12},(_,i)=>i+5)]);
  assert.equal(catalog.levels.filter(l=>!l.challenge.obstacleSequence && !l.referenceTrace).length,0);
  assert.deepEqual(catalog.audit.unresolvedSequenceLevelIds,[]);
  for(const l of catalog.levels)assert.deepEqual(l.initialPlacements,[]);
  for(const l of catalog.levels.filter(l=>l.id>=77))assert.equal(l.referenceLayout,null);
  assert.deepEqual(level(80).challenge.obstacleSequence,[2,10,5,6,3,9,8,4,1,5,6,3,9,2]);
});

test('all 76 complete reference traces replay ports, repeated obstacles, and empty branches',()=>{
  for(const l of catalog.levels.filter(l=>l.referenceLayout)) {
    const trace=l.referenceTrace,sequence=l.challenge.obstacleSequence??trace.obstacleSequence;
    const {graph,openings}=buildGraph(l.referenceLayout.map(p=>oriented(p,p.r)));
    let state=openings.find(o=>o.gate===trace.start).state;
    const jumps=[];
    for(const step of trace.pieceTraversals) {
      assert.equal(state,`${step.pieceId}:${step.from}`,`level ${l.id}`);
      const edge=graph.get(state)?.find(e=>e.out===step.to && e.jump===step.jump);
      assert.ok(edge,`illegal step in ${l.id}`);
      if(edge.jump!==null)jumps.push(edge.jump);
      state=edge.to;
    }
    assert.equal(state,`gate:${trace.finish}`);
    assert.deepEqual(jumps,sequence);
    assert.deepEqual(matchReferenceSequence(l,sequence),trace.pieceTraversals);
    assert.deepEqual(l.requiredPieceIds,[...new Set(sequence)].sort((a,b)=>a-b));
  }
  assert.equal(matchReferenceSequence(level(3),[5,1]),null);
  assert.equal(matchReferenceSequence(level(3),[5,1,6,6]),null);
  assert.equal(matchReferenceSequence(level(3),[5,6,1]),null);
});

test('consolidated transcripts retain original evidence and full multi-row instructions',()=>{
  const files=fs.readdirSync(new URL('references/transcriptions/',root)).filter(f=>f.endsWith('.json'));
  const ids=[];
  const manifest=read('references/index.json').items;
  const crops=read('references/levels/index.json').supplementalChallengeCrops;
  for(const file of files) {
    const batch=read('references/transcriptions/'+file);
    assert.equal(batch.status,'consolidated');
    for(const s of batch.sources){const original=manifest.find(m=>m.file===s.file);assert.equal(original.sha256,s.sha256);assert.equal(original.original_name,s.originalName);}
    for(const row of batch.levels) {
      ids.push(row.id);const l=level(row.id),crop=crops.find(c=>c.levelId===row.id);
      assert.deepEqual(l.challenge.obstacleSequence,row.obstacleSequence);
      assert.deepEqual(l.referenceTrace.pieceTraversals,row.pieceTraversals);
      assert.equal(l.challenge.start,row.start);assert.equal(l.challenge.finish,row.finish);
      assert.equal(l.challenge.cropFile,crop.file);assert.equal(l.challenge.source,crop.sourceFile);
      assert.deepEqual(l.challenge.cropBoxPixelsUpright,crop.cropBoxPixelsUpright);
      assert.equal(l.challenge.sequenceLength,row.obstacleSequence.length);
      assert.equal(Object.values(l.challenge.obstacleCounts).reduce((a,b)=>a+b,0),row.obstacleSequence.length);
    }
  }
  assert.deepEqual(ids.sort((a,b)=>a-b),Array.from({length:52},(_,i)=>i+25));
  assert.deepEqual(level(55).challenge.obstacleSequence,[1,2,4,3,5,8,6,9,2,4,3,10,5,3,4,2,1]);
  assert.deepEqual(level(64).challenge.obstacleSequence,[3,1,10,4,5,9,4,6,8,5,9,4,10,1,3]);
  assert.deepEqual(level(76).challenge.obstacleSequence,[1,10,6,2,4,5,9,4,2,6,8,5,9,4,2,6,10,1]);
});
