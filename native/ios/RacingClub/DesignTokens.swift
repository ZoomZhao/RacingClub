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
