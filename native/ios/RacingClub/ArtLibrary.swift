import Foundation
import ImageIO
import CoreGraphics

/// Loads the bundled ImageGen PNGs once, downsampled for drawing. Source files are never modified.
/// Loose PNGs are read by file URL, so Xcode and the command-line bundle resolve them identically.
final class ArtLibrary:@unchecked Sendable {
 static let backdrop="arena_backdrop",arena="horse-arena",wood="wood_grain",sand="sand_grain"
 /// Longest edge after downsampling; textures are stretched per cell like Android.
 static let sizes=[backdrop:2048,arena:1536,wood:512,sand:512]
 static let horseSize=384
 static var allNames:[String] {Array(sizes.keys).sorted()+HorseHeading.allCases.map(\.asset)}
 static let shared=ArtLibrary(directory:Bundle.main.resourceURL)

 private let directory:URL?
 private let lock=NSLock()
 private var cache:[String:CGImage]=[:]
 init(directory:URL?){self.directory=directory}

 func image(_ name:String)->CGImage? {
  lock.lock();defer{lock.unlock()}
  if let hit=cache[name]{return hit}
  let isHorse=name.hasPrefix("horse_")
  guard let loaded=load(name,maxPixel:isHorse ? ArtLibrary.horseSize:ArtLibrary.sizes[name] ?? 1024) else{return nil}
  // Sprites keep only their opaque bounds so the horse box scales the animal, not padding.
  let result=isHorse ? ArtLibrary.trimmed(loaded) ?? loaded:loaded
  cache[name]=result;return result
 }
 func horse(_ heading:HorseHeading)->CGImage? {image(heading.asset)}
 /// Decodes everything off the main thread at launch.
 func preload(){for name in ArtLibrary.allNames{_=image(name)}}

 private func load(_ name:String,maxPixel:Int)->CGImage? {
  guard let url=directory?.appendingPathComponent("\(name).png"),let source=CGImageSourceCreateWithURL(url as CFURL,nil) else{return nil}
  let options:[CFString:Any]=[kCGImageSourceCreateThumbnailFromImageAlways:true,kCGImageSourceCreateThumbnailWithTransform:true,kCGImageSourceShouldCacheImmediately:true,kCGImageSourceThumbnailMaxPixelSize:maxPixel]
  return CGImageSourceCreateThumbnailAtIndex(source,0,options as CFDictionary)
 }
 static func trimmed(_ image:CGImage,threshold:UInt8=12)->CGImage? {
  let w=image.width,h=image.height
  guard let ctx=CGContext(data:nil,width:w,height:h,bitsPerComponent:8,bytesPerRow:w*4,space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue) else{return nil}
  ctx.draw(image,in:CGRect(x:0,y:0,width:w,height:h))
  guard let bytes=ctx.data?.assumingMemoryBound(to:UInt8.self),let rendered=ctx.makeImage() else{return nil}
  var left=w,top=h,right=0,bottom=0
  for y in 0..<h{for x in 0..<w where bytes[(y*w+x)*4+3]>threshold{left=min(left,x);top=min(top,y);right=max(right,x+1);bottom=max(bottom,y+1)}}
  guard left<right,top<bottom else{return nil}
  return rendered.cropping(to:CGRect(x:left,y:top,width:right-left,height:bottom-top))
 }
}
