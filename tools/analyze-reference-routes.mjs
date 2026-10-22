import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = new URL('../', import.meta.url);
const read = name => JSON.parse(fs.readFileSync(new URL(name, root), 'utf8'));
const topology = read('references/piece-topology.json');
const catalog = read('references/level-catalog.json');
const pieces = new Map(topology.pieces.map(p => [p.id, p]));
const key = point => point.join(',');
const gates = new Map(Object.entries(catalog.board.gates).map(([g, p]) => [key(p), g]));
const rotate = ([x,y],r) => [[x,y],[1-y,x],[2-x,1-y],[y,2-x]][r];

export function oriented(layout, r) {
  const p = pieces.get(layout.pieceId);
  return {...layout, r, ports:p.ports.map(pt => rotate(pt,r).map((v,i) => v + (i ? layout.y : layout.x))), paths:p.paths};
}

// An analysis constraint for the pictured layouts, not an added gameplay rule:
// every drawn road end in these solution photos meets another road or a gate.
export function orientationCandidates(level) {
  const choices = level.referenceLayout.map(p => {
    const first = p.width === 2 ? 0 : 1;
    return (p.pieceId <= 3 ? [first] : [first,first+2]).map(r => oriented(p,r));
  });
  const results = [];
  function visit(placed) {
    if (placed.length === choices.length) {
      const ends = new Map();
      for (const p of placed) for (const pt of p.ports) ends.set(key(pt), (ends.get(key(pt)) ?? 0) + 1);
      if ([...ends].some(([k,n]) => n !== 2 && !(n === 1 && gates.has(k)))) return;
      if (ends.get(key(catalog.board.gates[level.observedFinishGate])) !== 1) return;
      results.push(placed);
      return;
    }
    for (const choice of choices[placed.length]) visit([...placed,choice]);
  }
  visit([]);
  return results;
}

export function buildGraph(placements) {
  const ports = new Map();
  const byState = new Map();
  for (const p of placements) p.ports.forEach((point,index) => {
    const entry = {state:`${p.pieceId}:${index}`,p,index};
    const at = ports.get(key(point)) ?? [];
    at.push(entry); ports.set(key(point),at); byState.set(entry.state,entry);
  });
  const graph = new Map();
  for (const [state,{p,index}] of byState) {
    const edges = [];
    for (const path of p.paths) {
      if (path.from !== index && path.to !== index) continue;
      const out = path.from === index ? path.to : path.from;
      const coordinate = key(p.ports[out]);
      const adjacent = ports.get(coordinate).find(other => other.p.pieceId !== p.pieceId);
      const gate = gates.get(coordinate);
      if (!adjacent && !gate) throw new Error(`Unconnected road ${state} -> ${out}`);
      edges.push({to:adjacent?.state ?? `gate:${gate}`,jump:path.jump,pieceId:p.pieceId,from:index,out});
    }
    graph.set(state,edges);
  }
  const openings = [...gates].flatMap(([coordinate,gate]) => {
    const entries = ports.get(coordinate);
    return entries?.length === 1 ? [{gate,state:entries[0].state}] : [];
  });
  return {graph,openings};
}

// Match the complete printed instruction, including repeated jumps. Static
// ambiguity samples are deliberately capped and cannot validate a long lap.
export function matchReferenceSequence(level, sequence) {
  const {graph,openings}=buildGraph(level.referenceLayout.map(p=>oriented(p,p.r)));
  const startState=openings.find(g=>g.gate===level.referenceStartGate)?.state;
  if(!startState) return null;
  const queue=[{state:startState,index:0,steps:[]}],seen=new Set();
  for(let i=0;i<queue.length;i++) {
    const {state,index,steps}=queue[i],key=`${state}|${index}`;
    if(seen.has(key))continue;
    seen.add(key);
    for(const edge of graph.get(state) ?? []) {
      if(edge.jump!==null && edge.jump!==sequence[index])continue;
      const nextIndex=index+(edge.jump===null?0:1);
      const nextSteps=[...steps,{pieceId:edge.pieceId,from:edge.from,to:edge.out,jump:edge.jump}];
      if(edge.to.startsWith('gate:')) {
        if(edge.to===`gate:${level.referenceFinishGate}` && nextIndex===sequence.length)return nextSteps;
      } else queue.push({state:edge.to,index:nextIndex,steps:nextSteps});
    }
  }
  return null;
}

// Enumerate route states with obstacle coverage. Re-entering the same piece is
// allowed; only an identical entry port AND coverage state forms a cycle.
export function analyzeRoutes(placements, start, finish) {
  const {graph,openings} = buildGraph(placements);
  const startState = openings.find(g => g.gate === start)?.state;
  if (!startState) throw new Error(`No start gate ${start}`);
  const fullMask = placements.reduce((m,p) => m | (1 << (p.pieceId-1)),0);
  const states = new Map();
  const stateKey = (state,mask) => `${state}|${mask}`;
  const firstKey = stateKey(startState,0);
  const queue = [{state:startState,mask:0}];
  const accepting = new Set();
  for (let i=0;i<queue.length;i++) {
    const {state,mask} = queue[i], k = stateKey(state,mask);
    if (states.has(k)) continue;
    const edges = [];
    states.set(k,edges);
    for (const step of graph.get(state) ?? []) {
      const nextMask = mask | (step.jump === null ? 0 : 1 << (step.jump-1));
      if (step.to.startsWith('gate:')) {
        if (step.to === `gate:${finish}` && nextMask === fullMask) {
          const to = 'accepted'; accepting.add(to); edges.push({...step,to});
        }
      } else {
        const to = stateKey(step.to,nextMask);
        edges.push({...step,to}); queue.push({state:step.to,mask:nextMask});
      }
    }
  }
  // Keep only states that can reach the finish after crossing every shown obstacle.
  const useful = new Set(accepting);
  for (let changed=true;changed;) {
    changed=false;
    for(const [k,edges] of states) if(!useful.has(k) && edges.some(e=>useful.has(e.to))) {useful.add(k);changed=true;}
  }
  if (!useful.has(firstKey)) return {routeCount:0,cyclic:false,sequences:[],sampleRoutes:[]};
  let cyclic=false, repeatableObstacleCycle=null;
  const active = new Set(), done = new Set();
  const stack=[], incomingJumps=[];
  function cycle(k,incomingJump=null) {
    if(active.has(k)) {
      cyclic=true;
      const index=stack.indexOf(k);
      const jumps=[...incomingJumps.slice(index+1),incomingJump].filter(j=>j!==null);
      if(jumps.length && !repeatableObstacleCycle)repeatableObstacleCycle={states:[...stack.slice(index),k],obstacleSequence:jumps};
      return;
    }
    if(done.has(k) || !useful.has(k)) return;
    active.add(k);stack.push(k);incomingJumps.push(incomingJump);
    for(const e of states.get(k) ?? []) cycle(e.to,e.jump);
    active.delete(k);stack.pop();incomingJumps.pop();done.add(k);
  }
  cycle(firstKey);
  const samples = [], distinct = new Set();
  // Simple state paths supply ambiguity witnesses even when cycles are possible.
  // Samples are capped; the exact finite count is calculated separately below.
  function walk(k,steps,sequence,seen) {
    if(samples.length >= 32) return;
    if(k==='accepted') {
      const signature=sequence.join(',');
      if(!distinct.has(signature)){distinct.add(signature);samples.push({sequence,steps});}
      return;
    }
    if(seen.has(k)) return;
    const nextSeen = new Set(seen);nextSeen.add(k);
    for(const e of states.get(k) ?? []) if(useful.has(e.to)) {
      walk(e.to,[...steps,{pieceId:e.pieceId,from:e.from,to:e.out,jump:e.jump}],e.jump===null?sequence:[...sequence,e.jump],nextSeen);
    }
  }
  if (!cyclic) walk(firstKey,[],[],new Set());
  else {
    // Two short witnesses; permit one repeated coverage-state to expose an
    // optional extra lap. This bound limits examples, not the cycle proof above.
    const frontier=[{k:firstKey,steps:[],sequence:[],visits:new Map()}];
    for(let i=0;i<frontier.length && samples.length<2;i++) {
      const {k,steps,sequence,visits}=frontier[i];
      if(k==='accepted') {
        const signature=sequence.join(',');
        if(!distinct.has(signature)){distinct.add(signature);samples.push({sequence,steps});}
        continue;
      }
      if((visits.get(k) ?? 0)>=2)continue;
      const nextVisits=new Map(visits);nextVisits.set(k,(visits.get(k) ?? 0)+1);
      for(const e of states.get(k) ?? [])if(useful.has(e.to))frontier.push({
        k:e.to,steps:[...steps,{pieceId:e.pieceId,from:e.from,to:e.out,jump:e.jump}],
        sequence:e.jump===null?sequence:[...sequence,e.jump],visits:nextVisits
      });
    }
  }
  samples.sort((a,b)=>a.sequence.length-b.sequence.length || a.sequence.join(',').localeCompare(b.sequence.join(',')));
  const counts=new Map([['accepted',1]]);
  function count(k) {
    if(counts.has(k))return counts.get(k);
    const n=(states.get(k) ?? []).filter(e=>useful.has(e.to)).reduce((sum,e)=>sum+count(e.to),0);
    counts.set(k,n);return n;
  }
  return {routeCount:cyclic?null:count(firstKey),cyclic,repeatableObstacleCycle,sequences:samples.map(s=>s.sequence),sampleRoutes:samples,stateCount:states.size};
}

export function analyzeCatalog() {
  return catalog.levels.filter(l=>l.referenceLayout).map(level=>{
    const candidates=orientationCandidates(level);
    if(candidates.length!==1)return {id:level.id,orientationCandidateCount:candidates.length};
    const placements=candidates[0],{openings}=buildGraph(placements);
    const finish=level.observedFinishGate;
    const starts=openings.filter(g=>g.gate!==finish);
    if(starts.length>1)throw new Error(`Multiple non-finish openings at ${level.id}`);
    const start=starts[0]?.gate ?? finish;
    return {id:level.id,orientationCandidateCount:1,placements:placements.map(({pieceId,x,y,r})=>({pieceId,x,y,r})),openGates:openings.map(g=>g.gate),start,finish,...analyzeRoutes(placements,start,finish)};
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const report={
    schemaVersion:1,reviewedOn:catalog.updatedOn,
    scope:'Reference-photo analysis, not confirmed challenge instructions or runtime validation',
    sources:['level-catalog.json','piece-topology.json','levels/index.json'],
    assumptions:[
      'Layouts are fixed to the photographed solution, not searched across all board arrangements.',
      'Orientations are selected by full road-end connectivity, then compared with the photos; this is an analysis constraint, not a gameplay rule.',
      'Every pictured obstacle is crossed at least once; empty branches do not add an obstacle number.',
      'The red gate anchors the reference finish. A sole boundary opening serves as both start and finish.',
      'Only listed smooth port transitions are allowed; exiting a board gate ends the route.',
      'No unprinted lap-count or shortest-route constraint is imposed. Cycle witnesses explain why a sequence cannot be recovered uniquely.'
    ],
    levels:analyzeCatalog()
  };
  const destination=process.argv[2];
  if(destination) fs.writeFileSync(destination,JSON.stringify(report,null,2)+'\n');
  else for(const l of report.levels)console.log(l.id,l.orientationCandidateCount,l.start,l.finish,l.routeCount,l.cyclic,l.sequences?.slice(0,2));
}
