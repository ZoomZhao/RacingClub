import Foundation
@main struct CoreTests {
 static func main() throws {
  let data=try JSONDecoder().decode(GameData.self,from:Data(contentsOf:URL(fileURLWithPath:CommandLine.arguments[1])))
  precondition(data.difficulties.map{$0.originalLabel} == ["STARTER","JUNIOR","EXPERT","MASTER","PUNDIT"])
  for l in data.levels {
   let difficulty=data.difficulties.first{$0.id==l.difficultyId}!
   precondition((difficulty.firstLevel...difficulty.lastLevel).contains(l.id))
   precondition(data.trace(l,l.solution,start:l.solutionStart,finish:l.solutionFinish)?.compactMap{$0.jump} == l.sequence)
   precondition(data.trace(l,Array(l.solution.dropLast())) == nil)
   precondition(data.trace(l,l.solution + [l.solution[0]]) == nil)
   for step in data.trace(l,l.solution,start:l.solutionStart,finish:l.solutionFinish)! {
    let a=data.position(step,0),b=data.position(step,1);let pp=data.ports(step.piece)
    precondition(zip(a,pp[step.from]).allSatisfy{abs($0-$1)<0.0001})
    precondition(zip(b,pp[step.to]).allSatisfy{abs($0-$1)<0.0001})
   }
  }
  precondition(!data.fits(Placement(id:5,x:0,y:3,r:1),data.levels[0].solution))
  precondition(data.inventory.count==10 && data.pieces.count==10 && data.levels.count==80)
  print("Swift native core: 80 levels, repeated jumps, branches, unknown endpoints, invalid layouts and animation endpoints passed.")
 }
}
