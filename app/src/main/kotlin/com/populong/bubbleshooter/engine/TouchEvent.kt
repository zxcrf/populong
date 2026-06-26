package com.populong.bubbleshooter.engine

sealed class TouchEvent {
    data class Down(val x: Float, val y: Float) : TouchEvent()
    data class Move(val x: Float, val y: Float) : TouchEvent()
    data class Up(val x: Float, val y: Float) : TouchEvent()
}
