package com.zoomzhao.racingclub

import org.junit.Assert.*
import org.junit.Test

/** All 80 shipped levels replayed on the JVM, so CI catches rule or data regressions. */
class CatalogRulesTest {
    private val rules=RepoData.rules
    private fun close(a:Point,b:Point){assertEquals(a.x,b.x,.0001f);assertEquals(a.y,b.y,.0001f)}

    @Test fun androidAssetIsTheSharedExport() {
        assertArrayEquals("assets/game-data.json must equal shared/game-data.json",RepoData.shared.readBytes(),RepoData.asset.readBytes())
    }
    @Test fun catalogShape() {
        assertEquals((1..80).toList(),rules.levels.map{it.id})
        assertEquals((1..10).toList(),rules.pieces.keys.sorted())
        assertEquals(listOf("A","B","C","D","E","F"),rules.gates.keys.sorted())
        assertEquals(listOf("STARTER","JUNIOR","EXPERT","MASTER","PUNDIT"),rules.difficulties.map{it.originalLabel})
        for(l in rules.levels){
            assertTrue(l.id in rules.difficulties.single{it.id==l.difficultyId}.let{it.firstLevel..it.lastLevel})
            assertEquals(l.sequence.distinct(),l.requiredPieces)
            assertEquals("Level ${l.id} endpoints are player-chosen from 65",l.id>=65,l.solveEndpoints)
        }
    }
    @Test fun everyReferenceAnswerTracesItsSequenceAndEndpoints() {
        for(l in rules.levels){
            val route=rules.trace(l,l.solution,l.solutionStart,l.solutionFinish)
            assertNotNull("Level ${l.id}",route);route!!
            assertEquals("Level ${l.id} crossings",l.sequence,route.mapNotNull{it.jump})
            assertEquals(l.solutionStart,rules.endpoint(route,false));assertEquals(l.solutionFinish,rules.endpoint(route,true))
            var prior:Point?=null
            for(step in route){
                val ports=rules.ports(step.p)
                close(ports[step.from],rules.position(step,0f));close(ports[step.to],rules.position(step,1f))
                prior?.let{close(it,rules.position(step,0f))};prior=rules.position(step,1f)
            }
            // Later levels also solve with the endpoints still unknown.
            if(l.solveEndpoints)assertNotNull("Level ${l.id} unknown endpoints",rules.trace(l,l.solution))
        }
        assertTrue("Level 6 passes 7 without crossing",rules.trace(rules.levels[5],rules.levels[5].solution)!!.any{it.p.id==7&&it.jump==null})
        assertEquals("Level 17 crosses 5 twice",2,rules.trace(rules.levels[16],rules.levels[16].solution)!!.count{it.jump==5})
        assertEquals(14,rules.levels.last().sequence.size);assertEquals(9,rules.levels.last().requiredPieces.size)
    }
    @Test fun missingAndDuplicateTilesNeverSolve() {
        for(l in rules.levels){
            for(p in l.solution)assertNull("Level ${l.id} without ${p.id}",rules.trace(l,l.solution-p,l.solutionStart,l.solutionFinish))
            assertNull("Level ${l.id} duplicate",rules.trace(l,l.solution+l.solution.first(),l.solutionStart,l.solutionFinish))
            if(l.solution.size>1)assertNull("Level ${l.id} duplicate id elsewhere",rules.trace(l,l.solution.dropLast(1)+l.solution.first().copy(x=l.solution.last().x,y=l.solution.last().y),l.solutionStart,l.solutionFinish))
        }
    }
    /** Branch tiles let some answer layouts also carry a neighbouring order along another
     * smooth route. Play only checks the printed order, so this is not a false win; the
     * exact set is pinned so any rule or data change shows up here. */
    private val alsoTraceableSwaps=mapOf(17 to listOf(1),30 to listOf(2),31 to listOf(2),32 to listOf(3),33 to listOf(2),34 to listOf(3),36 to listOf(1),39 to listOf(3,5,7),41 to listOf(0),54 to listOf(3),55 to listOf(11),57 to listOf(3,8),60 to listOf(3),62 to listOf(5),64 to listOf(4),65 to listOf(3),72 to listOf(1),76 to listOf(5))
    @Test fun wrongCrossingOrderIsRejected() {
        val accepted=mutableMapOf<Int,List<Int>>()
        for(l in rules.levels){
            val swaps=(0 until l.sequence.lastIndex).filter{l.sequence[it]!=l.sequence[it+1]}
            val ok=swaps.filter{i->
                val swapped=l.sequence.toMutableList().apply{val t=this[i];this[i]=this[i+1];this[i+1]=t}
                rules.trace(l.copy(sequence=swapped),l.solution,l.solutionStart,l.solutionFinish)?.also{assertEquals(swapped,it.mapNotNull{s->s.jump})}!=null
            }
            if(ok.isNotEmpty())accepted[l.id]=ok
            if(swaps.isNotEmpty())assertTrue("Level ${l.id} must reject some wrong order",ok.size<swaps.size)
            assertNull("Level ${l.id} extra crossing",rules.trace(l.copy(sequence=l.sequence+l.sequence.last()),l.solution,l.solutionStart,l.solutionFinish))
            if(l.sequence.size>1)assertNull("Level ${l.id} missing last crossing",rules.trace(l.copy(sequence=l.sequence.dropLast(1)),l.solution,l.solutionStart,l.solutionFinish))
        }
        assertEquals(alsoTraceableSwaps,accepted)
    }
    @Test fun wrongEntryOrExitNeverSolves() {
        for(l in rules.levels)for(g in rules.gates.keys){
            if(g!=l.solutionStart){
                val level=if(l.solveEndpoints)l else l.copy(start=g)
                assertNull("Level ${l.id} start $g",rules.trace(level,l.solution,g,l.solutionFinish))
            }
            if(g!=l.solutionFinish){
                val level=if(l.solveEndpoints)l else l.copy(finish=g)
                assertNull("Level ${l.id} finish $g",rules.trace(level,l.solution,l.solutionStart,g))
            }
        }
    }
    @Test fun aShiftedTileBreaksEveryAnswer() {
        for(l in rules.levels){
            val p=l.solution.first()
            val moved=(0..3).flatMap{r->(0..4).flatMap{x->(0..3).map{y->Placement(p.id,x,y,r)}}}
                .filter{q->rules.fits(q)&&l.solution.none{o->rules.overlaps(q,o)}&&rules.ports(q).toSet()!=rules.ports(p).toSet()}
                .minBy{q->kotlin.math.abs(q.x-p.x)+kotlin.math.abs(q.y-p.y)+if(q.r==p.r)0 else 1}
            assertNull("Level ${l.id} moved ${moved.id}",rules.trace(l,listOf(moved)+l.solution.drop(1),l.solutionStart,l.solutionFinish))
        }
    }
}
