package com.example.ui.screens

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.AppInfo
import com.example.data.HomeScreenItem
import com.example.ui.components.WallpaperBackground
import com.example.utils.LauncherUtils
import com.example.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherAppScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Database states
    val homeItems by viewModel.homeScreenItems.collectAsStateWithLifecycle()
    val allApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()

    // Settings states
    val rows by viewModel.gridRows.collectAsStateWithLifecycle()
    val cols by viewModel.gridColumns.collectAsStateWithLifecycle()
    val iconSize by viewModel.iconSizeMultiplier.collectAsStateWithLifecycle()
    val animsEnabled by viewModel.animationsEnabled.collectAsStateWithLifecycle()
    val activeWallpaper by viewModel.wallpaperName.collectAsStateWithLifecycle()
    val activeIconPackPkg by viewModel.iconPackPackage.collectAsStateWithLifecycle()

    // View States
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val activePage by viewModel.activePage.collectAsStateWithLifecycle()
    val selectedItemForActions by viewModel.selectedItemForActions.collectAsStateWithLifecycle()

    // Search and Drawer control
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var isDrawerOpen by remember { mutableStateOf(false) }

    // Settings sheet control
    var isSettingsOpen by remember { mutableStateOf(false) }

    // Double tap screen-off simulated overlay
    var isSimulatedOff by remember { mutableStateOf(false) }

    // Selected item inside Edit Mode for in-context movements
    var editModeSelectedItem by remember { mutableStateOf<HomeScreenItem?>(null) }

    // Widget dialog picker control
    var isWidgetPickerOpen by remember { mutableStateOf(false) }

    // Setup HorizontalPager for home screens
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 }) // 3 screen pages supported

    // Sync active page in ViewModel with pager state
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setActivePage(pagerState.currentPage)
    }

    LaunchedEffect(activePage) {
        if (activePage != pagerState.currentPage && activePage in 0..2) {
            pagerState.animateScrollToPage(activePage)
        }
    }

    // Handles back presses cleanly
    BackHandler(enabled = isDrawerOpen || isSettingsOpen || isEditMode || isSimulatedOff) {
        when {
            isSimulatedOff -> isSimulatedOff = false
            isSettingsOpen -> isSettingsOpen = false
            isDrawerOpen -> {
                isDrawerOpen = false
                viewModel.updateSearchQuery("")
            }
            isEditMode -> {
                viewModel.setEditMode(false)
                editModeSelectedItem = null
            }
        }
    }

    // Widget Binding Launchers
    val bindWidgetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (widgetId != -1) {
                val provider = viewModel.appWidgetManager.getAppWidgetInfo(widgetId)
                if (provider != null) {
                    // Check if configuration is needed
                    if (provider.configure != null) {
                        val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                            component = provider.configure
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        }
                        // For simplicity, add directly if config needed or launch config.
                        // We add directly to avoid complex config flows failing in simulator
                        viewModel.addWidgetToHomeScreen(provider, activePage, 0, 0, 4, 2, widgetId)
                    } else {
                        viewModel.addWidgetToHomeScreen(provider, activePage, 0, 0, 4, 2, widgetId)
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Wallpaper background
        WallpaperBackground(wallpaperName = activeWallpaper)

        // 2. Home Screen Container with double tap to lock & swipe gesture detection
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            isSimulatedOff = true
                        },
                        onLongPress = {
                            viewModel.setEditMode(true)
                            Toast
                                .makeText(context, "Edit Mode Activated", Toast.LENGTH_SHORT)
                                .show()
                        }
                    )
                }
                .pointerInput(Unit) {
                    // Gesture: Swipe down to open notifications shade
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount.y > 45f && dragAmount.x in -30f..30f && !isDrawerOpen) {
                                LauncherUtils.expandNotificationShade(context)
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Built-in Niagara Minimal Clock Header
                HomeClockHeader()

                // Swipeable pages horizontal pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { pageIndex ->
                    HomeGridPage(
                        pageIndex = pageIndex,
                        rows = rows,
                        cols = cols,
                        iconSize = iconSize,
                        homeItems = homeItems.filter { it.page == pageIndex },
                        isEditMode = isEditMode,
                        editModeSelectedItem = editModeSelectedItem,
                        onItemClick = { item ->
                            if (isEditMode) {
                                editModeSelectedItem = if (editModeSelectedItem?.id == item.id) null else item
                            } else {
                                if (item.type == "app") {
                                    val matched = allApps.find { it.packageName == item.packageName }
                                    if (matched != null) {
                                        viewModel.launchApp(matched)
                                    } else {
                                        Toast.makeText(context, "App not installed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onItemLongClick = { item ->
                            if (!isEditMode) {
                                viewModel.selectItemForActions(item)
                            } else {
                                editModeSelectedItem = item
                            }
                        },
                        viewModel = viewModel
                    )
                }

                // Page indicators & Action footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Page indicator dots
                    repeat(3) { i ->
                        val active = pagerState.currentPage == i
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (active) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (active) Color.White else Color.White.copy(alpha = 0.4f))
                        )
                    }
                }

                // Drawer Open/Edit action floating controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left settings button
                    IconButton(
                        onClick = { isSettingsOpen = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                            .testTag("launcher_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }

                    // Centered App Drawer handle indicator
                    Card(
                        onClick = { isDrawerOpen = true },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .width(130.dp)
                            .height(44.dp)
                            .testTag("app_drawer_handle")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Drawer",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Apps",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Right Widget/Edit mode FAB
                    IconButton(
                        onClick = {
                            if (isEditMode) {
                                isWidgetPickerOpen = true
                            } else {
                                viewModel.setEditMode(true)
                                Toast.makeText(context, "Long press grid to customize", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isEditMode) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f))
                            .testTag("add_widget_fab")
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Add else Icons.Default.Edit,
                            contentDescription = if (isEditMode) "Add Widget" else "Edit Grid",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // 3. Edit Mode overlays (for moving/sizing selected items)
        if (isEditMode && editModeSelectedItem != null) {
            val selected = editModeSelectedItem!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F202C)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Customize: ${selected.label}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Position controllers (Rows/Columns mapping)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Move Item", color = Color.LightGray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row {
                                    IconButton(onClick = {
                                        if (selected.column > 0) viewModel.moveItem(selected.id, selected.page, selected.row, selected.column - 1)
                                    }) { Icon(Icons.Default.KeyboardArrowLeft, "Left", tint = Color.White) }
                                    Column {
                                        IconButton(onClick = {
                                            if (selected.row > 0) viewModel.moveItem(selected.id, selected.page, selected.row - 1, selected.column)
                                        }) { Icon(Icons.Default.KeyboardArrowUp, "Up", tint = Color.White) }
                                        IconButton(onClick = {
                                            if (selected.row < rows - 1) viewModel.moveItem(selected.id, selected.page, selected.row + 1, selected.column)
                                        }) { Icon(Icons.Default.KeyboardArrowDown, "Down", tint = Color.White) }
                                    }
                                    IconButton(onClick = {
                                        if (selected.column < cols - 1) viewModel.moveItem(selected.id, selected.page, selected.row, selected.column + 1)
                                    }) { Icon(Icons.Default.KeyboardArrowRight, "Right", tint = Color.White) }
                                }
                            }

                            // Widget Resize Controller
                            if (selected.type == "widget") {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Resize Widget", color = Color.LightGray, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            if (selected.spanX > 1) viewModel.resizeWidget(selected.id, selected.spanX - 1, selected.spanY)
                                        }) { Icon(Icons.Default.Remove, "Narrow", tint = Color.White) }
                                        Text("${selected.spanX}x${selected.spanY}", color = Color.White, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = {
                                            if (selected.spanX < cols) viewModel.resizeWidget(selected.id, selected.spanX + 1, selected.spanY)
                                        }) { Icon(Icons.Default.Add, "Widen", tint = Color.White) }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            if (selected.spanY > 1) viewModel.resizeWidget(selected.id, selected.spanX, selected.spanY - 1)
                                        }) { Icon(Icons.Default.ArrowDownward, "Shorter", tint = Color.White) }
                                        Text("Height", color = Color.LightGray, fontSize = 11.sp)
                                        IconButton(onClick = {
                                            if (selected.spanY < rows) viewModel.resizeWidget(selected.id, selected.spanX, selected.spanY + 1)
                                        }) { Icon(Icons.Default.ArrowUpward, "Taller", tint = Color.White) }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    viewModel.removeItem(selected)
                                    editModeSelectedItem = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, "Delete")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }

                            Button(
                                onClick = { editModeSelectedItem = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                            ) {
                                Text("Done", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // 4. Sliding App Drawer
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            AppDrawerScreen(
                searchQuery = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                filteredApps = filteredApps,
                onAppClick = { app ->
                    viewModel.launchApp(app)
                    isDrawerOpen = false
                    viewModel.updateSearchQuery("")
                },
                onAppLongPress = { app ->
                    // Transform AppInfo to simulated HomeScreenItem for dialog actions
                    val simulatedItem = HomeScreenItem(
                        id = -1,
                        page = activePage,
                        row = -1,
                        column = -1,
                        type = "app",
                        packageName = app.packageName,
                        className = app.className,
                        label = app.label
                    )
                    viewModel.selectItemForActions(simulatedItem)
                },
                onClose = {
                    isDrawerOpen = false
                    viewModel.updateSearchQuery("")
                }
            )
        }

        // 5. Sliding Settings Sheet
        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            SettingsScreen(
                rows = rows,
                cols = cols,
                iconSize = iconSize,
                animsEnabled = animsEnabled,
                activeWallpaper = activeWallpaper,
                onGridChange = { r, c -> viewModel.changeGridSize(r, c) },
                onIconSizeChange = { viewModel.changeIconSizeMultiplier(it) },
                onAnimsChange = { viewModel.changeAnimationsEnabled(it) },
                onWallpaperChange = { viewModel.selectWallpaper(it) },
                iconPacks = viewModel.getInstalledIconPacks(),
                activeIconPackPkg = activeIconPackPkg,
                onIconPackSelected = { viewModel.selectIconPack(it) },
                onClose = { isSettingsOpen = false }
            )
        }

        // 6. Simulated Always-On Display (AOD) on Double Tap Lock
        if (isSimulatedOff) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { isSimulatedOff = false },
                            onDoubleTap = { isSimulatedOff = false }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateFormat = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
                val timeStr = timeFormat.format(Date())
                val dateStr = dateFormat.format(Date())

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeStr,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraLight,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dateStr,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "Double-tap to wake screen",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 7. Context actions popover dialog (Open details, Uninstall, Pin/Unpin)
        if (selectedItemForActions != null) {
            val item = selectedItemForActions!!
            AlertDialog(
                onDismissRequest = { viewModel.selectItemForActions(null) },
                title = { Text(item.label, color = Color.White, fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFF1E1F28),
                textContentColor = Color.LightGray,
                text = {
                    Column {
                        Text("Package: ${item.packageName}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Choose launcher action below:")
                    }
                },
                confirmButton = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Action 1: Add to Home or Remove from Home
                        if (item.id == -1) {
                            Button(
                                onClick = {
                                    val matched = allApps.find { it.packageName == item.packageName }
                                    if (matched != null) {
                                        viewModel.addAppToHomeScreen(matched, activePage)
                                        Toast.makeText(context, "Added to Page ${activePage + 1}", Toast.LENGTH_SHORT).show()
                                    }
                                    viewModel.selectItemForActions(null)
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, "Add to Home")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add to Home Screen")
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.removeItem(item)
                                    viewModel.selectItemForActions(null)
                                    Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Remove")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Remove from Home Screen")
                            }
                        }

                        // Action 2: Open Info
                        OutlinedButton(
                            onClick = {
                                viewModel.openAppInfo(item.packageName)
                                viewModel.selectItemForActions(null)
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Icon(Icons.Default.Info, "App Info")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("System App Info")
                        }

                        // Action 3: Request Uninstall
                        OutlinedButton(
                            onClick = {
                                viewModel.requestUninstall(item.packageName)
                                viewModel.selectItemForActions(null)
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, "Uninstall")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uninstall App")
                        }

                        // Cancel
                        TextButton(
                            onClick = { viewModel.selectItemForActions(null) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                }
            )
        }

        // 8. Custom native widget picker sheet/dialog
        if (isWidgetPickerOpen) {
            val providers = remember { viewModel.appWidgetManager.installedProviders }
            AlertDialog(
                onDismissRequest = { isWidgetPickerOpen = false },
                title = { Text("Add Native Widget", color = Color.White, fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFF15161E),
                text = {
                    Box(modifier = Modifier.height(350.dp).fillMaxWidth()) {
                        if (providers.isEmpty()) {
                            Text("No native widgets found on this device.", color = Color.Gray, textAlign = TextAlign.Center)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(providers) { provider ->
                                    Card(
                                        onClick = {
                                            isWidgetPickerOpen = false
                                            val widgetId = viewModel.appWidgetHost.allocateAppWidgetId()
                                            // Request widget binding permission
                                            val bindAllowed = viewModel.appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider.provider)
                                            if (bindAllowed) {
                                                viewModel.addWidgetToHomeScreen(provider, activePage, 0, 0, 4, 2, widgetId)
                                            } else {
                                                // Trigger system bind permission popup
                                                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                                                }
                                                bindWidgetLauncher.launch(intent)
                                            }
                                        },
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Widgets, "Widget", tint = Color.White, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(provider.loadLabel(context.packageManager), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Text(provider.provider.packageName, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isWidgetPickerOpen = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }
    }
}

/**
 * Built-in beautiful, minimalist clock widget inspired by Niagara.
 */
@Composable
fun HomeClockHeader() {
    var timeStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
            dateStr = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault()).format(now)
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = timeStr,
            color = Color.White,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp
        )
        Text(
            text = dateStr,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Custom 2D Grid Layout mapping home page icons and native widgets.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeGridPage(
    pageIndex: Int,
    rows: Int,
    cols: Int,
    iconSize: Float,
    homeItems: List<HomeScreenItem>,
    isEditMode: Boolean,
    editModeSelectedItem: HomeScreenItem?,
    onItemClick: (HomeScreenItem) -> Unit,
    onItemLongClick: (HomeScreenItem) -> Unit,
    viewModel: LauncherViewModel
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows

        // Visual guidelines helper for Edit Mode
        if (isEditMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                // Draw columns
                for (c in 1 until cols) {
                    val x = c * cellW.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.15f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = pathEffect
                    )
                }
                // Draw rows
                for (r in 1 until rows) {
                    val y = r * cellH.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.15f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = pathEffect
                    )
                }
            }
        }

        // Draw mapped icons & widgets
        for (item in homeItems) {
            val isSelected = editModeSelectedItem?.id == item.id
            val posX = cellW * item.column
            val posY = cellH * item.row
            val width = cellW * item.spanX
            val height = cellH * item.spanY

            Box(
                modifier = Modifier
                    .offset(x = posX, y = posY)
                    .size(width = width, height = height)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isSelected) 2.dp else if (isEditMode) 1.dp else 0.dp,
                        color = if (isSelected) Color.White else if (isEditMode) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.12f)
                        else if (isEditMode) Color.White.copy(alpha = 0.04f)
                        else Color.Transparent
                    )
                    .combinedClickable(
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) }
                    )
            ) {
                if (item.type == "widget" && item.widgetId != null) {
                    WidgetContainer(item = item, viewModel = viewModel)
                } else {
                    AppIconCell(item = item, iconSize = iconSize)
                }
            }
        }

        // Visual tip for blank page inside edit mode
        if (homeItems.isEmpty() && isEditMode) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Page ${pageIndex + 1} is empty.\nSwipe up for apps & hold to pin.",
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Hosts an Android native AppWidgetHostView in Jetpack Compose.
 */
@Composable
fun WidgetContainer(
    item: HomeScreenItem,
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    val providerInfo = remember(item.widgetId) {
        viewModel.appWidgetManager.getAppWidgetInfo(item.widgetId!!)
    }

    if (providerInfo != null) {
        AndroidView(
            factory = { ctx ->
                viewModel.appWidgetHost.createView(ctx, item.widgetId!!, providerInfo).apply {
                    // Fit within widget boundaries nicely
                    setPadding(0, 0, 0, 0)
                }
            },
            update = { /* Updates handled by host */ },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Red.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, "Unavailable", tint = Color.Red, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Widget Unbound", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Modern icon & label layout with custom sizing.
 */
@Composable
fun AppIconCell(
    item: HomeScreenItem,
    iconSize: Float
) {
    val pm = LocalContext.current.packageManager
    val iconPainter = remember(item.packageName, item.className) {
        try {
            val componentName = ComponentName(item.packageName, item.className)
            val iconDrawable = pm.getActivityIcon(componentName)
            BitmapDrawable(pm.getResourcesForApplication(item.packageName), drawableToBitmap(iconDrawable))
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (iconPainter != null) {
            Image(
                bitmap = iconPainter.bitmap.asImageBitmap(),
                contentDescription = item.label,
                modifier = Modifier
                    .size((46 * iconSize).dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            // Placeholder circular index icon
            Box(
                modifier = Modifier
                    .size((46 * iconSize).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.label.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Slide-up fully indexed app search drawer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    filteredApps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E15).copy(alpha = 0.96f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search field & Close action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search all installed apps...", color = Color.Gray) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("app_search_field")
                )
            }

            // Interactive scrollable list of matching applications
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching applications found", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    items(filteredApps) { app ->
                        Column(
                            modifier = Modifier
                                .padding(vertical = 12.dp, horizontal = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = { onAppClick(app) },
                                    onLongClick = { onAppLongPress(app) }
                                )
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = drawableToBitmap(app.icon).asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = app.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Slide-up full settings sheet.
 */
@Composable
fun SettingsScreen(
    rows: Int,
    cols: Int,
    iconSize: Float,
    animsEnabled: Boolean,
    activeWallpaper: String,
    onGridChange: (Int, Int) -> Unit,
    onIconSizeChange: (Float) -> Unit,
    onAnimsChange: (Boolean) -> Unit,
    onWallpaperChange: (String) -> Unit,
    iconPacks: List<com.example.utils.IconPackInfo>,
    activeIconPackPkg: String?,
    onIconPackSelected: (String?) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14151F).copy(alpha = 0.98f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Launcher Settings",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Section: Grid Layout Configuration
            SettingsSectionHeader(title = "Grid Layout Size")
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Columns (${cols})", color = Color.White, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (cols > 3) onGridChange(rows, cols - 1) }) {
                                Icon(Icons.Default.Remove, "Less", tint = Color.White)
                            }
                            IconButton(onClick = { if (cols < 6) onGridChange(rows, cols + 1) }) {
                                Icon(Icons.Default.Add, "More", tint = Color.White)
                            }
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.08f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rows (${rows})", color = Color.White, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (rows > 4) onGridChange(rows - 1, cols) }) {
                                Icon(Icons.Default.Remove, "Less", tint = Color.White)
                            }
                            IconButton(onClick = { if (rows < 7) onGridChange(rows + 1, cols) }) {
                                Icon(Icons.Default.Add, "More", tint = Color.White)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Section: Icon Sizing multiplier slider
            SettingsSectionHeader(title = "Icon Scaling Size")
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Multiplier", color = Color.White, fontSize = 14.sp)
                        Text("${(iconSize * 100).roundToInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Slider(
                        value = iconSize,
                        onValueChange = onIconSizeChange,
                        valueRange = 0.8f..1.4f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Section: Animated Transitions
            SettingsSectionHeader(title = "System Animations")
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Drawer Slide Transitions", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = animsEnabled,
                        onCheckedChange = onAnimsChange
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Section: Wallpaper Selector Horizontal cards list
            SettingsSectionHeader(title = "Launcher Background Wallpapers")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    "cosmic_dark" to "Cosmic Dark",
                    "pastel_sunset" to "Pastel Sunset",
                    "emerald_forest" to "Emerald Forest",
                    "neon_night" to "Neon Cyberpunk"
                ).forEach { (key, label) ->
                    val isSelected = activeWallpaper == key
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onWallpaperChange(key) }
                    ) {
                        WallpaperBackground(wallpaperName = key, modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, "Active", tint = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Box(modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Section: Third-party Icon Packs
            SettingsSectionHeader(title = "Apply Icon Packs")
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Customize system icons by selecting an icon pack installed on this device.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Default system pack option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIconPackSelected(null) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Android, "System", tint = Color.White)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("System Default Icons", color = Color.White, fontSize = 14.sp)
                        }
                        if (activeIconPackPkg == null) {
                            Icon(Icons.Default.Check, "Selected", tint = Color.Green)
                        }
                    }

                    if (iconPacks.isEmpty()) {
                        Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "No compatible icon packs found. Install one from the Google Play Store to apply custom designs.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    } else {
                        iconPacks.forEach { pack ->
                            Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onIconPackSelected(pack.packageName) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        bitmap = drawableToBitmap(pack.icon).asImageBitmap(),
                                        contentDescription = pack.label,
                                        modifier = Modifier.size(28.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(pack.label, color = Color.White, fontSize = 14.sp)
                                }
                                if (activeIconPackPkg == pack.packageName) {
                                    Icon(Icons.Default.Check, "Selected", tint = Color.Green)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = Color.LightGray,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * Utility: Converts a Drawable safely to a Bitmap.
 */
fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        if (drawable.bitmap != null) {
            return drawable.bitmap
        }
    }

    val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) // Single pixel fallback
    } else {
        Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    }

    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

// Deleted state helpers to avoid compile issues
