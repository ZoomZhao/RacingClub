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
 var startGate:String {level.start.isEmpty ? chosenStart:level.start}
 var finishGate:String {level.finish.isEmpty ? chosenFinish:level.finish}
 var route:[RouteStep]=[];private var started:Double?;private var history:[([Placement],Int,Int,String,String)]=[];private var runTask:Task<Void,Never>?
 init(){let url=Bundle.main.url(forResource:"game-data",withExtension:"json")!;data=try!JSONDecoder().decode(GameData.self,from:Data(contentsOf:url));level=data.levels[0];repository=LocalLeaderboard()}
 var time:Double {elapsed+(started.map{ProcessInfo.processInfo.systemUptime-$0} ?? 0)}
 func resume(){if started==nil && screen=="game" && !running && dialog.isEmpty {started=ProcessInfo.processInfo.systemUptime}}
 func pause(){if let start=started{elapsed+=ProcessInfo.processInfo.systemUptime-start};started=nil;save()}
 func save(){guard screen != "home" else{return};let r=ProgressRecord(placed:placed,moves:moves,hints:hints,elapsed:time,finished:running||screen=="win",start:chosenStart,finish:chosenFinish);if let d=try?JSONEncoder().encode(r){UserDefaults.standard.set(d,forKey:"level-\(level.id)")}}
 func start(_ l:Level){pause();runTask?.cancel();level=l;selectedDifficultyId=l.difficultyId;screen="game";running=false;placed=[];moves=0;hints=0;elapsed=0;history=[];route=[];chosenStart="";chosenFinish="";if let d=UserDefaults.standard.data(forKey:"level-\(l.id)"),let p=try?JSONDecoder().decode(ProgressRecord.self,from:d),!p.finished{for tile in p.placed where l.sequence.contains(tile.id)&&data.fits(tile,placed){placed.removeAll{$0.id==tile.id};placed.append(tile)};moves=p.moves;hints=p.hints;elapsed=p.elapsed;chosenStart=p.start ?? "";chosenFinish=p.finish ?? ""};selected=l.requiredPieces.first{n in !placed.contains{$0.id==n}};rotation=0;message="";resume()}
 func home(){pause();runTask?.cancel();screen="home";running=false}
 func select(_ id:Int){guard !running else{return};selected=id;rotation=placed.first{$0.id==id}?.r ?? 0;message=""}
 private func remember(){history.append((placed,moves,hints,chosenStart,chosenFinish))}
 func place(_ x:Int,_ y:Int){guard !running,let id=selected else{return};let p=Placement(id:id,x:x,y:y,r:rotation);guard data.fits(p,placed) else{message="这里重叠或越界，已保留原位";return};remember();placed.removeAll{$0.id==id};placed.append(p);moves+=1;changed()}
 func rotate(_ quarterTurns:Int=1){guard !running,let id=selected else{return};let next=((rotation+quarterTurns)%4+4)%4;if let i=placed.firstIndex(where:{$0.id==id}){var p=placed[i];p.r=next;guard data.fits(p,placed) else{message="旋转后重叠或越界，已保留原位";return};remember();placed[i]=p;moves+=1};rotation=next;changed()}
 func remove(){guard !running,let id=selected,placed.contains(where:{$0.id==id}) else{return};remember();placed.removeAll{$0.id==id};moves+=1;changed()}
 func undo(){guard !running,let last=history.popLast() else{return};placed=last.0;moves=last.1;hints=max(hints,last.2);chosenStart=last.3;chosenFinish=last.4;changed()}
 func reset(){guard !running else{return};remember();placed=[];chosenStart="";chosenFinish="";moves+=1;changed()}
 func hint(){guard !running else{return};guard let p=level.solution.first(where:{!placed.contains($0)}) else{if level.solveEndpoints{chosenStart=level.solutionStart;chosenFinish=level.solutionFinish;hints+=1;moves+=1;changed()};return};remember();placed=placed.filter{$0.id != p.id&&data.fits(p,[$0])}+[p];selected=p.id;rotation=p.r;moves+=1;hints+=1;message="";changed()}
 func changed(){message="";save();runTask?.cancel();guard !level.solveEndpoints || (!startGate.isEmpty && !finishGate.isEmpty),let valid=data.trace(level,placed,start:startGate,finish:finishGate) else{return};runTask=Task{try? await Task.sleep(for:.milliseconds(250));guard !Task.isCancelled else{return};pause();running=true;route=valid;progress=0;repository.save(Score(level:level.id,millis:Int(elapsed*1000),moves:moves,hints:hints,timestamp:Date()));save();let start=ProcessInfo.processInfo.systemUptime;while progress<1{guard !Task.isCancelled else{return};progress=min(1,(ProcessInfo.processInfo.systemUptime-start)/(Double(valid.count)*1.35));try? await Task.sleep(for:.milliseconds(16))};running=false;screen="win";save()}}
 func choose(_ letter:String,finish:Bool?=nil){remember();if finish ?? (dialog=="finish"){chosenFinish=letter}else{chosenStart=letter};moves+=1;close();changed()}
 func show(_ type:String){guard !running else{return};pause();dialog=type}
 func close(){dialog="";resume()}
}
private func timeString(_ seconds:Double)->String {let ms=Int(seconds*1000);return String(format:"%02d:%02d.%02d",ms/60000,ms/1000%60,ms%1000/10)}
@main struct RacingClubApp:App {@StateObject private var game=Game();@Environment(\.scenePhase) private var phase;var body:some Scene{WindowGroup{ClubView(game:game).preferredColorScheme(.light).onChange(of:phase){_,p in if p == .active{game.resume()}else{game.pause()}}}}}
struct ClubView:View {
 @ObservedObject var game:Game
 private let tick=Timer.publish(every:0.1,on:.main,in:.common).autoconnect()
 var body:some View {GeometryReader{geo in VStack(spacing:5){header;if game.screen != "home"{NativeCourseSequence(game:game,level:game.level)};if game.screen=="home"{home}else if game.screen=="win"{win}else{if geo.size.width>geo.size.height{HStack(spacing:18){NativeBoard(game:game).frame(maxWidth:.infinity,maxHeight:.infinity);workbench.frame(width:geo.size.width*0.39)}}else{VStack{NativeBoard(game:game).aspectRatio(5.7/4.7,contentMode:.fit);workbench}}}}.padding(.horizontal,18).padding(.vertical,8).background(paper).sheet(isPresented:Binding(get:{!game.dialog.isEmpty},set:{if !$0{game.close()}})){dialog}.onReceive(tick){_ in game.clockTick+=1}}
 }
 private var header:some View {HStack(spacing:12){if game.screen=="home"{Text("♞ RacingClub").font(.system(size:24,weight:.bold,design:.serif))}else{tool("chevron.left","返回"){game.home()};Text("挑战 \(game.level.number)").font(.headline);difficultyBadge(game.data.difficulties.first{$0.id==game.level.difficultyId}!);Spacer();Text(timeString(game.time)).font(.system(size:13,design:.monospaced));tool("trophy","本关排行榜"){game.show("rank")}};if game.screen=="home"{Spacer()};tool("questionmark.circle","玩法"){game.show("help")}}.foregroundStyle(forest).frame(height:38)}
 private func difficultyBadge(_ d:Difficulty)->some View {Text(d.label).font(.system(size:10,weight:.bold)).foregroundStyle(ink(d.color)).padding(.horizontal,8).padding(.vertical,4).background(ink(d.color).opacity(0.1),in:Capsule()).accessibilityLabel("难度：\(d.label) \(d.originalLabel)")}
 private var home:some View {
  let d=game.data.difficulties.first{$0.id==game.selectedDifficultyId}!
  let levels=game.data.levels.filter{$0.difficultyId==d.id}
  return HStack(spacing:18){
   ZStack(alignment:.topLeading){Image("horse-arena").resizable().scaledToFill();Text("今天，\n跃过新的挑战").font(.system(size:27,weight:.bold)).foregroundStyle(forest).padding(18)}.frame(maxWidth:.infinity,maxHeight:.infinity).clipped().clipShape(RoundedRectangle(cornerRadius:16))
   VStack(spacing:10){
    HStack{Text("马术训练场").font(.title2.bold());Spacer();Text(d.originalLabel).font(.caption.bold()).foregroundStyle(ink(d.color))}
    HStack(spacing:5){ForEach(game.data.difficulties){item in
     let active=game.selectedDifficultyId==item.id
     Button{game.selectedDifficultyId=item.id}label:{VStack(spacing:2){Text(item.label).font(.system(size:12,weight:.bold));Text(String(format:"%02d–%02d",item.firstLevel,item.lastLevel)).font(.system(size:9))}.frame(maxWidth:.infinity).padding(.vertical,6).foregroundStyle(active ? Color.white:ink(item.color)).background(active ? ink(item.color):paper,in:RoundedRectangle(cornerRadius:9))}.buttonStyle(.plain).accessibilityLabel("难度 \(item.label) \(item.originalLabel)")
    }}
    ScrollView{LazyVGrid(columns:Array(repeating:GridItem(.flexible(),spacing:7),count:4),spacing:7){ForEach(levels){level in
     Button{game.start(level)}label:{VStack(alignment:.leading,spacing:5){HStack{Text(level.number).font(.system(size:21,weight:.bold));Spacer();Text(d.label).font(.system(size:10)).foregroundStyle(ink(d.color))};Text("\(level.requiredPieces.count) 拼板 · \(level.sequence.count) 跨").font(.system(size:10)).foregroundStyle(saddle)}.padding(10).background(Color.white.opacity(0.7),in:RoundedRectangle(cornerRadius:10))}.buttonStyle(.plain)
    }}}
   }.foregroundStyle(forest).frame(maxWidth:.infinity)
  }
 }
 private var workbench:some View {
  VStack(spacing:8){
   HStack(spacing:0){
    tool("trash","移回"){game.remove()}.frame(maxWidth:.infinity)
    tool("arrow.uturn.backward","撤销"){game.undo()}.frame(maxWidth:.infinity)
    tool("lightbulb","提示"){game.hint()}.frame(maxWidth:.infinity)
    tool("arrow.clockwise","重置"){game.reset()}.frame(maxWidth:.infinity)
    tool("square.grid.2x2","全部 10 块"){game.show("inventory")}.frame(maxWidth:.infinity)
   }.disabled(game.running)
   Divider()
   ScrollView{LazyVGrid(columns:Array(repeating:GridItem(.flexible(),spacing:5),count:5),spacing:8){
    ForEach(game.level.requiredPieces,id:\.self){id in
     Button{game.select(id)}label:{
      VStack(spacing:5){
       HStack(spacing:2){badge(id);if game.placed.contains(where:{$0.id==id}){Image(systemName:"checkmark.circle.fill").font(.system(size:9)).foregroundStyle(forest)}}
       TilePreview(game:game,id:id).frame(height:70)
      }.padding(6).frame(maxWidth:.infinity).background(game.selected==id ? forest.opacity(0.1):Color(red:0.97,green:0.94,blue:0.87),in:RoundedRectangle(cornerRadius:8)).overlay{RoundedRectangle(cornerRadius:8).stroke(game.selected==id ? forest:Color.clear,lineWidth:2)}
     }.buttonStyle(.plain)
    }
   }}
   if game.message.contains("越界") || game.message.contains("重叠"){Text(game.message).font(.system(size:11)).foregroundStyle(ink("#a9442b")).frame(maxWidth:.infinity,alignment:.leading)}
   HStack(spacing:8){
    Button{game.rotate(-1)}label:{HStack(spacing:8){Image(systemName:"rotate.left").font(.system(size:23));Text("90°").font(.system(size:16,weight:.bold))}.frame(maxWidth:.infinity,minHeight:38)}.accessibilityLabel("逆时针旋转90度")
    Button{game.rotate(1)}label:{HStack(spacing:8){Image(systemName:"rotate.right").font(.system(size:23));Text("90°").font(.system(size:16,weight:.bold))}.frame(maxWidth:.infinity,minHeight:38)}.accessibilityLabel("顺时针旋转90度")
   }.buttonStyle(.borderedProminent).tint(forest).disabled(game.selected==nil || game.running)

  }.padding(12).background(Color.white.opacity(0.5),in:RoundedRectangle(cornerRadius:12))
 }
 private var win:some View {HStack(spacing:25){Image("horse-arena").resizable().scaledToFill().frame(maxWidth:.infinity,maxHeight:.infinity).clipped().clipShape(RoundedRectangle(cornerRadius:18));VStack(spacing:14){Text("漂亮，顺利通关！").font(.title.bold());Text(timeString(game.elapsed)).font(.system(size:32,design:.serif));Text("\(game.moves) 步 · \(game.hints==0 ? "独立完成":"使用提示")").font(.caption);Button(game.level.id<game.data.levels.last!.id ? "挑战下一关":"返回训练场"){if let next=game.data.levels.first(where:{$0.id>game.level.id}){game.start(next)}else{game.home()}}.buttonStyle(.borderedProminent).tint(forest);Button("查看本关排行榜"){game.show("rank")}}.frame(maxWidth:.infinity).foregroundStyle(forest)}}
 private var dialog:some View {NavigationStack{ScrollView{VStack(alignment:.leading,spacing:16){if game.dialog=="rank"{Text("挑战 \(game.level.number) · 本机排行榜").font(.title2.bold());let records=game.repository.list(level:game.level.id);if records.isEmpty{Text("还没有成绩，完成一次训练就会记录。")};ForEach(Array(records.enumerated()),id:\.element.id){i,r in HStack{Text("\(i+1)");Text(timeString(Double(r.millis)/1000)).monospacedDigit();Spacer();Text("\(r.moves) 步");Text(r.hints==0 ? "独立完成":"使用提示").font(.caption)}};Text("记录保存在此设备；线上服务尚未接入。").font(.caption)}else if game.dialog=="start" || game.dialog=="finish"{Text(game.dialog=="start" ? "选择起点":"选择终点").font(.title2.bold());HStack{ForEach(game.data.gates.keys.sorted(),id:\.self){letter in Button(letter){game.choose(letter)}.buttonStyle(.bordered)}}}else if game.dialog.hasPrefix("gate:"){let letter=String(game.dialog.dropFirst(5));Text("入口 \(letter)").font(.title2.bold());Text("设置起终点；两端可以使用同一入口。");HStack{Button("设为起点"){game.choose(letter,finish:false)}.buttonStyle(.borderedProminent);Button("设为终点"){game.choose(letter,finish:true)}.buttonStyle(.bordered)}}else if game.dialog=="inventory"{Text("全部 10 块拼板").font(.title2.bold());LazyVGrid(columns:Array(repeating:GridItem(.flexible()),count:5)){ForEach(game.data.inventory,id:\.id){p in VStack{badge(p.id);Text(game.level.sequence.contains(p.id) ? "本关使用":"本关不用").font(.caption2)}.padding(10)}}}else{Text("一条路，刚刚好的顺序").font(.title2.bold());Text("看上方障碍顺序。点右侧编号，再点棋盘左上格放置。\n\n点已有拼板可选中，再旋转或点空格移动。重叠或越界时保留原位。\n\n正确后自动停止计时，马儿沿路跑到终点。岔路须平顺转弯，无障碍支路不计跨障。同一障碍可多次跨越。第65关起，点入口字母设置起终点；两端可相同。绿色箭头指向起点，棋格标记代表终点。顶部顺序按箭头折行，全部直接展示。")}}.padding(25)}.foregroundStyle(forest).background(paper).toolbar{ToolbarItem(placement:.confirmationAction){Button("完成"){game.close()}}}}}
 private func tool(_ icon:String,_ label:String,_ action:@escaping()->Void)->some View {Button(action:action){Image(systemName:icon).font(.system(size:19)).frame(width:38,height:38)}.accessibilityLabel(label)}
 private func badge(_ id:Int)->some View {Text("\(id)").font(.system(size:12,weight:.bold)).foregroundStyle(numberInk(id)).frame(width:23,height:23).background(numberFill(id),in:Circle())}
 private func sequence(_ level:Level)->some View {NativeCourseSequence(game:game,level:level)}
}
private func drawTile(_ c:inout GraphicsContext,_ data:GameData,_ p:Placement,_ u:CGFloat,_ origin:CGPoint){let(w,h)=data.dimensions(p.r);let top=CGPoint(x:origin.x+CGFloat(p.x)*u,y:origin.y+CGFloat(p.y)*u);c.fill(Path(roundedRect:CGRect(x:top.x,y:top.y,width:CGFloat(w)*u,height:CGFloat(h)*u),cornerRadius:7),with:.color(ink("#d7b68a")));let piece=data.pieces.first{$0.id==p.id}!;for road in piece.paths {var path=Path();for(i,pt)in road.samples.enumerated(){let q=data.rotate(pt,p.r);let point=CGPoint(x:top.x+q[0]*u,y:top.y+q[1]*u);if i==0{path.move(to:point)}else{path.addLine(to:point)}};c.stroke(path,with:.color(ink("#fff2d6")),lineWidth:u*0.36)};let g=piece.gate;let q=data.rotate([g[0]/100,g[1]/100],p.r);let a=(g[2]+Double(p.r*90)) * .pi/180;let center=CGPoint(x:top.x+q[0]*u,y:top.y+q[1]*u);let dx = -sin(a)*u*0.23,dy=cos(a)*u*0.23;var gate=Path();gate.move(to:CGPoint(x:center.x-dx,y:center.y-dy));gate.addLine(to:CGPoint(x:center.x+dx,y:center.y+dy));c.stroke(gate,with:.color(ink(piece.color)),lineWidth:u*0.09)}
struct TilePreview:View {@ObservedObject var game:Game;let id:Int;var body:some View{Canvas{context,size in var c=context;let r=game.selected==id ? game.rotation:0;let(w,h)=game.data.dimensions(r);let u=min(size.width/CGFloat(w),size.height/CGFloat(h));drawTile(&c,game.data,Placement(id:id,x:0,y:0,r:r),u,CGPoint(x:(size.width-CGFloat(w)*u)/2,y:(size.height-CGFloat(h)*u)/2))}}}
private struct NativeCourseSequence:View {
 @ObservedObject var game:Game
 let level:Level
 @ScaledMetric(relativeTo:.caption) private var diameter:CGFloat=26
 var body:some View {
  ViewThatFits(in:.horizontal){
   goalRows(level.sequence.count>8 ? 2:1)
   goalRows(level.sequence.count>8 ? 3:2)
   goalRows(4)
   goalRows(5)
  }.frame(maxWidth:.infinity).padding(.vertical,5).padding(.horizontal,9).background(paper.opacity(0.96),in:RoundedRectangle(cornerRadius:10)).overlay{RoundedRectangle(cornerRadius:10).stroke(ink("#e8d4b8"),lineWidth:1)}.accessibilityElement(children:.contain).accessibilityLabel("障碍顺序")
 }
 private func rows(_ count:Int)->[[Int]] {
  let n=level.sequence.count+2,r=min(count,n)
  var next=0
  return(0..<r).map{row in let size=n/r+(row<n%r ? 1:0);defer{next+=size};return Array(next..<next+size)}
 }
 private func goalRows(_ count:Int)->some View {
  let groups=rows(count),columns=groups.map(\.count).max() ?? 1
  let width=CGFloat(columns)*diameter+CGFloat(columns-1)*10
  return VStack(spacing:0){
   ForEach(groups.indices,id:\.self){row in
    let reverse=row%2==1,indices=reverse ? Array(groups[row].reversed()):groups[row]
    HStack(spacing:0){ForEach(Array(indices.enumerated()),id:\.element){column,index in
     if column>0{Image(systemName:reverse ? "arrow.left":"arrow.right").font(.system(size:9,weight:.semibold)).foregroundStyle(saddle).frame(width:10)}
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
    let id=level.sequence[index-1]
    Text("\(id)").font(.system(size:diameter*0.57,weight:.bold)).foregroundStyle(numberInk(id)).frame(width:diameter,height:diameter).background(numberFill(id),in:Circle()).overlay{Circle().stroke(ink("#f9e8c8"),lineWidth:1)}.accessibilityLabel("目标 \(index)：\(id)号")
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
 @ObservedObject var game:Game
 var body:some View {
  GeometryReader{geo in
   let u=min(geo.size.width/6.3,(geo.size.height-18)/5.3),w=u*5.7,h=u*4.7,pad=u*0.35
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
       for(name,p)in game.data.gates{drawEntry(&c,name,p,u,name==game.startGate,name==game.finishGate)}
      }.contentShape(Rectangle()).gesture(SpatialTapGesture().onEnded{tap in
       guard !game.running else{return};let x=Int(floor((tap.location.x-pad)/u)),y=Int(floor((tap.location.y-pad)/u))
       guard(0..<5).contains(x),(0..<4).contains(y)else{return}
       if let p=game.placed.first(where:{p in let(w,h)=game.data.dimensions(p.r);return(p.x..<p.x+w).contains(x)&&(p.y..<p.y+h).contains(y)}){game.select(p.id)}else{game.place(x,y)}
      })
      if !game.startGate.isEmpty{
       let pos=horsePosition,dir=horseDirection
       Image("horse-pawn").resizable().scaledToFit().frame(width:u*0.72,height:u*0.68).offset(x:pad+(pos[0]+dir[0]*0.36)*u-u*0.36,y:pad+(pos[1]+dir[1]*0.36)*u-u*0.34-jumpHeight).allowsHitTesting(false)
      }
      ForEach(game.data.gates.keys.sorted(),id:\.self){letter in
       let center=entryCenter(game.data.gates[letter]!)
       Button{if game.level.solveEndpoints && !game.running{game.show("gate:\(letter)")}}label:{Color.clear.frame(width:u*0.70,height:u*0.70).contentShape(Rectangle())}.buttonStyle(.plain).accessibilityLabel("入口 \(letter)").offset(x:(center[0]-0.35)*u,y:(center[1]-0.35)*u)
      }
     }.frame(width:w,height:h)
    }.frame(width:u*6.3,height:u*5.3)
    HStack{
     Button{if game.level.solveEndpoints{game.show("start")}}label:{Label("起点 \(game.startGate.isEmpty ? "?":game.startGate)",systemImage:"arrow.right")}
     Spacer()
     Button{if game.level.solveEndpoints{game.show("finish")}}label:{Label("终点 \(game.finishGate.isEmpty ? "?":game.finishGate)",systemImage:"flag.checkered")}
    }.font(.system(size:9,weight:.bold)).foregroundStyle(forest).frame(width:w,height:18)
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
