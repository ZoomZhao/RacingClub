import SwiftUI

private func timeString(_ seconds:Double)->String {let ms=Int((seconds*1000).rounded());return String(format:"%02d:%02d.%02d",ms/60000,ms/1000%60,ms%1000/10)}
private func millisString(_ ms:Int)->String {String(format:"%02d:%02d.%02d",ms/60000,ms/1000%60,ms%1000/10)}
private func labelInk(_ d:Difficulty,on backgrounds:[String]=Design.plaqueFaces)->Color {ink(Design.legibleInk(d.color,on:backgrounds))}
@main struct RacingClubApp:App {
 @StateObject private var game=Game()
 @Environment(\.scenePhase) private var phase
 init(){Task.detached(priority:.userInitiated){ArtLibrary.shared.preload()}}
 var body:some Scene{WindowGroup{ClubView(game:game).preferredColorScheme(.light).onChange(of:phase){_,p in if p == .active{game.appDidBecomeActive()}else{game.appDidEnterBackground()}}}}
}

/// One touch owns the board at a time. Commits use the dragged piece, never the global selection,
/// and a second finger can neither change nor cancel the first gesture.
@MainActor final class NativeDrag:ObservableObject {
 @Published var board=CGRect.zero
 @Published private(set) var piece:Placement?
 @Published private(set) var fromControls=false
 @Published private(set) var pointer=CGPoint.zero
 private(set) var owner:UUID?
 private var grab=CGPoint.zero
 var liftOrigin:CGPoint {CGPoint(x:pointer.x-grab.x*unit,y:pointer.y-grab.y*unit)}
 var unit:CGFloat {board.width/5.7}
 func claim(_ token:UUID,_ game:Game)->Bool {
  if owner==token{return true}
  guard owner==nil,!game.running,game.screen=="game" else{return false}
  owner=token;game.beginDrag();return true
 }
 func owns(_ token:UUID)->Bool {owner==token}
 func begin(_ token:UUID,_ game:Game,_ p:Placement,_ at:CGPoint,_ anchor:CGPoint?=nil,controls:Bool=false){
  guard owner==token,piece==nil,!game.running,unit>0 else{return}
  game.select(p.id);fromControls=controls;piece=p;pointer=at
  let(w,h)=game.data.dimensions(p.r);grab=anchor ?? CGPoint(x:Double(w)/2,y:Double(h)/2)
 }
 func move(_ token:UUID,_ at:CGPoint){if owner==token,piece != nil{pointer=at}}
 var candidate:Placement? {guard let p=piece,unit>0 else{return nil};return Placement(id:p.id,x:Int(((pointer.x-board.minX)/unit-0.35-grab.x).rounded()),y:Int(((pointer.y-board.minY)/unit-0.35-grab.y).rounded()),r:p.r)}
 func problem(_ game:Game)->String {guard let p=candidate else{return ""};if !board.contains(pointer){return "拖入棋盘内放置"};return game.data.fits(p) ? "":"超出棋盘边界"}
 func feedback(_ game:Game)->String {let error=problem(game);if !error.isEmpty{return error};guard let p=candidate else{return ""};let ids=game.placed.filter{game.data.overlaps(p,$0)}.map(\.id).sorted();return ids.isEmpty ? "":"将叠放在 \(ids.map(String.init).joined(separator:"、")) 号上方"}
 func finish(_ token:UUID,_ game:Game){
  guard owner==token else{return}
  if let p=candidate{let error=problem(game);if error.isEmpty{game.drop(p)}else{game.message=error=="拖入棋盘内放置" ? "未放入棋盘，已保留原位":"\(error)，已保留原位"}}
  release(token,game)
 }
 func release(_ token:UUID,_ game:Game){guard owner==token else{return};piece=nil;fromControls=false;owner=nil;game.endDrag()}
 /// Screen change or backgrounding: drop the gesture without committing.
 func reset(_ game:Game){guard owner != nil else{return};piece=nil;fromControls=false;owner=nil;game.endDrag()}
}
private struct BoardFrameKey:PreferenceKey {static var defaultValue=CGRect.zero;static func reduce(value:inout CGRect,nextValue:()->CGRect){value=nextValue()}}
struct ClubView:View {
 @ObservedObject var game:Game
 @StateObject private var drag=NativeDrag()
 @Environment(\.scenePhase) private var phase
 var body:some View {
  GeometryReader{geo in
   VStack(spacing:5){
    if game.screen=="home"{home}else if game.screen=="win"{header;win}else{
     HStack(spacing:10){
      NativeBoard(game:game,drag:drag).frame(width:min((geo.size.height-16)*6.3/5.3,max(geo.size.width-(min(geo.size.width,geo.size.height)>=600 ? 360:274),(geo.size.width-24)*(min(geo.size.width,geo.size.height)>=600 ? 0.62:0.54))),height:geo.size.height-16)
      workbench.frame(maxWidth:.infinity,maxHeight:.infinity)
     }
    }
   }.padding(.horizontal,game.screen=="home" ? 18:12).padding(.vertical,8)
  }.coordinateSpace(name:"game").background{ZStack{paper;ArtImage(name:ArtLibrary.backdrop,fill:false)}.ignoresSafeArea()}
   .overlay(alignment:.topLeading){if let p=drag.piece{Canvas{context,_ in
    var c=context;c.opacity=0.86;let(w,h)=game.data.dimensions(p.r),u=drag.unit
    let origin=CGPoint(x:drag.liftOrigin.x,y:drag.liftOrigin.y-18)
    drawTile(c,game.data,Placement(id:p.id,x:0,y:0,r:p.r),u,origin,BoardTextures(c),selected:true,number:true)
    if !drag.problem(game).isEmpty{c.stroke(Path(roundedRect:CGRect(origin:origin,size:CGSize(width:CGFloat(w)*u,height:CGFloat(h)*u)),cornerRadius:6),with:.color(ink("#c04835")),lineWidth:2)}
   }.allowsHitTesting(false)}}
   .sheet(isPresented:Binding(get:{!game.dialog.isEmpty},set:{if !$0{game.close()}})){dialog}
   .onChange(of:game.screen){_,_ in drag.reset(game)}
   .onChange(of:phase){_,p in if p != .active{drag.reset(game)}}
 }
 private var header:some View {HStack(spacing:12){tool("chevron.left","返回"){game.home()};Text("挑战 \(game.level.number)").font(.headline);difficultyBadge(game.data.difficulties.first{$0.id==game.level.difficultyId}!);Spacer();Text(timeString(game.elapsed)).font(.system(size:13,design:.monospaced));tool("trophy","本关排行榜"){game.show("rank")};tool("gearshape","设置"){game.show("settings")};tool("questionmark.circle","玩法"){game.show("help")}}.foregroundStyle(forest).frame(height:38)}
 private func difficultyBadge(_ d:Difficulty)->some View {Text(d.label).font(.system(size:11,weight:.bold)).foregroundStyle(labelInk(d,on:[Design.paper])).padding(.horizontal,8).padding(.vertical,4).background(paper.opacity(0.96),in:Capsule()).overlay{Capsule().stroke(ink("#d5b486"),lineWidth:0.6)}.accessibilityLabel("难度：\(d.label) \(d.originalLabel)")}
 private var home:some View {
  let d=game.data.difficulties.first{$0.id==game.selectedDifficultyId}!
  let levels=game.data.levels.filter{$0.difficultyId==d.id}
  return GeometryReader{layout in HStack(spacing:18){
   VStack(spacing:8){Text("♞ RacingClub").font(.system(size:20,weight:.bold,design:.serif)).foregroundStyle(forest).frame(maxWidth:.infinity,minHeight:30,alignment:.leading)
   ZStack(alignment:.topLeading){
    ArtImage(name:ArtLibrary.arena).accessibilityHidden(true)
    VStack(alignment:.leading){
     Text("今天，\n跃过新的挑战").font(.system(size:25,weight:.bold)).foregroundStyle(forest)
     Spacer(minLength:0)
     Text("\(game.solvedLevels.count) / \(game.data.levels.count) 已完成").font(.system(size:12,weight:.bold)).foregroundStyle(forest).lineLimit(1).padding(.horizontal,10).padding(.vertical,7).background(paper.opacity(0.9),in:Capsule())
    }.padding(16)
   }.frame(maxWidth:.infinity,maxHeight:.infinity).clipped().clipShape(RoundedRectangle(cornerRadius:16))
   }.frame(width:layout.size.width*0.27)
   VStack(spacing:10){
    HStack(spacing:6){Text("马术训练场").font(.title2.bold());Spacer(minLength:0);Text("\(d.originalLabel) · \(levels.filter{game.solved($0.id)}.count) / \(levels.count) 已完成").font(.system(size:11,weight:.bold)).foregroundStyle(labelInk(d,on:[Design.paper])).lineLimit(1);tool("gearshape","设置"){game.show("settings")}}.frame(height:30)
    HStack(spacing:5){ForEach(Array(game.data.difficulties.enumerated()),id:\.element.id){index,item in
     let active=game.selectedDifficultyId==item.id
     Button{game.selectedDifficultyId=item.id}label:{ZStack(alignment:.bottom){
      HomePlaqueBackground(active:active ? ink(item.color):nil,top:8)
      VStack(spacing:1){Spacer().frame(height:20);Text(item.label).font(.system(size:12,weight:.bold));Text(String(format:"%02d–%02d",item.firstLevel,item.lastLevel)).font(.system(size:9))}.padding(.bottom,5).foregroundStyle(active ? Color.white:labelInk(item))
      Text(["I","II","III","IV","V"][index]).font(.system(size:11,weight:.bold,design:.serif)).foregroundStyle(ink("#ffedbc")).frame(width:20,height:20).background(ink(item.color),in:Circle()).overlay{Circle().stroke(ink("#d2a157"),lineWidth:2)}.frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.top)
     }.frame(height:50)}.buttonStyle(HomePlaquePress()).accessibilityLabel("难度 \(item.label) \(item.originalLabel)").accessibilityValue(active ? "已选择":"未选择")
    }}
    ScrollView{LazyVGrid(columns:Array(repeating:GridItem(.flexible(),spacing:7),count:4),spacing:6){ForEach(levels){level in
     let done=game.solved(level.id),ongoing=game.inProgress(level.id),best=game.bestMillis[level.id]
     Button{game.start(level)}label:{VStack(alignment:.leading,spacing:3){
      HStack(spacing:3){
       Text(level.number).font(.system(size:25,weight:.bold,design:.serif)).foregroundStyle(done ? ink("#fff6df"):forest)
       Spacer()
       Image(systemName:done ? "checkmark.circle.fill":ongoing ? "clock.fill":"chevron.right").font(.system(size:done ? 19:12,weight:.bold)).foregroundStyle(done ? ink("#ffd783"):ongoing ? ink(Design.inProgressAmber):saddle)
      }
      if done{HStack(spacing:4){Text("已完成").font(.system(size:10,weight:.bold)).foregroundStyle(ink("#ffe0a0"));Spacer(minLength:0);if let best{Text(millisString(best)).font(.system(size:10)).foregroundStyle(ink("#f8efdb")).lineLimit(1)}}}
      else if ongoing{Text("进行中 · \(level.requiredPieces.count) 块 · \(level.sequence.count) 跨").font(.system(size:10,weight:.bold)).foregroundStyle(ink(Design.inProgressAmber)).lineLimit(1).minimumScaleFactor(0.8)}
      else{Text("\(d.label) · \(level.requiredPieces.count) 块 · \(level.sequence.count) 跨").font(.system(size:10)).foregroundStyle(saddle).lineLimit(1).minimumScaleFactor(0.8)}
     }.padding(.horizontal,9).padding(.vertical,7).frame(maxWidth:.infinity,minHeight:max(57,(layout.size.height-116)/4)).background{HomePlaqueBackground(active:done ? forest:nil)}}.buttonStyle(HomePlaquePress()).accessibilityLabel("进入挑战 \(level.number)").accessibilityValue(done ? "已完成":ongoing ? "进行中":"未开始")
    }}}
   }.foregroundStyle(forest).frame(maxWidth:.infinity)
  }}
 }
 private var workbench:some View {
  VStack(spacing:4){
   HStack(spacing:5){tool("chevron.left","返回"){game.home()};Text("挑战 \(game.level.number)").font(.system(size:16,weight:.bold)).lineLimit(1);Spacer(minLength:0);difficultyBadge(game.data.difficulties.first{$0.id==game.level.difficultyId}!)}.frame(height:28)
   HStack(spacing:4){Image(systemName:"timer");ClockText(game:game);Spacer(minLength:0);tool("trophy","本关排行榜"){game.show("rank")};tool("gearshape","设置"){game.show("settings")};tool("questionmark.circle","玩法"){game.show("help")}}.font(.system(size:12)).frame(height:26)
   NativeCourseSequence(game:game,level:game.level)
   HStack{
    Button{if game.level.solveEndpoints{game.show("start")}}label:{Label("起点 \(game.startGate.isEmpty ? "?":game.startGate)",systemImage:"arrow.right")}.disabled(!game.level.solveEndpoints || game.running)
    Spacer(minLength:0)
    Button{if game.level.solveEndpoints{game.show("finish")}}label:{Label("终点 \(game.finishGate.isEmpty ? "?":game.finishGate)",systemImage:"flag.checkered")}.disabled(!game.level.solveEndpoints || game.running)
   }.font(.system(size:11,weight:.bold)).foregroundStyle(forest).padding(.vertical,2)
   NativeTileRack(game:game,drag:drag)
   let coveredCount=game.placed.filter{!game.data.coveredBy($0,game.placed).isEmpty}.count
   let error=drag.piece != nil ? drag.problem(game):game.message
   let feedback=drag.piece != nil ? drag.feedback(game):error.isEmpty && coveredCount>0 ? "\(coveredCount) 块被遮挡 · 点右侧编号置顶":error
   HStack(spacing:4){if !feedback.isEmpty && error.isEmpty{Image(systemName:"square.3.layers.3d")};Text(feedback)}.font(.system(size:11)).foregroundStyle(ink(error.isEmpty ? "#925b1c":"#a9442b")).lineLimit(2).frame(maxWidth:.infinity,minHeight:30,maxHeight:30,alignment:.leading).accessibilityLabel("放置提示").accessibilityValue(feedback)
   Divider()
   if game.running{Button{game.finishRun()}label:{Label("跳过动画",systemImage:"forward.end.fill").font(.system(size:13,weight:.bold)).foregroundStyle(forest).frame(maxWidth:.infinity,minHeight:42)}.buttonStyle(GameControlStyle()).accessibilityLabel("跳过动画").frame(height:48)}else{HStack(spacing:0){
    tool("arrow.uturn.backward","撤销"){game.undo()}.frame(maxWidth:.infinity)
    tool("lightbulb","提示"){game.hint()}.frame(maxWidth:.infinity)
    tool("arrow.clockwise","重置"){game.show("confirm-reset")}.frame(maxWidth:.infinity)
   }.frame(height:48)}
  }.foregroundStyle(forest).padding(.vertical,2)
 }
 private var win:some View {HStack(spacing:25){ArtImage(name:ArtLibrary.arena).frame(maxWidth:.infinity,maxHeight:.infinity).clipped().clipShape(RoundedRectangle(cornerRadius:18)).accessibilityHidden(true);VStack(spacing:14){Text("漂亮，顺利通关！").font(.title.bold());Text(timeString(game.elapsed)).font(.system(size:32,design:.serif));Text("\(game.moves) 步 · \(game.hints==0 ? "独立完成":"使用提示")").font(.system(size:13));Button(game.level.id<game.data.levels.last!.id ? "挑战下一关":"返回训练场"){if let next=game.data.levels.first(where:{$0.id>game.level.id}){game.start(next)}else{game.home()}}.buttonStyle(.borderedProminent).tint(forest);Button("查看本关排行榜"){game.show("rank")}}.frame(maxWidth:.infinity).foregroundStyle(forest)}}
 private var dialog:some View {NavigationStack{ScrollView{VStack(alignment:.leading,spacing:16){if game.dialog=="rank"{Text("挑战 \(game.level.number) · 本机排行榜").font(.title2.bold());let records=game.repository.list(level:game.level.id);if records.isEmpty{Text("还没有成绩，完成一次训练就会记录。")};ForEach(Array(records.enumerated()),id:\.element.id){i,r in HStack{Text("\(i+1)");Text(millisString(r.millis)).monospacedDigit();Spacer();Text("\(r.moves) 步");Text(r.hints==0 ? "独立完成":"使用提示").font(.caption)}};Text("记录保存在此设备；线上服务尚未接入。").font(.caption)}else if game.dialog=="start" || game.dialog=="finish"{Text(game.dialog=="start" ? "选择起点":"选择终点").font(.title2.bold());Text("按字母选择棋盘边界，可与另一端相同。").font(.callout);HStack{ForEach(game.data.gates.keys.sorted(),id:\.self){letter in Button(letter){game.choose(letter)}.buttonStyle(.bordered)}}}else if game.dialog.hasPrefix("gate:"){let letter=String(game.dialog.dropFirst(5));Text("入口 \(letter)").font(.title2.bold());Text("设置起终点；两端可以使用同一入口。");HStack{Button("设为起点"){game.choose(letter,finish:false)}.buttonStyle(.borderedProminent);Button("设为终点"){game.choose(letter,finish:true)}.buttonStyle(.bordered)}}else if game.dialog=="settings"{Text("设置").font(.title2.bold());Text("旋转按钮").font(.headline);Text("只影响棋盘上选中拼板旁边显示哪些按钮。").font(.callout);HStack{ForEach([TurnMode.both,.cw,.ccw],id:\.self){mode in Button(turnModeLabel(mode)){game.setTurnMode(mode)}.buttonStyle(.bordered).tint(game.turnMode==mode ? forest:saddle)}};Divider();Text("进度").font(.headline);Button("清除所有进度"){game.show("confirm-clear")}.buttonStyle(.bordered).tint(ink("#a9442b"))}else if game.dialog=="confirm-clear"{Text("清除所有进度？").font(.title2.bold());Text("会清除全部分关进度、未完成的摆放和完成标记。本机最佳成绩会保留。此操作不能撤销。");HStack{Button("取消"){game.show("settings")}.buttonStyle(.bordered);Button("清除"){game.clearAllProgress();game.close()}.buttonStyle(.borderedProminent).tint(ink("#a9442b"))}}else if game.dialog=="confirm-reset"{Text("重置本关？").font(.title2.bold());Text("清空本关已摆放的拼板和起终点，重新开始这一关。本机最佳成绩会保留，也可以用撤销恢复。");HStack{Button("取消"){game.close()}.buttonStyle(.bordered);Button("重置"){game.reset();game.close()}.buttonStyle(.borderedProminent).tint(ink("#a9442b"))}}else{Text("一条路，刚刚好的顺序").font(.title2.bold());Text("看右上方障碍顺序。点右侧编号，再点棋盘空格放置。\n\n从右侧拖入棋盘，或拖动棋盘上的拼板。点棋盘上的拼板后，附近按钮可双向旋转或移回拼板区；再点一次该拼板，或点空格、木框，即可取消选中。棋盘内允许临时叠放，琥珀标记提示下层拼板。点右侧编号可将下层置顶。边缘旋转会自动移入棋盘；消除重叠后才会判定通关。\n\n正确后自动停止计时，马儿沿路跑到终点。岔路须平顺转弯，无障碍支路不计跨障。同一障碍可多次跨越。第65关起，点入口字母设置起终点；两端可相同。绿色箭头指向起点，棋格标记代表终点。右上顺序按箭头折行，全部直接展示。")}}.padding(25)}.foregroundStyle(forest).background(paper).toolbar{ToolbarItem(placement:.confirmationAction){Button("完成"){game.close()}}}}}
 private func tool(_ icon:String,_ label:String,_ action:@escaping()->Void)->some View {Button(action:action){Image(systemName:icon).font(.system(size:19)).foregroundStyle(label=="提示" ? ink("#9b641b"):forest).frame(width:34,height:34)}.buttonStyle(GameControlStyle()).accessibilityLabel(label)}
}
/// Only this text refreshes while the clock runs; the rest of the page is state driven.
private struct ClockText:View {
 @ObservedObject var game:Game
 var body:some View {TimelineView(.animation(minimumInterval:0.1,paused:!game.clockRunning)){_ in Text(timeString(game.time)).monospacedDigit()}.accessibilityLabel("用时")}
}
private struct GameControlStyle:ButtonStyle {
 func makeBody(configuration:Configuration)->some View {configuration.label.background{GameControlFace(pressed:configuration.isPressed)}.offset(y:configuration.isPressed ? 1:0)}
}
private struct GameControlFace:View {
 var pressed=false
 var body:some View {Canvas{c,size in
  let rect=CGRect(x:1,y:1,width:size.width-2,height:size.height-3),face=rect.insetBy(dx:1.2,dy:1.2)
  c.fill(Path(roundedRect:rect.offsetBy(dx:0,dy:2),cornerRadius:7),with:.color(ink("#8d5b31").opacity(0.22)))
  c.fill(Path(roundedRect:rect,cornerRadius:7),with:.linearGradient(Gradient(colors:[ink("#c28d51"),ink("#936032")]),startPoint:.zero,endPoint:CGPoint(x:0,y:size.height)))
  c.fill(Path(roundedRect:face,cornerRadius:6),with:.linearGradient(Gradient(colors:pressed ? [ink("#f0dfbd"),ink("#fff7e5")]:[ink("#fffdf4"),ink("#f6e7ca")]),startPoint:.zero,endPoint:CGPoint(x:0,y:size.height)))
  c.stroke(Path(roundedRect:face.insetBy(dx:1,dy:1),cornerRadius:5),with:.color(.white.opacity(0.85)),lineWidth:0.6)
 }}
}
struct NativeActionAnchor {
 let left:CGFloat;let top:CGFloat;let tip:CGFloat;let below:Bool
 var width:CGFloat=140;var height:CGFloat=52;var button:CGFloat=44;var vertical=false;var inside=false
}
/// Prefer free space beside the tile; a crowded board keeps controls on their owner, never on a neighbor.
private func turnModeLabel(_ mode:TurnMode)->String {
 switch mode {
 case .both: return "双向"
 case .cw: return "仅顺时针"
 case .ccw: return "仅逆时针"
 }
}
func nativeActionAnchor(_ p:Placement,_ u:CGFloat,_ all:[Placement],count:Int=3)->NativeActionAnchor {
 let n=min(3,max(1,count))
 let w=p.r%2==0 ? 2:1,h=p.r%2==0 ? 1:2
 let width=n==3 ? 140:CGFloat(44*n+4*(n-1))
 let tile=CGRect(x:(CGFloat(p.x)+0.35)*u,y:(CGFloat(p.y)+0.35)*u,width:CGFloat(w)*u,height:CGFloat(h)*u)
 let low=0.35*u+4,high=max(low,5.35*u-width-4)
 let xs=[tile.midX-width/2,tile.minX,tile.maxX-width].map{min(high,max(low,$0))}
 for(top,below)in [(tile.minY-60,false),(tile.maxY+8,true)]{for left in xs{
  let box=CGRect(x:left,y:top,width:width,height:52)
  if top<4 || box.maxY>4.7*u-4 || box.intersects(tile){continue}
  let blocked=all.contains{o in
   if o.id==p.id{return false};let ow=o.r%2==0 ? 2:1,oh=o.r%2==0 ? 1:2
   return box.insetBy(dx:-2,dy:-2).intersects(CGRect(x:(CGFloat(o.x)+0.35)*u,y:(CGFloat(o.y)+0.35)*u,width:CGFloat(ow)*u,height:CGFloat(oh)*u))
  }
  if !blocked{return NativeActionAnchor(left:left,top:top,tip:min(width-10,max(10,tile.midX-left)),below:below,width:width)}
 }}
 let span=CGFloat(4*(n-1)),button=max(24,min(44,(2*u-8-span)/CGFloat(n),0.64*u-5)),vertical=h>w
 let barWidth=vertical ? button:button*CGFloat(n)+span,barHeight=vertical ? button*CGFloat(n)+span:button
 return NativeActionAnchor(left:vertical ? tile.maxX-3-button:tile.midX-barWidth/2,top:vertical ? tile.midY-barHeight/2:tile.maxY-3-button,tip:0,below:false,width:barWidth,height:barHeight,button:button,vertical:vertical,inside:true)
}
private struct NativeBoardPieceActions:View {
 @GestureState private var touching=false
 @State private var token=UUID()
 @ObservedObject var game:Game
 @ObservedObject var drag:NativeDrag
 let piece:Placement
 let unit:CGFloat
 var body:some View {
  let actions=game.turnMode.actions
  let a=nativeActionAnchor(piece,unit,game.placed,count:actions.count),edge:CGFloat=a.below ? 8:44,peak:CGFloat=a.below ? 0:52
  return ZStack(alignment:.topLeading){
   if !a.inside{Path{p in p.move(to:CGPoint(x:a.tip-6,y:edge));p.addLine(to:CGPoint(x:a.tip,y:peak));p.addLine(to:CGPoint(x:a.tip+6,y:edge))}.stroke(ink("#bd8d56"),lineWidth:1)}
   Group{if a.vertical{VStack(spacing:4){buttons(a.button,actions)}}else{HStack(spacing:4){buttons(a.button,actions)}}}.offset(y:a.below ? 8:0)
  }.frame(width:a.width,height:a.height,alignment:.topLeading).accessibilityElement(children:.contain).accessibilityLabel("拼板操作").accessibilityValue("\(piece.id)号").offset(x:a.left,y:a.top).opacity(drag.piece==nil ? 1:0).onChange(of:touching){_,active in if !active{drag.release(token,game)}}
 }
 private func buttons(_ size:CGFloat,_ actions:[Int])->some View {ForEach(actions,id:\.self){action in
  Button{guard drag.owner==nil else{return};if action==0{game.remove()}else{game.rotate(action)}}label:{
   Group{if action==0{Image(systemName:"square.and.arrow.up").font(.system(size:23,weight:.semibold))}else{VStack(spacing:0){Image(systemName:action<0 ? "arrow.counterclockwise":"arrow.clockwise").font(.system(size:19,weight:.semibold));Text("90°").font(.system(size:10,weight:.bold))}}}.foregroundStyle(forest).frame(width:size,height:size)
  }.buttonStyle(GameControlStyle()).accessibilityLabel(action==0 ? "移回":action<0 ? "逆时针旋转90度":"顺时针旋转90度").highPriorityGesture(DragGesture(minimumDistance:12,coordinateSpace:.named("game")).updating($touching){_,state,_ in state=true}.onChanged{value in
   guard drag.claim(token,game) else{return}
   if drag.piece==nil{let local=CGPoint(x:(value.startLocation.x-drag.board.minX)/drag.unit-0.35-CGFloat(piece.x),y:(value.startLocation.y-drag.board.minY)/drag.unit-0.35-CGFloat(piece.y));drag.begin(token,game,piece,value.startLocation,local,controls:true)}
   drag.move(token,value.location)
  }.onEnded{_ in drag.finish(token,game)})
 }}
}
private struct NativeTileRack:View {
 @ObservedObject var game:Game
 /// Used only inside gestures; not observed, so pointer moves do not re-render the rack.
 let drag:NativeDrag
 var body:some View {GeometryReader{geo in
  let ids=game.level.requiredPieces,rows=stride(from:0,to:ids.count,by:5).map{Array(ids[$0..<min($0+5,ids.count)])}
  let height=min(106,max(26,(geo.size.height-CGFloat(rows.count-1)*6)/CGFloat(rows.count)))
  VStack(spacing:6){ForEach(rows.indices,id:\.self){index in
   let row=rows[index],columns=rows.count>1 ? 5:row.count,cardWidth=(geo.size.width-CGFloat(columns-1)*5)/CGFloat(columns)
   HStack(spacing:5){ForEach(row,id:\.self){id in RackCard(game:game,drag:drag,id:id).frame(width:cardWidth,height:height)}
    if row.count<columns{ForEach(0..<(columns-row.count),id:\.self){_ in Color.clear.frame(width:cardWidth,height:height)}}}
  }}
 }}
}
/// Press selects at once; past a 12pt slop the card becomes a drag. A placed piece keeps its rotation,
/// an unplaced one always enters at 0° — the vertical preview is display only.
private struct RackCard:View {
 @GestureState private var touching=false
 @State private var token=UUID()
 @State private var pressing=false
 @State private var ignored=false
 @ObservedObject var game:Game
 let drag:NativeDrag
 let id:Int
 var body:some View {
  let placed=game.placed.first{$0.id==id},covered=placed.map{!game.data.coveredBy($0,game.placed).isEmpty} ?? false,active=game.selected==id
  ZStack(alignment:.topLeading){
   TilePreview(data:game.data,id:id,selected:active).equatable().padding(.top,3).padding(.bottom,placed != nil ? 18:2)
   if placed != nil{Image(systemName:covered ? "square.3.layers.3d":"checkmark.circle.fill").font(.system(size:12)).foregroundStyle(covered ? ink(Design.coveredAmber):forest).frame(maxWidth:.infinity,alignment:.trailing)
    Text(covered ? "被遮挡":"已放置").font(.system(size:9,weight:.bold)).foregroundStyle(.white).lineLimit(1).padding(.horizontal,3).padding(.vertical,2).background(covered ? ink(Design.coveredAmber):forest,in:RoundedRectangle(cornerRadius:3)).frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.bottom)
   }
  }.padding(3).background(active ? ink("#fff1d2"):placed != nil ? ink("#e6eee3"):ink("#fcf3e2"),in:RoundedRectangle(cornerRadius:8)).overlay{RoundedRectangle(cornerRadius:8).stroke(active || placed != nil ? forest:ink("#ebd9bc"),lineWidth:active ? 2:1)}
  .contentShape(Rectangle())
  .gesture(DragGesture(minimumDistance:0,coordinateSpace:.named("game")).updating($touching){_,state,_ in state=true}.onChanged{value in
   if !pressing{pressing=true;ignored = !drag.claim(token,game);if !ignored{game.select(id)}}
   guard !ignored else{return}
   if drag.piece==nil,hypot(value.translation.width,value.translation.height)>12{drag.begin(token,game,game.placed.first{$0.id==id} ?? Placement(id:id,x:0,y:0,r:0),value.startLocation)}
   drag.move(token,value.location)
  }.onEnded{_ in if !ignored{if drag.piece != nil{drag.finish(token,game)}else{drag.release(token,game)}};pressing=false;ignored=false})
  .onChange(of:touching){_,active in if !active,pressing{drag.release(token,game);pressing=false;ignored=false}}
  .accessibilityElement(children:.ignore).accessibilityLabel("拼板 \(id)").accessibilityValue("\(active ? "已选中，":"")\(placed == nil ? "待放置":covered ? "下层被遮挡，点按置顶":"已放置，\(placed!.r*90)度")").accessibilityAddTraits(active ? [.isButton,.isSelected]:.isButton).accessibilityAction{game.select(id)}
 }
}
private struct HomePlaquePress:ButtonStyle {
 func makeBody(configuration:Configuration)->some View {configuration.label.scaleEffect(configuration.isPressed ? 0.98:1).offset(y:configuration.isPressed ? 1:0).animation(.easeOut(duration:0.12),value:configuration.isPressed)}
}
private struct HomePlaqueBackground:View {
 var active:Color?=nil
 var top:CGFloat=0
 var body:some View {Canvas{context,size in
  let frame=CGRect(x:1,y:top+1,width:max(1,size.width-2),height:max(1,size.height-top-3))
  context.fill(Path(roundedRect:frame.offsetBy(dx:0,dy:2),cornerRadius:8),with:.color(ink("#704928").opacity(0.19)))
  context.fill(Path(roundedRect:frame,cornerRadius:8),with:.linearGradient(Gradient(colors:[ink("#f2cd93"),ink("#b57d42"),ink("#82512d")]),startPoint:CGPoint(x:0,y:frame.minY),endPoint:CGPoint(x:0,y:frame.maxY)))
  let face=frame.insetBy(dx:2.5,dy:2.5)
  context.fill(Path(roundedRect:face,cornerRadius:6),with:.linearGradient(Gradient(colors:active.map{[$0,$0.opacity(0.9)]} ?? [ink(Design.plaqueFaces[0]),ink(Design.plaqueFaces[1])]),startPoint:CGPoint(x:0,y:face.minY),endPoint:CGPoint(x:0,y:face.maxY)))
  context.stroke(Path(roundedRect:frame.insetBy(dx:1,dy:1),cornerRadius:7),with:.color(ink("#f7ddb0")),lineWidth:1)
  context.stroke(Path(roundedRect:face.insetBy(dx:1,dy:1),cornerRadius:5),with:.color(Color.white.opacity(0.7)),lineWidth:0.5)
  if top==0{for x in [CGFloat(5),size.width-5]{for y in [CGFloat(5),size.height-7]{context.fill(Path(ellipseIn:CGRect(x:x-1.5,y:y-1.5,width:3,height:3)),with:.color(ink("#bf8f45")))}}}
 }}
}
/// Rack preview is drawn vertically for compact cards; it never changes the placement rotation.
struct TilePreview:View,Equatable {
 let data:GameData;let id:Int;var selected=false
 static func ==(a:TilePreview,b:TilePreview)->Bool {a.id==b.id && a.selected==b.selected}
 var body:some View{Canvas{c,size in
  let r=1;let(w,h)=data.dimensions(r);let u=min(size.width/CGFloat(w),size.height/CGFloat(h))
  guard u>0 else{return}
  let origin=CGPoint(x:(size.width-CGFloat(w)*u)/2,y:(size.height-CGFloat(h)*u)/2)
  let bounds=CGRect(x:-origin.x/u+0.04,y:-origin.y/u+0.04,width:size.width/u-0.08,height:size.height/u-0.08)
  drawTile(c,data,Placement(id:id,x:0,y:0,r:r),u,origin,BoardTextures(c),selected:selected,number:true,minimumNumberFont:11,numberBounds:bounds)
 }.accessibilityHidden(true)}
}
private struct NativeCourseSequence:View {
 @ObservedObject var game:Game
 let level:Level
 private let diameter:CGFloat=24
 var body:some View {
  TimelineView(.animation(minimumInterval:1/30,paused:!game.running)){_ in
   let feedback=game.data.crossingFeedback(game.route,game.runProgress())
   ViewThatFits(in:.horizontal){
    goalRows(level.sequence.count>8 ? 2:1,feedback)
    goalRows(level.sequence.count>8 ? 3:2,feedback)
    goalRows(4,feedback)
    goalRows(5,feedback)
   }
  }.frame(maxWidth:.infinity).padding(.vertical,8).padding(.horizontal,9).background(paper.opacity(0.96),in:RoundedRectangle(cornerRadius:10)).overlay{RoundedRectangle(cornerRadius:10).stroke(ink("#e8d4b8"),lineWidth:1)}.accessibilityElement(children:.contain).accessibilityLabel("障碍顺序")
 }
 private func rows(_ count:Int)->[[Int]] {
  let n=level.sequence.count+2,r=min(count,n)
  var next=0
  return(0..<r).map{row in let size=n/r+(row<n%r ? 1:0);defer{next+=size};return Array(next..<next+size)}
 }
 private func goalRows(_ count:Int,_ feedback:(crossed:Int,pulse:Double))->some View {
  let groups=rows(count),columns=groups.map(\.count).max() ?? 1
  let width=CGFloat(columns)*diameter+CGFloat(columns-1)*8
  return VStack(spacing:0){
   ForEach(groups.indices,id:\.self){row in
    let reverse=row%2==1,indices=reverse ? Array(groups[row].reversed()):groups[row]
    HStack(spacing:0){ForEach(Array(indices.enumerated()),id:\.element){column,index in
     if column>0{Image(systemName:reverse ? "arrow.left":"arrow.right").font(.system(size:9,weight:.semibold)).foregroundStyle(saddle).frame(width:8)}
     goal(index,feedback)
    }}.frame(width:width,alignment:reverse ? .trailing:.leading)
    if row<groups.count-1{HStack{if !reverse{Spacer(minLength:0)};Image(systemName:"arrow.down").font(.system(size:8,weight:.semibold)).foregroundStyle(saddle);if reverse{Spacer(minLength:0)}}.padding(.horizontal,max(0,diameter/2-4)).frame(width:width,height:8)}
   }
  }
 }
 private func goal(_ index:Int,_ feedback:(crossed:Int,pulse:Double))->some View {
  Group{
   if index==0 || index==level.sequence.count+1 {
    let finish=index>0
    let value=game.screen != "home" && level.id==game.level.id ? (finish ? game.finishGate:game.startGate):(finish ? level.finish:level.start)
    let color=finish ? ink("#a95837"):forest
    Text(value.isEmpty ? "?":value).font(.system(size:diameter*0.56,weight:.bold)).foregroundStyle(color).frame(width:diameter,height:diameter).background(color.opacity(0.1),in:Circle()).overlay{Circle().stroke(color,lineWidth:1)}.accessibilityLabel(finish ? "目标终点":"目标起点").accessibilityValue(value.isEmpty ? "未指定":value)
   }else{
    let id=level.sequence[index-1]
    let passed=game.running && index<=feedback.crossed,pulse=game.running && index==feedback.crossed ? feedback.pulse:0
    // Progress decoration sits outside the number face; the digit is always on top.
    Text("\(id)").font(.system(size:diameter*0.57,weight:.bold)).foregroundStyle(numberInk(id)).frame(width:diameter,height:diameter).background(numberFill(id),in:Circle()).overlay{Circle().stroke(ink("#f9e8c8"),lineWidth:1)}
     .background{ZStack{if pulse>0.001{Circle().fill(ink("#efbc54").opacity(0.45*pulse)).padding(-3)};if passed || game.running && index==feedback.crossed+1{Circle().stroke(passed ? ink("#d9a442"):forest,lineWidth:2).padding(-2)}}}
     .zIndex(game.running && (index==feedback.crossed || index==feedback.crossed+1) ? 2:0).scaleEffect(1+pulse*0.14).accessibilityLabel("目标 \(index)：\(id)号").accessibilityValue(passed ? "已跨越":"等待跨越")
   }
  }
 }
}

/// Static board picture; equality skips redraws while a finger moves a drag preview.
private struct BoardSurface:View,Equatable {
 let data:GameData;let placed:[Placement];let selected:Int?;let start:String;let finish:String;let actionsVisible:Bool;let actionCount:Int
 static func ==(a:BoardSurface,b:BoardSurface)->Bool {a.placed==b.placed && a.selected==b.selected && a.start==b.start && a.finish==b.finish && a.actionsVisible==b.actionsVisible && a.actionCount==b.actionCount}
 var body:some View {Canvas{c,size in drawBoard(c,size,data,placed:placed,selected:selected,start:start,finish:finish,actionsVisible:actionsVisible,actionCount:actionCount)}}
}
private struct DragPreviewLayer:View {
 @ObservedObject var game:Game;@ObservedObject var drag:NativeDrag;let u:CGFloat
 var body:some View {Canvas{c,_ in
  guard let p=drag.candidate else{return}
  let(tw,th)=game.data.dimensions(p.r),pad=u*0.35,rect=CGRect(x:pad+CGFloat(p.x)*u,y:pad+CGFloat(p.y)*u,width:CGFloat(tw)*u,height:CGFloat(th)*u)
  let color = !drag.problem(game).isEmpty ? ink("#c04835"):game.placed.contains{game.data.overlaps(p,$0)} ? ink(Design.coveredAmber):forest
  var g=c;g.clip(to:Path(CGRect(x:pad,y:pad,width:5*u,height:4*u)))
  g.fill(Path(roundedRect:rect,cornerRadius:u*0.06),with:.color(color.opacity(0.28)));g.stroke(Path(roundedRect:rect,cornerRadius:u*0.06),with:.color(color),lineWidth:3)
 }.allowsHitTesting(false)}
}
/// Eight-direction horse from the route tangent; time-driven so only this layer animates.
private struct HorseLayer:View {
 @ObservedObject var game:Game;let u:CGFloat
 var body:some View {TimelineView(.animation(paused:!game.running)){_ in
  let pose=pose()
  Canvas{c,_ in drawHorse(c,CGPoint(x:u*0.35+pose.body[0]*u,y:u*0.35+pose.body[1]*u),pose.heading,u,jump:pose.jump)}
   .accessibilityLabel("马儿").accessibilityValue("朝向\(pose.heading.label)\(game.running ? "，跑动中":"")")
 }.allowsHitTesting(false)}
 private func pose()->(body:[Double],heading:HorseHeading,jump:CGFloat) {
  let route=game.route,running=game.running && !route.isEmpty
  let cursor=running ? min(game.runProgress()*Double(route.count),Double(route.count)-0.00001):0
  func onRoute(_ t:Double)->[Double] {let c=max(0,min(t,Double(route.count)-0.00001));return game.data.position(route[Int(c)],c.truncatingRemainder(dividingBy:1))}
  let raw=running ? onRoute(cursor):game.data.gates[game.startGate] ?? [-1,-1]
  var dir:[Double]=[1,0]
  if running{let a=onRoute(cursor-0.025),b=onRoute(cursor+0.025),length=max(0.0001,hypot(b[0]-a[0],b[1]-a[1]));dir=[(b[0]-a[0])/length,(b[1]-a[1])/length]}
  else if let p=game.data.gates[game.startGate]{let v=entryOutward(p);dir=[-v[0],-v[1]]}
  var jump:CGFloat=0
  if running{let step=route[Int(cursor)];if step.jump != nil{let road=game.data.road(step),local=cursor.truncatingRemainder(dividingBy:1),along=step.from==road.from ? local:1-local;jump=CGFloat(sin(Double.pi*max(0,1-abs(along-(road.jumpAt ?? 0.5))*8))*10)}}
  return([raw[0]+dir[0]*0.36,raw[1]+dir[1]*0.36],HorseHeading(dx:dir[0],dy:dir[1]),jump)
 }
}
private struct BoardPress {var piece:Placement?;var wasSelected=false;var moved=false;var ignored=false}
struct NativeBoard:View {
 @GestureState private var touching=false
 @State private var token=UUID()
 @State private var press:BoardPress?
 @ObservedObject var game:Game
 @ObservedObject var drag:NativeDrag
 var body:some View {
  GeometryReader{geo in
   let u=min(geo.size.width/6.3,geo.size.height/5.3),w=u*5.7,h=u*4.7
   VStack(spacing:0){
    ZStack{
     ZStack(alignment:.topLeading){
      BoardSurface(data:game.data,placed:game.placed,selected:game.selected,start:game.startGate,finish:game.finishGate,actionsVisible:!game.running,actionCount:game.turnMode.actions.count).equatable()
       .contentShape(Rectangle()).gesture(boardGesture(u))
       .accessibilityElement(children:.contain).accessibilityLabel("棋盘").accessibilityChildren{pieceElements(u,w,h)}
      DragPreviewLayer(game:game,drag:drag,u:u)
      if game.running{HorseLayer(game:game,u:u)}
      ForEach(game.data.gates.keys.sorted(),id:\.self){letter in gateButton(letter,u)}
      if !game.running,(drag.piece==nil || drag.fromControls),let piece=game.placed.first(where:{$0.id==game.selected}){NativeBoardPieceActions(game:game,drag:drag,piece:piece,unit:u)}
     }.frame(width:w,height:h).background{GeometryReader{frame in Color.clear.preference(key:BoardFrameKey.self,value:frame.frame(in:.named("game")))}}.onPreferenceChange(BoardFrameKey.self){drag.board=$0}
    }.frame(width:u*6.3,height:u*5.3)
   }.frame(maxWidth:.infinity,maxHeight:.infinity)
  }
 }
 /// Hit targets stay centered on the original A–F letters.
 private func gateButton(_ letter:String,_ u:CGFloat)->some View {
  let center=entryCenter(game.data.gates[letter]!)
  let roles=[letter==game.startGate ? "起点":nil,letter==game.finishGate ? "终点":nil].compactMap{$0}.joined(separator:"、")
  return Button{if game.level.solveEndpoints && !game.running && drag.owner==nil{game.show("gate:\(letter)")}}label:{Color.clear.frame(width:u*0.70,height:u*0.70).contentShape(Rectangle())}
   .buttonStyle(.plain).accessibilityLabel("入口 \(letter)").accessibilityValue(roles.isEmpty ? "未指定":roles).offset(x:(center[0]-0.35)*u,y:(center[1]-0.35)*u)
 }
 private func cell(_ location:CGPoint,_ u:CGFloat)->(x:Int,y:Int) {(Int(floor((location.x-drag.board.minX)/u-0.35)),Int(floor((location.y-drag.board.minY)/u-0.35)))}
 private func boardGesture(_ u:CGFloat)->some Gesture {
  DragGesture(minimumDistance:0,coordinateSpace:.named("game")).updating($touching){_,state,_ in state=true}.onChanged{value in
   if press==nil{
    guard drag.claim(token,game) else{press=BoardPress(ignored:true);return}
    let(x,y)=cell(value.startLocation,u),hit=game.data.hit(game.placed,x,y)
    press=BoardPress(piece:hit,wasSelected:hit != nil && game.selected==hit?.id)
    if let hit{game.select(hit.id)}
   }
   guard var current=press,!current.ignored else{return}
   if !current.moved,hypot(value.translation.width,value.translation.height)>12{current.moved=true;press=current}
   if current.moved,drag.piece==nil,let p=current.piece{
    let at=CGPoint(x:value.startLocation.x-drag.board.minX,y:value.startLocation.y-drag.board.minY)
    drag.begin(token,game,p,value.startLocation,CGPoint(x:at.x/u-0.35-CGFloat(p.x),y:at.y/u-0.35-CGFloat(p.y)))
   }
   drag.move(token,value.location)
  }.onEnded{value in
   defer{press=nil}
   guard let current=press,!current.ignored else{return}
   if drag.piece != nil{drag.finish(token,game);return}
   if !current.moved{
    let(x,y)=cell(value.startLocation,u),onCell=(0..<5).contains(x) && (0..<4).contains(y)
    let selectedIsPlaced=game.placed.contains{$0.id==game.selected}
    if current.piece != nil{if current.wasSelected{game.deselect()}}
    // An empty cell places a rack piece; with a board piece selected, it only clears the selection.
    else if onCell && game.selected != nil && !selectedIsPlaced{game.place(x,y)}
    else if selectedIsPlaced{game.deselect()}
   }
   drag.release(token,game)
  }
 }
 /// VoiceOver elements per board piece, layered for accessibility only; touch still goes to the board.
 private func pieceElements(_ u:CGFloat,_ w:CGFloat,_ h:CGFloat)->some View {
  ZStack(alignment:.topLeading){
   Color.clear.frame(width:w,height:h)
   ForEach(game.placed,id:\.id){p in pieceElement(p,u)}
  }.frame(width:w,height:h,alignment:.topLeading)
 }
 private func pieceElement(_ p:Placement,_ u:CGFloat)->some View {
  let(tw,th)=game.data.dimensions(p.r)
  let covered = !game.data.coveredBy(p,game.placed).isEmpty
  let active:Bool=game.selected==p.id
  let value:String="第\(p.y+1)行第\(p.x+1)列，\(p.r*90)度"+(covered ? "，下层被遮挡":"")+(active ? "，已选中":"")
  let traits:AccessibilityTraits=active ? [.isButton,.isSelected]:.isButton
  let frame=CGRect(x:(CGFloat(p.x)+0.35)*u,y:(CGFloat(p.y)+0.35)*u,width:CGFloat(tw)*u,height:CGFloat(th)*u)
  return Color.clear.frame(width:frame.width,height:frame.height).offset(x:frame.minX,y:frame.minY)
   .accessibilityLabel("棋盘拼板 \(p.id) 号").accessibilityValue(value).accessibilityAddTraits(traits)
   .accessibilityAction{if active{game.deselect()}else{game.select(p.id)}}
   .accessibilityAction(named:"顺时针旋转90度"){guard game.turnMode != .ccw else{return};game.select(p.id);game.rotate(1)}
   .accessibilityAction(named:"逆时针旋转90度"){guard game.turnMode != .cw else{return};game.select(p.id);game.rotate(-1)}
   .accessibilityAction(named:"移回拼板区"){game.select(p.id);game.remove()}
 }
}
