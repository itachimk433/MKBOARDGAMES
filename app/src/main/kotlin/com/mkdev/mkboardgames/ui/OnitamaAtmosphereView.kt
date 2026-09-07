package com.mkdev.mkboardgames.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The same animated blue board-room backdrop used by the other
 * Chess-family games, kept behind the Onitama board and card strips.
 */
class OnitamaAtmosphereView(context: Context) : View(context) {
    private val unit = resources.displayMetrics.density.coerceAtLeast(1f)
    private val backdrop = ChessFamilyBackdrop(unit)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 36_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        backdrop.draw(canvas, width.toFloat(), height.toFloat(), phase, rounded = false)
    }
}