package com.zoomzhao.racingclub

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.*

/** World space for the 3D board, in board-frame units: x to the right, z toward the
 * player (the 2D board's y), y up. The frame spans 5.7 × 4.7 and cells start at .35. */
internal data class V3(val x:Float,val y:Float,val z:Float) {
    operator fun plus(o:V3)=V3(x+o.x,y+o.y,z+o.z)
    operator fun minus(o:V3)=V3(x-o.x,y-o.y,z-o.z)
    operator fun times(k:Float)=V3(x*k,y*k,z*k)
    infix fun dot(o:V3)=x*o.x+y*o.y+z*o.z
    fun normalized():V3 {val l=sqrt(this dot this).coerceAtLeast(1e-6f);return V3(x/l,y/l,z/l)}
}

internal const val BoardBaseDepth=.34f
internal const val TileThickness=.16f
internal const val SelectedLift=.07f
internal const val DragLift=.55f
internal const val SignWidth=.46f
internal const val SignBottom=.05f
internal const val SignTop=.42f
internal fun tileTop(level:Int)=TileThickness*(level+1)

/** A fixed perspective camera above the near (south) edge, fitted into [width]×[height]
 * pixels. Screen points map back onto any horizontal plane by exact ray intersection. */
/** [topInset] keeps a strip at the top free for the floating clock; a width-limited board
 * only moves down into its spare height, so it shrinks only when there is no slack. */
internal class BoardCamera(val width:Float,val height:Float,pitchDegrees:Float=56f,fovDegrees:Float=28f,distance:Float=13f,topInset:Float=0f) {
    private val target=V3(2.85f,0f,2.3f)
    private val pitch=pitchDegrees*PI.toFloat()/180
    val eye=target+V3(0f,sin(pitch),cos(pitch))*distance
    private val forward=(target-eye).normalized()
    private val right=V3(1f,0f,0f)
    private val up=V3(0f,cos(pitch),-sin(pitch))
    /** Screen-up direction in world space: a quad spanned by x and this faces the camera. */
    val billboardUp get()=up
    private val focal=1f/tan(fovDegrees*PI.toFloat()/360)
    private var scale=1f;private var ox=0f;private var oy=0f
    init {
        val corners=listOf(0f,5.7f).flatMap{x->listOf(0f,4.7f).flatMap{z->listOf(-BoardBaseDepth,.3f).map{y->V3(x,y,z)}}}
        val points=corners.map(::ndc)
        val minX=points.minOf{it.x};val maxX=points.maxOf{it.x};val minY=points.minOf{it.y};val maxY=points.maxOf{it.y}
        val room=(height-topInset).coerceAtLeast(height*.6f)
        scale=min(width/(maxX-minX),room/(maxY-minY))
        ox=width/2-(minX+maxX)/2*scale;oy=height-room/2-(minY+maxY)/2*scale
    }
    private fun ndc(p:V3):Offset {val v=p-eye;val depth=v dot forward;return Offset((v dot right)/depth*focal,-(v dot up)/depth*focal)}
    fun project(p:V3):Offset=ndc(p).let{Offset(it.x*scale+ox,it.y*scale+oy)}
    fun depth(p:V3)=(p-eye) dot forward
    fun facing(normal:V3,point:V3)=(normal dot (eye-point))>0f
    /** Pixels per world unit at [p], measured along x. */
    fun pixelsPerUnit(p:V3)=(project(p+V3(.5f,0f,0f)).x-project(p-V3(.5f,0f,0f)).x)
    /** Board-frame coordinates (x, z) where the ray through [screen] meets the plane y=[h]. */
    fun toFrame(screen:Offset,h:Float):Offset {
        val xn=(screen.x-ox)/scale/focal;val yn=-(screen.y-oy)/scale/focal
        val dir=forward+right*xn+up*yn
        if(abs(dir.y)<1e-6f)return Offset(-99f,-99f)
        val t=(h-eye.y)/dir.y
        return Offset(eye.x+dir.x*t,eye.z+dir.z*t)
    }
    /** Screen-space bounds of the board frame's top surface. */
    fun boardBounds():Rect {
        val points=listOf(V3(0f,0f,0f),V3(5.7f,0f,0f),V3(5.7f,0f,4.7f),V3(0f,0f,4.7f),V3(0f,-BoardBaseDepth,4.7f),V3(5.7f,-BoardBaseDepth,4.7f)).map(::project)
        return Rect(points.minOf{it.x},points.minOf{it.y},points.maxOf{it.x},points.maxOf{it.y})
    }
}

private val Light=V3(-.38f,1f,.62f).normalized()
internal fun shade(argb:Int,normal:V3,ambient:Float=.56f):Int {
    val k=(ambient+(1-ambient)*max(0f,normal dot Light)).coerceIn(0f,1.08f)
    fun c(shift:Int)=(((argb shr shift) and 255)*k).roundToInt().coerceIn(0,255)
    return (argb and 0xff000000.toInt()) or (c(16) shl 16) or (c(8) shl 8) or c(0)
}

/** Rounded rectangle outline in the xz plane, one consistent winding. */
internal fun roundedOutline(x0:Float,z0:Float,x1:Float,z1:Float,radius:Float,segments:Int=5):List<Offset> {
    val r=radius.coerceAtMost(min(x1-x0,z1-z0)/2)
    // Each arc runs 90° backwards from where the previous edge arrives.
    val corners=listOf(Offset(x1-r,z1-r) to 90f,Offset(x1-r,z0+r) to 360f,Offset(x0+r,z0+r) to 270f,Offset(x0+r,z1-r) to 180f)
    val out=mutableListOf<Offset>()
    for((center,start) in corners) {
        for(i in 0..segments) {
            val a=(start-90f*i/segments)*PI.toFloat()/180
            out+=Offset(center.x+cos(a)*r,center.y+sin(a)*r)
        }
    }
    return out
}
/** Oriented rectangle footprint: [axis] is a unit vector in xz. */
internal fun orientedOutline(center:Offset,axis:Offset,halfAlong:Float,halfAcross:Float):List<Offset> {
    val n=Offset(-axis.y,axis.x)
    return listOf(center+axis*halfAlong+n*halfAcross,center+axis*halfAlong-n*halfAcross,center-axis*halfAlong-n*halfAcross,center-axis*halfAlong+n*halfAcross)
}

/** Draws extruded footprints with flat face shading and back-face culling. Visible faces
 * of one convex prism never overlap, so each prism needs no sorting of its own. */
internal class ScenePainter(val camera:BoardCamera,val canvas:Canvas) {
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL}
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeJoin=Paint.Join.ROUND}
    private val bitmapPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix=Matrix();private val path=Path()
    private val src=FloatArray(8);private val dst=FloatArray(8)

    fun polygon(points:List<V3>,argb:Int) {
        path.reset()
        points.forEachIndexed{i,p->val q=camera.project(p);if(i==0)path.moveTo(q.x,q.y)else path.lineTo(q.x,q.y)}
        path.close();fill.color=argb;canvas.drawPath(path,fill)
    }
    fun outline(points:List<V3>,argb:Int,width:Float) {
        path.reset()
        points.forEachIndexed{i,p->val q=camera.project(p);if(i==0)path.moveTo(q.x,q.y)else path.lineTo(q.x,q.y)}
        path.close();stroke.color=argb;stroke.strokeWidth=width;canvas.drawPath(path,stroke)
    }
    /** Soft contact shadow of a footprint on the plane y=[y]. */
    fun shadow(outline:List<Offset>,y:Float,offset:Offset,alpha:Float,blur:Float) {
        path.reset()
        outline.forEachIndexed{i,p->val q=camera.project(V3(p.x+offset.x,y,p.y+offset.y));if(i==0)path.moveTo(q.x,q.y)else path.lineTo(q.x,q.y)}
        path.close()
        fill.color=android.graphics.Color.argb((alpha*255).roundToInt().coerceIn(0,255),58,36,18)
        fill.maskFilter=if(blur>.5f)BlurMaskFilter(blur,BlurMaskFilter.Blur.NORMAL)else null
        canvas.drawPath(path,fill);fill.maskFilter=null
    }
    /** Maps the whole [bitmap] onto four world corners: top-left, top-right, bottom-right, bottom-left. */
    fun texture(bitmap:Bitmap,corners:List<V3>,alpha:Int=255) {
        val w=bitmap.width.toFloat();val h=bitmap.height.toFloat()
        src[0]=0f;src[1]=0f;src[2]=w;src[3]=0f;src[4]=w;src[5]=h;src[6]=0f;src[7]=h
        corners.forEachIndexed{i,p->val q=camera.project(p);dst[i*2]=q.x;dst[i*2+1]=q.y}
        matrix.setPolyToPoly(src,0,dst,0,4)
        bitmapPaint.alpha=alpha
        canvas.drawBitmap(bitmap,matrix,bitmapPaint)
    }
    /** Vertical walls of a footprint, then its top in [topArgb] unless [top] is false. */
    fun prism(outline:List<Offset>,y0:Float,y1:Float,sideArgb:Int,topArgb:Int?=null,ambient:Float=.56f) {
        val n=outline.size
        val area=(0 until n).sumOf{i->val a=outline[i];val b=outline[(i+1)%n];(a.x*b.y-b.x*a.y).toDouble()}
        val sign=if(area>0)1f else -1f
        for(i in 0 until n) {
            val a=outline[i];val b=outline[(i+1)%n]
            val e=b-a;if(e.getDistance()<1e-5f)continue
            // Outward normal in xz for this winding.
            val normal=V3(e.y*sign,0f,-e.x*sign).normalized()
            val mid=V3((a.x+b.x)/2,(y0+y1)/2,(a.y+b.y)/2)
            if(!camera.facing(normal,mid))continue
            polygon(listOf(V3(a.x,y1,a.y),V3(b.x,y1,b.y),V3(b.x,y0,b.y),V3(a.x,y0,a.y)),shade(sideArgb,normal,ambient))
        }
        if(topArgb!=null)polygon(outline.map{V3(it.x,y1,it.y)},shade(topArgb,V3(0f,1f,0f),ambient))
    }
    fun line(a:V3,b:V3,argb:Int,width:Float) {
        val p=camera.project(a);val q=camera.project(b)
        stroke.color=argb;stroke.strokeWidth=width;stroke.strokeCap=Paint.Cap.BUTT
        canvas.drawLine(p.x,p.y,q.x,q.y,stroke)
    }
}

internal fun argb(hex:String)=android.graphics.Color.parseColor(hex)

/** Corners of a tile's top face in world space, ordered to match its r=0 texture
 * (top-left, top-right, bottom-right, bottom-left), so the texture turns with the tile. */
internal fun tileTextureCorners(rules:Rules,p:Placement,x:Float,z:Float,y:Float):List<V3> =
    listOf(Point(0f,0f),Point(2f,0f),Point(2f,1f),Point(0f,1f)).map{c->val q=rules.rotate(c,p.r);V3(x+q.x,y,z+q.y)}

/** The striped obstacle rail standing on a tile: two posts and a pole between them. */
internal fun ScenePainter.paintRail(rules:Rules,p:Placement,x:Float,z:Float,y:Float) {
    val piece=rules.pieces.getValue(p.id)
    val g=piece.gate;val local=rules.rotate(Point(g[0]/100,g[1]/100),p.r)
    val center=Offset(x+local.x,z+local.y)
    val angle=(g[2]+p.r*90)*PI.toFloat()/180
    // The 2D rail runs along the local +y axis after rotating by the gate angle.
    val along=Offset(-sin(angle),cos(angle));val across=Offset(along.y,-along.x)
    val color=argb(piece.color);val white=argb("#fffaf0")
    val parts=mutableListOf<Pair<Float,()->Unit>>()
    for(sign in listOf(-1f,1f)){
        val c=center+along*(.30f*sign)
        parts+=camera.depth(V3(c.x,y,c.y)) to {
            prism(orientedOutline(c,across,.095f,.05f),y,y+.30f,color,color)
        }
    }
    val segments=7;val half=.29f
    for(i in 0 until segments){
        val t0=-half+2*half*i/segments;val t1=-half+2*half*(i+1)/segments
        val c=center+along*((t0+t1)/2)
        parts+=camera.depth(V3(c.x,y,c.y))+.001f to {
            prism(orientedOutline(c,along,(t1-t0)/2,.034f),y+.19f,y+.25f,if(i%2==0)color else white,if(i%2==0)color else white,.62f)
        }
    }
    parts.sortedByDescending{it.first}.forEach{it.second()}
}

/** Fence posts and rails from the 2D layout, raised into the scene. */
internal fun ScenePainter.paintFence(fence:FenceLayout) {
    val cream=argb("#fff4d7")
    val items=mutableListOf<Pair<Float,()->Unit>>()
    for((a,b) in fence.rails){
        val mid=(a+b)/2f;val d=b-a;val length=d.getDistance();if(length<.01f)continue
        val axis=d/length
        items+=camera.depth(V3(mid.x,.1f,mid.y))+.05f to {prism(orientedOutline(mid,axis,length/2,.022f),.10f,.155f,cream,cream,.66f)}
    }
    for(post in fence.posts){
        items+=camera.depth(V3(post.x,.1f,post.y)) to {prism(roundedOutline(post.x-.065f,post.y-.065f,post.x+.065f,post.y+.065f,.03f,2),0f,.24f,cream,cream,.66f)}
    }
    items.sortedByDescending{it.first}.forEach{it.second()}
}

/** Where an entry's upright sign stands, in frame units (x, z). */
internal fun signCenter(pt:Point):Offset=entryCenter(pt).let{Offset(it.x,it.y)}

/** A lettered sign on the fence opening, turned to face the camera so every letter stays readable. */
internal fun ScenePainter.paintSign(name:String,at:Offset,textures:SceneTextures,start:Boolean,finish:Boolean) {
    val w=SignWidth/2;val h=SignTop-SignBottom
    shadow(orientedOutline(Offset(at.x,at.y+.04f),Offset(1f,0f),w*.9f,.06f),0f,Offset(.02f,.03f),.24f,camera.pixelsPerUnit(V3(at.x,0f,at.y))*.04f)
    val edge=if(start)Green.toArgbInt() else if(finish)argb("#a4472f") else argb("#c99a5e")
    val up=camera.billboardUp*h;val base=V3(at.x,SignBottom,at.y)
    // A short post, then a darker lip under the plate to give it thickness.
    prism(roundedOutline(at.x-.03f,at.y-.03f,at.x+.03f,at.y+.03f,.012f,2),0f,SignBottom+h*.3f,argb("#c99a5e"),argb("#c99a5e"),.6f)
    val drop=V3(0f,-.035f,0f)
    polygon(listOf(base+V3(-w,0f,0f)+up+drop,base+V3(w,0f,0f)+up+drop,base+V3(w,0f,0f)+drop,base+V3(-w,0f,0f)+drop),shade(edge,V3(0f,0f,1f),.5f))
    texture(textures.sign(name,start,finish),listOf(base+V3(-w,0f,0f)+up,base+V3(w,0f,0f)+up,base+V3(w,0f,0f),base+V3(-w,0f,0f)))
}

/** Stack height of each placement, bottom to top, so overlapping tiles physically rest on one another. */
internal fun stackLevels(rules:Rules,all:List<Placement>):Map<Int,Int> {
    val levels=mutableMapOf<Int,Int>()
    all.forEachIndexed{i,p->levels[p.id]=all.take(i).filter{rules.overlaps(p,it)}.maxOfOrNull{levels.getValue(it.id)+1}?:0}
    return levels
}
/** Height of the surface under a board cell, for drop previews on top of a stack. */
internal fun surfaceHeight(rules:Rules,all:List<Placement>,levels:Map<Int,Int>,cells:Placement):Float =
    all.filter{rules.overlaps(cells.copy(id=-1),it)}.maxOfOrNull{tileTop(levels.getValue(it.id))}?:0f

internal fun tileFootprint(rules:Rules,p:Placement,x:Float,z:Float):List<Offset> {
    val(w,h)=rules.dimensions(p.r)
    return roundedOutline(x+.025f,z+.025f,x+w-.025f,z+h-.03f,.07f,3)
}

/** One tile slab with its baked road art, upright number label and raised rail. */
internal fun ScenePainter.paintTile3D(rules:Rules,p:Placement,x:Float,z:Float,base:Float,textures:SceneTextures,selected:Boolean,alpha:Int=255) {
    val top=base+TileThickness
    if(alpha<255)canvas.saveLayerAlpha(0f,0f,camera.width,camera.height,alpha)
    prism(tileFootprint(rules,p,x,z),base,top,argb("#9b6c3c"))
    texture(textures.tile(p.id),tileTextureCorners(rules,p,x,z,top))
    val label=roadNumberLayout(rules,p)
    val b=label.bounds
    texture(textures.label(p.id,label),listOf(V3(x+b.left,top,z+b.top),V3(x+b.right,top,z+b.top),V3(x+b.right,top,z+b.bottom),V3(x+b.left,top,z+b.bottom)))
    if(selected){
        val(w,h)=rules.dimensions(p.r)
        val ring=roundedOutline(x+.005f,z+.005f,x+w-.005f,z+h-.01f,.08f,3).map{V3(it.x,top+.002f,it.y)}
        val px=camera.pixelsPerUnit(V3(x+w/2f,top,z+h/2f))
        outline(ring,argb("#ffefbd"),px*.07f)
        outline(ring,Green.toArgbInt(),px*.035f)
    }
    paintRail(rules,p,x,z,top)
    if(alpha<255)canvas.restore()
}
private fun androidx.compose.ui.graphics.Color.toArgbInt()=android.graphics.Color.argb((alpha*255).roundToInt(),(red*255).roundToInt(),(green*255).roundToInt(),(blue*255).roundToInt())

/** Board, contact shadows, tiles in painter's order, the raised fence and entry signs. */
internal fun ScenePainter.paintBoard3D(s:GameState,textures:SceneTextures,fence:FenceLayout,ghost:Int?) {
    val rules=s.rules
    // The board slab: rounded walls, then the baked frame and sand as its top face.
    val frame=roundedOutline(.025f,.025f,5.675f,4.66f,.28f,6)
    shadow(frame,-BoardBaseDepth,Offset(.06f,.12f),.30f,camera.pixelsPerUnit(V3(2.85f,0f,2.35f))*.18f)
    prism(frame,-BoardBaseDepth,0f,argb("#a8743f"),ambient=.5f)
    texture(textures.board(s.startGate,s.finishGate),listOf(V3(0f,0f,0f),V3(5.7f,0f,0f),V3(5.7f,0f,4.7f),V3(0f,0f,4.7f)))
    val levels=stackLevels(rules,s.placed)
    val order=s.placed.sortedWith(compareBy<Placement>{levels.getValue(it.id)}.thenByDescending{val(w,h)=rules.dimensions(it.r);camera.depth(V3(it.x+.35f+w/2f,0f,it.y+.35f+h/2f))})
    val px=camera.pixelsPerUnit(V3(2.85f,0f,2.35f))
    // The back fence rail sits behind every tile; the side and front posts follow them.
    val back=FenceLayout(fence.rails.filter{(a,b)->a.y<.5f&&b.y<.5f},fence.posts.filter{it.y<.5f})
    val rest=FenceLayout(fence.rails-back.rails.toSet(),fence.posts-back.posts.toSet())
    val signs=rules.gates.map{(name,pt)->Triple(name,signCenter(pt),name)}
    fun signs(far:Boolean)=signs.filter{(it.second.y<.5f)==far}.sortedByDescending{camera.depth(V3(it.second.x,0f,it.second.y))}
        .forEach{(name,at)->paintSign(name,at,textures,name==s.startGate,name==s.finishGate)}
    paintFence(back)
    signs(true)
    for(level in 0..(levels.values.maxOrNull()?:-1)){
        val tier=order.filter{levels.getValue(it.id)==level}
        val base=if(level==0)0f else tileTop(level-1)
        tier.forEach{p->
            val lift=if(s.selected==p.id&&!s.running)SelectedLift else 0f
            shadow(tileFootprint(rules,p,p.x+.35f,p.y+.35f),base,Offset(.035f,.06f)*(1f+lift*8),if(lift>0).30f else .24f,px*(.05f+lift*.6f))
        }
        tier.forEach{p->
            val lift=if(s.selected==p.id&&!s.running)SelectedLift else 0f
            paintTile3D(rules,p,p.x+.35f,p.y+.35f,base+lift,textures,s.selected==p.id&&!s.running,if(p.id==ghost)110 else 255)
        }
    }
    paintFence(rest)
    signs(false)
}

/** Lifted tile following the finger, with its shadow and the snapped landing cells. */
internal fun ScenePainter.paintDragPreview(s:GameState,p:Placement,frame:Offset,grab:Offset,textures:SceneTextures,problem:Boolean) {
    val rules=s.rules;val levels=stackLevels(rules,s.placed.filter{it.id!=p.id})
    val others=s.placed.filter{it.id!=p.id}
    val candidate=p.copy(x=(frame.x-.35f-grab.x).roundToInt(),y=(frame.y-.35f-grab.y).roundToInt())
    val(w,h)=rules.dimensions(p.r)
    val landing=surfaceHeight(rules,others,levels,candidate)
    val color=if(problem)argb("#c04835")else if(others.any{rules.overlaps(candidate,it)})argb("#a96b1e")else Green.toArgbInt()
    val px=camera.pixelsPerUnit(V3(frame.x,0f,frame.y))
    if(!problem){
        val cell=roundedOutline(candidate.x+.35f,candidate.y+.35f,candidate.x+.35f+w,candidate.y+.35f+h,.06f,3).map{V3(it.x,landing+.004f,it.y)}
        polygon(cell,(color and 0x00ffffff) or (0x55 shl 24))
        outline(cell,color,px*.04f)
    }
    val x=frame.x-grab.x;val z=frame.y-grab.y
    shadow(tileFootprint(rules,p,x,z),landing,Offset(.08f,.14f),.22f,px*.16f)
    paintTile3D(rules,p,x,z,landing+DragLift,textures,true)
    if(problem){
        val ring=roundedOutline(x,z,x+w,z+h,.08f,3).map{V3(it.x,landing+DragLift+TileThickness+.003f,it.y)}
        outline(ring,color,px*.04f)
    }
}

/** Run feedback under the horse: the travelled route glows gold, the road ahead is a dashed
 * guide, hooves kick up dust, and every rail the horse clears bursts in its obstacle color. */
internal fun ScenePainter.paintRunEffects(rules:Rules,route:List<RouteStep>,progress:Float) {
    if(route.isEmpty())return
    val end=route.size-.0001f
    val cursor=(progress*route.size).coerceIn(0f,end)
    val ground=TileThickness+.004f
    fun world(t:Float):V3 {val c=t.coerceIn(0f,end);val p=rules.position(route[c.toInt()],c%1);return V3(p.x+.35f,ground,p.y+.35f)}
    val px=camera.pixelsPerUnit(V3(2.85f,ground,2.35f))
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    fun trace(from:Float,to:Float):Path {
        val line=Path();var t=from;var first=true
        while(true){val q=camera.project(world(t));if(first)line.moveTo(q.x,q.y)else line.lineTo(q.x,q.y);first=false;if(t>=to)break;t=min(to,t+.06f)}
        return line
    }
    if(cursor<end){
        paint.color=android.graphics.Color.argb(215,255,252,242);paint.strokeWidth=px*.035f
        paint.pathEffect=android.graphics.DashPathEffect(floatArrayOf(px*.06f,px*.09f),0f)
        canvas.drawPath(trace(cursor,end),paint);paint.pathEffect=null
    }
    if(cursor>.02f){
        val done=trace(0f,cursor)
        paint.color=android.graphics.Color.argb(105,239,188,84);paint.strokeWidth=px*.2f;canvas.drawPath(done,paint)
        paint.color=android.graphics.Color.argb(240,226,164,52);paint.strokeWidth=px*.07f;canvas.drawPath(done,paint)
        paint.color=android.graphics.Color.argb(220,255,246,216);paint.strokeWidth=px*.022f;canvas.drawPath(done,paint)
    }
    val dot=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL}
    // Dust: puffs left behind along the road, larger and fainter the older they are.
    for(k in 1..6){
        val back=cursor-k*.05f;if(back<=0f)break
        val q=camera.project(world(back)+V3(0f,.03f+k*.012f,0f))
        dot.color=android.graphics.Color.argb((92-k*13).coerceAtLeast(0),205,172,122)
        canvas.drawCircle(q.x+sin(k*2.1f+cursor*9f)*px*.025f,q.y,px*(.028f+k*.013f),dot)
    }
    // Rail bursts: a ring and sparks in the obstacle's color, for half a road after the jump.
    route.forEachIndexed{i,step->
        if(step.jump==null)return@forEachIndexed
        val road=rules.road(step);val at=road.jumpAt?:.5f
        val crossing=i+if(step.from==road.from)at else 1-at
        val t=(cursor-crossing)/.5f
        if(t<0f||t>1f)return@forEachIndexed
        val color=android.graphics.Color.parseColor(rules.pieces.getValue(step.p.id).color)
        val center=camera.project(world(crossing)+V3(0f,.22f,0f))
        val fade=((1f-t)*255).roundToInt().coerceIn(0,255)
        paint.color=(color and 0xffffff) or (fade*3/4 shl 24);paint.strokeWidth=px*.035f*(1.2f-t)
        canvas.drawCircle(center.x,center.y,px*(.12f+.42f*t),paint)
        for(n in 0 until 8){
            val angle=n*PI.toFloat()/4+i;val reach=px*(.16f+.5f*t)
            dot.color=if(n%2==0)((color and 0xffffff) or (fade shl 24))else android.graphics.Color.argb(fade,255,226,140)
            canvas.drawCircle(center.x+cos(angle)*reach,center.y+sin(angle)*reach*.7f-px*.12f*t,px*.035f*(1.1f-t),dot)
        }
    }
}

/** Bitmaps baked once from the same 2D art the top-down board paints. */
internal class SceneTextures(private val bakeTile:(Int)->Bitmap,private val bakeLabel:(Int,RoadNumberLayout)->Bitmap,private val bakeBoard:(String,String)->Bitmap,private val bakeSign:(String,Boolean,Boolean)->Bitmap) {
    private val tiles=mutableMapOf<Int,Bitmap>()
    private val labels=mutableMapOf<Pair<Int,Pair<Float,Float>>,Bitmap>()
    private val signs=mutableMapOf<Triple<String,Boolean,Boolean>,Bitmap>()
    private var board:Triple<String,String,Bitmap>?=null
    fun tile(id:Int)=tiles.getOrPut(id){bakeTile(id)}
    fun label(id:Int,layout:RoadNumberLayout)=labels.getOrPut(id to (layout.width to layout.height)){bakeLabel(id,layout)}
    fun sign(name:String,start:Boolean,finish:Boolean)=signs.getOrPut(Triple(name,start,finish)){bakeSign(name,start,finish)}
    fun board(start:String,finish:String):Bitmap {
        board?.let{(a,b,bitmap)->if(a==start&&b==finish)return bitmap}
        return bakeBoard(start,finish).also{board=Triple(start,finish,it)}
    }
}
