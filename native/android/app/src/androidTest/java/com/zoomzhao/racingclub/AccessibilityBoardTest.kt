package com.zoomzhao.racingclub

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
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

/** TalkBack-facing semantics for board tiles, and proof they never block real touch. */
@RunWith(AndroidJUnit4::class)
class AccessibilityBoardTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(ins)
    private fun node(description:String):AccessibilityNodeInfo? {
        if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache()
        fun walk(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(n==null)return null;if(n.contentDescription?.toString()==description)return n;for(i in 0 until n.childCount)walk(n.getChild(i))?.let{return it};return null}
        return walk(ins.uiAutomation.rootInActiveWindow)
    }
    private fun await(description:String):AccessibilityNodeInfo {
        val end=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<end){node(description)?.let{return it};SystemClock.sleep(60)}
        throw AssertionError("Missing $description")
    }
    private fun state(n:AccessibilityNodeInfo)=n.stateDescription?.toString()?.takeIf{it.isNotEmpty()}?:n.parent?.stateDescription?.toString().orEmpty()
    private fun custom(n:AccessibilityNodeInfo,label:String)=n.actionList.single{it.label?.toString()==label}
    private fun tap(r:Rect){val d=SystemClock.uptimeMillis();for(a in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(d,SystemClock.uptimeMillis(),a,r.exactCenterX(),r.exactCenterY(),0);ins.sendPointerSync(e);e.recycle();SystemClock.sleep(60)};SystemClock.sleep(200)}

    @Test fun boardTilesExposeSelectionAndActionsWithoutBlockingTouch() {
        Configurator.getInstance().waitForIdleTimeout=100
        ins.targetContext.getSharedPreferences("progress-v1",0).edit().clear().commit()
        val dir=File(ins.targetContext.getExternalFilesDir(null),"validation").apply{mkdirs()}
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            lateinit var s:GameState
            // 6 lies fully under 5; 1 is partly under 5.
            scenario.onActivity{a->s=GameState(a,loadRules(a),LocalLeaderboard(a));s.start(s.rules.levels[2]);s.select(1);s.place(0,0);s.select(6);s.place(1,0);s.select(5);s.place(1,0);s.clearSelection();a.setContent{MaterialTheme(typography=ClubTypography){RacingApp(s)}}}
            val one=await("棋盘上 1号拼板");await("棋盘上 5号拼板")
            assertNull("A fully covered tile has no board node",node("棋盘上 6号拼板"))
            assertTrue("It stays reachable from the tray",state(await("拼板 6")).contains("下层被遮挡"))
            assertTrue(state(one).contains("未选中"));assertTrue(state(one).contains("部分被遮挡"));assertTrue(state(one).contains("第1行第1列"))
            for(label in listOf("逆时针旋转90度","顺时针旋转90度","移回"))custom(one,label)
            assertTrue(one.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(200)
            scenario.onActivity{assertEquals(1,s.selected);assertEquals("Selecting raises the tile",1,s.placed.last().id)}
            assertTrue(state(await("棋盘上 1号拼板")).contains("已选中"))
            assertTrue(await("棋盘上 1号拼板").performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(200)
            scenario.onActivity{assertNull("Second activation dismisses",s.selected)}
            val turn=await("棋盘上 1号拼板");assertTrue(turn.performAction(custom(turn,"顺时针旋转90度").id));SystemClock.sleep(200)
            scenario.onActivity{assertEquals(Placement(1,0,0,1),s.placed.last());assertEquals(1,s.selected)}
            assertTrue(device.takeScreenshot(File(dir,"71-accessibility-rotated.png")))
            val back=await("棋盘上 1号拼板");assertTrue(back.performAction(custom(back,"移回").id));SystemClock.sleep(200)
            scenario.onActivity{assertTrue(s.placed.none{it.id==1})}
            assertNull(node("棋盘上 1号拼板"))
            // Real touches land where the semantics node sits and reach the board Canvas.
            val five=Rect().also{await("棋盘上 5号拼板").getBoundsInScreen(it)}
            tap(five);scenario.onActivity{assertEquals("Touch through the semantics node selects",5,s.selected)}
            tap(five);scenario.onActivity{assertNull("Second touch dismisses",s.selected)}
            // The tray card activates like a tap and raises the hidden tile.
            val rack=await("拼板 6")
            assertTrue("Tray owns click: ${rack.actionList.map{it.id to it.label}}",rack.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(200)
            scenario.onActivity{assertEquals(6,s.selected);assertEquals(6,s.placed.last().id)}
            assertNotNull(await("棋盘上 6号拼板"))
            File(dir,"accessibility-report.json").writeText("""{"passed":true,"checks":["visible board tiles expose nodes","fully covered tile only in tray","select and dismiss by accessibility click","rotate and move back custom actions","real touch passes through semantics nodes","tray click raises hidden tile"]}""")
        }
    }
}
