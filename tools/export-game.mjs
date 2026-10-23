import {readFile, writeFile, mkdir, copyFile} from 'node:fs/promises';
import {INVENTORY} from '../src/game.js';
const read = async path => JSON.parse(await readFile(path,'utf8'));
const catalog=await read('references/level-catalog.json');
const topology=await read('references/piece-topology.json');
const difficulties=await read('shared/difficulties.json');
const generated=await read('shared/generated-hints.json');
const mix=(a,b,t)=>a.map((v,i)=>v*(1-t)+b[i]*t);
const bez=(ps,t)=>ps.length===1?ps[0]:bez(ps.slice(0,-1).map((p,i)=>mix(p,ps[i+1],t)),t);
const line=(a,b)=>t=>mix(a,b,t);
const curve=ps=>t=>bez(ps,t);
const turn=down=>t=>t<.6?[t/.6,.5]:bez([[1,.5],[1.5,.5],[1.5,down?1:0]],(t-.6)/.4);
const geometry={
 1:[line([0,.5],[2,.5])],
 2:[curve([[.5,0],[.5,.62],[1.5,.38],[1.5,1]])],
 3:[curve([[.5,1],[.5,.38],[1.5,.62],[1.5,0]])],
 4:[curve([[.5,0],[.5,1],[1.5,1],[1.5,0]])],
 5:[turn(false)],6:[turn(true)],
 7:[curve([[0,.5],[.5,.5],[.5,0]]),line([1.5,0],[1.5,1])],
 8:[line([.5,0],[.5,1]),curve([[.5,0],[.5,.5],[.8,.5],[2,.5]])],
 9:[line([.5,1],[.5,0]),curve([[.5,1],[.5,.45],[1.5,.7],[1.5,0]])],
 10:[line([.5,0],[.5,1]),curve([[.5,0],[.5,.55],[1.5,.3],[1.5,1]])]
};
const gates={1:[100,50,0],2:[100,50,45],3:[100,50,-45],4:[100,75,0],5:[55,50,0],6:[75,50,0],7:[150,50,90],8:[50,79,90],9:[100,56,-20],10:[100,44,20]};
const names=['','直线路','S 弯','反向 S 弯','回转路','左转路','右转路','独立双路','分岔路','分岔路','分岔路'];
function sample(fn) {
 const dense=Array.from({length:501},(_,i)=>fn(i/500));const lengths=[0];
 for(let i=1;i<dense.length;i++)lengths.push(lengths[i-1]+Math.hypot(...dense[i].map((v,k)=>v-dense[i-1][k])));
 let j=1;const total=lengths.at(-1);
 const samples=Array.from({length:101},(_,i)=>{const d=total*i/100;while(j<500&&lengths[j]<d)j++;return mix(dense[j-1],dense[j],(d-lengths[j-1])/(lengths[j]-lengths[j-1])).map(v=>+v.toFixed(6));});
 return {samples,length:+total.toFixed(6)};
}
const pieces=topology.pieces.map(p=>{
 const gate=gates[p.id],paths=p.paths.map((path,i)=>{
  const sampled=sample(geometry[p.id][i]);
  const distances=sampled.samples.map(([x,y])=>Math.hypot(x-gate[0]/100,y-gate[1]/100));
  return {...path,...sampled,jumpAt:path.jump===null?null:distances.indexOf(Math.min(...distances))/100};
 });
 return {...p,name:names[p.id],color:INVENTORY.find(i=>i.id===p.id).color,gate,paths,samples:paths[0].samples};
});
const levels=catalog.levels.map(l=>{
 const sequence=l.challenge.obstacleSequence??l.referenceTrace?.obstacleSequence;
 if(!sequence?.length)throw Error(`Missing sequence ${l.id}`);
 const difficulty=difficulties.find(d=>d.group===l.printedGroupColor&&l.id>=d.firstLevel&&l.id<=d.lastLevel);
 if(!difficulty)throw Error(`Missing difficulty ${l.id}`);
 const hint=generated.levels.find(h=>h.id===l.id);
 const solution=l.referenceLayout?.map(p=>({id:p.pieceId,x:p.x,y:p.y,r:p.r}))??hint?.solution??[];
 return {id:l.id,title:l.id===3?'第一次轻盈转弯':l.id===4?'多一个弯，也从容':l.endpointMode==='solve_both'?'寻找两端，连成完整路线':new Set(sequence).size<sequence.length?'循着顺序，再次跃过障碍':'连起道路，依序跃过障碍',group:l.printedGroupColor,difficultyId:difficulty.id,endpointMode:l.endpointMode,start:l.startGate,finish:l.finishGate,sequence,solution,solutionStart:l.referenceStartGate??hint?.start,solutionFinish:l.referenceFinishGate??hint?.finish,solutionSource:l.referenceLayout?'reviewed_photo':'computed_from_verified_topology',sequenceSource:l.challenge.obstacleSequence?'challenge_photo':'unique_reference_trace'};
});
const data={schemaVersion:2,contentVersion:'80-levels-difficulties-2026-10-23',difficulties,columns:5,rows:4,gates:catalog.board.gates,inventory:INVENTORY,pieces,levels};
await writeFile('shared/game-data.json',JSON.stringify(data,null,2)+'\n');
for(const folder of ['native/android/app/src/main/assets','native/ios/RacingClub/Resources']){
 await mkdir(folder,{recursive:true});await copyFile('shared/game-data.json',`${folder}/game-data.json`);
}
console.log(`Exported ${levels.length} levels and ${pieces.length} real road topologies to both native apps.`);
