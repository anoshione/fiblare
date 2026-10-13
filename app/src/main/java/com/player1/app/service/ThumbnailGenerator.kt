package com.player1.app.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class ThumbnailGenerator(private val context: Context) {
    private val isGenerating = ConcurrentHashMap<String, Boolean>()
    private val activeRetrievers = ConcurrentHashMap<String, MediaMetadataRetriever>()

    fun getThumbnailDir(videoId: String): File {
        val cacheDir = File(context.cacheDir, "video_thumbnails")
        val dir = File(cacheDir, videoId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @Synchronized
    private fun getOrCreateRetriever(videoId: String, videoPath: String): MediaMetadataRetriever {
        var retriever = activeRetrievers[videoId]
        if (retriever == null) {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            activeRetrievers[videoId] = retriever
        }
        return retriever
    }

    fun releaseRetriever(videoId: String) {
        activeRetrievers.remove(videoId)?.let { retriever ->
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun touchAndEvictCache(videoId: String) {
        val cacheDir = File(context.cacheDir, "video_thumbnails")
        if (!cacheDir.exists()) return
        
        // Touch current dir
        val currentDir = File(cacheDir, videoId)
        if (currentDir.exists()) {
            currentDir.setLastModified(System.currentTimeMillis())
        }
        
        // Get all subdirectories
        val subDirs = cacheDir.listFiles { file -> file.isDirectory } ?: return
        if (subDirs.size > 5) { // Keep last 5 videos
            subDirs.sortByDescending { it.lastModified() }
            for (i in 5 until subDirs.size) {
                deleteRecursive(subDirs[i])
            }
        }
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { deleteRecursive(it) }
        }
        fileOrDirectory.delete()
    }

    suspend fun generateThumbnails(videoId: String, videoPath: String) = withContext(Dispatchers.IO) {
        if (isGenerating[videoId] == true) return@withContext

        touchAndEvictCache(videoId)

        val dir = getThumbnailDir(videoId)
        // Check specifically for thumb_ prefixed files to avoid false positive from library_thumb.jpg
        val existingFiles = dir.listFiles { file -> file.isFile && file.name.startsWith("thumb_") && file.name.endsWith(".jpg") }
        if (!existingFiles.isNullOrEmpty()) {
            return@withContext
        }

        isGenerating[videoId] = true
        try {
            val retriever = getOrCreateRetriever(videoId, videoPath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            if (durationMs <= 0) return@withContext

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val width = widthStr?.toIntOrNull() ?: 1280
            val height = heightStr?.toIntOrNull() ?: 720

            // Target max dimension of 480px for efficiency
            val scale = 480f / Math.max(width, height).toFloat()
            val dstWidth = if (scale < 1f) (width * scale).toInt() else width
            val dstHeight = if (scale < 1f) (height * scale).toInt() else height

            val intervalMs = 1000L // 1 second interval
            var currentMs = 0L
            var index = 1

            while (currentMs < durationMs) {
                val timeUs = currentMs * 1000L
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dstWidth, dstHeight)
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }

                if (bitmap != null) {
                    val fileName = String.format("thumb_%04d.jpg", index)
                    val outFile = File(dir, fileName)
                    FileOutputStream(outFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
                    }
                    bitmap.recycle()
                }
                currentMs += intervalMs
                index++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isGenerating[videoId] = false
        }
    }

    suspend fun getNearestThumbnail(videoId: String, positionMs: Long, videoPath: String? = null): File? = withContext(Dispatchers.IO) {
        val dir = getThumbnailDir(videoId)
        if (!dir.exists()) dir.mkdirs()

        val index = (positionMs / 1000.0).roundToInt() + 1
        val expectedFileName = String.format("thumb_%04d.jpg", index)
        val expectedFile = File(dir, expectedFileName)
        if (expectedFile.exists()) {
            return@withContext expectedFile
        }

        // Fallback: list all thumb files, sort them, return closest
        val allFiles = dir.listFiles { file -> file.isFile && file.name.endsWith(".jpg") && file.name.startsWith("thumb_") }
        if (!allFiles.isNullOrEmpty()) {
            allFiles.sortBy { it.name }
            val targetIdx = index - 1
            return@withContext if (targetIdx in allFiles.indices) {
                allFiles[targetIdx]
            } else if (targetIdx >= allFiles.size) {
                allFiles.last()
            } else {
                allFiles.first()
            }
        }

        // Fallback 2: Generate dynamic frame on demand using the cached retriever session
        if (videoPath != null) {
            try {
                val retriever = getOrCreateRetriever(videoId, videoPath)
                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val width = widthStr?.toIntOrNull() ?: 1280
                val height = heightStr?.toIntOrNull() ?: 720
                val scale = 480f / Math.max(width, height).toFloat()
                val dstWidth = if (scale < 1f) (width * scale).toInt() else width
                val dstHeight = if (scale < 1f) (height * scale).toInt() else height

                val timeUs = positionMs * 1000L
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dstWidth, dstHeight)
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }

                if (bitmap != null) {
                    val outFile = File(dir, expectedFileName)
                    FileOutputStream(outFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
                    }
                    bitmap.recycle()
                    return@withContext outFile
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        null
    }

    suspend fun getOrCreateLibraryThumbnail(videoId: String, videoPath: String): File? = withContext(Dispatchers.IO) {
        val dir = getThumbnailDir(videoId)
        val file = File(dir, "library_thumb.jpg")
        if (file.exists()) return@withContext file

        try {
            val retriever = getOrCreateRetriever(videoId, videoPath)
            // Extract frame at 1 second (1000000 microseconds)
            val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                }
                bitmap.recycle()
                return@withContext file
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
