package com.example.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val className: String,
    val label: String,
    val icon: Drawable,
    val isSystemApp: Boolean = false
) {
    val componentName: String
        get() = "$packageName/$className"
}
