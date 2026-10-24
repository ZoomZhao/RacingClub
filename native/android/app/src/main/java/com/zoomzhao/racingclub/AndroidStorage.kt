package com.zoomzhao.racingclub

import android.content.Context
import android.os.SystemClock

internal fun loadRules(context:Context)=parseRules(context.assets.open("game-data.json").bufferedReader().use {it.readText()})

class LocalLeaderboard(context:Context):LeaderboardRepository {
 private val prefs=context.applicationContext.getSharedPreferences("leaderboard-v1",Context.MODE_PRIVATE)
 private val order=compareBy<Score>{it.hints>0}.thenBy{it.millis}.thenBy{it.moves}
 private var records:List<Score> = decodeScores(prefs.getString("scores","[]")?:"[]")
 override fun list(level:Int)=records.filter{it.level==level}.sortedWith(order).take(10)
 override fun save(score:Score) {
  // Keep ten best records PER LEVEL; finishing later levels cannot erase early bests.
  records=records.filter{it.level!=score.level}+(list(score.level)+score).sortedWith(order).take(10)
  prefs.edit().putString("scores",encodeScores(records)).apply()
 }
}

/** progress-v1 keys are unchanged. Decoded entries are cached so home cards do not
 * parse JSON on every composition. */
class SharedPreferencesProgressStore(context:Context):ProgressStore {
 private val prefs=context.applicationContext.getSharedPreferences("progress-v1",Context.MODE_PRIVATE)
 private val cache=mutableMapOf<Int,SavedProgress?>()
 override fun load(level:Int)=cache.getOrPut(level){prefs.getString("level-$level",null)?.let{decodeProgress(it)}}
 override fun save(level:Int,progress:SavedProgress){cache[level]=progress;prefs.edit().putString("level-$level",encodeProgress(progress)).apply()}
 override fun completed(level:Int)=prefs.getBoolean("completed-$level",false)
 override fun markCompleted(level:Int){prefs.edit().putBoolean("completed-$level",true).apply()}
 override fun clearAll(){cache.clear();prefs.edit().clear().apply()}
}

private const val TURN_MODE_KEY="turn-mode"

internal fun loadTurnMode(context:Context):TurnMode=
 when(context.applicationContext.getSharedPreferences("settings-v1",Context.MODE_PRIVATE).getString(TURN_MODE_KEY,"both")){
  "cw"->TurnMode.CLOCKWISE;"ccw"->TurnMode.COUNTERCLOCKWISE;else->TurnMode.BOTH
 }

internal fun saveTurnMode(context:Context,mode:TurnMode){
 context.applicationContext.getSharedPreferences("settings-v1",Context.MODE_PRIVATE).edit().putString(TURN_MODE_KEY,when(mode){TurnMode.CLOCKWISE->"cw";TurnMode.COUNTERCLOCKWISE->"ccw";TurnMode.BOTH->"both"}).apply()
}

/** Convenience for the app and instrumented tests: real storage and the monotonic clock. */
fun GameState(context:Context,rules:Rules,repository:LeaderboardRepository)=
 GameState(rules,repository,SharedPreferencesProgressStore(context),loadTurnMode(context),{saveTurnMode(context,it)},SystemClock::elapsedRealtime)
