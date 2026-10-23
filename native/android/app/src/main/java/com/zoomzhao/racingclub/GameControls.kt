package com.zoomzhao.racingclub

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.*

/** Cream ceramic face, bronze lip and inset highlight, from the ImageGen design. */
internal fun Modifier.gameControlFace(pressed:Boolean=false)=drawBehind {
    val r=7.dp.toPx();val lip=if(pressed)1.dp.toPx()else 2.dp.toPx()
    val rect=Rect(1.dp.toPx(),1.dp.toPx(),size.width-1.dp.toPx(),size.height-lip)
    drawRoundRect(Color(0xff8d5b31).copy(alpha=.22f),rect.topLeft+Offset(0f,lip),rect.size,CornerRadius(r))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xffc28d51),Color(0xff936032))),rect.topLeft,rect.size,CornerRadius(r))
    val inset=1.1.dp.toPx();val face=Rect(rect.left+inset,rect.top+inset,rect.right-inset,rect.bottom-inset)
    drawRoundRect(Brush.verticalGradient(if(pressed)listOf(Color(0xfff0dfbd),Color(0xfffff7e5))else listOf(Color(0xfffffdf4),Color(0xfff6e7ca))),face.topLeft,face.size,CornerRadius(r-inset))
    drawRoundRect(Color.White.copy(alpha=.85f),face.topLeft+Offset(inset,inset),Size(face.width-2*inset,face.height-2*inset),CornerRadius(r-2*inset),style=Stroke(.6.dp.toPx()))
}

@Composable internal fun RotationPopover(s:GameState,width:Dp,center:Float,visible:Boolean) {
    val popWidth=104f;val left=(center-popWidth/2).coerceIn(0f,(width.value-popWidth).coerceAtLeast(0f))
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        if(visible)Box(Modifier.offset(left.dp,0.dp).width(popWidth.dp).height(52.dp)) {
            Canvas(Modifier.fillMaxSize()){
                val tip=(center-left).coerceIn(10f,popWidth-10).dp.toPx();val y=6.dp.toPx()
                val path=Path().apply{moveTo(tip-6.dp.toPx(),y);lineTo(tip,0f);lineTo(tip+6.dp.toPx(),y);close()}
                drawPath(path,Color(0xfffff7e5));drawLine(Color(0xffbd8d56),Offset(tip-6.dp.toPx(),y),Offset(tip,0f),1.dp.toPx());drawLine(Color(0xffbd8d56),Offset(tip,0f),Offset(tip+6.dp.toPx(),y),1.dp.toPx())
            }
            Row(Modifier.padding(top=6.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                for(turn in listOf(-1,1)){
                    val interaction=remember{MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
                    Box(Modifier.weight(1f).height(44.dp).gameControlFace(pressed).clip(RoundedCornerShape(7.dp)).clickable(enabled=!s.running,interactionSource=interaction,indication=null,role=Role.Button){s.rotate(turn)}.semantics{contentDescription=if(turn<0)"逆时针旋转90度"else"顺时针旋转90度"},contentAlignment=Alignment.Center){
                        Canvas(Modifier.size(29.dp,34.dp)){
                            // Matching hand-drawn quarter-turn glyphs, with compact 90° notation.
                            val u=size.width/29f;val c=Offset(size.width/2,11*u);val radius=8*u
                            val angle=if(turn>0)-130f else -50f;val sweep=if(turn>0)280f else -280f
                            drawArc(Green,angle,sweep,false,c-Offset(radius,radius),Size(radius*2,radius*2),style=Stroke(2.2f*u,cap=StrokeCap.Round))
                            val end=(angle+sweep)*PI.toFloat()/180;val p=c+Offset(cos(end)*radius,sin(end)*radius);val tangent=Offset(-sin(end)*turn,cos(end)*turn)
                            val arrow=Path().apply{moveTo(p.x+tangent.x*3*u,p.y+tangent.y*3*u);lineTo(p.x-tangent.x*3*u-cos(end)*3*u,p.y-tangent.y*3*u-sin(end)*3*u);lineTo(p.x-tangent.x*3*u+cos(end)*3*u,p.y-tangent.y*3*u+sin(end)*3*u);close()};drawPath(arrow,Green)
                            drawContext.canvas.nativeCanvas.drawText("90°",size.width/2,31*u,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=0xff254c3b.toInt();textSize=10.5f*u;textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true})
                        }
                    }
                }
            }
        }
    }
}
