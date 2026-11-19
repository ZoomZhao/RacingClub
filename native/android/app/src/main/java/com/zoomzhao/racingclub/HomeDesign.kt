package com.zoomzhao.racingclub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlin.math.*

private fun tint(d:Difficulty)=Color(android.graphics.Color.parseColor(d.color))
/** Difficulty hue darkened just enough to read at 4.5:1 across the cream card face. */
private fun ink(d:Difficulty)=Color(readableInk(android.graphics.Color.parseColor(d.color).toLong() and 0xffffffffL,PlaqueFaceLight,PlaqueFaceDeep))

/** Chapter tab: roman numeral over the name and level range; filled with the chapter hue when chosen. */
@Composable internal fun DifficultyPlaque(d:Difficulty,index:Int,active:Boolean,modifier:Modifier,onClick:()->Unit) {
    val color=tint(d);val shape=RoundedCornerShape(12.dp)
    val fontScale=LocalDensity.current.fontScale
    val range="${d.firstLevel.toString().padStart(2,'0')}–${d.lastLevel}"
    Box(modifier.height((44f+max(0f,fontScale-1f)*24f).dp).clip(shape).background(if(active)color else Panel,shape).border(1.dp,if(active)color else Line,shape).clickable(role=Role.Tab,onClick=onClick).semantics{contentDescription="难度 ${d.label} ${d.originalLabel}";stateDescription="$range，${if(active)"已选择"else"未选择"}";selected=active}) {
        Column(Modifier.fillMaxSize().padding(vertical=3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Box(Modifier.size(16.dp).background(if(active)Color.White.copy(alpha=.22f)else color,CircleShape),contentAlignment=Alignment.Center){
                Text(listOf("I","II","III","IV","V")[index],fontSize=(9f/fontScale).sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,color=Color(0xfffff3d6),maxLines=1)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                Text(d.label,fontSize=13.sp,lineHeight=15.sp,fontWeight=FontWeight.Bold,color=if(active)Color.White else ink(d),maxLines=1)
                if(fontScale<=1.15f)Text(range,fontSize=10.sp,lineHeight=13.sp,color=if(active)Color(0xffffecc8)else Brown,maxLines=1)
            }
        }
    }
}

/** Level card: chapter-colored top edge, big serif number, one status line. Solved levels turn green. */
@Composable internal fun ChallengePlaque(s:GameState,l:Level,d:Difficulty,height:Dp,modifier:Modifier=Modifier) {
    val done=s.solved(l.id);val inProgress=!done&&s.inProgress(l.id);val best=s.best(l.id)
    val fontScale=LocalDensity.current.fontScale;val shape=RoundedCornerShape(12.dp)
    val amber=Color(InProgressInk)
    BoxWithConstraints(modifier.height(height).clip(shape).background(if(done)Green else Panel,shape).border(if(inProgress)1.5.dp else 1.dp,if(done)Green else if(inProgress)amber.copy(alpha=.55f)else Line,shape).clickable(role=Role.Button){s.start(l)}.semantics{contentDescription="进入挑战 ${l.number}";stateDescription=if(done)"已完成"else if(inProgress)"进行中"else"未开始"}) {
        val compact=maxWidth<100.dp||fontScale>1.15f
        Box(Modifier.fillMaxWidth().height(4.dp).background(if(done)Accent else tint(d).copy(alpha=.85f)))
        Column(Modifier.fillMaxSize().padding(start=11.dp,end=9.dp,top=9.dp,bottom=7.dp),verticalArrangement=Arrangement.SpaceBetween){
            Row(Modifier.fillMaxWidth().weight(1f),verticalAlignment=Alignment.CenterVertically){
                Text(l.number,fontSize=(26/fontScale.coerceAtMost(1.2f)).sp,lineHeight=29.sp,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,color=if(done)Color(0xfffff6df)else Green,maxLines=1)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(22.dp).background(if(done)Accent else if(inProgress)amber.copy(alpha=.12f)else Well,CircleShape),contentAlignment=Alignment.Center){
                    Icon(if(done)Icons.Default.Check else if(inProgress)Icons.Default.Schedule else Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(13.dp),tint=if(done)Color.White else if(inProgress)amber else Brown)
                }
            }
            val details=if(best!=null)"%02d:%02d.%02d".format(best.millis/60000,best.millis/1000%60,best.millis%1000/10)else if(compact)"${l.requiredPieces.size}块 ·${l.sequence.size}跨"else"${l.requiredPieces.size} 块 · ${l.sequence.size} 跨"
            if(done)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                Text("已完成",fontSize=10.sp,lineHeight=12.sp,fontWeight=FontWeight.Bold,color=Color(0xffffe0a0),maxLines=1)
                Text(if(best!=null)details else "",fontSize=10.sp,lineHeight=12.sp,color=Color(0xfff8efdb),maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.End)
            }else if(inProgress)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                // Unfinished boards say so in words; the clock icon alone was easy to miss.
                Text("进行中",fontSize=10.sp,lineHeight=12.sp,fontWeight=FontWeight.Bold,color=amber,maxLines=1)
                Text(details,fontSize=10.sp,lineHeight=12.sp,color=Brown,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
            }else Text(if(compact)details else "${d.label} · $details",fontSize=10.sp,lineHeight=12.sp,color=Brown,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
