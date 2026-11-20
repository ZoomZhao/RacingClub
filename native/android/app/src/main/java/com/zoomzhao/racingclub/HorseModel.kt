package com.zoomzhao.racingclub

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Quaternius' CC0 low-poly horse ("Ultimate Animated Animals"), skinned offline into
 * whole-mesh frames by tools/bake_horse.py. Model space: +y up, +z toward the head,
 * hooves on y = 0, in the source's own units. */
internal class HorseModel(val colors:IntArray,val triangles:IntArray,val triangleColors:IntArray,val clips:Map<String,Clip>) {
    class Clip(val seconds:Float,val frames:List<FloatArray>)
    val vertexCount=clips.values.first().frames.first().size/3
    val triangleCount=triangleColors.size
    val gallop=clips.getValue("Gallop")
    val jump=clips.getValue("Gallop_Jump")
    /** Body length along z in the first gallop frame, to size the horse on the board. */
    val length=gallop.frames.first().let{f->(0 until vertexCount).maxOf{f[it*3+2]}-(0 until vertexCount).minOf{f[it*3+2]}}

    /** Blends the two baked frames around [phase] of a looping clip into [out]. */
    fun sample(clip:Clip,phase:Float,out:FloatArray) {
        val n=clip.frames.size;val at=((phase%1f+1f)%1f)*n
        val a=clip.frames[at.toInt()%n];val b=clip.frames[(at.toInt()+1)%n];val k=at-floor(at)
        for(i in out.indices)out[i]=a[i]+(b[i]-a[i])*k
    }

    /** The pose for one moment of a run: the gallop at [stride], crossing into the jump clip
     * while [jumpPhase] (0 → 1 across the rail window) is set. */
    fun pose(stride:Float,jumpPhase:Float?,out:FloatArray,scratch:FloatArray) {
        sample(gallop,stride,out)
        if(jumpPhase==null)return
        // Take-off crouch to landing, the part of the clip between two gallop strides.
        val t=(JumpFrom+(JumpTo-JumpFrom)*jumpPhase.coerceIn(0f,1f))/jump.seconds
        sample(jump,t,scratch)
        val w=smooth((jumpPhase/.14f).coerceIn(0f,1f))*smooth(((1f-jumpPhase)/.14f).coerceIn(0f,1f))
        for(i in out.indices)out[i]+=(scratch[i]-out[i])*w
    }

    companion object {
        const val JumpFrom=.27f
        const val JumpTo=1.3f
        private fun smooth(x:Float)=x*x*(3-2*x)
        @Volatile private var cached:HorseModel?=null
        fun get(resources:Resources):HorseModel=cached?:synchronized(this){cached?:parse(resources.openRawResource(R.raw.horse_quaternius).use{it.readBytes()}).also{cached=it}}

        fun parse(bytes:ByteArray):HorseModel {
            val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic=ByteArray(4).also{buffer.get(it)}
            require(String(magic)=="HRS1"){"Not a baked horse"}
            val vertices=buffer.short.toInt() and 0xffff;val triangles=buffer.short.toInt() and 0xffff
            val colorCount=buffer.get().toInt();val clipCount=buffer.get().toInt()
            val colors=IntArray(colorCount){buffer.int}
            val index=IntArray(triangles*3);val color=IntArray(triangles)
            for(t in 0 until triangles){for(c in 0..2)index[t*3+c]=buffer.short.toInt() and 0xffff;color[t]=buffer.get().toInt()}
            val clips=LinkedHashMap<String,Clip>()
            repeat(clipCount){
                val name=String(ByteArray(buffer.get().toInt()).also{buffer.get(it)})
                val frames=buffer.short.toInt() and 0xffff;val seconds=buffer.float
                val origin=FloatArray(3){buffer.float};val step=buffer.float
                clips[name]=Clip(seconds,List(frames){FloatArray(vertices*3){i->origin[i%3]+(buffer.short+32500)*step}})
            }
            return HorseModel(colors,index,color,clips)
        }
    }
}

/** World length of the horse from nose to tail, in board cells. */
internal const val HorseLength=1f
/** Extra height over a rail on top of the clip's own leap, so the hooves clear the pole. */
internal const val RailClearance=.14f

/** Paints the model into the board's perspective. Triangles are flat-shaded with the
 * board's light, sorted back to front and drawn as one vertex batch into a 2× offscreen
 * bitmap, which is filtered down so the low-poly edges stay smooth without seams. */
internal class HorseRenderer(val model:HorseModel) {
    private val local=FloatArray(model.vertexCount*3);private val scratch=FloatArray(model.vertexCount*3)
    private val world=FloatArray(model.vertexCount*3)
    private val screen=FloatArray(model.vertexCount*2);private val shadow=FloatArray(model.vertexCount*2)
    private val order=LongArray(model.triangleCount)
    private val verts=FloatArray(model.triangleCount*6);private val tint=IntArray(model.triangleCount*6)
    private val shadowTint=IntArray(model.triangleCount*6){ShadowColor}
    private var bitmap:Bitmap?=null;private var offscreen:Canvas?=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val blit=Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun paint(painter:ScenePainter,pose:HorsePose) {
        val camera=painter.camera
        model.pose(pose.stride,pose.jumpPhase,local,scratch)
        val k=HorseLength/model.length
        val ground=TileThickness
        val fx=pose.direction.x;val fz=pose.direction.y
        val ox=pose.at.x+.35f;val oz=pose.at.y+.35f;val lift=pose.jump*RailClearance
        var left=Float.MAX_VALUE;var top=Float.MAX_VALUE;var right=-Float.MAX_VALUE;var bottom=-Float.MAX_VALUE
        for(v in 0 until model.vertexCount){
            val mx=local[v*3]*k;val my=local[v*3+1]*k;val mz=local[v*3+2]*k
            // Model +x is the horse's left: up × forward on the board.
            val wx=ox+fx*mz+fz*mx;val wy=ground+my+lift;val wz=oz+fz*mz-fx*mx
            world[v*3]=wx;world[v*3+1]=wy;world[v*3+2]=wz
            val q=camera.project(V3(wx,wy,wz));screen[v*2]=q.x;screen[v*2+1]=q.y
            val h=wy-ground
            val s=camera.project(V3(wx+h*.12f,ground,wz+h*.2f));shadow[v*2]=s.x;shadow[v*2+1]=s.y
            left=min(left,min(q.x,s.x));right=max(right,max(q.x,s.x));top=min(top,min(q.y,s.y));bottom=max(bottom,max(q.y,s.y))
        }
        var visible=0
        val tri=model.triangles
        for(t in 0 until model.triangleCount){
            val a=tri[t*3]*3;val b=tri[t*3+1]*3;val c=tri[t*3+2]*3
            val ux=world[b]-world[a];val uy=world[b+1]-world[a+1];val uz=world[b+2]-world[a+2]
            val vx=world[c]-world[a];val vy=world[c+1]-world[a+1];val vz=world[c+2]-world[a+2]
            val normal=V3(uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx)
            val mid=V3((world[a]+world[b]+world[c])/3,(world[a+1]+world[b+1]+world[c+1])/3,(world[a+2]+world[b+2]+world[c+2])/3)
            if(!camera.facing(normal,mid))continue
            order[visible++]=(java.lang.Float.floatToIntBits(camera.depth(mid)).toLong() shl 32) or t.toLong()
        }
        java.util.Arrays.sort(order,0,visible)
        val margin=2f;val scale=2f
        left-=margin;top-=margin;right+=margin;bottom+=margin
        val width=((right-left)*scale).roundToInt().coerceIn(1,2048);val height=((bottom-top)*scale).roundToInt().coerceIn(1,2048)
        val target=bitmap?.takeIf{it.width>=width&&it.height>=height}
            ?:Bitmap.createBitmap(max(width,bitmap?.width?:0)+32,max(height,bitmap?.height?:0)+32,Bitmap.Config.ARGB_8888).also{bitmap=it;offscreen=Canvas(it)}
        val canvas=offscreen!!
        canvas.drawColor(0,PorterDuff.Mode.CLEAR)
        canvas.save();canvas.scale(scale,scale);canvas.translate(-left,-top)
        // Contact shadow: every triangle flattened onto the ground, merged in one translucent layer.
        for(t in 0 until model.triangleCount)for(c in 0..2){val v=tri[t*3+c];verts[t*6+c*2]=shadow[v*2];verts[t*6+c*2+1]=shadow[v*2+1]}
        canvas.saveLayerAlpha(left,top,right,bottom,(if(pose.jumpPhase!=null)52 else 72))
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES,model.triangleCount*6,verts,0,null,0,shadowTint,0,null,0,0,paint)
        canvas.restore()
        for(i in 0 until visible){
            val t=(order[visible-1-i] and 0xffffffffL).toInt()
            val a=tri[t*3]*3;val b=tri[t*3+1]*3;val c=tri[t*3+2]*3
            val ux=world[b]-world[a];val uy=world[b+1]-world[a+1];val uz=world[b+2]-world[a+2]
            val vx=world[c]-world[a];val vy=world[c+1]-world[a+1];val vz=world[c+2]-world[a+2]
            val color=shade(horsePalette(model.triangleColors[t],model.colors),V3(uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx).normalized(),.44f)
            for(n in 0..2){val v=tri[t*3+n];verts[i*6+n*2]=screen[v*2];verts[i*6+n*2+1]=screen[v*2+1];tint[i*3+n]=color}
        }
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES,visible*6,verts,0,null,0,tint,0,null,0,0,paint)
        canvas.restore()
        painter.canvas.drawBitmap(target,Rect(0,0,width,height),RectF(left,top,left+width/scale,top+height/scale),blit)
    }

    private companion object {
        val ShadowColor=android.graphics.Color.rgb(58,36,18)
    }
}

/** The source materials (coat, dark coat, light coat, hooves, hair, muzzle, eye white, pupil)
 * are tuned for a dark PBR scene; repaint them in the chestnut of the club's horse art. */
private val ClubHorseColors=intArrayOf(0xffb8662f.toInt(),0xff8c4622.toInt(),0xffd38c50.toInt(),0xff3a2a20.toInt(),0xff3f2317.toInt(),0xff5c3524.toInt(),0xfff4eee2.toInt(),0xff1a1410.toInt())
internal fun horsePalette(material:Int,source:IntArray)=ClubHorseColors.getOrElse(material){source[material]}
