package com.zoomzhao.racingclub
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
 private val pieces=mapOf(
  1 to listOf(Point(0f,.5f),Point(2f,.5f)),2 to listOf(Point(.5f,0f),Point(1.5f,1f)),
  3 to listOf(Point(.5f,1f),Point(1.5f,0f)),5 to listOf(Point(0f,.5f),Point(1.5f,0f)),6 to listOf(Point(0f,.5f),Point(1.5f,1f))
 ).mapValues{(id,ports)->Piece(id,"#ffffff",ports,ports,listOf(0f,0f,0f))}
 private val gates=mapOf("A" to Point(2.5f,0f),"B" to Point(5f,1.5f),"E" to Point(0f,3.5f),"F" to Point(0f,.5f))
 private val l3=Level(3,"","E","F",listOf(5,1,6),listOf(Placement(5,0,3,0),Placement(1,1,1,1),Placement(6,0,0,0)))
 private val l4=Level(4,"","B","A",listOf(2,1,5,3),listOf(Placement(2,4,1,1),Placement(1,2,2,0),Placement(5,1,1,1),Placement(3,1,0,0)))
 private val rules=Rules(pieces,gates,listOf(l3,l4),emptyMap())
 @Test fun bothPhotoSolutionsConnectInOrder(){for(l in listOf(l3,l4))assertEquals(l.sequence,rules.trace(l,l.solution)?.map{it.p.id})}
 @Test fun rejectsBrokenAndWrongOrder(){assertNull(rules.trace(l3,l3.solution.dropLast(1)));assertNull(rules.trace(l3.copy(sequence=listOf(6,1,5)),l3.solution));assertNull(rules.trace(l3,l3.solution.map{if(it.id==1)it.copy(x=2)else it}))}
 @Test fun rotatedTileCannotOverlapOrLeaveBoard(){assertFalse(rules.fits(Placement(5,0,3,1),l3.solution));assertFalse(rules.fits(Placement(1,0,0,0),listOf(Placement(5,1,0,0))))}
 @Test fun straightTileSymmetryAccepted(){assertNotNull(rules.trace(l3,l3.solution.map{if(it.id==1)it.copy(r=3)else it}))}
}
