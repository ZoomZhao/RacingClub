package com.zoomzhao.racingclub

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Cream key with a soft wood lip; pressing sinks it onto the lip. Shared by every control. */
internal fun Modifier.gameControlFace(pressed:Boolean=false)=drawBehind {
    val r=10.dp.toPx();val lip=2.dp.toPx();val sink=if(pressed)lip else 0f
    val face=Rect(0f,sink,size.width,size.height-lip+sink)
    drawRoundRect(Color(0xffd6bd92),Offset(0f,lip),Size(size.width,size.height-lip),CornerRadius(r))
    drawRoundRect(Brush.verticalGradient(if(pressed)listOf(Color(0xfff1e4c8),Color(0xfff6ecd8))else listOf(Color(0xffffffff),Color(0xfffaf1e0))),face.topLeft,face.size,CornerRadius(r))
    drawRoundRect(Color(0xffe3cfac),face.topLeft,face.size,CornerRadius(r),style=Stroke(1.dp.toPx()))
}
