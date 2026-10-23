package com.zoomzhao.racingclub

import org.junit.Assert.*
import org.junit.Test

class BoardInteractionTest {
    private val pieces=(1..10).associateWith{Piece(it,"#ffffff",emptyList(),emptyList(),emptyList())}
    private val rules=Rules(pieces,emptyMap(),emptyList(),emptyMap())
    @Test fun feedbackIdentifiesAllConflictingPiecesAndAllowsMovingSelf() {
        val all=listOf(Placement(1,0,0,0),Placement(5,2,0,0))
        assertEquals("与 1、5 号拼板重叠",placementProblem(rules,Placement(6,1,0,0),all))
        assertEquals("",placementProblem(rules,all[0],all))
        assertEquals("",placementProblem(rules,Placement(1,0,2,1),all))
        assertEquals("超出棋盘边界",placementProblem(rules,Placement(1,4,0,0),all))
        assertEquals("超出棋盘边界",placementProblem(rules,Placement(1,0,3,1),all))
    }
    @Test fun boardActionsStayNearTileAndAvoidEntriesAcrossEdgesAndRotations() {
        val gates=listOf(Point(2.85f,.18f),Point(5.5f,1.85f),Point(4.85f,4.5f),Point(1.85f,4.5f),Point(.18f,3.85f),Point(.18f,.85f))
        for(u in listOf(52f,56f,74f,100f))for(r in 0..3){
            val(w,h)=rules.dimensions(r)
            for(x in 0..5-w)for(y in 0..4-h){
                val a=pieceActionAnchor(Placement(1,x,y,r),u)
                assertTrue(a.left>=0&&a.top>=0&&a.left+140<=5.7f*u&&a.top+52<=4.7f*u)
                val tileLeft=(x+.35f)*u;val tileRight=tileLeft+w*u;val tileTop=(y+.35f)*u;val tileBottom=tileTop+h*u
                assertTrue(a.left+140<=tileLeft||a.left>=tileRight||a.top+52<=tileTop||a.top>=tileBottom)
                assertTrue(kotlin.math.abs(if(a.below)a.top-tileBottom else tileTop-a.top-52)<=8.01f)
                for(g in gates)assertFalse("Action bar covers entry center",g.x*u in a.left..a.left+140&&g.y*u in a.top..a.top+52)
            }
        }
    }
}
