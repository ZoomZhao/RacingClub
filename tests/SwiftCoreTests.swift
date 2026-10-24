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
  precondition(!data.fits(Placement(id:5,x:0,y:3,r:1)))
  for l in data.levels{
   let route=data.trace(l,l.solution,start:l.solutionStart,finish:l.solutionFinish)!;var count=0
   for(i,step)in route.enumerated() where step.jump != nil{
    let path=data.road(step),at=path.jumpAt ?? 0.5,t=(Double(i)+(step.from==path.from ? at:1-at))/Double(route.count)
    precondition(data.crossingFeedback(route,t-0.00001).crossed==count)
    count+=1;precondition(data.crossingFeedback(route,t+0.00001).crossed==count)
   }
   precondition(data.crossingFeedback(route,1).crossed==l.sequence.count)
  }
  precondition(data.levels[2].requiredPieces == [5,1,6])
  let bottom=Placement(id:1,x:0,y:0,r:0),top=Placement(id:5,x:0,y:0,r:0),stack=[bottom,top]
  precondition(data.fits(top) && data.hasOverlap(stack))
  precondition(data.hit(stack,0,0)==top && data.stackMarker(bottom,stack)==nil)
  precondition(data.coveredBy(bottom,stack)==[top] && data.beneath(top,stack)==[bottom])
  for r in 0..<4{let(w,h)=data.dimensions(r);for x in 0...5-w{for y in 0...4-h{for turn in [-1,1]{
   let placed=data.turnedInside(Placement(id:1,x:x,y:y,r:r),turn),(nw,nh)=data.dimensions(placed.r)
   precondition((0...5-nw).contains(placed.x) && (0...4-nh).contains(placed.y) && placed.r==(r+turn+4)%4,"turn stays on the board")
   // Only the bottom row and the right column have no corner that stays on the board,
   // so those turns are nudged inward and cannot be reversed. Everywhere else the
   // opposite turn returns to the same cell.
   let nudged=(r%2==0 && y==4-h) || (r%2==1 && x==5-w)
   if !nudged{precondition(data.turnedInside(placed,-turn)==Placement(id:1,x:x,y:y,r:r),"opposite turn restores")}
  }}}}
  for x in 0...2{for y in 0...1{for dir in [-1,1]{
   var p=Placement(id:1,x:x,y:y,r:0);for _ in 0..<4{p=data.turnedInside(p,dir)}
   precondition(p==Placement(id:1,x:x,y:y,r:0),"four turns return home")
  }}}
  precondition(data.inventory.count==10 && data.pieces.count==10 && data.levels.count==80)
  print("Swift native core: 80 levels, repeated jumps, branches, unknown endpoints, invalid layouts and animation endpoints passed.")
 }
}
