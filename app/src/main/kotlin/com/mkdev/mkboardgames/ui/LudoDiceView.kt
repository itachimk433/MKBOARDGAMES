package com.mkdev.mkboardgames.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private data class DiceOrientation(
    val x: Float,
    val y: Float,
) {
    companion object {
        fun forValue(value: Int): DiceOrientation = when (value.coerceIn(1, 6)) {
            1 -> DiceOrientation(0f, 90f)
            2 -> DiceOrientation(0f, 0f)
            3 -> DiceOrientation(90f, 0f)
            4 -> DiceOrientation(0f, 180f)
            5 -> DiceOrientation(-90f, 0f)
            else -> DiceOrientation(0f, -90f)
        }
    }
}

enum class MotionDiceDirection {
    UP, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT
}

/**
 * A software-rendered perspective die using the face textures embedded in
 * dice.gltf. Drawing on a normal View keeps the die visible on devices where
 * an OpenGL surface is unavailable or unreliable.
 */
class LudoDiceView(context: Context) : View(context) {
    var value: Int = 1
    var isRolling: Boolean = false
        private set
    var onRoll: (() -> Unit)? = null
    var motionDiceEnabled: Boolean = false
    var onMotionToggle: ((Boolean) -> Unit)? = null

    private var animator: ValueAnimator? = null
    private var rotationX = -18f
    private var rotationY = -28f
    private var touchDown = false
    private var longPressTriggered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (touchDown && !isRolling) {
            longPressTriggered = true
            motionDiceEnabled = !motionDiceEnabled
            onMotionToggle?.invoke(motionDiceEnabled)
            invalidate()
        }
    }
    private val textures = loadDiceTextures(context)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 30, 38, 48)
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 0, 0, 0)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(170, 184, 198)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    init {
        isClickable = true
        setBackgroundColor(Color.rgb(16, 21, 26))
    }

    fun rollTo(
        nextValue: Int,
        motionDirection: MotionDiceDirection = MotionDiceDirection.UP,
        onFinished: () -> Unit,
    ) {
        animator?.cancel()
        isRolling = true
        val targetValue = nextValue.coerceIn(1, 6)
        val target = DiceOrientation.forValue(targetValue)
        val startX = rotationX
        val startY = rotationY
        val endY = target.y + 720f
        val tiltX = when (motionDirection) {
            MotionDiceDirection.TOP_LEFT, MotionDiceDirection.TOP_RIGHT -> -22f
            MotionDiceDirection.UP -> -12f
            else -> 0f
        }
        val tiltY = when (motionDirection) {
            MotionDiceDirection.LEFT, MotionDiceDirection.TOP_LEFT -> -28f
            MotionDiceDirection.RIGHT, MotionDiceDirection.TOP_RIGHT -> 28f
            else -> 0f
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedFraction
                val gestureTilt = sin(progress * Math.PI).toFloat()
                rotationX = startX + (target.x - startX) * progress + tiltX * gestureTilt
                rotationY = startY + (endY - startY) * progress + tiltY * gestureTilt
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    value = targetValue
                    isRolling = false
                    rotationX = target.x
                    rotationY = target.y
                    invalidate()
                    onFinished()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        labelPaint.textSize = 12f * density
        canvas.drawText(
            when {
                isRolling -> "Rolling..."
                motionDiceEnabled -> "Tap to roll · motion on · hold to toggle"
                else -> "Tap to roll · hold to enable motion"
            },
            width / 2f,
            22f * density,
            labelPaint,
        )

        val size = min(width, height).toFloat() * 0.58f
        val half = size / 2f
        val centerX = width / 2f
        val centerY = height * 0.61f
        val cameraDistance = 4.2f
        val vertices = cubeVertices()
        val projected = vertices.map { vertex ->
            val rotated = rotate(vertex)
            val perspective = cameraDistance / (cameraDistance - rotated.z)
            ProjectedPoint(
                x = centerX + rotated.x * half * perspective,
                y = centerY - rotated.y * half * perspective,
                depth = rotated.z,
            )
        }

        canvas.drawOval(
            RectF(
                centerX - half * 0.72f,
                centerY + half * 0.78f,
                centerX + half * 0.72f,
                centerY + half * 1.02f,
            ),
            shadowPaint,
        )

        cubeFaces()
            .sortedBy { face -> face.indices.map { projected[it].depth }.average() }
            .forEach { face ->
                val destination = FloatArray(8)
                face.indices.forEachIndexed { index, vertexIndex ->
                    destination[index * 2] = projected[vertexIndex].x
                    destination[index * 2 + 1] = projected[vertexIndex].y
                }
                drawTexturedFace(
                    canvas = canvas,
                    bitmap = textures[face.textureIndex],
                    destination = destination,
                )
            }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDown = true
                longPressTriggered = false
                mainHandler.postDelayed(longPressRunnable, 520L)
            }
            MotionEvent.ACTION_UP -> {
                touchDown = false
                mainHandler.removeCallbacks(longPressRunnable)
                if (!longPressTriggered && !isRolling) onRoll?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> {
                touchDown = false
                mainHandler.removeCallbacks(longPressRunnable)
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        mainHandler.removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    private fun drawTexturedFace(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: FloatArray,
    ) {
        val source = floatArrayOf(
            0f, 0f,
            bitmap.width.toFloat(), 0f,
            bitmap.width.toFloat(), bitmap.height.toFloat(),
            0f, bitmap.height.toFloat(),
        )
        val transform = Matrix()
        if (transform.setPolyToPoly(source, 0, destination, 0, 4)) {
            canvas.drawBitmap(bitmap, transform, bitmapPaint)
        }

        val outline = Path().apply {
            moveTo(destination[0], destination[1])
            lineTo(destination[2], destination[3])
            lineTo(destination[4], destination[5])
            lineTo(destination[6], destination[7])
            close()
        }
        canvas.drawPath(outline, edgePaint)
    }

    private fun cubeVertices(): List<CubePoint> = listOf(
        CubePoint(-1f, -1f, -1f),
        CubePoint(1f, -1f, -1f),
        CubePoint(1f, 1f, -1f),
        CubePoint(-1f, 1f, -1f),
        CubePoint(-1f, -1f, 1f),
        CubePoint(1f, -1f, 1f),
        CubePoint(1f, 1f, 1f),
        CubePoint(-1f, 1f, 1f),
    )

    private fun cubeFaces(): List<CubeFace> = listOf(
        CubeFace(0, intArrayOf(0, 4, 7, 3)),
        CubeFace(1, intArrayOf(4, 5, 6, 7)),
        CubeFace(2, intArrayOf(3, 7, 6, 2)),
        CubeFace(3, intArrayOf(1, 0, 3, 2)),
        CubeFace(4, intArrayOf(4, 0, 1, 5)),
        CubeFace(5, intArrayOf(5, 1, 2, 6)),
    )

    private fun rotate(point: CubePoint): CubePoint {
        val y = Math.toRadians(rotationY.toDouble())
        val x = Math.toRadians(rotationX.toDouble())
        val cosY = cos(y).toFloat()
        val sinY = sin(y).toFloat()
        val xAfterY = point.x * cosY + point.z * sinY
        val zAfterY = -point.x * sinY + point.z * cosY
        val cosX = cos(x).toFloat()
        val sinX = sin(x).toFloat()
        return CubePoint(
            x = xAfterY,
            y = point.y * cosX - zAfterY * sinX,
            z = point.y * sinX + zAfterY * cosX,
        )
    }

    private fun loadDiceTextures(context: Context): List<Bitmap> {
        return try {
            val json = context.assets.open("dice.gltf").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val images = JSONObject(json).getJSONArray("images")
            List(6) { index ->
                val uri = images.getJSONObject(index).getString("uri")
                val encoded = uri.substringAfter(',', "")
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: createFallbackTexture(index + 1)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to load die textures", error)
            List(6) { createFallbackTexture(it + 1) }
        }
    }

    private fun createFallbackTexture(number: Int): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val fallbackCanvas = Canvas(bitmap)
        fallbackCanvas.drawColor(Color.WHITE)
        val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 32, 41) }
        val points = when (number.coerceIn(1, 6)) {
            1 -> listOf(1 to 1)
            2 -> listOf(0 to 0, 2 to 2)
            3 -> listOf(0 to 0, 1 to 1, 2 to 2)
            4 -> listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2)
            5 -> listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2)
            else -> listOf(0 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 2 to 2)
        }
        val step = size / 4f
        for ((x, y) in points) {
            fallbackCanvas.drawCircle(
                step + x * step,
                step + y * step,
                size * 0.12f,
                pipPaint,
            )
        }
        return bitmap
    }

    private data class CubePoint(
        val x: Float,
        val y: Float,
        val z: Float,
    )

    private data class ProjectedPoint(
        val x: Float,
        val y: Float,
        val depth: Float,
    )

    private data class CubeFace(
        val textureIndex: Int,
        val indices: IntArray,
    )

    companion object {
        private const val TAG = "LudoDiceView"
    }
}