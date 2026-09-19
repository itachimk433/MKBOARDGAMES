package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Shared source artwork for every selectable wood button.
 *
 * The artwork contains only the plate. Labels, icons, and descriptions remain
 * vector/text content so they stay crisp, localisable, and reusable across
 * every game surface.
 */
internal object PlainGameButtonAssets {
    enum class Style {
        SHORT,
        LONG,
    }

    private var longBitmap: Bitmap? = null
    private var shortBitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun initialize(context: Context) {
        if (longBitmap != null && shortBitmap != null) return
        val appContext = context.applicationContext
        longBitmap = longBitmap ?: load(appContext, "game_button_long.webp")
        shortBitmap = shortBitmap ?: load(appContext, "game_button_short.webp")
    }

    fun styleFor(rect: RectF): Style? {
        if (rect.width() <= 0f || rect.height() <= 0f) return null
        return when {
            rect.width() / rect.height() >= 2.2f -> Style.LONG
            rect.width() / rect.height() >= 1.45f -> Style.SHORT
            else -> null
        }
    }

    fun heightForWidth(width: Float, style: Style): Float {
        val bitmap = bitmap(style) ?: return 0f
        return width * bitmap.height.toFloat() / bitmap.width.toFloat()
    }

    fun draw(
        canvas: Canvas,
        rect: RectF,
        style: Style,
        pressed: Boolean,
    ): Boolean {
        val bitmap = bitmap(style) ?: return false
        val offset = if (pressed) 2f else 0f
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(rect.left, rect.top + offset, rect.right, rect.bottom + offset),
            bitmapPaint,
        )
        return true
    }

    /**
     * Returns the bounds of the physical wood frame in the source artwork.
     *
     * The assets include a soft, partially transparent shadow/glow around the
     * plate. That footprint is intentionally not used for pressed-state
     * outlines: the outline should sit on the wood rim, like the board-choice
     * selection frame does.
     */
    fun visibleRect(
        rect: RectF,
        style: Style,
        pressed: Boolean,
        pressedOffset: Float = if (pressed) 2f else 0f,
    ): RectF {
        val bitmap = bitmap(style) ?: return RectF(rect)
        val source = when (style) {
            // Opaque wood-rim bounds (alpha >= 128), not the soft outer glow.
            Style.SHORT -> RectF(25f, 86f, 1576f, 893f)
            Style.LONG -> RectF(24f, 110f, 2150f, 618f)
        }
        val scaleX = rect.width() / bitmap.width.toFloat()
        val scaleY = rect.height() / bitmap.height.toFloat()
        return RectF(
            rect.left + source.left * scaleX,
            rect.top + pressedOffset + source.top * scaleY,
            rect.left + source.right * scaleX,
            rect.top + pressedOffset + source.bottom * scaleY,
        )
    }

    private fun bitmap(style: Style): Bitmap? = when (style) {
        Style.SHORT -> shortBitmap
        Style.LONG -> longBitmap
    }

    private fun load(context: Context, assetName: String): Bitmap? = runCatching {
        context.assets.open(assetName).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}