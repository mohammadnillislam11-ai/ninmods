package com.ninmods.menu.service

import android.content.Context
import android.content.res.Configuration
import kotlin.math.min

object DpiHelper {
    fun dp(context: Context, value: Float): Int {
        val density = context.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    fun isLandscape(context: Context): Boolean {
        val config = context.resources.configuration
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE ||
               context.resources.displayMetrics.widthPixels > context.resources.displayMetrics.heightPixels
    }

    fun getPanelWidth(context: Context): Int {
        val dm = context.resources.displayMetrics
        return if (isLandscape(context)) {
            val maxW = dp(context, 300f)
            min((dm.widthPixels * 0.44f).toInt(), maxW)
        } else {
            val maxW = dp(context, 280f)
            min((dm.widthPixels * 0.82f).toInt(), maxW)
        }
    }

    fun getHeaderHeight(context: Context): Int {
        return dp(context, if (isLandscape(context)) 40f else 38f)
    }

    fun getBodyHeight(context: Context): Int {
        val dm = context.resources.displayMetrics
        return if (isLandscape(context)) {
            val maxH = dp(context, 180f)
            min((dm.heightPixels * 0.58f).toInt(), maxH)
        } else {
            val maxH = dp(context, 210f)
            min((dm.heightPixels * 0.45f).toInt(), maxH)
        }
    }

    fun getTotalPanelHeight(context: Context): Int {
        val logoH = getLogoHeight(context)
        val containerTopMargin = (logoH * 0.54f).toInt()
        return containerTopMargin + getHeaderHeight(context) + getBodyHeight(context) + dp(context, 16f)
    }

    fun getLogoWidth(context: Context): Int {
        return dp(context, if (isLandscape(context)) 175f else 160f)
    }

    fun getLogoHeight(context: Context): Int {
        return dp(context, if (isLandscape(context)) 68f else 62f)
    }

    fun getBodyLogoSize(context: Context): Int {
        return dp(context, if (isLandscape(context)) 270f else 250f)
    }

    fun clampX(context: Context, x: Int, viewWidth: Int): Int {
        val dm = context.resources.displayMetrics
        val maxX = (dm.widthPixels - viewWidth).coerceAtLeast(0)
        return x.coerceIn(0, maxX)
    }

    fun clampY(context: Context, y: Int, viewHeight: Int): Int {
        val dm = context.resources.displayMetrics
        val maxY = (dm.heightPixels - viewHeight).coerceAtLeast(0)
        return y.coerceIn(0, maxY)
    }
}
