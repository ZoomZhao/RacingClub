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
}
