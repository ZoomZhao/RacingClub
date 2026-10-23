package com.zoomzhao.racingclub

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real MainActivity and its ViewModel through recreation, background and dialogs. */
@RunWith(AndroidJUnit4::class)
class LifecycleRegressionTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(ins)
    @Before fun clean() {
        Configurator.getInstance().waitForIdleTimeout=100
        ins.targetContext.getSharedPreferences("progress-v1",0).edit().clear().commit()
        ins.targetContext.getSharedPreferences("leaderboard-v1",0).edit().clear().commit()
    }
    private fun find(selector:BySelector,timeout:Long=7000):UiObject2 {
        val end=SystemClock.uptimeMillis()+timeout
        while(SystemClock.uptimeMillis()<end){if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache();device.findObject(selector)?.let{return it};SystemClock.sleep(80)}
        throw AssertionError("Missing $selector")
    }
    private fun game(scenario:ActivityScenario<MainActivity>):GameState{lateinit var s:GameState;scenario.onActivity{s=it.game};return s}
    private fun <T> read(scenario:ActivityScenario<MainActivity>,block:(GameState)->T):T{var v:T?=null;scenario.onActivity{v=block(it.game)};@Suppress("UNCHECKED_CAST")return v as T}
    private fun await(scenario:ActivityScenario<MainActivity>,what:String,timeout:Long=6000,test:(GameState)->Boolean) {
        val end=SystemClock.uptimeMillis()+timeout
        while(SystemClock.uptimeMillis()<end){if(read(scenario,test))return;SystemClock.sleep(50)}
        fail("$what: ${read(scenario){"screen=${it.screen}, foreground=${it.foreground}, running=${it.running}, progress=${it.runProgress}, dialog=${it.dialog}, scores=${it.repository.list(it.level.id).size}"}}")
    }
    private fun enter03(scenario:ActivityScenario<MainActivity>){find(By.desc("进入挑战 03")).click();find(By.desc("棋盘"));await(scenario,"level 3 open"){it.screen=="game"&&it.level.id==3}}

    @Test fun recreationKeepsLevelSelectionDialogAndClock() {
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            enter03(scenario)
            scenario.onActivity{val s=it.game;s.select(1);s.place(1,1);s.showDialog("help")}
            find(By.text("知道了"))
            val before=read(scenario){it.time()};val original=game(scenario)
            scenario.recreate()
            find(By.text("知道了"))
            scenario.onActivity{a->val s=a.game
                assertSame("ViewModel keeps the session",original,s)
                assertEquals("game",s.screen);assertEquals(3,s.level.id);assertEquals(1,s.selected);assertEquals("help",s.dialog)
                assertEquals(listOf(Placement(1,1,1,0)),s.placed);assertEquals(1,s.moves)
                assertEquals("Clock stays stopped behind the dialog",before,s.time());assertTrue(s.foreground)}
            find(By.text("知道了")).click()
            await(scenario,"Clock resumes after the dialog closes"){it.dialog.isEmpty()&&it.time()>before+300}
            find(By.text("挑战 03"));find(By.desc("拼板操作"))
        }
    }
    @Test fun recreationDuringRunContinuesToOneSettlement() {
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            enter03(scenario)
            scenario.onActivity{val s=it.game;repeat(3){s.hint()}}
            await(scenario,"Run starts automatically"){it.running&&it.runProgress>.05f}
            val progress=read(scenario){it.runProgress};val frozen=read(scenario){it.elapsed}
            scenario.recreate()
            await(scenario,"Run continues after recreation",3000){it.running&&it.runProgress>progress}
            await(scenario,"Run settles",20000){it.screen=="win"}
            find(By.text("漂亮，顺利通关！"))
            scenario.onActivity{val s=it.game;assertFalse(s.running);assertEquals(frozen,s.elapsed);assertEquals(1,s.repository.list(3).size)}
        }
    }
    @Test fun noRunOrScoreWhileBackgroundedThenOneRunOnReturn() {
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            enter03(scenario)
            scenario.onActivity{val s=it.game;repeat(2){s.hint()}}
            scenario.moveToState(Lifecycle.State.CREATED)
            val clock=read(scenario){it.time()}
            scenario.onActivity{it.game.hint()}
            SystemClock.sleep(1200)
            scenario.onActivity{val s=it.game
                assertFalse(s.foreground);assertNotNull("Board is solved",s.trace())
                assertFalse("No run in the background",s.running);assertTrue("No score in the background",s.repository.list(3).isEmpty())
                assertEquals("Clock stopped in the background",clock,s.time())}
            scenario.moveToState(Lifecycle.State.RESUMED)
            await(scenario,"Run starts once visible again"){it.running||it.screen=="win"}
            await(scenario,"Run settles",20000){it.screen=="win"}
            scenario.onActivity{assertEquals(1,it.game.repository.list(3).size)}
        }
    }
    @Test fun dialogOpenedRightAfterTheLastTileHoldsTheRun() {
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            enter03(scenario)
            // Same main-thread turn as the last tile: the settle delay cannot slip in between.
            scenario.onActivity{val s=it.game;repeat(3){s.hint()};s.showDialog("help")}
            SystemClock.sleep(1200)
            scenario.onActivity{val s=it.game;assertEquals("help",s.dialog);assertFalse(s.running);assertTrue(s.repository.list(3).isEmpty())}
            find(By.text("知道了")).click()
            await(scenario,"Run starts after the dialog closes"){it.running}
            await(scenario,"Run settles",20000){it.screen=="win"}
            scenario.onActivity{assertEquals(1,it.game.repository.list(3).size)}
        }
    }
}
