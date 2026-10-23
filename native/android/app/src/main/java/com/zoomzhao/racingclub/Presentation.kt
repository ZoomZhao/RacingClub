package com.zoomzhao.racingclub

import androidx.compose.ui.geometry.Rect
import kotlin.math.*

internal const val RoadNumberFace=0xfffff0d2L
internal const val RoadNumberInk=0xff214334L

/** Upright obstacle label, in tile-local cell units. The rail stays exposed. */
internal data class RoadNumberLayout(val center:Point,val width:Float,val height:Float,val font:Float) {
    val bounds get()=Rect(center.x-width/2,center.y-height/2,center.x+width/2,center.y+height/2)
}
internal fun roadNumberLayout(rules:Rules,p:Placement,avoid:List<Rect> = emptyList(),minimumFont:Float=0f,available:Rect?=null):RoadNumberLayout {
    val piece=rules.pieces.getValue(p.id);val(w,h)=rules.dimensions(p.r)
    val font=max(.22f,minimumFont)
    val width=max(if(p.id==10).33f else .30f,font*if(p.id==10)1.4f else 1.05f)
    val height=max(.28f,font*1.3f)
    val bounds=available?:Rect(.04f,.04f,w-.04f,h-.04f)
    val g=piece.gate;val center=rules.rotate(Point(g[0]/100,g[1]/100),p.r)
    val angle=(g[2]+p.r*90)*PI.toFloat()/180
    val normal=Point(cos(angle),sin(angle));val tangent=Point(-normal.y,normal.x)
    fun points(jumping:Boolean)=piece.paths.filter{(it.jump==p.id)==jumping}.flatMap{road->road.samples.filterIndexed{i,_->i%4==0}.map{rules.rotate(it,p.r)}}
    val own=points(true);val other=points(false)
    fun distanceSquared(q:Point,points:List<Point>)=points.minOfOrNull{(it.x-q.x).pow(2)+(it.y-q.y).pow(2)}?:Float.MAX_VALUE
    fun label(q:Point)=RoadNumberLayout(q,width,height,font)
    fun valid(q:Point):Boolean {
        val box=label(q).bounds
        if(box.left<bounds.left-.0001f||box.top<bounds.top-.0001f||box.right>bounds.right+.0001f||box.bottom>bounds.bottom+.0001f||avoid.any{box.inflate(.01f).overlaps(it)})return false
        // SAT against the whole rotated rail, including both posts and a small clear gap.
        val separate=listOf(Point(1f,0f),Point(0f,1f),normal,tangent).any{axis->
            val span=width/2*abs(axis.x)+height/2*abs(axis.y)+.13f*abs(axis.x*normal.x+axis.y*normal.y)+.375f*abs(axis.x*tangent.x+axis.y*tangent.y)
            abs((q.x-center.x)*axis.x+(q.y-center.y)*axis.y)+.0001f>=span
        }
        if(!separate)return false
        val otherDistance=distanceSquared(q,other)
        return otherDistance>=.28f.pow(2)||sqrt(otherDistance)+.02f>=sqrt(distanceSquared(q,own))
    }
    fun score(q:Point)=(q.x-center.x).pow(2)+(q.y-center.y).pow(2)+.1f*distanceSquared(q,own)+.001f*(q.x+q.y)
    val candidates=mutableListOf<Point>()
    if(bounds.width>=width&&bounds.height>=height)for((axis,half)in listOf(normal to .105f,tangent to .35f)){
        val cross=Point(-axis.y,axis.x);val projection=width/2*abs(axis.x)+height/2*abs(axis.y)
        for(sign in listOf(-1,1))for(extra in listOf(0f,.06f,.12f,.24f,.32f,.40f))for(shift in listOf(0f,-.08f,.08f,-.16f,.16f,-.24f,.24f,-.32f,.32f)){
            val d=(half+projection+.025f+extra)*sign
            val q=Point((center.x+axis.x*d+cross.x*shift).coerceIn(bounds.left+width/2,bounds.right-width/2),(center.y+axis.y*d+cross.y*shift).coerceIn(bounds.top+height/2,bounds.bottom-height/2))
            if(valid(q))candidates+=q
        }
    }
    candidates.minByOrNull(::score)?.let{return label(it)}
    // Occlusion must never send a label to an unrelated branch. Keep its rail anchor.
    if(avoid.isNotEmpty())return roadNumberLayout(rules,p,minimumFont=minimumFont,available=available)
    return label(center)
}

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
internal fun contrast(a:Long,b:Long):Double {val x=relativeLuminance(a);val y=relativeLuminance(b);return (max(x,y)+.05)/(min(x,y)+.05)}
// Cream surfaces behind small labels: plaque face top and bottom, and the page paper.
internal const val PlaqueFaceLight=0xfffffbefL
internal const val PlaqueFaceDeep=0xfff7e6c6L
internal const val PaperFace=0xfff8f5edL
/** White "被遮挡" text needs at least 4.5:1, so the amber tag uses a deeper bronze. */
internal const val CoveredTag=0xff93591aL
internal const val InProgressInk=0xff8a4f0fL
/** Keeps a difficulty's hue but darkens it until it reads at 4.5:1 on every background. */
internal fun readableInk(ink:Long,vararg backgrounds:Long):Long {
    fun mix(t:Double):Long {
        fun channel(shift:Int)=(((ink shr shift)and 255)*(1-t)).roundToInt().toLong()
        return 0xff000000L or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    var t=0.0;var color=ink
    while(backgrounds.any{contrast(color,it)<4.5}&&t<1.0){t+=.02;color=mix(t)}
    return color
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

internal data class CrossingFeedback(val crossed:Int,val pulse:Float)
/** Progress follows actual rail crossings, including repeated numbers and reverse paths. */
internal fun crossingFeedback(rules:Rules,route:List<RouteStep>,progress:Float):CrossingFeedback {
    val cursor=progress.coerceIn(0f,1f)*route.size
    var crossed=0;var lastCrossing:Float?=null
    route.forEachIndexed{i,step->if(step.jump!=null){
        val road=rules.road(step);val at=road.jumpAt?:.5f
        val crossing=i+if(step.from==road.from)at else 1-at
        if(cursor>=crossing){crossed++;lastCrossing=crossing}
    }}
    val pulse=lastCrossing?.let{sin(PI.toFloat()*((cursor-it)*1350f/600f).coerceIn(0f,1f)).coerceAtLeast(0f)}?:0f
    return CrossingFeedback(crossed,pulse)
}
