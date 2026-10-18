package com.ninmods.menu

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ninmods.menu.auth.AuthManager
import com.ninmods.menu.esp.EspOverlayService
import com.ninmods.menu.service.FloatingMenuService
import com.ninmods.menu.ui.theme.NinmodsMenuTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NinmodsMenuTheme {
                MainScreen(
                    onStartMenu = { handleStartMenu() },
                    onLogout = { handleLogout() },
                    onExit = { finishAffinity() }
                )
            }
        }
    }

    private fun handleStartMenu() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("Please grant 'Display over other apps' permission to allow the floating menu to appear.")
                .setPositiveButton("Grant") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val hasFf = isPackageInstalled(com.ninmods.menu.memory.GameConfig.GAME_PACKAGE_FF)
        val hasMax = isPackageInstalled(com.ninmods.menu.memory.GameConfig.GAME_PACKAGE_MAX)

        if (hasFf && hasMax) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Select Game")
                .setMessage("Both Free Fire and Free Fire MAX are installed. Which game do you want to open?")
                .setNegativeButton("Free Fire") { _, _ ->
                    showLoadingAndLaunch(com.ninmods.menu.memory.GameConfig.GAME_PACKAGE_FF, "Free Fire")
                }
                .setPositiveButton("Free Fire MAX") { _, _ ->
                    showLoadingAndLaunch(com.ninmods.menu.memory.GameConfig.GAME_PACKAGE_MAX, "Free Fire MAX")
                }
                .setNeutralButton("Cancel", null)
                .show()
            return
        }

        if (hasFf) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Start Game")
                .setMessage("Start floating menu and launch Free Fire?")
                .setPositiveButton("Start") { _, _ ->
                    showLoadingAndLaunch(com.ninmods.menu.memory.GameConfig.GAME_PACKAGE_FF, "Free Fire")
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        if (hasMax) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Start Game")
                .setMessage("Start floating menu and launch Free Fire MAX?")
                .setPositiveButton("Start") { _, _ ->
                    showLoadingAndLaunch(com.ninmods.menu.memory.GameConfig.GAME_PACKAGE_MAX, "Free Fire MAX")
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Start Menu")
            .setMessage("No Free Fire game detected on this device. Do you want to start the floating menu anyway?")
            .setPositiveButton("Start Anyway") { _, _ ->
                showLoadingAndLaunch(null, "")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            packageManager.getLaunchIntentForPackage(pkg) != null
        }
    }

    private fun showLoadingAndLaunch(targetPkg: String?, gameName: String) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val space = (16 * resources.displayMetrics.density).toInt()

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val progressBar = android.widget.ProgressBar(this).apply {
            isIndeterminate = true
        }
        val textView = android.widget.TextView(this).apply {
            text = if (targetPkg != null) "Launching $gameName..." else "Starting floating menu..."
            textSize = 15f
            setPadding(space, 0, 0, 0)
        }
        layout.addView(progressBar)
        layout.addView(textView)

        val dialog = android.app.AlertDialog.Builder(this)
            .setCancelable(false)
            .setView(layout)
            .create()

        dialog.show()

        com.ninmods.menu.data.PrefsHelper.resetFeatures()

        Thread {
            com.ninmods.menu.memory.RootHelper.requestRoot()
            com.ninmods.menu.ipc.DaemonManager.ensureDaemonRunning(applicationContext)
            val menuIntent = Intent(this, FloatingMenuService::class.java)
            val espIntent = Intent(this, EspOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(menuIntent)
                startForegroundService(espIntent)
            } else {
                startService(menuIntent)
                startService(espIntent)
            }

            Thread.sleep(800)

            runOnUiThread {
                if (targetPkg != null) {
                    launchPackage(targetPkg)
                }
                try {
                    dialog.dismiss()
                } catch (_: Exception) {}
                finish()
            }
        }.start()
    }

    private fun launchPackage(pkg: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            return
        }
        Thread {
            com.ninmods.menu.memory.RootHelper.execSu("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
        }.start()
    }

    private fun handleLogout() {
        AuthManager.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
