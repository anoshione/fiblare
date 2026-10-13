package com.player1.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.Painter
import coil.compose.rememberAsyncImagePainter
import coil.compose.AsyncImagePainter
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import com.player1.app.ui.components.FrostedGlassAlertDialog
import com.player1.app.ui.components.FrostedGlassDropdownMenu
import com.player1.app.ui.components.LocalHazeState
import com.player1.app.ui.components.frostedGlass
import com.player1.app.ui.components.DropdownMenuItem
import com.player1.app.ui.components.hazeSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import com.player1.app.model.VideoSubtitleConfig
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.player1.app.MainApplication
import com.player1.app.model.ResumeMode
import com.player1.app.model.SubtitlePosition
import com.player1.app.model.VideoItem
import com.player1.app.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PlayerScreen(
    videoId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hazeState = LocalHazeState.current
    val app = context.applicationContext as MainApplication
    val activity = context as? Activity

    var currentVideoId by remember { mutableStateOf(videoId) }
    val isInPip = activity?.isInPictureInPictureMode == true

    val allVideos by viewModel.allVideos.collectAsState()
    val vaultVideos by viewModel.vaultVideos.collectAsState()
    val video = remember(allVideos, vaultVideos, currentVideoId) {
        allVideos.firstOrNull { it.id == currentVideoId } ?: vaultVideos.firstOrNull { it.id == currentVideoId }
    }

    val libraryTab by viewModel.libraryTab.collectAsState()
    val filteredVideos by viewModel.filteredVideos.collectAsState()

    val videosToNavigate = remember(filteredVideos, vaultVideos, libraryTab, video) {
        if (video == null) {
            emptyList()
        } else if (video.isLocked) {
            vaultVideos
        } else if (libraryTab == com.player1.app.viewmodel.LibraryTab.FOLDERS) {
            val parent = java.io.File(video.path).parent ?: ""
            filteredVideos.filter { (java.io.File(it.path).parent ?: "") == parent }
        } else {
            filteredVideos
        }
    }

    val currentIndex = remember(videosToNavigate, currentVideoId) {
        videosToNavigate.indexOfFirst { it.id == currentVideoId }
    }

    val playerSettings by viewModel.playerSettings.collectAsState()
    val subtitleSettings by viewModel.subtitleSettings.collectAsState()

    var player: ExoPlayer? by remember { mutableStateOf(null) }
    var isInitialized by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }

    // Controls Overlays States
    var controlsVisible by remember { mutableStateOf(false) }
    var rotationLocked by remember { mutableStateOf(false) }
    var activeDialogType by remember { mutableStateOf<String?>(null) } // "speed", "audio", "subtitle", "decoder"
    var pendingDialogType by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var videoSubConfig by remember(currentVideoId) { mutableStateOf(viewModel.getVideoSubtitleConfig(currentVideoId)) }
    var pendingSubtitleSeek by remember { mutableStateOf(false) }

    // Seek states
    var wasPlayingBeforeSeek by remember { mutableStateOf(false) }

    // Flashing overlay icon states
    var flashVisible by remember { mutableStateOf(false) }
    var flashIsPlay by remember { mutableStateOf(true) }
    var flashJob by remember { mutableStateOf<Job?>(null) }

    // Background playback state
    var backgroundPlaybackEnabled by remember { mutableStateOf(false) }

    // Sleep Timer states
    var sleepTimerJob by remember { mutableStateOf<Job?>(null) }
    var sleepTimeRemainingMs by remember { mutableStateOf(0L) }

    val coroutineScope = rememberCoroutineScope()


    fun flashIcon(isPlay: Boolean) {
        flashJob?.cancel()
        flashIsPlay = isPlay
        flashVisible = true
        flashJob = coroutineScope.launch {
            delay(600)
            flashVisible = false
        }
    }

    fun formatSleepTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    fun startSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = coroutineScope.launch {
            while (sleepTimeRemainingMs > 0) {
                delay(1000)
                sleepTimeRemainingMs -= 1000
            }
            player?.pause()
            Toast.makeText(context, "Sleep timer expired. Playback paused.", Toast.LENGTH_SHORT).show()
        }
    }

    fun addSleepTime(ms: Long) {
        sleepTimeRemainingMs += ms
        startSleepTimer()
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimeRemainingMs = 0L
    }

    // Helper to copy Uri to cache dir
    fun copyUriToLocalSubtitles(context: Context, uri: android.net.Uri, videoId: String): java.io.File? {
        try {
            val resolver = context.contentResolver
            val cursor = resolver.query(uri, null, null, null, null)
            var fileName = "subtitle_${System.currentTimeMillis()}.srt"
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        val name = it.getString(nameIdx)
                        if (!name.isNullOrEmpty()) {
                            fileName = name
                        }
                    }
                }
            }
            val subDir = java.io.File(context.cacheDir, "video_subtitles/$videoId")
            if (!subDir.exists()) subDir.mkdirs()
            val destFile = java.io.File(subDir, fileName)
            
            resolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return destFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // Subtitle Timestamp Timing Shift Helper
    fun shiftSubtitleTimestamps(inputFile: java.io.File, outputFile: java.io.File, offsetMs: Long) {
        if (!inputFile.exists()) return
        try {
            val lines = inputFile.readLines()
            val writer = outputFile.bufferedWriter()
            
            val timecodeRegex = """(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""".toRegex()
            val shortTimecodeRegex = """(\d{2}):(\d{2})[,.](\d{3})""".toRegex()

            fun parseTimecode(s: String): Long {
                timecodeRegex.matchEntire(s.trim())?.let { match ->
                    val h = match.groupValues[1].toLong()
                    val m = match.groupValues[2].toLong()
                    val s = match.groupValues[3].toLong()
                    val ms = match.groupValues[4].toLong()
                    return h * 3600000L + m * 60000L + s * 1000L + ms
                }
                shortTimecodeRegex.matchEntire(s.trim())?.let { match ->
                    val m = match.groupValues[1].toLong()
                    val s = match.groupValues[2].toLong()
                    val ms = match.groupValues[3].toLong()
                    return m * 60000L + s * 1000L + ms
                }
                return 0L
            }

            fun formatTimecode(msVal: Long, isVtt: Boolean): String {
                val totalMs = Math.max(0L, msVal)
                val h = totalMs / 3600000L
                val m = (totalMs % 3600000L) / 60000L
                val s = (totalMs % 60000L) / 1000L
                val ms = totalMs % 1000L
                val sep = if (isVtt) "." else ","
                return String.format("%02d:%02d:%02d%s%03d", h, m, s, sep, ms)
            }

            val isVtt = inputFile.extension.lowercase() == "vtt"

            for (line in lines) {
                if (line.contains("-->")) {
                    val parts = line.split("-->")
                    if (parts.size == 2) {
                        val startMs = parseTimecode(parts[0])
                        val endMs = parseTimecode(parts[1])
                        val newStartMs = startMs + offsetMs
                        val newEndMs = endMs + offsetMs
                        val newTimecodeLine = "${formatTimecode(newStartMs, isVtt)} --> ${formatTimecode(newEndMs, isVtt)}"
                        writer.write(newTimecodeLine)
                        writer.newLine()
                        continue
                    }
                }
                writer.write(line)
                writer.newLine()
            }
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Helper to reload subtitles on the player
    fun reloadPlayerSubtitles(exo: ExoPlayer, config: VideoSubtitleConfig) {
        val v = video ?: return
        val currentPos = exo.currentPosition
        val isPlayingState = exo.isPlaying

        val uri = if (v.path.startsWith("content://") || v.path.startsWith("file://")) {
            android.net.Uri.parse(v.path)
        } else {
            android.net.Uri.fromFile(java.io.File(v.path))
        }

        val subtitleConfigurations = mutableListOf<MediaItem.SubtitleConfiguration>()
        config.selectedTrackIds.forEach { trackId ->
            if (trackId.startsWith("custom:")) {
                val originalPath = trackId.substringAfter("custom:")
                val originalFile = java.io.File(originalPath)
                if (originalFile.exists()) {
                    context.cacheDir.listFiles { file ->
                        file.name.startsWith("temp_sync_${v.id}_")
                    }?.forEach { it.delete() }

                    val tempFile = java.io.File(context.cacheDir, "temp_sync_${v.id}_${System.currentTimeMillis()}.srt")
                    shiftSubtitleTimestamps(originalFile, tempFile, config.syncOffsetMs.toLong())
                    
                    val mimeType = if (originalFile.extension.lowercase() == "vtt") {
                        MimeTypes.TEXT_VTT
                    } else {
                        MimeTypes.APPLICATION_SUBRIP
                    }
                    
                    val subBuilder = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(tempFile))
                        .setMimeType(mimeType)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    subtitleConfigurations.add(subBuilder)
                }
            }
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subtitleConfigurations)
            .build()

        exo.setMediaItem(mediaItem, false)
        exo.prepare()
        exo.seekTo(currentPos)
        exo.playWhenReady = isPlayingState

        // Re-apply embedded selections
        val currentTracks = exo.currentTracks
        val builder = exo.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)

        config.selectedTrackIds.forEach { trackId ->
            if (trackId.startsWith("embedded:")) {
                val coords = trackId.substringAfter("embedded:").split("_")
                if (coords.size == 2) {
                    val groupIndex = coords[0].toIntOrNull() ?: 0
                    val trackIndex = coords[1].toIntOrNull() ?: 0
                    if (groupIndex in currentTracks.groups.indices) {
                        val group = currentTracks.groups[groupIndex]
                        builder.setOverrideForType(
                            androidx.media3.common.TrackSelectionOverride(
                                group.mediaTrackGroup,
                                trackIndex
                            )
                        )
                    }
                }
            }
        }
        exo.trackSelectionParameters = builder.build()
    }

    // Gesture Layer States
    var gestureIndicatorText by remember { mutableStateOf("") }
    var gestureIndicatorIcon by remember { mutableStateOf(Icons.Default.Brightness6) }
    var gestureIndicatorProgress by remember { mutableStateOf(0f) }
    var showGestureIndicator by remember { mutableStateOf(false) }

    // Seek states
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0L) }
    var seekPreviewVisible by remember { mutableStateOf(false) }
    var seekPreviewPosition by remember { mutableStateOf(0L) }
    var seekPreviewThumbPos by remember { mutableStateOf(0.5f) } // 0.0 to 1.0, or -1f for center
    var seekPreviewFile: File? by remember { mutableStateOf(null) }
    var previewBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(seekPreviewVisible) {
        if (!seekPreviewVisible) {
            previewBitmap = null
        }
    }

    var autoHideJob: Job? by remember { mutableStateOf(null) }
    var seekPreviewJob: Job? by remember { mutableStateOf(null) }

    fun resetControlsTimer() {
        autoHideJob?.cancel()
        if (player?.isPlaying == true && !menuExpanded) {
            autoHideJob = coroutineScope.launch {
                delay(2000)
                controlsVisible = false
            }
        }
    }

    // Toggle full screen / immersive UI
    fun toggleSystemUi(show: Boolean) {
        val window = activity?.window ?: return
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (show) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // Load seek preview thumbnail
    fun updateSeekPreview(positionMs: Long, thumbPos: Float) {
        seekPreviewPosition = positionMs
        seekPreviewThumbPos = thumbPos
        seekPreviewVisible = true

        seekPreviewJob?.cancel()
        seekPreviewJob = coroutineScope.launch {
            val file = app.thumbnailGenerator.getNearestThumbnail(currentVideoId, positionMs, video?.path)
            seekPreviewFile = file
            if (file != null) {
                try {
                    val loader = coil.ImageLoader(context)
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(file)
                        .allowConversionToBitmap(true)
                        .build()
                    val result = loader.execute(request)
                    if (result is coil.request.SuccessResult) {
                        val bitmap = result.drawable.toBitmap()
                        previewBitmap = bitmap.asImageBitmap()
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    }

    // Playback Speed Adjustment
    fun applySpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        viewModel.updatePlayerSettings { it.copy(defaultSpeed = speed) }
    }

    // Setup Keep Screen On and Immersive UI
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        toggleSystemUi(false)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            toggleSystemUi(true)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            // Reset brightness override back to default
            val layoutParams = activity?.window?.attributes
            layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity?.window?.attributes = layoutParams
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, backgroundPlaybackEnabled) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val inPip = activity?.isInPictureInPictureMode == true
                if (!backgroundPlaybackEnabled && !inPip) {
                    player?.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Disable subtitles in Picture-in-Picture mode and restore them in full screen
    LaunchedEffect(isInPip) {
        player?.let { exo ->
            val builder = exo.trackSelectionParameters.buildUpon()
            if (isInPip) {
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                videoSubConfig.selectedTrackIds.forEach { trackId ->
                    if (trackId.startsWith("embedded:")) {
                        val coords = trackId.substringAfter("embedded:").split("_")
                        if (coords.size == 2) {
                            val groupIndex = coords[0].toIntOrNull() ?: 0
                            val trackIndex = coords[1].toIntOrNull() ?: 0
                            val currentTracks = exo.currentTracks
                            if (groupIndex in currentTracks.groups.indices) {
                                val group = currentTracks.groups[groupIndex]
                                builder.setOverrideForType(
                                    androidx.media3.common.TrackSelectionOverride(
                                        group.mediaTrackGroup,
                                        trackIndex
                                    )
                                )
                            }
                        }
                    }
                }
            }
            exo.trackSelectionParameters = builder.build()
        }
    }

    // Handle Orientation Auto Rotate
    LaunchedEffect(video, rotationLocked) {
        if (video != null && activity != null) {
            if (rotationLocked) {
                // Lock current
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            } else {
                activity.requestedOrientation = if (video.isLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        }
    }

    // ExoPlayer Init / Cleanup
    if (video != null) {
        DisposableEffect(video.path) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    playerSettings.bufferSizeSec * 1000,
                    playerSettings.bufferSizeSec * 1000,
                    1500,
                    2000
                )
                .build()

            val uri = if (video.path.startsWith("content://") || video.path.startsWith("file://")) {
                android.net.Uri.parse(video.path)
            } else {
                android.net.Uri.fromFile(java.io.File(video.path))
            }

            val subtitleConfigurations = mutableListOf<MediaItem.SubtitleConfiguration>()
            videoSubConfig.selectedTrackIds.forEach { trackId ->
                if (trackId.startsWith("custom:")) {
                    val originalPath = trackId.substringAfter("custom:")
                    val originalFile = java.io.File(originalPath)
                    if (originalFile.exists()) {
                        val tempFile = java.io.File(context.cacheDir, "temp_sync_${video.id}.srt")
                        shiftSubtitleTimestamps(originalFile, tempFile, videoSubConfig.syncOffsetMs.toLong())
                        
                        val mimeType = if (originalFile.extension.lowercase() == "vtt") {
                            MimeTypes.TEXT_VTT
                        } else {
                            MimeTypes.APPLICATION_SUBRIP
                        }
                        
                        val subBuilder = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(tempFile))
                            .setMimeType(mimeType)
                            .setLanguage("en")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                        subtitleConfigurations.add(subBuilder)
                    }
                }
            }

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setSubtitleConfigurations(subtitleConfigurations)
                .build()

            val exo = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    playWhenReady = true
                    setPlaybackSpeed(playerSettings.defaultSpeed)
                    setMediaItem(mediaItem)
                    prepare()
                }

            player = exo
            viewModel.setLastResumableVideoId(video.id)

            // Handle Resume Dialog / Autosave
            val lastPosition = app.settingsRepository.getLastPosition(video.id)
            if (lastPosition != null && lastPosition > 5000L) {
                when (playerSettings.resumeMode) {
                    ResumeMode.ALWAYS_RESUME -> {
                        exo.seekTo(lastPosition)
                    }
                    ResumeMode.ALWAYS_ASK -> {
                        activeDialogType = "resume"
                    }
                    ResumeMode.ALWAYS_FROM_BEGINNING -> {
                        // Start from 0
                    }
                }
            }

            // Start Thumbnail Generation in Background
            coroutineScope.launch {
                app.thumbnailGenerator.generateThumbnails(video.id, video.path)
            }

            // Audio track query
            // Exoplayer tracks are ready after PREPARED. We will inspect them on listener callback.
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        val appSettingsVal = viewModel.appSettings.value
                        if (appSettingsVal.backToList) {
                            onBack()
                        } else {
                            if (currentIndex < videosToNavigate.size - 1) {
                                currentVideoId = videosToNavigate[currentIndex + 1].id
                            } else {
                                onBack()
                            }
                        }
                    } else if (state == Player.STATE_READY) {
                        isInitialized = true
                        totalDuration = exo.duration

                        // Apply embedded track overrides
                        val currentTracks = exo.currentTracks
                        val builder = exo.trackSelectionParameters.buildUpon()
                        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)

                        videoSubConfig.selectedTrackIds.forEach { trackId ->
                            if (trackId.startsWith("embedded:")) {
                                val coords = trackId.substringAfter("embedded:").split("_")
                                if (coords.size == 2) {
                                    val groupIndex = coords[0].toIntOrNull() ?: 0
                                    val trackIndex = coords[1].toIntOrNull() ?: 0
                                    if (groupIndex in currentTracks.groups.indices) {
                                        val group = currentTracks.groups[groupIndex]
                                        builder.setOverrideForType(
                                            androidx.media3.common.TrackSelectionOverride(
                                                group.mediaTrackGroup,
                                                trackIndex
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        exo.trackSelectionParameters = builder.build()

                        if (pendingSubtitleSeek) {
                            pendingSubtitleSeek = false
                            val current = exo.currentPosition
                            val target = if (current + 1 < exo.duration) current + 1 else Math.max(0, current - 1)
                            exo.seekTo(target)
                        }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    currentPosition = newPosition.positionMs
                }
            }

            exo.addListener(listener)

            // Periodic Poll of position
            val positionPoller = coroutineScope.launch {
                while (true) {
                    if (!isSeeking && exo.isPlaying) {
                        currentPosition = exo.currentPosition
                        // Save last position periodically
                        if (currentPosition > 0) {
                            viewModel.saveLastPosition(video.id, currentPosition)
                        }
                    }
                    delay(500)
                }
            }

            onDispose {
                positionPoller.cancel()
                exo.removeListener(listener)
                exo.release()
                player = null
                sleepTimerJob?.cancel()
                app.thumbnailGenerator.releaseRetriever(video.id)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
        // Layer 1: ExoPlayer Video Surface
        player?.let { exo ->
            AndroidView(
                factory = { ctx ->
                    val view = android.view.LayoutInflater.from(ctx).inflate(com.player1.app.R.layout.custom_player_view, null) as androidx.media3.ui.PlayerView
                    view.apply {
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = exo
                    view.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleSettings.fontSizeSp)
                    val subtitleView = view.subtitleView
                    if (subtitleView != null) {
                        subtitleView.setPadding(0, 0, 0, 0)
                        val lp = subtitleView.layoutParams as? FrameLayout.LayoutParams
                        if (lp != null) {
                            val activePadding = videoSubConfig.paddingDp
                            val padPx = (activePadding * view.context.resources.displayMetrics.density).toInt()
                            lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                            lp.setMargins(0, 0, 0, padPx)
                            subtitleView.layoutParams = lp
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }

        // Layer 1.5: Full Screen Seek Preview Overlay
        if (seekPreviewVisible) {
            previewBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = "Seek Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Flashing Play/Pause icon moved to the very top layer
        // Layer 2: Gesture Control Layer
        if (isInitialized && playerSettings.gesturesEnabled && !isInPip) {
            var startDx by remember { mutableStateOf(0f) }
            var startDy by remember { mutableStateOf(0f) }
            var totalDx by remember { mutableStateOf(0f) }
            var totalDy by remember { mutableStateOf(0f) }
            var gestureType: String? by remember { mutableStateOf(null) } // "volume", "brightness", "seek"

            var initialVolume by remember { mutableStateOf(0f) }
            var initialBrightness by remember { mutableStateOf(0f) }

            val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
            val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val width = size.width
                                val height = size.height
                                val isInCenter = offset.x in (width * 0.25f)..(width * 0.75f) &&
                                                 offset.y in (height * 0.25f)..(height * 0.75f)
                                
                                if (isInCenter) {
                                    player?.let {
                                        if (it.isPlaying) {
                                            it.pause()
                                            flashIcon(false)
                                        } else {
                                            it.play()
                                            flashIcon(true)
                                        }
                                    }
                                } else {
                                    controlsVisible = !controlsVisible
                                    if (controlsVisible) resetControlsTimer()
                                }
                            },
                            onDoubleTap = { offset ->
                                val width = size.width
                                val seekSec = if (offset.x < width / 2) -playerSettings.seekBackSec else playerSettings.seekForwardSec
                                val target = (currentPosition + seekSec * 1000L).coerceIn(0L, totalDuration)
                                player?.seekTo(target)
                                currentPosition = target
                                
                                // Show Quick Seek Center Indicator
                                gestureIndicatorIcon = if (seekSec > 0) Icons.Default.FastForward else Icons.Default.FastRewind
                                gestureIndicatorText = "${if (seekSec > 0) "+" else ""}${seekSec}s"
                                gestureIndicatorProgress = 0f
                                showGestureIndicator = true
                                coroutineScope.launch {
                                    delay(700)
                                    showGestureIndicator = false
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                startDx = offset.x
                                startDy = offset.y
                                totalDx = 0f
                                totalDy = 0f
                                gestureType = null

                                // Fetch current system brightness/volume
                                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                initialBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                if (initialBrightness < 0f) initialBrightness = 0.5f
                            },
                            onDrag = { change, dragAmount ->
                                totalDx += dragAmount.x
                                totalDy += dragAmount.y

                                val screenWidth = size.width.toFloat()
                                val screenHeight = size.height.toFloat()

                                if (gestureType == null) {
                                    if (Math.abs(totalDx) > Math.abs(totalDy) && Math.abs(totalDx) > 40) {
                                        gestureType = "seek"
                                        isSeeking = true
                                        wasPlayingBeforeSeek = player?.isPlaying == true
                                        player?.pause()
                                    } else if (Math.abs(totalDy) > Math.abs(totalDx) && Math.abs(totalDy) > 40) {
                                        gestureType = if (startDx < screenWidth / 2f) "brightness" else "volume"
                                    }
                                }

                                when (gestureType) {
                                    "brightness" -> {
                                        val delta = -totalDy / screenHeight
                                        val target = (initialBrightness + delta * 1.5f).coerceIn(0f, 1f)
                                        
                                        // Update Window Brightness natively
                                        val layoutParams = activity?.window?.attributes
                                        layoutParams?.screenBrightness = target
                                        activity?.window?.attributes = layoutParams

                                        gestureIndicatorIcon = Icons.Default.Brightness6
                                        gestureIndicatorText = "${(target * 100).toInt()}%"
                                        gestureIndicatorProgress = target
                                        showGestureIndicator = true
                                    }
                                    "volume" -> {
                                        val delta = -totalDy / screenHeight
                                        val maxVal = maxVolume.toFloat()
                                        val target = (initialVolume + delta * maxVal * 1.5f).coerceIn(0f, maxVal).toInt()
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)

                                        val progress = target.toFloat() / maxVal
                                        gestureIndicatorIcon = when {
                                            progress > 0.5f -> Icons.Default.VolumeUp
                                            progress > 0f -> Icons.Default.VolumeDown
                                            else -> Icons.Default.VolumeOff
                                        }
                                        gestureIndicatorText = "${(progress * 100).toInt()}%"
                                        gestureIndicatorProgress = progress
                                        showGestureIndicator = true
                                    }
                                    "seek" -> {
                                        val densityDpi = context.resources.displayMetrics.densityDpi
                                        val dragInches = totalDx / densityDpi.toFloat()
                                        val deltaSec = (dragInches * playerSettings.swipeSeekSensitivity).toLong()
                                        val target = (currentPosition + deltaSec * 1000L).coerceIn(0L, totalDuration)
                                        seekValue = target

                                        showGestureIndicator = false

                                        updateSeekPreview(target, -1f) // -1f for Center position alignment
                                    }
                                }
                            },
                            onDragEnd = {
                                if (gestureType == "seek") {
                                    player?.seekTo(seekValue)
                                    currentPosition = seekValue
                                    isSeeking = false
                                    if (wasPlayingBeforeSeek) player?.play() else player?.pause()
                                }
                                seekPreviewVisible = false
                                showGestureIndicator = false
                                gestureType = null
                            }
                        )
                    }
            )
        }

        // Layer 3: Controls Overlay
        AnimatedVisibility(
            visible = (controlsVisible || isSeeking) && !isInPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isSeeking) 0.0f else 0.4f))
            ) {
                // Top Bar
                if (!isSeeking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        IconButton(onClick = { onBack() }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = video?.title ?: "",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Picture-in-Picture / Popup button
                        IconButton(onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                val params = android.app.PictureInPictureParams.Builder().build()
                                activity?.enterPictureInPictureMode(params)
                            }
                        }) {
                            Icon(Icons.Default.PictureInPicture, "Popup Mode", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Overflow Menu
                        Box {
                            IconButton(onClick = { menuExpanded = true; resetControlsTimer() }) {
                                Icon(Icons.Default.MoreVert, "More Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FrostedGlassDropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false; resetControlsTimer() },
                                onDismissFinished = {
                                    pendingDialogType?.let {
                                        activeDialogType = it
                                        pendingDialogType = null
                                    }
                                }
                            ) {
                                DropdownMenuItem(
                                      text = { Text("Subtitle settings") },
                                      leadingIcon = { Icon(Icons.Default.Subtitles, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = {
                                        pendingDialogType = "subtitle"
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                      text = { Text("Speed (${String.format("%.2fx", playerSettings.defaultSpeed)})") },
                                      leadingIcon = { Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = {
                                        pendingDialogType = "speed"
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                      text = { Text("Audio tracks") },
                                      leadingIcon = { Icon(Icons.Default.Audiotrack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = {
                                        pendingDialogType = "audio"
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                      text = { Text("HW/SW Decoder") },
                                      leadingIcon = { Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = {
                                        pendingDialogType = "decoder"
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                      text = { Text(if (rotationLocked) "Unlock rotation" else "Lock rotation") },
                                      leadingIcon = { 
                                        androidx.compose.animation.Crossfade(targetState = rotationLocked, animationSpec = androidx.compose.animation.core.tween(500)) { locked ->
                                            Icon(if (locked) Icons.Default.ScreenLockRotation else Icons.Default.ScreenRotation, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                      },
                                      onClick = {
                                          rotationLocked = !rotationLocked
                                          menuExpanded = false
                                      }
                                )
                                DropdownMenuItem(
                                      text = { Text(if (backgroundPlaybackEnabled) "Background Playback: On" else "Background Playback: Off") },
                                      leadingIcon = { 
                                        androidx.compose.animation.Crossfade(targetState = backgroundPlaybackEnabled, animationSpec = androidx.compose.animation.core.tween(500)) { enabled ->
                                            Icon(if (enabled) Icons.Default.Headphones else Icons.Default.VolumeMute, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                      },
                                      onClick = {
                                          backgroundPlaybackEnabled = !backgroundPlaybackEnabled
                                          menuExpanded = false
                                          Toast.makeText(context, if (backgroundPlaybackEnabled) "Background playback enabled" else "Background playback disabled", Toast.LENGTH_SHORT).show()
                                      }
                                )
                                DropdownMenuItem(
                                    text = {
                                        val suffix = if (sleepTimeRemainingMs > 0) " (${formatSleepTime(sleepTimeRemainingMs)})" else ""
                                        Text("Sleep Timer$suffix")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Snooze, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = {
                                        pendingDialogType = "sleep_timer"
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Center Play/Pause button removed per user request

                // Bottom Seekbar & Navigation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    // SeekBar Slider
                    val displayPosition = if (isSeeking) seekValue else currentPosition
                    val activeColor = MaterialTheme.colorScheme.primary
                    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
                    val tickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                    val inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .padding(horizontal = 10.dp)
                        ) {
                            val trackWidth = size.width
                            val centerY = size.height / 2f
                            val strokeWidthPx = 4.dp.toPx()
                            
                            val fraction = if (totalDuration > 0) (displayPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                            val currentXPx = trackWidth * fraction
                            
                            // Active track
                            drawLine(
                                color = activeColor,
                                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                                end = androidx.compose.ui.geometry.Offset(currentXPx, centerY),
                                strokeWidth = strokeWidthPx,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            // Inactive track
                            drawLine(
                                color = inactiveColor,
                                start = androidx.compose.ui.geometry.Offset(currentXPx, centerY),
                                end = androidx.compose.ui.geometry.Offset(trackWidth, centerY),
                                strokeWidth = strokeWidthPx,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            
                            // Ticks inside line
                            val numDots = 100
                            for (i in 0..numDots) {
                                val cx = trackWidth * (i.toFloat() / numDots)
                                val cColor = if (cx <= currentXPx) tickColor else inactiveTickColor
                                drawCircle(color = cColor, radius = strokeWidthPx * 0.4f, center = androidx.compose.ui.geometry.Offset(cx, centerY))
                            }
                        }

                        Slider(
                            value = displayPosition.toFloat(),
                            onValueChange = {
                                if (!isSeeking) {
                                    wasPlayingBeforeSeek = player?.isPlaying == true
                                    player?.pause()
                                }
                                isSeeking = true
                                seekValue = it.toLong()
                                val fraction = if (totalDuration > 0) it / totalDuration.toFloat() else 0.5f
                                updateSeekPreview(seekValue, fraction)
                                resetControlsTimer()
                            },
                            onValueChangeFinished = {
                                player?.seekTo(seekValue)
                                currentPosition = seekValue
                                isSeeking = false
                                seekPreviewVisible = false
                                if (wasPlayingBeforeSeek) {
                                    player?.play()
                                } else {
                                    player?.pause()
                                }
                                resetControlsTimer()
                            },
                            valueRange = 0f..(if (totalDuration > 0) totalDuration.toFloat() else 1f),
                            colors = SliderDefaults.colors(
                                activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                                inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Timestamps & Nav Buttons Row
                    if (!isSeeking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            val duration = VideoItem(
                                id = "",
                                title = "",
                                path = "",
                                extension = "",
                                durationMs = displayPosition,
                                sizeBytes = 0,
                                dateAdded = java.util.Date()
                            ).formattedDuration

                            val total = VideoItem(
                                id = "",
                                title = "",
                                path = "",
                                extension = "",
                                durationMs = totalDuration,
                                sizeBytes = 0,
                                dateAdded = java.util.Date()
                            ).formattedDuration

                            Text(
                                "$duration / $total",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )



                            Spacer(modifier = Modifier.weight(1f))

                            if (playerSettings.visibleNavButtons.contains("prev")) {
                                IconButton(
                                    onClick = {
                                        resetControlsTimer()
                                        if (currentIndex > 0) {
                                            currentVideoId = videosToNavigate[currentIndex - 1].id
                                        } else {
                                            Toast.makeText(context, "First video", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = currentIndex > 0
                                ) {
                                    Icon(Icons.Default.SkipPrevious, null, tint = if (currentIndex > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                                }
                            }

                            if (playerSettings.visibleNavButtons.contains("next")) {
                                IconButton(
                                    onClick = {
                                        resetControlsTimer()
                                        if (currentIndex < videosToNavigate.size - 1) {
                                            currentVideoId = videosToNavigate[currentIndex + 1].id
                                        } else {
                                            Toast.makeText(context, "Last video", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = currentIndex < videosToNavigate.size - 1
                                ) {
                                    Icon(Icons.Default.SkipNext, null, tint = if (currentIndex < videosToNavigate.size - 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // Layer 4: Floating Subtitles Panel
        if (activeDialogType == "subtitle") {
            // Track representation helper
            class TextTrackInfo(val id: String, val label: String)

            val embeddedTextTracks = remember(player, isInitialized) {
                val list = mutableListOf<TextTrackInfo>()
                val currentTracks = player?.currentTracks ?: return@remember list
                currentTracks.groups.forEachIndexed { groupIndex, group ->
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            list.add(
                                TextTrackInfo(
                                    id = "embedded:${groupIndex}_${trackIndex}",
                                    label = format.label ?: format.language ?: "Embedded Track #${trackIndex + 1}"
                                )
                            )
                        }
                    }
                }
                list
            }

            val allAvailableSubtitles = remember(embeddedTextTracks, videoSubConfig.addedPaths) {
                val list = mutableListOf<TextTrackInfo>()
                list.addAll(embeddedTextTracks)
                videoSubConfig.addedPaths.forEach { path ->
                    val file = java.io.File(path)
                    list.add(
                        TextTrackInfo(
                            id = "custom:$path",
                            label = file.name
                        )
                    )
                }
                list
            }

            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    val copiedFile = copyUriToLocalSubtitles(context, uri, currentVideoId)
                    if (copiedFile != null) {
                        val updatedConfig = videoSubConfig.copy(
                            addedPaths = videoSubConfig.addedPaths + copiedFile.absolutePath,
                            selectedTrackIds = videoSubConfig.selectedTrackIds + "custom:${copiedFile.absolutePath}"
                        )
                        videoSubConfig = updatedConfig
                        viewModel.saveVideoSubtitleConfig(currentVideoId, updatedConfig)
                        player?.let { reloadPlayerSubtitles(it, updatedConfig) }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        activeDialogType = null
                    }
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .wrapContentHeight()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume clicks to prevent dismissing when clicking inside panel */ }
                        .frostedGlass(
                            hazeState = hazeState,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                        val textColor = MaterialTheme.colorScheme.onSurface
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Subtitles", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            IconButton(onClick = { activeDialogType = null }) {
                                Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Add subtitle file button
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add subtitle file", fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Subtitle tracks checklist title
                        Text("Tracks", color = textColor.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtitle tracks scrollable list
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (allAvailableSubtitles.isEmpty()) {
                                Text(
                                    "No subtitle tracks available",
                                    color = textColor.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                    allAvailableSubtitles.forEach { subTrack ->
                                        val isChecked = videoSubConfig.selectedTrackIds.contains(subTrack.id)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable {
                                                    val updatedIds = if (isChecked) {
                                                        videoSubConfig.selectedTrackIds - subTrack.id
                                                    } else {
                                                        videoSubConfig.selectedTrackIds + subTrack.id
                                                    }
                                                    val updatedConfig = videoSubConfig.copy(selectedTrackIds = updatedIds)
                                                    videoSubConfig = updatedConfig
                                                    viewModel.saveVideoSubtitleConfig(currentVideoId, updatedConfig)
                                                    player?.let { reloadPlayerSubtitles(it, updatedConfig) }
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = textColor,
                                                checkmarkColor = MaterialTheme.colorScheme.surface,
                                                uncheckedColor = textColor.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.size(24.dp).padding(end = 8.dp)
                                        )
                                        Text(
                                            text = subTrack.label,
                                            color = textColor,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    }
                                }
                            }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Vertical Padding Offset Slider
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Offset Padding", color = textColor, fontSize = 13.sp)
                            Text("${videoSubConfig.paddingDp}dp", color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                Slider(
                    value = videoSubConfig.paddingDp.toFloat(),
                    onValueChange = {
                        val updated = videoSubConfig.copy(paddingDp = it.toInt())
                        videoSubConfig = updated
                        viewModel.saveVideoSubtitleConfig(currentVideoId, updated)
                    },
                    valueRange = -50f..1000f,
                    steps = 105
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle Sync Timing Shift Buttons
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Subtitle Sync", color = textColor, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val currentOffsetSec = videoSubConfig.syncOffsetMs / 1000f
                        Text(
                            text = String.format("%.1fs", currentOffsetSec),
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                val updated = videoSubConfig.copy(syncOffsetMs = videoSubConfig.syncOffsetMs - 500)
                                videoSubConfig = updated
                                viewModel.saveVideoSubtitleConfig(currentVideoId, updated)
                                pendingSubtitleSeek = true
                                player?.let { reloadPlayerSubtitles(it, updated) }
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("-0.5s", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = {
                                val updated = videoSubConfig.copy(syncOffsetMs = videoSubConfig.syncOffsetMs + 500)
                                videoSubConfig = updated
                                viewModel.saveVideoSubtitleConfig(currentVideoId, updated)
                                pendingSubtitleSeek = true
                                player?.let { reloadPlayerSubtitles(it, updated) }
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("+0.5s", fontSize = 11.sp)
                        }
                        }
                    }
                }
            }
        }

        // Layer 5: Drag / Gesture Indicator (Volume/Brightness Pill Overlay)
        if (showGestureIndicator) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .size(width = 160.dp, height = 130.dp)
                        .frostedGlass(
                            hazeState = hazeState,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Icon(
                        imageVector = gestureIndicatorIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = gestureIndicatorText,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (gestureIndicatorProgress > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = gestureIndicatorProgress,
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .width(112.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        val flashAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (flashVisible) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(500),
            label = "flashAlpha"
        )

        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { this.alpha = flashAlpha },
                contentAlignment = Alignment.Center
            ) {
                // Background layer
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .frostedGlass(hazeState, shape = CircleShape, transitionAlpha = flashAlpha)
                )
                
                // Icon layer on top
                Icon(
                    imageVector = if (flashIsPlay) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
    }

    // ── BOTTOM SHEET CONTROL DIALOGS ──
    if (activeDialogType != null && !isInPip) {
        when (activeDialogType) {
        "resume" -> {
            val formattedTime = VideoItem(
                id = "",
                title = "",
                path = "",
                extension = "",
                durationMs = app.settingsRepository.getLastPosition(videoId) ?: 0L,
                sizeBytes = 0,
                dateAdded = java.util.Date()
            ).formattedDuration

            FrostedGlassAlertDialog(
                onDismissRequest = { activeDialogType = null },
                title = { Text("Resume playback?") },
                text = { Text("Resume from $formattedTime?") },
                confirmButton = {
                    Button(onClick = {
                        val lastPos = app.settingsRepository.getLastPosition(videoId)
                        if (lastPos != null) {
                            player?.seekTo(lastPos)
                            currentPosition = lastPos
                        }
                        activeDialogType = null
                    }) {
                        Text("Resume")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        player?.seekTo(0)
                        currentPosition = 0
                        activeDialogType = null
                    }) {
                        Text("Start over")
                    }
                }
            )
        }

        "speed" -> {
            val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f)
            val interactionSource = remember { MutableInteractionSource() }
            val isDragging by interactionSource.collectIsDraggedAsState()

            FrostedGlassAlertDialog(
                onDismissRequest = { activeDialogType = null },
                title = { Text("Playback Speed", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            "${String.format("%.2fx", playerSettings.defaultSpeed)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom continuous slider with snapping on tap, free dragging on swipe,
                        // and snapping markers inside the line track.
                        val activeColor = MaterialTheme.colorScheme.primary
                        val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Custom segmented track with line breaks
                            val tickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                            val inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .padding(horizontal = 10.dp)
                            ) {
                                val trackWidth = size.width
                                val centerY = size.height / 2f
                                val strokeWidthPx = 4.dp.toPx()
                                val gapWidthPx = 8.dp.toPx() // Width of the line breaks/gaps
                                
                                val currentValue = playerSettings.defaultSpeed
                                val currentFraction = (currentValue - 0.25f) / (4.0f - 0.25f)
                                val currentXPx = trackWidth * currentFraction

                                if (isDragging) {
                                    // Smooth continuous track when swiping
                                    drawLine(
                                        color = activeColor,
                                        start = androidx.compose.ui.geometry.Offset(0f, centerY),
                                        end = androidx.compose.ui.geometry.Offset(currentXPx, centerY),
                                        strokeWidth = strokeWidthPx,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                    drawLine(
                                        color = inactiveColor,
                                        start = androidx.compose.ui.geometry.Offset(currentXPx, centerY),
                                        end = androidx.compose.ui.geometry.Offset(trackWidth, centerY),
                                        strokeWidth = strokeWidthPx,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                } else {
                                    // Segmented track with breaks at each preset
                                    val points = presets.map { (it - 0.25f) / (4.0f - 0.25f) * trackWidth }
                                    
                                    for (i in 0 until points.size - 1) {
                                        val startX = points[i] + (if (i > 0) gapWidthPx / 2f else 0f)
                                        val endX = points[i + 1] - (if (i + 1 < points.size - 1) gapWidthPx / 2f else 0f)
                                        
                                        if (startX < endX) {
                                            val segmentColor = if (endX <= currentXPx + gapWidthPx / 2f) activeColor else inactiveColor
                                            drawLine(
                                                color = segmentColor,
                                                start = androidx.compose.ui.geometry.Offset(startX, centerY),
                                                end = androidx.compose.ui.geometry.Offset(endX, centerY),
                                                strokeWidth = strokeWidthPx,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )
                                        }
                                    }
                                }
                                
                                // Ticks inside line
                                val numDots = 40
                                for (i in 0..numDots) {
                                    val cx = trackWidth * (i.toFloat() / numDots)
                                    // Check if cx is inside a gap in non-dragging mode
                                    var inGap = false
                                    if (!isDragging) {
                                        val points = presets.map { (it - 0.25f) / (4.0f - 0.25f) * trackWidth }
                                        for (p in points) {
                                            if (Math.abs(cx - p) < gapWidthPx / 2f) {
                                                inGap = true
                                                break
                                            }
                                        }
                                    }
                                    if (!inGap) {
                                        val cColor = if (cx <= currentXPx) tickColor else inactiveTickColor
                                        drawCircle(color = cColor, radius = strokeWidthPx * 0.4f, center = androidx.compose.ui.geometry.Offset(cx, centerY))
                                    }
                                }
                            }

                            Slider(
                                value = playerSettings.defaultSpeed,
                                onValueChange = { rawValue ->
                                    if (isDragging) {
                                        applySpeed(rawValue)
                                    } else {
                                        val nearest = presets.minByOrNull { Math.abs(it - rawValue) } ?: rawValue
                                        applySpeed(nearest)
                                    }
                                },
                                valueRange = 0.25f..4.0f,
                                interactionSource = interactionSource,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.Transparent,
                                    inactiveTrackColor = Color.Transparent,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { applySpeed(1.0f) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Reset to 1.0x")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { activeDialogType = null }) {
                        Text("Done")
                    }
                }
            )
        }

        "sleep_timer" -> {
            FrostedGlassAlertDialog(
                onDismissRequest = { activeDialogType = null },
                title = { Text("Sleep Timer", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (sleepTimeRemainingMs > 0) {
                            Text(
                                "Remaining time: ${formatSleepTime(sleepTimeRemainingMs)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "Timer inactive",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { addSleepTime(15 * 60 * 1000L) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+15m")
                            }
                            Button(
                                onClick = { addSleepTime(30 * 60 * 1000L) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+30m")
                            }
                            Button(
                                onClick = { addSleepTime(60 * 60 * 1000L) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+1h")
                            }
                        }
                        
                        if (sleepTimeRemainingMs > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                onClick = { cancelSleepTimer() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Cancel Timer")
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { activeDialogType = null }) {
                        Text("Done")
                    }
                }
            )
        }



        "decoder" -> {
            FrostedGlassAlertDialog(
                onDismissRequest = { activeDialogType = null },
                title = { Text("Hardware Decoding") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchListItem(
                            title = "Enable HW Decoder",
                            subtitle = "Uses device hardware for better performance.",
                            checked = playerSettings.hardwareDecoding,
                            icon = Icons.Default.Memory,
                            onCheckedChange = { value ->
                                viewModel.updatePlayerSettings { it.copy(hardwareDecoding = value) }
                                Toast.makeText(context, "Restart playback to apply", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { activeDialogType = null }) {
                        Text("Done")
                    }
                }
            )
        }

        "audio" -> {
            // Fetch audio tracks natively using ExoPlayer.
            val audioTracks = remember {
                val list = mutableListOf<String>()
                player?.let { exo ->
                    val tracks = exo.currentTracks
                    var index = 1
                    tracks.groups.forEach { group ->
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (i in 0 until group.length) {
                                val trackFormat = group.getTrackFormat(i)
                                val title = trackFormat.label ?: trackFormat.language ?: "Track $index"
                                list.add(title)
                                index++
                            }
                        }
                    }
                }
                list
            }

            FrostedGlassAlertDialog(
                onDismissRequest = { activeDialogType = null },
                title = { Text("Audio Tracks") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (audioTracks.isEmpty()) {
                            Text("No audio tracks found.", modifier = Modifier.padding(16.dp))
                        } else {
                            audioTracks.forEachIndexed { index, trackTitle ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable {
                                            player?.let { exo ->
                                                // Select the specific track group index
                                                var trackCounter = 0
                                                exo.currentTracks.groups.forEach { group ->
                                                    if (group.type == C.TRACK_TYPE_AUDIO) {
                                                        for (i in 0 until group.length) {
                                                            if (trackCounter == index) {
                                                                // Switch to this audio track override
                                                                val override = TrackSelectionOverride(group.mediaTrackGroup, i)
                                                                exo.trackSelectionParameters = exo.trackSelectionParameters
                                                                    .buildUpon()
                                                                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                                    .addOverride(override)
                                                                    .build()
                                                            }
                                                            trackCounter++
                                                        }
                                                    }
                                                }
                                            }
                                            activeDialogType = null
                                        }
                                        .padding(16.dp)
                                ) {
                                    Icon(Icons.Default.Audiotrack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(trackTitle, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialogType = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
}





