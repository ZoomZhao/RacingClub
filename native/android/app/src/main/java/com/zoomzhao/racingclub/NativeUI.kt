package com.zoomzhao.racingclub

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.*
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import kotlin.math.*

private fun nativeColor(s:String)=Color(android.graphics.Color.parseColor(s))
private fun clock(ms:Long)="%02d:%02d.%02d".format(ms/60000,ms/1000%60,ms%1000/10)

@Composable private fun Action(icon:ImageVector,label:String,enabled:Boolean=true,slot:Dp=30.dp,onClick:()->Unit) {
    Box(Modifier.size(slot).clip(RoundedCornerShape(8.dp)).clickable(enabled=enabled,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Icon(icon,contentDescription=label,tint=if(enabled)Brown else Brown.copy(alpha=.3f),modifier=Modifier.size(19.dp))
    }
}
@Composable private fun NumberBadge(id:Int,s:GameState,diameter:Dp=25.dp,active:Boolean=false,description:String?=null) {
    val shape=RoundedCornerShape(50)
    Box(Modifier.size(diameter).background(Color(numberFace(id)),shape).border(if(active)2.dp else 1.dp,if(active)Green else Color(0xfff9e8c8),shape).then(if(description!=null)Modifier.semantics{contentDescription=description}else Modifier),contentAlignment=Alignment.Center) {
        Text("$id",color=Color(numberInk(id)),fontSize=(diameter.value*.57f/LocalDensity.current.fontScale.coerceAtLeast(1f)).sp,fontWeight=FontWeight.Bold,maxLines=1)
    }
}
@Composable internal fun CourseSequence(s:GameState,l:Level,modifier:Modifier=Modifier,crossed:Int=0) {
    val density=LocalDensity.current
    BoxWithConstraints(modifier.background(Paper.copy(alpha=.96f),RoundedCornerShape(10.dp)).border(1.dp,Color(0xffe8d4b8),RoundedCornerShape(10.dp)).padding(horizontal=9.dp,vertical=5.dp).semantics{contentDescription="障碍顺序";stateDescription="${s.startGate.ifEmpty{"?"}} → ${l.sequence.joinToString(" → ")} → ${s.finishGate.ifEmpty{"?"}}"}) {
        val rows=courseRows(l.sequence.size+2,maxWidth.value,density.fontScale)
        val diameter=min(26f*density.fontScale.coerceAtLeast(1f),(maxWidth.value-24f-(rows.maxOf{it.size}-1)*10f)/rows.maxOf{it.size}).dp
        val rowWidth=(diameter.value*rows.maxOf{it.size}+10f*(rows.maxOf{it.size}-1)).dp
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
            rows.forEachIndexed{rowIndex,indices->
                val reversed=rowIndex%2==1
                Row(Modifier.width(rowWidth),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(reversed)Arrangement.End else Arrangement.Start) {
                    val display=if(reversed)indices.reversed()else indices
                    display.forEachIndexed{column,index->
                        if(column>0)Icon(if(reversed)Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.width(10.dp).height(9.dp),tint=Brown)
                        when(index){
                            0->CourseEndpoint(s.startGate.ifEmpty{"?"},false,diameter)
                            l.sequence.size+1->CourseEndpoint(s.finishGate.ifEmpty{"?"},true,diameter)
                            else->NumberBadge(l.sequence[index-1],s,diameter,s.running&&index-1==crossed,"目标 $index：${l.sequence[index-1]}号")
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
    Box(Modifier.size(diameter).background(color.copy(alpha=.10f),RoundedCornerShape(50)).border(1.dp,color,RoundedCornerShape(50)).semantics{contentDescription=if(finish)"目标终点"else"目标起点"},contentAlignment=Alignment.Center) {
        Text(letter,fontSize=(diameter.value*.56f/LocalDensity.current.fontScale.coerceAtLeast(1f)).sp,fontWeight=FontWeight.Bold,color=color,maxLines=1)
    }
}

@Composable fun RacingApp(s:GameState) {
    var tick by remember { mutableLongStateOf(0) }
    LaunchedEffect(Unit) { while(true) { tick++;delay(100) } }
    val progress=remember { Animatable(0f) }
    LaunchedEffect(s.level.id,s.placed,s.screen,s.dialog,s.chosenStart,s.chosenFinish) {
        if(s.screen=="game" && !s.running && s.dialog.isEmpty()) {
            s.trace()?.let { route ->
                delay(250);s.beginRun(route);progress.snapTo(0f)
                progress.animateTo(1f,tween(route.size*1350,easing=LinearEasing))
                s.running=false;s.screen="win";s.save()
            }
        }
    }
    BackHandler(s.screen!="home") { s.home() }
    Surface(color=Paper,modifier=Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.arena_backdrop),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
        val margin=maxWidth*.08f
        Column(Modifier.safeDrawingPadding().padding(horizontal=margin,vertical=8.dp)) {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Row(Modifier.heightIn(min=if(s.screen=="home")46.dp else 30.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    if(s.screen=="home") {
                        Text("♞ RacingClub",fontSize=24.sp,color=Green,fontWeight=FontWeight.Bold,fontFamily=FontFamily.Serif)
                        Spacer(Modifier.weight(1f))
                    } else {
                        Action(Icons.AutoMirrored.Filled.ArrowBack,"返回"){s.home()}
                        Spacer(Modifier.width(8.dp));Text("挑战 ${s.level.number}",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Green)
                        Spacer(Modifier.weight(1f));Icon(Icons.Default.Timer,null,Modifier.size(14.dp),tint=Brown)
                        Spacer(Modifier.width(4.dp));Text(clock(if(s.running||s.screen=="win")s.elapsed else s.time()+tick*0),fontSize=12.sp,color=Brown)
                        Action(Icons.Default.EmojiEvents,"本关排行榜"){s.showDialog("rank")}
                    }
                    Action(Icons.Default.HelpOutline,"玩法"){s.showDialog("help")}
                }
                if(s.screen!="home") {
                    val cursor=(progress.value*s.activeRoute.size).coerceAtMost((s.activeRoute.size-.0001f).coerceAtLeast(0f));val step=s.activeRoute.getOrNull(cursor.toInt())
                    val crossed=s.activeRoute.take(cursor.toInt()).count{it.jump!=null}+(if(step?.jump!=null&&cursor%1>(s.rules.road(step).jumpAt?:.5f).let{if(step.from==s.rules.road(step).from)it else 1-it})1 else 0)
                    CourseSequence(s,s.level,Modifier.fillMaxWidth(),crossed)
                }
            }
            when(s.screen) {
                "home" -> TrainingHome(s)
                "win" -> WinScreen(s)
                else -> Row(Modifier.fillMaxSize().padding(top=3.dp,bottom=3.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    NativeBoard(s,progress.value,Modifier.weight(1.55f).fillMaxHeight())
                    Workbench(s,Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        }
    }
    if(s.dialog.isNotEmpty()) ClubDialog(s)
}

@Composable private fun TrainingHome(s:GameState) {
    Row(Modifier.fillMaxSize().padding(top=6.dp,bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(.27f).fillMaxHeight().clip(RoundedCornerShape(16.dp))) {
            Image(painterResource(R.drawable.horse_arena),"马术训练场",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Column(Modifier.padding(14.dp)) {
                Text("今天，\n跃过新的\n挑战",fontSize=23.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold,color=Green)
                Spacer(Modifier.weight(1f))
                Text("${s.rules.levels.count{s.solved(it.id)}} / 80 已完成",fontSize=12.sp,color=Green,fontWeight=FontWeight.Bold,modifier=Modifier.background(Paper.copy(alpha=.9f),RoundedCornerShape(50)).padding(horizontal=10.dp,vertical=7.dp))
            }
        }
        Column(Modifier.weight(.73f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("马术训练场",fontSize=19.sp,color=Green,fontWeight=FontWeight.Bold)
                Spacer(Modifier.weight(1f));Text(if(s.chapter==4)"自行推导起终点" else "按顺序连接路线",fontSize=10.sp,color=Brown)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                repeat(5){index->
                    val label="${(index*16+1).toString().padStart(2,'0')}–${(index+1)*16}"
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if(index==s.chapter)Green else Color(0xfff2e4cc)).clickable{ s.chapter=index }.semantics{contentDescription="关卡 $label";selected=s.chapter==index}.padding(vertical=8.dp),contentAlignment=Alignment.Center){Text(label,fontSize=11.sp,color=if(index==s.chapter)Paper else Brown,fontWeight=FontWeight.Bold)}
                }
            }
            LazyVerticalGrid(columns=GridCells.Fixed(4),modifier=Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(7.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                items(s.rules.levels.filter{(it.id-1)/16==s.chapter},key={it.id}){l->
                    val done=s.solved(l.id);val best=s.best(l.id)
                    Card(onClick={s.start(l)},modifier=Modifier.fillMaxWidth().height(58.dp).border(1.dp,if(done)Green.copy(alpha=.45f)else Color(0xffe8d4b8),RoundedCornerShape(10.dp)).semantics{contentDescription="进入挑战 ${l.number}";stateDescription=if(done)"已完成" else if(s.inProgress(l.id))"进行中" else "未开始"},shape=RoundedCornerShape(10.dp),colors=CardDefaults.cardColors(containerColor=if(done)Color(0xffeef2e5)else Color(0xfffffaf0))) {
                        Column(Modifier.fillMaxSize().padding(horizontal=10.dp,vertical=6.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically){Text(l.number,fontSize=19.sp,lineHeight=22.sp,fontWeight=FontWeight.Bold,color=Green);Spacer(Modifier.weight(1f));if(done)Icon(Icons.Default.CheckCircle,"已完成",Modifier.size(13.dp),tint=Green)else if(s.inProgress(l.id))Icon(Icons.Default.Schedule,"进行中",Modifier.size(13.dp),tint=Brown)}
                            Text(if(best!=null)clock(best.millis)else "${l.requiredPieces.size} 拼板 · ${l.sequence.size} 跨",fontSize=9.sp,lineHeight=13.sp,color=Brown)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Workbench(s:GameState,modifier:Modifier) {
    val materials=boardMaterials()
    val shape=RoundedCornerShape(16.dp)
    BoxWithConstraints(modifier.shadow(3.dp,shape,ambientColor=Brown.copy(alpha=.12f),spotColor=Brown.copy(alpha=.12f)).background(Color(0xfffffbf2).copy(alpha=.94f),shape).border(1.dp,Color(0xffead6b8),shape).padding(10.dp)) {
    val toolSlot=min(32f,maxWidth.value/5).dp
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.height(32.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Action(Icons.Default.DeleteOutline,"移回",s.selected!=null&&!s.running,slot=toolSlot) {s.remove()}
            Action(Icons.Default.Undo,"撤销",!s.running,slot=toolSlot) {s.undo()}
            Action(Icons.Default.Lightbulb,"提示",!s.running,slot=toolSlot) {s.hint()}
            Action(Icons.Default.Refresh,"重置",!s.running,slot=toolSlot) {s.reset()}
            Action(Icons.Default.GridView,"全部 10 块",slot=toolSlot) {s.showDialog("inventory")}
        }
        HorizontalDivider(color=Color(0xffead7ba),modifier=Modifier.padding(top=3.dp,bottom=10.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val rowCount=(s.level.requiredPieces.size+4)/5
        val cardHeight=min(if(rowCount>1)110f else 160f,(maxHeight.value-7f*(rowCount-1))/rowCount).dp
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            s.level.requiredPieces.chunked(5).forEach { row ->
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    row.forEach { id ->
                        val active=s.selected==id
                        val border=if(active)Color(0xffb9794d) else Color.Transparent
                        Column(Modifier.weight(1f).height(cardHeight).background(if(active)Color(0xfffff3d9) else Color(0xfffcf3e2),RoundedCornerShape(9.dp)).border(1.dp,border,RoundedCornerShape(9.dp)).clickable(enabled=!s.running) {s.select(id)}.semantics { contentDescription="拼板 $id";selected=active;stateDescription="${if(active)"已选中"else"未选中"}，${if(s.placed.any{it.id==id})"已放置"else"待放置"}，${(if(active)s.rotation else s.placed.firstOrNull{it.id==id}?.r?:0)*90}度" }.padding(horizontal=4.dp,vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
                                NumberBadge(id,s,22.dp)
                                if(s.placed.any{it.id==id})Icon(Icons.Default.CheckCircle,null,Modifier.size(9.dp).align(Alignment.TopEnd),tint=Green)
                            }
                            Spacer(Modifier.height(3.dp))
                            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                                val r=if(active)s.rotation else 0;val(w,h)=s.rules.dimensions(r)
                                val u=min(size.width/w,size.height/h)
                                paintTile(s.rules,Placement(id,0,0,r),u,Offset((size.width-w*u)/2,(size.height-h*u)/2),materials,selected=active)
                            }
                        }
                    }
                    if(s.level.requiredPieces.size>5)repeat(5-row.size){Spacer(Modifier.weight(1f))}
                }
            }
        }
        }
        if(s.message.contains("越界")||s.message.contains("重叠"))Text(s.message,fontSize=11.sp,lineHeight=16.sp,color=Color(0xffa9442b),modifier=Modifier.padding(top=6.dp))
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(-1 to "逆时针旋转90度",1 to "顺时针旋转90度").forEach{(turn,label)->
                Button(onClick={s.rotate(turn)},enabled=s.selected!=null&&!s.running,modifier=Modifier.weight(1f).height(54.dp).semantics{contentDescription=label},shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=10.dp),colors=ButtonDefaults.buttonColors(containerColor=Green,contentColor=Color.White)){
                    Icon(if(turn<0)Icons.Default.RotateLeft else Icons.Default.RotateRight,null,Modifier.size(25.dp))
                    Spacer(Modifier.width(8.dp));Text("90°",fontSize=16.sp,fontWeight=FontWeight.Bold,maxLines=1)
                }
            }
        }
    }
    }
}

@Composable private fun WinScreen(s:GameState) {
    Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(26.dp)) {
        Image(painterResource(R.drawable.horse_arena),"马儿",Modifier.weight(1f).fillMaxHeight(),contentScale=ContentScale.Crop)
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("漂亮，顺利通关！",fontSize=27.sp,fontWeight=FontWeight.Bold,color=Green)
            Text(clock(s.elapsed),fontSize=31.sp,color=Brown)
            Text("${s.moves} 步 · ${if(s.hints==0)"独立完成" else "使用提示"}",fontSize=12.sp,color=Brown)
            Button(onClick={s.rules.levels.firstOrNull{it.id>s.level.id}?.let{s.start(it)}?:s.home()}) {Text(if(s.level.id<s.rules.levels.last().id)"挑战下一关" else "返回训练场");Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(17.dp))}
            TextButton(onClick={s.showDialog("rank")}) {Text("查看本关排行榜")}
        }
    }
}

@Composable private fun ClubDialog(s:GameState) {
    AlertDialog(onDismissRequest={s.closeDialog()},containerColor=Paper,
        title={Text(when(s.dialog){"rank"->"挑战 ${s.level.number} · 本机排行榜";"inventory"->"全部 10 块拼板";"start"->"选择起点";"finish"->"选择终点";else->if(s.dialog.startsWith("gate:"))"入口 ${s.dialog.substringAfter(":")}"else"一条路，刚刚好的顺序"},fontSize=20.sp,lineHeight=27.sp,color=Green,fontWeight=FontWeight.Bold)},
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
                "start", "finish" -> {
                    Text("按字母选择棋盘边界，可与另一端相同。",fontSize=12.sp,color=Brown)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){s.rules.gates.keys.forEach{letter->OutlinedButton(onClick={s.chooseEndpoint(letter)},modifier=Modifier.size(42.dp),contentPadding=PaddingValues(0.dp)){Text(letter)}}}
                }
                "inventory" -> s.rules.inventory.toSortedMap().entries.chunked(5).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        row.forEach { (id,_) -> Column(horizontalAlignment=Alignment.CenterHorizontally) { NumberBadge(id,s);Text(if(id in s.level.sequence)"本关" else "不用",fontSize=9.sp) } }
                    }
                }
                else -> if(s.dialog.startsWith("gate:")){
                    val letter=s.dialog.substringAfter(":")
                    Text("将 $letter 设为起点或终点；两端可以使用同一入口。",fontSize=12.sp,color=Brown)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={s.chooseEndpoint(letter,false)}){Text("设为起点")};OutlinedButton(onClick={s.chooseEndpoint(letter,true)}){Text("设为终点")}}
                }else Text("看上方障碍顺序。点右侧编号选择拼板，再点棋盘左上格放置。\n\n点已有拼板可重新选中，使用旋转图标转 90°；重叠或越界会保留原位。\n\n岔路须顺着道路平顺转向，不能在两个分支之间锐角转弯。7 号的两条路互不相通。经过无障碍支路不计一次跨障，同一障碍可能再次跨越。\n\n第 65–80 关还需点入口字母设置起点和终点。绿色箭头指向起点，棋格标记代表终点；两端可以相同。上方目标按箭头折行，始终完整展示。\n\n正确后自动停止计时，马儿沿完整路线跑到终点。",fontSize=13.sp,lineHeight=21.sp)
            }
        }},confirmButton={TextButton(onClick={s.closeDialog()}) {Text("知道了")}})
}

@Composable internal fun NativeBoard(s:GameState,progress:Float,modifier:Modifier) {
    val materials=boardMaterials();val sprites=gameSprites()
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center) {
        val unit=min(maxWidth.value/6.3f,(maxHeight.value-18f)/5.3f);val pad=unit*.35f
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.size((unit*6.3f).dp,(unit*5.3f).dp),contentAlignment=Alignment.Center) {
            Box(Modifier.size((unit*5.7f).dp,(unit*4.7f).dp)) {
                Canvas(Modifier.fillMaxSize().semantics {
                    contentDescription="棋盘"
                    stateDescription=s.placed.sortedBy{it.id}.joinToString("；"){"${it.id}号，第${it.y+1}行第${it.x+1}列，${it.r*90}度"}.ifEmpty{"空棋盘"}
                }.pointerInput(s.placed,s.selected,s.rotation,s.running) {
                    detectTapGestures{pos->if(!s.running){val u=size.width/5.7f;val x=floor(pos.x/u-.35f).toInt();val y=floor(pos.y/u-.35f).toInt()
                        if(x in 0..4&&y in 0..3){val hit=s.placed.firstOrNull{val(w,h)=s.rules.dimensions(it.r);x in it.x until it.x+w&&y in it.y until it.y+h};if(hit!=null)s.select(hit.id)else s.place(x,y)}
                    }}
                }){paintBoard(s,materials)}
                val route=s.activeRoute;val cursor=if(route.isEmpty())0f else(progress*route.size).coerceAtMost(route.size-.0001f);val step=route.getOrNull(cursor.toInt())
                val raw=if(s.running&&step!=null)s.rules.position(step,cursor%1)else s.rules.gates[s.startGate]?:Point(-1f,-1f)
                fun onRoute(at:Float):Point {val t=at.coerceIn(0f,route.size-.0001f);return s.rules.position(route[t.toInt()],t%1)}
                val direction=if(s.running&&route.isNotEmpty()){
                    val before=onRoute(cursor-.025f);val after=onRoute(cursor+.025f);val length=hypot(after.x-before.x,after.y-before.y).coerceAtLeast(.0001f);Point((after.x-before.x)/length,(after.y-before.y)/length)
                }else s.rules.gates[s.startGate]?.let{entryOutward(it).let{v->Point(-v.x,-v.y)}}?:Point(1f,0f)
                val heading=horseHeading(direction.x,direction.y)
                val body=Point(raw.x+direction.x*.36f,raw.y+direction.y*.36f)
                val road=step?.let{s.rules.road(it)};val along=if(step?.from==road?.from)cursor%1 else 1-cursor%1
                val jump=if(s.running&&step?.jump!=null)sin(PI.toFloat()*max(0f,1-abs(along-(road?.jumpAt?:.5f))*8))*10 else 0f
                if(s.startGate.isNotEmpty()){
                    Canvas(Modifier.offset((pad+body.x*unit-unit*.20f).dp,(pad+body.y*unit+unit*.11f).dp).size((unit*.40f).dp,(unit*.10f).dp)){drawOval(Brown.copy(alpha=if(jump>0).12f else .23f))}
                    Canvas(Modifier.offset((pad+body.x*unit-unit*.36f).dp,(pad+body.y*unit-unit*.34f-jump).dp).size((unit*.72f).dp,(unit*.68f).dp).semantics{contentDescription="马儿";stateDescription="朝向${heading.label}${if(s.running)"，跑动中"else""}"}){paintSprite(sprites.horses.getValue(heading),Rect(Offset.Zero,size))}
                }
                // Hit targets stay centered on the original A–F labels for every role.
                s.rules.gates.forEach{(name,pt)->val center=entryCenter(pt)
                    Box(Modifier.offset((unit*(center.x-.35f)).dp,(unit*(center.y-.35f)).dp).size((unit*.70f).dp).then(if(s.level.solveEndpoints&&!s.running)Modifier.clickable{s.showDialog("gate:$name")}else Modifier).semantics{
                        contentDescription="入口 $name";stateDescription=listOfNotNull(if(name==s.startGate)"起点"else null,if(name==s.finishGate)"终点"else null).joinToString("、").ifEmpty{"未指定"}
                    })
                }
            }
            }
            Row(Modifier.width((unit*5.7f).dp).height(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                EndpointLabel(false,s.startGate,if(s.level.solveEndpoints&&!s.running)({s.showDialog("start")})else null)
                Spacer(Modifier.weight(1f))
                EndpointLabel(true,s.finishGate,if(s.level.solveEndpoints&&!s.running)({s.showDialog("finish")})else null)
            }
        }
    }
}
@Composable private fun EndpointLabel(finish:Boolean,letter:String,onClick:(()->Unit)?) {
    Row(Modifier.then(if(onClick!=null)Modifier.clickable(onClick=onClick).semantics{contentDescription=if(finish)"选择终点"else"选择起点"}else Modifier),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){
        Icon(if(finish)Icons.Default.SportsScore else Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(12.dp),tint=if(finish)Brown else Green)
        Text("${if(finish)"终点"else"起点"} ${letter.ifEmpty{"?"}}",fontSize=9.sp,fontWeight=FontWeight.Bold,color=if(finish)Brown else Green,maxLines=1)
    }
}
