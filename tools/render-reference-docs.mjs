import fs from 'node:fs';

const root=new URL('../',import.meta.url);
const read=p=>JSON.parse(fs.readFileSync(new URL(p,root),'utf8'));
const catalog=read('references/level-catalog.json');
const audit=read('references/route-audit.json');
const manifest=read('references/index.json');
const crops=read('references/levels/index.json');
const byId=new Map(audit.levels.map(a=>[a.id,a]));
const sequence=l=>l.challenge.obstacleSequence ?? l.referenceTrace?.obstacleSequence;
const confirmed=catalog.levels.filter(l=>l.challenge.obstacleSequence);
const inferred=catalog.levels.filter(l=>!l.challenge.obstacleSequence && l.referenceTrace);
const pending=catalog.levels.filter(l=>!sequence(l));
const unique=audit.levels.filter(a=>a.routeCount===1);
const sameGate=audit.levels.filter(a=>a.start===a.finish);
const supplemented=catalog.levels.filter(l=>l.id>=17 && l.id<=76 && l.challenge.obstacleSequence);
const resolvedAmbiguous=supplemented.filter(l=>byId.get(l.id)?.routeCount!==1);
const fmt=s=>s.join(' → ');
const candidateDescription=a=>a.cyclic?'可重复绕行，原题次数不能从图确定':`${a.routeCount} 种合法候选`;
const stats=[
  `原始图片 ${manifest.items.length} 张；原有编号裁切 ${crops.levels.length} 张，补充题目裁切 ${crops.supplementalChallengeCrops?.length ?? 0} 张。`,
  `${confirmed.length} 关经原题确认，${inferred.length} 关可从图示路线唯一推导；共 ${confirmed.length+inferred.length} 关具有明确序列，待定顺序 ${pending.length} 关。`,
  `76 份解答图的端点、朝向和完整序列已复核，其中 ${sameGate.length} 份两端同位；1–16 再次核对，与原有路线一致。`,
  `唯一参考推导而未独立见原题的关卡：${inferred.map(l=>l.id).join('、')}。77–80 题面已确认，尚无解答照片。`,
  '57、59、72 的旧参考起点 B 更正为 E、C、E；三处均曾将 8 号横杆误作边界出口。',
  '第 65 关起题目两端由玩家推导；参考解答的字母不是题面预设。'
];

const index=[
  'RacingClub 关卡资料索引（第 1–80 关）',`更新日期：${catalog.updatedOn}`,'',
  '一、覆盖与确认状态',...stats,
  '＊为经过照片与道路模型复核的参考推导，尚未独立核对原题；无＊的完整序列来自题目照片。',
  '“玩家推导”是已确认的题目要求，不是资料缺失。',
  '题目/参考字段、来源与朝向：references/level-catalog.json。',
  '道路模型与证据：references/piece-topology.json、references/route-audit.json。详细结论：docs/关卡核对报告.txt。','',
  '二、逐关起终点要求与障碍顺序','关卡  起点要求  终点要求  障碍顺序 / 核对结果'
];
for(const l of catalog.levels) {
  const star=l.challenge.obstacleSequence?'':'＊';
  const start=l.id>=65?'玩家推导':l.startGate+star;
  const finish=l.id>=65?'玩家推导':l.finishGate+star;
  const seq=sequence(l);
  const desc=seq?fmt(seq)+star:`原题顺序待确认；${candidateDescription(byId.get(l.id))}`;
  index.push(`${String(l.id).padStart(2,'0')}    ${start.padEnd(6)}  ${finish.padEnd(6)}  ${desc}`);
}
index.push('','三、参考端点、实体拼卡与来源',
  '第 65 关起本表端点仅为解答结果。拼卡集合不是跨障顺序；77–80 无解答摆法。',
  '图片路径相对于 references/；补充题目图保留为 challenge-NNN.png，不覆盖 level-NNN.png 解答图。',
  '关卡  参考起终点  实体拼卡编号  原图 / 编号裁切图');
for(const l of catalog.levels) {
  const a=byId.get(l.id),endpoints=a?`${a.start}→${a.finish}`:'无解答图';
  index.push(`${String(l.id).padStart(2,'0')}    ${endpoints.padEnd(6)}  ${l.visibleObstacleIds.join(',').padEnd(22)} ${l.source.file} / ${l.source.cropFile}`);
  if(l.challenge.cropFile)index.push(`      补充题目：${l.challenge.source} / ${l.challenge.cropFile}`);
}
index.push('','四、验证边界',
  '76 份布局检查占格、朝向、道路连接和实际跨障；原图、裁切和目录的校验值对应。',
  '无原题时，分析要求跨越全部图示障碍并遵守岔路短刻线；没有增加“必须走最短路线”的假设。',
  '17–76 原题已逐项匹配参考道路；按进入端口和序列进度验证完整顺序，不以有限候选示例代替长回环检查。',
  '55 为 17 次、64 为 15 次、76 为 18 次跨越，重复项完整保留。无障碍支路不记跨障。',
  '77–80 按箭头复核；80 为 14 次跨越、9 块实体拼卡，2、3、5、6、9 各两次。',
  '本次是资料复核；原型仍只启用 03、04，不代表其余关卡已在 App 验收。','');
fs.writeFileSync(new URL('docs/关卡索引.txt',root),index.join('\n'));

const report=[
  'RacingClub 规则、图片与逐关资料复核',`更新日期：${catalog.updatedOn}`,'',
  '一、结果',...stats,
  `静态图本身的分析：${unique.length} 份唯一序列，${audit.levels.filter(a=>!a.cyclic && a.routeCount>1).length} 份有限多候选，${audit.levels.filter(a=>a.cyclic).length} 份可重复跨障。`,
  `17–76 共 ${supplemented.length} 关题面均已确认，消除 ${resolvedAmbiguous.length} 关原有的顺序或圈数歧义；66、68 的题面与先前唯一推导一致。`,
  pending.length?'仍无明确序列的关卡：'+pending.map(l=>l.id).join('、')+'。':'全部 80 关已有明确序列；14 关的唯一参考推导继续与题面直接确认分开标注。','',
  '二、规则与图片的核对顺序',
  '先读 06-game-rules.jpg 和用户约定；再读 02–04、07–09 拼卡刻线及 10 空棋盘；最后查看题目、解答整页和 levels/ 单关裁切图。',
  '33–36 的解答整页需逆时针转正 90°。单关裁切已转正。新增题目沿箭头逐项转录，并与已有解答路线交叉检查。',
  '完整规则交叉核对来源：https://www.smartgamesandpuzzles.com/horse-academy.html（设计者 Raf Peeters，2022）。本项目使用实物编号 1–10、边界 A–F。',
  '起终点可同位；马沿道路走，不能在普通路段原地掉头，岔口不能跨过短刻线锐转。经其他拼卡构成的回环可以重复走同一路。',
  '7 号有两条不连通的路；8、9、10 号各有三个端口，但只允许共同干路与分支平顺相接；无障碍支路不记编号。',
  '辨认白色道路是否真正穿出棋盘，不能仅凭边缘附近有彩色横杆判定出口。题面多行折线是阅读顺序，不是棋盘路径形状。',
  '题册“唯一解”以完整题目序列为条件。解答图能走出多个顺序，不代表原题有多个解。','',
  '三、第 1–16 关重新确认（＊为唯一参考推导）'
];
for(let id=1;id<=16;id++) {
  const a=byId.get(id),l=catalog.levels[id-1];
  const bypass=a.sampleRoutes[0].steps.filter(s=>s.jump===null).map(s=>s.pieceId);
  report.push(`${String(id).padStart(2,'0')}：${a.start} → ${fmt(sequence(l))} → ${a.finish}。${l.challenge.obstacleSequence?'题面与解答双重确认。':'＊图中道路唯一。'}${bypass.length?`经过 ${bypass.join('、')} 号无障碍支路时不计跨障。`:''}`);
}
report.push('','四、第 17–76 关题面确认（问号表示玩家推导端点）');
for(const l of supplemented) {
  const start=l.id>=65?'?':l.challenge.start,finish=l.id>=65?'?':l.challenge.finish;
  report.push(`${l.id}：${start} → ${fmt(sequence(l))} → ${finish}。${sequence(l).length} 次跨越，匹配参考 ${l.referenceStartGate}→${l.referenceFinishGate}。`);
  report.push(`    题目：references/${l.challenge.cropFile}；解答：references/${l.source.cropFile}。`);
}
report.push('长序列复核：39、50 各 12 次，53 为 14 次，55 为 17 次，64 为 15 次，76 为 18 次；均沿题面箭头完整保留。','',
  '五、起点修正与唯一参考路线补充',
  '57：旧 B 更正为 E；参考 E→E。59：旧 B 更正为 C；参考 C→C。72：旧参考 B 更正为 E；参考 E→E。',
  '这三关 8 号均位于 (3,1)、r=2：端口 (4.5,1)、(4.5,2)、(3,1.5)，均在棋盘内部，B=(5,1.5) 没有道路开口。',
  '依据：references/levels/level-057.png、level-059.png、level-072.png。r 表示相对 piece-topology.json 标准姿态的顺时针 90° 次数。',
  '31：补出 E→D，左下 2 号道路连接 E；70：补出参考 B→A，右上 3 号道路连接 B。',
  '66：D → 1 → 3 → 4 → 7 → 5 → 6 → B；68：E → 6 → 7 → 5 → 4 → 2 → 3 → D。均为图中唯一参考序列。',
  '66、68 的两端仍由玩家推导，上述字母不预填为题目要求。',
  '参考起终点同位的关卡：'+sameGate.map(a=>a.id).join('、')+'。','',
  '六、静态解答图的歧义与原题确认',
  '58 张静态解答图本身仍有多种顺序，但相应题面已全部补齐。referenceAnalysis 保留原图的歧义证据，resolution 记录原题已确认，referenceTrace 保存匹配完整指令的端口步骤。',
  '回环路线按“进入端口 + 序列进度”回放，不能只看路径样例里是否包含该序列，也不能删除重复障碍来匹配。',
  'route-audit.json 中的候选示例遵守图示道路、岔路转向并跨越全部图示障碍后到红门。没有假设必须选择最短路线。',
  '有限候选数仅针对照片中的固定布局；回环示例并非完整枚举。完整端口步骤见 references/route-audit.json。',
  '每关图片为 references/levels/level-NNN.png（NNN 为三位关卡号）。');
for(const l of pending) {
  const a=byId.get(l.id);
  report.push('',`${l.id}：参考 ${a.start}→${a.finish}；${candidateDescription(a)}。`);
  a.sequences.slice(0,2).forEach((s,i)=>report.push(`  候选 ${i+1}：${fmt(s)}。`));
  if(a.repeatableObstacleCycle)report.push(`  可重复跨障环：${fmt(a.repeatableObstacleCycle.obstacleSequence)}（环的起读位置可循环平移）。`);
}
report.push('','七、第 77–80 关题面复核');
for(const l of catalog.levels.filter(l=>l.id>=77))report.push(`${l.id}：? → ${fmt(sequence(l))} → ?。`);
report.push('80 为 14 次跨越、9 块实体；2、3、5、6、9 各两次。两端问号不是缺失资料；目前无这四关的解答照片。','',
  '八、可复算资料与边界',
  'references/level-catalog.json 保存题面、参考端点、朝向、完整参考步骤与证据类型；14 关唯一参考推导未冒充已见原题。',
  'references/transcriptions/ 保留第 25–76 关的分批转录、原图校验值及归档路径；暂存资料已全部并入正式目录。',
  'references/levels/challenges-overview-017-076.jpg 为新增题目裁切总览；原来的解答总览与 80 张 level-NNN.png 保留。',
  'node tools/analyze-reference-routes.mjs /tmp/racingclub-route-audit.json 可重算固定布局路线；npm test 检查关键路线、目录与图片校验值。',
  'node tools/render-reference-docs.mjs 从目录与分析结果重新生成本报告及关卡索引。',
  '只在这些参考图中使用“画出的道路端口相接或接边界门”的约束选朝向；它不是新加的通用游戏规则。',
  '资料复核与 App 验收分开，运行时仍仅启用 03、04。','');
fs.writeFileSync(new URL('docs/关卡核对报告.txt',root),report.join('\n'));
console.log(`Rendered 80 levels: ${confirmed.length} confirmed, ${inferred.length} uniquely inferred, ${pending.length} pending.`);
