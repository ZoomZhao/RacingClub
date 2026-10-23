import Foundation

/// Foundation-only design values shared by the SwiftUI drawing code and macOS tests.
/// Board proportions mirror Android BoardMaterials.kt / Presentation.kt.
struct RGB:Equatable {
 let r:Double;let g:Double;let b:Double
 init(r:Double,g:Double,b:Double){self.r=r;self.g=g;self.b=b}
 init(hex:String){let n=UInt64(hex.drop{$0=="#"},radix:16) ?? 0;r=Double((n>>16)&255)/255;g=Double((n>>8)&255)/255;b=Double(n&255)/255}
 var hex:String {String(format:"#%02x%02x%02x",Int((r*255).rounded()),Int((g*255).rounded()),Int((b*255).rounded()))}
 var luminance:Double {func c(_ v:Double)->Double {v<=0.04045 ? v/12.92:pow((v+0.055)/1.055,2.4)};return 0.2126*c(r)+0.7152*c(g)+0.0722*c(b)}
 func mixed(with o:RGB,_ t:Double)->RGB {RGB(r:r+(o.r-r)*t,g:g+(o.g-g)*t,b:b+(o.b-b)*t)}
}
func contrastRatio(_ a:String,_ b:String)->Double {let x=RGB(hex:a).luminance,y=RGB(hex:b).luminance;return(max(x,y)+0.05)/(min(x,y)+0.05)}

enum Design {
 static let forest="#254c3b",paper="#f8f5ed",saddle="#78543b"
 static let roadNumberFace="#fff0d2",roadNumberInk="#214334"
 /// Plaque faces used behind unselected difficulty labels and level cards.
 static let plaqueFaces=["#fffbef","#f7e6c6"]
 /// White label on amber "covered" tags; the previous #a96b1e was 4.37:1.
 static let coveredAmber="#8f5a17"
 static let inProgressAmber="#8a5716"
 static let numberFaces=[1:"#aa346a",2:"#e5bd47",3:"#a7ba54",4:"#ae362e",5:"#245f91",6:"#236648",7:"#e09b59",8:"#75418e",9:"#75452b",10:"#1b6278"]
 static func numberFace(_ id:Int)->String {numberFaces[id] ?? forest}
 /// Same rule as Android numberInk: whichever of black/white contrasts more.
 static func numberInkIsDark(_ id:Int)->Bool {contrastRatio(numberFace(id),"#000000")>contrastRatio(numberFace(id),"#ffffff")}
 /// Darkens a brand color just enough to reach `minimum` on every background.
 static func legibleInk(_ hex:String,on backgrounds:[String]=plaqueFaces,minimum:Double=4.5)->String {
  var color=RGB(hex:hex)
  for _ in 0..<40 {if backgrounds.allSatisfy({contrastRatio(color.hex,$0)>=minimum}){return color.hex};color=color.mixed(with:RGB(r:0,g:0,b:0),0.04)}
  return color.hex
 }
}

/// Upright obstacle label. Coordinates are local to the rotated tile, in board cells.
struct RoadNumberLayout {
 let center:[Double];let width:Double;let height:Double;let font:Double
 var bounds:CGRect {CGRect(x:center[0]-width/2,y:center[1]-height/2,width:width,height:height)}
}
func roadNumberLayout(_ data:GameData,_ p:Placement,avoiding:[CGRect]=[],minimumFont:Double=0,available:CGRect?=nil)->RoadNumberLayout {
 let piece=data.pieces.first{$0.id==p.id}!,size=data.dimensions(p.r)
 let font=max(0.22,minimumFont),width=max(p.id==10 ? 0.33:0.30,font*(p.id==10 ? 1.4:1.05)),height=max(0.28,font*1.3)
 let bounds=available ?? CGRect(x:0.04,y:0.04,width:Double(size.0)-0.08,height:Double(size.1)-0.08)
 let g=piece.gate,center=data.rotate([g[0]/100,g[1]/100],p.r),angle=(g[2]+Double(p.r*90)) * .pi/180
 let normal=[cos(angle),sin(angle)],tangent=[-sin(angle),cos(angle)]
 func points(_ jumping:Bool)->[[Double]]{piece.paths.filter{($0.jump==p.id)==jumping}.flatMap{$0.samples.enumerated().filter{$0.offset%4==0}.map{data.rotate($0.element,p.r)}}}
 let own=points(true),other=points(false)
 func distanceSquared(_ q:[Double],_ points:[[Double]])->Double {points.map{pow($0[0]-q[0],2)+pow($0[1]-q[1],2)}.min() ?? Double.greatestFiniteMagnitude}
 func label(_ q:[Double])->RoadNumberLayout {RoadNumberLayout(center:q,width:width,height:height,font:font)}
 func valid(_ q:[Double])->Bool {
  let box=label(q).bounds
  if box.minX<bounds.minX-0.0001 || box.minY<bounds.minY-0.0001 || box.maxX>bounds.maxX+0.0001 || box.maxY>bounds.maxY+0.0001 || avoiding.contains(where:{box.insetBy(dx:-0.01,dy:-0.01).intersects($0)}){return false}
  // Separate the label from the complete rotated rail, both posts and a small clear gap.
  let separate=[[1.0,0],[0,1],normal,tangent].contains{axis in
   let labelSpan=width/2*abs(axis[0])+height/2*abs(axis[1])
   let railSpan=0.13*abs(axis[0]*normal[0]+axis[1]*normal[1])+0.375*abs(axis[0]*tangent[0]+axis[1]*tangent[1])
   return abs((q[0]-center[0])*axis[0]+(q[1]-center[1])*axis[1])+0.0001>=labelSpan+railSpan
  }
  if !separate{return false}
  let otherDistance=distanceSquared(q,other)
  return otherDistance>=pow(0.28,2) || sqrt(otherDistance)+0.02>=sqrt(distanceSquared(q,own))
 }
 func score(_ q:[Double])->Double {pow(q[0]-center[0],2)+pow(q[1]-center[1],2)+0.1*distanceSquared(q,own)+0.001*(q[0]+q[1])}
 var candidates=[[Double]]()
 if bounds.width>=width && bounds.height>=height{
  for(axis,half)in [(normal,0.105),(tangent,0.35)]{
   let cross=[-axis[1],axis[0]],projection=width/2*abs(axis[0])+height/2*abs(axis[1])
   for sign in [-1.0,1]{for extra in [0.0,0.06,0.12,0.24,0.32,0.40]{for shift in [0.0,-0.08,0.08,-0.16,0.16,-0.24,0.24,-0.32,0.32]{
    let d=(half+projection+0.025+extra)*sign
    let x=min(bounds.maxX-width/2,max(bounds.minX+width/2,center[0]+axis[0]*d+cross[0]*shift))
    let y=min(bounds.maxY-height/2,max(bounds.minY+height/2,center[1]+axis[1]*d+cross[1]*shift))
    if valid([x,y]){candidates.append([x,y])}
   }}}
  }
 }
 if let q=candidates.min(by:{score($0)<score($1)}){return label(q)}
 // Occlusion must not move the number onto a different branch.
 if !avoiding.isEmpty{return roadNumberLayout(data,p,minimumFont:minimumFont,available:available)}
 return label(center)
}

/// Eight ImageGen horse sprites, chosen from the route tangent (y grows downward).
enum HorseHeading:Int,CaseIterable {
 case e,se,s,sw,w,nw,n,ne
 var asset:String {"horse_\(["e","se","s","sw","w","nw","n","ne"][rawValue])_v3"}
 var label:String {["东","东南","南","西南","西","西北","北","东北"][rawValue]}
 init(dx:Double,dy:Double){
  guard abs(dx)+abs(dy)>=0.00001 else{self = .e;return}
  self=HorseHeading(rawValue:Int(((atan2(dy,dx)*180 / .pi+360+22.5)/45).rounded(.down))%8)!
 }
}

/// Letter tag center and outward direction for a gate on the board edge, in cell units incl. 0.35 frame.
func entryCenter(_ p:[Double])->[Double] {
 if p[1]==0{return[0.35+p[0],0.18]};if p[1]==4{return[0.35+p[0],4.50]};if p[0]==0{return[0.18,0.35+p[1]]};return[5.50,0.35+p[1]]
}
func entryOutward(_ p:[Double])->[Double] {
 if p[1]==0{return[0,-1]};if p[1]==4{return[0,1]};if p[0]==0{return[-1,0]};return[1,0]
}
