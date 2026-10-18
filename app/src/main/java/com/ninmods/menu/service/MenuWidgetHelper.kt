package com.ninmods.menu.service

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.*
import com.ninmods.menu.data.FeatureState
import com.ninmods.menu.data.PrefsHelper

object MenuWidgetHelper {
    fun addSectionHeader(ctx: Context, p: LinearLayout, t: String) {
        p.addView(TextView(ctx).apply {
            text = t; textSize = 10.5f; setTextColor(MenuColors.CYAN_ACCENT)
            setPadding(0, DpiHelper.dp(ctx, 6f), 0, DpiHelper.dp(ctx, 2f))
        })
    }

    private fun createSwitchCol(
        ctx: Context,
        lbl: String,
        cur: Boolean,
        onVal: (Boolean) -> Unit
    ): LinearLayout {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, DpiHelper.dp(ctx, 1f), DpiHelper.dp(ctx, 2f), DpiHelper.dp(ctx, 1f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = Switch(ctx).apply {
            isChecked = cur
            thumbTintList = MenuColors.CYAN_STATE_LIST
            trackTintList = MenuColors.SWITCH_TRACK_TINT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnCheckedChangeListener { _, c ->
                onVal(c)
                PrefsHelper.saveAll()
                com.ninmods.menu.logging.TelegramLogger.logToggle(lbl, c)
                com.ninmods.menu.esp.EspOverlayService.updateService(ctx)
            }
        }
        val tv = TextView(ctx).apply {
            text = lbl
            textSize = 9f
            setTextColor(MenuColors.TEXT_WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = DpiHelper.dp(ctx, 3f)
            }
        }
        col.addView(sw)
        col.addView(tv)
        col.setOnClickListener { sw.toggle() }
        return col
    }

    fun addSwitchPair(
        ctx: Context,
        p: LinearLayout,
        name1: String, cur1: Boolean, onVal1: (Boolean) -> Unit,
        name2: String? = null, cur2: Boolean = false, onVal2: ((Boolean) -> Unit)? = null
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 2f
            setPadding(0, DpiHelper.dp(ctx, 1.5f), 0, DpiHelper.dp(ctx, 1.5f))
        }
        row.addView(createSwitchCol(ctx, name1, cur1, onVal1))
        if (name2 != null && onVal2 != null) {
            row.addView(createSwitchCol(ctx, name2, cur2, onVal2))
        } else {
            row.addView(Space(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
        }
        p.addView(row)
    }

    fun addSkipBar(
        ctx: Context,
        p: LinearLayout,
        lbl: String,
        cur: Float,
        min: Float,
        max: Float,
        unit: String,
        onVal: (Float) -> Unit
    ) {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, DpiHelper.dp(ctx, 2f), 0, DpiHelper.dp(ctx, 2f))
        }
        val title = TextView(ctx).apply {
            text = "$lbl: ${cur.toInt()}$unit"
            textSize = 9f
            setTextColor(MenuColors.CYAN_DIM)
            setPadding(DpiHelper.dp(ctx, 4f), 0, 0, 0)
        }
        val bar = SeekBar(ctx).apply {
            this.max = (max - min).toInt()
            progress = (cur - min).toInt()
            progressTintList = MenuColors.CYAN_STATE_LIST
            thumbTintList = MenuColors.CYAN_STATE_LIST
            setPadding(DpiHelper.dp(ctx, 12f), DpiHelper.dp(ctx, 4f), DpiHelper.dp(ctx, 12f), DpiHelper.dp(ctx, 4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, pr: Int, u: Boolean) {
                    val v = min + pr
                    title.text = "$lbl: ${v.toInt()}$unit"
                    onVal(v)
                    PrefsHelper.saveAll()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) { sb?.parent?.requestDisallowInterceptTouchEvent(true) }
                override fun onStopTrackingTouch(sb: SeekBar?) { sb?.parent?.requestDisallowInterceptTouchEvent(false) }
            })
        }
        container.addView(title)
        container.addView(bar)
        p.addView(container)
    }

    fun addTriggerGrid(ctx: Context, parent: LinearLayout) {
        val opts = listOf("OFF", "ALWAYS")
        val grid = TableLayout(ctx).apply { setPadding(0, DpiHelper.dp(ctx, 2f), 0, DpiHelper.dp(ctx, 2f)); isStretchAllColumns = true }
        val r1 = TableRow(ctx); val rbs = mutableListOf<RadioButton>()
        opts.forEachIndexed { i, name ->
            val rb = RadioButton(ctx).apply {
                text = name; textSize = 9f; setTextColor(MenuColors.TEXT_WHITE); buttonTintList = MenuColors.CYAN_STATE_LIST
                isChecked = (FeatureState.aimMode == i); layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { FeatureState.aimMode = i; PrefsHelper.saveAll(); rbs.forEachIndexed { idx, b -> b.isChecked = (idx == i) } }
            }
            rbs.add(rb)
            r1.addView(rb)
        }
        grid.addView(r1); parent.addView(grid)
    }

    fun addLiveStatusHeader(ctx: Context, parent: LinearLayout) {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(MenuColors.CARD_BG)
                cornerRadius = DpiHelper.dp(ctx, 4f).toFloat()
                setStroke(1, MenuColors.CARD_BORDER)
            }
            setPadding(DpiHelper.dp(ctx, 8f), DpiHelper.dp(ctx, 4f), DpiHelper.dp(ctx, 8f), DpiHelper.dp(ctx, 4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = DpiHelper.dp(ctx, 4f)
            }
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val dotTv = TextView(ctx).apply {
            text = "● "
            textSize = 10f
            setTextColor(MenuColors.STATUS_ONLINE)
        }
        val engineTv = TextView(ctx).apply {
            text = "ENGINE: RT KERNEL"
            textSize = 9.5f
            setTextColor(MenuColors.CYAN_ACCENT)
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val pidTv = TextView(ctx).apply {
            text = "PID: --"
            textSize = 9.5f
            setTextColor(MenuColors.INFO_PID_VAL)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        topRow.addView(dotTv)
        topRow.addView(engineTv)
        topRow.addView(pidTv)
        card.addView(topRow)

        val bottomRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, DpiHelper.dp(ctx, 2f), 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val libLbl = TextView(ctx).apply {
            text = "LIBIL2CPP: "
            textSize = 9f
            setTextColor(MenuColors.INFO_LIB_LABEL)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val libVal = TextView(ctx).apply {
            text = "SCANNING..."
            textSize = 9f
            setTextColor(MenuColors.STATUS_ONLINE)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        bottomRow.addView(libLbl)
        bottomRow.addView(libVal)
        card.addView(bottomRow)

        val exportBtn = TextView(ctx).apply {
            text = "📁 SAVE TO DOWNLOAD & SEND BOT"
            textSize = 8.5f
            setTextColor(MenuColors.TEXT_WHITE)
            gravity = Gravity.CENTER
            setPadding(DpiHelper.dp(ctx, 6f), DpiHelper.dp(ctx, 3f), DpiHelper.dp(ctx, 6f), DpiHelper.dp(ctx, 3f))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#0288D1"))
                cornerRadius = DpiHelper.dp(ctx, 3f).toFloat()
            }
            setOnClickListener {
                com.ninmods.menu.logging.TelegramLogger.exportAndSend(ctx, "Menu Export Button")
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = DpiHelper.dp(ctx, 4f)
            }
        }
        card.addView(exportBtn)

        parent.addView(card)

        // Dynamic 1.5s real-time refresher loop
        val refresher = object : Runnable {
            override fun run() {
                val isKernel = com.ninmods.menu.ipc.DriverStateReceiver.isKernelActive() || com.ninmods.menu.memory.MemoryReader.isKernelActive()
                val isDaemon = com.ninmods.menu.ipc.DriverStateReceiver.isDaemonActive() || com.ninmods.menu.ipc.DaemonManager.isRunning()
                val isDriver = isKernel || isDaemon
                val pid = com.ninmods.menu.memory.GameProcess.getCachedPidFast()
                val base = if (pid > 0) com.ninmods.menu.memory.MemoryReader.getModuleBase(com.ninmods.menu.memory.GameConfig.LIBIL2CPP) else 0L

                engineTv.text = when {
                    isKernel -> "ENGINE: RT KERNEL"
                    isDaemon -> "ENGINE: ROOT IPC"
                    else -> "ENGINE: OFFLINE"
                }
                dotTv.setTextColor(if (isDriver) MenuColors.STATUS_ONLINE else MenuColors.STATUS_OFFLINE)
                pidTv.text = if (pid > 0) "PID: $pid" else "WAITING GAME"
                libVal.text = if (base > 0L) "0x%x".format(base) else if (pid > 0) "RESOLVING RAM..." else "GAME NOT READY"
                libVal.setTextColor(if (base > 0L) MenuColors.STATUS_ONLINE else MenuColors.INFO_MUTED)

                card.postDelayed(this, 1500L)
            }
        }
        card.post(refresher)

        // Instant broadcast listener callback
        com.ninmods.menu.ipc.DriverStateReceiver.onStateChangeListener = { _, _ ->
            card.post(refresher)
        }
    }
}
