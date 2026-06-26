package com.populong.bubbleshooter.game

import android.graphics.PointF
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class AimLine {
    data class Segment(val start: PointF, val end: PointF)

    fun calculate(
        origin: PointF,
        angle: Float,
        leftWall: Float,
        rightWall: Float,
        topWall: Float,
        bubbleRadius: Float,
        grid: BubbleGrid,
        gridOffsetY: Float,
        maxBounces: Int = 1
    ): List<Segment> {
        val segments = mutableListOf<Segment>()
        var px = origin.x
        var py = origin.y
        var dx = cos(angle)
        var dy = sin(angle)
        var bounces = 0

        repeat(maxBounces + 1) {
            val step = bubbleRadius * 0.5f
            var traveled = 0f
            val maxTravel = 2000f
            var hitSomething = false

            while (traveled < maxTravel) {
                val nx = px + dx * step
                val ny = py + dy * step

                if (ny - bubbleRadius <= topWall) {
                    segments.add(Segment(PointF(px, py), PointF(nx, topWall + bubbleRadius)))
                    hitSomething = true
                    return segments
                }

                for ((cell, _) in grid.allOccupied()) {
                    val center = grid.cellToPixel(cell, bubbleRadius, gridOffsetY)
                    val dist = hypot(nx - center.x, ny - center.y)
                    if (dist <= bubbleRadius * 2f * 0.9f) {
                        segments.add(Segment(PointF(px, py), PointF(nx, ny)))
                        return segments
                    }
                }

                if (nx - bubbleRadius <= leftWall) {
                    val hitX = leftWall + bubbleRadius
                    val t = (hitX - px) / (dx * step)
                    val hitY = py + dy * step * t
                    segments.add(Segment(PointF(px, py), PointF(hitX, hitY)))
                    px = hitX
                    py = hitY
                    dx = -dx
                    bounces++
                    hitSomething = true
                    break
                }
                if (nx + bubbleRadius >= rightWall) {
                    val hitX = rightWall - bubbleRadius
                    val t = (hitX - px) / (dx * step)
                    val hitY = py + dy * step * t
                    segments.add(Segment(PointF(px, py), PointF(hitX, hitY)))
                    px = hitX
                    py = hitY
                    dx = -dx
                    bounces++
                    hitSomething = true
                    break
                }

                px = nx
                py = ny
                traveled += step
            }

            if (!hitSomething) {
                segments.add(Segment(PointF(origin.x, origin.y), PointF(px, py)))
                return segments
            }
        }
        return segments
    }
}
