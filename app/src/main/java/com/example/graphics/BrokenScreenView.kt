package com.example.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.example.model.PrankConfig
import com.example.model.TouchMode

class BrokenScreenView(
    context: Context,
    var config: PrankConfig,
    private val onExitRequested: () -> Unit,
    private val onSparkGenerated: ((x: Float, y: Float) -> Unit)? = null
) : View(context) {

    private val renderer = BrokenScreenRenderer()
    private var isRunning = true

    // Emergency exit gesture tracking (e.g. 3 rapid taps in top-right corner zone)
    private var cornerTapCount = 0
    private var lastCornerTapTime = 0L
    private val cornerTapBounds = RectF()

    // Discreet stop button paint (semi-translucent so user knows where to tap)
    private val exitHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val exitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    init {
        // Hardware acceleration enabled
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderer.updateDimensions(w.toFloat(), h.toFloat(), config)
        // Set emergency corner tap zone (top right 140x140 dp zone)
        val density = resources.displayMetrics.density
        val zoneSize = 90f * density
        cornerTapBounds.set(w - zoneSize, 0f, w.toFloat(), zoneSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isRunning) return

        val w = width.toFloat()
        val h = height.toFloat()

        renderer.draw(canvas, w, h, config)

        // If interactive touch mode is enabled, draw subtle panic guide in corner
        if (config.touchMode == TouchMode.ELECTRIC_SPARKS) {
            canvas.drawRoundRect(
                cornerTapBounds.left + 16f,
                cornerTapBounds.top + 16f,
                cornerTapBounds.right - 16f,
                cornerTapBounds.bottom - 16f,
                24f,
                24f,
                exitHintPaint
            )
            canvas.drawText(
                "EXIT ×3",
                cornerTapBounds.centerX(),
                cornerTapBounds.centerY() + 10f,
                exitTextPaint
            )
        }

        // Keep glitch frame cycle pumping
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (config.touchMode == TouchMode.PASS_THROUGH) {
            return false // Let touches pass through
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            // Check corner tap for emergency exit
            if (cornerTapBounds.contains(x, y)) {
                val now = SystemClock.uptimeMillis()
                if (now - lastCornerTapTime < 600L) {
                    cornerTapCount++
                    if (cornerTapCount >= 3) {
                        onExitRequested()
                        return true
                    }
                } else {
                    cornerTapCount = 1
                }
                lastCornerTapTime = now
            }

            // Generate spark
            renderer.addSpark(x, y)
            onSparkGenerated?.invoke(x, y)
            invalidate()
            return true
        }

        return super.onTouchEvent(event)
    }

    fun updateConfig(newConfig: PrankConfig) {
        config = newConfig
        renderer.updateDimensions(width.toFloat(), height.toFloat(), newConfig)
        invalidate()
    }

    fun triggerSpark(x: Float, y: Float) {
        renderer.addSpark(x, y)
        invalidate()
    }

    fun destroy() {
        isRunning = false
    }
}
