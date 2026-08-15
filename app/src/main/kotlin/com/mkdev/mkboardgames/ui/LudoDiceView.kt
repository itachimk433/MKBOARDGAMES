package com.mkdev.mkboardgames.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Base64
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.max
import kotlin.math.min

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

/**
 * Displays the supplied dice.gltf asset using Android's built-in OpenGL ES 2.0
 * renderer. The view intentionally has no third-party 3D dependency so the
 * existing Android build remains small and works on the app's minSdk.
 */
class LudoDiceView(context: Context) : GLSurfaceView(context) {
    var value: Int = 1
    var isRolling: Boolean = false
        private set
    var onRoll: (() -> Unit)? = null

    private var animator: ValueAnimator? = null
    private val diceRenderer = DiceRenderer(context.applicationContext)

    init {
        setEGLContextClientVersion(2)
        setRenderer(diceRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        isClickable = true
    }

    fun rollTo(nextValue: Int, onFinished: () -> Unit) {
        animator?.cancel()
        isRolling = true
        val targetValue = nextValue.coerceIn(1, 6)
        val target = DiceOrientation.forValue(targetValue)
        val startX = diceRenderer.rotationX
        val startY = diceRenderer.rotationY
        val endY = target.y + 720f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedFraction
                diceRenderer.rotationX = startX + (target.x - startX) * progress
                diceRenderer.rotationY = startY + (endY - startY) * progress
                requestRender()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    value = targetValue
                    isRolling = false
                    diceRenderer.rotationX = target.x
                    diceRenderer.rotationY = target.y
                    requestRender()
                    onFinished()
                }
            })
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && !isRolling) {
            onRoll?.invoke()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}

private class DiceRenderer(
    private val context: Context,
) : GLSurfaceView.Renderer {
    @Volatile
    var rotationX: Float = 0f

    @Volatile
    var rotationY: Float = 0f

    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var program = 0
    private var positionHandle = -1
    private var texCoordHandle = -1
    private var mvpHandle = -1
    private var textureHandle = -1
    private var faces: List<GlFace> = emptyList()

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvp = FloatArray(16)

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        GLES20.glClearColor(0.063f, 0.082f, 0.102f, 1f)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        faces = loadModel()
    }

    override fun onSurfaceChanged(
        gl: javax.microedition.khronos.opengles.GL10?,
        width: Int,
        height: Int,
    ) {
        surfaceWidth = max(1, width)
        surfaceHeight = max(1, height)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        val aspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        Matrix.frustumM(projection, 0, -aspect, aspect, -1f, 1f, 2.2f, 8f)
        Matrix.setLookAtM(view, 0, 0f, 0.25f, 4.15f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0 || faces.isEmpty()) return

        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, rotationY, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, rotationX, 1f, 0f, 0f)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform1i(textureHandle, 0)

        for (face in faces) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, face.textureId)
            face.vertices.position(0)
            GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                face.vertices,
            )
            GLES20.glEnableVertexAttribArray(positionHandle)
            face.texCoords.position(0)
            GLES20.glVertexAttribPointer(
                texCoordHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                face.texCoords,
            )
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            face.indices.position(0)
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                face.indexCount,
                GLES20.GL_UNSIGNED_SHORT,
                face.indices,
            )
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun loadModel(): List<GlFace> {
        return try {
            val json = context.assets.open("dice.gltf").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(json)
            val buffers = loadBuffers(root.optJSONArray("buffers") ?: JSONArray())
            val rawFaces = mutableListOf<RawFace>()
            val boundsMin = floatArrayOf(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            val boundsMax = floatArrayOf(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)

            val scenes = root.optJSONArray("scenes")
            val sceneIndex = root.optInt("scene", 0)
            val sceneNodes = scenes?.optJSONObject(sceneIndex)?.optJSONArray("nodes")
            val roots = if (sceneNodes != null && sceneNodes.length() > 0) {
                (0 until sceneNodes.length()).map { sceneNodes.getInt(it) }
            } else {
                listOf(0)
            }
            val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
            for (nodeIndex in roots) {
                visitNode(root, nodeIndex, identity, buffers, rawFaces, boundsMin, boundsMax)
            }

            val extent = max(
                boundsMax[0] - boundsMin[0],
                max(boundsMax[1] - boundsMin[1], boundsMax[2] - boundsMin[2]),
            ).coerceAtLeast(0.001f)
            val center = floatArrayOf(
                (boundsMin[0] + boundsMax[0]) * 0.5f,
                (boundsMin[1] + boundsMax[1]) * 0.5f,
                (boundsMin[2] + boundsMax[2]) * 0.5f,
            )
            val scale = 2.0f / extent
            val textures = loadTextures(root)

            rawFaces.map { raw ->
                val normalized = FloatArray(raw.positions.size)
                for (i in normalized.indices step 3) {
                    normalized[i] = (raw.positions[i] - center[0]) * scale
                    normalized[i + 1] = (raw.positions[i + 1] - center[1]) * scale
                    normalized[i + 2] = (raw.positions[i + 2] - center[2]) * scale
                }
                GlFace(
                    vertices = directFloatBuffer(normalized),
                    texCoords = directFloatBuffer(raw.texCoords),
                    indices = directShortBuffer(raw.indices),
                    indexCount = raw.indices.size,
                    textureId = textures.getOrElse(raw.textureIndex) { 0 },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun visitNode(
        root: JSONObject,
        nodeIndex: Int,
        parentMatrix: FloatArray,
        buffers: List<ByteBuffer>,
        output: MutableList<RawFace>,
        boundsMin: FloatArray,
        boundsMax: FloatArray,
    ) {
        val nodes = root.getJSONArray("nodes")
        val node = nodes.getJSONObject(nodeIndex)
        val local = nodeTransform(node)
        val world = FloatArray(16)
        Matrix.multiplyMM(world, 0, parentMatrix, 0, local, 0)

        if (node.has("mesh")) {
            val mesh = root.getJSONArray("meshes").getJSONObject(node.getInt("mesh"))
            val primitives = mesh.optJSONArray("primitives") ?: JSONArray()
            for (primitiveIndex in 0 until primitives.length()) {
                val primitive = primitives.getJSONObject(primitiveIndex)
                val attributes = primitive.getJSONObject("attributes")
                val positions = readFloatAccessor(
                    root,
                    attributes.getInt("POSITION"),
                    buffers,
                    3,
                )
                val texCoords = readFloatAccessor(
                    root,
                    attributes.optInt("TEXCOORD_0", -1),
                    buffers,
                    2,
                )
                val indices = readIndexAccessor(
                    root,
                    primitive.getInt("indices"),
                    buffers,
                )
                val transformed = FloatArray(positions.size)
                val point = FloatArray(4)
                for (i in positions.indices step 3) {
                    point[0] = positions[i]
                    point[1] = positions[i + 1]
                    point[2] = positions[i + 2]
                    point[3] = 1f
                    Matrix.multiplyMV(point, 0, world, 0, point.copyOf(), 0)
                    transformed[i] = point[0]
                    transformed[i + 1] = point[1]
                    transformed[i + 2] = point[2]
                    for (axis in 0..2) {
                        boundsMin[axis] = min(boundsMin[axis], transformed[i + axis])
                        boundsMax[axis] = max(boundsMax[axis], transformed[i + axis])
                    }
                }
                output += RawFace(
                    positions = transformed,
                    texCoords = if (texCoords.isNotEmpty()) texCoords else defaultTexCoords(positions.size / 3),
                    indices = indices,
                    textureIndex = materialTextureIndex(root, primitive.optInt("material", -1)),
                )
            }
        }

        val children = node.optJSONArray("children") ?: return
        for (childIndex in 0 until children.length()) {
            visitNode(root, children.getInt(childIndex), world, buffers, output, boundsMin, boundsMax)
        }
    }

    private fun nodeTransform(node: JSONObject): FloatArray {
        val matrix = node.optJSONArray("matrix")
        if (matrix != null && matrix.length() == 16) {
            return FloatArray(16) { matrix.getDouble(it).toFloat() }
        }
        val result = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        val translation = node.optJSONArray("translation")
        if (translation != null && translation.length() >= 3) {
            Matrix.translateM(
                result,
                0,
                translation.getDouble(0).toFloat(),
                translation.getDouble(1).toFloat(),
                translation.getDouble(2).toFloat(),
            )
        }
        val rotation = node.optJSONArray("rotation")
        if (rotation != null && rotation.length() >= 4) {
            val x = rotation.getDouble(0).toFloat()
            val y = rotation.getDouble(1).toFloat()
            val z = rotation.getDouble(2).toFloat()
            val w = rotation.getDouble(3).toFloat()
            val quaternionMatrix = floatArrayOf(
                1f - 2f * (y * y + z * z), 2f * (x * y + z * w), 2f * (x * z - y * w), 0f,
                2f * (x * y - z * w), 1f - 2f * (x * x + z * z), 2f * (y * z + x * w), 0f,
                2f * (x * z + y * w), 2f * (y * z - x * w), 1f - 2f * (x * x + y * y), 0f,
                0f, 0f, 0f, 1f,
            )
            val rotated = FloatArray(16)
            Matrix.multiplyMM(rotated, 0, result, 0, quaternionMatrix, 0)
            rotated.copyInto(result)
        }
        val scale = node.optJSONArray("scale")
        if (scale != null && scale.length() >= 3) {
            Matrix.scaleM(
                result,
                0,
                scale.getDouble(0).toFloat(),
                scale.getDouble(1).toFloat(),
                scale.getDouble(2).toFloat(),
            )
        }
        return result
    }

    private fun loadBuffers(bufferArray: JSONArray): List<ByteBuffer> {
        return (0 until bufferArray.length()).map { index ->
            val uri = bufferArray.getJSONObject(index).getString("uri")
            val encoded = uri.substringAfter(',', "")
            ByteBuffer.wrap(Base64.decode(encoded, Base64.DEFAULT)).order(ByteOrder.LITTLE_ENDIAN)
        }
    }

    private fun readFloatAccessor(
        root: JSONObject,
        accessorIndex: Int,
        buffers: List<ByteBuffer>,
        componentCount: Int,
    ): FloatArray {
        if (accessorIndex < 0) return FloatArray(0)
        val accessor = root.getJSONArray("accessors").getJSONObject(accessorIndex)
        val viewIndex = accessor.getInt("bufferView")
        val view = root.getJSONArray("bufferViews").getJSONObject(viewIndex)
        val buffer = buffers[view.getInt("buffer")]
        val count = accessor.getInt("count")
        val viewOffset = view.optInt("byteOffset", 0)
        val accessorOffset = accessor.optInt("byteOffset", 0)
        val stride = view.optInt("byteStride", componentCount * 4)
        val result = FloatArray(count * componentCount)
        for (item in 0 until count) {
            val itemOffset = viewOffset + accessorOffset + item * stride
            for (component in 0 until componentCount) {
                result[item * componentCount + component] = buffer.getFloat(itemOffset + component * 4)
            }
        }
        return result
    }

    private fun readIndexAccessor(
        root: JSONObject,
        accessorIndex: Int,
        buffers: List<ByteBuffer>,
    ): ShortArray {
        val accessor = root.getJSONArray("accessors").getJSONObject(accessorIndex)
        val view = root.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
        val buffer = buffers[view.getInt("buffer")]
        val count = accessor.getInt("count")
        val offset = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        val result = ShortArray(count)
        when (accessor.getInt("componentType")) {
            5121 -> for (i in 0 until count) result[i] = (buffer.get(offset + i).toInt() and 0xff).toShort()
            5123 -> for (i in 0 until count) result[i] = buffer.getShort(offset + i * 2)
            5125 -> for (i in 0 until count) {
                val index = buffer.getInt(offset + i * 4)
                require(index in 0..0xffff) { "Dice mesh has too many vertices for GLES2 indices" }
                result[i] = index.toShort()
            }
            else -> error("Unsupported dice index component type")
        }
        return result
    }

    private fun materialTextureIndex(root: JSONObject, materialIndex: Int): Int {
        if (materialIndex < 0) return -1
        val material = root.getJSONArray("materials").getJSONObject(materialIndex)
        val pbr = material.optJSONObject("pbrMetallicRoughness") ?: return -1
        val texture = pbr.optJSONObject("baseColorTexture") ?: return -1
        val textureIndex = texture.optInt("index", -1)
        if (textureIndex < 0) return -1
        return root.getJSONArray("textures").getJSONObject(textureIndex).optInt("source", -1)
    }

    private fun loadTextures(root: JSONObject): List<Int> {
        val images = root.optJSONArray("images") ?: return emptyList()
        return (0 until images.length()).map { imageIndex ->
            val uri = images.getJSONObject(imageIndex).optString("uri")
            val encoded = uri.substringAfter(',', "")
            val imageBytes = Base64.decode(encoded, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            uploadTexture(bitmap)
        }
    }

    private fun uploadTexture(bitmap: Bitmap?): Int {
        if (bitmap == null) return 0
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return texture[0]
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
            require(status[0] != 0) { GLES20.glGetProgramInfoLog(linked) }
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
            require(status[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
        }
    }

    private fun defaultTexCoords(vertexCount: Int): FloatArray {
        val result = FloatArray(vertexCount * 2)
        for (i in 0 until vertexCount) {
            result[i * 2] = if (i % 3 == 1) 1f else 0f
            result[i * 2 + 1] = if (i % 3 == 2) 1f else 0f
        }
        return result
    }

    private fun directFloatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private fun directShortBuffer(values: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(values.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(values)
                position(0)
            }

    private data class RawFace(
        val positions: FloatArray,
        val texCoords: FloatArray,
        val indices: ShortArray,
        val textureIndex: Int,
    )

    private data class GlFace(
        val vertices: FloatBuffer,
        val texCoords: FloatBuffer,
        val indices: ShortBuffer,
        val indexCount: Int,
        val textureId: Int,
    )

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvp * aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}