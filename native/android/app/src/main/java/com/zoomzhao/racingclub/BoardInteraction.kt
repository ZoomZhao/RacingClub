package com.zoomzhao.racingclub

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

/** A drag previews geometry until a valid drop; selection may raise its tile first. */
internal class BoardDrag {
    var board by mutableStateOf(Rect.Zero)
    var piece by mutableStateOf<Placement?>(null)
    var pressing by mutableStateOf(false)
    var fromControls by mutableStateOf(false)
    var pointer by mutableStateOf(Offset.Zero)
    private var grab=Offset.Zero // in board cells, so grabbing a tile never jumps its anchor
    val unit get()=board.width/5.7f
    val liftOrigin get()=pointer-grab*unit
    fun begin(s:GameState,p:Placement,at:Offset,anchor:Offset?=null,fromControl:Boolean=false) {
        if(s.running||unit<=0)return
        s.select(p.id);s.rotation=p.r;fromControls=fromControl;piece=p;pointer=at
        val(w,h)=s.rules.dimensions(p.r)
        grab=anchor?:Offset(w/2f,h/2f)
    }
    fun candidate():Placement?=piece?.let{p->
        p.copy(x=((pointer.x-board.left)/unit-.35f-grab.x).roundToInt(),y=((pointer.y-board.top)/unit-.35f-grab.y).roundToInt())
    }
    fun problem(s:GameState):String {
        val p=candidate()?:return ""
        if(!board.contains(pointer))return "拖入棋盘内放置"
        return placementProblem(s.rules,p)
    }
    fun feedback(s:GameState):String {
        val error=problem(s);if(error.isNotEmpty())return error
        val p=candidate()?:return ""
        val ids=s.placed.filter{s.rules.overlaps(p,it)}.map{it.id}.sorted()
        return if(ids.isEmpty())"" else "将叠放在 ${ids.joinToString("、")} 号上方"
    }
    fun finish(s:GameState) {
        val p=candidate()?:return
        val error=problem(s)
        if(error.isEmpty()){if(s.placed.firstOrNull{it.id==p.id}!=p){s.rotation=p.r;s.place(p.x,p.y)}}
        else s.message=if(error=="拖入棋盘内放置")"未放入棋盘，已保留原位" else "$error，已保留原位"
        cancel()
    }
    fun cancel(){piece=null;pressing=false;fromControls=false}
}

internal fun placementProblem(rules:Rules,p:Placement):String {
    return if(rules.fits(p))"" else "超出棋盘边界"
}

internal data class PieceActionAnchor(val left:Float,val top:Float,val tipX:Float,val below:Boolean,val width:Float=140f,val height:Float=52f,val button:Float=44f,val vertical:Boolean=false,val inside:Boolean=false)
/** Prefer a free space beside the tile; a crowded board keeps controls on its owner. */
internal fun pieceActionAnchor(p:Placement,unit:Float,all:List<Placement> = emptyList()):PieceActionAnchor {
    val(w,h)=if(p.r%2==0)2 to 1 else 1 to 2
    val width=140f;val height=52f;val margin=4f;val gap=8f
    val center=(p.x+.35f+w/2f)*unit
    val sideMargin=.35f*unit+margin
    val rightLimit=(5.35f*unit-width-margin).coerceAtLeast(sideMargin)
    val tile=Rect((p.x+.35f)*unit,(p.y+.35f)*unit,(p.x+.35f+w)*unit,(p.y+.35f+h)*unit)
    val xs=listOf(center-width/2,tile.left,tile.right-width).map{it.coerceIn(sideMargin,rightLimit)}.distinct()
    val above=(p.y+.35f)*unit-gap-height
    for((top,below)in listOf(above to false,(tile.bottom+gap) to true))for(left in xs){
        val box=Rect(left,top,left+width,top+height)
        if(top<margin||box.bottom>4.7f*unit-margin||box.overlaps(tile))continue
        val blocks=all.any{o->if(o.id==p.id)false else{val(ow,oh)=if(o.r%2==0)2 to 1 else 1 to 2
            box.inflate(2f).overlaps(Rect((o.x+.35f)*unit,(o.y+.35f)*unit,(o.x+.35f+ow)*unit,(o.y+.35f+oh)*unit))}}
        if(!blocks)return PieceActionAnchor(left,top,(center-left).coerceIn(10f,width-10f),below)
    }
    // Leave the owner's number visible. Foundation retains the 48dp touch targets.
    val button=minOf(44f,(2*unit-8f)/3,.64f*unit-5f).coerceAtLeast(24f)
    val vertical=h>w;val barWidth=if(vertical)button else button*3+8
    val barHeight=if(vertical)button*3+8 else button
    val left=if(vertical)tile.right-3-button else tile.center.x-barWidth/2
    val top=if(vertical)tile.center.y-barHeight/2 else tile.bottom-3-button
    return PieceActionAnchor(left,top,0f,false,barWidth,barHeight,button,vertical,true)
}
