package com.ninmods.menu.service

import android.content.res.ColorStateList
import android.graphics.Color

object MenuColors {
    // Primary Brand & Accent Colors (like CSS :root)
    val CYAN_ACCENT = Color.parseColor("#00D2FF")
    val CYAN_BORDER = Color.parseColor("#8000D2FF")
    val CYAN_SUBTLE = Color.parseColor("#3300D2FF")
    val CYAN_DIM = Color.parseColor("#80D2FF")
    
    // Backgrounds & Surfaces
    val PANEL_BG = Color.parseColor("#EE182944")
    val CARD_BG = Color.parseColor("#990B1626")
    val CARD_BORDER = Color.parseColor("#1C3B5E")
    val AMBIENT_BLUR = Color.parseColor("#2200D2FF")

    // Status Colors
    val STATUS_ONLINE = Color.parseColor("#00FF88")
    val STATUS_OFFLINE = Color.parseColor("#FF4D4D")
    
    // Distinct Info Row Colors
    val INFO_DRIVER_LABEL = Color.parseColor("#00D2FF")
    val INFO_PKG_LABEL = Color.parseColor("#FFB800")
    val INFO_PKG_VAL = Color.parseColor("#FFE082")
    val INFO_PID_LABEL = Color.parseColor("#C084FC")
    val INFO_PID_VAL = Color.parseColor("#E9D5FF")
    val INFO_LIB_LABEL = Color.parseColor("#38BDF8")
    val INFO_LIB_VAL = Color.parseColor("#7DD3FC")
    val INFO_MUTED = Color.parseColor("#778899")

    // Buttons & Texts
    val START_BTN_BG = Color.parseColor("#EE0077C8")
    val TEXT_WHITE = Color.WHITE
    val TRACK_ON = Color.parseColor("#6600D2FF")
    val TRACK_OFF = Color.parseColor("#334455")

    // State Lists
    val SWITCH_TRACK_TINT = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(TRACK_ON, TRACK_OFF)
    )
    val CYAN_STATE_LIST = ColorStateList.valueOf(CYAN_ACCENT)
}
