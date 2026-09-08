package com.mkdev.mkboardgames.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.BlurMaskFilter

/**
 * Shared Brown Home card frame used by the game catalogue and mode selection.
 * Keeping this in one renderer prevents the two surfaces from drifting apart.
 */
internal class BrownWoodCardRenderer(private val dp: Float) {

    private val cardWoodPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardWoodPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardWoodInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardWoodInnerPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardWoodOuterHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * dp
        color = Color.argb(220, 173, 113, 70)
    }
    private val cardWoodOuterShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * dp
        color = Color.argb(230, 47, 19, 10)
    }
    private val cardWoodInnerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
        color = Color.argb(210, 186, 124, 75)
    }
    private val cardWoodInnerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * dp
        color = Color.argb(220, 41, 16, 9)
    }
    private val cardStudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStudHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 232, 178, 112)
    }

    fun draw(canvas: Canvas, rect: RectF, pressed: Boolean) {
        val radius = 11f * dp
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(155, 0, 0, 0)
            maskFilter = BlurMaskFilter(5f * dp, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            RectF(rect.left + 2.5f * dp, rect.top + 5f * dp, rect.right + 2.5f * dp, rect.bottom + 5f * dp),
            radius, radius, shadowPaint
        )

        val outerPaint = if (pressed) cardWoodPressedPaint else cardWoodPaint
        outerPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            if (pressed) {
                intArrayOf(
                    Color.rgb(119, 70, 39),
                    Color.rgb(76, 37, 20),
                    Color.rgb(105, 57, 30),
                )
            } else {
                intArrayOf(
                    Color.rgb(139, 83, 46),
                    Color.rgb(77, 37, 20),
                    Color.rgb(119, 64, 34),
                )
            },
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, radius, radius, outerPaint)
        canvas.drawRoundRect(
            RectF(rect.left + 0.8f * dp, rect.top + 0.8f * dp, rect.right - 0.8f * dp, rect.bottom - 0.8f * dp),
            radius - 0.5f * dp, radius - 0.5f * dp, cardWoodOuterHighlightPaint
        )
        canvas.drawRoundRect(
            RectF(rect.left + 2f * dp, rect.top + 2f * dp, rect.right - 2f * dp, rect.bottom - 2f * dp),
            radius - 1f * dp, radius - 1f * dp, cardWoodOuterShadowPaint
        )

        val inner = RectF(
            rect.left + 4.5f * dp, rect.top + 4.5f * dp,
            rect.right - 4.5f * dp, rect.bottom - 4.5f * dp,
        )
        val innerPaint = if (pressed) cardWoodInnerPressedPaint else cardWoodInnerPaint
        innerPaint.shader = LinearGradient(
            inner.left, inner.top, inner.right, inner.bottom,
            intArrayOf(Color.rgb(67, 30, 16), Color.rgb(42, 18, 10), Color.rgb(74, 35, 18)),
            null,
            Shader.TileMode.CLAMP,
        )
        val innerRadius = 7f * dp
        canvas.drawRoundRect(inner, innerRadius, innerRadius, innerPaint)
        canvas.drawRoundRect(
            RectF(inner.left + 1f * dp, inner.top + 1f * dp, inner.right - 1f * dp, inner.bottom - 1f * dp),
            innerRadius - 0.5f * dp, innerRadius - 0.5f * dp, cardWoodInnerHighlightPaint
        )
        canvas.drawRoundRect(
            RectF(inner.left + 2.5f * dp, inner.top + 2.5f * dp, inner.right - 2.5f * dp, inner.bottom - 2.5f * dp),
            innerRadius - 1.5f * dp, innerRadius - 1.5f * dp, cardWoodInnerShadowPaint
        )

        val studRadius = 1.8f * dp
        val studInset = 6.5f * dp
        cardStudPaint.color = Color.rgb(111, 62, 31)
        listOf(
            rect.left + studInset to rect.top + studInset,
            rect.right - studInset to rect.top + studInset,
            rect.left + studInset to rect.bottom - studInset,
            rect.right - studInset to rect.bottom - studInset,
        ).forEach { (x, y) ->
            canvas.drawCircle(x, y, studRadius, cardStudPaint)
            canvas.drawCircle(
                x - studRadius * 0.35f,
                y - studRadius * 0.35f,
                studRadius * 0.35f,
                cardStudHighlightPaint,
            )
        }
    }
}