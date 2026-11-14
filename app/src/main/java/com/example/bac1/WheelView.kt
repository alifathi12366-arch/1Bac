package com.example.bac1

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.animation.DecelerateInterpolator

class WheelView(ctx: Context) : View(ctx) {

    private var items: List<String> = emptyList()
    private var angle = 0f
    private var anim: ValueAnimator? = null

    private val palette = intArrayOf(
        Color.parseColor("#EF4444"), Color.parseColor("#F59E0B"), Color.parseColor("#22C55E"),
        Color.parseColor("#06B6D4"), Color.parseColor("#6366F1"), Color.parseColor("#EC4899"),
        Color.parseColor("#14B8A6"), Color.parseColor("#A855F7"), Color.parseColor("#F97316"),
        Color.parseColor("#3B82F6")
    )
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private fun dp(v: Float) = v * resources.displayMetrics.density

    init {
        line.style = Paint.Style.STROKE
        line.color = Color.WHITE
        line.strokeWidth = dp(2f)
        text.color = Color.WHITE
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.DEFAULT_BOLD
        text.setShadowLayer(dp(2f), 0f, dp(1f), Color.parseColor("#66000000"))
    }

    fun setItems(list: List<String>) {
        items = list
        invalidate()
    }

    private fun baseFor(i: Int): Float {
        val n = items.size
        if (n == 0) return 0f
        val seg = 360f / n
        return -((i + 0.5f) * seg)
    }

    // يوقف العجلة على العنصر فورًا من غير لف
    fun showIndex(i: Int) {
        anim?.cancel()
        angle = baseFor(i)
        invalidate()
    }

    fun spinTo(i: Int, durationMs: Long = 4800L, onEnd: () -> Unit) {
        val n = items.size
        if (n == 0 || i < 0 || i >= n) {
            onEnd()
            return
        }
        anim?.cancel()
        val seg = 360f / n
        val jitter = (Math.random().toFloat() - 0.5f) * seg * 0.6f
        var target = baseFor(i) + jitter
        val minEnd = angle + 360f * 5
        while (target < minEnd) target += 360f

        val a = ValueAnimator.ofFloat(angle, target)
        a.duration = durationMs
        a.interpolator = DecelerateInterpolator(2.2f)
        a.addUpdateListener {
            angle = it.animatedValue as Float
            invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd()
            }
        })
        anim = a
        a.start()
    }

    fun stop() {
        anim?.cancel()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        val top = dp(26f)
        val r = minOf(w - dp(16f), h - top - dp(8f)) / 2f
        if (r <= 0f) return
        val cx = w / 2f
        val cy = top + r
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val n = items.size

        fill.style = Paint.Style.FILL
        fill.color = Color.parseColor("#FFC83D")
        c.drawCircle(cx, cy, r + dp(6f), fill)

        if (n == 0) {
            fill.color = Color.parseColor("#1E2A5E")
            c.drawCircle(cx, cy, r, fill)
        } else {
            c.save()
            c.rotate(angle, cx, cy)
            val seg = 360f / n
            var ts = r * 0.16f
            if (n > 10) ts *= 0.75f
            ts = ts.coerceIn(dp(10f), dp(19f))
            text.textSize = ts

            for (i in 0 until n) {
                fill.color = palette[i % palette.size]
                c.drawArc(rect, -90f + i * seg, seg, true, fill)
            }
            if (n > 1) {
                for (i in 0 until n) {
                    c.drawArc(rect, -90f + i * seg, seg, true, line)
                }
            }
            for (i in 0 until n) {
                c.save()
                c.rotate(-90f + (i + 0.5f) * seg, cx, cy)
                val label = TextUtils.ellipsize(items[i], text, r * 0.62f, TextUtils.TruncateAt.END).toString()
                c.drawText(label, cx + r * 0.58f, cy + ts / 3f, text)
                c.restore()
            }
            c.restore()
        }

        // مركز العجلة
        fill.color = Color.parseColor("#0B1033")
        c.drawCircle(cx, cy, dp(16f), fill)
        fill.color = Color.parseColor("#FFC83D")
        c.drawCircle(cx, cy, dp(8f), fill)

        // المؤشر
        val p = Path()
        p.moveTo(cx - dp(13f), dp(2f))
        p.lineTo(cx + dp(13f), dp(2f))
        p.lineTo(cx, top + dp(14f))
        p.close()
        fill.color = Color.parseColor("#EF4444")
        c.drawPath(p, fill)
        c.drawPath(p, line)
    }
}
