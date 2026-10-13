package com.player1.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.player1.app.model.ThemeModeOption
import com.player1.app.model.ThemePreset
import com.player1.app.ui.theme.getSchemeForSeed
import com.player1.app.viewmodel.MainViewModel

import com.player1.app.ui.components.FrostedGlassAlertDialog
import com.player1.app.ui.components.LocalHazeState
import com.player1.app.ui.components.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val hazeState = LocalHazeState.current
    val appSettings by viewModel.appSettings.collectAsState()
    val customColor = appSettings.customSeedColor?.let { Color(it) }

    var showColorPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Appearance") }) },
        modifier = modifier.hazeSource(hazeState)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Theme Mode Segmented Button
            Text(
                "Theme mode",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = appSettings.themeMode == ThemeModeOption.DARK,
                        onClick = { viewModel.updateAppSettings { it.copy(themeMode = ThemeModeOption.DARK) } },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = { Icon(Icons.Default.DarkMode, null) }
                    ) {
                        Text("Dark")
                    }
                    SegmentedButton(
                        selected = appSettings.themeMode == ThemeModeOption.SYSTEM,
                        onClick = { viewModel.updateAppSettings { it.copy(themeMode = ThemeModeOption.SYSTEM) } },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = { Icon(Icons.Default.BrightnessAuto, null) }
                    ) {
                        Text("System")
                    }
                    SegmentedButton(
                        selected = appSettings.themeMode == ThemeModeOption.LIGHT,
                        onClick = { viewModel.updateAppSettings { it.copy(themeMode = ThemeModeOption.LIGHT) } },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = { Icon(Icons.Default.LightMode, null) }
                    ) {
                        Text("Light")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AMOLED Black Switch
            SwitchListItem(
                title = "AMOLED black",
                subtitle = "True black background in dark mode",
                checked = appSettings.amoledBlack,
                icon = Icons.Default.Brightness1,
                onCheckedChange = { value ->
                    viewModel.updateAppSettings { it.copy(amoledBlack = value) }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Color Palette Header
            Text(
                "Color palette",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Swatch Grid (Custom is handled below, so we exclude it here)
            val presets = ThemePreset.values().filter { it != ThemePreset.CUSTOM }
            
            // Custom simplified Grid using Columns & Rows to handle Scrollability nicely in verticalScroll
            val chunkedPresets = presets.chunked(4)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                chunkedPresets.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { preset ->
                            val isSelected = appSettings.themePreset == preset
                            SwatchCard(
                                preset = preset,
                                isSelected = isSelected,
                                onClick = {
                                    viewModel.updateAppSettings { it.copy(themePreset = preset) }
                                }
                            )
                        }
                        // Pad the last row if it's not full
                        if (row.size < 4) {
                            repeat(4 - row.size) {
                                Spacer(modifier = Modifier.size(56.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom Color Picker Tile
            val isCustomSelected = appSettings.themePreset == ThemePreset.CUSTOM
            ListItem(
                headlineContent = { Text("Custom color") },
                supportingContent = {
                    Text(
                        customColor?.let {
                            val argb = it.toArgb()
                            String.format("#%06X", 0xFFFFFF and argb)
                        } ?: "Tap to pick a color"
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(customColor ?: MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (isCustomSelected) 2.5.dp else 1.5.dp,
                                color = if (isCustomSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isCustomSelected || customColor == null) {
                            Icon(
                                Icons.Default.Add,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                trailingContent = {
                    if (isCustomSelected) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { showColorPicker = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Preview Section
            Text(
                "Preview",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            ThemePreviewCard(colorScheme = MaterialTheme.colorScheme)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showColorPicker) {
        val initialColor = customColor ?: Color(0xFF6750A4)
        ColorPickerDialog(
            initialColor = initialColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                viewModel.updateAppSettings {
                    it.copy(
                        themePreset = ThemePreset.CUSTOM,
                        customSeedColor = color.toArgb().toLong()
                    )
                }
                showColorPicker = false
            }
        )
    }
}

@Composable
fun SwatchCard(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val displayColor = when (preset) {
        ThemePreset.FOLLOW_SYSTEM -> Color.Transparent // Gradient
        else -> preset.seedColor?.let { Color(it) } ?: Color.Transparent
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 3.dp else 1.5.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        ) {
            if (preset == ThemePreset.FOLLOW_SYSTEM) {
                // Rainbow gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color.Red, Color.Yellow, Color.Green,
                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(displayColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            preset.displayName,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ThemePreviewCard(colorScheme: ColorScheme) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colorScheme.outline.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Fake App Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Player1",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.Settings,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fake Video Card
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colorScheme.onSurface.copy(alpha = 0.8f))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colorScheme.onSurface.copy(alpha = 0.3f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fake Chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "MP4", "MKV").forEach { label ->
                    val isFirst = label == "All"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFirst) colorScheme.primaryContainer else colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isFirst) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fake seek bar
            LinearProgressIndicator(
                progress = 0.38f,
                color = colorScheme.primary,
                trackColor = colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    // Custom Hue, Saturation, Lightness calculations
    var hue by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(1f) }
    var lightness by remember { mutableStateOf(0.5f) }

    LaunchedEffect(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        lightness = 0.5f // Simplify saturation / lightness mapping
    }

    val finalColor = remember(hue, saturation, lightness) {
        val hsv = floatArrayOf(hue, saturation, lightness)
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    FrostedGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Color Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(finalColor)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Hue Slider
                Text("Hue", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Red, Color.Yellow, Color.Green,
                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                )
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Saturation Slider
                Text("Saturation", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Lightness / Value Slider
                Text("Lightness", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = lightness,
                    onValueChange = { lightness = it },
                    valueRange = 0f..1f
                )

                Spacer(modifier = Modifier.height(8.dp))
                val hexStr = String.format("#%06X", 0xFFFFFF and finalColor.toArgb())
                Text(
                    hexStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(finalColor) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
