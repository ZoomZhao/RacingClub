package com.zoomzhao.racingclub

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.*

internal data class BoardMaterials(val wood:ImageBitmap,val sand:ImageBitmap)
internal class GameArtSet(val materials:BoardMaterials,val sprites:GameSprites)

/** Board textures and the eight horse views, decoded once per process. The ViewModel
 * preloads off the main thread; a caller arriving first waits rather than decoding twice. */
internal object GameArt {
    @Volatile private var art:GameArtSet?=null
    @Volatile var loads=0;private set
    fun get(resources:Resources):GameArtSet=art?:synchronized(this){art?:load(resources).also{art=it}}
    fun preload(resources:Resources){get(resources)}
    private fun load(resources:Resources):GameArtSet {
        loads++
        return GameArtSet(BoardMaterials(texture(resources,R.drawable.wood_grain),texture(resources,R.drawable.sand_grain)),loadSprites(resources))
    }
}
/** The ImageGen PNGs stay untouched; only the runtime copy is reduced to [limit] px. */
private fun texture(resources:Resources,id:Int,limit:Int=512):ImageBitmap {
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true;inScaled=false}
    BitmapFactory.decodeResource(resources,id,bounds)
    var sample=1
    while(max(bounds.outWidth,bounds.outHeight)/(sample*2)>=limit)sample*=2
    val decoded=BitmapFactory.decodeResource(resources,id,BitmapFactory.Options().apply{inScaled=false;inSampleSize=sample})
    val scale=limit.toFloat()/max(decoded.width,decoded.height)
    val bitmap=if(scale>=1f)decoded else Bitmap.createScaledBitmap(decoded,(decoded.width*scale).roundToInt(),(decoded.height*scale).roundToInt(),true).also{if(it!==decoded)decoded.recycle()}
    bitmap.prepareToDraw()
    return bitmap.asImageBitmap()
}
internal val LocalGameArt=staticCompositionLocalOf<GameArtSet?>{null}
@Composable internal fun gameArt():GameArtSet {
    LocalGameArt.current?.let{return it}
    val resources=LocalResources.current
    return remember(resources){GameArt.get(resources)}
}
@Composable internal fun boardMaterials()=gameArt().materials
private fun hex(s:String)=Color(android.graphics.Color.parseColor(s))
private operator fun Size.minus(other:Size)=Size(width-other.width,height-other.height)
private operator fun Size.plus(other:Size)=Size(width+other.width,height+other.height)
private fun rounded(rect:Rect,r:Float)=Path().apply {addRoundRect(RoundRect(rect,CornerRadius(r)))}
private fun DrawScope.texture(bitmap:ImageBitmap,rect:Rect,radius:Float) {
    clipPath(rounded(rect,radius)) {
        drawImage(bitmap,srcOffset=IntOffset.Zero,srcSize=IntSize(bitmap.width,bitmap.height),dstOffset=IntOffset(rect.left.roundToInt(),rect.top.roundToInt()),dstSize=IntSize(rect.width.roundToInt().coerceAtLeast(1),rect.height.roundToInt().coerceAtLeast(1)),filterQuality=FilterQuality.Medium)
    }
}

internal fun DrawScope.paintTile(rules:Rules,p:Placement,u:Float,origin:Offset,m:BoardMaterials,selected:Boolean=false,number:Boolean=false,numberAvoid:List<Rect> = emptyList(),minimumNumberFont:Float=0f,numberBounds:Rect?=null) {
    if(u<=0f||!u.isFinite())return
    val(w,h)=rules.dimensions(p.r);val top=origin+Offset(p.x*u,p.y*u)
    val face=Rect(top+Offset(u*.025f,u*.025f),Size(w*u-u*.05f,h*u-u*.055f))
    val radius=u*.07f
    drawRoundRect(hex("#80603f").copy(alpha=.26f),face.topLeft+Offset(0f,u*.045f),face.size,CornerRadius(radius))
    drawRoundRect(hex("#a87943"),face.topLeft,face.size,CornerRadius(radius))
    texture(m.wood,face,radius)
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=.25f),Color.Transparent,hex("#95633e").copy(alpha=.12f)),face.top,face.bottom),face.topLeft,face.size,CornerRadius(radius))
    val piece=rules.pieces.getValue(p.id)
    val paths=piece.paths.map {road->Path().apply{road.samples.forEachIndexed{i,point->val q=rules.rotate(point,p.r);val x=top.x+q.x*u;val y=top.y+q.y*u;if(i==0)moveTo(x,y)else lineTo(x,y)}}}
    clipPath(rounded(face,radius)) {
        paths.forEach{drawPath(it,hex("#b99261").copy(alpha=.45f),style=Stroke(u*.64f))}
        paths.forEach{drawPath(it,hex("#f8e5be"),style=Stroke(u*.59f))}
        paths.forEach{drawPath(it,Brush.linearGradient(listOf(hex("#fff3d8"),hex("#f5dfb6"),hex("#ffefd0")),top,top+Offset(w*u,h*u)),style=Stroke(u*.55f))}
    }
    drawRoundRect(hex("#b07e48"),face.topLeft,face.size,CornerRadius(radius),style=Stroke(u*.014f))
    drawRoundRect(hex("#fff1ce").copy(alpha=.76f),face.topLeft+Offset(u*.025f,u*.025f),face.size-Size(u*.05f,u*.05f),CornerRadius(radius*.85f),style=Stroke(u*.017f))

    val g=piece.gate;val center=rules.rotate(Point(g[0]/100,g[1]/100),p.r)
    val c=top+Offset(center.x*u,center.y*u)
    rotate(g[2]+p.r*90,pivot=c) {
        drawRoundRect(hex("#765135").copy(alpha=.24f),c+Offset(-u*.035f,-u*.31f),Size(u*.13f,u*.69f),CornerRadius(u*.02f))
        drawLine(hex(piece.color),c+Offset(0f,-u*.29f),c+Offset(0f,u*.29f),u*.075f,StrokeCap.Butt)
        for(i in 0..6)if(i%2==1) {
            val y=-.29f+.58f*i/7
            drawLine(hex("#fffaf0"),c+Offset(0f,y*u),c+Offset(0f,(y+.58f/7)*u),u*.076f,StrokeCap.Butt)
        }
        drawLine(Color.White.copy(alpha=.38f),c+Offset(-u*.018f,-u*.27f),c+Offset(-u*.018f,u*.27f),u*.013f)
        for(sign in listOf(-1,1)) {
            val foot=c+Offset(-u*.095f,sign*u*.3f-u*.045f)
            drawRoundRect(hex("#5b3a27").copy(alpha=.18f),foot+Offset(u*.025f,u*.035f),Size(u*.19f,u*.10f),CornerRadius(u*.025f))
            drawRoundRect(hex(piece.color),foot,Size(u*.19f,u*.10f),CornerRadius(u*.025f))
            drawRoundRect(Color.White.copy(alpha=.25f),foot+Offset(u*.018f,u*.009f),Size(u*.15f,u*.022f),CornerRadius(u*.01f))
        }
    }
    if(selected) {
        drawRoundRect(hex("#ffefbd"),face.topLeft-Offset(u*.012f,u*.012f),face.size+Size(u*.024f,u*.024f),CornerRadius(radius),style=Stroke(u*.05f))
        drawRoundRect(Green,face.topLeft-Offset(u*.012f,u*.012f),face.size+Size(u*.024f,u*.024f),CornerRadius(radius),style=Stroke(u*.027f))
    }
    if(number) {
        val label=roadNumberLayout(rules,p,numberAvoid,minimumNumberFont/u,numberBounds)
        val center=top+Offset(label.center.x*u,label.center.y*u)
        val rect=Rect(center-Offset(label.width*u/2,label.height*u/2),Size(label.width*u,label.height*u))
        val corner=CornerRadius(label.height*u*.20f)
        drawRoundRect(Color(RoadNumberFace),rect.topLeft,rect.size,corner)
        drawRoundRect(hex("#b89665").copy(alpha=.48f),rect.topLeft,rect.size,corner,style=Stroke(max(.6.dp.toPx(),u*.006f)))
        val accentY=center.y+label.height*u*.35f
        drawLine(hex(piece.color),Offset(center.x-label.width*u*.24f,accentY),Offset(center.x+label.width*u*.24f,accentY),label.height*u*.065f,StrokeCap.Round)
        val textPaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color=RoadNumberInk.toInt();textSize=label.font*u;textAlign=android.graphics.Paint.Align.CENTER
            typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD)
        }
        val bounds=android.graphics.Rect();textPaint.getTextBounds("${p.id}",0,"${p.id}".length,bounds)
        drawContext.canvas.nativeCanvas.drawText("${p.id}",center.x,center.y-label.height*u*.08f-(bounds.top+bounds.bottom)/2f,textPaint)
    }
}

/** Draw counters let instrumented tests prove that a finger move redraws only the overlay. */
internal object RenderStats {@Volatile var boardDraws=0;@Volatile var dropHighlightDraws=0}

internal fun DrawScope.paintBoard(s:GameState,m:BoardMaterials) {
    RenderStats.boardDraws++
    val u=size.width/5.7f;val origin=Offset(u*.35f,u*.35f)
    val outer=Rect(Offset(u*.025f,u*.025f),Size(size.width-u*.05f,size.height-u*.065f));val rad=u*.28f
    drawRoundRect(hex("#76512e").copy(alpha=.17f),outer.topLeft+Offset(0f,u*.055f),outer.size,CornerRadius(rad))
    texture(m.wood,outer,rad)
    drawRoundRect(Brush.verticalGradient(listOf(hex("#ffe1ad").copy(alpha=.35f),Color.Transparent,hex("#a16c35").copy(alpha=.16f))),outer.topLeft,outer.size,CornerRadius(rad))
    drawRoundRect(hex("#c08d52"),outer.topLeft,outer.size,CornerRadius(rad),style=Stroke(u*.025f))
    drawRoundRect(hex("#fff0cc"),outer.topLeft+Offset(u*.04f,u*.04f),outer.size-Size(u*.08f,u*.08f),CornerRadius(rad*.88f),style=Stroke(u*.035f))
    drawRoundRect(hex("#b57e41"),outer.topLeft+Offset(u*.10f,u*.10f),outer.size-Size(u*.20f,u*.20f),CornerRadius(rad*.74f),style=Stroke(u*.018f))
    val inset=Rect(origin-Offset(u*.025f,u*.025f),Size(5.05f*u,4.05f*u))
    drawRoundRect(hex("#a37845"),inset.topLeft,inset.size,CornerRadius(u*.14f))
    for(y in 0..3)for(x in 0..4) {
        val cell=Rect(origin+Offset(x*u+u*.018f,y*u+u*.018f),Size(u*.964f,u*.964f))
        texture(m.sand,cell,u*.13f)
        drawRoundRect(hex("#8f693d").copy(alpha=.20f),cell.topLeft,cell.size,CornerRadius(u*.13f),style=Stroke(u*.02f))
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=.14f),Color.Transparent,hex("#8c5d31").copy(alpha=.10f))),cell.topLeft,cell.size,CornerRadius(u*.13f))
    }
    drawRoundRect(hex("#f9e2b2"),inset.topLeft,inset.size,CornerRadius(u*.14f),style=Stroke(u*.028f))
    s.placed.forEach {p->
        val avoid=mutableListOf<Rect>()
        s.rules.coveredBy(p,s.placed).forEach{o->val(w,h)=s.rules.dimensions(o.r);avoid+=Rect((o.x-p.x).toFloat(),(o.y-p.y).toFloat(),(o.x-p.x+w).toFloat(),(o.y-p.y+h).toFloat())}
        if(s.rules.beneath(p,s.placed).isNotEmpty())s.rules.stackMarker(p,s.placed)?.let{q->avoid+=Rect(q.x-p.x-.80f,q.y-p.y-.16f,q.x-p.x,q.y-p.y+.15f)}
        if(s.selected==p.id&&!s.running) {
            val a=pieceActionAnchor(p,u/density,s.placed,turnActions(s.turnMode).size)
            if(a.inside)avoid+=Rect(a.left/(u/density)-p.x-.35f,a.top/(u/density)-p.y-.35f,(a.left+a.width)/(u/density)-p.x-.35f,(a.top+a.height)/(u/density)-p.y-.35f)
        }
        paintTile(s.rules,p,u,origin,m,s.selected==p.id,true,avoid)
    }
    // Only exposed top faces get a badge. Fully covered pieces remain reachable in the rack.
    s.placed.forEach {p->
        val below=s.rules.beneath(p,s.placed).size
        if(below>0)s.rules.stackMarker(p,s.placed)?.let{point->
            val right=origin+Offset(point.x*u,point.y*u)
            val rect=Rect(right-Offset(u*.80f,u*.16f),Size(u*.80f,u*.31f))
            drawRoundRect(hex("#fff3d8"),rect.topLeft,rect.size,CornerRadius(u*.08f))
            drawRoundRect(hex("#b47a2d"),rect.topLeft,rect.size,CornerRadius(u*.08f),style=Stroke(u*.012f))
            val icon=rect.topLeft+Offset(u*.09f,u*.09f)
            repeat(2){i->drawRoundRect(hex("#a96b1e"),icon+Offset(i*u*.025f,-i*u*.035f),Size(u*.14f,u*.10f),CornerRadius(u*.02f),style=Stroke(u*.016f))}
            drawContext.canvas.nativeCanvas.drawText("叠 ${below+1}",rect.left+u*.49f,rect.top+u*.215f,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=0xff925b1c.toInt();textSize=u*.17f;textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true})
        }
    }

    val cream=hex("#fff4d7");val fenceShade=hex("#ac804b").copy(alpha=.35f)
    val edges=listOf(Offset(u*.46f,u*.18f) to Offset(u*5.22f,u*.18f),Offset(u*.46f,u*4.50f) to Offset(u*5.22f,u*4.50f),Offset(u*.18f,u*.46f) to Offset(u*.18f,u*4.24f),Offset(u*5.50f,u*.46f) to Offset(u*5.50f,u*4.24f))
    for((a,b)in edges) {
        val horizontal=a.y==b.y
        val openings=s.rules.gates.values.filter { point ->
            if(horizontal)point.y==if(a.y<u)0f else 4f else point.x==if(a.x<u)0f else 5f
        }.map {if(horizontal)origin.x+it.x*u else origin.y+it.y*u}.sorted()
        val lo=if(horizontal)a.x else a.y;val hi=if(horizontal)b.x else b.y
        fun rail(from:Float,to:Float) {
            if(to<=from)return
            val p=if(horizontal)Offset(from,a.y)else Offset(a.x,from)
            val q=if(horizontal)Offset(to,a.y)else Offset(a.x,to)
            drawLine(fenceShade,p+Offset(0f,u*.018f),q+Offset(0f,u*.018f),u*.055f,StrokeCap.Round)
            drawLine(cream,p,q,u*.035f,StrokeCap.Round)
        }
        fun post(position:Float) {
            val c=if(horizontal)Offset(position,a.y)else Offset(a.x,position)
            val foot=c-Offset(u*.065f,u*.085f)
            drawRoundRect(fenceShade,foot+Offset(u*.018f,u*.025f),Size(u*.13f,u*.17f),CornerRadius(u*.035f))
            drawRoundRect(hex("#f4ddb3"),foot,Size(u*.13f,u*.17f),CornerRadius(u*.035f))
            drawRoundRect(cream,foot,Size(u*.13f,u*.13f),CornerRadius(u*.035f))
            drawRoundRect(Color.White.copy(alpha=.65f),foot+Offset(u*.017f,u*.012f),Size(u*.095f,u*.025f),CornerRadius(u*.01f))
        }
        var cursor=lo
        for(center in openings) {
            rail(cursor,max(lo,center-u*.40f))
            cursor=min(hi,center+u*.40f)
        }
        rail(cursor,hi)
        // Each open gateway has two deliberate end posts; the rails meet their centers.
        for(center in openings){post(max(lo,center-u*.40f));post(min(hi,center+u*.40f))}
        val count=if(horizontal)7 else 5
        for(i in 0..count) {
            val t=i.toFloat()/count;val c=a+(b-a)*t
            val position=if(horizontal)c.x else c.y
            if(openings.none {abs(position-it)<u*.58f})post(position)
        }
    }
    s.rules.gates.forEach{(name,pt)->
        val c=entryCenter(pt);val outward=entryOutward(pt)
        paintEntryMarker(name,Offset(c.x*u,c.y*u),Offset(outward.x,outward.y),u,name==s.startGate,name==s.finishGate)
    }
}

/** Flat entry markers share the ORIGINAL letter center. Nothing occupies the road. */
internal fun DrawScope.paintEntryMarker(name:String,c:Offset,outward:Offset,u:Float,start:Boolean,finish:Boolean) {
    val horizontal=outward.y!=0f
    val tag=if(horizontal)Size(u*.57f,u*.29f)else Size(u*.30f,u*.52f)
    val face=Rect(c-Offset(tag.width/2,tag.height/2),tag)
    val terracotta=hex("#a4472f")
    drawRoundRect(if(finish)hex("#fff3df")else hex("#ffe9bf"),face.topLeft,face.size,CornerRadius(u*.045f))
    drawRoundRect(if(finish)terracotta else hex("#d5ad73"),face.topLeft,face.size,CornerRadius(u*.045f),style=Stroke(if(finish)u*.022f else u*.01f))
    if(finish) {
        // A top-down checker threshold, aligned to the edge, with no raised arch.
        val tangent=Offset(-outward.y,outward.x)
        val bandCenter=c+outward*(if(horizontal)tag.height/2 else tag.width/2)-outward*u*.040f
        for(i in 0..5)for(row in 0..1){
            val center=bandCenter+tangent*((i-2.5f)*u*.068f)+outward*((row-.5f)*u*.030f)
            if(horizontal)drawRect(if((i+row)%2==0)terracotta else hex("#fffaf0"),center-Offset(u*.034f,u*.015f),Size(u*.068f,u*.030f))
            else drawRect(if((i+row)%2==0)terracotta else hex("#fffaf0"),center-Offset(u*.015f,u*.034f),Size(u*.030f,u*.068f))
        }
    }
    drawContext.canvas.nativeCanvas.drawText(name,c.x,c.y+u*.078f,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.parseColor("#342718");textSize=u*.24f;textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true})
    if(start) {
        // The arrow lives entirely outside the wooden frame and points to the letter.
        val tip=c+outward*u*.20f;val tail=c+outward*u*.46f;val tangent=Offset(-outward.y,outward.x)
        val path=Path().apply{
            moveTo(tip.x,tip.y)
            val left=tip+outward*u*.14f+tangent*u*.105f;lineTo(left.x,left.y)
            val leftNeck=tip+outward*u*.14f+tangent*u*.045f;lineTo(leftNeck.x,leftNeck.y)
            val stem=tail+tangent*u*.045f;lineTo(stem.x,stem.y)
            val other=tail-tangent*u*.045f;lineTo(other.x,other.y)
            val rightNeck=tip+outward*u*.14f-tangent*u*.045f;lineTo(rightNeck.x,rightNeck.y)
            val right=tip+outward*u*.14f-tangent*u*.105f;lineTo(right.x,right.y);close()
        }
        drawPath(path,hex("#fffaf0"),style=Stroke(u*.045f,join=StrokeJoin.Round))
        drawPath(path,Green)
    }
}
