package com.zoomzhao.racingclub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

/** The exact tap, tray and drag paths the board and rack gestures call. */
class SelectionAndDragTest {
    private val h=Harness();private val s=h.s
    private val drag=BoardDrag().apply{board=Rect(0f,0f,570f,470f)} // 100px cells
    /** Root pointer that drops [p] at cell (x,y) for a centre grab. */
    private fun pointer(p:Placement,x:Int,y:Int):Offset{val(w,h)=s.rules.dimensions(p.r);return Offset((x+.35f+w/2f)*100f,(y+.35f+h/2f)*100f)}
    private fun rackDrop(id:Int,x:Int,y:Int,token:Any=Any()):Placement {
        assertTrue(drag.claim(token));s.pressTile(id);val p=s.rackPiece(id)
        assertTrue(drag.begin(s,token,p,Offset.Zero));drag.moveTo(token,pointer(p,x,y));drag.finish(s,token);drag.release(token)
        return s.placed.single{it.id==id}
    }
    private fun tapCell(x:Int,y:Int){val hit=s.rules.hit(s.placed,x,y);val was=hit!=null&&s.pressTile(hit.id);s.releaseBoardTap(hit,was,x,y,false)}

    @Test fun firstTapTurnsAtTheRightEdgeAndNudgesInsideTheBottomCornerAsOneUndoableMove() {
        s.start(h.level(3));s.select(1);tapCell(4,1)
        assertEquals(listOf(Placement(1,4,1,1)),s.placed);assertEquals(1,s.rotation);assertEquals(1,s.moves);assertEquals("",s.message)
        s.undo();assertTrue(s.placed.isEmpty());assertEquals(0,s.moves)
        s.select(1);tapCell(4,3)
        assertEquals(listOf(Placement(1,4,1,1)),s.placed);assertEquals(1,s.moves)
        s.undo();s.select(5);s.rotate(1);tapCell(3,3)
        assertEquals(listOf(Placement(5,3,3,2)),s.placed);assertEquals(1,s.moves)
        s.undo();s.select(6);tapCell(2,1)
        assertEquals(listOf(Placement(6,2,1,0)),s.placed)
        // Explicit drag drops continue to reject an invalid position, keeping the current tile.
        val before=s.placed;val moves=s.moves
        assertFalse(s.placePiece(Placement(6,4,0,0)));assertEquals(before,s.placed);assertEquals(moves,s.moves)
        for(r in 0..3)for(x in 0..4)for(y in 0..3){
            val p=Placement(7,x,y,r);val placed=s.rules.initialPlacement(p)
            assertTrue(s.rules.fits(placed));assertEquals(if(s.rules.fits(p))r else(r+1)%4,placed.r)
            if(s.rules.fits(p))assertEquals(p,placed)
        }
    }

    @Test fun tappingTheSelectedTileOrFrameDismissesWithoutMoving() {
        s.start(h.level(3));s.select(1);s.place(1,1);val moves=s.moves
        tapCell(1,1);assertNull("Second tap dismisses",s.selected);assertEquals(moves,s.moves)
        tapCell(2,1);assertEquals("Next press selects again",1,s.selected)
        tapCell(3,3);assertNull("Empty cell only dismisses a placed tile",s.selected)
        assertEquals(listOf(Placement(1,1,1,0)),s.placed);assertEquals(moves,s.moves)
        s.select(1);s.releaseBoardTap(null,false,-1,2,true);assertEquals("Entry letter keeps selection",1,s.selected)
        s.releaseBoardTap(null,false,-1,2,false);assertNull("Bare frame dismisses",s.selected)
        s.select(5);tapCell(3,3);assertEquals("Tray tile is still placed by tap",Placement(5,3,3,0),s.placed.last())
    }
    @Test fun rackCardSecondTapDismissesOnlyPlacedTiles() {
        s.start(h.level(3));s.select(1);s.place(0,0)
        s.releaseRackTap(1,s.pressTile(1));assertNull(s.selected)
        s.releaseRackTap(1,s.pressTile(1));assertEquals(1,s.selected)
        s.releaseRackTap(5,s.pressTile(5));s.releaseRackTap(5,s.pressTile(5));assertEquals("Unplaced tile stays ready",5,s.selected)
        s.tapTile(1);assertEquals(1,s.selected);s.tapTile(1);assertNull(s.selected)
        s.select(5);s.place(0,0);assertTrue(s.rules.coveredBy(s.placed.first(),s.placed).isNotEmpty())
        s.selected=1 // e.g. after undo: a selected tile that is covered
        s.releaseRackTap(1,s.pressTile(1));assertEquals("Raising a covered tile never dismisses",1,s.selected);assertEquals(1,s.placed.last().id)
    }
    @Test fun trayOrientationMatchesForTapDragAndAfterMovingBack() {
        s.start(h.level(3))
        s.select(1);s.place(0,0);val tapped=s.placed.single()
        s.undo();val dragged=rackDrop(1,0,0)
        assertEquals("Tap and drag from the tray agree",tapped,dragged)
        s.rotate(1);assertEquals(1,s.placed.single().r)
        s.remove();assertEquals(0,s.rotation);s.place(0,0);assertEquals("Moved back, then tapped",tapped,s.placed.single())
        s.rotate(1);s.remove();assertEquals("Moved back, then dragged",tapped,rackDrop(1,0,0))
        // A turn chosen for a tray tile survives the repeated select of a single tap.
        s.select(5);s.rotate(1);s.pressTile(5);s.select(5);assertEquals(1,s.rotation)
        assertEquals(1,s.rackPiece(5).r);s.select(6);assertEquals(0,s.rackPiece(6).r)
        // A placed tile dragged from the tray keeps its board turn.
        s.select(1);s.rotate(-1);assertEquals(3,rackDrop(1,3,0).r)
    }
    @Test fun secondFingerCannotSelectCommitOrCancelTheFirstDrag() {
        s.start(h.level(3));s.select(1);s.place(0,0);s.select(5);s.place(2,2)
        val first=Any();val second=Any()
        assertTrue(drag.claim(first));s.pressTile(1)
        assertTrue(drag.begin(s,first,s.placed.single{it.id==1},Offset.Zero))
        assertFalse("Second finger cannot claim",drag.claim(second))
        assertFalse(drag.begin(s,second,s.placed.single{it.id==5},Offset.Zero))
        assertEquals(1,s.selected)
        drag.moveTo(second,Offset(9999f,9999f));drag.finish(s,second);drag.release(second)
        assertTrue("Other gestures cannot end this drag",drag.owns(first));assertNotNull(drag.piece)
        drag.moveTo(first,pointer(Placement(1,0,0,0),3,0))
        s.selected=5 // even if selection changes elsewhere, the drop commits the dragged id
        drag.finish(s,first)
        assertEquals(Placement(1,3,0,0),s.placed.single{it.id==1});assertEquals(Placement(5,2,2,0),s.placed.single{it.id==5})
        assertFalse(drag.busy);assertTrue(drag.claim(second))
    }
    @Test fun invalidAndUnchangedDropsKeepPositionAndMoveCount() {
        s.start(h.level(3));s.select(1);s.place(1,1);val moves=s.moves;val token=Any()
        drag.begin(s,token,s.placed.single(),Offset.Zero);drag.moveTo(token,pointer(s.placed.single(),1,1));drag.finish(s,token)
        assertEquals(moves,s.moves)
        drag.begin(s,token,s.placed.single(),Offset.Zero);drag.moveTo(token,pointer(s.placed.single(),4,1));drag.finish(s,token)
        assertEquals(Placement(1,1,1,0),s.placed.single());assertEquals(moves,s.moves);assertTrue(s.message.contains("边界"))
    }
}
