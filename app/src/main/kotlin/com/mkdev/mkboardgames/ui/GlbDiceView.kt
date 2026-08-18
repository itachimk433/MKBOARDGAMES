package com.mkdev.mkboardgames.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.sin
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES renderer for the supplied embedded glTF dice model.
 *
 * The GLB contains its own geometry and base-color texture, so the Ludo die
 * is no longer approximated by a software-rendered cube.
 */
class GlbDiceView(context: Context) : GLSurfaceView(context) {
    var value: Int = 1
        set(newValue) {
            field = newValue.coerceIn(1, 6)
        }
    var isRolling: Boolean = false
        private set
    var onRoll: (() -> Unit)? = null

    private var animator: ValueAnimator? = null
    private var rotationX = -18f
    private var rotationY = -28f
    private val glRenderer = DiceRenderer(context.applicationContext)

    init {
        setEGLContextClientVersion(2)
        setRenderer(glRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
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
                glRenderer.setRotation(rotationX, rotationY)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    value = targetValue
                    isRolling = false
                    rotationX = target.x
                    rotationY = target.y
                    glRenderer.setRotation(rotationX, rotationY)
                    onFinished()
                }
            })
            start()
        }
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
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private class DiceRenderer(
        private val context: Context,
    ) : GLSurfaceView.Renderer {
        private var model: GlbModel? = null
        private var program = 0
        private var positionHandle = 0
        private var normalHandle = 0
        private var texCoordHandle = 0
        private var mvpHandle = 0
        private var modelHandle = 0
        private var textureHandle = 0
        private var projection = FloatArray(16)
        private var view = FloatArray(16)
        private var width = 1
        private var height = 1
        @Volatile private var rotationX = -18f
        @Volatile private var rotationY = -28f

        fun setRotation(x: Float, y: Float) {
            rotationX = x
            rotationY = y
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(16f / 255f, 21f / 255f, 26f / 255f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)

            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
            modelHandle = GLES20.glGetUniformLocation(program, "uModel")
            textureHandle = GLES20.glGetUniformLocation(program, "uTexture")

            model = runCatching {
                GlbModel.load(context.assets.open(MODEL_ASSET))
            }.getOrNull()
            model?.uploadTexture()
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
            Matrix.scaleM(modelMatrix, 0, 50f, 50f, 50f)
            Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
            Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)

            val viewModel = FloatArray(16)
            val mvp = FloatArray(16)
            Matrix.multiplyMM(viewModel, 0, view, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)

            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, modelMatrix, 0)

            currentModel.positions.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                currentModel.positions,
            )

            currentModel.normals.position(0)
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(
                normalHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                currentModel.normals,
            )

            currentModel.texCoords.position(0)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(
                texCoordHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                currentModel.texCoords,
            )

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, currentModel.textureId)
            GLES20.glUniform1i(textureHandle, 0)
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                currentModel.indexCount,
                GLES20.GL_UNSIGNED_SHORT,
                currentModel.indices,
            )

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
                val bitmap = BitmapFactory.decodeByteArray(
                    textureBytes,
                    0,
                    textureBytes.size,
                ) ?: error("The GLB base-color texture could not be decoded")

                return GlbModel(
                    positions = floatBuffer(positions),
                    normals = floatBuffer(normals),
                    texCoords = floatBuffer(texCoords),
                    indices = shortBuffer(indexValues.map { it.toShort() }.toShortArray()),
                    indexCount = indexValues.size,
                    baseColor = bitmap,
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

    private data class DiceOrientation(
        val x: Float,
        val y: Float,
    ) {
        companion object {
            fun forValue(value: Int): DiceOrientation = when (value.coerceIn(1, 6)) {
                1 -> DiceOrientation(0f, 90f)
                2 -> DiceOrientation(0f, -90f)
                3 -> DiceOrientation(0f, 0f)
                4 -> DiceOrientation(90f, 0f)
                5 -> DiceOrientation(0f, 180f)
                else -> DiceOrientation(-90f, 0f)
            }
        }
    }

    companion object {
        private const val MODEL_ASSET = "perfect_little_dice_3cm_1787030625587.glb"

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
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            void main() {
                vec4 base = texture2D(uTexture, vTexCoord);
                vec3 normal = normalize(vNormal);
                vec3 lightDirection = normalize(vec3(-0.45, 0.8, 1.0));
                float diffuse = 0.48 + 0.52 * max(dot(normal, lightDirection), 0.0);
                gl_FragColor = vec4(base.rgb * diffuse, base.a);
            }
        """
    }
}