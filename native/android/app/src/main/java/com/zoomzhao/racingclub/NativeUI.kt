package com.zoomzhao.racingclub

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
    Text(d.label,fontSize=11.sp,fontWeight=FontWeight.Bold,color=tint,maxLines=1,modifier=Modifier.background(Well,RoundedCornerShape(50)).padding(horizontal=10.dp,vertical=4.dp).semantics{contentDescription="难度：${d.label} ${d.originalLabel}"})
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
            Spacer(Modifier.weight(1f))
            Text("马儿正在跑完全程…",fontSize=12.sp,color=Brown,maxLines=1,modifier=Modifier.padding(end=10.dp))
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
/** Switches between the perspective board and the flat top-down board. */
@Composable private fun ViewToggle(s:GameState,slot:Dp=40.dp) {
    val scale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(Modifier.height(slot).clubWell(12.dp).clip(RoundedCornerShape(12.dp)).clickable(role=Role.Switch){s.chooseBoardView(!s.view3d)}.semantics(mergeDescendants=true){contentDescription="切换视角";stateDescription=if(s.view3d)"立体 3D" else "俯视 2D"}.padding(3.dp),verticalAlignment=Alignment.CenterVertically) {
        listOf(false to Icons.Default.GridView,true to Icons.Default.ViewInAr).forEach{(three,icon)->
            val on=s.view3d==three
            Row(Modifier.fillMaxHeight().background(if(on)Green else Color.Transparent,RoundedCornerShape(9.dp)).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                Icon(icon,null,Modifier.size(16.dp),tint=if(on)Color.White else Brown)
                Text(if(three)"3D" else "2D",fontSize=(12f/scale).sp,fontWeight=FontWeight.Bold,color=if(on)Color.White else Brown,maxLines=1)
            }
        }
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
                    val trayReserve=if(tablet)380f else 300f
                    val boardShare=if(tablet).62f else .54f
                    val dock=64f;val gap=8f
                    // The tilted board is wider than tall, so in 3D it takes all the width the tray spares.
                    val spare=(maxWidth.value-trayReserve).coerceAtLeast(maxWidth.value*boardShare)
                    val boardWidth=(if(s.view3d)spare else min((maxHeight.value-dock-gap)*6.3f/5.3f,spare)).dp
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
        val flatLift by remember(drag,s){derivedStateOf{drag.piece?.takeIf{!(s.view3d&&drag.overBoard)}}}
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
@Composable private fun GameHeader(s:GameState) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp),verticalAlignment=Alignment.CenterVertically) {
            Action(Icons.AutoMirrored.Filled.ArrowBack,"返回"){s.home()}
            Spacer(Modifier.width(10.dp));Text("挑战 ${s.level.number}",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Green,maxLines=1)
            Spacer(Modifier.width(8.dp));DifficultyBadge(s.difficulty())
            Spacer(Modifier.weight(1f))
            Row(Modifier.height(30.dp).clubWell(50.dp).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                Icon(Icons.Default.Timer,null,Modifier.size(15.dp),tint=Brown)
                ClockText(s)
            }
        }
        Row(Modifier.fillMaxWidth().height(40.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(s.screen=="game")ViewToggle(s)
            Spacer(Modifier.weight(1f))
            Action(Icons.Default.EmojiEvents,"本关排行榜"){s.showDialog("rank")}
            Action(Icons.Default.Settings,"设置"){s.showDialog("settings")}
            Action(Icons.Default.HelpOutline,"玩法"){s.showDialog("help")}
        }
    }
}

/** Reads the run progress here, so only the board redraws each animation frame, not the whole screen. */
@Composable private fun RunBoard(s:GameState,modifier:Modifier,drag:BoardDrag) {
    val progress=s.runProgress
    if(s.view3d)NativeBoard3D(s,progress,modifier,drag) else NativeBoard(s,progress,modifier,drag)
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
            // Pick the column count that gives every card its largest square: a tile may lie
            // either way, so the shorter side decides how big it can be drawn.
            val pieces=s.level.requiredPieces;val gap=6f
            val columns=(1..5).maxBy{c->val rowsFor=(pieces.size+c-1)/c;min((maxWidth.value-gap*(c-1))/c,(maxHeight.value-gap*(rowsFor-1))/rowsFor)-c*.01f}
            val rows=pieces.chunked(columns)
            val cardHeight=min(120f,(maxHeight.value-gap*(rows.size-1))/rows.size).coerceAtLeast(26f).dp
            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
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
                                Canvas(Modifier.fillMaxSize().padding(top=3.dp,bottom=if(placed!=null)18.dp else 2.dp).graphicsLayer{rotationZ=swing-spin}){
                                    val r=turn;val(w,h)=s.rules.dimensions(r);val u=min(size.width/w,size.height/h)
                                    val origin=Offset((size.width-w*u)/2,(size.height-h*u)/2)
                                    val numberBounds=if(u>0f)Rect(-origin.x/u+.04f,-origin.y/u+.04f,(size.width-origin.x)/u-.04f,(size.height-origin.y)/u-.04f)else null
                                    paintTile(s.rules,Placement(id,0,0,r),u,origin,materials,selected=active,number=true,minimumNumberFont=11.dp.toPx(),numberBounds=numberBounds)
                                }
                                if(covered)Icon(Icons.Default.Layers,null,Modifier.size(13.dp).align(Alignment.TopEnd),tint=Color(0xffa96b1e))
                                else if(placed!=null)Icon(Icons.Default.CheckCircle,null,Modifier.size(14.dp).align(Alignment.TopEnd),tint=Green)
                                if(placed!=null)Text(if(covered)"被遮挡"else"已放置",fontSize=9.sp,lineHeight=11.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=1,modifier=Modifier.align(Alignment.BottomCenter).background(if(covered)Color(CoveredTag)else Green,RoundedCornerShape(50)).padding(horizontal=7.dp,vertical=2.dp))
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

@Composable private fun WinScreen(s:GameState) {
    Row(Modifier.fillMaxSize().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Image(painterResource(R.drawable.horse_arena),"马儿",Modifier.weight(1f).fillMaxHeight().shadow(5.dp,RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)),contentScale=ContentScale.Crop)
        Box(Modifier.weight(1f).fillMaxHeight().clubPanel(),contentAlignment=Alignment.Center){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(44.dp).background(Accent,RoundedCornerShape(50)),contentAlignment=Alignment.Center){Icon(Icons.Default.EmojiEvents,null,Modifier.size(24.dp),tint=Color.White)}
            Text("漂亮，顺利通关！",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Green)
            Text(clock(s.elapsed),fontSize=26.sp,fontFamily=FontFamily.Monospace,color=Brown)
            Text("${s.moves} 步 · ${if(s.hints==0)"独立完成" else "使用提示"}",fontSize=12.sp,color=Brown,modifier=Modifier.clubWell(50.dp).padding(horizontal=12.dp,vertical=5.dp))
            Button(onClick={s.rules.levels.firstOrNull{it.id>s.level.id}?.let{s.start(it)}?:s.home()},shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=22.dp,vertical=10.dp)) {Text(if(s.level.id<s.rules.levels.last().id)"挑战下一关" else "返回训练场",fontWeight=FontWeight.Bold);Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(17.dp))}
            TextButton(onClick={s.showDialog("rank")}) {Text("查看本关排行榜")}
        }
        }
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
                }else Text("看右上方障碍顺序。点右侧拼板选择，右侧卡片按放下时的方向显示；再点棋盘格放置。\n\n点一下选中，再点一次就旋转 90°，拼板在托盘里也能先转好再放。棋盘下方固定显示选中拼板的旋转和移回，以及撤销、提示、重置，不会遮住棋格。点空棋格或木框取消选择。\n\n可从右侧拖入棋盘，也可拖动棋盘上的拼板。棋盘内允许临时叠放，后放的在上面；点右侧编号可将下层置顶。边缘旋转会自动移入棋盘；消除重叠后才会判定通关。\n\n右上角「3D / 2D」切换立体视角和俯视棋盘，设置里也能更改。\n\n岔路须顺着道路平顺转向，不能在两个分支之间锐角转弯。7 号的两条路互不相通。经过无障碍支路不计一次跨障，同一障碍可能再次跨越。\n\n第 65–80 关还需点入口字母设置起点和终点。绿色箭头指向起点，棋格标记代表终点；两端可以相同。\n\n正确后自动停止计时，马儿沿完整路线跑到终点。",fontSize=13.sp,lineHeight=21.sp)
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
    Text("棋盘视角",fontSize=13.sp,fontWeight=FontWeight.Bold,color=Green)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf(true to "立体 3D",false to "俯视 2D").forEach{(threeD,label)->
            val on=s.view3d==threeD
            ChoiceChip(label,on){s.chooseBoardView(threeD)}
        }
    }
    HorizontalDivider(color=Line)
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

@Composable internal fun NativeBoard(s:GameState,progress:Float,modifier:Modifier,drag:BoardDrag=remember{BoardDrag()}) {
    val materials=boardMaterials();val sprites=gameSprites()
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center) {
        val unit=min(maxWidth.value/6.3f,maxHeight.value/5.3f);val pad=unit*.35f
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.size((unit*6.3f).dp,(unit*5.3f).dp),contentAlignment=Alignment.Center) {
            Box(Modifier.size((unit*5.7f).dp,(unit*4.7f).dp).onGloballyPositioned{drag.board=Rect(it.positionInRoot(),Size(it.size.width.toFloat(),it.size.height.toFloat()));drag.toFrame=null}) {
                Canvas(Modifier.fillMaxSize().semantics {
                    contentDescription="棋盘"
                    stateDescription=s.placed.joinToString("；"){"${it.id}号，第${it.y+1}行第${it.x+1}列，${it.r*90}度"+if(s.rules.coveredBy(it,s.placed).isNotEmpty())"，下层被遮挡"else""}.ifEmpty{"空棋盘"}
                }.pointerInput(s.running) {
                    val u=size.width/5.7f
                    boardGestures(s,drag,{it/u},{drag.board.topLeft+it})
                }.graphicsLayer()){paintBoard(s,materials)}
                // Drop highlight in its own layer: finger moves never re-record the board and tiles.
                Canvas(Modifier.fillMaxSize().graphicsLayer()){
                    drag.candidate()?.let{p->
                        RenderStats.dropHighlightDraws++
                        val(w,h)=s.rules.dimensions(p.r);val u=size.width/5.7f;val color=if(drag.problem(s).isNotEmpty())Color(0xffc04835)else if(s.placed.any{s.rules.overlaps(p,it)})Color(0xffa96b1e)else Green
                        clipRect(u*.35f,u*.35f,u*5.35f,u*4.35f){drawRoundRect(color.copy(alpha=.28f),Offset((p.x+.35f)*u,(p.y+.35f)*u),Size(w*u,h*u),CornerRadius(u*.06f));drawRoundRect(color,Offset((p.x+.35f)*u,(p.y+.35f)*u),Size(w*u,h*u),CornerRadius(u*.06f),style=Stroke(3.dp.toPx()))}
                    }
                }
                // Semantics-only nodes let TalkBack pick and act on each visible tile. They have no
                // pointer input, so every real touch still reaches the board Canvas below.
                if(!s.running)s.placed.filter{s.rules.stackMarker(it,s.placed)!=null}.forEach{p->
                    val(w,h)=s.rules.dimensions(p.r);val chosen=s.selected==p.id
                    Box(Modifier.offset((pad+p.x*unit).dp,(pad+p.y*unit).dp).size((w*unit).dp,(h*unit).dp).semantics{
                        contentDescription="棋盘上 ${p.id}号拼板";selected=chosen
                        stateDescription="${if(chosen)"已选中" else "未选中"}，第${p.y+1}行第${p.x+1}列，${p.r*90}度${if(s.rules.coveredBy(p,s.placed).isNotEmpty())"，部分被遮挡" else ""}"
                        onClick(if(chosen)"旋转" else "选中"){s.tapTile(p.id);true}
                        customActions=pieceActions(s,p.id)
                    })
                }
                horsePose(s,progress)?.let{pose->
                    val body=Point(pose.at.x+pose.direction.x*.36f,pose.at.y+pose.direction.y*.36f);val jump=pose.jump*10
                    Canvas(Modifier.offset((pad+body.x*unit-unit*.20f).dp,(pad+body.y*unit+unit*.11f).dp).size((unit*.40f).dp,(unit*.10f).dp)){drawOval(Brown.copy(alpha=if(jump>0).12f else .23f))}
                    Canvas(Modifier.offset((pad+body.x*unit-unit*.36f).dp,(pad+body.y*unit-unit*.34f-jump).dp).size((unit*.72f).dp,(unit*.68f).dp).semantics{contentDescription="马儿";stateDescription="朝向${pose.heading.label}，跑动中"}){paintSprite(sprites.horses.getValue(pose.heading),Rect(Offset.Zero,size))}
                }
                // Hit targets stay centered on the original A–F labels for every role.
                s.rules.gates.forEach{(name,pt)->val center=entryCenter(pt)
                    Box(Modifier.offset((unit*(center.x-.35f)).dp,(unit*(center.y-.35f)).dp).size((unit*.70f).dp).then(if(s.level.solveEndpoints&&!s.running)Modifier.clickable{s.showDialog("gate:$name")}else Modifier).semantics{
                        contentDescription="入口 $name";stateDescription=listOfNotNull(if(name==s.startGate)"起点"else null,if(name==s.finishGate)"终点"else null).joinToString("、").ifEmpty{"未指定"}
                    })
                }
            }
            }
        }
    }
}
@Composable private fun EndpointLabel(finish:Boolean,letter:String,onClick:(()->Unit)?) {
    val color=if(finish)Color(0xffa95837)else Green;val shape=RoundedCornerShape(50)
    Row(Modifier.clip(shape).background(Panel,shape).border(1.dp,if(onClick!=null)color else Line,shape).then(if(onClick!=null)Modifier.clickable(onClick=onClick).semantics{contentDescription=if(finish)"选择终点"else"选择起点"}else Modifier).padding(horizontal=8.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){
        Icon(if(finish)Icons.Default.SportsScore else Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(12.dp),tint=color)
        Text("${if(finish)"终点"else"起点"} ${letter.ifEmpty{"?"}}",fontSize=10.sp,fontWeight=FontWeight.Bold,color=color,maxLines=1)
    }
}
