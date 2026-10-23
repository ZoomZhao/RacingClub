package com.zoomzhao.racingclub

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@RunWith(AndroidJUnit4::class)
class CatalogIntegrationTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context=instrumentation.targetContext
 private val device=UiDevice.getInstance(instrumentation)
 private val dir=File(context.getExternalFilesDir(null),"validation").apply{mkdirs()}
 private fun fresh(){if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.clearCache()}
 private fun find(selector:BySelector,timeout:Long=7000):UiObject2 {
  val until=SystemClock.uptimeMillis()+timeout
  while(SystemClock.uptimeMillis()<until){fresh();device.findObject(selector)?.let{return it};SystemClock.sleep(80)}
  device.takeScreenshot(File(dir,"catalog-failure.png"));throw AssertionError("Missing $selector")
 }
 private fun tap(label:String){find(By.desc(label)).click()}
 private fun choose(label:String,letter:String){tap("入口 $letter");find(By.text("入口 $letter"));val c=find(By.text(if(label=="选择起点")"设为起点"else"设为终点")).visibleCenter;device.click(c.x,c.y);find(By.text("${if(label=="选择起点")"起点"else"终点"} $letter"))}
 private fun nodeState(description:String):String {
  fresh()
  fun walk(n:android.view.accessibility.AccessibilityNodeInfo?):String?{if(n==null)return null;if(n.contentDescription?.toString()==description)return n.stateDescription?.toString();for(i in 0 until n.childCount)walk(n.getChild(i))?.let{return it};return null}
  return walk(instrumentation.uiAutomation.rootInActiveWindow).orEmpty()
 }
 private fun awaitRunning(){val end=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<end){if(nodeState("马儿").contains("跑动中"))return;SystemClock.sleep(50)};fail("Horse must start automatically")}
 private fun capture(name:String){assertTrue(device.takeScreenshot(File(dir,"$name.png")))}
 @Test fun all80NativeRulesReplayIncludingLoopsAndUnknownEndpoints() {
  val rules=loadRules(context);assertEquals(80,rules.levels.size);assertEquals((1..10).toList(),rules.pieces.keys.sorted())
  val rows=JSONArray()
  for(l in rules.levels){
   val route=rules.trace(l,l.solution,l.solutionStart,l.solutionFinish)
   assertNotNull("Level ${l.id}",route);route!!
   assertEquals(l.sequence,route.mapNotNull{it.jump})
   assertEquals(l.solutionStart,rules.endpoint(route,false));assertEquals(l.solutionFinish,rules.endpoint(route,true))
   assertNull("Missing tile ${l.id}",rules.trace(l,l.solution.dropLast(1)))
   assertNull("Duplicate tile ${l.id}",rules.trace(l,l.solution+l.solution.first()))
   var prior:Point?=null
   for(step in route){
    val start=rules.position(step,0f);val end=rules.position(step,1f);val pp=rules.ports(step.p)
    fun close(a:Point,b:Point){assertEquals(a.x,b.x,.0001f);assertEquals(a.y,b.y,.0001f)}
    close(pp[step.from],start);close(pp[step.to],end);prior?.let{close(it,start)};prior=end
   }
   if(l.id>=65){assertTrue(l.solveEndpoints);assertEquals("",l.start);assertEquals("",l.finish)}
   rows.put(JSONObject().put("level",l.id).put("tiles",l.requiredPieces.size).put("crossings",l.sequence.size).put("roadTraversals",route.size).put("start",rules.endpoint(route,false)).put("finish",rules.endpoint(route,true)))
  }
  val l6=rules.levels[5];assertTrue(rules.trace(l6,l6.solution)!!.any{it.p.id==7&&it.jump==null})
  val l17=rules.levels[16];assertEquals(2,rules.trace(l17,l17.solution)!!.count{it.jump==5})
  val l80=rules.levels.last();assertEquals(14,l80.sequence.size);assertEquals(9,l80.requiredPieces.size)
  File(dir,"catalog-rules-report.json").writeText(JSONObject().put("passed",true).put("levels",rows).toString(2))
 }
 @Test fun nativeChapterNavigationTrayAndPlayerSelectedEndpoints() {
  Configurator.getInstance().waitForIdleTimeout=100
  context.getSharedPreferences("progress-v1",0).edit().clear().commit()
  context.getSharedPreferences("leaderboard-v1",0).edit().clear().commit()
  ActivityScenario.launch(MainActivity::class.java).use{scenario->
   find(By.text("马术训练场"));capture("20-catalog-home")
   for(chapter in 0..4){
    val lo=(chapter*16+1).toString().padStart(2,'0');val hi=(chapter+1)*16
    tap("关卡 $lo–$hi")
    for(id in chapter*16+1..hi)find(By.desc("进入挑战 ${id.toString().padStart(2,'0')}"))
   }
   capture("21-catalog-final-chapter");tap("进入挑战 80")
   find(By.text("起点 ?"));find(By.text("终点 ?"));capture("22-level80-empty")
   val sequence=find(By.desc("障碍顺序"));assertFalse(sequence.isScrollable)
   val level=loadRules(context).levels.last()
   level.sequence.forEachIndexed{i,id->assertTrue(sequence.visibleBounds.contains(find(By.desc("目标 ${i+1}：${id}号")).visibleBounds))}
   capture("23-level80-complete-goals")
   val ids=listOf(1,2,3,4,5,6,8,9,10);val bounds=ids.map{find(By.desc("拼板 $it")).visibleBounds}
   assertTrue("Second tray row must be reachable",bounds.last().bottom<device.displayHeight)
   bounds.take(5).zipWithNext().forEach{(a,b)->assertTrue(a.centerX()<b.centerX())}
   bounds.drop(5).zipWithNext().forEach{(a,b)->assertTrue(a.centerX()<b.centerX())}
   assertTrue(bounds[5].centerY()>bounds[4].centerY());assertTrue("Uniform tray columns",bounds.maxOf{it.width()}-bounds.minOf{it.width()}<=3)
   choose("选择起点","A")
   choose("选择终点","B");capture("24-level80-endpoints")
   tap("返回");tap("进入挑战 80");find(By.text("起点 A"));find(By.text("终点 B"))
   tap("重置");find(By.text("起点 ?"));find(By.text("终点 ?"))
   repeat(9){tap("提示")}
   find(By.text("起点 ?"));capture("25-level80-placed")
   val movie=File(dir,"challenge-80.mp4")
   val recording=Thread({device.executeShellCommand("screenrecord --time-limit 45 --size 1280x720 --bit-rate 4000000 ${movie.absolutePath}")},"level80-recording").apply{isDaemon=true;start()}
   SystemClock.sleep(250)
   choose("选择起点","A");choose("选择终点","B");SystemClock.sleep(500);assertFalse(nodeState("马儿").contains("跑动中"));choose("选择终点","A")
   awaitRunning();assertFalse(device.hasObject(By.text("路线正确，马儿出发！")));capture("26-level80-running")
   SystemClock.sleep(2000);capture("26-level80-turning")
   find(By.text("漂亮，顺利通关！"),45000);device.executeShellCommand("pkill -2 screenrecord");recording.join(2000);assertTrue(movie.length()>1000);capture("27-level80-win")
   find(By.text("返回训练场")).click();find(By.text("1 / 80 已完成"));capture("28-level80-completed")
   tap("进入挑战 65");tap("提示");choose("选择起点","C")
   choose("选择终点","D");tap("返回")
   scenario.recreate();find(By.text("马术训练场"));SystemClock.sleep(300);tap("关卡 65–80");tap("进入挑战 65")
   find(By.text("起点 C"));find(By.text("终点 D"));assertTrue(nodeState("棋盘").contains("号"));capture("29-level65-restored")
  }
 }
 @Test fun laterLevelScoresDoNotEvictEarlyLevelBests() {
  context.getSharedPreferences("leaderboard-v1",0).edit().clear().commit()
  val repo=LocalLeaderboard(context)
  for(level in 1..80)repeat(12){repo.save(Score(level,1000L+it,12-it,if(it==0)1 else 0))}
  for(level in 1..80){assertEquals(10,repo.list(level).size);assertEquals(1001L,repo.list(level).first().millis)}
 }
}
