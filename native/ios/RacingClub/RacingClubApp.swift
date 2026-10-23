import SwiftUI

private let forest = Color(red:0.145,green:0.298,blue:0.231)
private let paper = Color(red:0.973,green:0.961,blue:0.929)
private let saddle = Color(red:0.47,green:0.33,blue:0.23)
private func ink(_ hex:String)->Color { let n=UInt64(hex.dropFirst(),radix:16) ?? 0; return Color(red:Double((n>>16)&255)/255,green:Double((n>>8)&255)/255,blue:Double(n&255)/255) }
private func numberFill(_ id:Int)->Color {ink([1:"#aa346a",2:"#e5bd47",3:"#a7ba54",4:"#ae362e",5:"#245f91",6:"#236648",7:"#e09b59",8:"#75418e",9:"#75452b",10:"#1b6278"][id] ?? "#254c3b")}
private func numberInk(_ id:Int)->Color {[2,3,7].contains(id) ? .black:.white}
private struct ProgressRecord:Codable {let placed:[Placement];let moves:Int;let hints:Int;let elapsed:Double;let finished:Bool;let start:String?;let finish:String?}
@MainActor final class Game:ObservableObject {
 let data:GameData;let repository:any LeaderboardRepository
 @Published var screen="home";@Published var level:Level;@Published var placed:[Placement]=[];@Published var selected:Int?;@Published var rotation=0;@Published var moves=0;@Published var hints=0;@Published var message="";@Published var running=false;@Published var progress=0.0;@Published var dialog="";@Published var elapsed=0.0;@Published var clockTick=0
 @Published var selectedDifficultyId="starter";
 @Published var chosenStart="";@Published var chosenFinish=""
 func solved(_ id:Int)->Bool {!repository.list(level:id).isEmpty}
 func inProgress(_ id:Int)->Bool {guard let d=UserDefaults.standard.data(forKey:"level-\(id)"),let p=try?JSONDecoder().decode(ProgressRecord.self,from:d)else{return false};return !p.finished && !p.placed.isEmpty}
 var startGate:String {level.start.isEmpty ? chosenStart:level.start}
 var finishGate:String {level.finish.isEmpty ? chosenFinish:level.finish}
 var dragging=false
 func beginDrag(){dragging=true;runTask?.cancel()}
 func endDrag(){dragging=false;checkRoute()}
 var route:[RouteStep]=[];private var started:Double?;private var history:[([Placement],Int,Int,String,String)]=[];private var runTask:Task<Void,Never>?
 init(){let url=Bundle.main.url(forResource:"game-data",withExtension:"json")!;data=try!JSONDecoder().decode(GameData.self,from:Data(contentsOf:url));level=data.levels[0];repository=LocalLeaderboard()}
 var time:Double {elapsed+(started.map{ProcessInfo.processInfo.systemUptime-$0} ?? 0)}
 func resume(){if started==nil && screen=="game" && !running && dialog.isEmpty {started=ProcessInfo.processInfo.systemUptime}}
 func pause(){if let start=started{elapsed+=ProcessInfo.processInfo.systemUptime-start};started=nil;save()}
 func save(){guard screen != "home" else{return};let r=ProgressRecord(placed:placed,moves:moves,hints:hints,elapsed:time,finished:running||screen=="win",start:chosenStart,finish:chosenFinish);if let d=try?JSONEncoder().encode(r){UserDefaults.standard.set(d,forKey:"level-\(level.id)")}}
 func start(_ l:Level){pause();runTask?.cancel();level=l;selectedDifficultyId=l.difficultyId;screen="game";running=false;placed=[];moves=0;hints=0;elapsed=0;history=[];route=[];chosenStart="";chosenFinish="";if let d=UserDefaults.standard.data(forKey:"level-\(l.id)"),let p=try?JSONDecoder().decode(ProgressRecord.self,from:d),!p.finished{for tile in p.placed where l.sequence.contains(tile.id)&&data.fits(tile){placed.removeAll{$0.id==tile.id};placed.append(tile)};moves=p.moves;hints=p.hints;elapsed=p.elapsed;chosenStart=p.start ?? "";chosenFinish=p.finish ?? ""};selected=l.requiredPieces.first{n in !placed.contains{$0.id==n}};rotation=0;message="";resume()}
 func home(){pause();runTask?.cancel();screen="home";running=false}
 func select(_ id:Int){guard !running else{return};selected=id;let p=placed.first{$0.id==id};rotation=p?.r ?? 0;if let p,placed.last?.id != id{placed.removeAll{$0.id==id};placed.append(p);save()};message=""}
 private func remember(){history.append((placed,moves,hints,chosenStart,chosenFinish))}
 func place(_ x:Int,_ y:Int){guard !running,let id=selected else{return};let p=Placement(id:id,x:x,y:y,r:rotation);guard data.fits(p) else{message="超出棋盘边界，已保留原位";return};remember();placed.removeAll{$0.id==id};placed.append(p);moves+=1;changed()}
 func rotate(_ quarterTurns:Int=1){guard !running,let id=selected else{return};var next=((rotation+quarterTurns)%4+4)%4;if let i=placed.firstIndex(where:{$0.id==id}){let p=data.turnedInside(placed[i],quarterTurns);next=p.r;remember();placed.removeAll{$0.id==id};placed.append(p);moves+=1};rotation=next;changed()}
 func remove(){guard !running,let id=selected,placed.contains(where:{$0.id==id}) else{return};remember();placed.removeAll{$0.id==id};moves+=1;changed()}
 func undo(){guard !running,let last=history.popLast() else{return};placed=last.0;moves=last.1;hints=max(hints,last.2);chosenStart=last.3;chosenFinish=last.4;rotation=placed.first{$0.id==selected}?.r ?? rotation;changed()}
 func reset(){guard !running else{return};remember();placed=[];chosenStart="";chosenFinish="";moves+=1;changed()}
 func hint(){guard !running else{return};guard let p=level.solution.first(where:{!placed.contains($0)}) else{if level.solveEndpoints{chosenStart=level.solutionStart;chosenFinish=level.solutionFinish;hints+=1;moves+=1;changed()};return};remember();placed=placed.filter{$0.id != p.id && !data.overlaps(p,$0)}+[p];selected=p.id;rotation=p.r;moves+=1;hints+=1;message="";changed()}
 func changed(){message="";save();checkRoute()}
 func checkRoute(){runTask?.cancel();guard screen=="game",!running,!dragging,dialog.isEmpty else{return};guard !level.solveEndpoints || (!startGate.isEmpty && !finishGate.isEmpty),let valid=data.trace(level,placed,start:startGate,finish:finishGate) else{return};runTask=Task{try? await Task.sleep(for:.milliseconds(250));guard !Task.isCancelled else{return};pause();running=true;route=valid;progress=0;repository.save(Score(level:level.id,millis:Int(elapsed*1000),moves:moves,hints:hints,timestamp:Date()));save();let start=ProcessInfo.processInfo.systemUptime;while progress<1{guard !Task.isCancelled else{return};progress=min(1,(ProcessInfo.processInfo.systemUptime-start)/(Double(valid.count)*1.35));try? await Task.sleep(for:.milliseconds(16))};finishRun()}}
 func finishRun(){guard running else{return};runTask?.cancel();running=false;screen="win";save()}
 func choose(_ letter:String,finish:Bool?=nil){remember();if finish ?? (dialog=="finish"){chosenFinish=letter}else{chosenStart=letter};moves+=1;close();changed()}
 func show(_ type:String){guard !running else{return};pause();dialog=type}
 func close(){dialog="";resume()}
}
private func timeString(_ seconds:Double)->String {let ms=Int(seconds*1000);return String(format:"%02d:%02d.%02d",ms/60000,ms/1000%60,ms%1000/10)}
@main struct RacingClubApp:App {@StateObject private var game=Game();@Environment(\.scenePhase) private var phase;var body:some Scene{WindowGroup{ClubView(game:game).preferredColorScheme(.light).onChange(of:phase){_,p in if p == .active{game.resume()}else{game.pause()}}}}}
@MainActor final class NativeDrag:ObservableObject {
 @Published var board=CGRect.zero
 @Published var piece:Placement?
 @Published var fromControls=false
 @Published var pointer=CGPoint.zero
 private var grab=CGPoint.zero
 var liftOrigin:CGPoint {CGPoint(x:pointer.x-grab.x*unit,y:pointer.y-grab.y*unit)}
 var unit:CGFloat {board.width/5.7}
 func begin(_ game:Game,_ p:Placement,_ at:CGPoint,_ anchor:CGPoint?=nil,controls:Bool=false){guard !game.running,unit>0 else{return};game.beginDrag();game.select(p.id);game.rotation=p.r;fromControls=controls;piece=p;pointer=at;let(w,h)=game.data.dimensions(p.r);grab=anchor ?? CGPoint(x:Double(w)/2,y:Double(h)/2)}
 var candidate:Placement? {guard let p=piece,unit>0 else{return nil};return Placement(id:p.id,x:Int(((pointer.x-board.minX)/unit-0.35-grab.x).rounded()),y:Int(((pointer.y-board.minY)/unit-0.35-grab.y).rounded()),r:p.r)}
 func problem(_ game:Game)->String {guard let p=candidate else{return ""};if !board.contains(pointer){return "拖入棋盘内放置"};let(w,h)=game.data.dimensions(p.r);if p.x<0 || p.y<0 || p.x+w>5 || p.y+h>4{return "超出棋盘边界"};return ""}
 func feedback(_ game:Game)->String {let error=problem(game);if !error.isEmpty{return error};guard let p=candidate else{return ""};let ids=game.placed.filter{game.data.overlaps(p,$0)}.map(\.id).sorted();return ids.isEmpty ? "":"将叠放在 \(ids.map(String.init).joined(separator:"、")) 号上方"}
 func cancel(_ game:Game){piece=nil;fromControls=false;game.endDrag()}
 func finish(_ game:Game){defer{piece=nil;fromControls=false;game.endDrag()};guard let p=candidate else{return};let error=problem(game);if error.isEmpty{if game.placed.first(where:{$0.id==p.id}) != p{game.rotation=p.r;game.place(p.x,p.y)}}else{game.message=error=="拖入棋盘内放置" ? "未放入棋盘，已保留原位":"\(error)，已保留原位"}}
}
private struct BoardFrameKey:PreferenceKey {static var defaultValue=CGRect.zero;static func reduce(value:inout CGRect,nextValue:()->CGRect){value=nextValue()}}
struct ClubView:View {
 @ObservedObject var game:Game
 @StateObject private var drag=NativeDrag()
 @Environment(\.scenePhase) private var phase
 private let tick=Timer.publish(every:0.1,on:.main,in:.common).autoconnect()
 var body:some View {
  GeometryReader{geo in
   VStack(spacing:5){
    if game.screen=="home"{home}else if game.screen=="win"{header;win}else{
     HStack(spacing:10){
      NativeBoard(game:game,drag:drag).frame(width:min((geo.size.height-16)*6.3/5.3,max(geo.size.width-274,(geo.size.width-24)*0.54)),height:geo.size.height-16)
      workbench.frame(maxWidth:.infinity,maxHeight:.infinity)
     }
    }
   }.padding(.horizontal,game.screen=="home" ? 18:12).padding(.vertical,8)
  }.coordinateSpace(name:"game").background(paper)
   .overlay(alignment:.topLeading){if let p=drag.piece{Canvas{context,_ in
    var c=context;c.opacity=0.86;let(w,h)=game.data.dimensions(p.r),u=drag.unit
    let origin=CGPoint(x:drag.liftOrigin.x,y:drag.liftOrigin.y-18)
    drawTile(&c,game.data,Placement(id:p.id,x:0,y:0,r:p.r),u,origin)
    if !drag.problem(game).isEmpty{c.stroke(Path(roundedRect:CGRect(origin:origin,size:CGSize(width:CGFloat(w)*u,height:CGFloat(h)*u)),cornerRadius:6),with:.color(ink("#c04835")),lineWidth:2)}
   }.allowsHitTesting(false)}}
   .sheet(isPresented:Binding(get:{!game.dialog.isEmpty},set:{if !$0{game.close()}})){dialog}
   .onReceive(tick){_ in game.clockTick+=1}
   .onChange(of:game.screen){_,_ in drag.cancel(game)}
   .onChange(of:phase){_,p in if p != .active{drag.cancel(game)}}
 }
 private var header:some View {HStack(spacing:12){if game.screen=="home"{Text("♞ RacingClub").font(.system(size:20,weight:.bold,design:.serif))}else{tool("chevron.left","返回"){game.home()};Text("挑战 \(game.level.number)").font(.headline);difficultyBadge(game.data.difficulties.first{$0.id==game.level.difficultyId}!);Spacer();Text(timeString(game.time)).font(.system(size:13,design:.monospaced));tool("trophy","本关排行榜"){game.show("rank")}};if game.screen=="home"{Spacer()};if game.screen != "home"{tool("questionmark.circle","玩法"){game.show("help")}}}.foregroundStyle(forest).frame(height:38)}
 private func difficultyBadge(_ d:Difficulty)->some View {Text(d.label).font(.system(size:10,weight:.bold)).foregroundStyle(ink(d.color)).padding(.horizontal,8).padding(.vertical,4).background(paper.opacity(0.96),in:Capsule()).overlay{Capsule().stroke(ink("#d5b486"),lineWidth:0.6)}.accessibilityLabel("难度：\(d.label) \(d.originalLabel)")}
 private var home:some View {
  let d=game.data.difficulties.first{$0.id==game.selectedDifficultyId}!
  let levels=game.data.levels.filter{$0.difficultyId==d.id}
  return GeometryReader{layout in HStack(spacing:18){
   VStack(spacing:8){Text("♞ RacingClub").font(.system(size:20,weight:.bold,design:.serif)).foregroundStyle(forest).frame(maxWidth:.infinity,minHeight:30,alignment:.leading)
   ZStack(alignment:.topLeading){Image("horse-arena").resizable().scaledToFill();Text("今天，\n跃过新的挑战").font(.system(size:27,weight:.bold)).foregroundStyle(forest).padding(18)}.frame(maxWidth:.infinity,maxHeight:.infinity).clipped().clipShape(RoundedRectangle(cornerRadius:16))
   }.frame(width:layout.size.width*0.27)
   VStack(spacing:10){
    HStack{Text("马术训练场").font(.title2.bold());Spacer();Text(d.originalLabel).font(.caption.bold()).foregroundStyle(ink(d.color))}.frame(height:30)
    HStack(spacing:5){ForEach(Array(game.data.difficulties.enumerated()),id:\.element.id){index,item in
     let active=game.selectedDifficultyId==item.id
     Button{game.selectedDifficultyId=item.id}label:{ZStack(alignment:.bottom){
      HomePlaqueBackground(active:active ? ink(item.color):nil,top:8)
      VStack(spacing:1){Spacer().frame(height:20);Text(item.label).font(.system(size:11,weight:.bold));Text(String(format:"%02d–%02d",item.firstLevel,item.lastLevel)).font(.system(size:8))}.padding(.bottom,5).foregroundStyle(active ? Color.white:ink(item.color))
      Text(["I","II","III","IV","V"][index]).font(.system(size:11,weight:.bold,design:.serif)).foregroundStyle(ink("#ffedbc")).frame(width:20,height:20).background(ink(item.color),in:Circle()).overlay{Circle().stroke(ink("#d2a157"),lineWidth:2)}.frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.top)
     }.frame(height:48)}.buttonStyle(HomePlaquePress()).accessibilityLabel("难度 \(item.label) \(item.originalLabel)").accessibilityValue(active ? "已选择":"未选择")
    }}
    ScrollView{LazyVGrid(columns:Array(repeating:GridItem(.flexible(),spacing:7),count:4),spacing:6){ForEach(levels){level in
     let done=game.solved(level.id),ongoing=game.inProgress(level.id),best=game.repository.list(level:level.id).first
     Button{game.start(level)}label:{VStack(alignment:.leading,spacing:3){
      HStack(spacing:3){
       Text(level.number).font(.system(size:25,weight:.bold,design:.serif)).foregroundStyle(done ? ink("#fff6df"):forest)
       Spacer()
       Image(systemName:done ? "checkmark.circle.fill":ongoing ? "clock":"chevron.right").font(.system(size:done ? 19:11,weight:.bold)).foregroundStyle(done ? ink("#ffd783"):ongoing ? ink("#ad6a1e"):saddle)
      }
      if done{HStack(spacing:4){Text("已完成").font(.system(size:8,weight:.bold)).foregroundStyle(ink("#ffe0a0"));Spacer(minLength:0);if let best{Text(timeString(Double(best.millis)/1000)).font(.system(size:8)).foregroundStyle(ink("#f8efdb")).lineLimit(1)}}}else{Text("\(d.label) · \(level.requiredPieces.count) 块 · \(level.sequence.count) 跨").font(.system(size:8)).foregroundStyle(saddle).lineLimit(1)}
     }.padding(.horizontal,9).padding(.vertical,7).frame(maxWidth:.infinity,minHeight:max(57,(layout.size.height-114)/4)).background{HomePlaqueBackground(active:done ? forest:nil)}}.buttonStyle(HomePlaquePress()).accessibilityLabel("进入挑战 \(level.number)").accessibilityValue(done ? "已完成":ongoing ? "进行中":"未开始")
    }}}
   }.foregroundStyle(forest).frame(maxWidth:.infinity)
  }}
 }
 private var workbench:some View {
  VStack(spacing:4){
   HStack(spacing:5){tool("chevron.left","返回"){game.home()};Text("挑战 \(game.level.number)").font(.system(size:16,weight:.bold)).lineLimit(1);Spacer(minLength:0);difficultyBadge(game.data.difficulties.first{$0.id==game.level.difficultyId}!)}.frame(height:28)
   HStack(spacing:4){Image(systemName:"timer");Text(timeString(game.time)).monospacedDigit();Spacer(minLength:0);tool("trophy","本关排行榜"){game.show("rank")};tool("questionmark.circle","玩法"){game.show("help")}}.font(.system(size:11)).frame(height:26)
   NativeCourseSequence(game:game,level:game.level)
   HStack{Button{if game.level.solveEndpoints{game.show("start")}}label:{Label("起点 \(game.startGate.isEmpty ? "?":game.startGate)",systemImage:"arrow.right")};Spacer(minLength:0);Button{if game.level.solveEndpoints{game.show("finish")}}label:{Label("终点 \(game.finishGate.isEmpty ? "?":game.finishGate)",systemImage:"flag.checkered")}}.font(.system(size:9,weight:.bold)).padding(.vertical,2)
   NativeTileRack(game:game,drag:drag)
   let coveredCount=game.placed.filter{!game.data.coveredBy($0,game.placed).isEmpty}.count
   let error=drag.piece != nil ? drag.problem(game):game.message
   let feedback=drag.piece != nil ? drag.feedback(game):error.isEmpty && coveredCount>0 ? "\(coveredCount) 块被遮挡 · 点右侧编号置顶":error
   HStack(spacing:4){if !feedback.isEmpty && error.isEmpty{Image(systemName:"square.3.layers.3d")};Text(feedback)}.font(.system(size:10)).foregroundStyle(ink(error.isEmpty ? "#925b1c":"#a9442b")).lineLimit(2).frame(maxWidth:.infinity,minHeight:28,maxHeight:28,alignment:.leading)
   Divider()
   if game.running{Button{game.finishRun()}label:{Label("跳过动画",systemImage:"forward.end.fill").font(.system(size:13,weight:.bold)).foregroundStyle(forest).frame(maxWidth:.infinity,minHeight:42)}.buttonStyle(GameControlStyle()).accessibilityLabel("跳过动画").frame(height:48)}else{HStack(spacing:0){
    tool("arrow.uturn.backward","撤销"){game.undo()}.frame(maxWidth:.infinity)
    tool("lightbulb","提示"){game.hint()}.frame(maxWidth:.infinity)
    tool("arrow.clockwise","重置"){game.reset()}.frame(maxWidth:.infinity)
   }.frame(height:48)}
  }.foregroundStyle(forest).padding(.vertical,2)
 }
 private var win:some View {HStack(spacing:25){Image("horse-arena").resizable().scaledToFill().frame(maxWidth:.infinity,maxHeight:.infinity).clipped().clipShape(RoundedRectangle(cornerRadius:18));VStack(spacing:14){Text("漂亮，顺利通关！").font(.title.bold());Text(timeString(game.elapsed)).font(.system(size:32,design:.serif));Text("\(game.moves) 步 · \(game.hints==0 ? "独立完成":"使用提示")").font(.caption);Button(game.level.id<game.data.levels.last!.id ? "挑战下一关":"返回训练场"){if let next=game.data.levels.first(where:{$0.id>game.level.id}){game.start(next)}else{game.home()}}.buttonStyle(.borderedProminent).tint(forest);Button("查看本关排行榜"){game.show("rank")}}.frame(maxWidth:.infinity).foregroundStyle(forest)}}
 private var dialog:some View {NavigationStack{ScrollView{VStack(alignment:.leading,spacing:16){if game.dialog=="rank"{Text("挑战 \(game.level.number) · 本机排行榜").font(.title2.bold());let records=game.repository.list(level:game.level.id);if records.isEmpty{Text("还没有成绩，完成一次训练就会记录。")};ForEach(Array(records.enumerated()),id:\.element.id){i,r in HStack{Text("\(i+1)");Text(timeString(Double(r.millis)/1000)).monospacedDigit();Spacer();Text("\(r.moves) 步");Text(r.hints==0 ? "独立完成":"使用提示").font(.caption)}};Text("记录保存在此设备；线上服务尚未接入。").font(.caption)}else if game.dialog=="start" || game.dialog=="finish"{Text(game.dialog=="start" ? "选择起点":"选择终点").font(.title2.bold());HStack{ForEach(game.data.gates.keys.sorted(),id:\.self){letter in Button(letter){game.choose(letter)}.buttonStyle(.bordered)}}}else if game.dialog.hasPrefix("gate:"){let letter=String(game.dialog.dropFirst(5));Text("入口 \(letter)").font(.title2.bold());Text("设置起终点；两端可以使用同一入口。");HStack{Button("设为起点"){game.choose(letter,finish:false)}.buttonStyle(.borderedProminent);Button("设为终点"){game.choose(letter,finish:true)}.buttonStyle(.bordered)}}else{Text("一条路，刚刚好的顺序").font(.title2.bold());Text("看右上方障碍顺序。点右侧编号，再点棋盘左上格放置。\n\n从右侧拖入棋盘，或拖动棋盘上的拼板。点棋盘上的拼板后，附近按钮可双向旋转或移回拼板区。棋盘内允许临时叠放，琥珀标记提示下层拼板。点右侧编号可将下层置顶。边缘旋转会自动移入棋盘；消除重叠后才会判定通关。\n\n正确后自动停止计时，马儿沿路跑到终点。岔路须平顺转弯，无障碍支路不计跨障。同一障碍可多次跨越。第65关起，点入口字母设置起终点；两端可相同。绿色箭头指向起点，棋格标记代表终点。右上顺序按箭头折行，全部直接展示。")}}.padding(25)}.foregroundStyle(forest).background(paper).toolbar{ToolbarItem(placement:.confirmationAction){Button("完成"){game.close()}}}}}
 private func tool(_ icon:String,_ label:String,_ action:@escaping()->Void)->some View {Button(action:action){Image(systemName:icon).font(.system(size:19)).foregroundStyle(label=="提示" ? ink("#9b641b"):forest).frame(width:34,height:34)}.buttonStyle(GameControlStyle()).accessibilityLabel(label)}
 private func badge(_ id:Int)->some View {Text("\(id)").font(.system(size:12,weight:.bold)).foregroundStyle(numberInk(id)).frame(width:23,height:23).background(numberFill(id),in:Circle())}
 private func sequence(_ level:Level)->some View {NativeCourseSequence(game:game,level:level)}
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
private struct NativeActionAnchor {
 let left:CGFloat;let top:CGFloat;let tip:CGFloat;let below:Bool
 var width:CGFloat=140;var height:CGFloat=52;var button:CGFloat=44;var vertical=false;var inside=false
}
private func nativeActionAnchor(_ p:Placement,_ u:CGFloat,_ all:[Placement])->NativeActionAnchor {
 let w=p.r%2==0 ? 2:1,h=p.r%2==0 ? 1:2
 let tile=CGRect(x:(CGFloat(p.x)+0.35)*u,y:(CGFloat(p.y)+0.35)*u,width:CGFloat(w)*u,height:CGFloat(h)*u)
 let low=0.35*u+4,high=max(low,5.35*u-144)
 let xs=[tile.midX-70,tile.minX,tile.maxX-140].map{min(high,max(low,$0))}
 for(top,below)in [(tile.minY-60,false),(tile.maxY+8,true)]{for left in xs{
  let box=CGRect(x:left,y:top,width:140,height:52)
  if top<4 || box.maxY>4.7*u-4 || box.intersects(tile){continue}
  let blocked=all.contains{o in
   if o.id==p.id{return false};let ow=o.r%2==0 ? 2:1,oh=o.r%2==0 ? 1:2
   return box.insetBy(dx:-2,dy:-2).intersects(CGRect(x:(CGFloat(o.x)+0.35)*u,y:(CGFloat(o.y)+0.35)*u,width:CGFloat(ow)*u,height:CGFloat(oh)*u))
  }
  if !blocked{return NativeActionAnchor(left:left,top:top,tip:min(130,max(10,tile.midX-left)),below:below)}
 }}
 let button=max(24,min(44,(2*u-8)/3,0.64*u-5)),vertical=h>w
 let width=vertical ? button:button*3+8,height=vertical ? button*3+8:button
 return NativeActionAnchor(left:vertical ? tile.maxX-3-button:tile.midX-width/2,top:vertical ? tile.midY-height/2:tile.maxY-3-button,tip:0,below:false,width:width,height:height,button:button,vertical:vertical,inside:true)
}
private struct NativeBoardPieceActions:View {
 @GestureState private var touching=false
 @ObservedObject var game:Game
 @ObservedObject var drag:NativeDrag
 let piece:Placement
 let unit:CGFloat
 var body:some View {
  let a=nativeActionAnchor(piece,unit,game.placed),edge:CGFloat=a.below ? 8:44,peak:CGFloat=a.below ? 0:52
  return ZStack(alignment:.topLeading){
   if !a.inside{Path{p in p.move(to:CGPoint(x:a.tip-6,y:edge));p.addLine(to:CGPoint(x:a.tip,y:peak));p.addLine(to:CGPoint(x:a.tip+6,y:edge))}.stroke(ink("#bd8d56"),lineWidth:1)}
   Group{if a.vertical{VStack(spacing:4){buttons(a.button)}}else{HStack(spacing:4){buttons(a.button)}}}.offset(y:a.below ? 8:0)
  }.frame(width:a.width,height:a.height,alignment:.topLeading).accessibilityElement(children:.contain).accessibilityLabel("拼板操作").accessibilityValue("\(piece.id)号").offset(x:a.left,y:a.top).opacity(drag.piece==nil ? 1:0).onChange(of:touching){_,active in if !active,drag.fromControls{drag.cancel(game)}}
 }
 private func buttons(_ size:CGFloat)->some View {ForEach([-1,1,0],id:\.self){action in
  Button{if action==0{game.remove()}else{game.rotate(action)}}label:{
   Group{if action==0{Image(systemName:"square.and.arrow.up").font(.system(size:23,weight:.semibold))}else{VStack(spacing:0){Image(systemName:action<0 ? "arrow.counterclockwise":"arrow.clockwise").font(.system(size:19,weight:.semibold));Text("90°").font(.system(size:10,weight:.bold))}}}.foregroundStyle(forest).frame(width:size,height:size)
  }.buttonStyle(GameControlStyle()).accessibilityLabel(action==0 ? "移回":action<0 ? "逆时针旋转90度":"顺时针旋转90度").highPriorityGesture(DragGesture(minimumDistance:12,coordinateSpace:.named("game")).updating($touching){_,state,_ in state=true}.onChanged{value in
   if drag.piece==nil{let local=CGPoint(x:(value.startLocation.x-drag.board.minX)/drag.unit-0.35-CGFloat(piece.x),y:(value.startLocation.y-drag.board.minY)/drag.unit-0.35-CGFloat(piece.y));drag.begin(game,piece,value.startLocation,local,controls:true)}
   drag.pointer=value.location
  }.onEnded{_ in drag.finish(game)})
 }}
}
private struct NativeTileRack:View {
 @GestureState private var touching=false
 @State private var pressedId:Int?
 @ObservedObject var game:Game
 @ObservedObject var drag:NativeDrag
 var body:some View {GeometryReader{geo in
  let ids=game.level.requiredPieces,rows=stride(from:0,to:ids.count,by:5).map{Array(ids[$0..<min($0+5,ids.count)])}
  let height=min(106,max(26,(geo.size.height-CGFloat(rows.count-1)*6)/CGFloat(rows.count)))
  VStack(spacing:6){ForEach(rows.indices,id:\.self){index in
   let row=rows[index],columns=rows.count>1 ? 5:row.count,cardWidth=(geo.size.width-CGFloat(columns-1)*5)/CGFloat(columns)
   HStack(spacing:5){ForEach(row,id:\.self){id in
    let placed=game.placed.first{$0.id==id},covered=placed.map{!game.data.coveredBy($0,game.placed).isEmpty} ?? false
    Button{game.select(id)}label:{ZStack(alignment:.topLeading){
     TilePreview(game:game,id:id).padding(.top,12).padding(.bottom,placed != nil ? 17:2)
     Text("\(id)").font(.system(size:11,weight:.bold)).foregroundStyle(numberInk(id)).frame(width:18,height:18).background(numberFill(id),in:Circle())
     if placed != nil{Image(systemName:covered ? "square.3.layers.3d":"checkmark.circle.fill").font(.system(size:12)).foregroundStyle(covered ? ink("#a96b1e"):forest).frame(maxWidth:.infinity,alignment:.trailing)
      Text(covered ? "被遮挡":"已放置").font(.system(size:8,weight:.bold)).foregroundStyle(.white).padding(.horizontal,3).padding(.vertical,2).background(covered ? ink("#a96b1e"):forest,in:RoundedRectangle(cornerRadius:3)).frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.bottom)
     }
    }.padding(3).frame(width:cardWidth,height:height).background(game.selected==id ? ink("#fff1d2"):placed != nil ? ink("#e6eee3"):ink("#fcf3e2"),in:RoundedRectangle(cornerRadius:8)).overlay{RoundedRectangle(cornerRadius:8).stroke(game.selected==id || placed != nil ? forest:ink("#ebd9bc"),lineWidth:game.selected==id ? 2:1)}}.buttonStyle(.plain).disabled(game.running)
    .simultaneousGesture(DragGesture(minimumDistance:0,coordinateSpace:.named("game")).updating($touching){_,state,_ in state=true}.onChanged{value in
     guard !game.running else{return}
     if pressedId==nil{pressedId=id;game.beginDrag();game.select(id)}
     if drag.piece==nil,hypot(value.translation.width,value.translation.height)>12{let p=game.placed.first{$0.id==id} ?? Placement(id:id,x:0,y:0,r:game.rotation);drag.begin(game,p,value.startLocation)}
     if drag.piece != nil{drag.pointer=value.location}
    }.onEnded{_ in if drag.piece != nil{drag.finish(game)}else{game.endDrag()};pressedId=nil}).onChange(of:touching){_,active in if !active,pressedId != nil{drag.cancel(game);pressedId=nil}}.accessibilityLabel("拼板 \(id)").accessibilityValue(placed == nil ? "待放置":covered ? "下层被遮挡，点按置顶":"已放置")
   };if row.count<columns{ForEach(0..<(columns-row.count),id:\.self){_ in Color.clear.frame(width:cardWidth,height:height)}}}
  }}
 }}
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
  context.fill(Path(roundedRect:face,cornerRadius:6),with:.linearGradient(Gradient(colors:active.map{[$0,$0.opacity(0.9)]} ?? [ink("#fffbef"),ink("#f7e6c6")]),startPoint:CGPoint(x:0,y:face.minY),endPoint:CGPoint(x:0,y:face.maxY)))
  context.stroke(Path(roundedRect:frame.insetBy(dx:1,dy:1),cornerRadius:7),with:.color(ink("#f7ddb0")),lineWidth:1)
  context.stroke(Path(roundedRect:face.insetBy(dx:1,dy:1),cornerRadius:5),with:.color(Color.white.opacity(0.7)),lineWidth:0.5)
  if top==0{for x in [CGFloat(5),size.width-5]{for y in [CGFloat(5),size.height-7]{context.fill(Path(ellipseIn:CGRect(x:x-1.5,y:y-1.5,width:3,height:3)),with:.color(ink("#bf8f45")))}}}
 }}
}
private func drawTile(_ c:inout GraphicsContext,_ data:GameData,_ p:Placement,_ u:CGFloat,_ origin:CGPoint){let(w,h)=data.dimensions(p.r);let top=CGPoint(x:origin.x+CGFloat(p.x)*u,y:origin.y+CGFloat(p.y)*u);c.fill(Path(roundedRect:CGRect(x:top.x,y:top.y,width:CGFloat(w)*u,height:CGFloat(h)*u),cornerRadius:7),with:.color(ink("#d7b68a")));let piece=data.pieces.first{$0.id==p.id}!;for road in piece.paths {var path=Path();for(i,pt)in road.samples.enumerated(){let q=data.rotate(pt,p.r);let point=CGPoint(x:top.x+q[0]*u,y:top.y+q[1]*u);if i==0{path.move(to:point)}else{path.addLine(to:point)}};c.stroke(path,with:.color(ink("#fff2d6")),lineWidth:u*0.36)};let g=piece.gate;let q=data.rotate([g[0]/100,g[1]/100],p.r);let a=(g[2]+Double(p.r*90)) * .pi/180;let center=CGPoint(x:top.x+q[0]*u,y:top.y+q[1]*u);let dx = -sin(a)*u*0.23,dy=cos(a)*u*0.23;var gate=Path();gate.move(to:CGPoint(x:center.x-dx,y:center.y-dy));gate.addLine(to:CGPoint(x:center.x+dx,y:center.y+dy));c.stroke(gate,with:.color(ink(piece.color)),lineWidth:u*0.09)}
struct TilePreview:View {@ObservedObject var game:Game;let id:Int;var body:some View{Canvas{context,size in var c=context;let r=1;let(w,h)=game.data.dimensions(r);let u=min(size.width/CGFloat(w),size.height/CGFloat(h));drawTile(&c,game.data,Placement(id:id,x:0,y:0,r:r),u,CGPoint(x:(size.width-CGFloat(w)*u)/2,y:(size.height-CGFloat(h)*u)/2))}}}
private struct NativeCourseSequence:View {
 @ObservedObject var game:Game
 let level:Level
 private let diameter:CGFloat=24
 var body:some View {
  ViewThatFits(in:.horizontal){
   goalRows(level.sequence.count>8 ? 2:1)
   goalRows(level.sequence.count>8 ? 3:2)
   goalRows(4)
   goalRows(5)
  }.frame(maxWidth:.infinity).padding(.vertical,8).padding(.horizontal,9).background(paper.opacity(0.96),in:RoundedRectangle(cornerRadius:10)).overlay{RoundedRectangle(cornerRadius:10).stroke(ink("#e8d4b8"),lineWidth:1)}.accessibilityElement(children:.contain).accessibilityLabel("障碍顺序")
 }
 private func rows(_ count:Int)->[[Int]] {
  let n=level.sequence.count+2,r=min(count,n)
  var next=0
  return(0..<r).map{row in let size=n/r+(row<n%r ? 1:0);defer{next+=size};return Array(next..<next+size)}
 }
 private func goalRows(_ count:Int)->some View {
  let groups=rows(count),columns=groups.map(\.count).max() ?? 1
  let width=CGFloat(columns)*diameter+CGFloat(columns-1)*8
  return VStack(spacing:0){
   ForEach(groups.indices,id:\.self){row in
    let reverse=row%2==1,indices=reverse ? Array(groups[row].reversed()):groups[row]
    HStack(spacing:0){ForEach(Array(indices.enumerated()),id:\.element){column,index in
     if column>0{Image(systemName:reverse ? "arrow.left":"arrow.right").font(.system(size:9,weight:.semibold)).foregroundStyle(saddle).frame(width:8)}
     goal(index)
    }}.frame(width:width,alignment:reverse ? .trailing:.leading)
    if row<groups.count-1{HStack{if !reverse{Spacer(minLength:0)};Image(systemName:"arrow.down").font(.system(size:8,weight:.semibold)).foregroundStyle(saddle);if reverse{Spacer(minLength:0)}}.padding(.horizontal,max(0,diameter/2-4)).frame(width:width,height:8)}
   }
  }
 }
 private func goal(_ index:Int)->some View {
  Group{
   if index==0 || index==level.sequence.count+1 {
    let finish=index>0
    let value=game.screen != "home" && level.id==game.level.id ? (finish ? game.finishGate:game.startGate):(finish ? level.finish:level.start)
    let color=finish ? ink("#a95837"):forest
    Text(value.isEmpty ? "?":value).font(.system(size:diameter*0.56,weight:.bold)).foregroundStyle(color).frame(width:diameter,height:diameter).background(color.opacity(0.1),in:Circle()).overlay{Circle().stroke(color,lineWidth:1)}
   }else{
    let id=level.sequence[index-1],feedback=game.data.crossingFeedback(game.route,game.progress)
    let passed=game.running && index<=feedback.crossed,pulse=game.running && index==feedback.crossed ? feedback.pulse:0
    Text("\(id)").font(.system(size:diameter*0.57,weight:.bold)).foregroundStyle(numberInk(id)).frame(width:diameter,height:diameter).background(numberFill(id),in:Circle()).overlay{Circle().stroke(ink("#f9e8c8"),lineWidth:1)}.background{if passed || game.running && index==feedback.crossed+1{Circle().stroke(passed ? ink("#d9a442"):forest,lineWidth:2).padding(-1)}}.zIndex(game.running && (index==feedback.crossed || index==feedback.crossed+1) ? 2:0).scaleEffect(1+pulse*0.14).shadow(color:ink("#efbc54").opacity(pulse*0.7),radius:5).accessibilityLabel("目标 \(index)：\(id)号").accessibilityValue(passed ? "已跨越":"等待跨越")
   }
  }
 }
}

private func entryCenter(_ p:[Double])->[Double] {
 if p[1]==0{return[0.35+p[0],0.18]};if p[1]==4{return[0.35+p[0],4.50]};if p[0]==0{return[0.18,0.35+p[1]]};return[5.50,0.35+p[1]]
}
private func entryOutward(_ p:[Double])->[Double] {
 if p[1]==0{return[0,-1]};if p[1]==4{return[0,1]};if p[0]==0{return[-1,0]};return[1,0]
}
private func drawEntry(_ c:inout GraphicsContext,_ name:String,_ p:[Double],_ u:CGFloat,_ start:Bool,_ finish:Bool){
 let pt=entryCenter(p),v=entryOutward(p),center=CGPoint(x:pt[0]*u,y:pt[1]*u),horizontal=v[1] != 0
 let w=(horizontal ? 0.57:0.30)*u,h=(horizontal ? 0.29:0.52)*u,rect=CGRect(x:center.x-w/2,y:center.y-h/2,width:w,height:h)
 let shape=Path(roundedRect:rect,cornerRadius:u*0.045),red=ink("#a4472f")
 c.fill(shape,with:.color(ink(finish ? "#fff3df":"#ffe9bf")));c.stroke(shape,with:.color(finish ? red:ink("#d5ad73")),lineWidth:u*(finish ? 0.022:0.01))
 if finish{
  let bx=center.x+v[0]*(horizontal ? h:w)/2-v[0]*u*0.04,by=center.y+v[1]*(horizontal ? h:w)/2-v[1]*u*0.04
  for i in 0..<6{for row in 0..<2{let x=bx-v[1]*(Double(i)-2.5)*u*0.068+v[0]*(Double(row)-0.5)*u*0.030,y=by+v[0]*(Double(i)-2.5)*u*0.068+v[1]*(Double(row)-0.5)*u*0.030
   let rw=u*(horizontal ? 0.068:0.030),rh=u*(horizontal ? 0.030:0.068)
   c.fill(Path(CGRect(x:x-rw/2,y:y-rh/2,width:rw,height:rh)),with:.color((i+row)%2==0 ? red:.white))
  }}
 }
 c.draw(Text(name).font(.system(size:u*0.24,weight:.bold)).foregroundColor(ink("#342718")),at:center)
 if start{
  func point(_ along:Double,_ across:Double)->CGPoint {CGPoint(x:center.x+v[0]*u*along-v[1]*u*across,y:center.y+v[1]*u*along+v[0]*u*across)}
  let points=[point(0.20,0),point(0.34,0.105),point(0.34,0.045),point(0.46,0.045),point(0.46,-0.045),point(0.34,-0.045),point(0.34,-0.105)]
  var arrow=Path();arrow.addLines(points);arrow.closeSubpath();c.stroke(arrow,with:.color(.white),style:StrokeStyle(lineWidth:u*0.045,lineJoin:.round));c.fill(arrow,with:.color(forest))
 }
}
struct NativeBoard:View {
 @GestureState private var touching=false
 @State private var pressActive=false
 @State private var pressMoved=false
 @State private var pressPiece:Placement?
 @ObservedObject var game:Game
 @ObservedObject var drag:NativeDrag
 var body:some View {
  GeometryReader{geo in
   let u=min(geo.size.width/6.3,geo.size.height/5.3),w=u*5.7,h=u*4.7,pad=u*0.35
   VStack(spacing:0){
    ZStack{
     ZStack(alignment:.topLeading){
      Canvas{context,size in
       var c=context
       c.fill(Path(roundedRect:CGRect(origin:.zero,size:size),cornerRadius:18),with:.color(ink("#dfbc87")))
       for y in 0..<4{for x in 0..<5{c.fill(Path(roundedRect:CGRect(x:pad+CGFloat(x)*u+2,y:pad+CGFloat(y)*u+2,width:u-4,height:u-4),cornerRadius:7),with:.color(ink("#c6a16d")))}}
       for p in game.placed{
        drawTile(&c,game.data,p,u,CGPoint(x:pad,y:pad))
        if game.selected==p.id{let(tw,th)=game.data.dimensions(p.r);c.stroke(Path(roundedRect:CGRect(x:pad+CGFloat(p.x)*u,y:pad+CGFloat(p.y)*u,width:CGFloat(tw)*u,height:CGFloat(th)*u),cornerRadius:7),with:.color(forest),lineWidth:3)}
        let center=CGPoint(x:pad+(CGFloat(p.x)+0.20)*u,y:pad+(CGFloat(p.y)+0.20)*u)
        c.fill(Path(ellipseIn:CGRect(x:center.x-u*0.16,y:center.y-u*0.16,width:u*0.32,height:u*0.32)),with:.color(numberFill(p.id)))
        c.draw(Text("\(p.id)").font(.system(size:u*0.2,weight:.bold)).foregroundColor(numberInk(p.id)),at:center)
       }
       for p in game.placed{let below=game.data.beneath(p,game.placed).count
        if below>0,let pt=game.data.stackMarker(p,game.placed){let right=CGPoint(x:pad+pt[0]*u,y:pad+pt[1]*u),rect=CGRect(x:right.x-u*0.80,y:right.y-u*0.16,width:u*0.80,height:u*0.31),path=Path(roundedRect:rect,cornerRadius:u*0.08)
         c.fill(path,with:.color(ink("#fff3d8")));c.stroke(path,with:.color(ink("#b47a2d")),lineWidth:u*0.012)
         c.draw(Text("▱ 叠 \(below+1)").font(.system(size:u*0.17,weight:.bold)).foregroundColor(ink("#925b1c")),at:CGPoint(x:rect.midX,y:rect.midY))
        }
       }
       if let p=drag.candidate{let(tw,th)=game.data.dimensions(p.r);let rect=CGRect(x:pad+CGFloat(p.x)*u,y:pad+CGFloat(p.y)*u,width:CGFloat(tw)*u,height:CGFloat(th)*u),color = !drag.problem(game).isEmpty ? ink("#c04835"):game.placed.contains{game.data.overlaps(p,$0)} ? ink("#a96b1e"):forest;var preview=c;preview.clip(to:Path(CGRect(x:pad,y:pad,width:5*u,height:4*u)));preview.fill(Path(roundedRect:rect,cornerRadius:6),with:.color(color.opacity(0.28)));preview.stroke(Path(roundedRect:rect,cornerRadius:6),with:.color(color),lineWidth:3)}
       for(name,p)in game.data.gates{drawEntry(&c,name,p,u,name==game.startGate,name==game.finishGate)}
      }.contentShape(Rectangle()).gesture(DragGesture(minimumDistance:0,coordinateSpace:.named("game")).updating($touching){_,state,_ in state=true}.onChanged{value in
       guard !game.running else{return}
       let at=CGPoint(x:value.startLocation.x-drag.board.minX,y:value.startLocation.y-drag.board.minY)
       let x=Int(floor(at.x/u-0.35)),y=Int(floor(at.y/u-0.35))
       if !pressActive{pressActive=true;pressMoved=false;game.beginDrag();pressPiece=game.data.hit(game.placed,x,y);if let p=pressPiece{game.select(p.id)}}
       if hypot(value.translation.width,value.translation.height)>12{pressMoved=true}
       if pressMoved,drag.piece==nil,let p=pressPiece{drag.begin(game,p,value.startLocation,CGPoint(x:at.x/u-0.35-CGFloat(p.x),y:at.y/u-0.35-CGFloat(p.y)))}
       if drag.piece != nil{drag.pointer=value.location}
      }.onEnded{value in
       if drag.piece != nil{drag.finish(game)}else{
        let x=Int(floor((value.startLocation.x-drag.board.minX)/u-0.35)),y=Int(floor((value.startLocation.y-drag.board.minY)/u-0.35))
        if !pressMoved,pressPiece==nil,(0..<5).contains(x),(0..<4).contains(y){game.place(x,y)}
        game.endDrag()
       }
       pressActive=false;pressPiece=nil;pressMoved=false
      }).onChange(of:touching){_,active in if !active,pressActive{drag.cancel(game);pressActive=false;pressPiece=nil;pressMoved=false}}
      if !game.startGate.isEmpty{
       let pos=horsePosition,dir=horseDirection
       Image("horse-pawn").resizable().scaledToFit().frame(width:u*0.72,height:u*0.68).offset(x:pad+(pos[0]+dir[0]*0.36)*u-u*0.36,y:pad+(pos[1]+dir[1]*0.36)*u-u*0.34-jumpHeight).allowsHitTesting(false)
      }
      ForEach(game.data.gates.keys.sorted(),id:\.self){letter in
       let center=entryCenter(game.data.gates[letter]!)
       Button{if game.level.solveEndpoints && !game.running{game.show("gate:\(letter)")}}label:{Color.clear.frame(width:u*0.70,height:u*0.70).contentShape(Rectangle())}.buttonStyle(.plain).accessibilityLabel("入口 \(letter)").offset(x:(center[0]-0.35)*u,y:(center[1]-0.35)*u)
      }
      if !game.running,(drag.piece==nil || drag.fromControls),let piece=game.placed.first(where:{$0.id==game.selected}){NativeBoardPieceActions(game:game,drag:drag,piece:piece,unit:u)}
     }.frame(width:w,height:h).background{GeometryReader{frame in Color.clear.preference(key:BoardFrameKey.self,value:frame.frame(in:.named("game")))}}.onPreferenceChange(BoardFrameKey.self){drag.board=$0}
    }.frame(width:u*6.3,height:u*5.3)
   }.frame(maxWidth:.infinity,maxHeight:.infinity)
  }
 }
 private var cursor:Double {min(game.progress*Double(game.route.count),Double(game.route.count)-0.00001)}
 private func onRoute(_ t:Double)->[Double]{let c=max(0,min(t,Double(game.route.count)-0.00001));return game.data.position(game.route[Int(c)],c.truncatingRemainder(dividingBy:1))}
 private var horsePosition:[Double]{guard game.running,!game.route.isEmpty else{return game.data.gates[game.startGate] ?? [-1,-1]};return onRoute(cursor)}
 private var horseDirection:[Double]{
  if game.running && !game.route.isEmpty{let a=onRoute(cursor-0.025),b=onRoute(cursor+0.025),length=max(0.0001,hypot(b[0]-a[0],b[1]-a[1]));return[(b[0]-a[0])/length,(b[1]-a[1])/length]}
  guard let p=game.data.gates[game.startGate]else{return[1,0]};let v=entryOutward(p);return[-v[0],-v[1]]
 }
 private var jumpHeight:Double{guard game.running,!game.route.isEmpty else{return 0};let step=game.route[Int(cursor)];guard step.jump != nil else{return 0};let road=game.data.road(step),local=cursor.truncatingRemainder(dividingBy:1),along=step.from==road.from ? local:1-local;return sin(Double.pi*max(0,1-abs(along-(road.jumpAt ?? 0.5))*8))*10}
}
