package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_screen_items")
data class HomeScreenItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val page: Int,
    val row: Int,
    val column: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val type: String, // "app" or "widget"
    val packageName: String,
    val className: String,
    val label: String,
    val widgetId: Int? = null
)
