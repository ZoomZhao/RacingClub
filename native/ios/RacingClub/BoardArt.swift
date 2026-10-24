import SwiftUI

/// Native board drawing. Proportions follow Android BoardMaterials.kt so both apps share one look:
/// textured wooden frame, sand cells, three-layer roads, striped rails with feet, fence with six
/// openings, fixed letters, an outside start arrow and a top-down checkered finish.
func ink(_ hex:String)->Color {let c=RGB(hex:hex);return Color(red:c.r,green:c.g,blue:c.b)}
let forest=ink(Design.forest),paper=ink(Design.paper),saddle=ink(Design.saddle)
func numberFill(_ id:Int)->Color {ink(Design.numberFace(id))}
func numberInk(_ id:Int)->Color {Design.numberInkIsDark(id) ? .black:.white}

/// Textures resolved once per Canvas pass from the shared, downsampled cache.
struct BoardTextures {
 let wood:GraphicsContext.ResolvedImage?;let sand:GraphicsContext.ResolvedImage?
 init(_ c:GraphicsContext){
  wood=ArtLibrary.shared.image(ArtLibrary.wood).map{c.resolve(Image(decorative:$0,scale:1))}
  sand=ArtLibrary.shared.image(ArtLibrary.sand).map{c.resolve(Image(decorative:$0,scale:1))}
 }
}
private func rounded(_ r:CGRect,_ radius:CGFloat)->Path {Path(roundedRect:r,cornerRadius:radius)}
private func texture(_ c:GraphicsContext,_ image:GraphicsContext.ResolvedImage?,_ rect:CGRect,_ radius:CGFloat){
 guard let image else{return};var g=c;g.clip(to:rounded(rect,radius));g.draw(image,in:rect)
}
private func vertical(_ colors:[Color],_ r:CGRect)->GraphicsContext.Shading {.linearGradient(Gradient(colors:colors),startPoint:CGPoint(x:r.midX,y:r.minY),endPoint:CGPoint(x:r.midX,y:r.maxY))}
private func line(_ c:GraphicsContext,_ a:CGPoint,_ b:CGPoint,_ color:Color,_ width:CGFloat,_ cap:CGLineCap = .butt){var p=Path();p.move(to:a);p.addLine(to:b);c.stroke(p,with:.color(color),style:StrokeStyle(lineWidth:width,lineCap:cap))}

func drawTile(_ c:GraphicsContext,_ data:GameData,_ p:Placement,_ u:CGFloat,_ origin:CGPoint,_ tex:BoardTextures,selected:Bool=false,number:Bool=false,numberAvoid:[CGRect]=[],minimumNumberFont:Double=0,numberBounds:CGRect?=nil){
 guard u>0 && u.isFinite else{return}
 let(w,h)=data.dimensions(p.r),top=CGPoint(x:origin.x+CGFloat(p.x)*u,y:origin.y+CGFloat(p.y)*u)
 let face=CGRect(x:top.x+u*0.025,y:top.y+u*0.025,width:CGFloat(w)*u-u*0.05,height:CGFloat(h)*u-u*0.055),radius=u*0.07
 c.fill(rounded(face.offsetBy(dx:0,dy:u*0.045),radius),with:.color(ink("#80603f").opacity(0.26)))
 c.fill(rounded(face,radius),with:.color(ink("#a87943")))
 texture(c,tex.wood,face,radius)
 c.fill(rounded(face,radius),with:vertical([.white.opacity(0.25),.clear,ink("#95633e").opacity(0.12)],face))
 let piece=data.pieces.first{$0.id==p.id}!
 let roads=piece.paths.map{road in var path=Path();for(i,pt)in road.samples.enumerated(){let q=data.rotate(pt,p.r),point=CGPoint(x:top.x+q[0]*u,y:top.y+q[1]*u);if i==0{path.move(to:point)}else{path.addLine(to:point)}};return path}
 var clipped=c;clipped.clip(to:rounded(face,radius))
 for r in roads{clipped.stroke(r,with:.color(ink("#b99261").opacity(0.45)),lineWidth:u*0.64)}
 for r in roads{clipped.stroke(r,with:.color(ink("#f8e5be")),lineWidth:u*0.59)}
 for r in roads{clipped.stroke(r,with:.linearGradient(Gradient(colors:[ink("#fff3d8"),ink("#f5dfb6"),ink("#ffefd0")]),startPoint:top,endPoint:CGPoint(x:top.x+CGFloat(w)*u,y:top.y+CGFloat(h)*u)),lineWidth:u*0.55)}
 c.stroke(rounded(face,radius),with:.color(ink("#b07e48")),lineWidth:u*0.014)
 c.stroke(rounded(face.insetBy(dx:u*0.025,dy:u*0.025),radius*0.85),with:.color(ink("#fff1ce").opacity(0.76)),lineWidth:u*0.017)
 // Striped rail with two feet, rotated with the tile.
 let g=piece.gate,q=data.rotate([g[0]/100,g[1]/100],p.r)
 var rail=c;rail.translateBy(x:top.x+q[0]*u,y:top.y+q[1]*u);rail.rotate(by:.degrees(g[2]+Double(p.r*90)))
 rail.fill(rounded(CGRect(x:-u*0.035,y:-u*0.31,width:u*0.13,height:u*0.69),u*0.02),with:.color(ink("#765135").opacity(0.24)))
 line(rail,CGPoint(x:0,y:-u*0.29),CGPoint(x:0,y:u*0.29),ink(piece.color),u*0.075)
 for i in stride(from:1,through:5,by:2){let y = -0.29+0.58*Double(i)/7;line(rail,CGPoint(x:0,y:y*u),CGPoint(x:0,y:(y+0.58/7)*u),ink("#fffaf0"),u*0.076)}
 line(rail,CGPoint(x:-u*0.018,y:-u*0.27),CGPoint(x:-u*0.018,y:u*0.27),.white.opacity(0.38),u*0.013)
 for sign in [-1.0,1.0]{
  let foot=CGPoint(x:-u*0.095,y:sign*u*0.3-u*0.045)
  rail.fill(rounded(CGRect(x:foot.x+u*0.025,y:foot.y+u*0.035,width:u*0.19,height:u*0.10),u*0.025),with:.color(ink("#5b3a27").opacity(0.18)))
  rail.fill(rounded(CGRect(x:foot.x,y:foot.y,width:u*0.19,height:u*0.10),u*0.025),with:.color(ink(piece.color)))
  rail.fill(rounded(CGRect(x:foot.x+u*0.018,y:foot.y+u*0.009,width:u*0.15,height:u*0.022),u*0.01),with:.color(.white.opacity(0.25)))
 }
 if selected{
  let ring=face.insetBy(dx:-u*0.012,dy:-u*0.012)
  c.stroke(rounded(ring,radius),with:.color(ink("#ffefbd")),lineWidth:u*0.05)
  c.stroke(rounded(ring,radius),with:.color(forest),lineWidth:u*0.027)
 }
 if number{
  let label=roadNumberLayout(data,p,avoiding:numberAvoid,minimumFont:minimumNumberFont/Double(u),available:numberBounds)
  let center=CGPoint(x:top.x+label.center[0]*u,y:top.y+label.center[1]*u)
  let rect=CGRect(x:center.x-label.width*u/2,y:center.y-label.height*u/2,width:label.width*u,height:label.height*u)
  c.fill(rounded(rect,label.height*u*0.20),with:.color(ink(Design.roadNumberFace)))
  c.stroke(rounded(rect,label.height*u*0.20),with:.color(ink("#b89665").opacity(0.48)),lineWidth:max(0.6,u*0.006))
  let accentY=center.y+label.height*u*0.35
  line(c,CGPoint(x:center.x-label.width*u*0.24,y:accentY),CGPoint(x:center.x+label.width*u*0.24,y:accentY),ink(piece.color),label.height*u*0.065,.round)
  c.draw(Text("\(p.id)").font(.system(size:label.font*u,weight:.bold)).foregroundColor(ink(Design.roadNumberInk)),at:CGPoint(x:center.x,y:center.y-label.height*u*0.08))
 }
}

/// Static board layer; the drag preview and horse are separate layers so moving a finger
/// does not redraw textures.
func drawBoard(_ c:GraphicsContext,_ size:CGSize,_ data:GameData,placed:[Placement],selected:Int?,start:String,finish:String,actionsVisible:Bool=true,actionCount:Int=3){
 let u=size.width/5.7,origin=CGPoint(x:u*0.35,y:u*0.35),tex=BoardTextures(c)
 let outer=CGRect(x:u*0.025,y:u*0.025,width:size.width-u*0.05,height:size.height-u*0.065),rad=u*0.28
 c.fill(rounded(outer.offsetBy(dx:0,dy:u*0.055),rad),with:.color(ink("#76512e").opacity(0.17)))
 c.fill(rounded(outer,rad),with:.color(ink("#b98a55")))
 texture(c,tex.wood,outer,rad)
 c.fill(rounded(outer,rad),with:vertical([ink("#ffe1ad").opacity(0.35),.clear,ink("#a16c35").opacity(0.16)],outer))
 c.stroke(rounded(outer,rad),with:.color(ink("#c08d52")),lineWidth:u*0.025)
 c.stroke(rounded(outer.insetBy(dx:u*0.04,dy:u*0.04),rad*0.88),with:.color(ink("#fff0cc")),lineWidth:u*0.035)
 c.stroke(rounded(outer.insetBy(dx:u*0.10,dy:u*0.10),rad*0.74),with:.color(ink("#b57e41")),lineWidth:u*0.018)
 let inset=CGRect(x:origin.x-u*0.025,y:origin.y-u*0.025,width:5.05*u,height:4.05*u)
 c.fill(rounded(inset,u*0.14),with:.color(ink("#a37845")))
 for y in 0..<4{for x in 0..<5{
  let cell=CGRect(x:origin.x+CGFloat(x)*u+u*0.018,y:origin.y+CGFloat(y)*u+u*0.018,width:u*0.964,height:u*0.964)
  c.fill(rounded(cell,u*0.13),with:.color(ink("#d8b27c")))
  texture(c,tex.sand,cell,u*0.13)
  c.stroke(rounded(cell,u*0.13),with:.color(ink("#8f693d").opacity(0.20)),lineWidth:u*0.02)
  c.fill(rounded(cell,u*0.13),with:vertical([.white.opacity(0.14),.clear,ink("#8c5d31").opacity(0.10)],cell))
 }}
 c.stroke(rounded(inset,u*0.14),with:.color(ink("#f9e2b2")),lineWidth:u*0.028)
 for p in placed{
  var avoid=data.coveredBy(p,placed).map{o in let(w,h)=data.dimensions(o.r);return CGRect(x:o.x-p.x,y:o.y-p.y,width:w,height:h)}
  if !data.beneath(p,placed).isEmpty,let q=data.stackMarker(p,placed){avoid.append(CGRect(x:q[0]-Double(p.x)-0.80,y:q[1]-Double(p.y)-0.16,width:0.80,height:0.31))}
  if selected==p.id && actionsVisible{
   let a=nativeActionAnchor(p,u,placed,actionCount)
   if a.inside{avoid.append(CGRect(x:a.left/u-Double(p.x)-0.35,y:a.top/u-Double(p.y)-0.35,width:a.width/u,height:a.height/u))}
  }
  drawTile(c,data,p,u,origin,tex,selected:selected==p.id,number:true,numberAvoid:avoid)
 }
 // Only exposed top faces get a stack badge. Fully covered pieces stay reachable in the rack.
 for p in placed{
  let below=data.beneath(p,placed).count
  guard below>0,let pt=data.stackMarker(p,placed) else{continue}
  let right=CGPoint(x:origin.x+pt[0]*u,y:origin.y+pt[1]*u),rect=CGRect(x:right.x-u*0.80,y:right.y-u*0.16,width:u*0.80,height:u*0.31)
  c.fill(rounded(rect,u*0.08),with:.color(ink("#fff3d8")));c.stroke(rounded(rect,u*0.08),with:.color(ink("#b47a2d")),lineWidth:u*0.012)
  for i in 0..<2{c.stroke(rounded(CGRect(x:rect.minX+u*0.09+CGFloat(i)*u*0.025,y:rect.minY+u*0.09-CGFloat(i)*u*0.035,width:u*0.14,height:u*0.10),u*0.02),with:.color(ink("#a96b1e")),lineWidth:u*0.016)}
  c.draw(Text("叠 \(below+1)").font(.system(size:u*0.17,weight:.bold)).foregroundColor(ink("#925b1c")),at:CGPoint(x:rect.minX+u*0.49,y:rect.midY))
 }
 drawFence(c,u,origin,data)
 for(name,p)in data.gates{drawEntry(c,name,p,u,name==start,name==finish)}
}

private func drawFence(_ c:GraphicsContext,_ u:CGFloat,_ origin:CGPoint,_ data:GameData){
 let cream=ink("#fff4d7"),shade=ink("#ac804b").opacity(0.35)
 let edges:[(CGPoint,CGPoint)]=[(CGPoint(x:u*0.46,y:u*0.18),CGPoint(x:u*5.22,y:u*0.18)),(CGPoint(x:u*0.46,y:u*4.50),CGPoint(x:u*5.22,y:u*4.50)),(CGPoint(x:u*0.18,y:u*0.46),CGPoint(x:u*0.18,y:u*4.24)),(CGPoint(x:u*5.50,y:u*0.46),CGPoint(x:u*5.50,y:u*4.24))]
 for(a,b)in edges{
  let horizontal=a.y==b.y
  let openings=data.gates.values.filter{pt in horizontal ? pt[1]==(a.y<u ? 0:4):pt[0]==(a.x<u ? 0:5)}.map{horizontal ? origin.x+CGFloat($0[0])*u:origin.y+CGFloat($0[1])*u}.sorted()
  let lo=horizontal ? a.x:a.y,hi=horizontal ? b.x:b.y
  func at(_ v:CGFloat)->CGPoint {horizontal ? CGPoint(x:v,y:a.y):CGPoint(x:a.x,y:v)}
  func rail(_ from:CGFloat,_ to:CGFloat){
   guard to>from else{return};let p=at(from),q=at(to)
   line(c,CGPoint(x:p.x,y:p.y+u*0.018),CGPoint(x:q.x,y:q.y+u*0.018),shade,u*0.055,.round)
   line(c,p,q,cream,u*0.035,.round)
  }
  func post(_ v:CGFloat){
   let center=at(v),foot=CGPoint(x:center.x-u*0.065,y:center.y-u*0.085)
   c.fill(rounded(CGRect(x:foot.x+u*0.018,y:foot.y+u*0.025,width:u*0.13,height:u*0.17),u*0.035),with:.color(shade))
   c.fill(rounded(CGRect(x:foot.x,y:foot.y,width:u*0.13,height:u*0.17),u*0.035),with:.color(ink("#f4ddb3")))
   c.fill(rounded(CGRect(x:foot.x,y:foot.y,width:u*0.13,height:u*0.13),u*0.035),with:.color(cream))
   c.fill(rounded(CGRect(x:foot.x+u*0.017,y:foot.y+u*0.012,width:u*0.095,height:u*0.025),u*0.01),with:.color(.white.opacity(0.65)))
  }
  var cursor=lo
  for center in openings{rail(cursor,max(lo,center-u*0.40));cursor=min(hi,center+u*0.40)}
  rail(cursor,hi)
  // Each open gateway has two end posts; the rails meet their centers.
  for center in openings{post(max(lo,center-u*0.40));post(min(hi,center+u*0.40))}
  let count=horizontal ? 7:5
  for i in 0...count{let v=lo+(hi-lo)*CGFloat(i)/CGFloat(count);if !openings.contains(where:{abs(v-$0)<u*0.58}){post(v)}}
 }
}

/// Flat entry tags share the original letter center; nothing occupies the road.
func drawEntry(_ c:GraphicsContext,_ name:String,_ p:[Double],_ u:CGFloat,_ start:Bool,_ finish:Bool){
 let pt=entryCenter(p),v=entryOutward(p),center=CGPoint(x:pt[0]*u,y:pt[1]*u),horizontal=v[1] != 0
 let w=(horizontal ? 0.57:0.30)*u,h=(horizontal ? 0.29:0.52)*u,face=CGRect(x:center.x-w/2,y:center.y-h/2,width:w,height:h)
 let terracotta=ink("#a4472f"),cream=ink("#fffaf0")
 c.fill(rounded(face,u*0.045),with:.color(ink(finish ? "#fff3df":"#ffe9bf")))
 c.stroke(rounded(face,u*0.045),with:.color(finish ? terracotta:ink("#d5ad73")),lineWidth:u*(finish ? 0.022:0.01))
 if finish{
  // A top-down checker threshold along the board edge, with no raised arch.
  let band=CGPoint(x:center.x+v[0]*(horizontal ? h:w)/2-v[0]*u*0.04,y:center.y+v[1]*(horizontal ? h:w)/2-v[1]*u*0.04)
  for i in 0..<6{for row in 0..<2{
   let along=(Double(i)-2.5)*u*0.068,across=(Double(row)-0.5)*u*0.030
   let x=band.x-v[1]*along+v[0]*across,y=band.y+v[0]*along+v[1]*across
   let rw=u*(horizontal ? 0.068:0.030),rh=u*(horizontal ? 0.030:0.068)
   c.fill(Path(CGRect(x:x-rw/2,y:y-rh/2,width:rw,height:rh)),with:.color((i+row)%2==0 ? terracotta:cream))
  }}
 }
 c.draw(Text(name).font(.system(size:u*0.24,weight:.bold)).foregroundColor(ink("#342718")),at:center)
 if start{
  // The arrow lives entirely outside the wooden frame and points at the letter.
  func point(_ along:Double,_ across:Double)->CGPoint {CGPoint(x:center.x+v[0]*u*along-v[1]*u*across,y:center.y+v[1]*u*along+v[0]*u*across)}
  var arrow=Path();arrow.addLines([point(0.20,0),point(0.34,0.105),point(0.34,0.045),point(0.46,0.045),point(0.46,-0.045),point(0.34,-0.045),point(0.34,-0.105)]);arrow.closeSubpath()
  c.stroke(arrow,with:.color(cream),style:StrokeStyle(lineWidth:u*0.045,lineJoin:.round));c.fill(arrow,with:.color(forest))
 }
}

/// Shadow plus the trimmed eight-direction sprite, fitted to the Android 0.72×0.68 cell box.
func drawHorse(_ c:GraphicsContext,_ body:CGPoint,_ heading:HorseHeading,_ u:CGFloat,jump:CGFloat){
 c.fill(Path(ellipseIn:CGRect(x:body.x-u*0.20,y:body.y+u*0.11,width:u*0.40,height:u*0.10)),with:.color(saddle.opacity(jump>0 ? 0.12:0.23)))
 guard let sprite=ArtLibrary.shared.horse(heading) else{return}
 let box=CGRect(x:body.x-u*0.36,y:body.y-u*0.34-jump,width:u*0.72,height:u*0.68)
 let scale=min(box.width/CGFloat(sprite.width),box.height/CGFloat(sprite.height)),size=CGSize(width:CGFloat(sprite.width)*scale,height:CGFloat(sprite.height)*scale)
 c.draw(Image(decorative:sprite,scale:1),in:CGRect(x:box.midX-size.width/2,y:box.midY-size.height/2,width:size.width,height:size.height))
}

/// Full-bleed or cropped bitmap from the shared cache; falls back to paper if missing.
struct ArtImage:View {
 let name:String;var fill=true
 var body:some View {
  if let image=ArtLibrary.shared.image(name){
   if fill{Image(decorative:image,scale:1).resizable().scaledToFill()}else{Image(decorative:image,scale:1).resizable()}
  }else{paper}
 }
}
