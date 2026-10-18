package com.ninmods.menu.service

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ninmods.menu.ipc.DaemonManager
import com.ninmods.menu.memory.GameConfig
import com.ninmods.menu.memory.GameProcess
import com.ninmods.menu.memory.MemoryReader

object MenuInfoBanner {
    fun buildInfoLayer(context: Context, onStartClick: () -> Unit): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(
                DpiHelper.dp(context, 10f),
                DpiHelper.dp(context, 8f),
                DpiHelper.dp(context, 10f),
                DpiHelper.dp(context, 8f)
            )
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(MenuColors.CARD_BG)
                cornerRadius = DpiHelper.dp(context, 6f).toFloat()
                setStroke(1, MenuColors.CARD_BORDER)
            }
            setPadding(
                DpiHelper.dp(context, 10f),
                DpiHelper.dp(context, 6f),
                DpiHelper.dp(context, 10f),
                DpiHelper.dp(context, 6f)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun createRow(label: String, labelColor: Int, valColor: Int): TextView {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, DpiHelper.dp(context, 2.5f), 0, DpiHelper.dp(context, 2.5f))
            }
            val lbl = TextView(context).apply {
                text = label
                textSize = 10f
                setTextColor(labelColor)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(DpiHelper.dp(context, 90f), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val valTv = TextView(context).apply {
                text = "N/A"
                textSize = 10f
                setTextColor(valColor)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(lbl)
            row.addView(valTv)
            card.addView(row)
            return valTv
        }

        val statusVal = createRow("MASTER ENGINE:", MenuColors.INFO_DRIVER_LABEL, MenuColors.STATUS_OFFLINE)
        val pkgVal = createRow("PACKAGE:", MenuColors.INFO_PKG_LABEL, MenuColors.INFO_MUTED)
        val pidVal = createRow("PID:", MenuColors.INFO_PID_LABEL, MenuColors.INFO_MUTED)
        val libVal = createRow("LIBIL2CPP:", MenuColors.INFO_LIB_LABEL, MenuColors.INFO_MUTED)

        root.addView(card)

        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DpiHelper.dp(context, 8f))
        })

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DpiHelper.dp(context, 36f)).apply {
                setMargins(DpiHelper.dp(context, 8f), 0, DpiHelper.dp(context, 8f), 0)
            }
        }

        val startBtn = Button(context).apply {
            text = "START"
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(MenuColors.TEXT_WHITE)
            background = GradientDrawable().apply {
                setColor(MenuColors.START_BTN_BG)
                cornerRadius = DpiHelper.dp(context, 6f).toFloat()
                setStroke(1, MenuColors.CYAN_ACCENT)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener {
                isEnabled = false
                text = "CONNECTING..."
                Thread {
                    DaemonManager.ensureDaemonRunning(context)
                    val pid = GameProcess.getGamePid()
                    if (pid > 0) {
                        MemoryReader.ensureAttached(pid)
                    }
                    root.post {
                        isEnabled = true
                        text = "START"
                        onStartClick()
                    }
                }.start()
            }
        }

        btnRow.addView(startBtn)
        root.addView(btnRow)

        fun updateInfo() {
            Thread {
                val isDaemonOnline = DaemonManager.isRunning()
                val isDriverOnline = isDaemonOnline || MemoryReader.isKernelActive()
                val pid = GameProcess.getGamePid()
                val pkg = if (pid > 0) GameProcess.detectedPackage.ifEmpty { "Free Fire ($pid)" } else "Waiting for Game"
                val attached = MemoryReader.currentHandle != 0L && MemoryReader.currentPid == pid
                val base = if (attached) MemoryReader.getModuleBase(GameConfig.LIBIL2CPP) else 0L

                root.post {
                    val engineLabel = when {
                        isDaemonOnline -> "ONLINE (ROOT IPC)"
                        isDriverOnline -> "ONLINE (KERNEL RT)"
                        else -> "OFFLINE"
                    }

                    when {
                        attached && base > 0L -> {
                            statusVal.text = "ACTIVE ($engineLabel)"
                            statusVal.setTextColor(MenuColors.STATUS_ONLINE)
                        }
                        isDriverOnline -> {
                            statusVal.text = "READY ($engineLabel)"
                            statusVal.setTextColor(MenuColors.STATUS_ONLINE)
                        }
                        else -> {
                            statusVal.text = "OFFLINE"
                            statusVal.setTextColor(MenuColors.STATUS_OFFLINE)
                        }
                    }
                    pkgVal.text = pkg
                    pkgVal.setTextColor(if (pid > 0) MenuColors.INFO_PKG_VAL else MenuColors.INFO_MUTED)
                    pidVal.text = if (pid > 0) pid.toString() else "N/A"
                    pidVal.setTextColor(if (pid > 0) MenuColors.INFO_PID_VAL else MenuColors.INFO_MUTED)
                    libVal.text = if (base > 0L) "0x%x".format(base) else "N/A"
                    libVal.setTextColor(if (base > 0L) MenuColors.INFO_LIB_VAL else MenuColors.INFO_MUTED)
                }
            }.start()
        }

        var isAttached = false
        val loop = object : Runnable {
            override fun run() {
                updateInfo()
                if (isAttached) {
                    root.postDelayed(this, 1500L)
                }
            }
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                isAttached = true
                com.ninmods.menu.memory.AppLogger.runDiagnostics()
                root.removeCallbacks(loop)
                root.post(loop)
            }
            override fun onViewDetachedFromWindow(v: View) {
                isAttached = false
                root.removeCallbacks(loop)
            }
        })
        root.post(loop)

        return root
    }
}
