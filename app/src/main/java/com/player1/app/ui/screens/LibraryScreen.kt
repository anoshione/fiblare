package com.player1.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.player1.app.MainApplication
import com.player1.app.model.VideoItem
import com.player1.app.viewmodel.*
import java.io.File
import android.widget.Toast
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import com.player1.app.ui.components.FrostedGlassAlertDialog
import com.player1.app.ui.components.FrostedGlassDropdownMenu
import com.player1.app.ui.components.frostedGlass
import androidx.compose.ui.graphics.luminance
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import com.player1.app.ui.components.DropdownMenuItem

data class FolderItem(val name: String, val path: String, val videoCount: Int, val previewVideos: List<VideoItem> = emptyList())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MainApplication

    val videos by viewModel.filteredVideos.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()

    val libraryTab by viewModel.libraryTab.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedVideoIds.collectAsState()

    androidx.activity.compose.BackHandler(isSelectionMode) {
        viewModel.clearSelection()
    }

    var currentFolder by remember { mutableStateOf<String?>(null) }

    // Reset current folder if tab changes
    LaunchedEffect(libraryTab) {
        currentFolder = null
    }

    // Filter videos by folder if currentFolder is set
    val displayedVideos = remember(videos, libraryTab, currentFolder) {
        if (libraryTab == LibraryTab.FOLDERS && currentFolder != null) {
            videos.filter { (File(it.path).parent ?: "Root") == currentFolder }
        } else {
            videos
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val headerHeight = screenHeight * 0.2f

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshLibrary {
                pullToRefreshState.endRefresh()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        // ── SCROLLABLE CONTENT LAYER ──
        val targetOffset = pullToRefreshState.verticalOffset * 0.3f
        val animatedOffset by animateFloatAsState(
            targetValue = targetOffset,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = animatedOffset
                }
        ) {
        if (libraryTab == LibraryTab.FOLDERS && currentFolder == null) {
            val folders = remember(videos) {
                videos.groupBy { File(it.path).parent ?: "Root" }
                    .map { (parentPath, folderVideos) ->
                        val name = File(parentPath).name
                        FolderItem(name = name, path = parentPath, videoCount = folderVideos.size, previewVideos = folderVideos.take(3))
                    }
            }

            if (folders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No folders found",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                if (viewMode == LibraryViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(top = headerHeight + 16.dp, bottom = 90.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(folders, key = { it.path }) { folder ->
                            FolderGridItem(folder = folder, app = app, onClick = { currentFolder = folder.path })
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = headerHeight + 16.dp, bottom = 90.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(folders, key = { it.path }) { folder ->
                            FolderListItem(folder = folder, app = app, onClick = { currentFolder = folder.path })
                        }
                    }
                }
            }
        } else {
            if (displayedVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No videos found",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                if (viewMode == LibraryViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(top = headerHeight + 16.dp, bottom = 90.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedVideos, key = { it.id }) { video ->
                            val isSelected = selectedIds.contains(video.id)
                            VideoGridItem(
                                video = video,
                                app = app,
                                viewModel = viewModel,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(video.id)
                                    } else {
                                        onNavigateToPlayer(video.id)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.isSelectionMode.value = true
                                        viewModel.toggleSelection(video.id)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = headerHeight + 16.dp, bottom = 90.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedVideos, key = { it.id }) { video ->
                            val isSelected = selectedIds.contains(video.id)
                            VideoListItem(
                                video = video,
                                app = app,
                                viewModel = viewModel,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(video.id)
                                    } else {
                                        onNavigateToPlayer(video.id)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.isSelectionMode.value = true
                                        viewModel.toggleSelection(video.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        }

        // ── FLOATING 20% TRANSPARENT HEADER LAYER ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .background(Color.Transparent)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            val titleText = when {
                libraryTab == LibraryTab.ALL_VIDEOS -> "All Videos"
                libraryTab == LibraryTab.FOLDERS && currentFolder == null -> "Folders"
                else -> {
                    val path = currentFolder ?: ""
                    File(path).name
                }
            }

            if (libraryTab == LibraryTab.FOLDERS && currentFolder != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { currentFolder = null }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = titleText,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Text(
                    text = titleText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun FolderThumbnailImage(video: VideoItem, app: MainApplication, modifier: Modifier) {
    var thumbnailFile by androidx.compose.runtime.remember(video.id) { androidx.compose.runtime.mutableStateOf<java.io.File?>(null) }
    androidx.compose.runtime.LaunchedEffect(video.id) {
        thumbnailFile = app.thumbnailGenerator.getOrCreateLibraryThumbnail(video.id, video.path)
    }
    coil.compose.AsyncImage(
        model = thumbnailFile,
        contentDescription = null,
        modifier = modifier.background(Color.Black),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop
    )
}

@Composable
fun FolderGridItem(
    folder: FolderItem,
    app: MainApplication,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { onClick() }
            .padding(8.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 13f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val previewVideos = folder.previewVideos
                Column(modifier = Modifier.fillMaxSize()) {
                    if (previewVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        }
                    } else if (previewVideos.size == 1) {
                        FolderThumbnailImage(video = previewVideos[0], app = app, modifier = Modifier.fillMaxSize())
                    } else if (previewVideos.size == 2) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            FolderThumbnailImage(video = previewVideos[0], app = app, modifier = Modifier.weight(1f).fillMaxHeight())
                            Spacer(modifier = Modifier.width(2.dp))
                            FolderThumbnailImage(video = previewVideos[1], app = app, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            FolderThumbnailImage(video = previewVideos[0], app = app, modifier = Modifier.weight(1f).fillMaxHeight())
                            Spacer(modifier = Modifier.width(2.dp))
                            FolderThumbnailImage(video = previewVideos[1], app = app, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        FolderThumbnailImage(video = previewVideos[2], app = app, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.videoCount} videos",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun FolderListItem(
    folder: FolderItem,
    app: MainApplication,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val previewVideos = folder.previewVideos
        Box(
            modifier = Modifier
                .size(width = 110.dp, height = 66.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (previewVideos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                } else if (previewVideos.size == 1) {
                    FolderThumbnailImage(video = previewVideos[0], app = app, modifier = Modifier.fillMaxSize())
                } else if (previewVideos.size == 2) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        FolderThumbnailImage(video = previewVideos[0], app = app, modifier = Modifier.weight(1f).fillMaxHeight())
                        Spacer(modifier = Modifier.width(1.dp))
                        FolderThumbnailImage(video = previewVideos[1], app = app, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        FolderThumbnailImage(video = previewVideos[0], app = app, modifier = Modifier.weight(1f).fillMaxHeight())
                        Spacer(modifier = Modifier.width(1.dp))
                        FolderThumbnailImage(video = previewVideos[1], app = app, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Spacer(modifier = Modifier.height(1.dp))
                    FolderThumbnailImage(video = previewVideos[2], app = app, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${folder.videoCount} videos",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridItem(
    video: VideoItem,
    app: MainApplication,
    viewModel: MainViewModel,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var thumbnailFile by remember(video.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(video.id) {
        thumbnailFile = app.thumbnailGenerator.getOrCreateLibraryThumbnail(video.id, video.path)
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var pendingMenuAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 13f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
            ) {
                coil.compose.AsyncImage(
                    model = thumbnailFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else androidx.compose.ui.graphics.Color.Transparent)
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = video.extension.uppercase(),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = video.formattedDuration,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (video.progress > 0f) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { video.progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = androidx.compose.ui.graphics.Color.Transparent,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.formattedSize,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    FrostedGlassDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        onDismissFinished = {
                            pendingMenuAction?.invoke()
                            pendingMenuAction = null
                        }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                        val vaultText = if (video.isLocked) "Unlock from Vault" else "Add to Vault"
                        val vaultIcon = if (video.isLocked) Icons.Default.LockOpen else Icons.Default.Lock
                        DropdownMenuItem(
                            text = { Text(vaultText) },
                            leadingIcon = { Icon(vaultIcon, null) },
                            onClick = {
                                menuExpanded = false
                                if (video.isLocked) {
                                    viewModel.unlockVideos(listOf(video.id))
                                    Toast.makeText(context, "${video.title} unlocked from vault", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.lockVideos(listOf(video.id))
                                    Toast.makeText(context, "${video.title} moved to vault", Toast.LENGTH_SHORT).show()
                                }
                             }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = {
                                menuExpanded = false
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out this video: ${video.title} (${video.path})")
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                pendingMenuAction = { showDeleteDialog = true }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Properties") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = {
                                menuExpanded = false
                                pendingMenuAction = { showPropertiesDialog = true }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPropertiesDialog) {
        val formattedDate = remember(video.dateAdded) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(video.dateAdded)
            } catch (_: Exception) {
                video.dateAdded.toString()
            }
        }
        FrostedGlassAlertDialog(
            onDismissRequest = { showPropertiesDialog = false },
            title = { Text("Video Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Title: ${video.title}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Path: ${video.path}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Format: ${video.extension.uppercase()}",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Size: ${video.formattedSize}",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Duration: ${video.formattedDuration}",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Date Added: $formattedDate",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPropertiesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showDeleteDialog) {
        FrostedGlassAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Video?") },
            text = { Text("Are you sure you want to permanently delete \"${video.title}\" from storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        try {
                            val file = java.io.File(video.path)
                            if (file.exists() && file.delete()) {
                                Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
                                viewModel.scanVideos()
                            } else {
                                Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: VideoItem,
    app: MainApplication,
    viewModel: MainViewModel,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var thumbnailFile by remember(video.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(video.id) {
        thumbnailFile = app.thumbnailGenerator.getOrCreateLibraryThumbnail(video.id, video.path)
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var pendingMenuAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(width = 110.dp, height = 66.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            AsyncImage(
                model = thumbnailFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = video.formattedDuration,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (video.progress > 0f) {
                LinearProgressIndicator(
                    progress = video.progress,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = video.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = video.extension.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = video.formattedSize,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isSelectionMode) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                FrostedGlassDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    onDismissFinished = {
                        pendingMenuAction?.invoke()
                        pendingMenuAction = null
                    }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    val vaultText = if (video.isLocked) "Unlock from Vault" else "Add to Vault"
                    val vaultIcon = if (video.isLocked) Icons.Default.LockOpen else Icons.Default.Lock
                    DropdownMenuItem(
                        text = { Text(vaultText) },
                        leadingIcon = { Icon(vaultIcon, null) },
                        onClick = {
                            menuExpanded = false
                            if (video.isLocked) {
                                viewModel.unlockVideos(listOf(video.id))
                                Toast.makeText(context, "${video.title} unlocked from vault", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.lockVideos(listOf(video.id))
                                Toast.makeText(context, "${video.title} moved to vault", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            menuExpanded = false
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "Check out this video: ${video.title} (${video.path})")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            pendingMenuAction = { showDeleteDialog = true }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Properties") },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = {
                            menuExpanded = false
                            pendingMenuAction = { showPropertiesDialog = true }
                        }
                    )
                }
            }
        }
    }

    if (showPropertiesDialog) {
        val formattedDate = remember(video.dateAdded) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(video.dateAdded)
            } catch (_: Exception) {
                video.dateAdded.toString()
            }
        }
        FrostedGlassAlertDialog(
            onDismissRequest = { showPropertiesDialog = false },
            title = { Text("Video Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Title: ${video.title}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Path: ${video.path}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Format: ${video.extension.uppercase()}",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Size: ${video.formattedSize}",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Duration: ${video.formattedDuration}",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Date Added: $formattedDate",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPropertiesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showDeleteDialog) {
        FrostedGlassAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Video?") },
            text = { Text("Are you sure you want to permanently delete \"${video.title}\" from storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        try {
                            val file = java.io.File(video.path)
                            if (file.exists() && file.delete()) {
                                Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
                                viewModel.scanVideos()
                            } else {
                                Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}



