package com.zoomzhao.racingclub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

/** One palette for the list, the game page and dialogs: forest-green ink on warm cream,
 * with wood lines and a single gold accent for "selected". */
internal val Panel=Color(0xfffffbf3)
internal val Well=Color(0xfff4ead6)
internal val Line=Color(0xffe3cfac)
internal val Accent=Color(0xffd29a3c)
internal val HintInk=Color(0xff9b641b)
internal val Danger=Color(0xffa9442b)

internal val ClubColors=lightColorScheme(
    primary=Green,onPrimary=Color.White,primaryContainer=Color(0xffdfe9df),onPrimaryContainer=Green,
    secondary=Brown,onSecondary=Color.White,background=Paper,surface=Panel,onSurface=Color(0xff3a2e24),
    surfaceVariant=Well,onSurfaceVariant=Brown,outline=Line,error=Danger,
    surfaceContainerHigh=Panel,surfaceContainerHighest=Panel,surfaceContainer=Panel
)

/** Raised cream card: the single container style used on every screen. */
internal fun Modifier.clubPanel(radius:Dp=18.dp,elevation:Dp=5.dp):Modifier {
    val shape=RoundedCornerShape(radius)
    return shadow(elevation,shape,ambientColor=Color(0xff6b4a2a),spotColor=Color(0xff6b4a2a)).background(Panel,shape).border(1.dp,Line,shape)
}
/** Recessed area inside a panel, for the route and other grouped content. */
internal fun Modifier.clubWell(radius:Dp=12.dp)=background(Well,RoundedCornerShape(radius))

/** Small section heading with an optional right-aligned note. */
@Composable internal fun SectionLabel(title:String,modifier:Modifier=Modifier,note:String?=null,trailing:(@Composable RowScope.()->Unit)?=null) {
    Row(modifier.fillMaxWidth().heightIn(min=24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(4.dp,13.dp).background(Accent,RoundedCornerShape(2.dp)))
        Text(title,fontSize=12.sp,fontWeight=FontWeight.Bold,color=Green,maxLines=1)
        Spacer(Modifier.weight(1f))
        if(note!=null)Text(note,fontSize=10.sp,color=Brown,maxLines=1)
        trailing?.invoke(this)
    }
}

/** Segmented option used by settings: filled green when chosen. */
@Composable internal fun ChoiceChip(label:String,on:Boolean,onClick:()->Unit) {
    val shape=RoundedCornerShape(50)
    Box(Modifier.clip(shape).background(if(on)Green else Well,shape).border(1.dp,if(on)Green else Line,shape).clickable(role=Role.Button,onClick=onClick).semantics{contentDescription=label;selected=on}.padding(horizontal=14.dp,vertical=8.dp)){
        Text(label,fontSize=12.sp,fontWeight=FontWeight.Bold,color=if(on)Color.White else Brown,maxLines=1)
    }
}

/** Thin rounded progress track. */
@Composable internal fun ProgressTrack(fraction:Float,color:Color,modifier:Modifier=Modifier) {
    Box(modifier.height(5.dp).clip(RoundedCornerShape(50)).background(Line.copy(alpha=.7f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f,1f)).background(color,RoundedCornerShape(50)))
    }
}
