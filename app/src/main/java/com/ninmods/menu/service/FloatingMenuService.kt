package com.ninmods.menu.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ninmods.menu.MainActivity
import com.ninmods.menu.NinApplication
import com.ninmods.menu.R
import com.ninmods.menu.aimbot.AimbotThread
import com.ninmods.menu.data.PrefsHelper

class FloatingMenuService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingIcon: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var menuPanel: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var isMenuOpen = false
    private var savedMenuX: Int? = null
    private var savedMenuY: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1001, createNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        Thread {
            com.ninmods.menu.ipc.DriverStateReceiver.syncStateFromFile()
            com.ninmods.menu.memory.RootHelper.fixDriverPermissions()
            if (com.ninmods.menu.ipc.DriverStateReceiver.isDaemonActive()) {
                com.ninmods.menu.ipc.DaemonManager.ensureDaemonRunning(this)
            }
            val pid = com.ninmods.menu.memory.GameProcess.getGamePid()
            if (pid > 0) {
                com.ninmods.menu.memory.MemoryReader.ensureAttached(pid)
            }
        }.start()

        AimbotThread.start()
        com.ninmods.menu.combat.CombatThread.start()
        com.ninmods.menu.memory.ProcessSupervisor.start()
        val px = PrefsHelper.getMenuPosX(); val py = PrefsHelper.getMenuPosY()
        if (px >= 0) savedMenuX = px; if (py >= 0) savedMenuY = py
        createFloatingIcon()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isMenuOpen && menuPanel != null) {
            menuParams?.let { savedMenuX = it.x; savedMenuY = it.y }
            try { windowManager?.removeView(menuPanel) } catch (_: Exception) {}
            menuPanel = null; isMenuOpen = false
            showMenuPanel()
        } else {
            floatingIcon?.let { icon ->
                floatingParams?.let { p ->
                    val size = DpiHelper.dp(this, 88f)
                    p.x = DpiHelper.clampX(this, p.x, size); p.y = DpiHelper.clampY(this, p.y, size)
                    try { windowManager?.updateViewLayout(icon, p) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun createFloatingIcon() {
        val root = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        floatingIcon = root
        val logoSize = DpiHelper.dp(this, 75f); val badgeSize = DpiHelper.dp(this, 20f); val boxSize = DpiHelper.dp(this, 88f)

        val logoView = ImageView(this).apply {
            setImageResource(R.drawable.nin_logo); scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(logoSize, logoSize).apply { gravity = Gravity.CENTER }
        }
        root.addView(logoView)

        val closeBadge = TextView(this).apply {
            text = "✕"; textSize = 9.5f; setTextColor(Color.parseColor("#00D2FF")); gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT); setStroke(DpiHelper.dp(context, 1f), Color.parseColor("#00D2FF"))
            }
            layoutParams = FrameLayout.LayoutParams(badgeSize, badgeSize).apply { gravity = Gravity.TOP or Gravity.END }
            setOnClickListener { stopSelf() }
        }
        root.addView(closeBadge)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(boxSize, boxSize, layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = DpiHelper.clampX(this@FloatingMenuService, PrefsHelper.getFloatingPosX(100), boxSize)
            y = DpiHelper.clampY(this@FloatingMenuService, PrefsHelper.getFloatingPosY(300), boxSize)
        }
        floatingParams = params
        val dragListener = DragTouchListener(windowManager!!, params, root) { if (isMenuOpen) hideMenuPanel() else showMenuPanel() }
        logoView.setOnTouchListener(dragListener); root.setOnTouchListener(dragListener)
        try { windowManager?.addView(root, params) } catch (_: Exception) {}
    }

    private fun setFloatingVisibility(visible: Boolean) {
        floatingIcon?.let { icon ->
            icon.visibility = if (visible) View.VISIBLE else View.GONE
            floatingParams?.let { p ->
                p.alpha = if (visible) 1f else 0f
                p.flags = if (visible) WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE else (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                try { windowManager?.updateViewLayout(icon, p) } catch (_: Exception) {}
            }
        }
    }

    private fun showMenuPanel() {
        if (isMenuOpen || menuPanel != null) return
        setFloatingVisibility(false)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val dm = resources.displayMetrics
        val panelW = DpiHelper.getPanelWidth(this)
        val panelH = DpiHelper.getTotalPanelHeight(this)
        val posX = DpiHelper.clampX(this, savedMenuX ?: ((dm.widthPixels - panelW) / 2), panelW)
        val posY = DpiHelper.clampY(this, savedMenuY ?: ((dm.heightPixels - panelH) / 2), panelH)
        savedMenuX = posX; savedMenuY = posY

        val params = WindowManager.LayoutParams(panelW, WindowManager.LayoutParams.WRAP_CONTENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = posX; y = posY
        }
        menuParams = params

        val headerTouchListener = object : View.OnTouchListener {
            var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = DpiHelper.clampX(this@FloatingMenuService, startX + (e.rawX - touchX).toInt(), panelW)
                        params.y = DpiHelper.clampY(this@FloatingMenuService, startY + (e.rawY - touchY).toInt(), panelH)
                        savedMenuX = params.x; savedMenuY = params.y
                        try { windowManager?.updateViewLayout(menuPanel, params) } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        savedMenuX = params.x; savedMenuY = params.y
                        PrefsHelper.saveMenuPos(params.x, params.y)
                        return true
                    }
                }
                return false
            }
        }
        menuPanel = MenuPanelBuilder.build(this, { hideMenuPanel() }, headerTouchListener)
        try { windowManager?.addView(menuPanel, params); isMenuOpen = true } catch (_: Exception) {}
    }

    private fun hideMenuPanel() {
        menuParams?.let { mp ->
            savedMenuX = mp.x; savedMenuY = mp.y
            PrefsHelper.saveMenuPos(mp.x, mp.y)
            val boxSize = DpiHelper.dp(this, 88f); val panelW = DpiHelper.getPanelWidth(this)
            floatingParams?.let { fp ->
                fp.x = DpiHelper.clampX(this, mp.x + (panelW - boxSize) / 2, boxSize)
                fp.y = DpiHelper.clampY(this, mp.y, boxSize)
                PrefsHelper.saveFloatingPos(fp.x, fp.y)
                floatingIcon?.let { fi -> try { windowManager?.updateViewLayout(fi, fp) } catch (_: Exception) {} }
            }
        }
        if (menuPanel != null) { try { windowManager?.removeView(menuPanel) } catch (_: Exception) {} ; menuPanel = null }
        setFloatingVisibility(true); isMenuOpen = false
    }

    private fun createNotification(): Notification {
        val pIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NinApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(R.drawable.nin_logo)
            .setContentIntent(pIntent).setOngoing(true).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (menuPanel != null) { try { windowManager?.removeView(menuPanel) } catch (_: Exception) {} }
        if (floatingIcon != null) { try { windowManager?.removeView(floatingIcon) } catch (_: Exception) {} }
        MenuPanelBuilder.resetSession()
        PrefsHelper.resetFeatures()
        try { stopService(Intent(this, com.ninmods.menu.esp.EspOverlayService::class.java)) } catch (_: Exception) {}
        AimbotThread.stop()
        com.ninmods.menu.combat.CombatThread.stop()
        com.ninmods.menu.memory.ProcessSupervisor.stop()
        com.ninmods.menu.memory.MemoryReader.close()
        com.ninmods.menu.ipc.DaemonManager.stopDaemon()
        PrefsHelper.setDriverMode(com.ninmods.menu.ipc.DriverMode.NONE)
    }
}
