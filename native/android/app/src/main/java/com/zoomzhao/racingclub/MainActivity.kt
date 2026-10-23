package com.zoomzhao.racingclub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Window, lifecycle and ViewModel ownership only; the session lives in GameState. */
class MainActivity:ComponentActivity() {
 private val model:GameViewModel by viewModels()
 internal val game get()=model.state
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  WindowCompat.setDecorFitsSystemWindows(window,false)
  WindowInsetsControllerCompat(window,window.decorView).apply{hide(WindowInsetsCompat.Type.systemBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}
  // The clock, automatic run and its animation all follow this one foreground signal.
  lifecycle.addObserver(LifecycleEventObserver{_,event->
   if(event==Lifecycle.Event.ON_PAUSE)game.enterBackground() else if(event==Lifecycle.Event.ON_RESUME)game.enterForeground()
  })
  setContent{MaterialTheme(typography=ClubTypography,colorScheme=lightColorScheme(primary=Green,background=Paper,surface=Paper)){RacingApp(game)}}
 }
}
