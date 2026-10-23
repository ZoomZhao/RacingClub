import Foundation
import ImageIO

// Headless iOS session tests on macOS: real game-data.json, shared JSON scenarios,
// a manual monotonic clock and in-memory storage. Arguments: game-data, scenarios, Resources dir.
@MainActor final class ManualClock {
 var now=0.0
 private var jobs:[(id:Int,at:Double,action:@MainActor ()->Void)]=[]
 private var nextId=0
 var scheduler:SessionScheduler {{[unowned self] seconds,action in
  nextId+=1;let id=nextId;jobs.append((id,now+seconds,action))
  return{[weak self] in self?.jobs.removeAll{$0.id==id}}
 }}
 func advance(ms:Int){
  let target=now+Double(ms)/1000
  while let job=jobs.filter({$0.at<=target+1e-9}).min(by:{$0.at<$1.at}){jobs.removeAll{$0.id==job.id};now=max(now,job.at);job.action()}
  now=target
 }
}
final class MemoryStore:ProgressStore {
 var records:[Int:ProgressRecord]=[:]
 var inProgressLevels:Set<Int> {Set(records.filter{!$0.value.finished && !$0.value.placed.isEmpty}.keys)}
 func load(level:Int)->ProgressRecord? {records[level]}
 func save(_ record:ProgressRecord,level:Int){records[level]=record}
}
final class MemoryLeaderboard:LeaderboardRepository {
 var scores:[Score]=[]
 func list(level:Int)->[Score] {scores.filter{$0.level==level}.sorted{$0.millis<$1.millis}}
 func save(_ score:Score){scores.append(score)}
}
struct ScenarioFile:Decodable {let schemaVersion:Int;let scenarios:[Scenario]}
struct Scenario:Decodable {let id:String;let level:Int;let fixture:String?;let actions:[Action];let expect:Expect}
struct Action:Decodable {let op:String;let ms:Int?;let letter:String?}
struct Expect:Decodable {let millis:Int?;let moves:Int?;let hints:Int?;let start:String?;let finish:String?}

func check(_ ok:Bool,_ message:@autoclosure ()->String,line:Int=#line){if !ok{FileHandle.standardError.write("FAIL line \(line): \(message())\n".data(using:.utf8)!);exit(1)}}

@MainActor struct Harness {
 let clock=ManualClock(),store=MemoryStore(),scores=MemoryLeaderboard();let game:Game
 init(_ data:GameData){game=Game(data:data,repository:scores,store:store,now:{[clock] in clock.now},schedule:clock.scheduler)}
 /// "solution-pieces": every correct piece placed, no moves, hints or endpoints — via the real restore path.
 func start(_ id:Int,fixture:String?=nil,start:String?=nil,finish:String?=nil){
  let level=game.data.levels.first{$0.id==id}!
  if let fixture{check(fixture=="solution-pieces","unknown fixture \(fixture)");store.records[id]=ProgressRecord(placed:level.solution,moves:0,hints:0,elapsed:0,finished:false,start:start,finish:finish)}
  game.start(level)
 }
}

@main struct SessionTests {
 @MainActor static func main() throws {
  let args=CommandLine.arguments
  let data=try JSONDecoder().decode(GameData.self,from:Data(contentsOf:URL(fileURLWithPath:args[1])))
  let file=try JSONDecoder().decode(ScenarioFile.self,from:Data(contentsOf:URL(fileURLWithPath:args[2])))
  check(file.schemaVersion==1,"scenario schemaVersion \(file.schemaVersion)")
  check(!file.scenarios.isEmpty,"no shared scenarios")
  for s in file.scenarios{
   let h=Harness(data);h.start(s.level,fixture:s.fixture)
   for a in s.actions{
    switch a.op{
    case "advance":h.clock.advance(ms:a.ms ?? 0)
    case "pause":h.game.appDidEnterBackground()
    case "resume":h.game.appDidBecomeActive()
    case "hint":h.game.hint()
    case "undo":h.game.undo()
    case "choose-start":h.game.choose(a.letter ?? "",finish:false)
    case "choose-finish":h.game.choose(a.letter ?? "",finish:true)
    default:check(false,"\(s.id): unknown op \(a.op)")
    }
   }
   let g=h.game,e=s.expect
   if let v=e.millis{check(g.millis==v,"\(s.id): millis \(g.millis) != \(v)")}
   if let v=e.moves{check(g.moves==v,"\(s.id): moves \(g.moves) != \(v)")}
   if let v=e.hints{check(g.hints==v,"\(s.id): hints \(g.hints) != \(v)")}
   if let v=e.start{check(g.chosenStart==v,"\(s.id): start '\(g.chosenStart)' != '\(v)'")}
   if let v=e.finish{check(g.chosenFinish==v,"\(s.id): finish '\(g.chosenFinish)' != '\(v)'")}
  }
  try iosSpecific(data,resources:URL(fileURLWithPath:args[3]))
  print("Swift session: \(file.scenarios.count) shared scenarios plus run timing, dialogs, restore, orientation, cache and art checks passed.")
 }

 @MainActor static func iosSpecific(_ data:GameData,resources:URL) throws {
  // A dialog opened inside the 250 ms window cancels the run; nothing is scored while it is open.
  do{
   let h=Harness(data),g=h.game;h.start(3,fixture:"solution-pieces")
   check(g.isRunPending && !g.running,"restored solution should wait 250 ms")
   h.clock.advance(ms:100);g.show("help")
   check(!g.isRunPending && !g.clockRunning,"dialog must cancel the pending run and stop the clock")
   h.clock.advance(ms:5000)
   check(!g.running && h.scores.scores.isEmpty && g.millis==100,"no score while dialog open")
   g.close();check(g.isRunPending,"closing re-checks the route")
   h.clock.advance(ms:249);check(!g.running,"still waiting")
   h.clock.advance(ms:1);check(g.running && h.scores.scores.count==1 && h.scores.scores[0].millis==350,"run starts and scores once at 350 ms, got \(h.scores.scores.map(\.millis))")
   check(!g.clockRunning && h.store.records[3]!.finished,"running stops the clock and saves finished")
   g.show("rank");check(g.dialog.isEmpty,"no dialogs during the run")
   h.clock.advance(ms:Int(g.runDuration*1000)+10)
   check(g.screen=="win" && g.runState == .finished && h.scores.scores.count==1,"natural finish, single score")
   g.finishRun();check(h.scores.scores.count==1 && g.solved(3),"skip after finish does nothing")
  }
  // Skip during the run, backgrounding and navigation.
  do{
   let h=Harness(data),g=h.game;h.start(3,fixture:"solution-pieces")
   g.appDidEnterBackground();check(!g.isRunPending,"background cancels pending run")
   h.clock.advance(ms:1000);check(!g.running && h.scores.scores.isEmpty,"no run in background")
   g.appDidBecomeActive();h.clock.advance(ms:250);check(g.running,"run after returning")
   g.finishRun();check(g.screen=="win" && h.scores.scores.count==1,"skip records nothing new")
   h.clock.advance(ms:60_000);check(h.scores.scores.count==1 && g.screen=="win","finish timer cancelled by skip")
   h.start(4,fixture:"solution-pieces");g.home();h.clock.advance(ms:1000)
   check(g.screen=="home" && h.scores.scores.count==1,"leaving the level cancels its pending run")
   h.start(4,fixture:"solution-pieces");g.beginDrag();h.clock.advance(ms:1000)
   check(!g.running,"no run while a finger is down");g.endDrag();h.clock.advance(ms:250);check(g.running,"run after the drag ends")
  }
  // Endpoint choice validation and restore validation.
  do{
   let h=Harness(data),g=h.game;h.start(65)
   g.show("finish");g.choose("B");check(g.chosenFinish=="B" && g.dialog.isEmpty && g.moves==1,"dialog choose sets finish")
   g.choose("A",finish:false);g.choose("A",finish:true);check(g.chosenStart=="A" && g.chosenFinish=="A","both ends may be the same")
   h.start(3);g.choose("A",finish:false);check(g.chosenStart=="" && g.moves==0,"fixed-endpoint level ignores choices")
   h.store.records[65]=ProgressRecord(placed:[],moves:2,hints:0,elapsed:1,finished:false,start:"Z",finish:"B")
   h.start(65);check(g.chosenStart=="" && g.chosenFinish=="B" && g.moves==2,"restore drops unknown letters")
   h.store.records[3]=ProgressRecord(placed:[],moves:0,hints:0,elapsed:0,finished:false,start:"A",finish:"B")
   h.start(3);check(g.chosenStart=="" && g.startGate=="E","fixed endpoints are never overridden")
   let l65=data.levels.first{$0.id==65}!
   h.start(65,fixture:"solution-pieces",start:l65.solutionStart,finish:l65.solutionFinish)
   g.show("help");g.hint();check(g.hints==0,"already-correct endpoints cost nothing")
  }
  // Placement hint is undoable, hint marker stays.
  do{
   let h=Harness(data),g=h.game;h.start(3)
   g.hint();check(g.placed.count==1 && g.hints==1 && g.moves==1,"piece hint")
   g.undo();check(g.placed.isEmpty && g.hints==1 && g.moves==0,"undo keeps hint marker")
  }
  // Orientation: rack pieces enter at 0°, placed pieces keep their own rotation; drop ignores selection.
  do{
   let h=Harness(data),g=h.game;h.start(3)
   g.select(5);g.place(0,0);g.rotate(1);check(g.placed.last!.r==1,"rotated")
   g.remove();check(g.rotation==0,"returned piece re-enters at 0°")
   g.place(0,0);check(g.placed.last!.r==0,"tap after return places at 0°")
   g.rotate(1);g.select(1);g.drop(Placement(id:5,x:3,y:0,r:1))
   check(g.placed.first{$0.id==5}==Placement(id:5,x:3,y:0,r:1) && g.selected==5,"drop commits its own piece")
   check(!g.placed.contains{$0.id==1},"selected rack piece untouched")
   let moves=g.moves;g.drop(Placement(id:5,x:3,y:0,r:1));check(g.moves==moves,"dropping in place is not a move")
   g.drop(Placement(id:5,x:4,y:0,r:0));check(g.message.contains("超出") && g.moves==moves,"out-of-bounds drop kept")
   g.deselect();check(g.selected==nil,"deselect")
  }
  // Leaderboard reads come from memory; progress summary follows writes.
  do{
   let suite="racingclub-session-tests-\(UUID().uuidString)",defaults=UserDefaults(suiteName:suite)!
   defer{defaults.removePersistentDomain(forName:suite)}
   let board=LocalLeaderboard(defaults:defaults)
   board.save(Score(level:7,millis:900,moves:5,hints:0,timestamp:Date()))
   check(LocalLeaderboard(defaults:defaults).list(level:7).count==1,"scores persisted")
   defaults.removeObject(forKey:"scores-v1")
   check(board.list(level:7).count==1,"list served from memory, not UserDefaults")
   for i in 0..<12{board.save(Score(level:7,millis:1000+i,moves:1,hints:0,timestamp:Date()))}
   check(board.list(level:7).count==10 && board.list(level:7)[0].millis==900,"ten best per level")
   let store=UserDefaultsProgressStore(defaults:defaults,levels:data.levels.map(\.id))
   check(store.inProgressLevels.isEmpty,"fresh summary")
   store.save(ProgressRecord(placed:[Placement(id:1,x:0,y:0,r:0)],moves:1,hints:0,elapsed:1,finished:false,start:nil,finish:nil),level:2)
   check(store.inProgressLevels==[2] && UserDefaultsProgressStore(defaults:defaults,levels:data.levels.map(\.id)).inProgressLevels==[2],"summary updated on write and rebuilt on launch")
   store.save(ProgressRecord(placed:[Placement(id:1,x:0,y:0,r:0)],moves:1,hints:0,elapsed:1,finished:true,start:nil,finish:nil),level:2)
   check(store.inProgressLevels.isEmpty,"finished level leaves summary")
  }
  // Design tokens: contrast and number ink parity with Android.
  check(contrastRatio(Design.coveredAmber,"#ffffff")>=4.5,"covered tag contrast")
  check(contrastRatio(Design.inProgressAmber,Design.plaqueFaces[1])>=4.5,"in-progress text contrast")
  for d in data.difficulties{for bg in Design.plaqueFaces+[Design.paper]{check(contrastRatio(Design.legibleInk(d.color,on:Design.plaqueFaces+[Design.paper]),bg)>=4.5,"\(d.id) label on \(bg)")}}
  check(Design.legibleInk("#426b35")=="#426b35","already legible colors are unchanged")
  check((1...10).filter(Design.numberInkIsDark)==[2,3,7],"dark number ink set")
  check([HorseHeading(dx:1,dy:0),HorseHeading(dx:1,dy:1),HorseHeading(dx:0,dy:1),HorseHeading(dx:-1,dy:1),HorseHeading(dx:-1,dy:0),HorseHeading(dx:-1,dy:-1),HorseHeading(dx:0,dy:-1),HorseHeading(dx:1,dy:-1)]==HorseHeading.allCases,"eight headings")
  // Art: every bundled bitmap loads from the Resources folder, downsampled; horses are trimmed.
  let art=ArtLibrary(directory:resources)
  for name in ArtLibrary.allNames{
   guard let image=art.image(name) else{check(false,"missing art \(name)");return}
   let limit=name.hasPrefix("horse_") ? ArtLibrary.horseSize:ArtLibrary.sizes[name]!
   check(max(image.width,image.height)<=limit,"\(name) not downsampled")
  }
  for heading in HorseHeading.allCases{
   let trimmed=art.horse(heading)!,full=CGImageSourceCreateWithURL(resources.appendingPathComponent("\(heading.asset).png") as CFURL,nil)!
   let props=CGImageSourceCopyPropertiesAtIndex(full,0,nil) as! [CFString:Any],w=props[kCGImagePropertyPixelWidth] as! Int,h=props[kCGImagePropertyPixelHeight] as! Int
   let scale=Double(ArtLibrary.horseSize)/Double(max(w,h))
   check(Double(trimmed.width)<Double(w)*scale-1 || Double(trimmed.height)<Double(h)*scale-1,"\(heading.asset) transparent margin trimmed")
  }
 }
}
