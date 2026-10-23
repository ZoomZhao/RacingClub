package com.zoomzhao.racingclub

import androidx.compose.runtime.*

private data class Snapshot(val pieces:List<Placement>,val moves:Int,val hints:Int,val start:String,val finish:String)

/** Screen identity that must survive Activity and process recreation. */
data class UiSnapshot(val screen:String,val level:Int,val selected:Int?,val dialog:String,val difficulty:String,val running:Boolean,val elapsed:Long,val moves:Int,val hints:Int)

/** One play session. Storage and the monotonic clock are injected, so the whole
 * state machine runs in JVM tests; no Context or system clock is touched here. */
class GameState(val rules:Rules,val repository:LeaderboardRepository,private val progress:ProgressStore,private val nowMillis:()->Long) {
 var level by mutableStateOf(rules.levels.first());var screen by mutableStateOf("home");var placed by mutableStateOf(listOf<Placement>());var selected by mutableStateOf<Int?>(null);var rotation by mutableIntStateOf(0);var moves by mutableIntStateOf(0);var hints by mutableIntStateOf(0);var message by mutableStateOf("");var running by mutableStateOf(false);var dialog by mutableStateOf("");var elapsed by mutableLongStateOf(0);private var started:Long?=null;private val history=mutableListOf<Snapshot>();var activeRoute:List<RouteStep> = emptyList();var chosenStart by mutableStateOf("");var chosenFinish by mutableStateOf("");var selectedDifficultyId by mutableStateOf(rules.difficulties.firstOrNull()?.id.orEmpty());var progressVersion by mutableIntStateOf(0)
 /** Route animation position; kept here so a recreated UI continues the same run. */
 var runProgress by mutableFloatStateOf(0f)
 /** False from ON_PAUSE to ON_RESUME: the clock stops and no run or score may begin. */
 var foreground by mutableStateOf(true);private set
 fun difficulty(l:Level=level)=rules.difficulties.first{it.id==l.difficultyId}
 val startGate get()=level.start.ifEmpty{chosenStart}
 val finishGate get()=level.finish.ifEmpty{chosenFinish}
 fun solved(id:Int):Boolean {progressVersion;return progress.completed(id)||repository.list(id).isNotEmpty()}
 fun inProgress(id:Int):Boolean=progress.load(id)?.let{!it.finished&&it.placed.isNotEmpty()}==true
 fun best(id:Int)=repository.list(id).firstOrNull()
 fun time()=elapsed+(started?.let{nowMillis()-it}?:0)
 fun pause(){started?.let{elapsed+=nowMillis()-it};started=null;save()}
 fun resume(){if(foreground&&screen=="game"&&!running&&dialog.isEmpty()&&started==null)started=nowMillis()}
 fun enterBackground(){foreground=false;pause()}
 fun enterForeground(){foreground=true;resume()}
 fun save(){if(screen=="home")return;progress.save(level.id,SavedProgress(placed,moves,hints,time(),chosenStart,chosenFinish,running||screen=="win"))}
 fun start(l:Level){
  pause();level=l;screen="game";running=false;runProgress=0f;dialog="";placed=emptyList();moves=0;hints=0;elapsed=0;history.clear();activeRoute=emptyList();chosenStart="";chosenFinish="";selectedDifficultyId=l.difficultyId
  progress.load(l.id)?.takeIf{!it.finished}?.let{o->
   o.placed.forEach{p->if(p.id in l.sequence&&rules.fits(p))placed=placed.filter{it.id!=p.id}+p}
   moves=o.moves;hints=o.hints;elapsed=o.elapsed.coerceAtLeast(0)
   chosenStart=o.start.takeIf{it in rules.gates}.orEmpty();chosenFinish=o.finish.takeIf{it in rules.gates}.orEmpty()
  }
  selected=l.requiredPieces.firstOrNull{id->placed.none{it.id==id}};rotation=0;message="";resume()
 }
 fun home(){pause();screen="home";running=false;runProgress=0f;dialog=""}
 /** Raises a placed tile. Re-selecting the same tray tile keeps its pending turn. */
 fun select(id:Int){
  if(running)return
  val p=placed.find{it.id==id}
  if(p!=null){rotation=p.r;if(placed.lastOrNull()?.id!=id){placed=placed.filter{it.id!=id}+p;save()}}
  else if(selected!=id)rotation=0
  selected=id;message=""
 }
 fun clearSelection(){if(running)return;selected=null;rotation=0}
 private fun remember(){history+=Snapshot(placed,moves,hints,chosenStart,chosenFinish)}
 /** Commits exactly the given tile; callers never rely on another gesture's selection. */
 fun placePiece(p:Placement):Boolean{
  if(running)return false
  if(!rules.fits(p)){message="超出棋盘边界，已保留原位";return false}
  remember();placed=placed.filter{it.id!=p.id}+p;selected=p.id;rotation=p.r;moves++;message="";save();return true
 }
 fun place(x:Int,y:Int){val id=selected?:return;val p=Placement(id,x,y,rotation);placePiece(if(placed.none{it.id==id})rules.initialPlacement(p)else p)}
 fun rotatePiece(id:Int,quarterTurns:Int){
  if(running)return
  val old=placed.find{it.id==id}
  if(old!=null){
   val p=rules.turnedInside(old,quarterTurns)
   remember();placed=placed.filter{it.id!=id}+p;selected=id;rotation=p.r;moves++
  }else{if(selected!=id){selected=id;rotation=0};rotation=((rotation+quarterTurns)%4+4)%4}
  message="";save()
 }
 fun rotate(quarterTurns:Int=1){rotatePiece(selected?:return,quarterTurns)}
 /** A tile moved back waits in the tray at the default 0°, like every tray tile. */
 fun removePiece(id:Int){if(running||placed.none{it.id==id})return;remember();placed=placed.filter{it.id!=id};selected=id;rotation=0;moves++;message="";save()}
 fun remove(){removePiece(selected?:return)}
 fun undo(){if(running||history.isEmpty())return;val s=history.removeAt(history.lastIndex);placed=s.pieces;moves=s.moves;hints=maxOf(hints,s.hints);chosenStart=s.start;chosenFinish=s.finish;rotation=placed.firstOrNull{it.id==selected}?.r?:0;message="";save()}
 fun reset(){if(running)return;remember();placed=emptyList();chosenStart="";chosenFinish="";rotation=0;moves++;message="";save()}
 fun hint(){if(running)return;val p=level.solution.firstOrNull{it !in placed};if(p==null){if(level.solveEndpoints&&(chosenStart!=level.solutionStart||chosenFinish!=level.solutionFinish)){remember();chosenStart=level.solutionStart;chosenFinish=level.solutionFinish;moves++;hints++;message="";save()};return};remember();placed=placed.filter{it.id!=p.id&&!rules.overlaps(p,it)}+p;selected=p.id;rotation=p.r;moves++;hints++;message="";save()}
 fun chooseEndpoint(letter:String,finish:Boolean=dialog=="finish"){if(running||!level.solveEndpoints||letter !in rules.gates)return;remember();if(finish)chosenFinish=letter else chosenStart=letter;moves++;message="";closeDialog();save()}
 fun trace():List<RouteStep>? {if(level.solveEndpoints&&(startGate.isEmpty()||finishGate.isEmpty()))return null;return rules.trace(level,placed,startGate,finishGate)}
 fun canAutoRun()=screen=="game"&&!running&&dialog.isEmpty()&&foreground
 /** Freezes the clock and records exactly one score. Refused in the background or behind a dialog. */
 fun beginRun(route:List<RouteStep>):Boolean{
  if(!canAutoRun()||route.isEmpty())return false
  pause();running=true;runProgress=0f;activeRoute=route;message="";repository.save(Score(level.id,elapsed,moves,hints));progress.markCompleted(level.id);progressVersion++;save();return true
 }
 /** Re-checks every guard at the moment of starting, not when the delay was scheduled. */
 fun beginRunIfSolved():Boolean=canAutoRun()&&trace()?.let{beginRun(it)}==true
 fun finishRun(){if(!running)return;running=false;runProgress=1f;screen="win";save()}
 fun showDialog(which:String){if(running)return;pause();dialog=which}
 fun closeDialog(){dialog="";resume()}

 fun uiSnapshot()=UiSnapshot(screen,level.id,selected,dialog,selectedDifficultyId,running,if(running||screen=="win")elapsed else time(),moves,hints)
 /** Process-death restore. Placement and timing reload from the progress store; a run
  * that was interrupted settles, because its score was saved when it began. */
 fun restore(ui:UiSnapshot){
  rules.difficulties.firstOrNull{it.id==ui.difficulty}?.let{selectedDifficultyId=it.id}
  val l=rules.levels.firstOrNull{it.id==ui.level}?:return
  when {
   ui.running||ui.screen=="win"->{
    pause();level=l;screen="win";running=false;runProgress=1f;placed=emptyList();history.clear();activeRoute=emptyList()
    elapsed=ui.elapsed;moves=ui.moves;hints=ui.hints;selected=null;dialog=if(ui.dialog=="rank")"rank" else ""
   }
   ui.screen=="game"->{
    start(l);selectedDifficultyId=rules.difficulties.firstOrNull{it.id==ui.difficulty}?.id?:selectedDifficultyId
    selected=ui.selected?.takeIf{it in l.requiredPieces};rotation=placed.firstOrNull{it.id==selected}?.r?:0
    if(ui.dialog in listOf("rank","help","start","finish")||(ui.dialog.startsWith("gate:")&&ui.dialog.substringAfter(":") in rules.gates))showDialog(ui.dialog)
   }
   else->{level=l;screen="home"}
  }
 }
}
