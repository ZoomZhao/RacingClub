package com.zoomzhao.racingclub

import org.junit.Assert.*
import org.junit.Test

class BoardInteractionTest {
    private val pieces=(1..10).associateWith{Piece(it,"#ffffff",emptyList(),emptyList(),emptyList())}
    private val rules=Rules(pieces,emptyMap(),emptyList(),emptyMap())
    @Test fun temporaryStacksAreValidButOutOfBoundsDropsAreRejected() {
        assertEquals("",placementProblem(rules,Placement(6,1,0,0)))
        assertEquals("",placementProblem(rules,Placement(1,0,2,1)))
        assertEquals("超出棋盘边界",placementProblem(rules,Placement(1,4,0,0)))
        assertEquals("超出棋盘边界",placementProblem(rules,Placement(1,0,3,1)))
    }
    @Test fun stacksUseTopmostHitAndOnlyExposedFacesGetMarkers() {
        val a=Placement(1,0,0,0);val b=Placement(5,1,0,0);val c=Placement(6,0,0,0)
        val all=listOf(a,b,c)
        assertEquals(c,rules.hit(all,1,0))
        assertEquals(b,rules.hit(all,2,0))
        assertNull(rules.hit(all,4,3))
        assertEquals(listOf(b,c),rules.coveredBy(a,all))
        assertEquals(listOf(a,b),rules.beneath(c,all))
        assertNull(rules.stackMarker(a,all))
        for(p in listOf(b,c)){
            val marker=rules.stackMarker(p,all)!!
            assertEquals(p.id,rules.hit(all,marker.x.toInt(),marker.y.toInt())?.id)
        }
        assertTrue(rules.hasOverlap(all))
        assertFalse(rules.hasOverlap(listOf(a,Placement(5,2,0,0))))
    }
}
