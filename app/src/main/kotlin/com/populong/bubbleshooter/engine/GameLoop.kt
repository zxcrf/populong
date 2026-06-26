package com.populong.bubbleshooter.engine

import android.view.SurfaceHolder
import com.populong.bubbleshooter.render.GameRenderer

class GameLoop(
    private val surfaceHolder: SurfaceHolder,
    private val controller: GameController,
    private val renderer: GameRenderer
) : Runnable {

    @Volatile
    var running = false

    override fun run() {
        running = true
        var lastTime = System.nanoTime()

        while (running) {
            val now = System.nanoTime()
            val deltaMs = (now - lastTime) / 1_000_000f
            lastTime = now

            controller.processInput()
            controller.update(deltaMs.coerceAtMost(32f))

            val canvas = surfaceHolder.lockCanvas()
            if (canvas != null) {
                try {
                    renderer.draw(canvas, controller)
                } finally {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                }
            }

            val elapsed = (System.nanoTime() - now) / 1_000_000
            val sleep = GameConfig.FRAME_DURATION_MS - elapsed
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep)
                } catch (_: InterruptedException) {
                }
            }
        }
    }
}
