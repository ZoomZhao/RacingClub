package com.zoomzhao.racingclub

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Interprets shared/session-scenarios.json; the iOS session runs the same file.
 * Each scenario: new session, fake monotonic clock at 0, start(level). The
 * "solution-pieces" fixture places every reference tile without counting moves or
 * hints and leaves both endpoints unchosen. */
class SessionScenarioTest {
    @Test fun sharedScenariosHold() {
        val file=JSONObject(RepoData.scenarios.readText())
        assertEquals(1,file.getInt("schemaVersion"))
        val scenarios=file.getJSONArray("scenarios")
        assertTrue(scenarios.length()>=3)
        for(i in 0 until scenarios.length()){
            val sc=scenarios.getJSONObject(i);val id=sc.getString("id")
            val h=Harness();val s=h.s
            s.start(h.level(sc.getInt("level")))
            when(val fixture=sc.optString("fixture","")){
                ""->{}
                "solution-pieces"->s.placed=s.level.solution
                else->fail("$id: unknown fixture $fixture")
            }
            val actions=sc.getJSONArray("actions")
            for(n in 0 until actions.length()){
                val a=actions.getJSONObject(n)
                when(val op=a.getString("op")){
                    "advance"->h.clock.advance(a.getLong("ms"))
                    "pause"->s.pause()
                    "resume"->s.resume()
                    "hint"->s.hint()
                    "undo"->s.undo()
                    "choose-start"->s.chooseEndpoint(a.getString("letter"),false)
                    "choose-finish"->s.chooseEndpoint(a.getString("letter"),true)
                    else->fail("$id: unknown op $op")
                }
            }
            val expect=sc.getJSONObject("expect")
            if(expect.has("millis"))assertEquals("$id millis",expect.getLong("millis"),s.time())
            if(expect.has("moves"))assertEquals("$id moves",expect.getInt("moves"),s.moves)
            if(expect.has("hints"))assertEquals("$id hints",expect.getInt("hints"),s.hints)
            if(expect.has("start"))assertEquals("$id start",expect.getString("start"),s.chosenStart)
            if(expect.has("finish"))assertEquals("$id finish",expect.getString("finish"),s.chosenFinish)
        }
    }
}
