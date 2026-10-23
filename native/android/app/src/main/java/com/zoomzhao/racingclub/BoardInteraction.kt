package com.zoomzhao.racingclub

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

/** A drag is a preview transaction. The model only changes after a valid drop. */
internal class BoardDrag {
    var board by mutableStateOf(Rect.Zero)
    var piece by mutableStateOf<Placement?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    private var grab=Offset.Zero // in board cells, so grabbing a tile never jumps its anchor
    val unit get()=board.width/5.7f
    val liftOrigin get()=pointer-grab*unit
    fun begin(s:GameState,p:Placement,at:Offset,anchor:Offset?=null) {
        if(s.running||unit<=0)return
        s.select(p.id);s.rotation=p.r;piece=p;pointer=at
        val(w,h)=s.rules.dimensions(p.r)
        grab=anchor?:Offset(w/2f,h/2f)
    }
    fun candidate():Placement?=piece?.let{p->
        p.copy(x=((pointer.x-board.left)/unit-.35f-grab.x).roundToInt(),y=((pointer.y-board.top)/unit-.35f-grab.y).roundToInt())
    }
    fun problem(s:GameState):String {
        val p=candidate()?:return ""
        if(!board.contains(pointer))return "拖入棋盘内放置"
        return placementProblem(s.rules,p,s.placed)
    }
    fun finish(s:GameState) {
        val p=candidate()?:return
        val error=problem(s)
        if(error.isEmpty()){if(s.placed.firstOrNull{it.id==p.id}!=p){s.rotation=p.r;s.place(p.x,p.y)}}
        else s.message=if(error=="拖入棋盘内放置")"未放入棋盘，已保留原位" else "$error，已保留原位"
        cancel()
    }
    fun cancel(){piece=null}
}

internal fun placementProblem(rules:Rules,p:Placement,all:List<Placement>):String {
    val(w,h)=rules.dimensions(p.r)
    if(p.x<0||p.y<0||p.x+w>5||p.y+h>4)return "超出棋盘边界"
    val ids=all.filter{o->val(ow,oh)=rules.dimensions(o.r);o.id!=p.id&&p.x<o.x+ow&&o.x<p.x+w&&p.y<o.y+oh&&o.y<p.y+h}.map{it.id}.sorted()
    return if(ids.isEmpty())"" else "与 ${ids.joinToString("、")} 号拼板重叠"
}
