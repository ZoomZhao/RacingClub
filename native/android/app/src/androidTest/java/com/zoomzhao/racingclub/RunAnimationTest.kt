package com.zoomzhao.racingclub

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RunAnimationTest {
    @Test fun actualRailCrossingPulsesOnlyItsGoalAndSkipKeepsSingleFrozenScore() {
        val ins=InstrumentationRegistry.getInstrumentation();val device=UiDevice.getInstance(ins)
        Configurator.getInstance().waitForIdleTimeout=100
        val dir=File(ins.targetContext.getExternalFilesDir(null),"validation").apply{mkdirs()}
        ins.targetContext.getSharedPreferences("progress-v1",0).edit().clear().commit()
        ins.targetContext.getSharedPreferences("leaderboard-v1",0).edit().clear().commit()
        fun find(label:String):AccessibilityNodeInfo? {
            fun walk(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
                if(n==null)return null
                if(n.contentDescription?.toString()==label)return n
                for(i in 0 until n.childCount)walk(n.getChild(i))?.let{return it}
                return null
            }
            if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache()
            return walk(ins.uiAutomation.rootInActiveWindow)
        }
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            lateinit var s:GameState
            scenario.onActivity{a->s=GameState(a,loadRules(a),LocalLeaderboard(a));s.start(s.rules.levels[2]);a.setContent{MaterialTheme(typography=ClubTypography){RacingApp(s)}};repeat(3){s.hint()}}
            val end=SystemClock.uptimeMillis()+6000
            var pulsed=false;val observed=linkedSetOf<String>()
            fun state(n:AccessibilityNodeInfo?)=n?.stateDescription?.toString()?.takeIf{it.isNotEmpty()}?:n?.parent?.stateDescription?.toString().orEmpty()
            while(SystemClock.uptimeMillis()<end){
                val node=find("目标 1：5号")
                observed+=state(node)
                if(state(node).contains("完成动效中")){
                    assertEquals("等待跨越",state(find("目标 2：1号")))
                    pulsed=true;assertTrue(device.takeScreenshot(File(dir,"66-goal-crossing-pulse.png")));break
                }
                SystemClock.sleep(25)
            }
            File(dir,"goal-feedback-observed.txt").writeText(observed.joinToString("\n"))
            assertTrue("A real horse rail crossing must animate its corresponding goal: $observed",pulsed)
            var frozen=0L;var scoreCount=0;var moves=0
            scenario.onActivity{assertTrue(s.running);frozen=s.elapsed;scoreCount=s.repository.list(3).size;moves=s.moves}
            // Read a fresh native node while animated semantics are changing each frame.
            // UiAutomator's cached selector can otherwise briefly miss a visible button.
            var skip=find("跳过动画");val skipDeadline=SystemClock.uptimeMillis()+1500
            while(skip==null&&SystemClock.uptimeMillis()<skipDeadline){SystemClock.sleep(25);skip=find("跳过动画")}
            assertNotNull("Skip is available during the run",skip)
            val hit=android.graphics.Rect().also{skip!!.getBoundsInScreen(it)}
            device.click(hit.centerX(),hit.centerY())
            val settled=SystemClock.uptimeMillis()+2000
            while(SystemClock.uptimeMillis()<settled&&!device.hasObject(By.text("漂亮，顺利通关！"))){if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache();SystemClock.sleep(50)}
            scenario.onActivity{assertEquals("win",s.screen);assertFalse(s.running);assertEquals(frozen,s.elapsed);assertEquals(scoreCount,s.repository.list(3).size);assertEquals(moves,s.moves)}
            assertTrue(device.takeScreenshot(File(dir,"67-skipped-run-settlement.png")))
            SystemClock.sleep(500)
            scenario.onActivity{assertEquals("win",s.screen);assertEquals(1,s.repository.list(3).size);assertTrue(s.solved(3))}
            File(dir,"run-feedback-report.json").writeText("""{"passed":true,"checks":["actual obstacle crossing pulses its goal","future goal waits","skip available during run","skip settles immediately","timer and moves unchanged","score saved only once","animation cancellation cannot overwrite settlement"]}""")
        }
    }
}
