package com.neko.circleimageview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import com.neko.v2ray.R

class CircleImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderMatrix = Matrix()

    private var bitmapShader: BitmapShader? = null
    private var bitmap: Bitmap? = null

    private var borderColor: Int = Color.WHITE
    private var borderWidth: Float = 8f
    private var rainbowBorderEnabled = false
    private var autoStartAnimations = true

    private var sweepAngle = 0f
    private var animatedBorderWidth = borderWidth
    private var borderAlpha = 255

    private val rainbowColors = intArrayOf(
        Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED
    )

    private var borderAnimator: ValueAnimator? = null
    private var pulseWidthAnimator: ValueAnimator? = null
    private var pulseAlphaAnimator: ValueAnimator? = null

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.CircleImageView, 0, 0).apply {
            try {
                borderColor = getColor(R.styleable.CircleImageView_borderColor, Color.WHITE)
                borderWidth = getDimension(R.styleable.CircleImageView_borderWidth, 8f)
                rainbowBorderEnabled = getBoolean(R.styleable.CircleImageView_rainbowBorderEnabled, false)
                autoStartAnimations = getBoolean(R.styleable.CircleImageView_autoStartAnimations, true)
                animatedBorderWidth = borderWidth
            } finally {
                recycle()
            }
        }

        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeCap = Paint.Cap.ROUND
        borderPaint.color = borderColor
        borderPaint.strokeWidth = borderWidth

        setupPaint()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoStartAnimations) {
            startAnimations()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
        
        // Clean up bitmaps to prevent memory leaks
        bitmap?.recycle()
        bitmap = null
        bitmapShader = null
    }

    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (autoStartAnimations) {
            if (visibility == android.view.View.VISIBLE) {
                startAnimations()
            } else {
                stopAnimations()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Reset bitmap saat ukuran berubah
        bitmap = null
        bitmapShader = null
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        
        val drawable = drawable ?: return

        if (bitmap == null) {
            setupBitmap()
        }

        bitmap?.let { bitmap ->
            bitmapShader?.let { shader ->
                val radius = (width.coerceAtMost(height) / 2f) - animatedBorderWidth / 2f
                updateShaderMatrix(bitmap)
                
                // Draw image
                canvas.drawCircle(width / 2f, height / 2f, radius, paint)
                
                // Draw border
                if (borderWidth > 0) {
                    drawBorder(canvas, radius)
                }
            }
        }
    }

    private fun setupPaint() {
        paint.isAntiAlias = true
        borderPaint.isAntiAlias = true
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeCap = Paint.Cap.ROUND
        borderPaint.strokeWidth = borderWidth
    }

    private fun setupBitmap() {
        try {
            bitmap = getBitmapFromDrawable()
            bitmap?.let {
                bitmapShader = BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                paint.shader = bitmapShader
                updateShaderMatrix(it)
            }
        } catch (e: Exception) {
            // Handle exception
        }
    }

    private fun drawBorder(canvas: Canvas, radius: Float) {
        if (rainbowBorderEnabled) {
            val sweep = SweepGradient(width / 2f, height / 2f, rainbowColors, null)
            val matrix = Matrix()
            matrix.postRotate(sweepAngle, width / 2f, height / 2f)
            sweep.setLocalMatrix(matrix)
            borderPaint.shader = sweep
        } else {
            borderPaint.shader = null
            borderPaint.color = borderColor
        }

        borderPaint.strokeWidth = animatedBorderWidth
        borderPaint.alpha = borderAlpha
        canvas.drawCircle(width / 2f, height / 2f, radius, borderPaint)
    }

    private fun getBitmapFromDrawable(): Bitmap {
        val d = drawable ?: throw IllegalStateException("Drawable is null")
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, canvas.width, canvas.height)
        d.draw(canvas)
        return bmp
    }

    private fun updateShaderMatrix(bitmap: Bitmap) {
        val scale: Float
        val dx: Float
        val dy: Float

        shaderMatrix.reset()
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val bWidth = bitmap.width.toFloat()
        val bHeight = bitmap.height.toFloat()

        if (bWidth * viewHeight > viewWidth * bHeight) {
            scale = viewHeight / bHeight
            dx = (viewWidth - bWidth * scale) * 0.5f
            dy = 0f
        } else {
            scale = viewWidth / bWidth
            dx = 0f
            dy = (viewHeight - bHeight * scale) * 0.5f
        }

        shaderMatrix.setScale(scale, scale)
        shaderMatrix.postTranslate(dx, dy)
        bitmapShader?.setLocalMatrix(shaderMatrix)
    }

    fun startAnimations() {
        startBorderAnimation()
        startPulseAnimation()
    }

    fun stopAnimations() {
        borderAnimator?.cancel()
        pulseWidthAnimator?.cancel()
        pulseAlphaAnimator?.cancel()
    }

    private fun startBorderAnimation() {
        borderAnimator?.cancel()
        
        borderAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                sweepAngle = it.animatedValue as Float
                if (rainbowBorderEnabled) invalidate()
            }
            start()
        }
    }

    private fun startPulseAnimation() {
        pulseWidthAnimator?.cancel()
        pulseAlphaAnimator?.cancel()
        
        pulseWidthAnimator = ValueAnimator.ofFloat(borderWidth * 0.8f, borderWidth * 1.4f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                animatedBorderWidth = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        pulseAlphaAnimator = ValueAnimator.ofInt(100, 255).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                borderAlpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    fun setRainbowBorderEnabled(enabled: Boolean) {
        rainbowBorderEnabled = enabled
        invalidate()
        
        // Restart border animation if needed
        if (enabled && autoStartAnimations) {
            startBorderAnimation()
        }
    }

    fun setAutoStartAnimations(enabled: Boolean) {
        autoStartAnimations = enabled
        if (enabled) {
            startAnimations()
        } else {
            stopAnimations()
        }
    }
}
