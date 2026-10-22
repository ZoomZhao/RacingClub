package com.zoomzhao.racingclub

import org.junit.Assert.*
import org.junit.Test

class DirectionsTest {
    @Test fun allEightTravelDirectionsChooseTheMatchingView() {
        val vectors=listOf(1f to 0f,1f to 1f,0f to 1f,-1f to 1f,-1f to 0f,-1f to -1f,0f to -1f,1f to -1f)
        assertEquals(HorseHeading.entries,vectors.map{horseHeading(it.first,it.second)})
        assertEquals(HorseHeading.N,horseHeading(.01f,-1f))
        assertEquals(HorseHeading.W,horseHeading(-1f,-.01f))
    }
    @Test fun everyLetteredGateUsesItsBoardEdgeAndInwardDirection() {
        val gates=mapOf("A" to Point(2.5f,0f),"B" to Point(5f,1.5f),"C" to Point(4.5f,4f),"D" to Point(1.5f,4f),"E" to Point(0f,3.5f),"F" to Point(0f,.5f))
        assertEquals(listOf(BoundarySide.N,BoundarySide.E,BoundarySide.S,BoundarySide.S,BoundarySide.W,BoundarySide.W),gates.values.map{boundarySide(it)})
        assertEquals(listOf(HorseHeading.S,HorseHeading.W,HorseHeading.N,HorseHeading.N,HorseHeading.E,HorseHeading.E),gates.values.map{boundarySide(it).inward})
    }
}
