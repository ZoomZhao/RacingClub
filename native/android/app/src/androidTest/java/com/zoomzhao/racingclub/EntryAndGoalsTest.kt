package com.zoomzhao.racingclub

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
class EntryAndGoalsTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)
    private val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"validation").apply{mkdirs()}
    private data class Node(val bounds:Rect,val state:String,val scrollable:Boolean)
    private fun snapshot():Map<String,Node> {
        if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.clearCache()
        val result=mutableMapOf<String,Node>()
        fun walk(node:AccessibilityNodeInfo?) {
            if(node==null)return
            val name=node.contentDescription?.toString()
            if(!name.isNullOrEmpty())result[name]=Node(Rect().also{node.getBoundsInScreen(it)},node.stateDescription?.toString().orEmpty(),node.isScrollable)
            for(i in 0 until node.childCount)walk(node.getChild(i))
        }
        walk(instrumentation.uiAutomation.rootInActiveWindow)
        return result
    }
    private fun ready(level:Level,start:String=level.start,finish:String=level.finish):Map<String,Node> {
        val expected="${start.ifEmpty{"?"}} → ${level.sequence.joinToString(" → ")} → ${finish.ifEmpty{"?"}}"
        val end=SystemClock.uptimeMillis()+6000
        while(SystemClock.uptimeMillis()<end){val s=snapshot();if(s["障碍顺序"]?.state==expected&&level.sequence.indices.all{s.containsKey("目标 ${it+1}：${level.sequence[it]}号")})return s;SystemClock.sleep(50)}
        device.takeScreenshot(File(dir,"entry-goals-failure.png"));throw AssertionError("Goals not ready: ${level.id}")
    }
    private fun verifyGoals(l:Level,nodes:Map<String,Node>) {
        val banner=requireNotNull(nodes["障碍顺序"])
        assertFalse("Goals must not scroll",banner.scrollable)
        val names=listOf("目标起点","目标终点")+l.sequence.mapIndexed{i,id->"目标 ${i+1}：${id}号"}
        for(name in names){val n=requireNotNull(nodes[name]);assertTrue("Full goal $name in level ${l.id}",banner.bounds.contains(n.bounds));assertTrue("Legible goal $name",n.bounds.width()>=42&&n.bounds.height()>=42)}
        val screen=Rect(0,0,device.displayWidth,device.displayHeight)
        assertTrue(screen.contains(banner.bounds));assertTrue(screen.contains(requireNotNull(nodes["棋盘"]).bounds))
    }
    @Test fun everyLevelShowsAllGoalsAndLongInstructionsWrapWithoutScrolling() {
        Configurator.getInstance().waitForIdleTimeout=100
        val rules=loadRules(instrumentation.targetContext)
        val rows=JSONArray()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            lateinit var state:GameState
            scenario.onActivity{activity->
                state=GameState(activity,rules,LocalLeaderboard(activity));state.screen="game"
                activity.setContent{MaterialTheme(typography=ClubTypography){RacingApp(state)}}
            }
            for(l in rules.levels){
                scenario.onActivity{state.level=l;state.chosenStart="";state.chosenFinish=""}
                val nodes=ready(l);verifyGoals(l,nodes)
                val y=l.sequence.indices.map{nodes.getValue("目标 ${it+1}：${l.sequence[it]}号").bounds.centerY()}.distinct()
                if(l.sequence.size>8)assertTrue("Long sidebar goals wrap in full",y.size>=2)
                rows.put(JSONObject().put("level",l.id).put("visibleGoals",l.sequence.size).put("rows",y.size).put("scrollable",false))
                if(l.id in listOf(3,55,64,76,80)){
                    assertFalse(device.hasObject(By.text("拼板")))
                    assertFalse(device.hasObject(By.textContains("已选中")))
                    assertFalse(device.hasObject(By.text("选择一块拼板")))
                    assertFalse(device.hasObject(By.text("正确连接后自动出发")))
                    assertTrue(device.takeScreenshot(File(dir,"40-goals-${l.number}.png")))
                }
            }
            scenario.onActivity{activity->
                state.level=rules.levels[75]
                activity.setContent{CompositionLocalProvider(LocalDensity provides Density(activity.resources.displayMetrics.density,1.35f)){
                    MaterialTheme(typography=ClubTypography){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Box(Modifier.width(620.dp).fillMaxHeight()){RacingApp(state)}}}
                }}
            }
            verifyGoals(rules.levels[75],ready(rules.levels[75]))
            assertTrue(device.takeScreenshot(File(dir,"41-goals-76-large-font.png")))
            scenario.onActivity{state.screen="home";state.selectedDifficultyId="pundit"}
            val homeDeadline=SystemClock.uptimeMillis()+5000
            var home=snapshot()
            while(home["进入挑战 65"]==null&&SystemClock.uptimeMillis()<homeDeadline){SystemClock.sleep(50);home=snapshot()}
            assertNotNull("Narrow home must remain usable",home["进入挑战 65"])
            for((index,d) in rules.difficulties.withIndex()){
                val n=home.getValue("难度 ${d.label} ${d.originalLabel}");assertTrue(n.bounds.left>=0&&n.bounds.right<=device.displayWidth)
                val rank=device.findObject(By.text(listOf("I","II","III","IV","V")[index])).visibleBounds
                val label=device.findObject(By.text(d.label)).visibleBounds
                assertTrue("Rank and large label must not overlap",rank.bottom<=label.top+2)
            }
            assertFalse("No help on narrow home",device.hasObject(By.desc("玩法")))
            assertTrue(device.takeScreenshot(File(dir,"53-home-large-font.png")))
        }
        File(dir,"goals-visibility-report.json").writeText(JSONObject().put("passed",true).put("levels",rows).put("largeFontScale",1.35).put("narrowWidthDp",620).toString(2))
    }
    @Test fun startFinishAndSharedEntryKeepEveryOriginalLetterPosition() {
        val rules=loadRules(instrumentation.targetContext);val level=rules.levels[64]
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            lateinit var state:GameState
            scenario.onActivity{activity->state=GameState(activity,rules,LocalLeaderboard(activity));state.level=level;state.screen="game";activity.setContent{MaterialTheme(typography=ClubTypography){RacingApp(state)}}}
            val original=ready(level)
            for(letter in rules.gates.keys){
                scenario.onActivity{state.chosenStart=letter;state.chosenFinish=letter}
                val both=ready(level,letter,letter)
                for(name in rules.gates.keys)assertEquals("Letter $name moved for $letter",original.getValue("入口 $name").bounds,both.getValue("入口 $name").bounds)
                assertEquals("起点、终点",both.getValue("入口 $letter").state)
                assertTrue(device.takeScreenshot(File(dir,"42-shared-entry-$letter.png")))
            }
            scenario.onActivity{state.chosenStart="E";state.chosenFinish="B"}
            ready(level,"E","B");assertTrue(device.takeScreenshot(File(dir,"43-separate-entry-E-B.png")))
        }
    }
}
