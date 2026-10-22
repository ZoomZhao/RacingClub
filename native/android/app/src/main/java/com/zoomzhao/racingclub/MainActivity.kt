package com.zoomzhao.racingclub

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.Bundle
import android.os.SystemClock
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlin.math.*

internal val Green=Color(0xff254c3b);internal val Paper=Color(0xfff8f5ed);internal val Brown=Color(0xff78543b)
private fun color(s:String)=Color(android.graphics.Color.parseColor(s))
private fun JSONArray.points()=(0 until length()).map { getJSONArray(it).let { p->Point(p.getDouble(0).toFloat(),p.getDouble(1).toFloat()) } }
private fun placement(o:JSONObject)=Placement(o.getInt("id"),o.getInt("x"),o.getInt("y"),o.getInt("r"))
private fun placements(a:JSONArray)=(0 until a.length()).map { placement(a.getJSONObject(it)) }
private fun encode(p:Placement)=JSONObject().put("id",p.id).put("x",p.x).put("y",p.y).put("r",p.r)
internal fun loadRules(context:Context)=parseRules(context.assets.open("game-data.json").bufferedReader().use {it.readText()})
internal fun parseRules(json:String):Rules {
 val o=JSONObject(json);val ps=o.getJSONArray("pieces");val ls=o.getJSONArray("levels");val gs=o.getJSONObject("gates");val inv=o.getJSONArray("inventory")
 fun string(o:JSONObject,k:String)=if(o.isNull(k))"" else o.optString(k,"")
 return Rules((0 until ps.length()).associate {
  val p=ps.getJSONObject(it);val id=p.getInt("id");val paths=p.getJSONArray("paths")
  id to Piece(id,p.getString("color"),p.getJSONArray("ports").points(),p.getJSONArray("samples").points(),(0..2).map{n->p.getJSONArray("gate").getDouble(n).toFloat()},(0 until paths.length()).map{n->
   val path=paths.getJSONObject(n);RoadPath(path.getInt("from"),path.getInt("to"),if(path.isNull("jump"))null else path.getInt("jump"),path.getJSONArray("samples").points(),if(path.isNull("jumpAt"))null else path.getDouble("jumpAt").toFloat(),path.getDouble("length").toFloat())
  })
 },gs.keys().asSequence().associateWith{k->gs.getJSONArray(k).let{Point(it.getDouble(0).toFloat(),it.getDouble(1).toFloat())}},(0 until ls.length()).map {
  val l=ls.getJSONObject(it);val seq=l.getJSONArray("sequence")
  Level(l.getInt("id"),l.getString("title"),string(l,"start"),string(l,"finish"),(0 until seq.length()).map{n->seq.getInt(n)},placements(l.getJSONArray("solution")),l.getString("group"),string(l,"solutionStart"),string(l,"solutionFinish"))
 },(0 until inv.length()).associate{inv.getJSONObject(it).let{p->p.getInt("id") to p.getString("color")}})
}
class LocalLeaderboard(context:Context):LeaderboardRepository {
 private val prefs=context.getSharedPreferences("leaderboard-v1",Context.MODE_PRIVATE)
 private val order=compareBy<Score>{it.hints>0}.thenBy{it.millis}.thenBy{it.moves}
 private var records:List<Score> = runCatching {
  val a=JSONArray(prefs.getString("scores","[]")?:"[]")
  (0 until a.length()).map{val o=a.getJSONObject(it);Score(o.getInt("level"),o.getLong("millis"),o.getInt("moves"),o.getInt("hints"),o.getLong("timestamp"))}
 }.getOrDefault(emptyList())
 override fun list(level:Int)=records.filter{it.level==level}.sortedWith(order).take(10)
 override fun save(score:Score) {
  // Keep ten best records PER LEVEL; finishing later levels cannot erase early bests.
  records=records.filter{it.level!=score.level}+(list(score.level)+score).sortedWith(order).take(10)
  val a=JSONArray();records.forEach{a.put(JSONObject().put("level",it.level).put("millis",it.millis).put("moves",it.moves).put("hints",it.hints).put("timestamp",it.timestamp))}
  prefs.edit().putString("scores",a.toString()).apply()
 }
}
private data class Snapshot(val pieces:List<Placement>,val moves:Int,val hints:Int,val start:String,val finish:String)
class GameState(private val context:Context,val rules:Rules,val repository:LeaderboardRepository) {
 var level by mutableStateOf(rules.levels.first());var screen by mutableStateOf("home");var placed by mutableStateOf(listOf<Placement>());var selected by mutableStateOf<Int?>(null);var rotation by mutableIntStateOf(0);var moves by mutableIntStateOf(0);var hints by mutableIntStateOf(0);var message by mutableStateOf("点选拼板，再点左上格放置");var running by mutableStateOf(false);var dialog by mutableStateOf("");var elapsed by mutableLongStateOf(0);private var started:Long?=null;private val history=mutableListOf<Snapshot>();var activeRoute:List<RouteStep> = emptyList();var chosenStart by mutableStateOf("");var chosenFinish by mutableStateOf("");var chapter by mutableIntStateOf(0);var progressVersion by mutableIntStateOf(0)
 val startGate get()=level.start.ifEmpty{chosenStart}
 val finishGate get()=level.finish.ifEmpty{chosenFinish}
 fun solved(id:Int):Boolean {progressVersion;return prefs.getBoolean("completed-$id",false)||repository.list(id).isNotEmpty()}
 fun inProgress(id:Int):Boolean=runCatching{JSONObject(prefs.getString("level-$id","{}")?:"{}").let{!it.optBoolean("finished")&&it.optJSONArray("placed")?.length()?.let{n->n>0}==true}}.getOrDefault(false)
 fun best(id:Int)=repository.list(id).firstOrNull()
 private val prefs=context.getSharedPreferences("progress-v1",Context.MODE_PRIVATE)
 fun time()=elapsed+(started?.let{SystemClock.elapsedRealtime()-it}?:0)
 fun pause(){started?.let{elapsed+=SystemClock.elapsedRealtime()-it};started=null;save()}
 fun resume(){if(screen=="game" && !running && dialog.isEmpty() && started==null)started=SystemClock.elapsedRealtime()}
 fun save(){if(screen=="home")return;val a=JSONArray();placed.forEach{a.put(encode(it))};prefs.edit().putString("level-${level.id}",JSONObject().put("placed",a).put("moves",moves).put("hints",hints).put("elapsed",time()).put("start",chosenStart).put("finish",chosenFinish).put("finished",running||screen=="win").toString()).apply()}
 fun start(l:Level){pause();level=l;screen="game";running=false;placed=emptyList();moves=0;hints=0;elapsed=0;history.clear();activeRoute=emptyList();chosenStart="";chosenFinish="";chapter=(l.id-1)/16;runCatching{val o=JSONObject(prefs.getString("level-${l.id}","{}")?:"{}");if(!o.optBoolean("finished")){placements(o.optJSONArray("placed")?:JSONArray()).forEach{p->if(p.id in l.sequence && rules.fits(p,placed))placed=placed.filter{it.id!=p.id}+p};moves=o.optInt("moves");hints=o.optInt("hints");elapsed=o.optLong("elapsed");chosenStart=o.optString("start").takeIf{it in rules.gates}.orEmpty();chosenFinish=o.optString("finish").takeIf{it in rules.gates}.orEmpty()}};selected=l.requiredPieces.firstOrNull{ id->placed.none{it.id==id} };rotation=0;message=if(l.solveEndpoints)"本关起终点待推导，点棋盘下方选择" else "拼板按编号排列 · 正确后自动出发";resume()}
 fun home(){pause();screen="home";running=false;dialog=""}
 fun select(id:Int){if(running)return;selected=id;rotation=placed.find{it.id==id}?.r?:0;message="已选中 $id 号，可旋转或点空格移动"}
 private fun remember(){history+=Snapshot(placed,moves,hints,chosenStart,chosenFinish)}
 fun place(x:Int,y:Int){val id=selected?:return;if(running)return;val p=Placement(id,x,y,rotation);if(!rules.fits(p,placed)){message="这里重叠或越界，已保留原位";return};remember();placed=placed.filter{it.id!=id}+p;moves++;message="已放置 $id 号";save()}
 fun rotate(){if(running)return;val id=selected?:return;val nr=(rotation+1)%4;val old=placed.find{it.id==id};if(old!=null){val p=old.copy(r=nr);if(!rules.fits(p,placed)){message="旋转后重叠或越界，已保留原位";return};remember();placed=placed.map{if(it.id==id)p else it};moves++};rotation=nr;save()}
 fun remove(){if(running)return;val id=selected?:return;if(placed.none{it.id==id})return;remember();placed=placed.filter{it.id!=id};moves++;save()}
 fun undo(){if(running||history.isEmpty())return;val s=history.removeAt(history.lastIndex);placed=s.pieces;moves=s.moves;hints=maxOf(hints,s.hints);chosenStart=s.start;chosenFinish=s.finish;rotation=placed.firstOrNull{it.id==selected}?.r?:rotation;message="已撤销上一步";save()}
 fun reset(){if(running)return;remember();placed=emptyList();chosenStart="";chosenFinish="";moves++;save()}
 fun hint(){if(running)return;val p=level.solution.firstOrNull{it !in placed};if(p==null){if(level.solveEndpoints&&(chosenStart!=level.solutionStart||chosenFinish!=level.solutionFinish)){remember();chosenStart=level.solutionStart;chosenFinish=level.solutionFinish;moves++;hints++;message="提示标出了起终点";save()};return};remember();placed=placed.filter{it.id!=p.id&&rules.fits(p,listOf(it))}+p;selected=p.id;rotation=p.r;moves++;hints++;message="提示帮你摆好了 ${p.id} 号";save()}
 fun chooseEndpoint(letter:String){if(running||!level.solveEndpoints||letter !in rules.gates)return;remember();if(dialog=="start")chosenStart=letter else chosenFinish=letter;moves++;message="起点 ${startGate.ifEmpty{"?"}} · 终点 ${finishGate.ifEmpty{"?"}}";closeDialog();save()}
 fun trace():List<RouteStep>? {if(level.solveEndpoints&&(startGate.isEmpty()||finishGate.isEmpty()))return null;return rules.trace(level,placed,startGate,finishGate)}
 fun beginRun(route:List<RouteStep>){pause();running=true;activeRoute=route;message="路线正确，马儿出发！";repository.save(Score(level.id,elapsed,moves,hints));prefs.edit().putBoolean("completed-${level.id}",true).apply();progressVersion++;save()}
 fun showDialog(which:String){if(running)return;pause();dialog=which}
 fun closeDialog(){dialog="";resume()}
}
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);WindowCompat.setDecorFitsSystemWindows(window,false);WindowInsetsControllerCompat(window,window.decorView).apply{hide(WindowInsetsCompat.Type.systemBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE};setContent{val context=LocalContext.current;val state=remember{GameState(context,loadRules(context),LocalLeaderboard(context))};val owner=LocalLifecycleOwner.current;DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE)state.pause() else if(event==Lifecycle.Event.ON_RESUME)state.resume()};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}};MaterialTheme(typography=ClubTypography,colorScheme=lightColorScheme(primary=Green,background=Paper,surface=Paper)){RacingApp(state)}}}}
private fun formatTime(ms:Long)="%02d:%02d.%02d".format(ms/60000,ms/1000%60,ms%1000/10)
@Composable private fun Tool(image:ImageVector,label:String,enabled:Boolean=true,action:()->Unit){IconButton(onClick=action,enabled=enabled,modifier=Modifier.size(42.dp)){Icon(image,label,tint=if(enabled)Brown else Brown.copy(alpha=.25f),modifier=Modifier.size(22.dp))}}
