package com.zoomzhao.racingclub

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native renders for human inspection of actual sprite views and gate placement. */
@RunWith(AndroidJUnit4::class)
class DirectionalVisualTest {
    @Test fun eightHorseViewsAndEveryGateBoundaryRender() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val device=UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout=100
        fun awaitText(value:String):UiObject2? {
            val end=SystemClock.uptimeMillis()+5000
            while(SystemClock.uptimeMillis()<end){
                if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.clearCache()
                device.findObject(By.text(value))?.let{return it}
                SystemClock.sleep(100)
            }
            return null
        }
        val output=File(instrumentation.targetContext.getExternalFilesDir(null),"validation").apply{mkdirs()}
        fun capture(name:String){assertTrue(device.takeScreenshot(File(output,"$name.png")))}
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                MaterialTheme(typography=ClubTypography){Surface(color=Paper){DirectionalGallery()}}
            } }
            assertNotNull(awaitText("八向马儿与平面入口"))
            capture("14-directional-assets")
            for((gate,opposite)in listOf("A" to "D","B" to "E","C" to "A","F" to "B")) {
                scenario.onActivity { activity ->
                    val rules=loadRules(activity)
                    val state=GameState(activity,rules,LocalLeaderboard(activity))
                    state.level=rules.levels.first().copy(start=opposite,finish=gate)
                    state.screen="game"
                    activity.setContent {
                        MaterialTheme(typography=ClubTypography){Surface(color=Paper){
                            Column(Modifier.fillMaxSize().padding(horizontal=28.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                Text("门位置验证 · $gate",fontSize=18.sp,lineHeight=24.sp,color=Green,fontWeight=FontWeight.Bold)
                                NativeBoard(state,0f,Modifier.weight(1f).fillMaxWidth())
                            }
                        }}
                    }
                }
                assertNotNull(awaitText("门位置验证 · $gate"))
                assertNotNull(device.findObject(By.desc("入口 $gate")))
                val board=device.findObject(By.desc("棋盘")).visibleBounds
                assertTrue(board.left>=0&&board.right<=device.displayWidth&&board.bottom<=device.displayHeight)
                capture("15-gate-$gate")
            }
        }
    }
}

@Composable private fun DirectionalGallery() {
    val sprites=gameSprites()
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("八向马儿与平面入口",fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold,color=Green)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            HorseHeading.entries.forEach { heading ->
                Column(Modifier.weight(1f).background(Color(0xfffffaf0),RoundedCornerShape(10.dp)).border(1.dp,Color(0xffe8d4b8),RoundedCornerShape(10.dp)).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(91.dp)){paintSprite(sprites.horses.getValue(heading),Rect(Offset.Zero,size))}
                    Text(heading.label,fontSize=11.sp,lineHeight=17.sp,color=Green)
                }
            }
        }
        Text("原字母位置 · 外侧起点箭头与俯视终点棋格",fontSize=13.sp,lineHeight=19.sp,color=Brown)
        Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            BoundarySide.entries.forEach { side ->
                Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xfffffaf0),RoundedCornerShape(10.dp)).border(1.dp,Color(0xffe8d4b8),RoundedCornerShape(10.dp)).padding(9.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().weight(1f)){val outward=when(side){BoundarySide.N->Offset(0f,-1f);BoundarySide.E->Offset(1f,0f);BoundarySide.S->Offset(0f,1f);BoundarySide.W->Offset(-1f,0f)};paintEntryMarker("A",size.let{Offset(it.width/2,it.height/2)},outward,kotlin.math.min(size.width,size.height)*.8f,true,true)}
                    Text(when(side){BoundarySide.N->"上边 · A";BoundarySide.E->"右边 · B";BoundarySide.S->"下边 · C / D";BoundarySide.W->"左边 · E / F"},fontSize=11.sp,lineHeight=18.sp,color=Green,modifier=Modifier.padding(top=6.dp))
                }
            }
        }
    }
}
