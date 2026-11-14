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
        parent.addView(r, LinearLayout.LayoutParams(-1, -2))
        return r
    }

    fun addCard(
        ctx: Context, row: LinearLayout, title: String, sub: String, emoji: String,
        c1: String, c2: String, accent: String, onClick: () -> Unit
    ): TextView {
        val card = FrameLayout(ctx)
        val lp = LinearLayout.LayoutParams(0, dp(ctx, 112), 1f)
        lp.setMargins(dp(ctx, 5), dp(ctx, 5), dp(ctx, 5), dp(ctx, 5))
        card.layoutParams = lp

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

        // الإيموجي
        val e = TextView(ctx)
        e.text = emoji
        e.textSize = 40f
        e.gravity = Gravity.CENTER
        val elp = FrameLayout.LayoutParams(dp(ctx, 64), dp(ctx, 64), Gravity.END or Gravity.TOP)
        elp.marginEnd = dp(ctx, 8)
        elp.topMargin = dp(ctx, 10)
        card.addView(e, elp)

        // العنوان
        val titleTv = TextView(ctx)
        titleTv.text = title
        titleTv.textSize = 15f
        titleTv.setTypeface(null, Typeface.BOLD)
        titleTv.setTextColor(Color.parseColor("#241B4E"))
        titleTv.maxLines = 2
        titleTv.gravity = Gravity.START
        val tlp = FrameLayout.LayoutParams(-1, -2, Gravity.START or Gravity.TOP)
        tlp.marginStart = dp(ctx, 14)
        tlp.marginEnd = dp(ctx, 76)
        tlp.topMargin = dp(ctx, 16)
        card.addView(titleTv, tlp)

        // الوصف
        val subTv = TextView(ctx)
        subTv.text = sub
        subTv.textSize = 10.5f
        subTv.setTextColor(Color.parseColor("#5E5A7E"))
        subTv.maxLines = 2
        subTv.gravity = Gravity.START
        val slp = FrameLayout.LayoutParams(-1, -2, Gravity.START or Gravity.BOTTOM)
        slp.marginStart = dp(ctx, 14)
        slp.marginEnd = dp(ctx, 48)
        slp.bottomMargin = dp(ctx, 12)
        card.addView(subTv, slp)

        // دايرة السهم
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
        val alp = FrameLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28), Gravity.END or Gravity.BOTTOM)
        alp.marginEnd = dp(ctx, 10)
        alp.bottomMargin = dp(ctx, 10)
        card.addView(arrow, alp)

        row.addView(card)
        return subTv
    }
}
