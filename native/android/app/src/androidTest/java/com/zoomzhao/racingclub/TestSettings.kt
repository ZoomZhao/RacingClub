package com.zoomzhao.racingclub

import android.content.Context

/** Pins the board view. Geometry checks map cells linearly, so most suites use the flat board. */
internal fun useBoardView(context:Context,threeD:Boolean=false){
    context.applicationContext.getSharedPreferences("settings-v1",Context.MODE_PRIVATE).edit().putString("board-view",if(threeD)"3d" else "2d").commit()
}
