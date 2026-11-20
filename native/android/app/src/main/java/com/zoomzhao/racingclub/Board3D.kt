package com.zoomzhao.racingclub

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Where the horse is on its route at [progress], or null when no run is playing. */
/** [stride] is the gallop phase in [0, 1); about three strides cover one road. [jumpPhase]
 * runs 0 → 1 through the take-off, flight and landing over a rail, and is null elsewhere. */
internal data class HorsePose(val at:Point,val direction:Point,val jump:Float,val heading:HorseHeading,val stride:Float=0f,val jumpPhase:Float?=null)
internal fun horsePose(s:GameState,progress:Float):HorsePose? {
    val route=s.activeRoute
    if(!s.running||route.isEmpty())return null
    val cursor=(progress*route.size).coerceIn(0f,route.size-.0001f);val step=route[cursor.toInt()]
    val raw=s.rules.position(step,cursor%1)
    fun onRoute(at:Float):Point {val t=at.coerceIn(0f,route.size-.0001f);return s.rules.position(route[t.toInt()],t%1)}
    val before=onRoute(cursor-.025f);val after=onRoute(cursor+.025f)
    val length=hypot(after.x-before.x,after.y-before.y).coerceAtLeast(.0001f)
    val direction=Point((after.x-before.x)/length,(after.y-before.y)/length)
    val road=s.rules.road(step);val along=if(step.from==road.from)cursor%1 else 1-cursor%1
    val jump=if(step.jump!=null)sin(PI.toFloat()*max(0f,1-abs(along-(road.jumpAt?:.5f))*8))else 0f
    val fromRail=along-(road.jumpAt?:.5f)
    val jumpPhase=if(step.jump!=null&&abs(fromRail)<JumpWindow)(fromRail+JumpWindow)/(2*JumpWindow)else null
    return HorsePose(raw,direction,jump,horseHeading(direction.x,direction.y),(cursor*3f)%1f,jumpPhase)
}
/** Half the stretch of road, around the rail, that the jump clip plays over. */
internal const val JumpWindow=.2f

private fun bake(width:Int,height:Int,block:DrawScope.()->Unit):Bitmap {
    val image=ImageBitmap(width.coerceAtLeast(1),height.coerceAtLeast(1))
    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,ComposeCanvas(image),Size(width.toFloat(),height.toFloat()),block)
    return image.asAndroidBitmap().also{it.prepareToDraw()}
}
/** The 3D faces reuse the exact 2D artwork: tile roads, number plates and the frame. */
internal fun sceneTextures(rules:Rules,m:BoardMaterials,cell:Int=192,boardCell:Int=200)=SceneTextures(
    bakeTile={id->bake(2*cell,cell){paintTile(rules,Placement(id,0,0,0),cell.toFloat(),Offset.Zero,m,shadow=false,rail=false)}},
    bakeLabel={id,layout->bake(ceil(layout.width*cell).toInt(),ceil(layout.height*cell).toInt()){paintRoadNumber(id,rules.pieces.getValue(id).color,layout,cell.toFloat(),Offset(size.width/2,size.height/2))}},
    bakeBoard={start,finish->bake((5.7f*boardCell).roundToInt(),(4.7f*boardCell).roundToInt()){paintBoardSurface(m,dropShadow=false);paintEntryMarkers(rules,start,finish,boardCell.toFloat(),tags=false)}},
    bakeSign={name,start,finish->bake(184,148){paintSignFace(name,start,finish)}}
)

/** Face of an upright entry sign: green for the start, a checkered threshold for the finish. */
private fun DrawScope.paintSignFace(name:String,start:Boolean,finish:Boolean) {
    val r=size.height*.16f
    val face=if(start)Green else androidx.compose.ui.graphics.Color(if(finish)0xfffff3df else 0xffffeccb)
    val rim=if(start)androidx.compose.ui.graphics.Color(0xfffff1cc)else androidx.compose.ui.graphics.Color(if(finish)0xffa4472f else 0xffc99a5e)
    drawRoundRect(face,cornerRadius=androidx.compose.ui.geometry.CornerRadius(r))
    drawRoundRect(rim,topLeft=Offset(4f,4f),size=Size(size.width-8f,size.height-8f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(r-3f),style=androidx.compose.ui.graphics.drawscope.Stroke(5f))
    if(finish){
        val cell=size.width/12f;val top=size.height-cell*2.2f
        for(i in 0 until 10)for(row in 0..1)drawRect(androidx.compose.ui.graphics.Color(if((i+row)%2==0)0xffa4472f else 0xfffffaf0),Offset(cell+i*cell,top+row*cell*.7f),Size(cell,cell*.7f))
    }
    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{
        color=if(start)android.graphics.Color.WHITE else android.graphics.Color.parseColor("#342718")
        textSize=size.height*.62f;textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true
    }
    val bounds=android.graphics.Rect();paint.getTextBounds(name,0,name.length,bounds)
    drawContext.canvas.nativeCanvas.drawText(name,size.width/2,size.height*(if(finish).42f else .5f)-(bounds.top+bounds.bottom)/2f,paint)
}

/** One gesture path for both views. [toFrame] maps a local pointer to board-frame units,
 * [toRoot] to the root coordinates the cross-panel drag uses. */
internal suspend fun PointerInputScope.boardGestures(s:GameState,drag:BoardDrag,toFrame:(Offset)->Offset,toRoot:(Offset)->Offset) {
    awaitEachGesture {
        val down=awaitFirstDown();if(s.running)return@awaitEachGesture
        // Another finger already owns a tile: this press changes nothing.
        val token=Any();if(!drag.claim(token))return@awaitEachGesture
        val at=down.position;val frame=toFrame(at)
        val x=floor(frame.x-.35f).toInt();val y=floor(frame.y-.35f).toInt()
        val hit=s.rules.hit(s.placed,x,y)
        val slop=max(viewConfiguration.touchSlop,12.dp.toPx())
        var moved=false;var dragging=false
        val wasSelected=hit!=null&&s.pressTile(hit.id)
        try {
            while(true){
                val change=awaitPointerEvent().changes.firstOrNull{it.id==down.id}?:break
                if(change.isConsumed)break
                if((change.position-at).getDistance()>slop)moved=true
                if(moved&&!dragging&&hit!=null)dragging=drag.begin(s,token,hit,toRoot(at),Offset(frame.x-.35f-hit.x,frame.y-.35f-hit.y))
                if(dragging){drag.moveTo(token,toRoot(change.position));change.consume()}
                if(!change.pressed){
                    if(dragging)drag.finish(s,token)
                    else if(!moved)s.releaseBoardTap(hit,wasSelected,x,y,onEntryLetter(s.rules,frame.x,frame.y))
                    break
                }
            }
        }finally{drag.release(token)}
    }
}

/** Where a board-frame point (x, z on the tile surface) lands in root pixels on the current
 * board. Instrumented tests use it to aim real touches at cells of the perspective board. */
internal object BoardProbe {@Volatile var frameToRoot:((Float,Float)->Offset)?=null}

/** Perspective board. Static scene and moving overlay are separate layers, so a finger
 * move redraws only the lifted tile and its landing preview. */
@Composable internal fun NativeBoard3D(s:GameState,progress:Float,modifier:Modifier,drag:BoardDrag,topInset:Dp=0.dp) {
    val art=gameArt()
    val horse=LocalResources.current.let{resources->remember(resources){HorseRenderer(HorseModel.get(resources))}}
    val textures=remember(s.rules,art){sceneTextures(s.rules,art.materials)}
    val fence=remember(s.rules){fenceLayout(s.rules)}
    val density=LocalDensity.current
    BoxWithConstraints(modifier) {
        val inset=with(density){topInset.toPx()}
        val camera=remember(constraints.maxWidth,constraints.maxHeight,inset){BoardCamera(constraints.maxWidth.toFloat(),constraints.maxHeight.toFloat(),topInset=inset)}
        var root by remember{mutableStateOf(Offset.Zero)}
        val ghost=drag.piece?.id?.takeIf{id->s.placed.any{it.id==id}}
        Canvas(Modifier.fillMaxSize().onGloballyPositioned{
            root=it.positionInRoot()
            val origin=root
            drag.board=camera.boardBounds().translate(origin)
            drag.toFrame={p->camera.toFrame(p-origin,TileThickness)}
            BoardProbe.frameToRoot={x,z->origin+camera.project(V3(x,TileThickness,z))}
        }.semantics {
            contentDescription="棋盘"
            stateDescription=s.placed.joinToString("；"){"${it.id}号，第${it.y+1}行第${it.x+1}列，${it.r*90}度"+if(s.rules.coveredBy(it,s.placed).isNotEmpty())"，下层被遮挡"else""}.ifEmpty{"空棋盘"}
        }.pointerInput(s.running,camera) {
            boardGestures(s,drag,{camera.toFrame(it,TileThickness)},{root+it})
        }.graphicsLayer()) {
            RenderStats.boardDraws++
            ScenePainter(camera,drawContext.canvas.nativeCanvas).paintBoard3D(s,textures,fence,ghost)
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val painter=ScenePainter(camera,drawContext.canvas.nativeCanvas)
            drag.piece?.let{p->
                if(drag.overBoard){
                    RenderStats.dropHighlightDraws++
                    painter.paintDragPreview(s,p,drag.frame(drag.pointer),drag.grabCells,textures,drag.problem(s).isNotEmpty())
                }
            }
            if(s.running){
                painter.paintRunEffects(s.rules,s.activeRoute,progress)
                horsePose(s,progress)?.let{pose->horse.paint(painter,pose)}
            }
        }
        horsePose(s,progress)?.let{pose->
            val foot=camera.project(V3(pose.at.x+.35f,TileThickness,pose.at.y+.35f))
            Box(Modifier.offset{IntOffset((foot.x-20).roundToInt(),(foot.y-40).roundToInt())}.size(14.dp).semantics{contentDescription="马儿";stateDescription="朝向${pose.heading.label}，跑动中"})
        }
        // Entry hit targets sit on the upright A–F signs.
        s.rules.gates.forEach{(name,pt)->
            val c=signCenter(pt);val center=camera.project(V3(c.x,SignBottom,c.y)+camera.billboardUp*((SignTop-SignBottom)/2))
            val side=max(with(density){48.dp.toPx()},camera.pixelsPerUnit(V3(c.x,0f,c.y))*.6f)
            Box(Modifier.offset{IntOffset((center.x-side/2).roundToInt(),(center.y-side/2).roundToInt())}.size(with(density){side.toDp()}).then(if(s.level.solveEndpoints&&!s.running)Modifier.clickable{s.showDialog("gate:$name")}else Modifier).semantics{
                contentDescription="入口 $name";stateDescription=listOfNotNull(if(name==s.startGate)"起点"else null,if(name==s.finishGate)"终点"else null).joinToString("、").ifEmpty{"未指定"}
            })
        }
        // Semantics-only nodes let TalkBack act on each visible stacked tile, as on the flat board.
        if(!s.running){
            val levels=stackLevels(s.rules,s.placed)
            s.placed.filter{s.rules.stackMarker(it,s.placed)!=null}.forEach{p->
                val(w,h)=s.rules.dimensions(p.r);val top=tileTop(levels.getValue(p.id))
                val corners=listOf(V3(p.x+.35f,top,p.y+.35f),V3(p.x+.35f+w,top,p.y+.35f),V3(p.x+.35f+w,top,p.y+.35f+h),V3(p.x+.35f,top,p.y+.35f+h)).map(camera::project)
                val box=Rect(corners.minOf{it.x},corners.minOf{it.y},corners.maxOf{it.x},corners.maxOf{it.y})
                val chosen=s.selected==p.id
                Box(Modifier.offset{IntOffset(box.left.roundToInt(),box.top.roundToInt())}.size(with(density){box.width.toDp()},with(density){box.height.toDp()}).semantics{
                    contentDescription="棋盘上 ${p.id}号拼板";selected=chosen
                    stateDescription="${if(chosen)"已选中" else "未选中"}，第${p.y+1}行第${p.x+1}列，${p.r*90}度${if(s.rules.coveredBy(p,s.placed).isNotEmpty())"，部分被遮挡" else ""}"
                    onClick(if(chosen)"旋转" else "选中"){s.tapTile(p.id);true}
                    customActions=pieceActions(s,p.id)
                })
            }
        }
    }
}

internal fun pieceActions(s:GameState,id:Int)=turnActions(s.turnMode).map{action->
    when(action){
        -1->CustomAccessibilityAction("逆时针旋转90度"){s.rotatePiece(id,-1);true}
        1->CustomAccessibilityAction("顺时针旋转90度"){s.rotatePiece(id,1);true}
        else->CustomAccessibilityAction("移回"){s.removePiece(id);true}
    }
}
