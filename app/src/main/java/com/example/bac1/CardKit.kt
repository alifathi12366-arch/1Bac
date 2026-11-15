package com.example.bac1

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object CardKit {

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun newRow(ctx: Context, parent: LinearLayout): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.weightSum = 2f
        r.isBaselineAligned = false
        parent.addView(r, LinearLayout.LayoutParams(-1, -2))
        return r
    }

    // الكارت عمودي: السهم والإيموجي فوق، والنص تحتهم بعرض الكارت كله
    fun addCard(
        ctx: Context, row: LinearLayout, title: String, sub: String, emoji: String,
        c1: String, c2: String, accent: String, onClick: () -> Unit
    ): TextView {
        val card = FrameLayout(ctx)
        val lp = LinearLayout.LayoutParams(0, -1, 1f)
        lp.setMargins(dp(ctx, 5), dp(ctx, 5), dp(ctx, 5), dp(ctx, 5))
        card.layoutParams = lp
        card.minimumHeight = dp(ctx, 130)

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
        )
        bg.cornerRadius = dp(ctx, 22).toFloat()
        bg.setStroke(dp(ctx, 1), Color.parseColor("#B3FFFFFF"))
        card.background = bg
        card.clipToOutline = true
        card.elevation = dp(ctx, 3).toFloat()
        card.isClickable = true
        card.setOnClickListener { onClick() }

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 14))

        // الصف العلوي: السهم + الإيموجي
        val top = FrameLayout(ctx)
        col.addView(top, LinearLayout.LayoutParams(-1, dp(ctx, 46)))

        val arrow = TextView(ctx)
        arrow.text = "›"
        arrow.textDirection = View.TEXT_DIRECTION_LTR
        arrow.gravity = Gravity.CENTER
        arrow.textSize = 20f
        arrow.includeFontPadding = false
        arrow.setTypeface(null, Typeface.BOLD)
        arrow.setTextColor(Color.parseColor(accent))
        val abg = GradientDrawable()
        abg.shape = GradientDrawable.OVAL
        abg.setColor(Color.WHITE)
        arrow.background = abg
        top.addView(arrow, FrameLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28), Gravity.START or Gravity.CENTER_VERTICAL))

        val e = TextView(ctx)
        e.text = emoji
        e.textSize = 34f
        e.gravity = Gravity.CENTER
        top.addView(e, FrameLayout.LayoutParams(dp(ctx, 46), dp(ctx, 46), Gravity.END))

        // العنوان
        val titleTv = TextView(ctx)
        titleTv.text = title
        titleTv.textSize = 14.5f
        titleTv.setTypeface(null, Typeface.BOLD)
        titleTv.setTextColor(Color.parseColor("#241B4E"))
        titleTv.gravity = Gravity.START
        val tlp = LinearLayout.LayoutParams(-1, -2)
        tlp.topMargin = dp(ctx, 8)
        col.addView(titleTv, tlp)

        // الوصف
        val subTv = TextView(ctx)
        subTv.text = sub
        subTv.textSize = 10.5f
        subTv.setTextColor(Color.parseColor("#5E5A7E"))
        subTv.gravity = Gravity.START
        subTv.setPadding(0, dp(ctx, 3), 0, 0)
        col.addView(subTv, LinearLayout.LayoutParams(-1, -2))

        card.addView(col, FrameLayout.LayoutParams(-1, -2))
        row.addView(card)
        return subTv
    }
}
