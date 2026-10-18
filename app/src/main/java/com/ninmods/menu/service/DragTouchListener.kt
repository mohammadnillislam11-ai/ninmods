package com.ninmods.menu.service

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.ninmods.menu.data.PrefsHelper
import kotlin.math.hypot

class DragTouchListener(
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val targetView: View? = null,
    private val onTap: () -> Unit
) : View.OnTouchListener {

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val updateTarget = targetView ?: v
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!isDragging && hypot(dx, dy) > 10f) {
                    isDragging = true
                }
                if (isDragging) {
                    val newX = initialX + dx.toInt()
                    val newY = initialY + dy.toInt()
                    layoutParams.x = DpiHelper.clampX(updateTarget.context, newX, layoutParams.width)
                    layoutParams.y = DpiHelper.clampY(updateTarget.context, newY, layoutParams.height)
                    try {
                        windowManager.updateViewLayout(updateTarget, layoutParams)
                    } catch (_: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    onTap()
                } else {
                    PrefsHelper.saveFloatingPos(layoutParams.x, layoutParams.y)
                }
                return true
            }
        }
        return false
    }
}
