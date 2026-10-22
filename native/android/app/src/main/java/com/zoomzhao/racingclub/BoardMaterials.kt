package com.zoomzhao.racingclub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.*

internal data class BoardMaterials(val wood:ImageBitmap,val sand:ImageBitmap)
@Composable internal fun boardMaterials():BoardMaterials {
    val wood=ImageBitmap.imageResource(R.drawable.wood_grain)
    val sand=ImageBitmap.imageResource(R.drawable.sand_grain)
    return remember(wood,sand){BoardMaterials(wood,sand)}
}
private fun hex(s:String)=Color(android.graphics.Color.parseColor(s))
private operator fun Size.minus(other:Size)=Size(width-other.width,height-other.height)
private operator fun Size.plus(other:Size)=Size(width+other.width,height+other.height)
private fun rounded(rect:Rect,r:Float)=Path().apply {addRoundRect(RoundRect(rect,CornerRadius(r)))}
private fun DrawScope.texture(bitmap:ImageBitmap,rect:Rect,radius:Float) {
    clipPath(rounded(rect,radius)) {
        drawImage(bitmap,srcOffset=IntOffset.Zero,srcSize=IntSize(bitmap.width,bitmap.height),dstOffset=IntOffset(rect.left.roundToInt(),rect.top.roundToInt()),dstSize=IntSize(rect.width.roundToInt().coerceAtLeast(1),rect.height.roundToInt().coerceAtLeast(1)),filterQuality=FilterQuality.Medium)
    }
}

internal fun DrawScope.paintTile(rules:Rules,p:Placement,u:Float,origin:Offset,m:BoardMaterials,selected:Boolean=false,number:Boolean=false) {
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
        val badge=top+Offset(u*.20f,u*.20f)
        drawCircle(hex("#74522f").copy(alpha=.25f),u*.16f,badge+Offset(0f,u*.025f))
        drawCircle(if(selected)Green else hex(piece.color),u*.16f,badge)
        drawCircle(hex("#fff5d8"),u*.16f,badge,style=Stroke(u*.018f))
        drawContext.canvas.nativeCanvas.drawText("${p.id}",badge.x,badge.y+u*.07f,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;textSize=u*.20f;textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true})
    }
}

internal fun DrawScope.paintBoard(s:GameState,m:BoardMaterials,sprites:GameSprites) {
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
    s.placed.forEach {paintTile(s.rules,it,u,origin,m,s.selected==it.id,true)}

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
    s.rules.gates[s.finishGate]?.let {end->
        val e=origin+Offset(end.x*u,end.y*u);val side=boundarySide(end)
        val gate=sprites.gates.getValue(side)
        val gateSize=if(side==BoundarySide.N||side==BoundarySide.S)Size(u*.82f,u*.63f)else Size(u*.63f,u*.90f)
        val anchor=if(side==BoundarySide.N||side==BoundarySide.S)Offset(.5f,.91f)else Offset(.5f,.72f)
        paintSprite(gate,Rect(e-Offset(gateSize.width*anchor.x,gateSize.height*anchor.y),gateSize))
    }
    s.rules.gates.forEach { (name,pt) ->
        val c=when {pt.y==0f->Offset(origin.x+pt.x*u,u*.18f);pt.y==4f->Offset(origin.x+pt.x*u,u*4.50f);pt.x==0f->Offset(u*.18f,origin.y+pt.y*u);else->Offset(u*5.50f,origin.y+pt.y*u)}
        val horizontal=pt.y==0f||pt.y==4f
        if(name==s.startGate||name==s.finishGate) {
            val roleColor=if(name==s.startGate)Green else hex("#a95837")
            val label=c+if(horizontal)Offset(-u*.63f,0f)else Offset(if(pt.x==0f)-u*.23f else u*.23f,-u*.4f)
            drawCircle(hex("#886440").copy(alpha=.25f),u*.15f,label+Offset(0f,u*.02f))
            drawCircle(roleColor,u*.15f,label)
            drawCircle(hex("#fff1ce"),u*.15f,label,style=Stroke(u*.018f))
            drawContext.canvas.nativeCanvas.drawText(name,label.x,label.y+u*.067f,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;textSize=u*.20f;textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true})
            return@forEach
        }
        val tag=if(horizontal)Size(u*.57f,u*.29f)else Size(u*.30f,u*.52f)
        drawRoundRect(hex("#8b653a").copy(alpha=.20f),c-Offset(tag.width/2,tag.height/2)+Offset(0f,u*.022f),tag,CornerRadius(u*.045f))
        drawRoundRect(hex("#ffe9bf"),c-Offset(tag.width/2,tag.height/2),tag,CornerRadius(u*.045f))
        drawContext.canvas.nativeCanvas.drawText(name,c.x,c.y+u*.078f,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.parseColor("#342718");textSize=u*.24f;textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true})
    }
}
