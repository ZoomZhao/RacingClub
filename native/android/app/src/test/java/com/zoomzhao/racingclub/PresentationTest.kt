package com.zoomzhao.racingclub

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PresentationTest {
    @Test fun roadNumbersStayNearTheirOwnRailAndKeepTheirSizeAndContrastInEveryTurn() {
        val rules=RepoData.rules
        assertTrue("Small road numerals exceed 7:1",contrast(RoadNumberInk,RoadNumberFace)>=7)
        for(id in 1..10)for(r in 0..3){
            val p=Placement(id,0,0,r);val label=roadNumberLayout(rules,p);val(w,h)=rules.dimensions(r)
            assertTrue("Never shrink the old .20 cell font",label.font>=.22f)
            assertTrue(label.bounds.left>=.04f&&label.bounds.top>=.04f&&label.bounds.right<=w-.04f&&label.bounds.bottom<=h-.04f)
            val piece=rules.pieces.getValue(id)
            val points=piece.paths.filter{it.jump==id}.flatMap{it.samples}.map{rules.rotate(it,r)}
            val rail=rules.rotate(Point(piece.gate[0]/100,piece.gate[1]/100),r)
            assertTrue("$id/$r stays next to its rail",hypot(rail.x-label.center.x,rail.y-label.center.y)<.36f)
            assertTrue("$id/$r sits on the obstacle's own road",points.minOf{hypot(it.x-label.center.x,it.y-label.center.y)}<.20f)
            for(unit in listOf(20f,24f,32f,48f)){
                val card=androidx.compose.ui.geometry.Rect(-.65f,.04f,w+.65f,h-.04f)
                val preview=roadNumberLayout(rules,p,minimumFont=11f/unit,available=card)
                assertTrue("Rack digits keep their previous physical font size",preview.font*unit>=10.99f)
                assertTrue("Preview $id/$r stays in its own card",preview.bounds.left>=card.left-.0001f&&preview.bounds.top>=card.top-.0001f&&preview.bounds.right<=card.right+.0001f&&preview.bounds.bottom<=card.bottom+.0001f)
                assertTrue("Preview $id/$r remains next to its rail",hypot(rail.x-preview.center.x,rail.y-preview.center.y)<.75f)
            }
        }
    }
    @Test fun everyRoadNumberClearsItsControlsOnAFullyOccupiedBoard() {
        val rules=RepoData.rules
        for(unit in listOf(52f,56f,74f,100f))for(id in 1..10)for(r in 0..3){
            val ids=listOf(id)+(1..10).filter{it!=id}
            val slots=if(r%2==0)(0..3).flatMap{y->listOf(Placement(0,0,y,r),Placement(0,2,y,r))}+listOf(Placement(0,4,0,1),Placement(0,4,2,1))
                else (0..1).flatMap{y->(0..4).map{x->Placement(0,x,y*2,r)}}
            val all=slots.mapIndexed{i,p->p.copy(id=ids[i])};val p=all.first()
            val a=pieceActionAnchor(p,unit,all)
            assertTrue(a.inside)
            val blocked=androidx.compose.ui.geometry.Rect(a.left/unit-p.x-.35f,a.top/unit-p.y-.35f,(a.left+a.width)/unit-p.x-.35f,(a.top+a.height)/unit-p.y-.35f)
            val label=roadNumberLayout(rules,p,listOf(blocked))
            assertFalse("$id/$r at $unit: controls cover the number",label.bounds.overlaps(blocked))
        }
    }
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
    @Test fun smallLabelsMeetTextContrastOnTheirBackgrounds() {
        assertTrue("White on covered tag",contrast(0xffffffff,CoveredTag)>=4.5)
        for(face in listOf(PlaqueFaceLight,PlaqueFaceDeep))assertTrue("In-progress label",contrast(InProgressInk,face)>=4.5)
        for(d in RepoData.rules.difficulties){
            val color=java.lang.Long.parseLong(d.color.removePrefix("#"),16) or 0xff000000
            val ink=readableInk(color,PlaqueFaceLight,PlaqueFaceDeep,PaperFace)
            for(face in listOf(PlaqueFaceLight,PlaqueFaceDeep,PaperFace))assertTrue("${d.label} on ${face.toString(16)}",contrast(ink,face)>=4.5)
            if(listOf(PlaqueFaceLight,PlaqueFaceDeep,PaperFace).all{contrast(color,it)>=4.5})assertEquals("Readable hues stay unchanged",color,ink)
        }
        val junior=RepoData.rules.difficulties.single{it.id=="junior"}
        assertTrue("Junior's original hue was the 4.37:1 case",contrast(0xff965e14,PlaqueFaceDeep)<4.5);assertEquals("#965E14",junior.color)
    }

}
