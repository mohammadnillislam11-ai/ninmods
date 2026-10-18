package com.ninmods.menu.service

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.ninmods.menu.data.FeatureState

object MenuTabsBuilder {
    fun buildTabs(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, DpiHelper.dp(context, 2f), 0, DpiHelper.dp(context, 8f))
        }

        // Live Real-Time Driver & Memory Status Header
        MenuWidgetHelper.addLiveStatusHeader(context, root)

        // Section 1: VISUALS (ESP)
        MenuWidgetHelper.addSectionHeader(context, root, "VISUALS (ESP)")
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "ESP LINE", FeatureState.espLine, { FeatureState.espLine = it },
            "ESP BOX", FeatureState.espBox, { FeatureState.espBox = it }
        )
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "HEALTH BAR", FeatureState.espHealth, { FeatureState.espHealth = it },
            "DISTANCE", FeatureState.espDistance, { FeatureState.espDistance = it }
        )
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "NAME", FeatureState.espName, { FeatureState.espName = it },
            "COUNT BANNER", FeatureState.espCount, { FeatureState.espCount = it }
        )
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "DRAW FOV", FeatureState.drawAimFov, { FeatureState.drawAimFov = it },
            "360° RADAR", FeatureState.espOffScreen, { FeatureState.espOffScreen = it }
        )
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "CORNER BOX", FeatureState.espCornerBox, { FeatureState.espCornerBox = it },
            "BOTTOM LINE", FeatureState.espLineBottom, { FeatureState.espLineBottom = it }
        )
        MenuWidgetHelper.addSkipBar(context, root, "ESP MAX DIST", FeatureState.espMaxDist, 50f, 350f, "m") { FeatureState.espMaxDist = it }
        MenuWidgetHelper.addSkipBar(context, root, "FOV CIRCLE", FeatureState.aimFov, 10f, 360f, "°") { FeatureState.aimFov = it }

        // Section 2: AIMBOT
        MenuWidgetHelper.addSectionHeader(context, root, "AIMBOT & HELPER")
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "AUTO LOCK", FeatureState.aimbot, { FeatureState.aimbot = it },
            "SILENT AIM", FeatureState.silentAim, { FeatureState.silentAim = it }
        )
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "IGNORE KNOCK", FeatureState.ignoreKnock, { FeatureState.ignoreKnock = it },
            "SMART KILL", FeatureState.smartKill, { FeatureState.smartKill = it }
        )
        MenuWidgetHelper.addSkipBar(context, root, "AIM FOV", FeatureState.aimFov, 10f, 360f, "°") { FeatureState.aimFov = it }
        MenuWidgetHelper.addTriggerGrid(context, root)

        // Section 3: COMBAT (RAM MODS)
        MenuWidgetHelper.addSectionHeader(context, root, "COMBAT (RAM MODIFIERS)")
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "FLY HACK", FeatureState.flyHack, { FeatureState.flyHack = it },
            "SPEED HACK", FeatureState.speedHack, { FeatureState.speedHack = it }
        )
        MenuWidgetHelper.addSkipBar(context, root, "FLY HEIGHT", FeatureState.flyHeight, 1f, 15f, "m") { FeatureState.flyHeight = it }
        MenuWidgetHelper.addSkipBar(context, root, "SPEED MULTIPLIER", FeatureState.flySpeed, 1f, 10f, "x") { FeatureState.flySpeed = it }
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "PC MOVE 360°", FeatureState.pcMovement, { FeatureState.pcMovement = it },
            "GHOST HACK", FeatureState.ghostHack, { FeatureState.ghostHack = it }
        )
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "TP MARK", FeatureState.tpMark, { FeatureState.tpMark = it },
            "SPIN BOT", FeatureState.spinBot, { FeatureState.spinBot = it }
        )
        MenuWidgetHelper.addSwitchPair(
            context, root,
            "WALLBUG", FeatureState.wallBug, { FeatureState.wallBug = it },
            "SCALE DOWN", FeatureState.scaleDown, { FeatureState.scaleDown = it }
        )

        return root
    }
}
