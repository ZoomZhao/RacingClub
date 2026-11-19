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
    var pointer by mutableStateOf(Offset.Zero)
    var owner by mutableStateOf<Any?>(null);private set
    val busy get()=owner!=null
    private var grab=Offset.Zero // in board cells, so grabbing a tile never jumps its anchor
    val grabCells get()=grab
    /** Root pointer → board-frame units. Null keeps the flat mapping over [board]; the 3D
     * board installs a ray cast onto its tile plane. */
    var toFrame:((Offset)->Offset)?=null
    val unit get()=board.width/5.7f
    val liftOrigin get()=pointer-grab*unit
    fun frame(at:Offset):Offset=toFrame?.invoke(at)?:((at-board.topLeft)/unit)
    val overBoard:Boolean get()=frame(pointer).let{it.x in 0f..5.7f&&it.y in 0f..4.7f}
    fun claim(token:Any):Boolean{if(owner!=null&&owner!==token)return false;owner=token;return true}
    fun owns(token:Any)=owner===token
    fun begin(s:GameState,token:Any,p:Placement,at:Offset,anchor:Offset?=null):Boolean {
        if(s.running||unit<=0||!claim(token))return false
        s.select(p.id);piece=p;pointer=at
        val(w,h)=s.rules.dimensions(p.r)
        grab=anchor?:Offset(w/2f,h/2f)
        return true
    }
    fun moveTo(token:Any,at:Offset){if(owns(token)&&piece!=null)pointer=at}
    fun candidate():Placement?=piece?.let{p->
        val f=frame(pointer)
        p.copy(x=(f.x-.35f-grab.x).roundToInt(),y=(f.y-.35f-grab.y).roundToInt())
    }
    fun problem(s:GameState):String {
        val p=candidate()?:return ""
        if(!overBoard)return "拖入棋盘内放置"
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
    fun release(token:Any){if(!owns(token))return;owner=null;piece=null}
}

internal fun placementProblem(rules:Rules,p:Placement):String {
    return if(rules.fits(p))"" else "超出棋盘边界"
}

/** Press selects at once. The returned flag says the tile was already the selected top
 * tile, so an unmoved release turns it instead of selecting again. */
internal fun GameState.pressTile(id:Int):Boolean {
    val was=selected==id&&(placed.none{it.id==id}||placed.last().id==id)
    select(id);return was
}
/** The quarter turn a repeated tap applies, following the rotation setting. */
internal fun TurnMode.tapTurn()=if(this==TurnMode.COUNTERCLOCKWISE)-1 else 1
/** Taps on the board never move an already placed tile; only tray tiles are placed by tap.
 * Tapping the selected tile again turns it; an empty cell or the frame dismisses. */
internal fun GameState.releaseBoardTap(hit:Placement?,wasSelected:Boolean,x:Int,y:Int,onLetter:Boolean) {
    when {
        running->{}
        hit!=null->if(wasSelected)rotatePiece(hit.id,turnMode.tapTurn())
        x in 0..4&&y in 0..3->{val id=selected;if(id!=null&&placed.none{it.id==id})place(x,y)else clearSelection()}
        !onLetter->clearSelection()
    }
}
/** A second tap on the selected tray card turns it, placed or still waiting in the tray. */
internal fun GameState.releaseRackTap(id:Int,wasSelected:Boolean){if(!running&&wasSelected)rotatePiece(id,turnMode.tapTurn())}
/** Accessibility click on a tray card or board tile: the same select-or-turn as a tap. */
internal fun GameState.tapTile(id:Int){releaseRackTap(id,pressTile(id))}
/** Tray tiles enter at their pending turn (0° unless turned); placed tiles keep their board turn. */
internal fun GameState.rackPiece(id:Int)=placed.firstOrNull{it.id==id}?:Placement(id,0,0,if(selected==id)rotation else 0)
/** Letter hit boxes match the entry labels; board units include the .35 frame. */
internal fun onEntryLetter(rules:Rules,x:Float,y:Float)=rules.gates.values.any{val c=entryCenter(it);abs(x-c.x)<=.35f&&abs(y-c.y)<=.35f}

internal fun turnActions(mode:TurnMode)=when(mode){TurnMode.CLOCKWISE->listOf(1,0);TurnMode.COUNTERCLOCKWISE->listOf(-1,0);TurnMode.BOTH->listOf(-1,1,0)}
