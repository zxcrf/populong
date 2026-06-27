package com.populong.bubbleshooter.engine

import android.view.Choreographer
import android.view.SurfaceHolder
import com.populong.bubbleshooter.render.GameRenderer

class GameLoop(
    private val surfaceHolder: SurfaceHolder,
    private val controller: GameController,
    private val renderer: GameRenderer
) : Runnable, Choreographer.FrameCallback {

    @Volatile
    var running = false

    private val vsyncLock = Object()
    private var choreographer: Choreographer? = null

    fun attachChoreographer() {
        choreographer = Choreographer.getInstance()
    }

    override fun doFrame(frameTimeNanos: Long) {
        synchronized(vsyncLock) {
            vsyncLock.notifyAll()
        }
        if (running) {
            choreographer?.postFrameCallback(this)
        }
    }

    override fun run() {
        running = true
        choreographer?.postFrameCallback(this)

        var lastTime = System.nanoTime()

        while (running) {
            synchronized(vsyncLock) {
                try {
                    vsyncLock.wait(34)
                } catch (_: InterruptedException) {
                }
            }

            if (!running) break

            val now = System.nanoTime()
            val deltaMs = ((now - lastTime) / 1_000_000f).coerceAtMost(32f)
            lastTime = now

            controller.processInput()
            controller.update(deltaMs)

            val canvas = surfaceHolder.lockCanvas()
            if (canvas != null) {
                try {
                    renderer.draw(canvas, controller)
                } finally {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                }
            }
        }
    }

    fun stop() {
        running = false
        synchronized(vsyncLock) {
            vsyncLock.notifyAll()
        }
    }
}
