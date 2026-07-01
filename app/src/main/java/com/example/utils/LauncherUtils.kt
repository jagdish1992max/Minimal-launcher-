package com.example.utils

import android.content.Context
import android.content.Intent
import android.util.Log

object LauncherUtils {

    /**
     * Programmatically expands the Android notification shade using reflection.
     */
    fun expandNotificationShade(context: Context) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManagerClass.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
        } catch (e: Exception) {
            Log.e("LauncherUtils", "Failed to programmatically expand notifications shade", e)
            // Fallback to opening system notification settings or showing user instructions
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e("LauncherUtils", "Failed to launch notifications history settings fallback", e2)
            }
        }
    }
}
