import Foundation

struct Placement:Codable,Equatable {let id:Int;var x:Int;var y:Int;var r:Int}
struct RoadPath:Decodable {let from:Int;let to:Int;let jump:Int?;let samples:[[Double]];let jumpAt:Double?;let length:Double}
struct Piece:Decodable {let id:Int;let color:String;let ports:[[Double]];let samples:[[Double]];let gate:[Double];let paths:[RoadPath]}
struct InventoryPiece:Decodable {let id:Int;let color:String}
struct Difficulty:Decodable,Identifiable {let id:String;let label:String;let originalLabel:String;let color:String;let firstLevel:Int;let lastLevel:Int}
struct Level:Decodable,Identifiable {
 let id:Int;let title:String;let difficultyId:String;let start:String;let finish:String;let sequence:[Int];let solution:[Placement];let solutionStart:String;let solutionFinish:String
 var number:String {String(format:"%02d",id)}
 var requiredPieces:[Int] {Set(sequence).sorted()}
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
 func fits(_ p:Placement,_ placed:[Placement])->Bool {guard pieces.contains(where:{$0.id==p.id}),(0...3).contains(p.r)else{return false};let(w,h)=dimensions(p.r);guard p.x>=0,p.y>=0,p.x+w<=5,p.y+h<=4 else{return false};return placed.allSatisfy{o in let(ow,oh)=dimensions(o.r);return o.id==p.id || p.x+w<=o.x || o.x+ow<=p.x || p.y+h<=o.y || o.y+oh<=p.y}}
 func trace(_ level:Level,_ placed:[Placement],start:String="",finish:String="")->[RouteStep]? {
  guard placed.count==level.requiredPieces.count,Set(placed.map(\.id)).count==placed.count,placed.allSatisfy({level.sequence.contains($0.id)&&fits($0,placed)})else{return nil}
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
 func position(_ step:RouteStep,_ progress:Double)->[Double] {let path=road(step),samples=path.samples;let t=min(1,max(0,step.from==path.from ? progress:1-progress))*Double(samples.count-1);let i=min(Int(t),samples.count-2),f=t-Double(i),a=samples[i],b=samples[i+1];let pt=rotate([a[0]+(b[0]-a[0])*f,a[1]+(b[1]-a[1])*f],step.piece.r);return[pt[0]+Double(step.piece.x),pt[1]+Double(step.piece.y)]}
}
struct RouteStep {let piece:Placement;let from:Int;let to:Int;let pathIndex:Int;let jump:Int?}
struct Score:Codable,Identifiable {var id=UUID();let level:Int;let millis:Int;let moves:Int;let hints:Int;let timestamp:Date}
protocol LeaderboardRepository {func list(level:Int)->[Score];func save(_ score:Score)}
final class LocalLeaderboard:LeaderboardRepository {
 private var scores:[Score] {get{guard let d=UserDefaults.standard.data(forKey:"scores-v1")else{return[]};return(try?JSONDecoder().decode([Score].self,from:d)) ?? []}set{if let d=try?JSONEncoder().encode(newValue){UserDefaults.standard.set(d,forKey:"scores-v1")}}}
 func list(level:Int)->[Score] {Array(scores.filter{$0.level==level}.sorted{a,b in if(a.hints==0) != (b.hints==0){return a.hints==0};if a.millis != b.millis{return a.millis<b.millis};return a.moves<b.moves}.prefix(10))}
 func save(_ score:Score) {scores.append(score);scores=scores.filter{$0.level != score.level}+list(level:score.level)}
}
