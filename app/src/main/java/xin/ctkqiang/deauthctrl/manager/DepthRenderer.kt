package xin.ctkqiang.deauthctrl.manager

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * ARCore 深度扫描 GL 渲染器 —— 相机纹理背景 + 每帧深度采样回调。
 *
 * 着色器与纹理上传节奏沿用 ARCore 官方 hello_ar_kotlin 示例
 * (Apache-2.0)的结构;深度热力图不走 GL,由 Compose Canvas 覆盖层
 * 用 [DepthMath.buildHeatmap] 的数据绘制 —— 逻辑留在纯 Kotlin,可单测。
 */
class DepthRenderer(
    private val managerProvider: () -> DepthScanManager?,
    private val onGlReady: (DepthCapability) -> Unit,
    private val onFrame: (DepthScanManager.DepthFrameResult?) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var heatmapOn = true

    private var quadProgram = 0
    private var quadPositionAttr = 0
    private var quadTexCoordAttr = 0
    private var quadTextureUniform = 0
    private var cameraTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var framesBeforeTextureUpdate = 0
    private var sessionStarted = false

    private val quadCoords = floatArrayOf(
        -1f, -1f, 0f,
        1f, -1f, 0f,
        -1f, 1f, 0f,
        1f, 1f, 0f,
    )
    private val quadTexCoords = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f,
    )
    private val quadCoordsBuffer: FloatBuffer = FloatBuffer.wrap(quadCoords)
    private val quadTexCoordsBuffer: FloatBuffer = FloatBuffer.wrap(quadTexCoords)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        cameraTextureId = createExternalTexture()
        surfaceTexture = SurfaceTexture(cameraTextureId).also {
            // onFrameAvailable 运行在 GL 线程(GLES 回调线程),直接计数
            it.setOnFrameAvailableListener { framesBeforeTextureUpdate = UPDATE_ON_FIRST_FRAME }
        }
        quadProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_OES)
        quadPositionAttr = GLES20.glGetAttribLocation(quadProgram, "a_Position")
        quadTexCoordAttr = GLES20.glGetAttribLocation(quadProgram, "a_TexCoord")
        quadTextureUniform = GLES20.glGetUniformLocation(quadProgram, "sTexture")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val manager = managerProvider() ?: return
        val rotation = manager.displayRotation()
        val cap = manager.onSurfaceReadyOnGlThread(cameraTextureId, width, height, rotation)
        sessionStarted = manager.session != null
        onGlReady(cap)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (quadProgram == 0 || !sessionStarted) return
        val manager = managerProvider() ?: return

        // 相机初始化需要运动 + 特征点:异常只降级显示,不崩溃
        val frame: Frame
        try {
            frame = manager.session?.update() ?: return
        } catch (e: NotYetAvailableException) {
            onFrame(DepthScanManager.DepthFrameResult(null, null))
            return
        }

        // 显示几何变化时重算纹理坐标(官方示例同款)
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED,
                quadTexCoords,
            )
        }
        if (frame.timestamp == 0L || frame.hasDisplayGeometryChanged()) {
            framesBeforeTextureUpdate = UPDATE_ON_FIRST_FRAME
        }
        if (framesBeforeTextureUpdate > 0) {
            surfaceTexture?.updateTexImage()
            framesBeforeTextureUpdate--
        }

        // 相机纹理背景
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(quadProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(quadTextureUniform, 0)
        GLES20.glEnableVertexAttribArray(quadPositionAttr)
        GLES20.glVertexAttribPointer(quadPositionAttr, 3, GLES20.GL_FLOAT, false, 0, quadCoordsBuffer)
        GLES20.glEnableVertexAttribArray(quadTexCoordAttr)
        GLES20.glVertexAttribPointer(quadTexCoordAttr, 2, GLES20.GL_FLOAT, false, 0, quadTexCoordsBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        // 深度采样:manager 内每帧 close() 深度 Image
        onFrame(runCatching { manager.sampleDepthFrame(frame, heatmapOn) }.getOrNull())
    }

    /** 前后摄切换(由外部 queueEvent 到 GL 线程调用) */
    fun setFacingOnGlThread(front: Boolean) {
        managerProvider()?.setFacing(front)
    }

    private fun createExternalTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return ids[0]
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private companion object {
        /** 相机帧到达后等 2 帧再 updateTexImage(官方示例节奏,避免撕裂) */
        const val UPDATE_ON_FIRST_FRAME = 2

        val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """.trimIndent()

        val FRAGMENT_SHADER_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """.trimIndent()
    }
}
