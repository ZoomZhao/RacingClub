import Foundation
import Combine

/// Runs `action` after a delay and returns a cancel closure. Tests inject a manual clock.
typealias SessionScheduler = (_ seconds:Double,_ action:@escaping @MainActor ()->Void)->(()->Void)

/// One level in play: placement, endpoints, clock, automatic route run and score.
/// Pure Foundation/Combine so the whole state machine runs in macOS command-line tests.
@MainActor final class Game:ObservableObject {
 enum RunState:Equatable {case idle,pending,running,finished}
 static let runDelay=0.25
 static let secondsPerStep=1.35
 let data:GameData;let repository:any LeaderboardRepository;let store:any ProgressStore
 private let now:()->Double;private let schedule:SessionScheduler
 @Published var screen="home"
 @Published private(set) var level:Level
 @Published private(set) var placed:[Placement]=[]
 @Published private(set) var selected:Int?
 @Published private(set) var moves=0
 @Published private(set) var hints=0
 @Published var message=""
 @Published private(set) var runState=RunState.idle
 @Published private(set) var dialog=""
 @Published private(set) var elapsed=0.0
 @Published var selectedDifficultyId="starter"
 @Published private(set) var turnMode=TurnMode.both
 @Published private(set) var chosenStart=""
 @Published private(set) var chosenFinish=""
 @Published private(set) var route:[RouteStep]=[]
 @Published private(set) var runStartedAt:Double?
 @Published private(set) var solvedLevels:Set<Int>=[]
 @Published private(set) var inProgressLevels:Set<Int>=[]
 @Published private(set) var bestMillis:[Int:Int]=[:]
 private(set) var foreground=true
 private(set) var dragging=false
 /// Published so the clock label can pause its own timeline; changes only on start/stop.
 @Published private var started:Double?
 var clockRunning:Bool {started != nil}
 private var history:[(placed:[Placement],moves:Int,hints:Int,start:String,finish:String)]=[]
 private var generation=0,nextToken=0,pendingToken:Int?,runToken:Int?
 private var cancelPending:(()->Void)?,cancelFinish:(()->Void)?

 init(data:GameData,repository:any LeaderboardRepository,store:any ProgressStore,now:@escaping ()->Double,schedule:@escaping SessionScheduler){
  self.data=data;self.repository=repository;self.store=store;self.now=now;self.schedule=schedule;level=data.levels[0]
  for l in data.levels{if let best=repository.list(level:l.id).first{solvedLevels.insert(l.id);bestMillis[l.id]=best.millis}}
  inProgressLevels=store.inProgressLevels
 }
 /// App default: bundled data, UserDefaults storage and the monotonic uptime clock.
 convenience init(){
  let url=Bundle.main.url(forResource:"game-data",withExtension:"json")!
  let data=try!JSONDecoder().decode(GameData.self,from:Data(contentsOf:url))
  self.init(data:data,repository:LocalLeaderboard(),store:UserDefaultsProgressStore(levels:data.levels.map(\.id)),now:{ProcessInfo.processInfo.systemUptime},schedule:Game.taskScheduler)
  if let saved=UserDefaults.standard.string(forKey:"turn-mode"),let mode=TurnMode(rawValue:saved){turnMode=mode}
 }
 static let taskScheduler:SessionScheduler={seconds,action in
  let task=Task{@MainActor in try? await Task.sleep(for:.milliseconds(Int(seconds*1000)));if !Task.isCancelled{action()}}
  return{task.cancel()}
 }

 var running:Bool {runState == .running}
 var startGate:String {level.start.isEmpty ? chosenStart:level.start}
 var finishGate:String {level.finish.isEmpty ? chosenFinish:level.finish}
 /// Unplaced pieces always enter at 0°; placed pieces keep their own rotation.
 var rotation:Int {placed.first{$0.id==selected}?.r ?? 0}
 var time:Double {elapsed+(started.map{now()-$0} ?? 0)}
 var millis:Int {Int((time*1000).rounded())}
 var isRunPending:Bool {runState == .pending}
 var runDuration:Double {Double(route.count)*Game.secondsPerStep}
 func runProgress()->Double {guard running,let s=runStartedAt,runDuration>0 else{return runState == .finished ? 1:0};return min(1,max(0,(now()-s)/runDuration))}
 func solved(_ id:Int)->Bool {solvedLevels.contains(id)}
 func inProgress(_ id:Int)->Bool {!solved(id) && inProgressLevels.contains(id)}
 private var canEdit:Bool {screen=="game" && !running}

 // MARK: clock
 private func suspendClock(){if let s=started{elapsed+=now()-s};started=nil}
 private func resumeClock(){if started==nil && screen=="game" && runState != .running && runState != .finished && dialog.isEmpty && foreground{started=now()}}
 func appDidEnterBackground(){foreground=false;cancelPendingRun();suspendClock();save()}
 func appDidBecomeActive(){
  foreground=true
  if running,let s=runStartedAt,now()-s>=runDuration{finishRun()}
  resumeClock();checkRoute()
 }

 // MARK: persistence
 func save(){
  guard screen != "home" else{return}
  store.save(ProgressRecord(placed:placed,moves:moves,hints:hints,elapsed:time,finished:running || screen=="win",start:chosenStart,finish:chosenFinish),level:level.id)
  if inProgressLevels != store.inProgressLevels{inProgressLevels=store.inProgressLevels}
 }
 private func leave(){suspendClock();cancelPendingRun();cancelFinish?();cancelFinish=nil;runToken=nil;save();generation+=1}
 func start(_ l:Level){
  leave()
  level=l;selectedDifficultyId=l.difficultyId;screen="game";runState = .idle;dialog="";placed=[];moves=0;hints=0;elapsed=0;history=[];route=[];runStartedAt=nil;chosenStart="";chosenFinish="";message=""
  if let p=store.load(level:l.id),!p.finished{
   for tile in p.placed where l.sequence.contains(tile.id) && data.fits(tile){placed.removeAll{$0.id==tile.id};placed.append(tile)}
   moves=max(0,p.moves);hints=max(0,p.hints);elapsed=max(0,p.elapsed)
   if l.solveEndpoints{chosenStart=validGate(p.start);chosenFinish=validGate(p.finish)}
  }
  selected=l.requiredPieces.first{n in !placed.contains{$0.id==n}}
  resumeClock();checkRoute()
 }
 private func validGate(_ letter:String?)->String {letter.flatMap{data.gates[$0] != nil ? $0:nil} ?? ""}
 func home(){leave();runState = .idle;dialog="";screen="home"}

 // MARK: editing
 func select(_ id:Int){
  guard canEdit,level.requiredPieces.contains(id) else{return};selected=id;message=""
  if let p=placed.first(where:{$0.id==id}),placed.last?.id != id{placed.removeAll{$0.id==id};placed.append(p);save()}
 }
 func deselect(){selected=nil}
 private func remember(){history.append((placed,moves,hints,chosenStart,chosenFinish))}
 /// Places the selected piece; used only for rack pieces that are not on the board yet.
 func place(_ x:Int,_ y:Int){guard let id=selected else{return};let p=Placement(id:id,x:x,y:y,r:rotation);drop(placed.contains{$0.id==id} ? p:data.initialPlacement(p))}
 /// Commits a specific piece, independent of any selection changed by another touch.
 func drop(_ p:Placement){
  guard canEdit,level.requiredPieces.contains(p.id) else{return}
  guard data.fits(p) else{message="超出棋盘边界，已保留原位";return}
  selected=p.id
  guard placed.first(where:{$0.id==p.id}) != p else{return}
  remember();placed.removeAll{$0.id==p.id};placed.append(p);moves+=1;changed()
 }
 func rotate(_ quarterTurns:Int=1){
  guard canEdit,let id=selected,let i=placed.firstIndex(where:{$0.id==id}) else{return}
  let p=data.turnedInside(placed[i],quarterTurns);remember();placed.remove(at:i);placed.append(p);moves+=1;changed()
 }
 func remove(){guard canEdit,let id=selected,placed.contains(where:{$0.id==id}) else{return};remember();placed.removeAll{$0.id==id};moves+=1;changed()}
 func undo(){guard canEdit,let last=history.popLast() else{return};placed=last.placed;moves=last.moves;hints=max(hints,last.hints);chosenStart=last.start;chosenFinish=last.finish;changed()}
 func reset(){guard canEdit else{return};remember();placed=[];chosenStart="";chosenFinish="";moves+=1;changed()}
 func setTurnMode(_ mode:TurnMode){guard turnMode != mode else{return};turnMode=mode;UserDefaults.standard.set(mode.rawValue,forKey:"turn-mode")}
 /// Clears every level's layout and completion. Best scores stay. The open board starts over.
 func clearAllProgress(){
  guard canEdit || screen=="home" else{return}
  store.clearAll();inProgressLevels=[]
  solvedLevels=Set(data.levels.compactMap{repository.list(level:$0.id).isEmpty ? nil:$0.id})
  guard screen != "home" else{return}
  history=[];placed=[];chosenStart="";chosenFinish="";selected=level.requiredPieces.first;moves=0;hints=0;elapsed=0;started=nil;message=""
  save();resumeClock()
 }
 func hint(){
  guard canEdit else{return}
  if let p=level.solution.first(where:{!placed.contains($0)}){
   remember();placed=placed.filter{$0.id != p.id && !data.overlaps(p,$0)}+[p];selected=p.id;moves+=1;hints+=1;changed();return
  }
  // From level 65 the last hint reveals both endpoints, once; an already correct answer costs nothing.
  guard level.solveEndpoints,chosenStart != level.solutionStart || chosenFinish != level.solutionFinish else{return}
  remember();chosenStart=level.solutionStart;chosenFinish=level.solutionFinish;moves+=1;hints+=1;changed()
 }
 func choose(_ letter:String,finish:Bool?=nil){
  guard canEdit,level.solveEndpoints,data.gates[letter] != nil else{return}
  let isFinish=finish ?? (dialog=="finish")
  remember();if isFinish{chosenFinish=letter}else{chosenStart=letter};moves+=1
  dialog="";resumeClock();changed()
 }
 private func changed(){message="";save();checkRoute()}

 // MARK: dialogs and gestures
 func show(_ type:String){guard screen != "home",!running else{return};cancelPendingRun();suspendClock();save();dialog=type}
 func close(){guard !dialog.isEmpty else{return};dialog="";resumeClock();checkRoute()}
 func beginDrag(){dragging=true;cancelPendingRun()}
 func endDrag(){guard dragging else{return};dragging=false;checkRoute()}

 // MARK: automatic run
 private var mayRun:Bool {screen=="game" && runState == .idle && dialog.isEmpty && foreground && !dragging}
 private func currentRoute()->[RouteStep]? {
  if level.solveEndpoints && (startGate.isEmpty || finishGate.isEmpty){return nil}
  return data.trace(level,placed,start:startGate,finish:finishGate)
 }
 private func cancelPendingRun(){cancelPending?();cancelPending=nil;pendingToken=nil;if runState == .pending{runState = .idle}}
 /// A solved board waits briefly, then re-checks every condition before the clock stops.
 func checkRoute(){
  cancelPendingRun()
  guard mayRun,currentRoute() != nil else{return}
  nextToken+=1;let token=nextToken,gen=generation
  pendingToken=token;runState = .pending
  cancelPending=schedule(Game.runDelay){[weak self] in self?.firePendingRun(token,gen)}
 }
 private func firePendingRun(_ token:Int,_ gen:Int){
  guard pendingToken==token else{return}
  cancelPending=nil;pendingToken=nil;runState = .idle
  guard gen==generation,mayRun,let valid=currentRoute() else{return}
  beginRun(valid)
 }
 private func beginRun(_ valid:[RouteStep]){
  suspendClock();runState = .running;route=valid;runStartedAt=now();message=""
  let score=Score(level:level.id,millis:Int((elapsed*1000).rounded()),moves:moves,hints:hints,timestamp:Date())
  repository.save(score);solvedLevels.insert(level.id);bestMillis[level.id]=repository.list(level:level.id).first?.millis ?? score.millis
  save()
  nextToken+=1;let token=nextToken;runToken=token
  cancelFinish=schedule(runDuration){[weak self] in if self?.runToken==token{self?.finishRun()}}
 }
 /// Natural end or "skip": the score was recorded once when the run began.
 func finishRun(){guard running else{return};cancelFinish?();cancelFinish=nil;runToken=nil;runState = .finished;screen="win";save()}
}
