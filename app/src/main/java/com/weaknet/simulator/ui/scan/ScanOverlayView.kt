package com.weaknet.simulator.ui.scan

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.journeyapps.barcodescanner.ViewfinderView

class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val maskColor = Color.parseColor("#80000000")
    private val cornerColor = Color.parseColor("#FF4CAF50")
    private val scanLineColor = Color.parseColor("#FF4CAF50")

    private val cornerLength = 40f.dp
    private val cornerWidth = 4f.dp
    private val scanFrameSize = 260f.dp

    private val maskPaint = Paint().apply { color = maskColor }
    private val cornerPaint = Paint().apply {
        color = cornerColor
        strokeWidth = cornerWidth
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val scanLinePaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, scanFrameSize, 0f,
            intArrayOf(Color.TRANSPARENT, scanLineColor, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        isAntiAlias = true
    }

    private var scanLineY = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2500
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            scanLineY = it.animatedFraction
            invalidate()
        }
    }

    private val Float.dp: Float
        get() = this * context.resources.displayMetrics.density

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        val frameLeft = (w - scanFrameSize) / 2
        val frameTop = (h - scanFrameSize) / 2 - 40f.dp
        val frameRight = frameLeft + scanFrameSize
        val frameBottom = frameTop + scanFrameSize
        val frame = RectF(frameLeft, frameTop, frameRight, frameBottom)

        // Dark mask (4 rects around the frame)
        canvas.drawRect(0f, 0f, w, frame.top, maskPaint)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, maskPaint)
        canvas.drawRect(frame.right, frame.top, w, frame.bottom, maskPaint)
        canvas.drawRect(0f, frame.bottom, w, h, maskPaint)

        // Corner lines
        drawCorners(canvas, frame)

        // Scan line
        val lineY = frame.top + scanLineY * scanFrameSize
        scanLinePaint.shader = LinearGradient(
            frame.left, 0f, frame.right, 0f,
            intArrayOf(Color.TRANSPARENT, scanLineColor, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(frame.left + 10f.dp, lineY, frame.right - 10f.dp, lineY + 2f.dp, scanLinePaint)
    }

    private fun drawCorners(canvas: Canvas, frame: RectF) {
        val l = cornerLength
        // Top-left
        canvas.drawLine(frame.left, frame.top, frame.left + l, frame.top, cornerPaint)
        canvas.drawLine(frame.left, frame.top, frame.left, frame.top + l, cornerPaint)
        // Top-right
        canvas.drawLine(frame.right, frame.top, frame.right - l, frame.top, cornerPaint)
        canvas.drawLine(frame.right, frame.top, frame.right, frame.top + l, cornerPaint)
        // Bottom-left
        canvas.drawLine(frame.left, frame.bottom, frame.left + l, frame.bottom, cornerPaint)
        canvas.drawLine(frame.left, frame.bottom, frame.left, frame.bottom - l, cornerPaint)
        // Bottom-right
        canvas.drawLine(frame.right, frame.bottom, frame.right - l, frame.bottom, cornerPaint)
        canvas.drawLine(frame.right, frame.bottom, frame.right, frame.bottom - l, cornerPaint)
    }
}
