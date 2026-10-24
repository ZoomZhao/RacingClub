package com.zoomzhao.racingclub

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
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

@Composable internal fun BoardPieceActions(s:GameState,piece:Placement,unit:Float,drag:BoardDrag) {
    val actions=turnActions(s.turnMode)
    val anchor=pieceActionAnchor(piece,unit,s.placed,actions.size)
    val config=LocalViewConfiguration.current
    val density=androidx.compose.ui.platform.LocalDensity.current.density
    val controlsConfig=remember(config,density){object:androidx.compose.ui.platform.ViewConfiguration by config{override val touchSlop=max(config.touchSlop,12*density)}}
    Box(Modifier.offset(anchor.left.dp,anchor.top.dp).size(anchor.width.dp,anchor.height.dp).graphicsLayer{alpha=if(drag.piece==null)1f else 0f}.semantics{contentDescription="拼板操作";stateDescription="${piece.id}号${if(anchor.inside)"，贴合当前拼板"else""}"}) {
        if(!anchor.inside)Canvas(Modifier.fillMaxSize()){
            val tip=anchor.tipX.dp.toPx();val edge=if(anchor.below)8.dp.toPx()else 44.dp.toPx();val peak=if(anchor.below)0f else 52.dp.toPx()
            val path=Path().apply{moveTo(tip-6.dp.toPx(),edge);lineTo(tip,peak);lineTo(tip+6.dp.toPx(),edge);close()}
            drawPath(path,Color(0xfffff7e5));drawLine(Color(0xffbd8d56),Offset(tip-6.dp.toPx(),edge),Offset(tip,peak),1.dp.toPx());drawLine(Color(0xffbd8d56),Offset(tip,peak),Offset(tip+6.dp.toPx(),edge),1.dp.toPx())
        }
        @Composable fun buttons(){
            for(action in actions){
                val interaction=remember{MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
                var origin by remember{mutableStateOf(Offset.Zero)}
                Box(Modifier.size(anchor.button.dp).onGloballyPositioned{origin=it.positionInRoot()}.gameControlFace(pressed).clip(RoundedCornerShape(7.dp)).pointerInput(piece,s.running){
                    // Dragging off a control moves its tile; the token keeps that gesture exclusive.
                    var token:Any?=null
                    detectDragGestures(onDragStart={at->val t=Any();val pointer=origin+at;if(drag.begin(s,t,piece,pointer,(pointer-drag.board.topLeft)/drag.unit-Offset(piece.x+.35f,piece.y+.35f),fromControl=true))token=t},onDrag={change,delta->token?.let{t->change.consume();drag.moveTo(t,drag.pointer+delta)}},onDragEnd={token?.let{drag.finish(s,it)};token=null},onDragCancel={token?.let{drag.release(it)};token=null})
                }.clickable(enabled=!s.running&&!drag.busy,interactionSource=interaction,indication=null,role=Role.Button){if(action==0)s.removePiece(piece.id)else s.rotatePiece(piece.id,action)}.semantics{contentDescription=when(action){-1->"逆时针旋转90度";1->"顺时针旋转90度";else->"移回"}},contentAlignment=Alignment.Center){
                    if(action==0)Icon(Icons.Default.Unarchive,null,Modifier.size(24.dp),tint=Green)
                    else QuarterTurnGlyph(action)
                }
            }
        }
        CompositionLocalProvider(LocalViewConfiguration provides controlsConfig){
            if(anchor.vertical)Column(verticalArrangement=Arrangement.spacedBy(4.dp)){buttons()}
            else Row(Modifier.padding(top=if(anchor.below)8.dp else 0.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){buttons()}
        }
    }
}

@Composable private fun QuarterTurnGlyph(turn:Int) {
    Canvas(Modifier.size(29.dp,34.dp)){
        val u=size.width/29f;val c=Offset(size.width/2,11*u);val radius=8*u
        val angle=if(turn>0)-130f else -50f;val sweep=if(turn>0)280f else -280f
        drawArc(Green,angle,sweep,false,c-Offset(radius,radius),Size(radius*2,radius*2),style=Stroke(2.2f*u,cap=StrokeCap.Round))
        val end=(angle+sweep)*PI.toFloat()/180;val p=c+Offset(cos(end)*radius,sin(end)*radius);val tangent=Offset(-sin(end)*turn,cos(end)*turn)
        val arrow=Path().apply{moveTo(p.x+tangent.x*3*u,p.y+tangent.y*3*u);lineTo(p.x-tangent.x*3*u-cos(end)*3*u,p.y-tangent.y*3*u-sin(end)*3*u);lineTo(p.x-tangent.x*3*u+cos(end)*3*u,p.y-tangent.y*3*u+sin(end)*3*u);close()};drawPath(arrow,Green)
        drawContext.canvas.nativeCanvas.drawText("90°",size.width/2,31*u,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=0xff254c3b.toInt();textSize=10.5f*u;textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true})
    }
}
