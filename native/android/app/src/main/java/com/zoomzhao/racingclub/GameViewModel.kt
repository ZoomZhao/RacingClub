package com.zoomzhao.racingclub

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Owns the session across Activity recreation. Only the application context is held. */
class GameViewModel(application:Application,handle:SavedStateHandle):AndroidViewModel(application) {
 val state=GameState(application,loadRules(application),LocalLeaderboard(application))
 init {
  // Decode board art once, off the main thread, before the first board is shown.
  viewModelScope.launch(Dispatchers.Default){GameArt.preload(application.resources)}
  handle.get<Bundle>(KEY)?.let{state.restore(it.toUiSnapshot())}
  handle.setSavedStateProvider(KEY){state.uiSnapshot().toBundle()}
 }
 private companion object {const val KEY="racingclub-ui"}
}

private fun UiSnapshot.toBundle()=Bundle().apply {
 putString("screen",screen);putInt("level",level);putInt("selected",selected?:-1);putString("dialog",dialog);putString("difficulty",difficulty)
 putBoolean("running",running);putLong("elapsed",elapsed);putInt("moves",moves);putInt("hints",hints)
}
private fun Bundle.toUiSnapshot()=UiSnapshot(getString("screen")?:"home",getInt("level"),getInt("selected",-1).takeIf{it>=0},getString("dialog").orEmpty(),getString("difficulty").orEmpty(),getBoolean("running"),getLong("elapsed"),getInt("moves"),getInt("hints"))
