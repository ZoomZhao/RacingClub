package com.zoomzhao.racingclub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
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

private val Gold=Color(0xffd2a157)
private fun tint(d:Difficulty)=Color(android.graphics.Color.parseColor(d.color))

/** The frame is drawn natively, leaving all content and hit targets accessible. */
private fun DrawScope.plaque(top:Float=0f,active:Color?=null) {
    val p=1.dp.toPx();val r=8*p
    val frame=Rect(p,top+p,size.width-p,size.height-2*p)
    drawRoundRect(Color(0xff704928).copy(alpha=.19f),frame.topLeft+Offset(0f,2*p),frame.size,CornerRadius(r))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xfff2cd93),Color(0xffb57d42),Color(0xff82512d))),frame.topLeft,frame.size,CornerRadius(r))
    val face=Rect(frame.left+2*p,frame.top+2*p,frame.right-2*p,frame.bottom-3*p)
    drawRoundRect(if(active!=null)Brush.verticalGradient(listOf(active,lerp(active,Color.Black,.20f)))else Brush.verticalGradient(listOf(Color(0xfffffbef),Color(0xfff7e6c6))),face.topLeft,face.size,CornerRadius(6*p))
    drawRoundRect(if(active!=null)Gold else Color(0xfff7ddb0),frame.topLeft+Offset(p,p),Size(frame.width-2*p,frame.height-2*p),CornerRadius(r-p),style=Stroke(p))
    drawRoundRect(if(active!=null)Color.White.copy(alpha=.19f)else Color.White.copy(alpha=.85f),face.topLeft+Offset(p,p),Size(face.width-2*p,face.height-2*p),CornerRadius(5*p),style=Stroke(.55f*p))
    if(active==null) {
        // Subtle engraved grain, rather than a flat dashboard surface.
        for(i in 0..3){val y=face.top+(i+1)*face.height/5;drawLine(Color(0xffbf915c).copy(alpha=.06f),Offset(face.left+8*p,y),Offset(face.right-8*p,y-.7f*p),.6f*p)}
    }
}

@Composable internal fun DifficultyPlaque(d:Difficulty,index:Int,active:Boolean,modifier:Modifier,onClick:()->Unit) {
    val color=tint(d)
    val fontScale=LocalDensity.current.fontScale
    val range="${d.firstLevel.toString().padStart(2,'0')}–${d.lastLevel}"
    Box(modifier.height((48f+max(0f,fontScale-1f)*30f).dp).clip(RoundedCornerShape(8.dp)).clickable(role=Role.Tab,onClick=onClick).semantics{contentDescription="难度 ${d.label} ${d.originalLabel}";stateDescription="$range，${if(active)"已选择"else"未选择"}";selected=active}) {
        Canvas(Modifier.matchParentSize()) {
            val p=1.dp.toPx();plaque(8*p,if(active)color else null)
            val c=Offset(size.width/2,10*p)
            for(side in listOf(-1,1))for(i in 0..3){
                val leaf=c+Offset(side*(12f+(3-i)*.75f)*p,(-3f+i*3f)*p)
                rotate(side*(38f+i*10f),leaf){drawOval(if(active)Gold else color.copy(alpha=.68f),leaf-Offset(1.4f*p,2.8f*p),Size(2.8f*p,5.6f*p))}
            }
            drawCircle(Color(0xff785335).copy(alpha=.20f),10*p,c+Offset(0f,p))
            drawCircle(Gold,10*p,c);drawCircle(color,8.2f*p,c)
            drawCircle(Color(0xffffedbc),8.2f*p,c,style=Stroke(.55f*p))
        }
        Box(Modifier.align(Alignment.TopCenter).size(20.dp),contentAlignment=Alignment.Center){Text(listOf("I","II","III","IV","V")[index],fontSize=(11/LocalDensity.current.fontScale).sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,color=Color(0xffffedbc))}
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom=5.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(d.label,fontSize=11.sp,lineHeight=13.sp,fontWeight=FontWeight.Bold,color=if(active)Color.White else color,maxLines=1)
            Text(range,fontSize=8.sp,lineHeight=10.sp,color=if(active)Color(0xffffe8b8)else Brown,maxLines=1)
        }
    }
}

@Composable internal fun ChallengePlaque(s:GameState,l:Level,d:Difficulty,height:Dp,modifier:Modifier=Modifier) {
    val done=s.solved(l.id);val inProgress=!done&&s.inProgress(l.id);val best=s.best(l.id)
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(modifier.height(height).clip(RoundedCornerShape(9.dp)).clickable(role=Role.Button){s.start(l)}.semantics{contentDescription="进入挑战 ${l.number}";stateDescription=if(done)"已完成"else if(inProgress)"进行中"else"未开始"}) {
        val compact=maxWidth<100.dp||fontScale>1.15f
        Canvas(Modifier.matchParentSize()) {
            val p=1.dp.toPx();plaque(active=if(done)Green else null)
            // Small brass pins make the cream face read as a stable name plaque.
            for(x in listOf(5*p,size.width-5*p))for(y in listOf(5*p,size.height-7*p)){
                drawCircle(Color(0xff95612c),1.65f*p,Offset(x,y));drawCircle(Color(0xfff9d68a),.85f*p,Offset(x-.2f*p,y-.3f*p))
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal=9.dp,vertical=7.dp),verticalArrangement=Arrangement.SpaceBetween){
            Row(Modifier.fillMaxWidth().weight(1f),verticalAlignment=Alignment.CenterVertically){
                Text(l.number,fontSize=(26/fontScale.coerceAtMost(1.2f)).sp,lineHeight=29.sp,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,color=if(done)Color(0xfffff6df)else Green,maxLines=1)
                Spacer(Modifier.weight(1f))
                Icon(if(done)Icons.Default.CheckCircle else if(inProgress)Icons.Default.Schedule else Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(if(done)19.dp else 12.dp),tint=if(done)Color(0xffffd783)else if(inProgress)Color(0xffad6a1e)else Brown)
            }
            val details=if(best!=null)"%02d:%02d.%02d".format(best.millis/60000,best.millis/1000%60,best.millis%1000/10)else if(compact)"${l.requiredPieces.size}块 ·${l.sequence.size}跨"else"${l.requiredPieces.size} 块 · ${l.sequence.size} 跨"
            if(done)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                Text("已完成",fontSize=8.sp,lineHeight=11.sp,fontWeight=FontWeight.Bold,color=Color(0xffffe0a0),maxLines=1)
                Text(if(best!=null)details else "",fontSize=8.sp,lineHeight=11.sp,color=Color(0xfff8efdb),maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.End)
            }else Text(if(compact)details else "${d.label} · $details",fontSize=8.sp,lineHeight=11.sp,color=Brown,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
