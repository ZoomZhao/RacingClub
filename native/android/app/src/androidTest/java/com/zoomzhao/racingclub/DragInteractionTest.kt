package com.zoomzhao.racingclub

import android.graphics.PointF
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
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

/** Inject real continuous touch events, including held previews and cancellation. */
@RunWith(AndroidJUnit4::class)
class DragInteractionTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(ins)
    private var down=0L
    private fun node(label:String):UiObject2 {
        val end=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<end){if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache();device.findObject(By.desc(label))?.let{return it};SystemClock.sleep(60)}
        throw AssertionError("Missing $label")
    }
    private fun expectNoActions() {
        val end=SystemClock.uptimeMillis()+3000
        while(SystemClock.uptimeMillis()<end){
            if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache()
            if(!device.hasObject(By.desc("拼板操作")))return
            SystemClock.sleep(60)
        }
        fail("Removing tile must hide its contextual actions")
    }
    private fun event(action:Int,p:PointF){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,p.x,p.y,0);ins.sendPointerSync(e);e.recycle()}
    private fun dragTo(from:PointF,to:PointF){down=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,from);repeat(18){i->val t=(i+1)/18f;event(MotionEvent.ACTION_MOVE,PointF(from.x+(to.x-from.x)*t,from.y+(to.y-from.y)*t));SystemClock.sleep(22)};SystemClock.sleep(180)}
    private fun center(label:String)=node(label).visibleBounds.let{PointF(it.exactCenterX(),it.exactCenterY())}
    private fun anchor(x:Float,y:Float)=node("棋盘").visibleBounds.let{val u=it.width()/5.7f;PointF(it.left+(x+.35f)*u,it.top+(y+.35f)*u)}
    private fun assertPieceActions(p:Placement) {
        val b=node("棋盘").visibleBounds;val u=b.width()/5.7f
        val rect=Rect((b.left+(p.x+.35f)*u).toInt(),(b.top+(p.y+.35f)*u).toInt(),(b.left+(p.x+.35f+if(p.r%2==0)2 else 1)*u).toInt(),(b.top+(p.y+.35f+if(p.r%2==0)1 else 2)*u).toInt())
        val bar=node("拼板操作").visibleBounds
        assertTrue("Actions stay on board",b.contains(bar))
        assertFalse("Actions do not cover selected tile",Rect.intersects(rect,bar))
        val controls=listOf("逆时针旋转90度","顺时针旋转90度","移回").map{node(it).visibleBounds}
        for(r in controls){assertTrue("Contextual hit target stays on board: $bar, $r",b.contains(r));assertTrue(bar.contains(r.centerX(),r.centerY()));assertTrue(r.width()>=110&&r.height()>=110)}
        assertEquals(controls.map{it.centerX()}.sorted(),controls.map{it.centerX()})
    }
    @Test fun trayAndBoardDragPreviewConflictsUndoAndCancel() {
        Configurator.getInstance().waitForIdleTimeout=100
        val dir=File(ins.targetContext.getExternalFilesDir(null),"validation").apply{mkdirs()}
        ins.targetContext.getSharedPreferences("progress-v1",0).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            lateinit var s:GameState
            scenario.onActivity{a->s=GameState(a,loadRules(a),LocalLeaderboard(a));s.start(s.rules.levels[2]);a.setContent{MaterialTheme(typography=ClubTypography){RacingApp(s)}}}
            node("棋盘")
            assertFalse("No actions for unplaced tile",device.hasObject(By.desc("移回")))
            val first=anchor(2f,1.5f)
            dragTo(center("拼板 1"),first)
            scenario.onActivity{assertTrue("Preview must not commit",s.placed.isEmpty())}
            assertTrue(device.takeScreenshot(File(dir,"55-drag-valid-preview.png")))
            event(MotionEvent.ACTION_UP,first);SystemClock.sleep(220)
            scenario.onActivity{assertEquals(listOf(Placement(1,1,1,0)),s.placed);assertEquals(1,s.moves)}
            assertPieceActions(Placement(1,1,1,0))
            assertTrue(device.takeScreenshot(File(dir,"56-drag-placed-controls.png")))

            val moved=anchor(3f,2.5f)
            dragTo(first,moved);event(MotionEvent.ACTION_UP,moved);SystemClock.sleep(180)
            scenario.onActivity{assertEquals(Placement(1,2,2,0),s.placed.single())}
            node("撤销").click();SystemClock.sleep(150)
            scenario.onActivity{assertEquals(Placement(1,1,1,0),s.placed.single());assertEquals(1,s.moves)}

            val five=anchor(4f,.5f)
            dragTo(center("拼板 5"),five);event(MotionEvent.ACTION_UP,five);SystemClock.sleep(180)
            scenario.onActivity{assertTrue(s.placed.contains(Placement(5,3,0,0)))}
            dragTo(five,first)
            assertTrue(node("放置提示").text.orEmpty().contains("重叠") || device.hasObject(By.textContains("号拼板重叠")))
            scenario.onActivity{assertTrue("Conflict must preserve old tile",s.placed.contains(Placement(5,3,0,0)))}
            assertTrue(device.takeScreenshot(File(dir,"57-drag-overlap-preview.png")))
            event(MotionEvent.ACTION_UP,first);SystemClock.sleep(180)
            scenario.onActivity{assertTrue(s.message.contains("与 1 号拼板重叠"));assertEquals(2,s.moves)}
            node("撤销").click();SystemClock.sleep(150)
            scenario.onActivity{assertEquals(listOf(Placement(1,1,1,0)),s.placed);assertEquals(1,s.moves)}

            val edge=anchor(0f,1.5f)
            dragTo(first,edge);event(MotionEvent.ACTION_UP,edge);SystemClock.sleep(150)
            scenario.onActivity{assertEquals(listOf(Placement(1,1,1,0)),s.placed);assertTrue(s.message.contains("边界"));assertEquals(1,s.moves)}
            dragTo(first,moved);event(MotionEvent.ACTION_CANCEL,moved);SystemClock.sleep(150)
            scenario.onActivity{assertEquals(listOf(Placement(1,1,1,0)),s.placed);assertEquals(1,s.moves)}
            // Top and bottom edge placements keep all contextual operations reachable.
            val topLeft=anchor(1f,.5f)
            dragTo(first,topLeft);event(MotionEvent.ACTION_UP,topLeft);SystemClock.sleep(180)
            assertPieceActions(Placement(1,0,0,0))
            assertTrue(device.takeScreenshot(File(dir,"58-drag-edge-controls.png")))
            val bottomRight=anchor(4f,3.5f)
            dragTo(topLeft,bottomRight);event(MotionEvent.ACTION_UP,bottomRight);SystemClock.sleep(150)
            assertPieceActions(Placement(1,3,3,0))
            node("移回").click();SystemClock.sleep(150)
            scenario.onActivity{assertTrue("Remove acts on selected board piece",s.placed.isEmpty())}
            expectNoActions()
            node("撤销").click();SystemClock.sleep(150)
            scenario.onActivity{assertEquals(listOf(Placement(1,3,3,0)),s.placed)}
            assertPieceActions(Placement(1,3,3,0))
            assertTrue(device.takeScreenshot(File(dir,"61-board-actions-bottom-edge.png")))
            File(dir,"drag-report.json").writeText("""{"passed":true,"input":"native continuous MotionEvent","checks":["tray to board","board relocation","live uncommitted preview","numbered overlap feedback","invalid drops preserve position and move count","undo one successful drag","out of bounds","gesture cancellation","rotation and removal follow selected board piece; top and bottom edges; removal hides actions; undo restores"]}""")
        }
    }
    @Test fun secondRowSelectionAndTenTileCapacityKeepControlsReachable() {
        Configurator.getInstance().waitForIdleTimeout=100
        ins.targetContext.getSharedPreferences("progress-v1",0).edit().clear().commit()
        val dir=File(ins.targetContext.getExternalFilesDir(null),"validation").apply{mkdirs()}
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            lateinit var s:GameState
            scenario.onActivity{a->s=GameState(a,loadRules(a),LocalLeaderboard(a));s.start(s.rules.levels[75]);a.setContent{MaterialTheme(typography=ClubTypography){RacingApp(s)}}}
            node("拼板 10")
            val target=anchor(1f,.5f)
            dragTo(center("拼板 10"),target);event(MotionEvent.ACTION_UP,target);SystemClock.sleep(200)
            scenario.onActivity{assertEquals(listOf(Placement(10,0,0,0)),s.placed)}
            assertPieceActions(Placement(10,0,0,0))
            node("顺时针旋转90度").click();SystemClock.sleep(120)
            scenario.onActivity{assertEquals(Placement(10,0,0,1),s.placed.single())}
            assertTrue(device.takeScreenshot(File(dir,"59-long-goals-second-row.png")))
            node("拼板 8").click()
            assertFalse("Selecting unplaced tile hides board actions",device.hasObject(By.desc("拼板操作")))
            val initial=anchor(2.5f,.5f);device.click(initial.x.toInt(),initial.y.toInt());SystemClock.sleep(120)
            node("逆时针旋转90度").click()
            val rotated=anchor(3.5f,1f)
            dragTo(center("拼板 8"),rotated);event(MotionEvent.ACTION_UP,rotated);SystemClock.sleep(150)
            scenario.onActivity{assertTrue("Board rotation survives tray relocation",s.placed.contains(Placement(8,3,0,3)))}
            // A presentation fixture verifies capacity for all ten without changing the 80 real levels.
            scenario.onActivity{s.level=s.level.copy(sequence=(1..10).toList())}
            for(id in 1..10){val b=node("拼板 $id").visibleBounds;assertTrue(b.width()>0&&b.height()>=68&&b.bottom<device.displayHeight)}
            assertFalse("No all-pieces feature",device.hasObject(By.desc("全部 10 块")))
            assertTrue(device.takeScreenshot(File(dir,"60-ten-tile-capacity.png")))
        }
    }

}
