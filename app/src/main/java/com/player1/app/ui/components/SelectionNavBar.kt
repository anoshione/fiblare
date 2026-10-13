package com.player1.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.player1.app.model.VideoItem
import com.player1.app.viewmodel.MainViewModel

@Composable
fun SelectionNavBar(
    viewModel: MainViewModel,
    isVault: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedIds by viewModel.selectedVideoIds.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()
    val vaultVideos by viewModel.vaultVideos.collectAsState()

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    val selectedVideos = remember(selectedIds, allVideos, vaultVideos) {
        val all = allVideos + vaultVideos
        all.filter { selectedIds.contains(it.id) }
    }

    val hazeState = LocalHazeState.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .height(64.dp)
            .frostedGlass(
                hazeState = hazeState,
                shape = RoundedCornerShape(40.dp),
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                opacity = 0.10f // 10% opacity for selection navbar
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            // 1. Select All (Left-most)
            ActionItem(
                icon = Icons.Default.SelectAll,
                contentDescription = "Select All",
                onClick = { viewModel.selectAll() }
            )

            // 2. Vault / Unlock (Single button position, toggling based on isVault)
            if (isVault) {
                ActionItem(
                    icon = Icons.Default.LockOpen,
                    contentDescription = "Unlock",
                    isEnabled = selectedIds.isNotEmpty(),
                    onClick = {
                        viewModel.unlockVideos(selectedIds)
                        viewModel.clearSelection()
                        Toast.makeText(context, "Video(s) unlocked from vault", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                ActionItem(
                    icon = Icons.Default.Lock,
                    contentDescription = "Move to Vault",
                    isEnabled = selectedIds.isNotEmpty(),
                    onClick = {
                        viewModel.lockVideos(selectedIds)
                        Toast.makeText(context, "${selectedIds.size} video(s) moved to vault", Toast.LENGTH_SHORT).show()
                        viewModel.clearSelection()
                    }
                )
            }

            // 3. Share (Disabled in Vault screen)
            ActionItem(
                icon = Icons.Default.Share,
                contentDescription = "Share",
                isEnabled = !isVault && selectedIds.isNotEmpty(),
                onClick = {
                    viewModel.shareVideos(context, selectedIds)
                }
            )

            // 4. Details
            ActionItem(
                icon = Icons.Default.Info,
                contentDescription = "Details",
                isEnabled = selectedIds.isNotEmpty(),
                onClick = { showDetailsDialog = true }
            )

            // 5. Delete
            ActionItem(
                icon = Icons.Default.Delete,
                contentDescription = "Delete",
                isEnabled = selectedIds.isNotEmpty(),
                isDestructive = true,
                onClick = { showDeleteConfirmation = true }
            )

            // 6. Cancel (Right-most)
            ActionItem(
                icon = Icons.Default.Close,
                contentDescription = "Cancel",
                onClick = { viewModel.clearSelection() }
            )
        }
    }

    // ── DETAILS DIALOG ──
    if (showDetailsDialog) {
        FrostedGlassAlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text("Selected Video Details") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (selectedVideos.size == 1) {
                        val video = selectedVideos.first()
                        Text("Title: ${video.title}", fontWeight = FontWeight.Medium)
                        Text("Path: ${video.path}")
                        Text("Size: ${video.formattedSize}")
                        Text("Duration: ${video.formattedDuration}")
                        Text("Resolution: ${video.width ?: "N/A"} x ${video.height ?: "N/A"}")
                        val formattedDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            .format(video.dateAdded)
                        Text("Date Added: $formattedDate")
                    } else {
                        Text("Total Videos Selected: ${selectedVideos.size}", fontWeight = FontWeight.Bold)
                        val totalSizeBytes = selectedVideos.sumOf { it.sizeBytes }
                        val totalDurationMs = selectedVideos.sumOf { it.durationMs }
                        Text("Total Storage Size: ${formatSize(totalSizeBytes)}")
                        Text("Total Playback Duration: ${formatDuration(totalDurationMs)}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // ── DELETE CONFIRMATION DIALOG ──
    if (showDeleteConfirmation) {
        FrostedGlassAlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Selected Videos?") },
            text = {
                Text("Are you sure you want to permanently delete the ${selectedIds.size} selected video(s) from storage? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteVideos(selectedIds) {
                            viewModel.clearSelection()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RowScope.ActionItem(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isEnabled: Boolean = true,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tint = if (!isEnabled) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    } else if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .then(
                    if (isEnabled) Modifier.clickable { onClick() } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$bytes Bytes"
    }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
