package com.zoomzhao.racingclub

data class Point(val x: Float, val y: Float)
data class Placement(val id: Int, val x: Int, val y: Int, val r: Int)
data class RoadPath(val from:Int,val to:Int,val jump:Int?,val samples:List<Point>,val jumpAt:Float?=.5f,val length:Float=1.5f)
data class Piece(val id:Int,val color:String,val ports:List<Point>,val samples:List<Point>,val gate:List<Float>,val paths:List<RoadPath> = listOf(RoadPath(0,1,id,samples)))
data class Level(val id:Int,val title:String,val start:String,val finish:String,val sequence:List<Int>,val solution:List<Placement>,val group:String="green",val solutionStart:String=start,val solutionFinish:String=finish) {
    val requiredPieces get()=sequence.distinct().sorted()
    val solveEndpoints get()=start.isEmpty() && finish.isEmpty()
    val number get()=id.toString().padStart(2,'0')
}
data class RouteStep(val p:Placement,val from:Int,val to:Int,val pathIndex:Int,val jump:Int?)
data class Score(val level:Int,val millis:Long,val moves:Int,val hints:Int,val timestamp:Long=System.currentTimeMillis())

class Rules(val pieces:Map<Int,Piece>,val gates:Map<String,Point>,val levels:List<Level>,val inventory:Map<Int,String>) {
    fun rotate(pt:Point,r:Int):Point = when(r) {1->Point(1-pt.y,pt.x);2->Point(2-pt.x,1-pt.y);3->Point(pt.y,2-pt.x);else->pt}
    fun dimensions(r:Int)=if(r%2==0)2 to 1 else 1 to 2
    fun ports(p:Placement)=pieces.getValue(p.id).ports.map {rotate(it,p.r).let{q->Point(q.x+p.x,q.y+p.y)}}
    fun fits(p:Placement,all:List<Placement>):Boolean {
        if(p.id !in pieces || p.r !in 0..3)return false
        val(w,h)=dimensions(p.r)
        if(p.x<0||p.y<0||p.x+w>5||p.y+h>4)return false
        return all.all{o->val(ow,oh)=dimensions(o.r);o.id==p.id||p.x+w<=o.x||o.x+ow<=p.x||p.y+h<=o.y||o.y+oh<=p.y}
    }
    /** Search only legal smooth port pairs, tracking printed obstacle progress.
     * A tile can be entered repeatedly or traversed without crossing its rail.
     * No saved solution or reference endpoint participates in this decision. */
    fun trace(level:Level,all:List<Placement>,start:String?=null,finish:String?=null):List<RouteStep>? {
        if(all.size!=level.requiredPieces.size||all.map{it.id}.distinct().size!=all.size||all.any{it.id !in level.sequence||!fits(it,all)})return null
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
