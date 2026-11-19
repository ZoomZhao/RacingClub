package com.zoomzhao.racingclub

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.*
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import kotlin.math.*

internal val Green=Color(0xff254c3b);internal val Paper=Color(0xfff8f5ed);internal val Brown=Color(0xff78543b)
private fun nativeColor(s:String)=Color(android.graphics.Color.parseColor(s))
private fun readable(s:String,vararg on:Long)=Color(readableInk(android.graphics.Color.parseColor(s).toLong() and 0xffffffffL,*on))
private fun clock(ms:Long)="%02d:%02d.%02d".format(ms/60000,ms/1000%60,ms%1000/10)

@Composable private fun DifficultyBadge(d:Difficulty) {
    val tint=readable(d.color,PaperFace)
    Text(d.label,fontSize=10.sp,lineHeight=12.sp,fontWeight=FontWeight.Bold,color=tint,maxLines=1,modifier=Modifier.background(Well,RoundedCornerShape(50)).padding(horizontal=8.dp,vertical=1.dp).semantics{contentDescription="难度：${d.label} ${d.originalLabel}"})
}

@Composable private fun Action(icon:ImageVector,label:String,enabled:Boolean=true,slot:Dp=40.dp,onClick:()->Unit) {
    val interaction=remember{MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
    Box(Modifier.size(slot).gameControlFace(pressed).clip(RoundedCornerShape(10.dp)).clickable(enabled=enabled,interactionSource=interaction,indication=null,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Icon(icon,contentDescription=label,tint=(if(label=="提示")HintInk else Green).copy(alpha=if(enabled)1f else .3f),modifier=Modifier.size(slot*.53f))
    }
}
/** Labeled dock control: icon over a short word, at least 52dp tall. */
@Composable private fun DockButton(icon:ImageVector,label:String,description:String=label,enabled:Boolean=true,modifier:Modifier=Modifier,onClick:()->Unit) {
    val interaction=remember{MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
    val tint=(if(label=="提示")HintInk else Green).copy(alpha=if(enabled)1f else .32f)
    Box(modifier.widthIn(max=72.dp).height(52.dp).gameControlFace(pressed&&enabled).clip(RoundedCornerShape(10.dp)).clickable(enabled=enabled,interactionSource=interaction,indication=null,role=Role.Button,onClick=onClick).semantics{contentDescription=description;if(!enabled)disabled()},contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(bottom=2.dp)) {
            Icon(icon,null,Modifier.size(21.dp),tint=tint)
            Text(label,fontSize=(10f/LocalDensity.current.fontScale.coerceAtLeast(1f)).sp,lineHeight=12.sp,fontWeight=FontWeight.Bold,color=tint,maxLines=1)
        }
    }
}
/** Fixed toolbar under the board: the selected tile's turns and move-back on the left,
 * undo, hint and reset on the right. Nothing here ever covers a board cell. */
/** Obstacles cleared so far and a gold track that follows the horse. The count recomposes
 * only when it changes; the track reads the progress while drawing. */
@Composable private fun RunMeter(s:GameState,modifier:Modifier) {
    val total=remember(s.activeRoute){s.activeRoute.count{it.jump!=null}}
    val crossed by remember(s){derivedStateOf{crossingFeedback(s.rules,s.activeRoute,s.runProgress).crossed}}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Icon(Icons.Default.EmojiEvents,null,Modifier.size(15.dp),tint=Accent)
            Text("已跨越 $crossed / $total",fontSize=13.sp,fontWeight=FontWeight.Bold,color=Green,maxLines=1)
            Spacer(Modifier.weight(1f))
            Text(if(crossed>=total)"冲向终点！" else "加油，继续跳！",fontSize=11.sp,color=Brown,maxLines=1)
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)){
            val r=CornerRadius(size.height/2)
            drawRoundRect(Well,cornerRadius=r)
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xffe2a434),Color(0xffefbc54))),size=Size(size.width*s.runProgress.coerceIn(0f,1f),size.height),cornerRadius=r)
        }
    }
}
@Composable private fun ActionDock(s:GameState,modifier:Modifier) {
    if(s.running){
        // Kept on the left, away from undo, hint and reset, so a repeated hint tap cannot skip the run.
        Row(modifier.clubPanel(16.dp,3.dp).padding(horizontal=6.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.fillMaxWidth(.42f).height(50.dp).gameControlFace().clip(RoundedCornerShape(10.dp)).clickable(role=Role.Button){s.finishRun()}.semantics{contentDescription="跳过动画"},contentAlignment=Alignment.Center){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    Icon(Icons.Default.SkipNext,null,Modifier.size(22.dp),tint=Green)
                    Text("跳过动画",fontSize=13.sp,fontWeight=FontWeight.Bold,color=Green)
                }
            }
            Spacer(Modifier.width(14.dp))
            RunMeter(s,Modifier.weight(1f).padding(end=10.dp))
        }
        return
    }
    val id=s.selected;val placed=id?.let{i->s.placed.firstOrNull{it.id==i}}
    Row(modifier.clubPanel(16.dp,3.dp).padding(horizontal=6.dp,vertical=5.dp).semantics{contentDescription="棋盘操作"},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        val key=Modifier.weight(1f,fill=false).fillMaxWidth()
        Row(Modifier.weight(3.8f).semantics{contentDescription=if(placed!=null)"拼板操作" else if(id!=null)"托盘拼板操作" else "未选拼板"},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            if(id!=null){
                Box(Modifier.size(44.dp,52.dp).clubWell(10.dp),contentAlignment=Alignment.Center){NumberBadge(id,s,30.dp)}
                turnActions(s.turnMode).forEach{action->
                    when(action){
                        -1->DockButton(Icons.Default.RotateLeft,"逆时针","逆时针旋转90度",modifier=key){s.rotatePiece(id,-1)}
                        1->DockButton(Icons.Default.RotateRight,"顺时针","顺时针旋转90度",modifier=key){s.rotatePiece(id,1)}
                        else->if(placed!=null)DockButton(Icons.Default.Unarchive,"移回",modifier=key){s.removePiece(id)}
                    }
                }
            }else Row(Modifier.weight(1f).height(52.dp).clubWell(10.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Icon(Icons.Default.TouchApp,null,Modifier.size(18.dp),tint=Brown)
                Text("点拼板选择 · 再点一次旋转",fontSize=11.sp,lineHeight=14.sp,color=Brown,maxLines=2)
            }
        }
        Box(Modifier.size(1.dp,36.dp).background(Line))
        DockButton(Icons.Default.Undo,"撤销",modifier=Modifier.weight(1f).fillMaxWidth()){s.undo()}
        DockButton(Icons.Default.Lightbulb,"提示",modifier=Modifier.weight(1f).fillMaxWidth()){s.hint()}
        DockButton(Icons.Default.Refresh,"重置",modifier=Modifier.weight(1f).fillMaxWidth()){s.showDialog("confirm-reset")}
    }
}
@Composable private fun NumberBadge(id:Int,s:GameState,diameter:Dp=25.dp,active:Boolean=false,description:String?=null,passed:Boolean=false,pulse:Float=0f) {
    val shape=RoundedCornerShape(50)
    Box(Modifier.size(diameter).zIndex(if(active||pulse>.001f)2f else 0f).graphicsLayer{scaleX=1f+pulse*.14f;scaleY=1f+pulse*.14f}.drawBehind{
        val radius=size.minDimension/2
        if(pulse>.001f)drawCircle(Color(0xffefbc54).copy(alpha=.45f*pulse),radius+3.dp.toPx())
        // All progress decoration stays outside the number face; the digit is drawn last.
        if(active||passed)drawCircle(if(passed)Color(0xffd9a442)else Green,radius+1.dp.toPx(),style=Stroke(2.dp.toPx()))
    }.background(Color(numberFace(id)),shape).border(1.dp,Color(0xfff9e8c8),shape).then(if(description!=null)Modifier.semantics{contentDescription=description;stateDescription=if(passed)"已跨越${if(pulse>.01f)"，完成动效中"else""}"else"等待跨越"}else Modifier),contentAlignment=Alignment.Center) {
        Text("$id",color=Color(numberInk(id)),fontSize=(diameter.value*.57f/LocalDensity.current.fontScale.coerceAtLeast(1f)).sp,fontWeight=FontWeight.Bold,maxLines=1)
    }
}
@Composable internal fun CourseSequence(s:GameState,l:Level,modifier:Modifier=Modifier,crossed:Int=0,pulse:Float=0f) {
    BoxWithConstraints(modifier.clubWell(12.dp).padding(horizontal=9.dp,vertical=9.dp).semantics{contentDescription="障碍顺序";stateDescription="${s.startGate.ifEmpty{"?"}} → ${l.sequence.joinToString(" → ")} → ${s.finishGate.ifEmpty{"?"}}"}) {
        val rows=courseRows(l.sequence.size+2,maxWidth.value,1f)
        val diameter=min(24f,(maxWidth.value-8f-(rows.maxOf{it.size}-1)*8f)/rows.maxOf{it.size}).dp
        val rowWidth=(diameter.value*rows.maxOf{it.size}+8f*(rows.maxOf{it.size}-1)).dp
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
            rows.forEachIndexed{rowIndex,indices->
                val reversed=rowIndex%2==1
                Row(Modifier.width(rowWidth),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(reversed)Arrangement.End else Arrangement.Start) {
                    val display=if(reversed)indices.reversed()else indices
                    display.forEachIndexed{column,index->
                        if(column>0)Icon(if(reversed)Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.width(8.dp).height(9.dp),tint=Brown)
                        when(index){
                            0->CourseEndpoint(s.startGate.ifEmpty{"?"},false,diameter)
                            l.sequence.size+1->CourseEndpoint(s.finishGate.ifEmpty{"?"},true,diameter)
                            else->NumberBadge(l.sequence[index-1],s,diameter,s.running&&index-1==crossed,"目标 $index：${l.sequence[index-1]}号",s.running&&index<=crossed,if(s.running&&index==crossed)pulse else 0f)
                        }
                    }
                }
                if(rowIndex<rows.lastIndex)Row(Modifier.width(rowWidth).height(8.dp).padding(horizontal=(diameter.value/2-4).coerceAtLeast(0f).dp),horizontalArrangement=if(reversed)Arrangement.Start else Arrangement.End){Icon(Icons.Default.ArrowDownward,null,Modifier.size(8.dp),tint=Brown)}
            }
        }
    }
}
@Composable private fun CourseEndpoint(letter:String,finish:Boolean,diameter:Dp) {
    val color=if(finish)Color(0xffa95837)else Green
    Box(Modifier.size(diameter).background(Panel,RoundedCornerShape(50)).border(1.5.dp,color,RoundedCornerShape(50)).semantics{contentDescription=if(finish)"目标终点"else"目标起点"},contentAlignment=Alignment.Center) {
        Text(letter,fontSize=(diameter.value*.56f/LocalDensity.current.fontScale.coerceAtLeast(1f)).sp,fontWeight=FontWeight.Bold,color=color,maxLines=1)
    }
}

@Composable fun RacingApp(s:GameState) {
    val drag=remember(s.level.id,s.screen){BoardDrag()}
    val art=gameArt()
    // A solved board starts the run after a short settle; every guard is re-checked then.
    LaunchedEffect(s.level.id,s.placed,s.screen,s.dialog,s.chosenStart,s.chosenFinish,s.foreground,drag.busy) {
        if(s.canAutoRun()&&!drag.busy&&s.trace()!=null){delay(250);s.beginRunIfSolved()}
    }
    // The run follows the session: it halts in the background and resumes after recreation.
    LaunchedEffect(s.running,s.foreground) {
        if(!s.running||!s.foreground)return@LaunchedEffect
        val from=s.runProgress.coerceIn(0f,1f)
        Animatable(from).animateTo(1f,tween(((1f-from)*s.activeRoute.size*1350).roundToInt(),easing=LinearEasing)){s.runProgress=value}
        s.finishRun()
    }
    BackHandler(s.screen!="home") { s.home() }
    MaterialTheme(colorScheme=ClubColors,typography=MaterialTheme.typography) {
    CompositionLocalProvider(LocalGameArt provides art) {
    Surface(color=Paper,modifier=Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.arena_backdrop),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
        // A cream wash keeps the meadow as atmosphere, so the board and panels lead.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Paper.copy(alpha=.80f),Paper.copy(alpha=.66f),Paper.copy(alpha=.80f)))))
        val margin=if(s.screen=="home")maxWidth*.05f else 14.dp
        val materials=boardMaterials()
        Column(Modifier.safeDrawingPadding().padding(horizontal=margin,vertical=8.dp)) {
            when(s.screen) {
                "home" -> TrainingHome(s)
                "win" -> {GameHeader(s);WinScreen(s)}
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Phones keep a compact tray. Tablets (shortest side ≥ 600dp) give the board
                    // the full 5.3-cell height and a wider tray so ten tiles stay tappable.
                    val tablet=min(maxWidth.value,maxHeight.value)>=600f
                    val trayReserve=(if(tablet)380f else 300f)+6f*(s.level.requiredPieces.size-6).coerceAtLeast(0)
                    val boardShare=if(tablet).62f else .54f
                    val dock=64f;val gap=8f
                    // The tilted board is wider than tall, so in 3D it takes all the width the tray spares.
                    val spare=(maxWidth.value-trayReserve).coerceAtLeast(maxWidth.value*boardShare)
                    val boardWidth=spare.dp
                    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(if(tablet)18.dp else 14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.width(boardWidth).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(gap.dp)) {
                            RunBoard(s,Modifier.weight(1f).fillMaxWidth(),drag)
                            ActionDock(s,Modifier.fillMaxWidth().height(dock.dp))
                        }
                        Workbench(s,drag,Modifier.weight(1f).fillMaxHeight().clubPanel())
                    }
                }
            }
        }
        // Root coordinates let a single native gesture travel from tray to board. The finger
        // only moves this layer; the tile is re-recorded when its validity flips.
        // Over the 3D board the scene itself lifts the tile, so this flat copy steps aside.
        val flatLift by remember(drag,s){derivedStateOf{drag.piece?.takeIf{!drag.overBoard}}}
        flatLift?.let{p->
            val invalid by remember(drag,p){derivedStateOf{drag.problem(s).isNotEmpty()}}
            Canvas(Modifier.fillMaxSize().graphicsLayer{val origin=drag.liftOrigin;translationX=origin.x;translationY=origin.y-18.dp.toPx()}) {
                val(w,h)=s.rules.dimensions(p.r);val u=drag.unit
                drawContext.canvas.saveLayer(Rect(Offset.Zero,Size(w*u,h*u)),Paint().apply{alpha=.86f});paintTile(s.rules,p.copy(x=0,y=0),u,Offset.Zero,materials,selected=true,number=true);if(invalid)drawRoundRect(Color(0xffc04835),Offset.Zero,Size(w*u,h*u),CornerRadius(u*.06f),style=Stroke(2.dp.toPx()));drawContext.canvas.restore()
            }
        }
        }
    }
    if(s.dialog.isNotEmpty()) ClubDialog(s)
    }
    }
}

@Composable private fun TrainingHome(s:GameState) {
    val fontScale=LocalDensity.current.fontScale
    val difficulty=s.rules.difficulties.first{it.id==s.selectedDifficultyId}
    val levels=s.rules.levels.filter{it.difficultyId==difficulty.id}
    val solved=s.rules.levels.count{s.solved(it.id)};val solvedHere=levels.count{s.solved(it.id)}
    val hue=nativeColor(difficulty.color)
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("♞ RacingClub",fontSize=(20f/fontScale.coerceAtLeast(1f)).sp,color=Green,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif,maxLines=1)
            Box(Modifier.padding(horizontal=12.dp).size(1.dp,18.dp).background(Line))
            Text("马术训练场",fontSize=18.sp,color=Green,fontWeight=FontWeight.Bold,maxLines=1)
            Spacer(Modifier.weight(1f))
            Row(Modifier.height(32.dp).background(Panel,RoundedCornerShape(50)).border(1.dp,Line,RoundedCornerShape(50)).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.EmojiEvents,null,Modifier.size(15.dp),tint=Accent)
                Text("$solved / 80 已完成",fontSize=if(fontScale>1.15f)11.sp else 12.sp,maxLines=1,color=Green,fontWeight=FontWeight.Bold)
                ProgressTrack(solved/80f,Green,Modifier.width(56.dp))
            }
            Spacer(Modifier.width(10.dp));Action(Icons.Default.Settings,"设置",slot=36.dp){s.showDialog("settings")}
        }
        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            HomeHero(s,Modifier.weight(.26f).fillMaxHeight())
            Column(Modifier.weight(.74f).fillMaxHeight().clubPanel().padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    s.rules.difficulties.forEachIndexed{index,d->
                        DifficultyPlaque(d,index,d.id==s.selectedDifficultyId,Modifier.weight(1f)){s.selectedDifficultyId=d.id}
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("${difficulty.label} · ${difficulty.originalLabel}",fontSize=12.sp,fontWeight=FontWeight.Bold,color=readable(difficulty.color,PlaqueFaceLight),maxLines=1)
                    ProgressTrack(solvedHere/levels.size.coerceAtLeast(1).toFloat(),hue,Modifier.weight(1f))
                    Text("$solvedHere / ${levels.size} 已完成",fontSize=11.sp,color=Brown,maxLines=1)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val cardHeight=maxOf((54f+max(0f,fontScale-1f)*32f).dp,(maxHeight-21.dp)/4)
                    LazyVerticalGrid(columns=GridCells.Fixed(4),modifier=Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        items(levels,key={it.id}){l->
                            ChallengePlaque(s,l,difficulty,cardHeight,Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
/** Photo card with the headline and a one-tap way back into the next unfinished level. */
@Composable private fun HomeHero(s:GameState,modifier:Modifier) {
    val next=s.rules.levels.firstOrNull{!s.solved(it.id)&&s.inProgress(it.id)}?:s.rules.levels.firstOrNull{!s.solved(it.id)}
    Box(modifier.shadow(5.dp,RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))) {
        Image(painterResource(R.drawable.horse_arena),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent,.45f to Color.Transparent,1f to Green.copy(alpha=.92f))))
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Spacer(Modifier.weight(1f))
            Text("今天，\n跃过新的挑战",fontSize=21.sp,lineHeight=29.sp,fontWeight=FontWeight.Bold,color=Color.White)
            if(next!=null){
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Panel).clickable(role=Role.Button){s.start(next)}.semantics(mergeDescendants=true){contentDescription="继续挑战 ${next.number}"}.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(s.inProgress(next.id))"继续 ${next.number}" else "开始 ${next.number}",fontSize=14.sp,fontWeight=FontWeight.Bold,color=Green,maxLines=1)
                    Spacer(Modifier.weight(1f));Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(18.dp),tint=Green)
                }
            }
        }
    }
}

/** Owns the 100 ms clock tick, so the timer refresh recomposes only this text. */
@Composable private fun ClockText(s:GameState) {
    var tick by remember { mutableLongStateOf(0) }
    LaunchedEffect(Unit) { while(true) { tick++;delay(100) } }
    Text(clock(if(s.running||s.screen=="win")s.elapsed else s.time()+tick*0),fontSize=12.sp,fontFamily=FontFamily.Monospace,color=Brown,maxLines=1)
}
/** One compact bar: back, the level and its chapter, then ranking, settings and help. */
@Composable private fun GameHeader(s:GameState,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().height(40.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Action(Icons.AutoMirrored.Filled.ArrowBack,"返回",slot=38.dp){s.home()}
        Spacer(Modifier.width(2.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
            Text("挑战 ${s.level.number}",fontSize=17.sp,lineHeight=19.sp,fontWeight=FontWeight.Bold,color=Green,maxLines=1)
            DifficultyBadge(s.difficulty())
        }
        Action(Icons.Default.EmojiEvents,"本关排行榜",slot=38.dp){s.showDialog("rank")}
        Action(Icons.Default.Settings,"设置",slot=38.dp){s.showDialog("settings")}
        Action(Icons.Default.HelpOutline,"玩法",slot=38.dp){s.showDialog("help")}
    }
}
/** Stopwatch chip that floats over the board's top-left corner. */
@Composable private fun ClockChip(s:GameState,modifier:Modifier=Modifier) {
    Row(modifier.height(30.dp).background(Panel.copy(alpha=.92f),RoundedCornerShape(50)).border(1.dp,Line,RoundedCornerShape(50)).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
        Icon(Icons.Default.Timer,null,Modifier.size(15.dp),tint=Brown)
        ClockText(s)
    }
}

/** Reads the run progress here, so only the board redraws each animation frame, not the whole screen. */
@Composable private fun RunBoard(s:GameState,modifier:Modifier,drag:BoardDrag) {
    Box(modifier) {
        NativeBoard3D(s,s.runProgress,Modifier.fillMaxSize(),drag)
        ClockChip(s,Modifier.align(Alignment.TopStart).padding(start=4.dp,top=2.dp))
    }
}
@Composable private fun RouteProgress(s:GameState,modifier:Modifier) {
    val crossing=crossingFeedback(s.rules,s.activeRoute,s.runProgress)
    CourseSequence(s,s.level,modifier,crossing.crossed,crossing.pulse)
}

@Composable private fun Workbench(s:GameState,drag:BoardDrag,modifier:Modifier) {
    val materials=boardMaterials()
    BoxWithConstraints(modifier.padding(12.dp)) {
    Column(Modifier.fillMaxSize()) {
        GameHeader(s)
        Box(Modifier.padding(vertical=8.dp).fillMaxWidth().height(1.dp).background(Line))
        SectionLabel("路线顺序"){
            EndpointLabel(false,s.startGate,if(s.level.solveEndpoints&&!s.running)({s.showDialog("start")})else null)
            EndpointLabel(true,s.finishGate,if(s.level.solveEndpoints&&!s.running)({s.showDialog("finish")})else null)
        }
        RouteProgress(s,Modifier.fillMaxWidth().padding(top=6.dp))
        val needed=s.level.requiredPieces
        SectionLabel("本关拼板",Modifier.padding(top=10.dp,bottom=6.dp),note="放置 ${needed.count{id->s.placed.any{it.id==id}}} / ${needed.size}")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Tiles are two cells long and wait lying flat, so pick the column count that gives a
            // 2:1 tile its largest cell size; cards never grow taller than such a tile needs.
            val pieces=s.level.requiredPieces;val gap=6f;val inset=8f
            fun unitFor(c:Int):Float{val rowsFor=(pieces.size+c-1)/c;return min(((maxWidth.value-gap*(c-1))/c-inset)/2f,(maxHeight.value-gap*(rowsFor-1))/rowsFor-inset)}
            val columns=(1..4).maxBy{c->unitFor(c)-c*.01f}
            val rows=pieces.chunked(columns)
            val cardWidth=(maxWidth.value-gap*(columns-1))/columns
            val cardHeight=min(cardWidth*.62f,(maxHeight.value-gap*(rows.size-1))/rows.size).coerceAtLeast(26f).dp
            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(gap.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(gap.dp)) {
                        row.forEach { id ->
                            val active=s.selected==id
                            val placed=s.placed.firstOrNull{it.id==id};val covered=placed?.let{s.rules.coveredBy(it,s.placed).isNotEmpty()}==true
                            val turn=placed?.r?:if(active)s.rotation else 0
                            var origin by remember{id.let{mutableStateOf(Offset.Zero)}}
                            Box(Modifier.weight(1f).height(cardHeight).onGloballyPositioned{origin=it.positionInRoot()}.background(if(active)Color(0xfffff3da)else if(placed!=null)Well else Color.White,RoundedCornerShape(12.dp)).border(if(active)2.dp else 1.dp,if(active)Accent else Line,RoundedCornerShape(12.dp)).pointerInput(id,s.running){
                                awaitEachGesture{
                                    val down=awaitFirstDown(requireUnconsumed=false);if(s.running)return@awaitEachGesture
                                    // Another finger already owns a tile: this press changes nothing.
                                    val token=Any();if(!drag.claim(token))return@awaitEachGesture
                                    val wasSelected=s.pressTile(id)
                                    val p=s.rackPiece(id)
                                    val slop=max(viewConfiguration.touchSlop,12.dp.toPx());var moving=false
                                    try{while(true){
                                        val change=awaitPointerEvent().changes.firstOrNull{it.id==down.id}?:break
                                        if(change.isConsumed)break
                                        if(!moving&&(change.position-down.position).getDistance()>slop)moving=drag.begin(s,token,p,origin+down.position)
                                        if(moving){drag.moveTo(token,origin+change.position);change.consume()}
                                        if(!change.pressed){if(moving)drag.finish(s,token)else s.releaseRackTap(id,wasSelected);break}
                                    }}finally{drag.release(token)}
                                }
                            }.clearAndSetSemantics {
                                role=Role.Button
                                if(s.running)disabled()
                                onClick(if(active)"旋转" else "选中"){if(s.running)false else{s.tapTile(id);true}}
                                contentDescription="拼板 $id"
                                selected=active
                                // Keep the visible status discoverable on this one actionable node.
                                if(placed!=null)text=AnnotatedString(if(covered)"被遮挡"else"已放置")
                                stateDescription="${if(active)"已选中"else"未选中"}，${if(placed!=null)"已放置"else"待放置"}，${turn*90}度${if(covered)"，下层被遮挡，点按置顶"else""}"
                            }.padding(3.dp),contentAlignment=Alignment.Center) {
                                // The card shows the tile exactly as it will land, and swings into each new turn.
                                var spin by remember{mutableFloatStateOf(0f)};var shownTurn by remember{mutableIntStateOf(turn)}
                                if(shownTurn!=turn){val d=((turn-shownTurn)%4+4)%4;spin+=if(d==3)-90f else d*90f;shownTurn=turn}
                                val swing by animateFloatAsState(spin,tween(220,easing=FastOutSlowInEasing),label="tray-turn")
                                Canvas(Modifier.fillMaxSize().padding(1.dp).graphicsLayer{rotationZ=swing-spin;alpha=if(placed!=null).55f else 1f}){
                                    val r=turn;val(w,h)=s.rules.dimensions(r);val u=min(size.width/w,size.height/h)
                                    val origin=Offset((size.width-w*u)/2,(size.height-h*u)/2)
                                    val numberBounds=if(u>0f)Rect(-origin.x/u+.04f,-origin.y/u+.04f,(size.width-origin.x)/u-.04f,(size.height-origin.y)/u-.04f)else null
                                    paintTile(s.rules,Placement(id,0,0,r),u,origin,materials,selected=active,number=true,minimumNumberFont=11.dp.toPx(),numberBounds=numberBounds)
                                }
                                if(covered)Icon(Icons.Default.Layers,null,Modifier.size(13.dp).align(Alignment.TopEnd),tint=Color(0xffa96b1e))
                                else if(placed!=null)Icon(Icons.Default.CheckCircle,null,Modifier.size(14.dp).align(Alignment.TopEnd),tint=Green)
                                if(placed!=null)Text(if(covered)"被遮挡"else"已放置",fontSize=9.sp,lineHeight=11.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=1,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=1.dp).background(if(covered)Color(CoveredTag)else Green,RoundedCornerShape(50)).padding(horizontal=7.dp,vertical=2.dp))
                            }
                        }
                        if(rows.size>1)repeat(columns-row.size){Spacer(Modifier.weight(1f))}
                    }
                }
            }
        }
        PlacementFeedback(s,drag)
    }
    }
}

/** Isolated so a moving finger recomposes this line only, and only when its text changes. */
@Composable private fun PlacementFeedback(s:GameState,drag:BoardDrag) {
    val message by remember(s,drag){derivedStateOf{
        val error=if(drag.piece!=null)drag.problem(s)else s.message
        val coveredCount=s.placed.count{s.rules.coveredBy(it,s.placed).isNotEmpty()}
        error to if(drag.piece!=null)drag.feedback(s)else error.ifEmpty{if(coveredCount>0)"$coveredCount 块被遮挡 · 点右侧编号置顶"else""}
    }}
    val(error,feedback)=message
    // Persistent amber feedback survives the gesture until every overlap is resolved.
    Box(Modifier.fillMaxWidth().height(28.dp),contentAlignment=Alignment.CenterStart){
        if(feedback.isNotEmpty())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
            if(error.isEmpty())Icon(Icons.Default.Layers,null,Modifier.size(14.dp),tint=Color(0xffa96b1e))
            Text(feedback,fontSize=10.sp,lineHeight=13.sp,color=if(error.isNotEmpty())Color(0xffa9442b)else Color(0xff925b1c),maxLines=2,modifier=Modifier.semantics{contentDescription="放置提示";stateDescription=feedback})
        }
    }
}

/** Result page: a show rosette over the arena photo, stars for how it was solved, the time
 * with its place on the local board, and the next steps. Confetti falls once on arrival. */
@Composable private fun WinScreen(s:GameState) {
    val records=remember(s.level.id,s.elapsed){s.repository.list(s.level.id)}
    val place=records.indexOfFirst{it.millis==s.elapsed&&it.moves==s.moves}+1
    val stars=when{s.hints==0->3;s.hints*2<=s.level.requiredPieces.size->2;else->1}
    val next=s.rules.levels.firstOrNull{it.id>s.level.id}
    val show=remember(s.level.id){Animatable(0f)}
    LaunchedEffect(s.level.id){show.animateTo(1f,tween(2600,easing=LinearEasing))}
    Box(Modifier.fillMaxSize().padding(top=10.dp)) {
    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(.9f).fillMaxHeight().shadow(5.dp,RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center) {
            Image(painterResource(R.drawable.horse_arena),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Green.copy(alpha=.30f),Green.copy(alpha=.62f),Green.copy(alpha=.92f)))))
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(170.dp).graphicsLayer{val k=(show.value*2600f/420f).coerceIn(0f,1f);val pop=1f+.12f*sin(PI.toFloat()*k);scaleX=k*pop;scaleY=k*pop;rotationZ=(1f-k)*-14f},contentAlignment=Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()){paintRosette()}
                    Column(Modifier.offset(y=(-14).dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("挑战",fontSize=11.sp,lineHeight=13.sp,color=Color(0xfffff3d6),fontWeight=FontWeight.Bold)
                        Text(s.level.number,fontSize=34.sp,lineHeight=36.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,color=Color.White)
                    }
                }
                Text("${s.difficulty().label} · 顺利完赛",fontSize=14.sp,fontWeight=FontWeight.Bold,color=Color.White)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().clubPanel(),contentAlignment=Alignment.Center){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("漂亮，顺利通关！",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Green)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.semantics(mergeDescendants=true){contentDescription="$stars 星"}) {
                repeat(3){k->
                    val earned=k<stars
                    Icon(if(earned)Icons.Default.Star else Icons.Default.StarBorder,null,Modifier.size(34.dp).graphicsLayer{
                        val t=((show.value*2600f-380f-k*170f)/240f).coerceIn(0f,1f);val pop=if(earned)1f+.35f*sin(PI.toFloat()*t)else 1f
                        scaleX=t*pop;scaleY=t*pop
                    },tint=if(earned)Accent else Line)
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(clock(s.elapsed),fontSize=28.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,color=Brown)
                val tag=when{place==1&&records.size>1->"新纪录";records.size<=1->"首次通关";place>0->"第 $place 名";else->null}
                if(tag!=null)Text(tag,fontSize=11.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=1,modifier=Modifier.background(if(place==1)Accent else Green,RoundedCornerShape(50)).padding(horizontal=9.dp,vertical=3.dp))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ResultStat("步数","${s.moves}",Modifier.weight(1f))
                ResultStat("提示",if(s.hints==0)"独立完成" else "${s.hints} 次",Modifier.weight(1f))
                ResultStat("本关最佳",records.firstOrNull()?.let{clock(it.millis)}?:clock(s.elapsed),Modifier.weight(1f))
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick={s.start(s.level)},shape=RoundedCornerShape(12.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Green),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp),modifier=Modifier.weight(.8f)) {
                    Icon(Icons.Default.Refresh,null,Modifier.size(17.dp),tint=Green);Spacer(Modifier.width(6.dp));Text("再来一次",fontWeight=FontWeight.Bold,color=Green,maxLines=1)
                }
                Button(onClick={next?.let{s.start(it)}?:s.home()},shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp),modifier=Modifier.weight(1.2f)) {
                    Text(if(next!=null)"挑战下一关" else "返回训练场",fontWeight=FontWeight.Bold,maxLines=1);Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(17.dp))
                }
            }
            TextButton(onClick={s.showDialog("rank")}) {Icon(Icons.Default.EmojiEvents,null,Modifier.size(16.dp),tint=Accent);Spacer(Modifier.width(6.dp));Text("查看本关排行榜")}
        }
        }
    }
    Canvas(Modifier.fillMaxSize()){paintConfetti(show.value)}
    }
}
@Composable private fun ResultStat(label:String,value:String,modifier:Modifier) {
    Column(modifier.clubWell(12.dp).padding(vertical=8.dp,horizontal=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,fontSize=10.sp,lineHeight=12.sp,color=Brown,maxLines=1)
        Text(value,fontSize=13.sp,lineHeight=16.sp,fontWeight=FontWeight.Bold,color=Green,maxLines=1)
    }
}
/** Horse-show rosette: two notched ribbon tails, a pleated gold ring and a green center. */
private fun DrawScope.paintRosette() {
    val c=Offset(size.width/2,size.height*.42f);val r=size.minDimension*.34f
    fun tail(dx:Float,color:Color) {
        val top=c+Offset(dx*.25f*r,0f);val w=r*.42f;val bottom=c+Offset(dx*r*.55f,r*1.6f)
        val path=androidx.compose.ui.graphics.Path().apply{
            moveTo(top.x-w/2,top.y);lineTo(top.x+w/2,top.y);lineTo(bottom.x+w/2,bottom.y);lineTo(bottom.x,bottom.y-w*.45f);lineTo(bottom.x-w/2,bottom.y);close()
        }
        drawPath(path,color)
    }
    tail(-1f,Color(0xff2f6b45));tail(1f,Color(0xff24583a))
    repeat(22){i->val a=i*2*PI.toFloat()/22;drawCircle(Color(0xffd29a3c),r*.2f,c+Offset(cos(a),sin(a))*r*.98f)}
    drawCircle(Color(0xffefbc54),r*1.02f,c)
    drawCircle(Color(0xfff6d48a),r*.86f,c,style=Stroke(r*.05f))
    drawCircle(Green,r*.74f,c)
    drawCircle(Color(0xffefbc54),r*.66f,c,style=Stroke(r*.025f))
}
/** One shower of confetti over the result: deterministic pieces falling while [t] goes 0→1. */
private fun DrawScope.paintConfetti(t:Float) {
    if(t<=0f||t>=1f)return
    val colors=listOf(Color(0xffefbc54),Color(0xff2f6b45),Color(0xffc04835),Color(0xff3c74a8),Color(0xffa4c45a),Color(0xffb44c86))
    val random=java.util.Random(7)
    repeat(70){i->
        val x=random.nextFloat()*size.width;val delay=random.nextFloat()*.35f;val speed=.75f+random.nextFloat()*.6f
        val k=((t-delay)/(1f-delay)).coerceIn(0f,1f);if(k<=0f)return@repeat
        val y=-20f+k*speed*size.height*1.1f;val sway=sin(k*9f+i)*22f
        val alpha=(1f-((k-.75f)/.25f).coerceIn(0f,1f))
        val w=6f+random.nextFloat()*7f;val h=w*(.4f+.4f*abs(sin(k*12f+i)))
        drawRect(colors[i%colors.size].copy(alpha=alpha),Offset(x+sway-w/2,y-h/2),Size(w,h))
    }
}

@Composable private fun ClubDialog(s:GameState) {
    AlertDialog(onDismissRequest={s.closeDialog()},containerColor=Panel,shape=RoundedCornerShape(20.dp),
        title={Text(when(s.dialog){"rank"->"挑战 ${s.level.number} · 本机排行榜";"start"->"选择起点";"finish"->"选择终点";"settings"->"设置";"confirm-clear"->"清除所有进度？";"confirm-reset"->"重置本关？";else->if(s.dialog.startsWith("gate:"))"入口 ${s.dialog.substringAfter(":")}"else"一条路，刚刚好的顺序"},fontSize=20.sp,lineHeight=27.sp,color=Green,fontWeight=FontWeight.Bold)},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            when(s.dialog) {
                "rank" -> {
                    val records=s.repository.list(s.level.id)
                    if(records.isEmpty())Text("还没有成绩，完成一次训练就会记录。")
                    records.forEachIndexed { i,r -> Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("${i+1}",modifier=Modifier.width(16.dp),fontSize=13.sp,color=Brown)
                        Text(clock(r.millis),modifier=Modifier.weight(1f),fontSize=14.sp,fontFamily=FontFamily.Monospace,color=Green)
                        Text("${r.moves} 步",fontSize=11.sp,color=Brown)
                        Text(if(r.hints==0)"独立完成" else "使用提示",fontSize=10.sp,color=if(r.hints==0)Green else Brown,modifier=Modifier.background(Color(0xffeee5d4),RoundedCornerShape(50)).padding(horizontal=7.dp,vertical=4.dp))
                    } }
                    Text("成绩保存在本机；线上榜接口已预留。",fontSize=10.sp,lineHeight=15.sp,modifier=Modifier.padding(top=5.dp))
                }
                "settings" -> SettingsBody(s)
                "confirm-clear" -> Text("会清除全部分关进度、未完成的摆放和完成标记。本机最佳成绩会保留。此操作不能撤销。",fontSize=13.sp,lineHeight=21.sp,color=Brown)
                "confirm-reset" -> Text("清空本关已摆放的拼板和起终点，重新开始这一关。本机最佳成绩会保留，也可以用撤销恢复。",fontSize=13.sp,lineHeight=21.sp,color=Brown)
                "start", "finish" -> {
                    Text("按字母选择棋盘边界，可与另一端相同。",fontSize=12.sp,color=Brown)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){s.rules.gates.keys.forEach{letter->OutlinedButton(onClick={s.chooseEndpoint(letter)},modifier=Modifier.size(42.dp),contentPadding=PaddingValues(0.dp)){Text(letter)}}}
                }
                else -> if(s.dialog.startsWith("gate:")){
                    val letter=s.dialog.substringAfter(":")
                    Text("将 $letter 设为起点或终点；两端可以使用同一入口。",fontSize=12.sp,color=Brown)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={s.chooseEndpoint(letter,false)}){Text("设为起点")};OutlinedButton(onClick={s.chooseEndpoint(letter,true)}){Text("设为终点")}}
                }else Text("看右上方障碍顺序。点右侧拼板选择，右侧卡片按放下时的方向显示；再点棋盘格放置。\n\n点一下选中，再点一次就旋转 90°，拼板在托盘里也能先转好再放。棋盘下方固定显示选中拼板的旋转和移回，以及撤销、提示、重置，不会遮住棋格。点空棋格或木框取消选择。\n\n可从右侧拖入棋盘，也可拖动棋盘上的拼板。棋盘内允许临时叠放，后放的在上面；点右侧编号可将下层置顶。边缘旋转会自动移入棋盘；消除重叠后才会判定通关。\n\n岔路须顺着道路平顺转向，不能在两个分支之间锐角转弯。7 号的两条路互不相通。经过无障碍支路不计一次跨障，同一障碍可能再次跨越。\n\n第 65–80 关还需点入口字母设置起点和终点。绿色箭头指向起点，棋格标记代表终点；两端可以相同。\n\n正确后自动停止计时，马儿沿完整路线跑到终点。",fontSize=13.sp,lineHeight=21.sp)
            }
        }},confirmButton={
            if(s.dialog=="confirm-clear") TextButton(onClick={s.clearAllProgress();s.closeDialog()}){Text("清除",color=Danger)}
            else if(s.dialog=="confirm-reset") TextButton(onClick={s.reset();s.closeDialog()},Modifier.semantics{contentDescription="确认重置"}){Text("重置",color=Danger)}
            else TextButton(onClick={s.closeDialog()}) {Text(if(s.dialog=="settings")"完成" else "知道了")}
        },dismissButton={
            if(s.dialog=="confirm-clear") TextButton(onClick={s.showDialog("settings")}){Text("取消")}
            else if(s.dialog=="confirm-reset") TextButton(onClick={s.closeDialog()}){Text("取消")}
        })
}

@Composable private fun SettingsBody(s:GameState) {
    Text("旋转按钮",fontSize=13.sp,fontWeight=FontWeight.Bold,color=Green)
    Text("决定棋盘下方显示哪些旋转按钮；再点一次拼板时也按这个方向转。双向时再点为顺时针。",fontSize=12.sp,lineHeight=18.sp,color=Brown)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf(TurnMode.BOTH to "双向",TurnMode.CLOCKWISE to "仅顺时针",TurnMode.COUNTERCLOCKWISE to "仅逆时针").forEach{(mode,label)->
            val on=s.turnMode==mode
            ChoiceChip(label,on){s.chooseTurnMode(mode)}
        }
    }
    HorizontalDivider(color=Line)
    Text("进度",fontSize=13.sp,fontWeight=FontWeight.Bold,color=Green)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp,Danger.copy(alpha=.4f),RoundedCornerShape(12.dp)).clickable(role=Role.Button){s.showDialog("confirm-clear")}.semantics{contentDescription="清除所有进度"}.padding(horizontal=12.dp,vertical=10.dp)){
        Text("清除所有进度",fontSize=13.sp,fontWeight=FontWeight.Bold,color=Danger)
    }
}

@Composable private fun EndpointLabel(finish:Boolean,letter:String,onClick:(()->Unit)?) {
    val color=if(finish)Color(0xffa95837)else Green;val shape=RoundedCornerShape(50)
    Row(Modifier.clip(shape).background(Panel,shape).border(1.dp,if(onClick!=null)color else Line,shape).then(if(onClick!=null)Modifier.clickable(onClick=onClick).semantics{contentDescription=if(finish)"选择终点"else"选择起点"}else Modifier).padding(horizontal=8.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){
        Icon(if(finish)Icons.Default.SportsScore else Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(12.dp),tint=color)
        Text("${if(finish)"终点"else"起点"} ${letter.ifEmpty{"?"}}",fontSize=10.sp,fontWeight=FontWeight.Bold,color=color,maxLines=1)
    }
}
