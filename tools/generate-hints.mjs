import {readFileSync,writeFileSync,mkdtempSync,rmSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
const temp=mkdtempSync(join(tmpdir(),'racingclub-hints-'));
try {
 const binary=join(temp,'solver');
 execFileSync('clang++',['-std=c++17','-O3','tools/solve-levels.cpp','-o',binary],{stdio:'inherit'});
 const catalog=JSON.parse(readFileSync('references/level-catalog.json','utf8'));
 const levels=catalog.levels.filter(l=>!l.referenceLayout).map(l=>{
  const seq=l.challenge.obstacleSequence;
  return {id:l.id,...JSON.parse(execFileSync(binary,{input:`${seq.length} ${seq.join(' ')}\n`,encoding:'utf8',timeout:60000}))};
 });
 writeFileSync('shared/generated-hints.json',JSON.stringify({schemaVersion:1,basis:'Computed from verified ports and challenge sequences; not a photographed or claimed unique solution.',generator:'tools/solve-levels.cpp',levels},null,2)+'\n');
 console.log(`Generated ${levels.length} hint layouts. Run export-game.mjs and npm test to independently replay them.`);
}finally{rmSync(temp,{recursive:true,force:true});}
