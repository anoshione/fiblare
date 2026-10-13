package com.player1.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.player1.app.model.ThemePreset
import com.player1.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonCustomizationScreen(
    viewModel: MainViewModel,
    onNavigateToThemePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appSettings by viewModel.appSettings.collectAsState()
    val libraryTab by viewModel.libraryTab.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Customize") }) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 88.dp) // clear floating pill nav
        ) {
            // ── APPEARANCE ──
            Text(
                "Appearance",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Theme Preset Circle + Tile
            val activeColor = remember(appSettings.themePreset, appSettings.customSeedColor) {
                if (appSettings.themePreset == ThemePreset.FOLLOW_SYSTEM) {
                    null
                } else if (appSettings.themePreset == ThemePreset.CUSTOM) {
                    appSettings.customSeedColor?.let { Color(it) }
                } else {
                    appSettings.themePreset.seedColor?.let { Color(it) }
                }
            } ?: MaterialTheme.colorScheme.primary

            ListItem(
                headlineContent = { Text("Color theme") },
                supportingContent = { Text(appSettings.themePreset.displayName) },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(activeColor)
                    )
                },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { onNavigateToThemePicker() }
            )

            // AMOLED Black Switch
            ListItem(
                headlineContent = { Text("AMOLED black") },
                supportingContent = { Text("True black in dark mode") },
                leadingContent = { Icon(Icons.Default.Brightness1, null) },
                trailingContent = {
                    Switch(
                        checked = appSettings.amoledBlack,
                        onCheckedChange = { value ->
                            viewModel.updateAppSettings { it.copy(amoledBlack = value) }
                        }
                    )
                },
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable {
                    viewModel.updateAppSettings { it.copy(amoledBlack = !appSettings.amoledBlack) }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // ── LIBRARY PREFERENCES ──
            Text(
                "Library View Mode",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Tab Selector: All Videos vs Folders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = libraryTab == LibraryTab.ALL_VIDEOS,
                        onClick = { viewModel.setLibraryTab(LibraryTab.ALL_VIDEOS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("All Videos")
                    }
                    SegmentedButton(
                        selected = libraryTab == LibraryTab.FOLDERS,
                        onClick = { viewModel.setLibraryTab(LibraryTab.FOLDERS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Folders")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // View Mode Selector: Grid vs List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = viewMode == LibraryViewMode.GRID,
                        onClick = { viewModel.setViewMode(LibraryViewMode.GRID) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Default.GridView, null) }
                    ) {
                        Text("Grid")
                    }
                    SegmentedButton(
                        selected = viewMode == LibraryViewMode.LIST,
                        onClick = { viewModel.setViewMode(LibraryViewMode.LIST) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Default.ViewList, null) }
                    ) {
                        Text("List")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sort Options
            Text(
                "Sort Library By",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val sortOptions = listOf(
                    LibrarySortMode.DATE to "Date",
                    LibrarySortMode.NAME to "Name",
                    LibrarySortMode.SIZE to "Size",
                    LibrarySortMode.TYPE to "Type"
                )

                sortOptions.forEach { (mode, label) ->
                    val isActive = sortMode == mode
                    FilterChip(
                        selected = isActive,
                        onClick = {
                            if (isActive) {
                                // Toggle direction
                                val newDir = if (sortDirection == SortDirection.DESCENDING) SortDirection.ASCENDING else SortDirection.DESCENDING
                                viewModel.setSortDirection(newDir)
                            } else {
                                viewModel.setSortMode(mode)
                                viewModel.setSortDirection(SortDirection.DESCENDING) // default
                            }
                        },
                        label = { Text(label) },
                        trailingIcon = {
                            if (isActive) {
                                Icon(
                                    imageVector = if (sortDirection == SortDirection.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hidden Files Switch
            ListItem(
                headlineContent = { Text("Show hidden files") },
                supportingContent = { Text("Display files starting with a dot") },
                leadingContent = { Icon(Icons.Default.Visibility, null) },
                trailingContent = {
                    Switch(
                        checked = showHidden,
                        onCheckedChange = { viewModel.setShowHidden(it) }
                    )
                },
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { viewModel.setShowHidden(!showHidden) }
            )
        }
    }
}
