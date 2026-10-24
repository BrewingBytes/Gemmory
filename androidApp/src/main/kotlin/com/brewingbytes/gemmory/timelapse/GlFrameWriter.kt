package com.brewingbytes.gemmory.timelapse

import android.graphics.Bitmap
import android.graphics.Rect
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Puts [FrameArt]'s layers onto the encoder's input surface through EGL, one frame at a time, each with its own
 * presentation time (`eglPresentationTimeANDROID`).
 *
 * Why not `Surface.lockHardwareCanvas`: its producer runs with swap interval 0, so a frame posted before the encoder
 * took the previous one replaces it (the smoke test lost 1-6 of 450 frames), and frames carry wall-clock timestamps
 * that confuse the encoder's rate control. An EGL window surface blocks in `eglSwapBuffers` instead of dropping.
 *
 * The layers are textures: the static background, the canvas (re-uploaded only on frames that placed gems), for the
 * photo intro the source photo drawn over the canvas with its opacity, and for the player's share (T53) the overlay
 * (fade, title and Gemmory row; a founder clip's title and lockup) over all of them. On end-card frames the band, a
 * black dim, the card layer and the canvas at the card's picture rect fade in over that. A founder clip's hook (#144)
 * is the finished canvas in the photo's slot. Bitmaps upload premultiplied, so blending is premultiplied.
 */
class GlFrameWriter(surface: Surface, private val width: Int, private val height: Int) {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private val textures = IntArray(TEXTURES)
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var aPosition = 0
    private var aTexCoord = 0
    private var uAlpha = 0

    init {
        // A failure partway (no config, no window surface, a shader that won't link) releases what was made so far:
        // the fallback's next attempt must not start with this one's EGL context still current on the IO thread.
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize: ${EGL14.eglGetError()}" }
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "no recordable EGL config" }
            val config = configs[0]!!
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext: ${EGL14.eglGetError()}" }
            eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface: ${EGL14.eglGetError()}" }
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent: ${EGL14.eglGetError()}" }
            setUpProgram()
        } catch (e: Throwable) {
            release()
            throw e
        }
    }

    /** Uploads the layers that don't change during the video. */
    fun upload(background: Bitmap, canvas: Bitmap, photo: Bitmap?, overlay: Bitmap? = null, card: Bitmap? = null) {
        GLES20.glGenTextures(TEXTURES, textures, 0)
        load(BACKGROUND, background)
        load(CANVAS, canvas)
        if (photo != null) load(PHOTO, photo)
        if (overlay != null) load(OVERLAY, overlay)
        if (card != null) {
            load(CARD, card)
            val black = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF000000.toInt()) }
            load(BLACK, black)
            black.recycle()
        }
    }

    /**
     * Draws one frame and hands it to the encoder at [presentationTimeUs]: the [overlay] layer at [overlayRect] when
     * there is one, and the end card when [endCard] is given.
     */
    fun write(
        canvas: Bitmap,
        canvasChanged: Boolean,
        canvasRect: Rect,
        photoAlpha: Float,
        presentationTimeUs: Long,
        overlayRect: Rect? = null,
        endCard: EndCardRects? = null,
    ) {
        if (canvasChanged) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[CANVAS])
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, canvas)
        }
        GLES20.glViewport(0, 0, width, height)
        GLES20.glDisable(GLES20.GL_BLEND)
        drawQuad(BACKGROUND, Rect(0, 0, width, height), 1f)
        drawQuad(CANVAS, canvasRect, 1f)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        if (photoAlpha > 0f) {
            GLES20.glEnable(GLES20.GL_BLEND)
            drawQuad(PHOTO, canvasRect, photoAlpha)
        }
        if (overlayRect != null) {
            GLES20.glEnable(GLES20.GL_BLEND)
            drawQuad(OVERLAY, overlayRect, 1f)
        }
        if (endCard != null) {
            val a = endCard.alpha
            GLES20.glEnable(GLES20.GL_BLEND)
            drawQuad(BACKGROUND, Rect(0, 0, width, height), a)
            drawQuad(BLACK, Rect(0, 0, width, height), endCard.dim * a)
            // The card layer holds the picture's shadow, so the canvas goes over it.
            drawQuad(CARD, endCard.content, a)
            drawQuad(CANVAS, endCard.picture, a)
        }
        checkGl("draw")
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, presentationTimeUs * 1_000)
        check(EGL14.eglSwapBuffers(display, eglSurface)) { "eglSwapBuffers: ${EGL14.eglGetError()}" }
    }

    /** Safe after a partial init and safe to call twice. */
    fun release() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        if (context != EGL14.EGL_NO_CONTEXT) {
            runCatching {
                GLES20.glDeleteTextures(TEXTURES, textures, 0)
                if (program != 0) GLES20.glDeleteProgram(program)
            }
        }
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
        eglSurface = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
        display = EGL14.EGL_NO_DISPLAY
    }

    private fun load(slot: Int, bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[slot])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        checkGl("upload")
    }

    /** [rect] in frame pixels, top-left origin; the texture's first row is the bitmap's top row. */
    private fun drawQuad(slot: Int, rect: Rect, alpha: Float) {
        val l = rect.left * 2f / width - 1f
        val r = rect.right * 2f / width - 1f
        val t = 1f - rect.top * 2f / height
        val b = 1f - rect.bottom * 2f / height
        quad.clear()
        // x, y, s, t as a triangle strip: bottom-left, bottom-right, top-left, top-right.
        quad.put(floatArrayOf(l, b, 0f, 1f, r, b, 1f, 1f, l, t, 0f, 0f, r, t, 1f, 0f))
        quad.position(0)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, quad)
        quad.position(2)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glUniform1f(uAlpha, alpha)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[slot])
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun setUpProgram() {
        fun shader(type: Int, source: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, source)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader: ${GLES20.glGetShaderInfoLog(s)}" }
            return s
        }
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, VERTEX))
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
        GLES20.glLinkProgram(program)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "link: ${GLES20.glGetProgramInfoLog(program)}" }
        GLES20.glUseProgram(program)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glEnableVertexAttribArray(aTexCoord)
    }

    private fun checkGl(what: String) {
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) { "GL $what: 0x${error.toString(16)}" }
    }

    private companion object {
        const val EGL_RECORDABLE_ANDROID = 0x3142
        const val BACKGROUND = 0
        const val CANVAS = 1
        const val PHOTO = 2
        const val OVERLAY = 3
        const val CARD = 4
        const val BLACK = 5
        const val TEXTURES = 6

        const val VERTEX = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        const val FRAGMENT = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            uniform float uAlpha;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord) * uAlpha;
            }
        """
    }
}
