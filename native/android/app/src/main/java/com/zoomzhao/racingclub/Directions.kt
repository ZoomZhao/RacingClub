package com.zoomzhao.racingclub

import kotlin.math.*

enum class HorseHeading(val label:String) {
    E("东"),SE("东南"),S("南"),SW("西南"),W("西"),NW("西北"),N("北"),NE("东北")
}

fun horseHeading(dx:Float,dy:Float):HorseHeading {
    if(abs(dx)+abs(dy)<.00001f)return HorseHeading.E
    val octant=((atan2(dy,dx)*180/PI+360+22.5)/45).toInt()%8
    return HorseHeading.entries[octant]
}

enum class BoundarySide(val inward:HorseHeading) {
    N(HorseHeading.S),E(HorseHeading.W),S(HorseHeading.N),W(HorseHeading.E)
}

fun boundarySide(point:Point):BoundarySide=when {
    point.x==0f->BoundarySide.W
    point.x==5f->BoundarySide.E
    point.y==0f->BoundarySide.N
    point.y==4f->BoundarySide.S
    else->error("A gate must lie on the board boundary")
}
