package com.zoomzhao.racingclub

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Real device input and wall-clock animations; no Compose test-clock fast forwarding. */
@RunWith(AndroidJUnit4::class)
class EmulatorFlowTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val device=UiDevice.getInstance(instrumentation)
    private val dir=File(context.getExternalFilesDir(null),"validation")
    private val events=JSONArray()
    private var recording:Thread?=null
    private var recordingFile:File?=null
    private var observedHorse:AccessibilityNodeInfo?=null
    private var observedTimer:AccessibilityNodeInfo?=null
    private var initialHorse=Rect()

    @Before fun prepare() {
        Configurator.getInstance().waitForIdleTimeout=100
        dir.mkdirs()
        context.getSharedPreferences("progress-v1",0).edit().clear().commit()
        context.getSharedPreferences("leaderboard-v1",0).edit().clear().commit()
    }
    private fun capture(name:String) {
        assertTrue("Screenshot $name",device.takeScreenshot(File(dir,"$name.png")))
        freshAccessibility()
        device.dumpWindowHierarchy(File(dir,"$name.xml"))
    }
    private fun freshAccessibility() {
        // API 35 may retain old Compose text nodes even after the rendered frame changes.
        if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.clearCache()
    }
    private fun find(selector:BySelector):UiObject2 {
        val deadline=SystemClock.uptimeMillis()+7000
        while(SystemClock.uptimeMillis()<deadline) {
            freshAccessibility()
            device.findObject(selector)?.let{return it}
            SystemClock.sleep(100)
        }
        capture("failure");throw AssertionError("Missing UI: $selector")
    }
    private fun text(value:String)=find(By.textContains(value))
    private fun tap(label:String) { find(By.desc(label)).click() }
    private fun nodeState(description:String):String {
        fun search(node:AccessibilityNodeInfo?):String? {
            if(node==null)return null
            if(node.contentDescription?.toString()==description)return node.stateDescription?.toString()
            for(i in 0 until node.childCount)search(node.getChild(i))?.let{return it}
            return null
        }
        freshAccessibility()
        return search(instrumentation.uiAutomation.rootInActiveWindow).orEmpty()
    }
    private fun boardState()=nodeState("棋盘")
    private fun cell(x:Int,y:Int) {
        val b=find(By.desc("棋盘")).visibleBounds
        val unit=b.width()/5.7f
        device.click((b.left+unit*(x+.85f)).toInt(),(b.top+unit*(y+.85f)).toInt())
        SystemClock.sleep(180)
    }
    private fun expectBoard(fragment:String) {
        val deadline=SystemClock.uptimeMillis()+3000
        while(SystemClock.uptimeMillis()<deadline) {
            if(boardState().contains(fragment))return
            SystemClock.sleep(80)
        }
        capture("failure-board")
        fail("Expected '$fragment', actual '${boardState()}'")
    }
    private fun note(name:String,value:Any) {events.put(JSONObject().put("name",name).put("value",value))}
    private fun start03() {
        // Wait for Activity launch, orientation and first-frame input readiness.
        instrumentation.waitForIdleSync()
        device.waitForIdle(2000)
        SystemClock.sleep(500)
        find(By.desc("进入挑战 03")).click()
        find(By.desc("棋盘"))
        assertTrue("Native activity must be landscape",device.displayWidth>device.displayHeight)
    }
    @Test fun landscapeLayoutAndInventory() {
        ActivityScenario.launch(MainActivity::class.java).use {
            text("马术训练场");capture("01-native-home")
            start03()
            val b=find(By.desc("棋盘")).visibleBounds
            assertTrue("Board must fit screen",b.left>=0&&b.top>=0&&b.right<=device.displayWidth&&b.bottom<=device.displayHeight)
            val labels=listOf("移回","撤销","提示","重置","全部 10 块")
            val bounds=labels.map {find(By.desc(it)).visibleBounds}
            bounds.forEach {r->assertTrue("Tool must be to right of board: $r vs $b",r.centerX()>b.right);assertTrue(r.width()>0&&r.right<=device.displayWidth)}
            assertTrue("All tools must share a row",bounds.maxOf{it.centerY()}-bounds.minOf{it.centerY()}<8)
            val rotateBounds=listOf("逆时针旋转90度","顺时针旋转90度").map{find(By.desc(it)).visibleBounds}
            rotateBounds.forEach{r->assertTrue("Large rotate target",r.width()>=120&&r.height()>=120);assertTrue("Rotate must be at bottom right",r.centerX()>b.right&&r.top>bounds.first().bottom)}
            assertTrue(rotateBounds[0].right<=rotateBounds[1].left)
            assertTrue(kotlin.math.abs(rotateBounds[0].centerY()-rotateBounds[1].centerY())<8)
            val rack=listOf(1,5,6).map{find(By.desc("拼板 $it")).visibleBounds.centerX()}
            assertEquals("Tray must be sorted by number",rack.sorted(),rack)
            assertFalse("No trial-run button",device.hasObject(By.textContains("出发试跑")))
            capture("02-native-landscape")
            tap("全部 10 块");for(id in 1..10)find(By.text(id.toString()))
            capture("03-native-inventory");text("知道了").click()
            note("landscape",JSONObject().put("width",device.displayWidth).put("height",device.displayHeight).put("board",b.flattenToString()).put("toolbar",JSONArray(bounds.map{it.flattenToString()})))
            File(dir,"layout-report.json").writeText(events.toString(2))
        }
    }
    @Test fun manualSelectionRotationAndBothAutoCompletions() {
        ActivityScenario.launch(MainActivity::class.java).use {
            start03()
            tap("拼板 5");tap("拼板 1")
            tap("逆时针旋转90度");cell(1,1)
            expectBoard("1号，第2行第2列，270度")
            tap("顺时针旋转90度");expectBoard("1号，第2行第2列，0度")
            tap("顺时针旋转90度")
            expectBoard("1号，第2行第2列，90度")
            capture("04-native-selected-90")

            cell(1,1);tap("逆时针旋转90度");expectBoard("1号，第2行第2列，0度")
            tap("顺时针旋转90度");expectBoard("1号，第2行第2列，90度");tap("顺时针旋转90度")
            expectBoard("1号，第2行第2列，180度")
            capture("05-native-rotated-placed")
            tap("撤销");expectBoard("1号，第2行第2列，90度")

            tap("拼板 5");cell(0,3);expectBoard("5号，第4行第1列，0度")
            cell(0,3);tap("顺时针旋转90度");text("旋转后重叠或越界，已保留原位")
            tap("逆时针旋转90度");expectBoard("5号，第4行第1列，0度");capture("06-native-invalid-rotation")
            cell(3,3);expectBoard("5号，第4行第4列，0度")
            cell(0,3);expectBoard("5号，第4行第1列，0度")
            tap("移回");assertFalse(boardState().contains("5号"))
            tap("拼板 5");cell(0,3);tap("拼板 6")
            capture("07-native-ready")
            beginRecording("challenge-03")
            cell(0,0);observeRun("08-course03",3)
            text("漂亮，顺利通关！");capture("09-native-win03")
            text("查看本关排行榜").click();text("独立完成");capture("10-native-ranking03");text("知道了").click()

            text("挑战下一关").click();text("挑战 04")
            val rack=listOf(1,2,3,5).map{find(By.desc("拼板 $it")).visibleBounds.centerX()}
            assertEquals(rack.sorted(),rack)
            repeat(3){tap("提示")}
            beginRecording("challenge-04")
            tap("提示");observeRun("11-course04",4)
            text("漂亮，顺利通关！");capture("12-native-win04")
            text("查看本关排行榜").click();text("使用提示");capture("13-native-ranking04")
            File(dir,"flow-report.json").writeText(JSONObject().put("passed",true).put("events",events).toString(2))
        }
    }
    private fun beginRecording(name:String) {
        freshAccessibility()
        fun observe(node:AccessibilityNodeInfo?) {
            if(node==null)return
            if(node.contentDescription?.toString()=="马儿")observedHorse=node
            if(node.text?.toString()?.matches(Regex("[0-9]{2}:[0-9]{2}\\.[0-9]{2}"))==true)observedTimer=node
            for(i in 0 until node.childCount)observe(node.getChild(i))
        }
        observe(instrumentation.uiAutomation.rootInActiveWindow)
        requireNotNull(observedHorse).getBoundsInScreen(initialHorse)
        requireNotNull(observedTimer)
        val file=File(dir,"$name.mp4")
        recordingFile=file
        recording=Thread({device.executeShellCommand("screenrecord --time-limit 14 --size 1280x720 --bit-rate 4000000 ${file.absolutePath}")},"native-screenrecord").apply{isDaemon=true;start()}
        SystemClock.sleep(250)
    }
    private fun observeRun(prefix:String,count:Int) {
        // Retain native node handles before animation. Refreshing these two nodes
        // avoids UI Automator's idle wait and a full accessibility-tree traversal.
        data class Frame(val timer:String,val horse:Rect?,val running:Boolean,val heading:String)
        fun sample():Frame {
            val alive=observedHorse?.refresh()==true&&observedTimer?.refresh()==true
            val horse=if(alive)Rect().also{observedHorse!!.getBoundsInScreen(it)}else null
            return Frame(observedTimer?.text?.toString().orEmpty(),horse,alive&&horse!=initialHorse,observedHorse?.stateDescription?.toString().orEmpty())
        }
        var first=sample();val deadline=SystemClock.uptimeMillis()+2500
        while(!first.running&&SystemClock.uptimeMillis()<deadline){SystemClock.sleep(50);first=sample()}
        assertTrue("Automatic route animation must start",first.running)
        val frozen=first.timer
        assertTrue("Timer must be visible",frozen.isNotEmpty())
        val positions=mutableListOf<Rect>()
        val headings=mutableListOf<String>()
        val images=mutableListOf<android.graphics.Bitmap>()
        repeat(3){i->
            val frame=if(i==0)first else sample()
            assertTrue("Horse must still be running",frame.running)
            positions+=requireNotNull(frame.horse)
            headings+=frame.heading
            assertEquals("Timer must stop during animation",frozen,frame.timer)
            images+=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            if(i<2)SystemClock.sleep(600)
        }
        assertTrue("Horse must move along board: $positions",positions.map{it.centerX() to it.centerY()}.distinct().size>=3)
        assertTrue("Directional view must be exposed",headings.all{it.startsWith("朝向")})
        if(count==3)assertTrue("Horse must change its view while turning: $headings",headings.distinct().size>=2)
        text("漂亮，顺利通关！")
        device.executeShellCommand("pkill -2 screenrecord")
        recording?.join(2000)
        assertTrue("Native screen recording must be saved",recordingFile?.let{it.isFile&&it.length()>1000}==true)
        images.forEachIndexed{i,bitmap->
            File(dir,"$prefix-frame-$i.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        note("challenge-$count-animation",JSONObject().put("timer",frozen).put("horseBounds",JSONArray(positions.map{it.flattenToString()})).put("headings",JSONArray(headings)))
    }
}
