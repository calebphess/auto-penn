package com.autopenn.mirror

import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Draws the mirrored display into only the part of Android Auto's surface that the head unit leaves visible
 * (AA paints its own panels over the rest, see SurfaceCallback.onVisibleAreaChanged). The VirtualDisplay renders
 * into [inputSurface]; every frame is drawn as a quad into the car surface inside [setRect]'s rectangle.
 */
class FitRenderer(private val carSurface: Surface, private val surfaceHeight: Int, initial: Rect) {
    private val thread = HandlerThread("autopenn-fit").apply { start() }
    private val handler = Handler(thread.looper)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var texture = 0
    private var program = 0
    private var uMatrix = 0
    private lateinit var surfaceTexture: SurfaceTexture
    lateinit var inputSurface: Surface
        private set

    @Volatile private var rect = Rect(initial)
    private val matrix = FloatArray(16)

    private val quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        // x, y, u, v
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
    }

    init {
        val ready = java.util.concurrent.CountDownLatch(1)
        handler.post { setup(); ready.countDown() }
        ready.await()
    }

    fun setRect(r: Rect) { rect = Rect(r) }

    fun setBufferSize(w: Int, h: Int) = handler.post { surfaceTexture.setDefaultBufferSize(w, h) }

    private fun setup() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2); EGL14.eglInitialize(eglDisplay, v, 0, v, 1)
        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, 0x3142 /* EGL_RECORDABLE_ANDROID */, 1, EGL14.EGL_NONE,
        )
        val cfg = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attrs, 0, cfg, 0, 1, n, 0)
        eglContext = EGL14.eglCreateContext(eglDisplay, cfg[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, cfg[0], carSurface, intArrayOf(EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); texture = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        program = link(
            "attribute vec4 aPos; attribute vec2 aUv; uniform mat4 uMatrix; varying vec2 vUv;" +
                "void main(){ gl_Position = aPos; vUv = (uMatrix * vec4(aUv, 0.0, 1.0)).xy; }",
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 vUv;" +
                "uniform samplerExternalOES uTex; void main(){ gl_FragColor = texture2D(uTex, vUv); }",
        )
        uMatrix = GLES20.glGetUniformLocation(program, "uMatrix")

        surfaceTexture = SurfaceTexture(texture)
        surfaceTexture.setDefaultBufferSize(rect.width(), rect.height())
        surfaceTexture.setOnFrameAvailableListener({ draw() }, handler)
        inputSurface = Surface(surfaceTexture)
    }

    private fun draw() {
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(matrix)
        val r = rect
        GLES20.glViewport(0, 0, 4096, 4096)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glViewport(r.left, surfaceHeight - r.bottom, r.width(), r.height())
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMatrix, 1, false, matrix, 0)
        val pos = GLES20.glGetAttribLocation(program, "aPos"); val uv = GLES20.glGetAttribLocation(program, "aUv")
        quad.position(0); GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(pos)
        quad.position(2); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(it)
        }
    }

    fun release() {
        handler.post {
            inputSurface.release(); surfaceTexture.release()
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface); EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
            thread.quitSafely()
        }
    }
}
