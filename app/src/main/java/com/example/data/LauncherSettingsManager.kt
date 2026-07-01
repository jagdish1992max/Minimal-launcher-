package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

class LauncherSettingsManager(private val context: Context) {

    companion object {
        val GRID_ROWS = intPreferencesKey("grid_rows")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val ICON_SIZE_MULTIPLIER = floatPreferencesKey("icon_size_multiplier")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val WALLPAPER_NAME = stringPreferencesKey("wallpaper_name")
        val ICON_PACK_PACKAGE = stringPreferencesKey("icon_pack_package")
    }

    val gridRows: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[GRID_ROWS] ?: 5
    }

    val gridColumns: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[GRID_COLUMNS] ?: 4
    }

    val iconSizeMultiplier: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[ICON_SIZE_MULTIPLIER] ?: 1.0f
    }

    val animationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ANIMATIONS_ENABLED] ?: true
    }

    val wallpaperName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WALLPAPER_NAME] ?: "cosmic_dark"
    }

    val iconPackPackage: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ICON_PACK_PACKAGE]
    }

    suspend fun setGridSize(rows: Int, columns: Int) {
        context.dataStore.edit { preferences ->
            preferences[GRID_ROWS] = rows
            preferences[GRID_COLUMNS] = columns
        }
    }

    suspend fun setIconSizeMultiplier(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[ICON_SIZE_MULTIPLIER] = size
        }
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ANIMATIONS_ENABLED] = enabled
        }
    }

    suspend fun setWallpaperName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[WALLPAPER_NAME] = name
        }
    }

    suspend fun setIconPackPackage(packageName: String?) {
        context.dataStore.edit { preferences ->
            if (packageName == null) {
                preferences.remove(ICON_PACK_PACKAGE)
            } else {
                preferences[ICON_PACK_PACKAGE] = packageName
            }
        }
    }
}
