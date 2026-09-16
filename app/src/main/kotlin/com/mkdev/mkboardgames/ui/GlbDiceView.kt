package com.mkdev.mkboardgames.ui

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.widget.FrameLayout
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.sin
import kotlin.random.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES renderer for the supplied embedded glTF dice model.
 */
class GlbDiceView(context: Context) : FrameLayout(context) {
    private val glSurfaceView = GLSurfaceView(context)

    var value: Int = 1
        set(newValue) {
            field = newValue.coerceIn(1, 6)
        }
    var facesOppositeSide: Boolean = false
        set(value) {
            field = value
            glRenderer.setFacingRotation(if (value) 180f else 0f)
        }
    var isRolling: Boolean = false
        private set
    var onRoll: (() -> Unit)? = null

    private var animator: ValueAnimator? = null
    private var rollGeneration = 0
    private var rotationX = -18f
    private var rotationY = -28f
    private var rotationZ = 0f
    private val glRenderer = DiceRenderer(context.applicationContext)

    init {
        setBackgroundColor(Color.TRANSPARENT)
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        glSurfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)
        glSurfaceView.setZOrderOnTop(true)
        glSurfaceView.setRenderer(glRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        glSurfaceView.isClickable = true
        glSurfaceView.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP && !isRolling) {
                view.performClick()
                onRoll?.invoke()
            }
            true
        }
        addView(
            glSurfaceView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun rollTo(
        nextValue: Int,
        motionDirection: MotionDiceDirection = MotionDiceDirection.UP,
        onFinished: () -> Unit,
    ) {
        val generation = ++rollGeneration
        animator?.cancel()
        isRolling = true
        val targetValue = nextValue.coerceIn(1, 6)
        val target = DiceOrientation.forValue(targetValue)
        val startX = rotationX
        val startY = rotationY
        val startZ = rotationZ
        val spin = RollSpin.random(motionDirection)
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
            // Give the tumble enough time to read as a physical throw. The
            // die still accelerates into the roll, then settles cleanly on
            // the predetermined face instead of snapping there too quickly.
            duration = 680L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val progress = it.animatedFraction
                val tumble = sin(progress * Math.PI).toFloat()
                val sizePulse = 1f + 0.12f * tumble
                rotationX = startX +
                    (target.x - startX + spin.x) * progress +
                    (tiltX + spin.wobbleX) * tumble
                rotationY = startY +
                    (target.y - startY + spin.y) * progress +
                    (tiltY + spin.wobbleY) * tumble
                rotationZ = startZ +
                    (target.z - startZ + spin.z) * progress +
                    spin.wobbleZ * tumble
                glRenderer.setAnimationScale(sizePulse)
                glRenderer.setRotation(rotationX, rotationY, rotationZ)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (generation != rollGeneration) return
                    value = targetValue
                    isRolling = false
                    animator = null
                    rotationX = target.x
                    rotationY = target.y
                    rotationZ = target.z
                    glRenderer.setAnimationScale(1f)
                    glRenderer.setRotation(rotationX, rotationY, rotationZ)
                    onFinished()
                }
            })
            start()
        }
    }

    fun cancelRoll() {
        rollGeneration++
        animator?.cancel()
        animator = null
        isRolling = false
        glRenderer.setAnimationScale(1f)
        glRenderer.setRotation(rotationX, rotationY, rotationZ)
    }

    /**
     * GLSurfaceView does not receive Activity lifecycle callbacks through its
     * FrameLayout parent. Forward them explicitly so returning to the app
     * cannot leave the renderer paused on a partially rolled frame.
     */
    fun onHostPause() {
        cancelRoll()
        glSurfaceView.onPause()
    }

    fun onHostResume() {
        glSurfaceView.onResume()
        glRenderer.setAnimationScale(1f)
        glRenderer.setRotation(rotationX, rotationY, rotationZ)
    }

    /**
     * GLSurfaceView can remain above its parent when it uses z-order-on-top.
     * Dialog transitions must hide the surface itself, not just this container.
     */
    fun setGameplayVisible(visible: Boolean) {
        glSurfaceView.visibility = if (visible) VISIBLE else INVISIBLE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !isRolling) {
            performClick()
            onRoll?.invoke()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        cancelRoll()
        super.onDetachedFromWindow()
    }

    private class DiceRenderer(
        private val context: Context,
    ) : GLSurfaceView.Renderer {
        private var model: GltfModel? = null
        private var program = 0
        private var positionHandle = 0
        private var normalHandle = 0
        private var texCoordHandle = 0
        private var mvpHandle = 0
        private var modelHandle = 0
        private var textureHandle = 0
        private var useTextureHandle = 0
        private var baseColorHandle = 0
        private var projection = FloatArray(16)
        private var view = FloatArray(16)
        private var width = 1
        private var height = 1
        @Volatile private var rotationX = -18f
        @Volatile private var rotationY = -28f
        @Volatile private var rotationZ = 0f
        @Volatile private var facingRotation = 0f
        @Volatile private var animationScale = 1f

        fun setRotation(x: Float, y: Float, z: Float) {
            rotationX = x
            rotationY = y
            rotationZ = z
        }

        fun setFacingRotation(rotation: Float) {
            facingRotation = rotation
        }

        fun setAnimationScale(scale: Float) {
            animationScale = scale.coerceIn(1f, 1.2f)
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)

            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
            modelHandle = GLES20.glGetUniformLocation(program, "uModel")
            textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
            useTextureHandle = GLES20.glGetUniformLocation(program, "uUseTexture")
            baseColorHandle = GLES20.glGetUniformLocation(program, "uBaseColor")

            model = try {
                GltfModel.load(context.assets, MODEL_ASSET)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to load Ludo dice model", error)
                null
            }
            model?.uploadTextures()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            this.width = width.coerceAtLeast(1)
            this.height = height.coerceAtLeast(1)
            GLES20.glViewport(0, 0, this.width, this.height)
            Matrix.perspectiveM(
                projection,
                0,
                34f,
                this.width.toFloat() / this.height.toFloat(),
                0.1f,
                100f,
            )
            Matrix.setLookAtM(
                view,
                0,
                0f, 0f, 3.15f,
                0f, 0f, 0f,
                0f, 1f, 0f,
            )
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val currentModel = model ?: return

            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
            Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, rotationZ, 0f, 0f, 1f)
            Matrix.rotateM(modelMatrix, 0, facingRotation, 0f, 0f, 1f)
            Matrix.scaleM(
                modelMatrix,
                0,
                currentModel.renderScale * animationScale,
                currentModel.renderScale * animationScale,
                currentModel.renderScale * animationScale,
            )
            Matrix.translateM(
                modelMatrix,
                0,
                -currentModel.centerX,
                -currentModel.centerY,
                -currentModel.centerZ,
            )

            val viewModel = FloatArray(16)
            val mvp = FloatArray(16)
            Matrix.multiplyMM(viewModel, 0, view, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)

            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, modelMatrix, 0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glEnableVertexAttribArray(texCoordHandle)

            for (part in currentModel.parts) {
                part.positions.position(0)
                GLES20.glVertexAttribPointer(
                    positionHandle,
                    3,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    part.positions,
                )

                part.normals.position(0)
                GLES20.glVertexAttribPointer(
                    normalHandle,
                    3,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    part.normals,
                )

                part.texCoords.position(0)
                GLES20.glVertexAttribPointer(
                    texCoordHandle,
                    2,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    part.texCoords,
                )

                GLES20.glUniform1f(
                    useTextureHandle,
                    if (part.textureId == 0) 0f else 1f,
                )
                GLES20.glUniform4fv(baseColorHandle, 1, part.baseColor, 0)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, part.textureId)
                GLES20.glUniform1i(textureHandle, 0)
                part.indices.position(0)
                GLES20.glDrawElements(
                    GLES20.GL_TRIANGLES,
                    part.indexCount,
                    GLES20.GL_UNSIGNED_SHORT,
                    part.indices,
                )
            }
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(normalHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            return GLES20.glCreateProgram().also { linked ->
                GLES20.glAttachShader(linked, vertex)
                GLES20.glAttachShader(linked, fragment)
                GLES20.glLinkProgram(linked)
                val status = IntArray(1)
                GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
                check(status[0] == GLES20.GL_TRUE) {
                    GLES20.glGetProgramInfoLog(linked)
                }
                GLES20.glDeleteShader(vertex)
                GLES20.glDeleteShader(fragment)
            }
        }

        private fun compileShader(type: Int, source: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val status = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
                check(status[0] == GLES20.GL_TRUE) {
                    GLES20.glGetShaderInfoLog(shader)
                }
            }
        }
    }

    private data class GlbModel(
        val positions: FloatBuffer,
        val normals: FloatBuffer,
        val texCoords: FloatBuffer,
        val indices: ShortBuffer,
        val indexCount: Int,
        val baseColor: Bitmap,
        val baseColorUsesAlpha: Boolean,
        val centerX: Float,
        val centerY: Float,
        val centerZ: Float,
        val renderScale: Float,
    ) {
        var textureId: Int = 0

        fun uploadTexture() {
            val handles = IntArray(1)
            GLES20.glGenTextures(1, handles, 0)
            textureId = handles[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR_MIPMAP_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_REPEAT,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_REPEAT,
            )
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, baseColor, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            baseColor.recycle()
        }

        companion object {
            fun load(input: InputStream): GlbModel {
                val bytes = input.use { it.readBytes() }
                val parsed = GlbParser(bytes)
                val json = parsed.json
                val primitive = json.getJSONArray("meshes")
                    .getJSONObject(0)
                    .getJSONArray("primitives")
                    .getJSONObject(0)
                val attributes = primitive.getJSONObject("attributes")
                val positions = parsed.readFloats(attributes.getInt("POSITION"), 3)
                val normals = parsed.readFloats(attributes.getInt("NORMAL"), 3)
                val texCoords = parsed.readFloats(attributes.getInt("TEXCOORD_0"), 2)
                val indexValues = parsed.readIndices(primitive.getInt("indices"))
                val textureBytes = parsed.readBaseColorImage(json)
                val decodedBitmap = BitmapFactory.decodeByteArray(
                    textureBytes,
                    0,
                    textureBytes.size,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inScaled = false
                    },
                ) ?: error("The GLB base-color texture could not be decoded")
                val baseColorUsesAlpha = decodedBitmap.config == Bitmap.Config.ALPHA_8
                val bitmap = decodedBitmap.let { decoded ->
                    if (baseColorUsesAlpha) {
                        decoded
                    } else if (decoded.config == Bitmap.Config.ARGB_8888) {
                        decoded
                    } else {
                        decoded.copy(Bitmap.Config.ARGB_8888, false)
                            ?: error("The GLB base-color texture could not be converted")
                    }
                }

                var minX = Float.POSITIVE_INFINITY
                var minY = Float.POSITIVE_INFINITY
                var minZ = Float.POSITIVE_INFINITY
                var maxX = Float.NEGATIVE_INFINITY
                var maxY = Float.NEGATIVE_INFINITY
                var maxZ = Float.NEGATIVE_INFINITY
                for (index in positions.indices step 3) {
                    minX = minOf(minX, positions[index])
                    minY = minOf(minY, positions[index + 1])
                    minZ = minOf(minZ, positions[index + 2])
                    maxX = maxOf(maxX, positions[index])
                    maxY = maxOf(maxY, positions[index + 1])
                    maxZ = maxOf(maxZ, positions[index + 2])
                }
                val maxDimension = maxOf(maxX - minX, maxY - minY, maxZ - minZ)
                    .coerceAtLeast(0.0001f)

                return GlbModel(
                    positions = floatBuffer(positions),
                    normals = floatBuffer(normals),
                    texCoords = floatBuffer(texCoords),
                    indices = shortBuffer(indexValues.map { it.toShort() }.toShortArray()),
                    indexCount = indexValues.size,
                    baseColor = bitmap,
                    baseColorUsesAlpha = baseColorUsesAlpha,
                    centerX = (minX + maxX) / 2f,
                    centerY = (minY + maxY) / 2f,
                    centerZ = (minZ + maxZ) / 2f,
                    renderScale = 1.08f / maxDimension,
                )
            }

            private fun floatBuffer(values: FloatArray): FloatBuffer =
                ByteBuffer.allocateDirect(values.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(values)
                        position(0)
                    }

            private fun shortBuffer(values: ShortArray): ShortBuffer =
                ByteBuffer.allocateDirect(values.size * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                    .apply {
                        put(values)
                        position(0)
                    }
        }
    }

    private class GlbParser(
        private val bytes: ByteArray,
    ) {
        val json: JSONObject
        private val binary: ByteArray

        init {
            require(bytes.size >= 20) { "GLB is too small" }
            require(String(bytes, 0, 4, Charsets.US_ASCII) == "glTF") { "Not a GLB file" }
            val chunks = mutableListOf<Pair<Int, ByteArray>>()
            var offset = 12
            while (offset + 8 <= bytes.size) {
                val chunkLength = readInt(offset)
                val chunkType = readInt(offset + 4)
                offset += 8
                require(offset + chunkLength <= bytes.size) { "GLB chunk exceeds file" }
                chunks += chunkType to bytes.copyOfRange(offset, offset + chunkLength)
                offset += chunkLength
            }
            val jsonChunk = chunks.firstOrNull { it.first == JSON_CHUNK }
                ?: error("GLB JSON chunk missing")
            binary = chunks.firstOrNull { it.first == BIN_CHUNK }?.second
                ?: error("GLB binary chunk missing")
            json = JSONObject(String(jsonChunk.second, Charsets.UTF_8).trimEnd('\u0000', ' ', '\n', '\r', '\t'))
        }

        fun readFloats(accessorIndex: Int, components: Int): FloatArray {
            val accessor = json.getJSONArray("accessors").getJSONObject(accessorIndex)
            val view = json.getJSONArray("bufferViews")
                .getJSONObject(accessor.getInt("bufferView"))
            val count = accessor.getInt("count")
            val componentSize = 4
            val stride = view.optInt("byteStride", components * componentSize)
            val start = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val output = FloatArray(count * components)
            val buffer = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN)
            for (index in 0 until count) {
                val source = start + index * stride
                for (component in 0 until components) {
                    output[index * components + component] = buffer.getFloat(
                        source + component * componentSize,
                    )
                }
            }
            return output
        }

        fun readIndices(accessorIndex: Int): IntArray {
            val accessor = json.getJSONArray("accessors").getJSONObject(accessorIndex)
            val view = json.getJSONArray("bufferViews")
                .getJSONObject(accessor.getInt("bufferView"))
            val count = accessor.getInt("count")
            val componentType = accessor.getInt("componentType")
            val componentSize = when (componentType) {
                5121 -> 1
                5123 -> 2
                5125 -> 4
                else -> error("Unsupported GLB index type $componentType")
            }
            val stride = view.optInt("byteStride", componentSize)
            val start = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val buffer = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN)
            return IntArray(count) { index ->
                val source = start + index * stride
                when (componentType) {
                    5121 -> buffer.get(source).toInt() and 0xFF
                    5123 -> buffer.getShort(source).toInt() and 0xFFFF
                    else -> buffer.getInt(source)
                }
            }
        }

        fun readBaseColorImage(document: JSONObject): ByteArray {
            val material = document.getJSONArray("materials").getJSONObject(0)
            val pbr = material.getJSONObject("pbrMetallicRoughness")
            val textureIndex = pbr.getJSONObject("baseColorTexture").getInt("index")
            val imageIndex = document.getJSONArray("textures")
                .getJSONObject(textureIndex)
                .getInt("source")
            val image = document.getJSONArray("images").getJSONObject(imageIndex)
            val view = document.getJSONArray("bufferViews")
                .getJSONObject(image.getInt("bufferView"))
            val start = view.optInt("byteOffset", 0)
            return binary.copyOfRange(start, start + view.getInt("byteLength"))
        }

        private fun readInt(offset: Int): Int =
            ByteBuffer.wrap(bytes, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int

        companion object {
            private const val JSON_CHUNK = 0x4E4F534A
            private const val BIN_CHUNK = 0x004E4942
        }
    }

    private data class MeshPart(
        val positions: FloatBuffer,
        val normals: FloatBuffer,
        val texCoords: FloatBuffer,
        val indices: ShortBuffer,
        val indexCount: Int,
        val baseColor: FloatArray,
        private val baseColorBitmap: Bitmap?,
    ) {
        var textureId: Int = 0

        fun uploadTexture() {
            val bitmap = baseColorBitmap ?: return
            val handles = IntArray(1)
            GLES20.glGenTextures(1, handles, 0)
            textureId = handles[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR_MIPMAP_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_REPEAT,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_REPEAT,
            )
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            bitmap.recycle()
        }
    }

    private data class GltfModel(
        val parts: List<MeshPart>,
        val centerX: Float,
        val centerY: Float,
        val centerZ: Float,
        val renderScale: Float,
    ) {
        fun uploadTextures() {
            parts.forEach { it.uploadTexture() }
        }

        companion object {
            fun load(assets: AssetManager, assetPath: String): GltfModel {
                val document = assets.open(assetPath).bufferedReader(Charsets.UTF_8).use {
                    JSONObject(it.readText())
                }
                val assetDirectory = assetPath.substringBeforeLast('/', "")
                val bufferUri = document
                    .getJSONArray("buffers")
                    .getJSONObject(0)
                    .getString("uri")
                val binary = assets.open(resolvePath(assetDirectory, bufferUri)).use {
                    it.readBytes()
                }
                val images = document.optJSONArray("images")
                val textures = document.optJSONArray("textures")
                val materials = document.optJSONArray("materials")
                val imageCache = mutableMapOf<Int, Bitmap?>()
                val parts = mutableListOf<MeshPart>()

                val meshes = document.getJSONArray("meshes")
                for (meshIndex in 0 until meshes.length()) {
                    val primitives = meshes.getJSONObject(meshIndex).getJSONArray("primitives")
                    for (primitiveIndex in 0 until primitives.length()) {
                        val primitive = primitives.getJSONObject(primitiveIndex)
                        val attributes = primitive.getJSONObject("attributes")
                        val positions = readFloats(
                            document,
                            binary,
                            attributes.getInt("POSITION"),
                            3,
                        )
                        val normals = attributes.optInt("NORMAL", -1).takeIf { it >= 0 }?.let {
                            readFloats(document, binary, it, 3)
                        } ?: FloatArray(positions.size) { index ->
                            if (index % 3 == 2) 1f else 0f
                        }
                        val texCoords = attributes.optInt("TEXCOORD_0", -1).takeIf { it >= 0 }?.let {
                            readFloats(document, binary, it, 2)
                        } ?: FloatArray((positions.size / 3) * 2)
                        val indexValues = readIndices(
                            document,
                            binary,
                            primitive.getInt("indices"),
                        )
                        require(indexValues.maxOrNull() ?: 0 <= 0xFFFF) {
                            "The Ludo dice contains more than 65535 indices"
                        }

                        val materialIndex = primitive.optInt("material", -1)
                        val material = materials?.optJSONObject(materialIndex)
                        val pbr = material?.optJSONObject("pbrMetallicRoughness")
                        val factor = pbr?.optJSONArray("baseColorFactor")
                        val baseColor = FloatArray(4) { channel ->
                            factor?.optDouble(channel, 1.0)?.toFloat() ?: 1f
                        }
                        val textureIndex = pbr
                            ?.optJSONObject("baseColorTexture")
                            ?.optInt("index", -1)
                            ?: -1
                        val imageIndex = textures
                            ?.optJSONObject(textureIndex)
                            ?.optInt("source", -1)
                            ?: -1
                        val bitmap = if (imageIndex >= 0 && images != null) {
                            imageCache.getOrPut(imageIndex) {
                                val uri = images.getJSONObject(imageIndex).getString("uri")
                                BitmapFactory.decodeStream(
                                    assets.open(resolvePath(assetDirectory, uri)),
                                    null,
                                    BitmapFactory.Options().apply { inScaled = false },
                                )
                            }
                        } else {
                            null
                        }

                        parts += MeshPart(
                            positions = floatBuffer(positions),
                            normals = floatBuffer(normals),
                            texCoords = floatBuffer(texCoords),
                            indices = shortBuffer(indexValues.map { it.toShort() }.toShortArray()),
                            indexCount = indexValues.size,
                            baseColor = baseColor,
                            baseColorBitmap = bitmap,
                        )
                    }
                }

                require(parts.isNotEmpty()) { "The Ludo dice has no renderable meshes" }
                var minX = Float.POSITIVE_INFINITY
                var minY = Float.POSITIVE_INFINITY
                var minZ = Float.POSITIVE_INFINITY
                var maxX = Float.NEGATIVE_INFINITY
                var maxY = Float.NEGATIVE_INFINITY
                var maxZ = Float.NEGATIVE_INFINITY
                parts.forEach { part ->
                    val positions = part.positions
                    for (index in 0 until positions.limit() step 3) {
                        minX = minOf(minX, positions.get(index))
                        minY = minOf(minY, positions.get(index + 1))
                        minZ = minOf(minZ, positions.get(index + 2))
                        maxX = maxOf(maxX, positions.get(index))
                        maxY = maxOf(maxY, positions.get(index + 1))
                        maxZ = maxOf(maxZ, positions.get(index + 2))
                    }
                }
                val maxDimension = maxOf(maxX - minX, maxY - minY, maxZ - minZ)
                    .coerceAtLeast(0.0001f)

                return GltfModel(
                    parts = parts,
                    centerX = (minX + maxX) / 2f,
                    centerY = (minY + maxY) / 2f,
                    centerZ = (minZ + maxZ) / 2f,
                    renderScale = 1.08f / maxDimension,
                )
            }

            private fun resolvePath(directory: String, uri: String): String =
                if (directory.isEmpty()) uri else "$directory/$uri"

            private fun readFloats(
                document: JSONObject,
                binary: ByteArray,
                accessorIndex: Int,
                components: Int,
            ): FloatArray {
                val accessor = document.getJSONArray("accessors").getJSONObject(accessorIndex)
                require(accessor.getInt("componentType") == 5126) {
                    "Only FLOAT glTF attributes are supported"
                }
                val view = document.getJSONArray("bufferViews")
                    .getJSONObject(accessor.getInt("bufferView"))
                val count = accessor.getInt("count")
                val componentSize = 4
                val stride = view.optInt("byteStride", components * componentSize)
                val start = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
                val output = FloatArray(count * components)
                val buffer = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN)
                for (index in 0 until count) {
                    val source = start + index * stride
                    for (component in 0 until components) {
                        output[index * components + component] = buffer.getFloat(
                            source + component * componentSize,
                        )
                    }
                }
                return output
            }

            private fun readIndices(
                document: JSONObject,
                binary: ByteArray,
                accessorIndex: Int,
            ): IntArray {
                val accessor = document.getJSONArray("accessors").getJSONObject(accessorIndex)
                val view = document.getJSONArray("bufferViews")
                    .getJSONObject(accessor.getInt("bufferView"))
                val count = accessor.getInt("count")
                val componentType = accessor.getInt("componentType")
                val componentSize = when (componentType) {
                    5121 -> 1
                    5123 -> 2
                    5125 -> 4
                    else -> error("Unsupported glTF index type $componentType")
                }
                val stride = view.optInt("byteStride", componentSize)
                val start = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
                val buffer = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN)
                return IntArray(count) { index ->
                    val source = start + index * stride
                    when (componentType) {
                        5121 -> buffer.get(source).toInt() and 0xFF
                        5123 -> buffer.getShort(source).toInt() and 0xFFFF
                        else -> buffer.getInt(source)
                    }
                }
            }

            private fun floatBuffer(values: FloatArray): FloatBuffer =
                ByteBuffer.allocateDirect(values.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(values)
                        position(0)
                    }

            private fun shortBuffer(values: ShortArray): ShortBuffer =
                ByteBuffer.allocateDirect(values.size * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                    .apply {
                        put(values)
                        position(0)
                    }
        }
    }

    private data class DiceOrientation(
        val x: Float,
        val y: Float,
        val z: Float = 0f,
    ) {
        companion object {
            fun forValue(value: Int): DiceOrientation = when (value.coerceIn(1, 6)) {
                1 -> DiceOrientation(90f, 0f)
                2 -> DiceOrientation(0f, 0f)
                3 -> DiceOrientation(0f, -90f)
                4 -> DiceOrientation(0f, 90f)
                5 -> DiceOrientation(0f, 180f)
                else -> DiceOrientation(-90f, 0f)
            }
        }
    }

    private data class RollSpin(
        val x: Float,
        val y: Float,
        val z: Float,
        val wobbleX: Float,
        val wobbleY: Float,
        val wobbleZ: Float,
    ) {
        companion object {
            fun random(direction: MotionDiceDirection): RollSpin {
                val sign = { if (Random.nextBoolean()) 1f else -1f }
                val xSign = sign()
                val ySign = sign()
                val zSign = sign()
                val directionBoost = when (direction) {
                    MotionDiceDirection.LEFT, MotionDiceDirection.TOP_LEFT -> -1f
                    MotionDiceDirection.RIGHT, MotionDiceDirection.TOP_RIGHT -> 1f
                    MotionDiceDirection.UP -> 0f
                }
                return RollSpin(
                    x = Random.nextInt(2, 5) * 360f * xSign,
                    y = Random.nextInt(2, 5) * 360f * ySign,
                    z = Random.nextInt(2, 4) * 360f * zSign,
                    wobbleX = (8f + Random.nextFloat() * 12f) * xSign,
                    wobbleY = (8f + Random.nextFloat() * 12f) * ySign +
                        directionBoost * 8f,
                    wobbleZ = (10f + Random.nextFloat() * 16f) * zSign,
                )
            }
        }
    }

    companion object {
        private const val TAG = "GlbDiceView"
        private const val MODEL_ASSET = "low_poly_dice/scene.gltf"

        private const val VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vNormal = mat3(uModel) * aNormal;
                vTexCoord = vec2(aTexCoord.x, 1.0 - aTexCoord.y);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform float uUseTexture;
            uniform vec4 uBaseColor;
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            void main() {
                vec4 base = mix(uBaseColor, texture2D(uTexture, vTexCoord), uUseTexture);
                vec3 normal = normalize(vNormal);
                vec3 lightDirection = normalize(vec3(-0.45, 0.8, 1.0));
                float diffuse = 0.48 + 0.52 * max(dot(normal, lightDirection), 0.0);
                gl_FragColor = vec4(base.rgb * diffuse, 1.0);
            }
        """
    }
}