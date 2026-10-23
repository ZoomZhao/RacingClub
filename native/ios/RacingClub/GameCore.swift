import Foundation

struct Placement:Codable,Equatable {let id:Int;var x:Int;var y:Int;var r:Int}
struct RoadPath:Decodable {let from:Int;let to:Int;let jump:Int?;let samples:[[Double]];let jumpAt:Double?;let length:Double}
struct Piece:Decodable {let id:Int;let color:String;let ports:[[Double]];let samples:[[Double]];let gate:[Double];let paths:[RoadPath]}
struct InventoryPiece:Decodable {let id:Int;let color:String}
struct Difficulty:Decodable,Identifiable {let id:String;let label:String;let originalLabel:String;let color:String;let firstLevel:Int;let lastLevel:Int}
struct Level:Decodable,Identifiable {
 let id:Int;let title:String;let difficultyId:String;let start:String;let finish:String;let sequence:[Int];let solution:[Placement];let solutionStart:String;let solutionFinish:String
 var number:String {String(format:"%02d",id)}
 var requiredPieces:[Int] {var seen=Set<Int>();return sequence.filter{seen.insert($0).inserted}}
 var solveEndpoints:Bool {start.isEmpty && finish.isEmpty}
 enum CodingKeys:String,CodingKey {case id,title,difficultyId,start,finish,sequence,solution,solutionStart,solutionFinish}
 init(from decoder:Decoder)throws {
  let c=try decoder.container(keyedBy:CodingKeys.self)
  id=try c.decode(Int.self,forKey:.id);title=try c.decode(String.self,forKey:.title);difficultyId=try c.decode(String.self,forKey:.difficultyId)
  start=try c.decodeIfPresent(String.self,forKey:.start) ?? "";finish=try c.decodeIfPresent(String.self,forKey:.finish) ?? ""
  sequence=try c.decode([Int].self,forKey:.sequence);solution=try c.decode([Placement].self,forKey:.solution)
  solutionStart=try c.decode(String.self,forKey:.solutionStart);solutionFinish=try c.decode(String.self,forKey:.solutionFinish)
 }
}
struct GameData:Decodable {
 let difficulties:[Difficulty];let pieces:[Piece];let gates:[String:[Double]];let levels:[Level];let inventory:[InventoryPiece]
 func rotate(_ p:[Double],_ r:Int)->[Double] {switch r {case 1:return[1-p[1],p[0]];case 2:return[2-p[0],1-p[1]];case 3:return[p[1],2-p[0]];default:return p}}
 func dimensions(_ r:Int)->(Int,Int) {r%2==0 ? (2,1):(1,2)}
 func ports(_ p:Placement)->[[Double]] {pieces.first{$0.id==p.id}!.ports.map{let q=rotate($0,p.r);return[q[0]+Double(p.x),q[1]+Double(p.y)]}}
 func fits(_ p:Placement)->Bool {guard pieces.contains(where:{$0.id==p.id}),(0...3).contains(p.r)else{return false};let(w,h)=dimensions(p.r);return p.x>=0 && p.y>=0 && p.x+w<=5 && p.y+h<=4}
 func overlaps(_ p:Placement,_ o:Placement)->Bool {let(w,h)=dimensions(p.r),(ow,oh)=dimensions(o.r);return p.id != o.id && p.x<o.x+ow && o.x<p.x+w && p.y<o.y+oh && o.y<p.y+h}
 func hasOverlap(_ all:[Placement])->Bool {all.enumerated().contains{i,p in all.dropFirst(i+1).contains{overlaps(p,$0)}}}
 func turnedInside(_ p:Placement,_ turns:Int)->Placement {let r=((p.r+turns)%4+4)%4,(w,h)=dimensions(r);return Placement(id:p.id,x:min(max(0,p.x),5-w),y:min(max(0,p.y),4-h),r:r)}
 /// A first board tap from the tray turns once when needed, then hugs the nearest edge.
 func initialPlacement(_ p:Placement)->Placement {fits(p) || !pieces.contains(where:{$0.id==p.id}) || !(0...3).contains(p.r) || !(0...4).contains(p.x) || !(0...3).contains(p.y) ? p:turnedInside(p,1)}
 func hit(_ all:[Placement],_ x:Int,_ y:Int)->Placement? {all.last{p in let(w,h)=dimensions(p.r);return(p.x..<p.x+w).contains(x) && (p.y..<p.y+h).contains(y)}}
 func coveredBy(_ p:Placement,_ all:[Placement])->[Placement] {guard let i=all.firstIndex(where:{$0.id==p.id})else{return[]};return all.dropFirst(i+1).filter{overlaps(p,$0)}}
 func beneath(_ p:Placement,_ all:[Placement])->[Placement] {all.prefix(while:{$0.id != p.id}).filter{overlaps(p,$0)}}
 func stackMarker(_ p:Placement,_ all:[Placement])->[Double]? {let(w,h)=dimensions(p.r);for y in p.y..<p.y+h{for x in stride(from:p.x+w-1,through:p.x,by:-1){if hit(all,x,y)?.id==p.id{return[Double(x)+0.92,Double(y)+0.82]}}};return nil}
 func trace(_ level:Level,_ placed:[Placement],start:String="",finish:String="")->[RouteStep]? {
  guard placed.count==level.requiredPieces.count,Set(placed.map(\.id)).count==placed.count,placed.allSatisfy({level.sequence.contains($0.id)&&fits($0)}),!hasOverlap(placed)else{return nil}
  let fromGate=level.start.isEmpty ? start:level.start,toGate=level.finish.isEmpty ? finish:level.finish
  for gate in fromGate.isEmpty ? gates.keys.sorted():[fromGate] {
   guard let pt=gates[gate],let p=placed.first(where:{ports($0).contains(pt)}),let entry=ports(p).firstIndex(of:pt)else{continue}
   var seen=Set<String>()
   func visit(_ p:Placement,_ from:Int,_ index:Int)->[RouteStep]? {
    guard seen.insert("\(p.id):\(from):\(index)").inserted else{return nil}
    let piece=pieces.first{$0.id==p.id}!
    for(i,path)in piece.paths.enumerated()where path.from==from || path.to==from {
     if let jump=path.jump,index>=level.sequence.count || level.sequence[index] != jump {continue}
     let nextIndex=index+(path.jump==nil ? 0:1),out=path.from==from ? path.to:path.from
     let step=RouteStep(piece:p,from:from,to:out,pathIndex:i,jump:path.jump),end=ports(p)[out]
     if let reached=gates.first(where:{$0.value==end})?.key {
      if nextIndex==level.sequence.count && (toGate.isEmpty || toGate==reached){return[step]}
     }else if let next=placed.first(where:{$0.id != p.id && ports($0).contains(end)}),let port=ports(next).firstIndex(of:end),let rest=visit(next,port,nextIndex){return[step]+rest}
    }
    return nil
   }
   if let route=visit(p,entry,0){return route}
  }
  return nil
 }
 func road(_ step:RouteStep)->RoadPath {pieces.first{$0.id==step.piece.id}!.paths[step.pathIndex]}
 func crossingFeedback(_ route:[RouteStep],_ progress:Double)->(crossed:Int,pulse:Double) {
  let cursor=min(1,max(0,progress))*Double(route.count);var count=0;var last:Double?
  for(i,step)in route.enumerated() where step.jump != nil {
   let path=road(step),at=path.jumpAt ?? 0.5,crossing=Double(i)+(step.from==path.from ? at:1-at)
   if cursor>=crossing{count+=1;last=crossing}
  }
  return(count,last.map{max(0,sin(Double.pi*min(1,max(0,(cursor-$0)*1350/600))))} ?? 0)
 }
 func position(_ step:RouteStep,_ progress:Double)->[Double] {let path=road(step),samples=path.samples;let t=min(1,max(0,step.from==path.from ? progress:1-progress))*Double(samples.count-1);let i=min(Int(t),samples.count-2),f=t-Double(i),a=samples[i],b=samples[i+1];let pt=rotate([a[0]+(b[0]-a[0])*f,a[1]+(b[1]-a[1])*f],step.piece.r);return[pt[0]+Double(step.piece.x),pt[1]+Double(step.piece.y)]}
}
struct RouteStep {let piece:Placement;let from:Int;let to:Int;let pathIndex:Int;let jump:Int?}
struct Score:Codable,Identifiable {var id=UUID();let level:Int;let millis:Int;let moves:Int;let hints:Int;let timestamp:Date}
protocol LeaderboardRepository:AnyObject {func list(level:Int)->[Score];func save(_ score:Score)}
/// Scores are decoded once; reads come from memory and only writes touch UserDefaults.
final class LocalLeaderboard:LeaderboardRepository {
 private let defaults:UserDefaults;private let key="scores-v1";private var scores:[Score]
 init(defaults:UserDefaults = .standard){self.defaults=defaults;scores=defaults.data(forKey:key).flatMap{try?JSONDecoder().decode([Score].self,from:$0)} ?? []}
 func list(level:Int)->[Score] {Array(scores.filter{$0.level==level}.sorted{a,b in if(a.hints==0) != (b.hints==0){return a.hints==0};if a.millis != b.millis{return a.millis<b.millis};return a.moves<b.moves}.prefix(10))}
 func save(_ score:Score) {scores.append(score);scores=scores.filter{$0.level != score.level}+list(level:score.level);if let d=try?JSONEncoder().encode(scores){defaults.set(d,forKey:key)}}
}
struct ProgressRecord:Codable,Equatable {let placed:[Placement];let moves:Int;let hints:Int;let elapsed:Double;let finished:Bool;let start:String?;let finish:String?}
protocol ProgressStore:AnyObject {func load(level:Int)->ProgressRecord?;func save(_ record:ProgressRecord,level:Int);var inProgressLevels:Set<Int> {get}}
/// Keeps an in-memory summary for the home grid; per-level records stay in UserDefaults.
final class UserDefaultsProgressStore:ProgressStore {
 private let defaults:UserDefaults;private let levelIds:[Int];private(set) var inProgressLevels:Set<Int>=[]
 init(defaults:UserDefaults = .standard,levels:[Int]){self.defaults=defaults;levelIds=levels;inProgressLevels=Set(levels.filter{id in decode(id).map{!$0.finished && !$0.placed.isEmpty} ?? false})}
 private func decode(_ level:Int)->ProgressRecord? {defaults.data(forKey:"level-\(level)").flatMap{try?JSONDecoder().decode(ProgressRecord.self,from:$0)}}
 func load(level:Int)->ProgressRecord? {decode(level)}
 func save(_ record:ProgressRecord,level:Int){
  if let d=try?JSONEncoder().encode(record){defaults.set(d,forKey:"level-\(level)")}
  if !record.finished && !record.placed.isEmpty{inProgressLevels.insert(level)}else{inProgressLevels.remove(level)}
 }
}
