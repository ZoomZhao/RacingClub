package com.zoomzhao.racingclub

import org.junit.Assert.*
import org.junit.Test

class Scene3DTest {
    @Test fun tapsRayCastBackToTheBoardPointUnderThemAtAnyHeightAndSize() {
        for((w,h) in listOf(900f to 700f,1400f to 760f,600f to 900f))for(y in listOf(0f,.16f,.5f)){
            val camera=BoardCamera(w,h)
            for(x in listOf(.35f,1.2f,2.85f,5.3f))for(z in listOf(.35f,2.3f,4.3f)){
                val back=camera.toFrame(camera.project(V3(x,y,z)),y)
                assertEquals(x,back.x,1e-3f);assertEquals(z,back.y,1e-3f)
            }
        }
    }
    @Test fun boardFitsTheViewAndNearCellsLookLarger() {
        val camera=BoardCamera(1200f,800f);val b=camera.boardBounds()
        assertTrue(b.left>=-.5f&&b.top>=-.5f&&b.right<=1200.5f&&b.bottom<=800.5f)
        assertTrue(camera.pixelsPerUnit(V3(2.85f,0f,4.3f))>camera.pixelsPerUnit(V3(2.85f,0f,.35f)))
    }
    @Test fun gallopPlantsAHoofAndMovesEveryLegWhileAJumpTucksThemAllUp() {
        for(i in 0 until 20){
            val legs=horseLegs(i/20f,0f)
            assertTrue("Some hoof carries the horse at phase $i",legs.any{it.sole.y<.03f})
            legs.forEach{assertTrue("Hoof stays under the body",it.sole.y>=-.001f&&it.sole.y<HipHeight)}
        }
        val a=horseLegs(0f,0f);val b=horseLegs(.25f,0f)
        a.zip(b).forEach{(x,y)->assertTrue("Each leg moves within a stride",(x.hoof-y.hoof).getDistance()>.02f)}
        assertTrue("Every leg leaves the ground over a rail",horseLegs(.3f,1f).all{it.sole.y>.05f})
    }
    @Test fun topInsetMovesTheBoardDownWithoutShrinkingAWideView() {
        val plain=BoardCamera(1400f,1100f).boardBounds();val inset=BoardCamera(1400f,1100f,topInset=100f).boardBounds()
        assertEquals(plain.width,inset.width,1f)
        assertTrue(inset.top>=100f-.5f&&inset.bottom<=1100.5f)
    }
    @Test fun stackLevelsCountOnlyOverlappingTilesBelow() {
        val rules=RepoData.rules
        val a=Placement(1,0,0,0);val b=Placement(5,1,0,0);val c=Placement(6,0,0,0);val d=Placement(7,3,3,0)
        assertEquals(mapOf(1 to 0,5 to 1,6 to 2,7 to 0),stackLevels(rules,listOf(a,b,c,d)))
    }
}
