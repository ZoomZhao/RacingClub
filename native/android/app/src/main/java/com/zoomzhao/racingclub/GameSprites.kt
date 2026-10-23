package com.zoomzhao.racingclub

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.*

internal data class GameSprite(val bitmap:ImageBitmap,val source:IntOffset,val size:IntSize)
internal data class GameSprites(val horses:Map<HorseHeading,GameSprite>)

private fun loadSprite(resources:Resources,id:Int):GameSprite {
    val bitmap=BitmapFactory.decodeResource(resources,id,BitmapFactory.Options().apply{inScaled=false;inSampleSize=4})
    val pixels=IntArray(bitmap.width*bitmap.height)
    bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
    var left=bitmap.width;var top=bitmap.height;var right=0;var bottom=0
    for(y in 0 until bitmap.height)for(x in 0 until bitmap.width)if((pixels[y*bitmap.width+x] ushr 24)>12) {
        left=min(left,x);top=min(top,y);right=max(right,x+1);bottom=max(bottom,y+1)
    }
    check(left<right&&top<bottom){"Sprite is empty"}
    // Trim only the draw source rectangle; preserve every original ImageGen PNG.
    return GameSprite(bitmap.asImageBitmap(),IntOffset(left,top),IntSize(right-left,bottom-top))
}

/** Called only from [GameArt], so each view is decoded and scanned once per process. */
internal fun loadSprites(resources:Resources)=GameSprites(mapOf(
    HorseHeading.E to R.drawable.horse_e_v3,HorseHeading.SE to R.drawable.horse_se_v3,
    HorseHeading.S to R.drawable.horse_s_v3,HorseHeading.SW to R.drawable.horse_sw_v3,
    HorseHeading.W to R.drawable.horse_w_v3,HorseHeading.NW to R.drawable.horse_nw_v3,
    HorseHeading.N to R.drawable.horse_n_v3,HorseHeading.NE to R.drawable.horse_ne_v3
).mapValues{loadSprite(resources,it.value)})

@Composable internal fun gameSprites()=gameArt().sprites

internal fun DrawScope.paintSprite(sprite:GameSprite,box:Rect) {
    val scale=min(box.width/sprite.size.width,box.height/sprite.size.height)
    val width=(sprite.size.width*scale).roundToInt().coerceAtLeast(1)
    val height=(sprite.size.height*scale).roundToInt().coerceAtLeast(1)
    drawImage(sprite.bitmap,srcOffset=sprite.source,srcSize=sprite.size,
        dstOffset=IntOffset((box.center.x-width/2f).roundToInt(),(box.center.y-height/2f).roundToInt()),
        dstSize=IntSize(width,height),filterQuality=FilterQuality.Medium)
}
