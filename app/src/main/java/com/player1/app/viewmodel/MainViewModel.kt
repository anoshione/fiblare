package com.player1.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.player1.app.MainApplication
import com.player1.app.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import android.content.Context
import android.media.MediaScannerConnection

enum class LibraryViewMode { GRID, LIST }
enum class LibrarySortMode { DATE, NAME, SIZE, TYPE }
enum class LibraryTab { ALL_VIDEOS, FOLDERS }
enum class SortDirection { ASCENDING, DESCENDING }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MainApplication
    private val settingsRepository = app.settingsRepository
    private val videoScanner = app.videoScanner

    // Settings States
    val appSettings = MutableStateFlow(settingsRepository.loadAppSettings())
    val playerSettings = MutableStateFlow(settingsRepository.loadPlayerSettings())
    val subtitleSettings = MutableStateFlow(settingsRepository.loadSubtitleSettings())
    val lastResumableVideoId = MutableStateFlow<String?>(settingsRepository.getLastResumableVideoId())
    val hasChosenResumePref = MutableStateFlow(settingsRepository.hasChosenResumePref())
    val deleteRequestIntent = kotlinx.coroutines.flow.MutableSharedFlow<android.app.PendingIntent>(extraBufferCapacity = 1)

    fun setLastResumableVideoId(id: String) {
        lastResumableVideoId.value = id
        settingsRepository.saveLastResumableVideoId(id)
    }

    fun setHasChosenResumePref(value: Boolean) {
        hasChosenResumePref.value = value
        settingsRepository.setHasChosenResumePref(value)
    }

    fun getVideoSubtitleConfig(videoId: String): VideoSubtitleConfig {
        return settingsRepository.getVideoSubtitleConfig(videoId)
    }

    fun saveVideoSubtitleConfig(videoId: String, config: VideoSubtitleConfig) {
        settingsRepository.saveVideoSubtitleConfig(videoId, config)
    }

    // Library UI States loaded from repo
    val viewMode = MutableStateFlow(LibraryViewMode.valueOf(settingsRepository.getViewMode(LibraryViewMode.GRID.name)))
    val sortMode = MutableStateFlow(LibrarySortMode.valueOf(settingsRepository.getSortMode(LibrarySortMode.DATE.name)))
    val sortDirection = MutableStateFlow(SortDirection.valueOf(settingsRepository.getSortDirection(SortDirection.DESCENDING.name)))
    val libraryTab = MutableStateFlow(LibraryTab.valueOf(settingsRepository.getLibraryTab(LibraryTab.ALL_VIDEOS.name)))
    val showHidden = MutableStateFlow(settingsRepository.getShowHidden(false))
    val searchQuery = MutableStateFlow("")
    val selectedExtensions = MutableStateFlow<Set<String>>(emptySet())

    fun setViewMode(mode: LibraryViewMode) {
        viewMode.value = mode
        settingsRepository.saveViewMode(mode.name)
    }

    fun setSortMode(mode: LibrarySortMode) {
        sortMode.value = mode
        settingsRepository.saveSortMode(mode.name)
    }

    fun setSortDirection(dir: SortDirection) {
        sortDirection.value = dir
        settingsRepository.saveSortDirection(dir.name)
    }

    fun setLibraryTab(tab: LibraryTab) {
        libraryTab.value = tab
        settingsRepository.saveLibraryTab(tab.name)
    }

    fun setShowHidden(show: Boolean) {
        showHidden.value = show
        settingsRepository.saveShowHidden(show)
    }

    // Selection States
    val isSelectionMode = MutableStateFlow(false)
    val selectedVideoIds = MutableStateFlow<Set<String>>(emptySet())

    // Vault States
    private val _lockedVideoIds = MutableStateFlow(settingsRepository.loadLockedIds())
    val lockedVideoIds: StateFlow<Set<String>> = _lockedVideoIds.asStateFlow()
    val vaultAuthenticated = MutableStateFlow(false)
    val settingsSearchActive = MutableStateFlow(false)

    private val _vaultVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val vaultVideos: StateFlow<List<VideoItem>> = _vaultVideos.asStateFlow()

    // Raw Videos List
    private val _allVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val allVideos: StateFlow<List<VideoItem>> = _allVideos.asStateFlow()

    init {
        scanVideos()
        scanVaultVideos()
    }

    fun refreshLibrary(onComplete: () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val job1 = scanVideos()
            val job2 = scanVaultVideos()
            job1.join()
            job2.join()
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1200L) {
                kotlinx.coroutines.delay(1200L - elapsed)
            }
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun scanVaultVideos(): kotlinx.coroutines.Job {
        return viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            val vaultDir = File(context.filesDir, "hidden_vault")
            if (!vaultDir.exists()) vaultDir.mkdirs()
            val nomedia = File(vaultDir, ".nomedia")
            if (!nomedia.exists()) {
                try {
                    nomedia.createNewFile()
                } catch (_: Exception) {}
            }

            val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
            val files = vaultDir.listFiles { file -> file.isFile && file.name != ".nomedia" } ?: emptyArray()

            val list = files.map { file ->
                val name = file.name
                val origPath = prefs.getString("orig_path_$name", "") ?: ""
                val duration = prefs.getLong("duration_$name", 0L)
                val width = prefs.getInt("width_$name", 0)
                val height = prefs.getInt("height_$name", 0)
                val size = prefs.getLong("size_$name", file.length())
                val date = prefs.getLong("date_$name", file.lastModified())

                VideoItem(
                    id = name,
                    title = file.nameWithoutExtension,
                    path = file.absolutePath,
                    extension = file.extension,
                    durationMs = duration,
                    sizeBytes = size,
                    dateAdded = java.util.Date(date),
                    width = if (width > 0) width else null,
                    height = if (height > 0) height else null,
                    isLocked = true
                )
            }
            _vaultVideos.value = list
        }
    }

    fun scanVideos(): kotlinx.coroutines.Job {
        return viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val excluded = appSettings.value.excludedExtensions
            val videos = videoScanner.scanVideos(excluded)
            
            // Load last positions and locked status
            val lastPositions = settingsRepository.loadLastPositions()
            val locked = _lockedVideoIds.value

            videos.forEach { video ->
                video.lastPositionMs = lastPositions[video.id] ?: 0L
                video.isLocked = locked.contains(video.id)
            }

            _allVideos.value = videos

            // Resolve duration fallback in the background
            val missingDurations = videos.filter { it.durationMs <= 0 }
            if (missingDurations.isNotEmpty()) {
                missingDurations.forEach { video ->
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(video.path)
                            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            val duration = durStr?.toLongOrNull() ?: 0L
                            if (duration > 0) {
                                video.durationMs = duration
                                _allVideos.value = _allVideos.value.toList()
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        } finally {
                            try {
                                retriever.release()
                            } catch (_: Throwable) {}
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        }
    }

    // Combined filtered video list
    val filteredVideos: StateFlow<List<VideoItem>> = combine(
        _allVideos,
        selectedExtensions,
        _lockedVideoIds,
        searchQuery,
        sortMode,
        sortDirection,
        showHidden
    ) { flowsArray ->
        @Suppress("UNCHECKED_CAST")
        val videos = flowsArray[0] as List<VideoItem>
        @Suppress("UNCHECKED_CAST")
        val selectedExts = flowsArray[1] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val locked = flowsArray[2] as Set<String>
        val query = flowsArray[3] as String
        val sort = flowsArray[4] as LibrarySortMode
        val direction = flowsArray[5] as SortDirection
        val hidden = flowsArray[6] as Boolean

        var list = videos.filter { v ->
            // Exclude locked videos from main library
            if (locked.contains(v.id)) return@filter false

            // Hide hidden files if not enabled
            if (!hidden && v.path.contains("/.")) return@filter false

            // Search query
            if (query.isNotEmpty() && !v.title.contains(query, ignoreCase = true)) return@filter false

            // Filter by extension
            if (selectedExts.isNotEmpty()) {
                selectedExts.contains(v.extension)
            } else {
                true
            }
        }

        // Apply Sort
        list = when (sort) {
            LibrarySortMode.DATE -> {
                if (direction == SortDirection.DESCENDING) list.sortedByDescending { it.dateAdded }
                else list.sortedBy { it.dateAdded }
            }
            LibrarySortMode.SIZE -> {
                if (direction == SortDirection.DESCENDING) list.sortedByDescending { it.sizeBytes }
                else list.sortedBy { it.sizeBytes }
            }
            LibrarySortMode.NAME -> {
                val comp = { a: VideoItem, b: VideoItem -> naturalCompare(a.title, b.title) }
                if (direction == SortDirection.DESCENDING) list.sortedWith { a, b -> comp(b, a) }
                else list.sortedWith { a, b -> comp(a, b) }
            }
            LibrarySortMode.TYPE -> {
                if (direction == SortDirection.DESCENDING) list.sortedByDescending { it.extension }
                else list.sortedBy { it.extension }
            }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unique extension chips with counts
    val extensionChips: StateFlow<Map<String, Int>> = _allVideos.map { videos ->
        val counts = mutableMapOf<String, Int>()
        videos.forEach { v ->
            counts[v.extension] = (counts[v.extension] ?: 0) + 1
        }
        counts.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ── Selection Actions ────────────────────────────────────────────

    fun toggleSelection(id: String) {
        val current = selectedVideoIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
            if (current.isEmpty()) {
                isSelectionMode.value = false
            }
        } else {
            current.add(id)
        }
        selectedVideoIds.value = current
    }

    fun clearSelection() {
        isSelectionMode.value = false
        selectedVideoIds.value = emptySet()
    }

    fun selectAll() {
        val allIds = filteredVideos.value.map { it.id }.toSet()
        if (selectedVideoIds.value.size == allIds.size) {
            selectedVideoIds.value = emptySet()
        } else {
            selectedVideoIds.value = allIds
        }
    }

    // ── Settings Updates ─────────────────────────────────────────────

    fun updateAppSettings(updater: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = updater(appSettings.value)
            appSettings.value = updated
            settingsRepository.saveAppSettings(updated)
            // Re-scan if exclusions changed
            scanVideos()
        }
    }

    fun updatePlayerSettings(updater: (PlayerSettings) -> PlayerSettings) {
        viewModelScope.launch {
            val updated = updater(playerSettings.value)
            playerSettings.value = updated
            settingsRepository.savePlayerSettings(updated)
        }
    }

    fun updateSubtitleSettings(updater: (SubtitleSettings) -> SubtitleSettings) {
        viewModelScope.launch {
            val updated = updater(subtitleSettings.value)
            subtitleSettings.value = updated
            settingsRepository.saveSubtitleSettings(updated)
        }
    }

    // ── Last Position Updates ────────────────────────────────────────

    fun saveLastPosition(videoId: String, positionMs: Long) {
        viewModelScope.launch {
            settingsRepository.saveLastPosition(videoId, positionMs)
            // Update in-memory video list
            val current = _allVideos.value.toMutableList()
            val index = current.indexOfFirst { it.id == videoId }
            if (index != -1) {
                current[index] = current[index].copy(lastPositionMs = positionMs)
                _allVideos.value = current
            }
        }
    }

    fun clearLastPosition(videoId: String) {
        viewModelScope.launch {
            settingsRepository.clearLastPosition(videoId)
            val current = _allVideos.value.toMutableList()
            val index = current.indexOfFirst { it.id == videoId }
            if (index != -1) {
                current[index] = current[index].copy(lastPositionMs = 0L)
                _allVideos.value = current
            }
        }
    }

    // ── Vault Actions ────────────────────────────────────────────────

    fun lockVideos(ids: Collection<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            val vaultDir = File(context.filesDir, "hidden_vault")
            if (!vaultDir.exists()) vaultDir.mkdirs()
            val nomedia = File(vaultDir, ".nomedia")
            if (!nomedia.exists()) {
                try {
                    nomedia.createNewFile()
                } catch (_: Exception) {}
            }

            val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
            val currentLocked = _lockedVideoIds.value.toMutableSet()
            val urisToDelete = mutableListOf<android.net.Uri>()

            ids.forEach { id ->
                val video = _allVideos.value.find { it.id == id } ?: return@forEach
                val srcFile = File(video.path)
                if (srcFile.exists()) {
                    val uniqueName = ".${System.currentTimeMillis()}_${srcFile.name}"
                    val destFile = File(vaultDir, uniqueName)
                    
                    var copied = false
                    try {
                        srcFile.copyTo(destFile, overwrite = true)
                        copied = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (copied) {
                        prefs.edit()
                            .putString("orig_path_$uniqueName", video.path)
                            .putLong("duration_$uniqueName", video.durationMs)
                            .putInt("width_$uniqueName", video.width ?: 0)
                            .putInt("height_$uniqueName", video.height ?: 0)
                            .putLong("size_$uniqueName", video.sizeBytes)
                            .putLong("date_$uniqueName", video.dateAdded.time)
                            .apply()

                        currentLocked.add(uniqueName)

                        var deleted = false
                        try {
                            deleted = srcFile.delete()
                        } catch (_: Exception) {}

                        if (!deleted) {
                            val uri = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id.toLong()
                            )
                            urisToDelete.add(uri)
                        }
                    }
                }
            }

            _lockedVideoIds.value = currentLocked
            settingsRepository.saveLockedIds(currentLocked)

            if (urisToDelete.isNotEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                        deleteRequestIntent.tryEmit(pendingIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    urisToDelete.forEach { uri ->
                        try {
                            context.contentResolver.delete(uri, null, null)
                        } catch (_: Exception) {}
                    }
                }
            }

            scanVideos()
            scanVaultVideos()
        }
    }

    fun unlockVideos(ids: Collection<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            val vaultDir = File(context.filesDir, "hidden_vault")
            val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
            val currentLocked = _lockedVideoIds.value.toMutableSet()

            ids.forEach { id ->
                val destFile = File(vaultDir, id)
                if (destFile.exists()) {
                    val origPath = prefs.getString("orig_path_$id", null)
                    if (origPath != null) {
                        val srcFile = File(origPath)
                        srcFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        
                        var restored = destFile.renameTo(srcFile)
                        if (!restored) {
                            try {
                                destFile.copyTo(srcFile, overwrite = true)
                                destFile.delete()
                                restored = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        if (restored) {
                            prefs.edit()
                                .remove("orig_path_$id")
                                .remove("duration_$id")
                                .remove("width_$id")
                                .remove("height_$id")
                                .remove("size_$id")
                                .remove("date_$id")
                                .apply()

                            currentLocked.remove(id)

                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(srcFile.absolutePath),
                                null,
                                null
                            )
                        }
                    }
                }
            }

            _lockedVideoIds.value = currentLocked
            settingsRepository.saveLockedIds(currentLocked)

            scanVideos()
            scanVaultVideos()
        }
    }

    fun deleteVideos(ids: Collection<String>, onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            val vaultDir = File(context.filesDir, "hidden_vault")
            val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)

            val urisToDelete = mutableListOf<android.net.Uri>()
            val vaultIdsToDelete = mutableListOf<String>()

            ids.forEach { id ->
                val video = _allVideos.value.find { it.id == id }
                if (video != null) {
                    val file = File(video.path)
                    var deleted = false
                    try {
                        deleted = file.delete()
                    } catch (_: Exception) {}

                    if (!deleted) {
                        val uri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id.toLong()
                        )
                        urisToDelete.add(uri)
                    }
                } else {
                    val vaultFile = File(vaultDir, id)
                    if (vaultFile.exists()) {
                        vaultFile.delete()
                    }
                    prefs.edit()
                        .remove("orig_path_$id")
                        .remove("duration_$id")
                        .remove("width_$id")
                        .remove("height_$id")
                        .remove("size_$id")
                        .remove("date_$id")
                        .apply()
                    vaultIdsToDelete.add(id)
                }
            }

            if (vaultIdsToDelete.isNotEmpty()) {
                val currentLocked = _lockedVideoIds.value.toMutableSet()
                currentLocked.removeAll(vaultIdsToDelete)
                _lockedVideoIds.value = currentLocked
                settingsRepository.saveLockedIds(currentLocked)
                scanVaultVideos()
            }

            if (urisToDelete.isNotEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                        deleteRequestIntent.tryEmit(pendingIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    urisToDelete.forEach { uri ->
                        try {
                            context.contentResolver.delete(uri, null, null)
                        } catch (_: Exception) {}
                    }
                    scanVideos()
                }
            } else {
                scanVideos()
            }

            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun shareVideos(context: Context, ids: Collection<String>) {
        val uris = ArrayList<android.net.Uri>()
        ids.forEach { id ->
            val video = _allVideos.value.find { it.id == id } ?: _vaultVideos.value.find { it.id == id }
            if (video != null) {
                val file = File(video.path)
                if (file.exists()) {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                        uris.add(uri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        if (uris.isEmpty()) {
            android.widget.Toast.makeText(context, "No shareable files found", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            type = "video/*"
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Videos"))
    }

    // ── Natural Compare Helper ───────────────────────────────────────

    private fun naturalCompare(a: String, b: String): Int {
        val regex = Regex("(\\d+)|(\\D+)")
        val aMatches = regex.findAll(a).map { it.value }.toList()
        val bMatches = regex.findAll(b).map { it.value }.toList()

        for (i in 0 until minOf(aMatches.size, bMatches.size)) {
            val aMatch = aMatches[i]
            val bMatch = bMatches[i]

            val aNum = aMatch.toLongOrNull()
            val bNum = bMatch.toLongOrNull()

            if (aNum != null && bNum != null) {
                if (aNum != bNum) {
                    return aNum.compareTo(bNum)
                }
            } else {
                val comp = aMatch.lowercase().compareTo(bMatch.lowercase())
                if (comp != 0) {
                    return comp
                }
            }
        }
        return a.length.compareTo(b.length)
    }

    // Factory Class
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application) as T
        }
    }
}
