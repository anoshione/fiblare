package com.player1.app.service

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.player1.app.model.VideoItem
import java.io.File
import java.util.Date

class VideoScanner(private val context: Context) {

    fun scanVideos(excludedExtensions: List<String>): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val query = try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn).toString()
                val name = cursor.getString(nameColumn) ?: "Video"
                val path = cursor.getString(dataColumn) ?: continue
                var durationMs = cursor.getLong(durationColumn)
                val sizeBytes = cursor.getLong(sizeColumn)
                val dateAddedSeconds = cursor.getLong(dateColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)

                val file = File(path)
                val extension = file.extension.lowercase()
                if (extension.isEmpty()) continue
                if (excludedExtensions.contains(extension)) continue

                videos.add(
                    VideoItem(
                        id = id,
                        title = file.nameWithoutExtension.ifEmpty { name },
                        path = path,
                        extension = extension,
                        durationMs = durationMs,
                        sizeBytes = sizeBytes,
                        dateAdded = Date(dateAddedSeconds * 1000),
                        width = if (width > 0) width else null,
                        height = if (height > 0) height else null
                    )
                )
            }
        }

        return videos
    }
}
