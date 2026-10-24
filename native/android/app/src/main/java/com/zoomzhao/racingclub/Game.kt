package com.zoomzhao.racingclub

data class Point(val x: Float, val y: Float)
data class Placement(val id: Int, val x: Int, val y: Int, val r: Int)
data class RoadPath(val from:Int,val to:Int,val jump:Int?,val samples:List<Point>,val jumpAt:Float?=.5f,val length:Float=1.5f)
data class Piece(val id:Int,val color:String,val ports:List<Point>,val samples:List<Point>,val gate:List<Float>,val paths:List<RoadPath> = listOf(RoadPath(0,1,id,samples)))
data class Difficulty(val id:String,val label:String,val originalLabel:String,val color:String,val firstLevel:Int,val lastLevel:Int)
data class Level(val id:Int,val title:String,val start:String,val finish:String,val sequence:List<Int>,val solution:List<Placement>,val group:String="green",val solutionStart:String=start,val solutionFinish:String=finish,val difficultyId:String="") {
    val requiredPieces get()=sequence.distinct()
    val solveEndpoints get()=start.isEmpty() && finish.isEmpty()
    val number get()=id.toString().padStart(2,'0')
}
data class RouteStep(val p:Placement,val from:Int,val to:Int,val pathIndex:Int,val jump:Int?)
data class Score(val level:Int,val millis:Long,val moves:Int,val hints:Int,val timestamp:Long=System.currentTimeMillis())

class Rules(val pieces:Map<Int,Piece>,val gates:Map<String,Point>,val levels:List<Level>,val inventory:Map<Int,String>,val difficulties:List<Difficulty> = emptyList()) {
    fun rotate(pt:Point,r:Int):Point = when(r) {1->Point(1-pt.y,pt.x);2->Point(2-pt.x,1-pt.y);3->Point(pt.y,2-pt.x);else->pt}
    fun dimensions(r:Int)=if(r%2==0)2 to 1 else 1 to 2
    fun ports(p:Placement)=pieces.getValue(p.id).ports.map {rotate(it,p.r).let{q->Point(q.x+p.x,q.y+p.y)}}
    /** Placement permits temporary stacks; route validation still requires a flat board. */
    fun fits(p:Placement):Boolean {
        if(p.id !in pieces || p.r !in 0..3)return false
        val(w,h)=dimensions(p.r)
        return p.x>=0&&p.y>=0&&p.x+w<=5&&p.y+h<=4
    }
    fun overlaps(p:Placement,o:Placement):Boolean {
        val(w,h)=dimensions(p.r);val(ow,oh)=dimensions(o.r)
        return p.id!=o.id&&p.x<o.x+ow&&o.x<p.x+w&&p.y<o.y+oh&&o.y<p.y+h
    }
    fun hasOverlap(all:List<Placement>)=all.indices.any{i->all.drop(i+1).any{overlaps(all[i],it)}}
    /** Quarter-turn about a corner of the tile's own cells, not a point in its
     *  artwork. The top-left corner is kept whenever that stays on the board, so an
     *  interior tile does not move and four turns bring it home. The other corners are
     *  tried in order, and the result is pulled one cell inward only when every corner
     *  would leave the 5×4 board. A negative count is that many counterclockwise steps:
     *  Kotlin's remainder keeps the sign, so folding it into 0..3 would turn -1 into 3. */
    fun turnedInside(p:Placement,quarterTurns:Int):Placement {
        val sign=if(quarterTurns>=0)1 else -1
        val turns=kotlin.math.abs(quarterTurns)%4
        var cur=p
        repeat(turns){cur=quarterAboutCorner(cur,sign)}
        return cur
    }
    private fun quarterAboutCorner(p:Placement,sign:Int):Placement {
        val(w,h)=dimensions(p.r)
        val corners=listOf(Point(0f,0f),Point(w.toFloat(),0f),Point(0f,h.toFloat()),Point(w.toFloat(),h.toFloat()))
        for(corner in corners){
            val next=originForCorner(p,sign,corner)
            val(nw,nh)=dimensions(next.r)
            if(next.x in 0..5-nw && next.y in 0..4-nh)return next
        }
        val next=originForCorner(p,sign,corners.first())
        val(nw,nh)=dimensions(next.r)
        return next.copy(x=next.x.coerceIn(0,5-nw),y=next.y.coerceIn(0,4-nh))
    }
    private fun originForCorner(p:Placement,sign:Int,corner:Point):Placement {
        val nr=(p.r+sign+4)%4
        val ox=corner.x.toInt();val oy=corner.y.toInt()
        // Rotate the top-left around this corner: CW (a,b)->(b,-a), CCW (a,b)->(-b,a).
        val x=if(sign>=0)p.x+oy else p.x-oy
        val y=if(sign>=0)p.y-ox else p.y+ox
        return Placement(p.id,x,y,nr)
    }
    /** First tap from the tray: turn once if needed, then hug the nearest board edge. */
    fun initialPlacement(p:Placement):Placement =
        if(p.id in pieces&&p.r in 0..3&&p.x in 0..4&&p.y in 0..3&&!fits(p))turnedInside(p,1)else p
    /** Lists are stored bottom to top, including saved progress and undo snapshots. */
    fun hit(all:List<Placement>,x:Int,y:Int)=all.lastOrNull{val(w,h)=dimensions(it.r);x in it.x until it.x+w&&y in it.y until it.y+h}
    fun coveredBy(p:Placement,all:List<Placement>)=all.dropWhile{it.id!=p.id}.drop(1).filter{overlaps(p,it)}
    fun beneath(p:Placement,all:List<Placement>)=all.takeWhile{it.id!=p.id}.filter{overlaps(p,it)}
    fun stackMarker(p:Placement,all:List<Placement>):Point? {
        val(w,h)=dimensions(p.r)
        for(y in p.y until p.y+h)for(x in p.x+w-1 downTo p.x)
            if(hit(all,x,y)?.id==p.id)return Point(x+.92f,y+.82f)
        return null
    }
    /** Search only legal smooth port pairs, tracking printed obstacle progress.
     * A tile can be entered repeatedly or traversed without crossing its rail.
     * No saved solution or reference endpoint participates in this decision. */
    fun trace(level:Level,all:List<Placement>,start:String?=null,finish:String?=null):List<RouteStep>? {
        if(all.size!=level.requiredPieces.size||all.map{it.id}.distinct().size!=all.size||all.any{it.id !in level.sequence||!fits(it)}||hasOverlap(all))return null
        val startGate=level.start.ifEmpty{start.orEmpty()};val finishGate=level.finish.ifEmpty{finish.orEmpty()}
        val starts=if(startGate.isEmpty())gates.keys.toList()else listOf(startGate)
        val byPoint=mutableMapOf<Point,MutableList<Pair<Placement,Int>>>()
        for(p in all)ports(p).forEachIndexed{i,pt->byPoint.getOrPut(pt){mutableListOf()}.add(p to i)}
        data class State(val id:Int,val port:Int,val index:Int)
        for(gate in starts) {
            val entry=byPoint[gates[gate]]?.singleOrNull()?:continue
            val seen=mutableSetOf<State>()
            fun visit(p:Placement,from:Int,index:Int):List<RouteStep>? {
                if(!seen.add(State(p.id,from,index)))return null
                val piece=pieces.getValue(p.id)
                for((pathIndex,path)in piece.paths.withIndex()) {
                    if(path.from!=from&&path.to!=from)continue
                    if(path.jump!=null&&(index>=level.sequence.size||path.jump!=level.sequence[index]))continue
                    val nextIndex=index+if(path.jump==null)0 else 1
                    val to=if(path.from==from)path.to else path.from
                    val step=RouteStep(p,from,to,pathIndex,path.jump)
                    val end=ports(p)[to]
                    val reached=gates.entries.firstOrNull{it.value==end}?.key
                    if(reached!=null) {
                        if(nextIndex==level.sequence.size&&(finishGate.isEmpty()||reached==finishGate))return listOf(step)
                    }else {
                        val next=byPoint[end]?.singleOrNull{it.first.id!=p.id}?:continue
                        visit(next.first,next.second,nextIndex)?.let{return listOf(step)+it}
                    }
                }
                return null
            }
            visit(entry.first,entry.second,0)?.let{return it}
        }
        return null
    }
    fun road(step:RouteStep)=pieces.getValue(step.p.id).paths[step.pathIndex]
    fun position(step:RouteStep,progress:Float):Point {
        val path=road(step);val samples=path.samples
        val t=(if(step.from==path.from)progress else 1-progress).coerceIn(0f,1f)*(samples.size-1)
        val i=t.toInt().coerceAtMost(samples.lastIndex-1);val f=t-i;val a=samples[i];val b=samples[i+1]
        val p=rotate(Point(a.x+(b.x-a.x)*f,a.y+(b.y-a.y)*f),step.p.r)
        return Point(step.p.x+p.x,step.p.y+p.y)
    }
    fun endpoint(route:List<RouteStep>,finish:Boolean):String? {
        val step=if(finish)route.lastOrNull()else route.firstOrNull();step?:return null
        val pt=ports(step.p)[if(finish)step.to else step.from]
        return gates.entries.firstOrNull{it.value==pt}?.key
    }
}

/** Native storage boundary; can be replaced with an authenticated service. */
interface LeaderboardRepository {fun list(level:Int):List<Score>;fun save(score:Score)}

/** Raw saved progress. The session validates pieces and gates before using it. */
data class SavedProgress(val placed:List<Placement>,val moves:Int,val hints:Int,val elapsed:Long,val start:String,val finish:String,val finished:Boolean)
/** Which quarter-turn buttons appear beside a selected tile. */
enum class TurnMode { BOTH, CLOCKWISE, COUNTERCLOCKWISE }

/** Per-level progress boundary, so sessions can run against memory in JVM tests. */
interface ProgressStore {
    fun load(level:Int):SavedProgress?
    fun save(level:Int,progress:SavedProgress)
    fun completed(level:Int):Boolean
    fun markCompleted(level:Int)
    /** Drop every unfinished layout and completion mark. Best scores stay. */
    fun clearAll() {}
}
