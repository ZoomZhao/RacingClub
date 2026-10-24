package com.zoomzhao.racingclub

import org.junit.Assert.*
import org.junit.Test

/** Session state machine with in-memory storage and a fake clock; no device needed. */
class GameSessionTest {
    private fun solveByHints(s:GameState){repeat(30){if(s.trace()==null)s.hint()};assertNotNull("Hints must complete level ${s.level.id}",s.trace())}

    @Test fun hintsSolveEveryLevelAndEachWinRecordsOneScore() {
        val h=Harness()
        for(l in h.rules.levels){
            h.s.start(l);solveByHints(h.s)
            assertTrue(h.s.beginRunIfSolved());assertFalse("A run starts once",h.s.beginRunIfSolved())
            h.s.finishRun();h.s.finishRun()
            assertEquals("win",h.s.screen);assertEquals(1,h.board.list(l.id).size);assertTrue(h.s.solved(l.id))
            assertEquals(h.s.moves,h.s.hints)
        }
    }
    @Test fun clockStopsForBackgroundAndDialogsAndFreezesForTheRun() {
        val h=Harness();val s=h.s
        s.start(h.level(3));h.clock.advance(1000)
        s.enterBackground();h.clock.advance(5000)
        assertEquals(1000,s.time())
        s.resume();h.clock.advance(5000)
        assertEquals("Resume is ignored while in the background",1000,s.time())
        s.enterForeground();h.clock.advance(250)
        s.showDialog("help");h.clock.advance(4000);assertEquals(1250,s.time())
        s.closeDialog();h.clock.advance(250)
        solveByHints(s);assertTrue(s.beginRunIfSolved());h.clock.advance(9000)
        assertEquals(1500,s.time());assertEquals(1500L,h.board.list(3).single().millis)
        s.finishRun();assertEquals(1500,s.elapsed)
    }
    @Test fun noRunOrScoreInTheBackgroundOrBehindADialog() {
        val h=Harness();val s=h.s
        s.start(h.level(3));solveByHints(s)
        s.enterBackground()
        assertFalse(s.canAutoRun());assertFalse(s.beginRunIfSolved());assertFalse(s.beginRun(s.trace()!!))
        assertFalse(s.running);assertTrue(h.board.scores.isEmpty());assertFalse(s.solved(3))
        s.enterForeground();s.showDialog("rank")
        assertFalse(s.beginRunIfSolved());assertTrue(h.board.scores.isEmpty())
        s.closeDialog();assertTrue(s.beginRunIfSolved());assertEquals(1,h.board.scores.size)
        s.showDialog("help");assertEquals("No dialog over a running horse","",s.dialog)
    }
    @Test fun savedProgressRestoresAndRejectsInvalidPiecesAndGates() {
        val h=Harness();val s=h.s;val l=h.level(65)
        s.start(l);s.hint();s.chooseEndpoint("C",false);h.clock.advance(700);s.home()
        val again=h.reopen().s;again.start(l)
        assertEquals(s.placed,again.placed);assertEquals("C",again.chosenStart);assertEquals(2,again.moves);assertEquals(1,again.hints);assertEquals(700,again.time())
        assertTrue(again.inProgress(65))
        h.progress.save(65,SavedProgress(listOf(Placement(4,0,0,1),Placement(4,4,3,0),Placement(1,0,0,0),Placement(9,4,3,0)),3,0,-5,"Z","",false))
        val sanitized=h.reopen().s;sanitized.start(l)
        assertEquals("Off-board and foreign tiles are dropped",listOf(Placement(4,0,0,1)),sanitized.placed)
        assertEquals("",sanitized.chosenStart);assertEquals(0,sanitized.time())
        h.progress.save(65,SavedProgress(listOf(Placement(4,0,0,1)),3,0,10,"","",true))
        val finished=h.reopen().s;finished.start(l);assertTrue("A settled level starts fresh",finished.placed.isEmpty())
    }
    @Test fun recreationSnapshotRestoresGameSelectionAndDialog() {
        val h=Harness();val s=h.s
        s.selectedDifficultyId="junior";s.start(h.level(3));s.hint();s.select(1);s.showDialog("help");h.clock.advance(300)
        val ui=s.uiSnapshot()
        val restored=h.reopen().s;restored.restore(ui)
        assertEquals("game",restored.screen);assertEquals(3,restored.level.id);assertEquals(1,restored.selected);assertEquals("help",restored.dialog)
        assertEquals(s.placed,restored.placed);assertEquals(s.moves,restored.moves)
        h.clock.advance(5000);assertEquals("Dialog keeps the clock stopped",s.time(),restored.time())
        restored.closeDialog();h.clock.advance(100);assertEquals(s.time()+100,restored.time())
    }
    @Test fun interruptedRunSettlesWithoutASecondScore() {
        val h=Harness();val s=h.s
        s.start(h.level(3));h.clock.advance(1200);solveByHints(s);assertTrue(s.beginRunIfSolved());s.runProgress=.4f
        val ui=s.uiSnapshot();assertTrue(ui.running)
        val restored=h.reopen().s;restored.restore(ui)
        assertEquals("win",restored.screen);assertFalse(restored.running)
        assertEquals(1200,restored.elapsed);assertEquals(s.moves,restored.moves);assertEquals(s.hints,restored.hints)
        assertFalse(restored.beginRunIfSolved());assertEquals(1,h.board.scores.size)
        restored.restore(restored.uiSnapshot());assertEquals("win",restored.screen);assertEquals(1,h.board.scores.size)
        val home=h.reopen().s;home.restore(UiSnapshot("home",80,null,"","pundit",false,0,0,0))
        assertEquals("home",home.screen);assertEquals("pundit",home.selectedDifficultyId)
    }
    @Test fun fourTurnsAboutTheCenterReturnToTheSameCellAndClearNeedsNoUndo() {
        val h=Harness();val s=h.s
        s.start(h.level(3));s.select(1);s.place(1,1)
        val home=s.placed.single()
        repeat(4){s.rotate(1)}
        assertEquals(home,s.placed.single())
        repeat(4){s.rotate(-1)}
        assertEquals(home,s.placed.single())
        s.chooseTurnMode(TurnMode.CLOCKWISE);assertEquals(listOf(1,0),turnActions(s.turnMode))
        s.chooseTurnMode(TurnMode.COUNTERCLOCKWISE);assertEquals(listOf(-1,0),turnActions(s.turnMode))
        s.hint();h.progress.save(4,SavedProgress(listOf(Placement(1,0,0,0)),1,0,10,"","",false));h.progress.markCompleted(5)
        s.clearAllProgress()
        assertTrue(s.placed.isEmpty());assertEquals(0,s.moves);assertFalse(s.solved(3));assertFalse(s.inProgress(4));assertFalse(h.progress.completed(5))
        s.undo();assertTrue("Clear is not one undo step",s.placed.isEmpty())
    }
    @Test fun endpointHintIsUndoableAndCountsOnce() {
        val h=Harness();val s=h.s;val l=h.level(65)
        s.start(l);s.placed=l.solution
        s.hint();assertEquals(l.solutionStart,s.chosenStart);assertEquals(l.solutionFinish,s.chosenFinish)
        s.hint();s.hint();assertEquals(1,s.hints);assertEquals(1,s.moves)
        s.undo();assertEquals("",s.chosenStart);assertEquals(0,s.moves);assertEquals("Practice mark stays",1,s.hints)
    }
}
