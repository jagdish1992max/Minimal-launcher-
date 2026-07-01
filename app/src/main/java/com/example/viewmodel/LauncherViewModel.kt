package com.example.viewmodel

import android.app.Application
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val pm = context.packageManager
    private val db = AppDatabase.getInstance(context)
    private val repo = HomeScreenRepository(db.homeScreenDao())
    val settingsManager = LauncherSettingsManager(context)
    private val iconPackManager = IconPackManager(context)

    // Standard Widget Host ID
    private val APPWIDGET_HOST_ID = 1024
    val appWidgetHost = AppWidgetHost(context, APPWIDGET_HOST_ID)
    val appWidgetManager = AppWidgetManager.getInstance(context)

    // State flows from settings
    val gridRows = settingsManager.gridRows.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)
    val gridColumns = settingsManager.gridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)
    val iconSizeMultiplier = settingsManager.iconSizeMultiplier.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val animationsEnabled = settingsManager.animationsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val wallpaperName = settingsManager.wallpaperName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "cosmic_dark")
    val iconPackPackage = settingsManager.iconPackPackage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Home screen items from Room database
    val homeScreenItems = repo.allItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All installed launchable apps
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    // Filtered apps in the app drawer search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredApps = combine(_installedApps, _searchQuery) { apps, query ->
        if (query.isBlank()) {
            apps.sortedBy { it.label.lowercase() }
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
                .sortedBy { it.label.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI States
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _activePage = MutableStateFlow(0)
    val activePage: StateFlow<Int> = _activePage.asStateFlow()

    private val _selectedItemForActions = MutableStateFlow<HomeScreenItem?>(null)
    val selectedItemForActions: StateFlow<HomeScreenItem?> = _selectedItemForActions.asStateFlow()

    // Icon Pack Mapping
    private var iconPackMapping: Map<String, String> = emptyMap()

    init {
        // App widget host setup
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Failed to start listening to AppWidgetHost", e)
        }

        // Load apps and listen for changes in chosen icon pack
        viewModelScope.launch {
            iconPackPackage.collect { activePack ->
                loadIconPackAndApps(activePack)
            }
        }
    }

    fun startWidgetHost() {
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Error starting app widget host listener", e)
        }
    }

    fun stopWidgetHost() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Error stopping app widget host listener", e)
        }
    }

    /**
     * Loads the active icon pack configurations and lists all launcher applications.
     */
    private suspend fun loadIconPackAndApps(iconPackPkg: String?) {
        withContext(Dispatchers.IO) {
            iconPackMapping = if (!iconPackPkg.isNullOrEmpty()) {
                iconPackManager.loadIconPackMapping(iconPackPkg)
            } else {
                emptyMap()
            }

            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val appsList = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                // Skip our own launcher from showing inside the App Drawer
                if (packageName == context.packageName) return@mapNotNull null

                val className = resolveInfo.activityInfo.name
                val label = resolveInfo.loadLabel(pm).toString()
                
                // Load icon (apply icon pack if available and contains mapping, otherwise use default)
                var icon: Drawable? = null
                if (!iconPackPkg.isNullOrEmpty()) {
                    icon = iconPackManager.loadIcon(iconPackPkg, iconPackMapping, packageName, className)
                }
                if (icon == null) {
                    icon = resolveInfo.loadIcon(pm)
                }

                val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

                AppInfo(
                    packageName = packageName,
                    className = className,
                    label = label,
                    icon = icon,
                    isSystemApp = isSystem
                )
            }

            _installedApps.value = appsList
        }
    }

    // Search query update
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // App actions state
    fun selectItemForActions(item: HomeScreenItem?) {
        _selectedItemForActions.value = item
    }

    // UI Navigation & Edit Mode Toggle
    fun setEditMode(enabled: Boolean) {
        _isEditMode.value = enabled
    }

    fun setActivePage(page: Int) {
        _activePage.value = page
    }

    /**
     * Adds an application to the home screen. Finds the first available spot.
     */
    fun addAppToHomeScreen(app: AppInfo, page: Int = _activePage.value) {
        viewModelScope.launch {
            val itemsOnPage = homeScreenItems.value.filter { it.page == page }
            val gridC = gridColumns.value
            val gridR = gridRows.value

            // Simple search for empty space
            var foundSpot = false
            var foundRow = 0
            var foundCol = 0

            val occupied = Array(gridR) { BooleanArray(gridC) }
            for (item in itemsOnPage) {
                for (r in item.row until minOf(gridR, item.row + item.spanY)) {
                    for (c in item.column until minOf(gridC, item.column + item.spanX)) {
                        occupied[r][c] = true
                    }
                }
            }

            for (r in 0 until gridR) {
                for (c in 0 until gridC) {
                    if (!occupied[r][c]) {
                        foundRow = r
                        foundCol = c
                        foundSpot = true
                        break
                    }
                }
                if (foundSpot) break
            }

            if (foundSpot) {
                val newItem = HomeScreenItem(
                    page = page,
                    row = foundRow,
                    column = foundCol,
                    type = "app",
                    packageName = app.packageName,
                    className = app.className,
                    label = app.label
                )
                repo.insertItem(newItem)
            } else {
                // If current page is full, try next page or add new page
                val maxPage = homeScreenItems.value.maxOfOrNull { it.page } ?: 0
                val nextPage = if (page == maxPage) page + 1 else maxPage
                val newItem = HomeScreenItem(
                    page = nextPage,
                    row = 0,
                    column = 0,
                    type = "app",
                    packageName = app.packageName,
                    className = app.className,
                    label = app.label
                )
                repo.insertItem(newItem)
                setActivePage(nextPage)
            }
        }
    }

    /**
     * Move home screen item positions.
     */
    fun moveItem(id: Int, page: Int, row: Int, column: Int) {
        viewModelScope.launch {
            repo.updateItemPosition(id, page, row, column)
        }
    }

    /**
     * Resizes a widget.
     */
    fun resizeWidget(id: Int, spanX: Int, spanY: Int) {
        viewModelScope.launch {
            repo.updateItemSize(id, spanX, spanY)
        }
    }

    /**
     * Removes an item from the database. Deletes associated widgets from host.
     */
    fun removeItem(item: HomeScreenItem) {
        viewModelScope.launch {
            repo.deleteItem(item)
            if (item.type == "widget" && item.widgetId != null) {
                try {
                    appWidgetHost.deleteAppWidgetId(item.widgetId)
                } catch (e: Exception) {
                    Log.e("LauncherViewModel", "Failed to delete app widget ID", e)
                }
            }
        }
    }

    /**
     * Add native widget.
     */
    fun addWidgetToHomeScreen(provider: AppWidgetProviderInfo, page: Int, row: Int, col: Int, spanX: Int, spanY: Int, widgetId: Int) {
        viewModelScope.launch {
            val label = provider.loadLabel(pm)
            val newItem = HomeScreenItem(
                page = page,
                row = row,
                column = col,
                spanX = spanX,
                spanY = spanY,
                type = "widget",
                packageName = provider.provider.packageName,
                className = provider.provider.className,
                label = label,
                widgetId = widgetId
            )
            repo.insertItem(newItem)
        }
    }

    /**
     * Fetch all installed icon pack applications.
     */
    fun getInstalledIconPacks(): List<IconPackInfo> {
        return iconPackManager.getInstalledIconPacks()
    }

    /**
     * Settings operations.
     */
    fun changeGridSize(rows: Int, columns: Int) {
        viewModelScope.launch {
            settingsManager.setGridSize(rows, columns)
        }
    }

    fun changeIconSizeMultiplier(multiplier: Float) {
        viewModelScope.launch {
            settingsManager.setIconSizeMultiplier(multiplier)
        }
    }

    fun changeAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAnimationsEnabled(enabled)
        }
    }

    fun selectWallpaper(name: String) {
        viewModelScope.launch {
            settingsManager.setWallpaperName(name)
        }
    }

    fun selectIconPack(packageName: String?) {
        viewModelScope.launch {
            settingsManager.setIconPackPackage(packageName)
        }
    }

    /**
     * Standard device action wrappers.
     */
    fun launchApp(info: AppInfo) {
        try {
            val intent = pm.getLaunchIntentForPackage(info.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Failed to launch package: ${info.packageName}", e)
        }
    }

    fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Failed to open app info for package: $packageName", e)
        }
    }

    fun requestUninstall(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Failed to uninstall package: $packageName", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Error in onCleared stopping listening to AppWidgetHost", e)
        }
    }
}
