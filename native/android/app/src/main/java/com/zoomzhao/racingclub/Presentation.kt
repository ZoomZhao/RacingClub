package com.zoomzhao.racingclub

import kotlin.math.*

/** Keep long instructions legible and complete; never hide goals in a scroller. */
internal fun courseRows(nodeCount:Int,width:Float,fontScale:Float):List<List<Int>> {
    val minBadge=22f*fontScale.coerceAtLeast(1f)
    val columns=floor(width/(minBadge+8f)).toInt().coerceIn(2,10)
    val rowCount=(nodeCount+columns-1)/columns
    var index=0
    return List(rowCount){row->
        val count=nodeCount/rowCount+if(row<nodeCount%rowCount)1 else 0
        (index until index+count).toList().also{index+=count}
    }
}

// Preserve obstacle color families, with solid backgrounds and contrasting ink.
internal fun numberFace(id:Int):Long=when(id) {
    1->0xffaa346a;2->0xffe5bd47;3->0xffa7ba54;4->0xffae362e
    5->0xff245f91;6->0xff236648;7->0xffe09b59;8->0xff75418e
    9->0xff75452b;10->0xff1b6278;else->0xff254c3b
}
internal fun relativeLuminance(rgb:Long):Double {
    fun channel(shift:Int):Double {val v=((rgb shr shift)and 255)/255.0;return if(v<=.04045)v/12.92 else ((v+.055)/1.055).pow(2.4)}
    return .2126*channel(16)+.7152*channel(8)+.0722*channel(0)
}
internal fun numberInk(id:Int):Long=if((relativeLuminance(numberFace(id))+.05)/.05 > 1.05/(relativeLuminance(numberFace(id))+.05))0xff000000 else 0xffffffff
internal fun entryCenter(pt:Point):Point=when {
    pt.y==0f->Point(.35f+pt.x,.18f)
    pt.y==4f->Point(.35f+pt.x,4.50f)
    pt.x==0f->Point(.18f,.35f+pt.y)
    else->Point(5.50f,.35f+pt.y)
}
internal fun entryOutward(pt:Point):Point=when(boundarySide(pt)) {
    BoundarySide.N->Point(0f,-1f);BoundarySide.E->Point(1f,0f)
    BoundarySide.S->Point(0f,1f);BoundarySide.W->Point(-1f,0f)
}
