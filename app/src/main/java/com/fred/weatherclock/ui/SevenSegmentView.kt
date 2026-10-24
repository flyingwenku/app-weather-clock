package com.fred.weatherclock.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * 七段数码管数字（Canvas 自绘）。
 *
 * 为什么自绘而不是用字体：
 * - 不引入字体文件（省体积、无授权问题）
 * - 颜色（红/蓝）、粗细完全可控
 * - 只画点亮的段：纯数字，无背景、无发光（用户要求）
 *
 * 单位空间约定：单个数字宽 1.0、高 2.0，绘制时整体缩放到可用区域。
 */
class SevenSegmentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val H = 0.13f              // 段厚度的一半
        private const val X_L = H                // 左竖段中心线
        private const val X_R = 1.0f - H         // 右竖段中心线
        private const val Y_TOP = H              // 上横段中心
        private const val Y_MID = 1.0f           // 中横段中心
        private const val Y_BOT = 2.0f - H       // 下横段中心
        private const val COLON_W = 0.38f        // 冒号宽度
        private const val SPACING = 0.14f        // 字符间距

        /** 各数字点亮哪些段 */
        private val SEGMENTS: Map<Char, String> = mapOf(
            '0' to "abcdef",
            '1' to "bc",
            '2' to "abged",
            '3' to "abgcd",
            '4' to "fgbc",
            '5' to "afgcd",
            '6' to "afgecd",
            '7' to "abc",
            '8' to "abcdefg",
            '9' to "abcdfg",
            '-' to "g",
            ' ' to ""
        )
    }

    var text: String = "00:00:00"
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        setColor(Color.RED)
    }

    fun setColor(color: Int) {
        onPaint.color = color
        onPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)

        val unitW = unitWidth()
        var h = if (hMode == MeasureSpec.EXACTLY) hSize else 200
        var w = (unitW * h / 2f).toInt()

        if (wMode == MeasureSpec.EXACTLY) {
            w = wSize
            val fittedH = (2f * w / unitW).toInt()
            if (fittedH < h) h = fittedH
        } else if (wSize > 0 && w > wSize) {
            w = wSize
            h = (2f * w / unitW).toInt()
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unitW = unitWidth()
        val scale = min(height / 2f, width / unitW)
        val offsetX = (width - unitW * scale) / 2f

        canvas.save()
        canvas.translate(offsetX, 0f)
        canvas.scale(scale, scale)

        var x = 0f
        for (ch in text) {
            if (ch == ':') {
                drawColon(canvas, x)
                x += COLON_W
            } else {
                drawDigit(canvas, ch, x)
                x += 1f
            }
            x += SPACING
        }
        canvas.restore()
    }

    private fun unitWidth(): Float {
        var w = 0f
        for (ch in text) {
            w += if (ch == ':') COLON_W else 1.0f
            w += SPACING
        }
        return if (w > 0f) w else 1f
    }

    private fun drawDigit(canvas: Canvas, ch: Char, dx: Float) {
        val lit = SEGMENTS[ch] ?: return
        for (seg in lit) {
            canvas.drawPath(segmentPath(seg, dx), onPaint)
        }
    }

    private fun drawColon(canvas: Canvas, dx: Float) {
        val cx = dx + COLON_W / 2f
        canvas.drawCircle(cx, 0.62f, H * 0.85f, onPaint)
        canvas.drawCircle(cx, 1.38f, H * 0.85f, onPaint)
    }

    private fun segmentPath(seg: Char, dx: Float): Path {
        val p = Path()
        when (seg) {
            'a' -> hBar(p, Y_TOP, dx)
            'g' -> hBar(p, Y_MID, dx)
            'd' -> hBar(p, Y_BOT, dx)
            'f' -> vBar(p, X_L, Y_TOP, Y_MID, dx)
            'b' -> vBar(p, X_R, Y_TOP, Y_MID, dx)
            'e' -> vBar(p, X_L, Y_MID, Y_BOT, dx)
            'c' -> vBar(p, X_R, Y_MID, Y_BOT, dx)
        }
        return p
    }

    private fun hBar(p: Path, y: Float, dx: Float) {
        p.moveTo(dx + X_L, y)
        p.lineTo(dx + X_L + H, y - H)
        p.lineTo(dx + X_R - H, y - H)
        p.lineTo(dx + X_R, y)
        p.lineTo(dx + X_R - H, y + H)
        p.lineTo(dx + X_L + H, y + H)
        p.close()
    }

    private fun vBar(p: Path, x: Float, y0: Float, y1: Float, dx: Float) {
        p.moveTo(dx + x, y0)
        p.lineTo(dx + x + H, y0 + H)
        p.lineTo(dx + x + H, y1 - H)
        p.lineTo(dx + x, y1)
        p.lineTo(dx + x - H, y1 - H)
        p.lineTo(dx + x - H, y0 + H)
        p.close()
    }
}
