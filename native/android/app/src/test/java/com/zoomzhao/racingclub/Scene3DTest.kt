package com.zoomzhao.racingclub

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

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
    @Test fun bakedHorseGallopsOnTheGroundAndClearsARail() {
        val model=HorseModel.parse(java.io.File("src/main/res/raw/horse_quaternius.bin").readBytes())
        assertTrue("A full low-poly mesh, not a handful of boxes",model.triangleCount>1500)
        val k=HorseLength/model.length
        val out=FloatArray(model.vertexCount*3);val scratch=FloatArray(model.vertexCount*3)
        fun lowest()=(0 until model.vertexCount).minOf{out[it*3+1]}*k
        val poses=(0 until 12).map{i->model.pose(i/12f,null,out,scratch);lowest() to out.copyOf()}
        assertTrue("Hooves reach the ground during a stride",poses.minOf{it.first}<.005f)
        assertTrue("Hooves never sink into the board",poses.all{it.first>-.01f})
        val moved=(0 until model.vertexCount*3).maxOf{abs(poses[0].second[it]-poses[6].second[it])}*k
        assertTrue("Legs swing within a stride: $moved",moved>.1f)
        model.pose(.3f,.5f,out,scratch)
        assertTrue("Every hoof is above the rail pole mid-jump",lowest()+RailClearance>.25f)
        model.pose(.3f,0f,out,scratch);val entering=out.copyOf();model.pose(.3f,null,out,scratch)
        assertArrayEquals("The jump blends in from the gallop without a pop",out,entering,1e-4f)
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
