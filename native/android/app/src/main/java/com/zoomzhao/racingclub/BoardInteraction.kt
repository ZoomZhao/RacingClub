package com.zoomzhao.racingclub

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

/** A drag previews geometry until a valid drop. The first finger on a tile, tray card
 * or control owns the gesture; any other finger can neither select, commit nor cancel. */
internal class BoardDrag {
    var board by mutableStateOf(Rect.Zero)
    var piece by mutableStateOf<Placement?>(null)
    var fromControls by mutableStateOf(false)
    var pointer by mutableStateOf(Offset.Zero)
    var owner by mutableStateOf<Any?>(null);private set
    val busy get()=owner!=null
    private var grab=Offset.Zero // in board cells, so grabbing a tile never jumps its anchor
    val unit get()=board.width/5.7f
    val liftOrigin get()=pointer-grab*unit
    fun claim(token:Any):Boolean{if(owner!=null&&owner!==token)return false;owner=token;return true}
    fun owns(token:Any)=owner===token
    fun begin(s:GameState,token:Any,p:Placement,at:Offset,anchor:Offset?=null,fromControl:Boolean=false):Boolean {
        if(s.running||unit<=0||!claim(token))return false
        s.select(p.id);fromControls=fromControl;piece=p;pointer=at
        val(w,h)=s.rules.dimensions(p.r)
        grab=anchor?:Offset(w/2f,h/2f)
        return true
    }
    fun moveTo(token:Any,at:Offset){if(owns(token)&&piece!=null)pointer=at}
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
    /** Commits the dragged tile by its own id, then ends only this gesture. */
    fun finish(s:GameState,token:Any) {
        if(!owns(token))return
        candidate()?.let{p->
            val error=problem(s)
            if(error.isEmpty()){if(s.placed.firstOrNull{it.id==p.id}!=p)s.placePiece(p)}
            else s.message=if(error=="拖入棋盘内放置")"未放入棋盘，已保留原位" else "$error，已保留原位"
        }
        release(token)
    }
    fun release(token:Any){if(!owns(token))return;owner=null;piece=null;fromControls=false}
}

internal fun placementProblem(rules:Rules,p:Placement):String {
    return if(rules.fits(p))"" else "超出棋盘边界"
}

/** Press selects at once. The returned flag says the tile was already the selected top
 * tile, so an unmoved release dismisses it instead of selecting again. */
internal fun GameState.pressTile(id:Int):Boolean {
    val was=selected==id&&(placed.none{it.id==id}||placed.last().id==id)
    select(id);return was
}
/** Taps on the board never move an already placed tile; only tray tiles are placed by tap. */
internal fun GameState.releaseBoardTap(hit:Placement?,wasSelected:Boolean,x:Int,y:Int,onLetter:Boolean) {
    when {
        running->{}
        hit!=null->if(wasSelected)clearSelection()
        x in 0..4&&y in 0..3->{val id=selected;if(id!=null&&placed.none{it.id==id})place(x,y)else clearSelection()}
        !onLetter->clearSelection()
    }
}
internal fun GameState.releaseRackTap(id:Int,wasSelected:Boolean){if(!running&&wasSelected&&placed.any{it.id==id})clearSelection()}
/** Accessibility click on a tray card or board tile: the same select-or-dismiss as a tap. */
internal fun GameState.tapTile(id:Int){releaseRackTap(id,pressTile(id))}
/** Tray tiles enter at their pending turn (0° unless turned); placed tiles keep their board turn. */
internal fun GameState.rackPiece(id:Int)=placed.firstOrNull{it.id==id}?:Placement(id,0,0,if(selected==id)rotation else 0)
/** Letter hit boxes match the entry labels; board units include the .35 frame. */
internal fun onEntryLetter(rules:Rules,x:Float,y:Float)=rules.gates.values.any{val c=entryCenter(it);abs(x-c.x)<=.35f&&abs(y-c.y)<=.35f}

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
