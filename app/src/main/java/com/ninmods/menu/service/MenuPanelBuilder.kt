package com.ninmods.menu.service

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.ninmods.menu.R
import kotlin.math.abs

object MenuPanelBuilder {
    private var isStarted = false

    fun resetSession() {
        isStarted = false
    }

    fun build(context: Context, onClose: () -> Unit, onHeaderTouch: View.OnTouchListener? = null): View {
        val root = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
        val logoW = DpiHelper.getLogoWidth(context); val logoH = DpiHelper.getLogoHeight(context)
        val headerH = DpiHelper.getHeaderHeight(context)
        val bodyHeight = DpiHelper.getBodyHeight(context); val sidePadding = DpiHelper.dp(context, 12f)

        val container = RelativeLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(MenuColors.PANEL_BG)
                cornerRadius = 0f
                setStroke(1, MenuColors.CYAN_BORDER)
            }
            setPadding(sidePadding, DpiHelper.dp(context, 4f), sidePadding, DpiHelper.dp(context, 8f))
        }

        val headerDrag = View(context).apply {
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, headerH)
            setOnTouchListener(onHeaderTouch)
        }
        container.addView(headerDrag)

        val btnSize = DpiHelper.dp(context, 26f); val btnTop = ((headerH - btnSize) / 2).coerceAtLeast(DpiHelper.dp(context, 2f))
        val closeBtn = TextView(context).apply {
            text = "✕"; textSize = 15f; setTextColor(MenuColors.TEXT_WHITE); gravity = Gravity.CENTER
            layoutParams = RelativeLayout.LayoutParams(btnSize, btnSize).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT); addRule(RelativeLayout.ALIGN_PARENT_TOP)
                topMargin = btnTop; rightMargin = DpiHelper.dp(context, 6f)
            }
            var downTime = 0L; var downX = 0f; var downY = 0f
            setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { downTime = System.currentTimeMillis(); downX = e.rawX; downY = e.rawY; v.alpha = 0.4f; true }
                    MotionEvent.ACTION_UP -> { v.alpha = 1.0f; if (System.currentTimeMillis() - downTime < 600L && abs(e.rawX - downX) < 40f && abs(e.rawY - downY) < 40f) onClose(); true }
                    MotionEvent.ACTION_CANCEL -> { v.alpha = 1.0f; true }
                    else -> false
                }
            }
            setOnClickListener { onClose() }
        }
        container.addView(closeBtn)

        val bodyLogoSize = DpiHelper.getBodyLogoSize(context)
        val bodyFrame = FrameLayout(context).apply {
            clipChildren = false
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, bodyHeight).apply { topMargin = headerH }
        }

        val ambientOrbSize = DpiHelper.dp(context, 150f)
        val ambientGlow = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(MenuColors.AMBIENT_BLUR)
            }
            alpha = 0.20f
            layoutParams = FrameLayout.LayoutParams(ambientOrbSize, ambientOrbSize).apply { gravity = Gravity.CENTER }
        }
        bodyFrame.addView(ambientGlow)

        ObjectAnimator.ofFloat(ambientGlow, View.TRANSLATION_Y, -8f, 8f).apply {
            duration = 5000L; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start()
        }
        ObjectAnimator.ofFloat(ambientGlow, View.ALPHA, 0.15f, 0.30f).apply {
            duration = 4200L; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start()
        }

        val bodyLogoView = ImageView(context).apply {
            setImageResource(R.drawable.body_logo); scaleType = ImageView.ScaleType.FIT_CENTER; alpha = 0.35f
            layoutParams = FrameLayout.LayoutParams(bodyLogoSize, bodyLogoSize).apply { gravity = Gravity.CENTER }
        }
        bodyFrame.addView(bodyLogoView)

        ObjectAnimator.ofFloat(bodyLogoView, View.ALPHA, 0.28f, 0.40f).apply {
            duration = 4500L; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start()
        }

        // Main Menu Single Page (Directly Available - No Blocking Landing Screen)
        val menuLayer = ScrollView(context).apply {
            isFillViewport = true; clipToPadding = false
            isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER; setPadding(0, 0, 0, DpiHelper.dp(context, 10f))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = View.VISIBLE
        }
        menuLayer.addView(MenuTabsBuilder.buildTabs(context))

        bodyFrame.addView(menuLayer)
        container.addView(bodyFrame)

        val containerParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (logoH * 0.54f).toInt()
        }
        root.addView(container, containerParams)

        val headerLogo = ImageView(context).apply {
            setImageResource(R.drawable.header_logo); scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(logoW, logoH).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
            setOnTouchListener(onHeaderTouch)
        }
        root.addView(headerLogo)
        return root
    }
}
