package com.player1.app.model

import java.io.File
import java.util.Date

data class VideoItem(
    val id: String,
    val title: String,
    val path: String,
    val extension: String,
    var durationMs: Long,
    val sizeBytes: Long,
    val dateAdded: Date,
    val width: Int? = null,
    val height: Int? = null,
    var lastPositionMs: Long = 0L,
    var isLocked: Boolean = false,
    var audioTracks: List<String> = emptyList()
) {
    val formattedSize: String
        get() {
            if (sizeBytes < 1024) return "$sizeBytes B"
            if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0)
            if (sizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            return String.format("%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }

    val aspectRatio: Float
        get() {
            val w = width
            val h = height
            if (w != null && h != null && h > 0) {
                return w.toFloat() / h.toFloat()
            }
            return 16f / 9f
        }

    val isLandscape: Boolean
        get() = aspectRatio >= 1.0f

    val progress: Float
        get() {
            if (durationMs <= 0) return 0f
            return (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }

    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (h > 0) {
                String.format("%d:%02d:%02d", h, m, s)
            } else {
                String.format("%02d:%02d", m, s)
            }
        }
}
