package com.zoomzhao.racingclub

import java.io.File

/** Shipped data, read straight from the repository rather than a copied fixture. */
internal object RepoData {
    val root:File by lazy {
        generateSequence(File("").absoluteFile){it.parentFile}.firstOrNull{File(it,"shared/session-scenarios.json").isFile}
            ?:error("Repository root with shared/session-scenarios.json not found")
    }
    val asset get()=File(root,"native/android/app/src/main/assets/game-data.json")
    val shared get()=File(root,"shared/game-data.json")
    val scenarios get()=File(root,"shared/session-scenarios.json")
    val rules:Rules by lazy {parseRules(asset.readText())}
}

internal class FakeClock(var now:Long=0){fun advance(ms:Long){now+=ms}}
internal class MemoryProgress:ProgressStore {
    val levels=mutableMapOf<Int,SavedProgress>();val done=mutableSetOf<Int>()
    override fun load(level:Int)=levels[level]
    override fun save(level:Int,progress:SavedProgress){levels[level]=progress}
    override fun completed(level:Int)=level in done
    override fun markCompleted(level:Int){done+=level}
}
internal class MemoryLeaderboard:LeaderboardRepository {
    val scores=mutableListOf<Score>()
    override fun list(level:Int)=scores.filter{it.level==level}
    override fun save(score:Score){scores+=score}
}
internal class Harness(val rules:Rules=RepoData.rules,val clock:FakeClock=FakeClock(),val progress:MemoryProgress=MemoryProgress(),val board:MemoryLeaderboard=MemoryLeaderboard()) {
    val s=GameState(rules,board,progress){clock.now}
    fun level(id:Int)=rules.levels.single{it.id==id}
    /** A fresh session over the same storage and clock, as after process death. */
    fun reopen()=Harness(rules,clock,progress,board)
}
