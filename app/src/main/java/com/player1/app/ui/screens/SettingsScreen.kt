package com.player1.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.player1.app.model.ResumeMode
import com.player1.app.model.SubtitlePosition
import com.player1.app.ui.components.FrostedGlassDropdownMenu
import com.player1.app.ui.components.DropdownMenuItem
import com.player1.app.viewmodel.MainViewModel

data class ButtonInfo(val key: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val playerSettings by viewModel.playerSettings.collectAsState()
    val subtitleSettings by viewModel.subtitleSettings.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()

    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val globalSearchActive by viewModel.settingsSearchActive.collectAsState()
    LaunchedEffect(globalSearchActive) {
        if (globalSearchActive) {
            isSearching = true
            viewModel.settingsSearchActive.value = false
        }
    }

    val allButtons = remember {
        listOf(
            ButtonInfo("prev", "Previous video", Icons.Default.SkipPrevious),
            ButtonInfo("next", "Next video", Icons.Default.SkipNext),
            ButtonInfo("rotation_lock", "Rotation lock", Icons.Default.ScreenLockRotation)
        )
    }

    var orderedKeys by remember {
        val saved = playerSettings.visibleNavButtons
        val list = saved.toMutableList()
        allButtons.forEach { btn ->
            if (!list.contains(btn.key)) {
                list.add(btn.key)
            }
        }
        mutableStateOf(list.toList())
    }

    val enabledButtons = playerSettings.visibleNavButtons

    fun saveButtons(newEnabled: List<String>) {
        viewModel.updatePlayerSettings { it.copy(visibleNavButtons = newEnabled) }
    }

    fun match(vararg keywords: String): Boolean {
        if (searchQuery.isBlank()) return true
        return keywords.any { it.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            if (isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search settings...", fontSize = 16.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Settings") },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search settings")
                        }
                    }
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 88.dp) // clear floating pill nav
        ) {
            // ── PLAYBACK ──
            val playbackMatch = match("seek forward", "seek backward", "default speed", "resume playback", "playback", "speed", "resume", "duration")
            if (playbackMatch) {
                if (searchQuery.isBlank()) {
                    SectionHeader(title = "Playback", icon = Icons.Default.PlayCircle)
                }

                // Seek Forward duration selector chips
                if (match("seek forward", "seek", "forward", "duration", "playback")) {
                    SeekDurationTile(
                        title = "Seek forward",
                        currentValue = playerSettings.seekForwardSec,
                        onValueChange = { valSec -> viewModel.updatePlayerSettings { it.copy(seekForwardSec = valSec) } }
                    )
                }

                // Seek Backward duration selector chips
                if (match("seek backward", "seek", "backward", "duration", "playback")) {
                    SeekDurationTile(
                        title = "Seek backward",
                        currentValue = playerSettings.seekBackSec,
                        onValueChange = { valSec -> viewModel.updatePlayerSettings { it.copy(seekBackSec = valSec) } }
                    )
                }

                // Default playback speed dropdown
                if (match("default speed", "speed", "playback", "rate", "fast", "slow")) {
                    var speedDropdownExpanded by remember { mutableStateOf(false) }
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    ListItem(
                        headlineContent = { Text("Default speed") },
                        supportingContent = { Text("${playerSettings.defaultSpeed}x") },
                        leadingContent = { Icon(Icons.Default.Speed, null) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { speedDropdownExpanded = true }) {
                                    Text("Select")
                                }
                                FrostedGlassDropdownMenu(
                                    expanded = speedDropdownExpanded,
                                    onDismissRequest = { speedDropdownExpanded = false }
                                ) {
                                    speeds.forEach { speed ->
                                        DropdownMenuItem(
                                            text = { Text("${speed}x") },
                                            onClick = {
                                                viewModel.updatePlayerSettings { it.copy(defaultSpeed = speed) }
                                                speedDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                // Resume playback dropdown
                if (match("resume playback", "resume", "playback", "position", "start over", "ask", "always ask")) {
                    var resumeDropdownExpanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("Resume playback") },
                        supportingContent = { Text(playerSettings.resumeMode.displayName) },
                        leadingContent = { Icon(Icons.Default.Restore, null) },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { resumeDropdownExpanded = true }) {
                                    Text("Select")
                                }
                                FrostedGlassDropdownMenu(
                                    expanded = resumeDropdownExpanded,
                                    onDismissRequest = { resumeDropdownExpanded = false }
                                ) {
                                    ResumeMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.displayName) },
                                            onClick = {
                                                viewModel.updatePlayerSettings { it.copy(resumeMode = mode) }
                                                resumeDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                // Back to list option
                if (match("back to list", "play", "list", "playlist", "next", "after", "end", "playback")) {
                    ListItem(
                        headlineContent = { Text("Back to list") },
                        supportingContent = { Text("Go back to the videos list after the current video ends instead of playing the next one") },
                        leadingContent = { Icon(Icons.Default.List, null) },
                        trailingContent = {
                            Switch(
                                checked = appSettings.backToList,
                                onCheckedChange = { isChecked ->
                                    viewModel.updateAppSettings { it.copy(backToList = isChecked) }
                                }
                            )
                        }
                    )
                }
            }

            if (playbackMatch && searchQuery.isBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // ── SUBTITLES ──
            val subtitleMatch = match("default sync offset", "font size", "position", "subtitles", "sync", "offset", "font", "size", "top", "bottom", "text")
            if (subtitleMatch) {
                if (searchQuery.isBlank()) {
                    SectionHeader(title = "Subtitles", icon = Icons.Default.Subtitles)
                }


                // Font size slider
                if (match("font size", "font", "size", "subtitles", "text")) {
                    ListItem(
                        headlineContent = { Text("Font size") },
                        supportingContent = {
                            Column {
                                Slider(
                                    value = subtitleSettings.fontSizeSp,
                                    onValueChange = {
                                        viewModel.updateSubtitleSettings { settings -> settings.copy(fontSizeSp = it) }
                                    },
                                    valueRange = 12f..48f,
                                    steps = 36
                                )
                            }
                        },
                        leadingContent = { Icon(Icons.Default.FormatSize, null) },
                        trailingContent = { Text("${subtitleSettings.fontSizeSp.toInt()}pt") },
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }

                // Position segmented buttons (Top / Bottom)
                if (match("position", "subtitles", "top", "bottom", "alignment")) {
                    ListItem(
                        headlineContent = { Text("Position") },
                        leadingContent = { Icon(Icons.Default.VerticalAlignBottom, null) },
                        trailingContent = {
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = subtitleSettings.position == SubtitlePosition.TOP,
                                    onClick = {
                                        viewModel.updateSubtitleSettings { it.copy(position = SubtitlePosition.TOP) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) {
                                    Text("Top")
                                }
                                SegmentedButton(
                                    selected = subtitleSettings.position == SubtitlePosition.BOTTOM,
                                    onClick = {
                                        viewModel.updateSubtitleSettings { it.copy(position = SubtitlePosition.BOTTOM) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) {
                                    Text("Bottom")
                                }
                            }
                        }
                    )
                }
            }

            if (subtitleMatch && searchQuery.isBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // ── CONTROLS & GESTURES ──
            val controlsMatch = match("gesture controls", "swipe seek sensitivity", "control bar buttons", "player buttons", "action buttons", "previous", "next", "rotation", "buttons", "controls", "gestures", "swipe")
            if (controlsMatch) {
                if (searchQuery.isBlank()) {
                    SectionHeader(title = "Controls & Gestures", icon = Icons.Default.TouchApp)
                }

                // Gestures toggle
                if (match("gesture controls", "gestures", "swipe", "volume", "brightness", "controls")) {
                    SwitchListItem(
                        title = "Gesture controls",
                        subtitle = "Swipe for volume, brightness, seek",
                        checked = playerSettings.gesturesEnabled,
                        icon = Icons.Default.Swipe,
                        onCheckedChange = { value ->
                            viewModel.updatePlayerSettings { it.copy(gesturesEnabled = value) }
                        }
                    )
                }

                // Swipe Seek Sensitivity Slider
                if (playerSettings.gesturesEnabled && match("swipe seek sensitivity", "sensitivity", "swipe", "seek", "gestures")) {
                    ListItem(
                        headlineContent = { Text("Swipe seek sensitivity") },
                        supportingContent = {
                            Column {
                                Slider(
                                    value = playerSettings.swipeSeekSensitivity.toFloat().coerceIn(20f, 500f),
                                    onValueChange = {
                                        viewModel.updatePlayerSettings { s -> s.copy(swipeSeekSensitivity = it.toInt()) }
                                    },
                                    valueRange = 20f..500f,
                                    steps = 48
                                )
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Swipe, null) },
                        trailingContent = { Text("${playerSettings.swipeSeekSensitivity}s per inch") },
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }

                // Custom control bar buttons embedded reorderer list!
                if (match("control bar buttons", "player buttons", "action buttons", "previous", "next", "rotation", "buttons", "controls")) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Player Action Buttons",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Live Preview Strip
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    "Preview",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))

                                    orderedKeys.forEach { key ->
                                        if (enabledButtons.contains(key)) {
                                            val info = allButtons.firstOrNull { it.key == key }
                                            if (info != null) {
                                                Icon(
                                                    info.icon,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .padding(horizontal = 8.dp)
                                                        .size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Buttons List
                        orderedKeys.forEachIndexed { index, key ->
                            val info = allButtons.firstOrNull { it.key == key }
                            if (info != null) {
                                val isEnabled = enabledButtons.contains(key)
                                if (match(info.label, info.key, "buttons", "controls")) {
                                    ListItem(
                                        headlineContent = { Text(info.label) },
                                        leadingContent = { Icon(info.icon, null) },
                                        trailingContent = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Move Up
                                                IconButton(
                                                    enabled = index > 0,
                                                    onClick = {
                                                        val list = orderedKeys.toMutableList()
                                                        val temp = list[index]
                                                        list[index] = list[index - 1]
                                                        list[index - 1] = temp
                                                        orderedKeys = list
                                                        saveButtons(list.filter { enabledButtons.contains(it) })
                                                    }
                                                ) {
                                                    Icon(Icons.Default.ArrowUpward, "Move Up")
                                                }

                                                // Move Down
                                                IconButton(
                                                    enabled = index < orderedKeys.size - 1,
                                                    onClick = {
                                                        val list = orderedKeys.toMutableList()
                                                        val temp = list[index]
                                                        list[index] = list[index + 1]
                                                        list[index + 1] = temp
                                                        orderedKeys = list
                                                        saveButtons(list.filter { enabledButtons.contains(it) })
                                                    }
                                                ) {
                                                    Icon(Icons.Default.ArrowDownward, "Move Down")
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Switch(
                                                    checked = isEnabled,
                                                    onCheckedChange = { checked ->
                                                        val newEnabled = enabledButtons.toMutableList()
                                                        if (checked) {
                                                            newEnabled.add(key)
                                                        } else {
                                                            newEnabled.remove(key)
                                                        }
                                                        val orderedEnabled = orderedKeys.filter { newEnabled.contains(it) }
                                                        saveButtons(orderedEnabled)
                                                    }
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (controlsMatch && searchQuery.isBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // ── ADVANCED ──
            val advancedMatch = match("hardware decoding", "buffer size", "advanced", "hardware", "decoding", "mediacodec", "hw", "buffer", "size")
            if (advancedMatch) {
                if (searchQuery.isBlank()) {
                    ListItem(
                        headlineContent = {
                            Text(
                                "Advanced",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.DeveloperMode,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { isAdvancedExpanded = !isAdvancedExpanded }) {
                                Icon(
                                    if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    null
                                )
                            }
                        },
                        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { isAdvancedExpanded = !isAdvancedExpanded }
                    )
                }

                if (isAdvancedExpanded || searchQuery.isNotBlank()) {
                    // Hardware decoding toggle
                    if (match("hardware decoding", "hardware", "decoding", "mediacodec", "advanced", "hw")) {
                        SwitchListItem(
                            title = "Hardware decoding",
                            subtitle = "Use MediaCodec for video decoding",
                            checked = playerSettings.hardwareDecoding,
                            icon = Icons.Default.Memory,
                            onCheckedChange = { value ->
                                viewModel.updatePlayerSettings { it.copy(hardwareDecoding = value) }
                            }
                        )
                    }

                    // Buffer size slider
                    if (match("buffer size", "buffer", "size", "seconds", "advanced")) {
                        ListItem(
                            headlineContent = { Text("Buffer size") },
                            supportingContent = {
                                Slider(
                                    value = playerSettings.bufferSizeSec.toFloat(),
                                    onValueChange = {
                                        viewModel.updatePlayerSettings { settings -> settings.copy(bufferSizeSec = it.toInt()) }
                                    },
                                    valueRange = 1f..60f,
                                    steps = 59
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Storage, null) },
                            trailingContent = { Text("${playerSettings.bufferSizeSec}s") },
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Version info at bottom
            if (searchQuery.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Player1 v0.2.0",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SwitchListItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun SeekDurationTile(
    title: String,
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    val options = listOf(5, 10, 15, 30)

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                options.forEach { sec ->
                    val isSelected = currentValue == sec
                    FilterChip(
                        selected = isSelected,
                        onClick = { onValueChange(sec) },
                        label = { Text("${sec}s") }
                    )
                }
            }
        },
        leadingContent = { Icon(Icons.Default.FastForward, null) }
    )
}

