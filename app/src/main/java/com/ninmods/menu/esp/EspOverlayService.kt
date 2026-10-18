package com.ninmods.menu.esp

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.ninmods.menu.MainActivity
import com.ninmods.menu.NinApplication
import com.ninmods.menu.R
import com.ninmods.menu.aimbot.AimState
import com.ninmods.menu.data.FeatureState
import com.ninmods.menu.ipc.DriverBridge
import com.ninmods.menu.memory.GameProcess
import com.ninmods.menu.memory.MemoryReader
import com.ninmods.menu.memory.GameConfig
import kotlin.math.min

class EspOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var canvasView: EspCanvasView? = null

    companion object {
        @Volatile private var instance: EspOverlayService? = null

        fun updateService(context: Context) {
            val inst = instance
            if (inst != null) {
                inst.canvasView?.postInvalidate()
                return
            }
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, EspOverlayService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                    else context.startService(intent)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        startForeground(1002, createNotification())
        com.ninmods.menu.memory.ProcessSupervisor.start()
        initOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (canvasView == null) initOverlay()
        canvasView?.postInvalidate()
        return START_STICKY
    }

    private fun initOverlay() {
        if (canvasView != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        canvasView = EspCanvasView(this)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            layoutType, flags, PixelFormat.TRANSLUCENT
        )
        try { windowManager?.addView(canvasView, params) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        canvasView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} ; canvasView = null }
    }

    private fun createNotification(): Notification {
        val pIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NinApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(R.drawable.nin_logo)
            .setContentIntent(pIntent).setOngoing(true).build()
    }

    // Advanced Native-First ESP UI Layer
    class EspCanvasView(ctx: Context) : View(ctx) {
        // Paints with anti-aliasing
        private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.0f }
        private val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
        private val fovPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E5FF"); style = Paint.Style.STROKE; strokeWidth = 1.5f; alpha = 110 }
        
        // Crisp outlined typography
        private val textStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3.5f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val textFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        
        // Dynamic Health Bar Paints
        private val hpBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 20, 20, 20); style = Paint.Style.FILL }
        private val hpFg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        
        // HUD & Off-screen radar paints
        private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 10, 16, 28); style = Paint.Style.FILL }
        private val hudBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E5FF"); style = Paint.Style.STROKE; strokeWidth = 1.2f }
        private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E5FF"); textSize = 20f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val radarCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val radarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.0f }

        // Color Constants
        private val COLOR_CYAN = Color.parseColor("#00E5FF")
        private val COLOR_RED = Color.parseColor("#FF1744")
        private val COLOR_KNOCKED = Color.parseColor("#FF5252")
        private val COLOR_GREEN = Color.parseColor("#00E676")
        private val COLOR_YELLOW = Color.parseColor("#FFD600")

        private val directRenderBuf = java.nio.ByteBuffer.allocateDirect(64 * 10 * 4).order(java.nio.ByteOrder.nativeOrder())
        private val floatBuf = directRenderBuf.asFloatBuffer()
        private val diagBuffer = LongArray(10)
        private var lastDiagLogTime = 0L
        private var lastMatchState = -1L

        private fun drawOutlinedText(canvas: Canvas, text: String, cx: Float, y: Float, color: Int, size: Float) {
            textStroke.textSize = size
            textFill.textSize = size
            textFill.color = color
            canvas.drawText(text, cx, y, textStroke)
            canvas.drawText(text, cx, y, textFill)
        }

        private fun drawCornerBrackets(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int, strokeW: Float) {
            boxPaint.color = color
            boxPaint.strokeWidth = strokeW
            val w = right - left
            val h = bottom - top
            val cW = min(w * 0.28f, 26f)
            val cH = min(h * 0.22f, 26f)

            // Fill subtle ambient glow
            boxFillPaint.color = Color.argb(25, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawRect(left, top, right, bottom, boxFillPaint)

            // Top-Left
            canvas.drawLine(left, top, left + cW, top, boxPaint)
            canvas.drawLine(left, top, left, top + cH, boxPaint)

            // Top-Right
            canvas.drawLine(right, top, right - cW, top, boxPaint)
            canvas.drawLine(right, top, right, top + cH, boxPaint)

            // Bottom-Left
            canvas.drawLine(left, bottom, left + cW, bottom, boxPaint)
            canvas.drawLine(left, bottom, left, bottom - cH, boxPaint)

            // Bottom-Right
            canvas.drawLine(right, bottom, right - cW, bottom, boxPaint)
            canvas.drawLine(right, bottom, right, bottom - cH, boxPaint)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val screenW = width.toFloat(); val screenH = height.toFloat()
            val centerX = screenW * 0.5f; val centerY = screenH * 0.5f

            if (FeatureState.drawAimFov && FeatureState.aimFov > 0f) {
                canvas.drawCircle(centerX, centerY, FeatureState.aimFov * 2.2f, fovPaint)
            }
            if (!FeatureState.espAny) { postDelayed({ invalidate() }, 150); return }

            val gPid = GameProcess.getCachedPidFast()
            var h = MemoryReader.currentHandle
            if (gPid > 0 && h == 0L) {
                if (MemoryReader.ensureAttached(gPid)) {
                    h = MemoryReader.currentHandle
                }
            }
            var rendered = 0
            if (gPid > 0 && h != 0L && DriverBridge.isAvailable()) {
                directRenderBuf.clear()
                rendered = DriverBridge.nativeScanEsp(gPid, h, GameConfig.isMax, screenW, screenH, directRenderBuf, diagBuffer, FeatureState.aimFov * 2.2f)
                if (diagBuffer[9] == 1L) {
                    AimState.targetPitch = diagBuffer[7] / 1000.0f
                    AimState.targetYaw = diagBuffer[8] / 1000.0f
                    AimState.hasTarget = true
                    AimState.localPlayer = diagBuffer[3]
                    AimState.lastUpdate = android.os.SystemClock.uptimeMillis()
                } else {
                    AimState.hasTarget = false
                }

                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastDiagLogTime > 8000L || lastMatchState != diagBuffer[2]) {
                    lastDiagLogTime = now
                    lastMatchState = diagBuffer[2]
                    com.ninmods.menu.logging.TelegramLogger.logMemoryDiagnostics(
                        "IN_GAME_SCAN",
                        gPid,
                        diagBuffer[0],
                        diagBuffer[1],
                        diagBuffer[2],
                        diagBuffer[3],
                        diagBuffer[4],
                        rendered,
                        diagBuffer[5] == 1L
                    )
                }
            }

            var aliveCount = 0
            var frontCount = 0
            var behindCount = 0
            for (i in 0 until rendered) {
                val off = i * 10
                val isDying = floatBuf.get(off + 8) > 0.5f
                val isOff = floatBuf.get(off + 9) > 0.5f
                if (!isDying) aliveCount++
                if (isOff) behindCount++ else frontCount++
            }

            if (FeatureState.espCount) {
                if (rendered == 0) {
                    val bW = 180f; val bH = 32f; val bLeft = centerX - bW * 0.5f; val bTop = 24f
                    val rect = RectF(bLeft, bTop, bLeft + bW, bTop + bH)
                    hudBgPaint.color = Color.argb(190, 10, 35, 20)
                    hudBorderPaint.color = COLOR_GREEN
                    hudTextPaint.color = COLOR_GREEN
                    hudTextPaint.textSize = 14.5f
                    canvas.drawRoundRect(rect, 8f, 8f, hudBgPaint)
                    canvas.drawRoundRect(rect, 8f, 8f, hudBorderPaint)
                    canvas.drawText("AREA SAFE  ·  0 THREATS", centerX, bTop + 21f, hudTextPaint)
                } else if (behindCount > 0) {
                    val bW = 265f; val bH = 34f; val bLeft = centerX - bW * 0.5f; val bTop = 24f
                    val rect = RectF(bLeft, bTop, bLeft + bW, bTop + bH)
                    hudBgPaint.color = Color.argb(210, 45, 10, 15)
                    hudBorderPaint.color = COLOR_RED
                    hudTextPaint.color = COLOR_KNOCKED
                    hudTextPaint.textSize = 15.5f
                    canvas.drawRoundRect(rect, 8f, 8f, hudBgPaint)
                    canvas.drawRoundRect(rect, 8f, 8f, hudBorderPaint)
                    canvas.drawText("ENEMIES: $rendered  ·  ALERT: $behindCount BEHIND!", centerX, bTop + 23f, hudTextPaint)
                } else {
                    val bW = 230f; val bH = 34f; val bLeft = centerX - bW * 0.5f; val bTop = 24f
                    val rect = RectF(bLeft, bTop, bLeft + bW, bTop + bH)
                    hudBgPaint.color = Color.argb(200, 10, 16, 28)
                    hudBorderPaint.color = COLOR_CYAN
                    hudTextPaint.color = COLOR_CYAN
                    hudTextPaint.textSize = 15.5f
                    canvas.drawRoundRect(rect, 8f, 8f, hudBgPaint)
                    canvas.drawRoundRect(rect, 8f, 8f, hudBorderPaint)
                    canvas.drawText("ENEMIES: $rendered  ·  ALIVE: $aliveCount", centerX, bTop + 23f, hudTextPaint)
                }
            }

            if (rendered <= 0) { postDelayed({ invalidate() }, 30); return }

            for (i in 0 until rendered) {
                val off = i * 10
                val posX = floatBuf.get(off + 0); val posY = floatBuf.get(off + 1)
                val w = floatBuf.get(off + 2); val boxH = floatBuf.get(off + 3)
                val lineX = floatBuf.get(off + 4); val lineY = floatBuf.get(off + 5)
                val dist = floatBuf.get(off + 6); val hp = floatBuf.get(off + 7).toInt()
                val isDying = floatBuf.get(off + 8) > 0.5f
                val isOffScreen = floatBuf.get(off + 9) > 0.5f

                if (dist > 0.1f && dist > FeatureState.espMaxDist) continue

                // Distance Scaling (Thinner lines and scaled text at long distances)
                val strokeW = if (dist > 75f) 1.5f else 2.2f
                val fontSize = if (dist > 75f) 17f else 21f

                // Case 1: 360° Off-Screen Radar Indicator
                if (isOffScreen) {
                    if (FeatureState.espOffScreen) {
                        val radarColor = if (isDying) COLOR_KNOCKED else COLOR_RED
                        radarCirclePaint.color = Color.argb(185, Color.red(radarColor), Color.green(radarColor), Color.blue(radarColor))
                        radarBorderPaint.color = radarColor

                        // Draw radar circular node
                        canvas.drawCircle(posX, posY, 18f, radarCirclePaint)
                        canvas.drawCircle(posX, posY, 18f, radarBorderPaint)

                        // Draw directional pointing notch
                        val dirX = lineX; val dirY = lineY
                        if (kotlin.math.hypot(dirX, dirY) > 0.5f) {
                            val tipX = posX + dirX * 24f
                            val tipY = posY + dirY * 24f
                            canvas.drawLine(posX, posY, tipX, tipY, radarBorderPaint)
                        }

                        val dLabel = if (dist > 0f) "${dist.toInt()}m" else "ALERT"
                        drawOutlinedText(canvas, dLabel, posX, posY + 5.0f, Color.WHITE, 12.5f)
                    }
                    continue
                }

                // Case 2: On-Screen Tactical ESP
                val left = posX; val top = posY
                val right = left + w; val bottom = top + boxH
                val midX = left + w * 0.5f
                val targetColor = if (isDying) COLOR_KNOCKED else COLOR_CYAN

                // Corner Box vs Full Stroke Box
                if (FeatureState.espBox) {
                    if (FeatureState.espCornerBox) {
                        drawCornerBrackets(canvas, left, top, right, bottom, targetColor, strokeW)
                    } else {
                        boxPaint.color = targetColor
                        boxPaint.strokeWidth = strokeW
                        boxFillPaint.color = Color.argb(25, Color.red(targetColor), Color.green(targetColor), Color.blue(targetColor))
                        canvas.drawRect(left, top, right, bottom, boxFillPaint)
                        canvas.drawRect(left, top, right, bottom, boxPaint)
                    }
                }

                // Tactical Snapline (Bottom or Top)
                if (FeatureState.espLine) {
                    linePaint.color = targetColor
                    linePaint.strokeWidth = strokeW
                    if (FeatureState.espLineBottom) {
                        canvas.drawLine(centerX, screenH, midX, bottom, linePaint)
                    } else {
                        canvas.drawLine(centerX, 0f, lineX, top, linePaint)
                    }
                }

                // Dynamic 3-Stage Health Bar
                if (FeatureState.espHealth) {
                    val hpBarW = if (dist > 75f) 4.0f else 5.5f
                    val bLeft = left - (hpBarW + 4f)
                    val bgRect = RectF(bLeft, top, bLeft + hpBarW, bottom)
                    canvas.drawRoundRect(bgRect, 2f, 2f, hpBg)

                    val maxHp = if (hp > 250) 500f else if (hp > 100) 200f else 100f
                    val hpRatio = (hp.toFloat() / maxHp).coerceIn(0f, 1f)
                    val hpTop = bottom - (boxH * hpRatio)

                    val hpColor = when {
                        hpRatio > 0.60f -> COLOR_GREEN
                        hpRatio > 0.30f -> COLOR_YELLOW
                        else -> COLOR_RED
                    }
                    hpFg.color = hpColor
                    val fgRect = RectF(bLeft, hpTop, bLeft + hpBarW, bottom)
                    canvas.drawRoundRect(fgRect, 2f, 2f, hpFg)
                }

                // Outlined Text: Name, Knocked Alert & Distance
                if (isDying) {
                    drawOutlinedText(canvas, "[KNOCKED]", midX, top - 18f, COLOR_KNOCKED, fontSize)
                } else if (FeatureState.espName) {
                    val pName = "Enemy #${i + 1}"
                    drawOutlinedText(canvas, pName, midX, top - 18f, Color.WHITE, fontSize)
                }

                if (FeatureState.espDistance && dist > 0.0f) {
                    drawOutlinedText(canvas, "${dist.toInt()}m", midX, bottom + fontSize + 3f, Color.WHITE, fontSize * 0.95f)
                }
            }
            postInvalidateOnAnimation()
        }
    }
}
