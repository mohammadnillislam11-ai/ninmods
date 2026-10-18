package com.ninmods.menu

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ninmods.menu.data.PrefsHelper

class NinApplication : Application() {
    companion object {
        const val CHANNEL_ID = "ninmods_fg"
    }

    override fun onCreate() {
        super.onCreate()
        PrefsHelper.init(this)
        PrefsHelper.loadAll()
        com.ninmods.menu.logging.TelegramLogger.init(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            com.ninmods.menu.logging.TelegramLogger.logError(
                "CRASH_HANDLER",
                "Uncaught Exception in thread ${thread.name}: ${throwable.message}",
                throwable
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
