package com.zoomzhao.racingclub

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PresentationTest {
    @Test fun allGoalPositionsSurviveWrappingAtLandscapeWidthsAndLargerFonts() {
        for(count in 3..20)for(width in listOf(240f,360f,520f,620f,800f))for(scale in listOf(1f,1.3f,1.6f,2f)){
            val rows=courseRows(count,width,scale)
            assertEquals((0 until count).toList(),rows.flatten())
            assertTrue(rows.maxOf{it.size}-rows.minOf{it.size}<=1)
            rows.forEach{assertTrue(it.size*(22f*scale)+(it.size-1)*8f<=width-8f)}
        }
        assertEquals(listOf(10,10),courseRows(20,600f,1f).map{it.size})
    }
    @Test fun everyNumberHasReadableContrastAndDarkBadgesUseWhiteInk() {
        for(id in 1..10){
            val a=relativeLuminance(numberFace(id));val b=relativeLuminance(numberInk(id))
            assertTrue("Contrast for $id",(max(a,b)+.05)/(min(a,b)+.05)>=4.5)
        }
        for(id in listOf(1,4,5,6,8,9,10))assertEquals(0xffffffff,numberInk(id))
        for(id in listOf(2,3,7))assertEquals(0xff000000,numberInk(id))
    }
    @Test fun entryLettersNeverMoveWhenTheirRolesChange() {
        val gates=mapOf("A" to Point(2.5f,0f),"B" to Point(5f,1.5f),"C" to Point(4.5f,4f),"D" to Point(1.5f,4f),"E" to Point(0f,3.5f),"F" to Point(0f,.5f))
        assertEquals(Point(2.85f,.18f),entryCenter(gates.getValue("A")))
        assertEquals(Point(.18f,3.85f),entryCenter(gates.getValue("E")))
        for(p in gates.values){val c=entryCenter(p);val o=entryOutward(p);assertEquals(1f,abs(o.x)+abs(o.y),.0001f);assertTrue(c.x in 0f..5.7f&&c.y in 0f..4.7f)}
    }
    @Test fun goalFeedbackFollowsActualJumpTimesAndRepeatedOccurrences() {
        val path=RoadPath(0,1,5,listOf(Point(0f,0f),Point(1f,0f)),.25f)
        val piece=Piece(5,"#fff",emptyList(),emptyList(),emptyList(),listOf(path))
        val rules=Rules(mapOf(5 to piece),emptyMap(),emptyList(),emptyMap())
        val p=Placement(5,0,0,0)
        val route=listOf(RouteStep(p,0,1,0,5),RouteStep(p,1,0,0,null),RouteStep(p,1,0,0,5))
        assertEquals(0,crossingFeedback(rules,route,.24f/3).crossed)
        assertEquals(1,crossingFeedback(rules,route,.25f/3).crossed)
        assertTrue(crossingFeedback(rules,route,.45f/3).pulse>.9f)
        assertEquals(1,crossingFeedback(rules,route,2.74f/3).crossed)
        assertEquals(2,crossingFeedback(rules,route,2.75f/3).crossed)
        assertTrue(crossingFeedback(rules,route,2.95f/3).pulse>.9f)
        assertEquals(2,crossingFeedback(rules,route,1f).crossed)
        assertEquals(CrossingFeedback(0,0f),crossingFeedback(rules,emptyList(),1f))
    }

}
