package com.player1.app.repository

import android.content.Context
import android.content.SharedPreferences
import com.player1.app.model.*
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("player1_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_CUSTOM_SEED_COLOR = "custom_seed_color"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED_BLACK = "amoled_black"
        private const val KEY_ACTIVE_DECODER_MODE = "active_decoder_mode"
        private const val KEY_EXCLUDED_EXTENSIONS = "excluded_extensions"

        private const val KEY_SEEK_FORWARD_SEC = "seek_forward_sec"
        private const val KEY_SEEK_BACK_SEC = "seek_back_sec"
        private const val KEY_DEFAULT_SPEED = "default_speed"
        private const val KEY_VISIBLE_NAV_BUTTONS = "visible_nav_buttons"
        private const val KEY_GESTURES_ENABLED = "gestures_enabled"
        private const val KEY_RESUME_MODE = "resume_mode"
        private const val KEY_HARDWARE_DECODING = "hardware_decoding"
        private const val KEY_BUFFER_SIZE_SEC = "buffer_size_sec"

        private const val KEY_SUBTITLE_SYNC_OFFSET = "subtitle_sync_offset"
        private const val KEY_SUBTITLE_FONT_SIZE = "subtitle_font_size"
        private const val KEY_SUBTITLE_POSITION = "subtitle_position"

        private const val KEY_LAST_POSITIONS = "last_positions"
        private const val KEY_LOCKED_IDS = "locked_ids"

        private const val KEY_LIBRARY_TAB = "library_tab"
        private const val KEY_SORT_DIRECTION = "sort_direction"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_BACK_TO_LIST = "back_to_list"

        private const val KEY_SWIPE_SEEK_SENSITIVITY = "swipe_seek_sensitivity"
        private const val KEY_SUBTITLE_PADDING = "subtitle_padding"
        private const val KEY_LAST_RESUMABLE_VIDEO_ID = "last_resumable_video_id"
    }

    // ── App Settings ─────────────────────────────────────────────────

    fun loadAppSettings(): AppSettings {
        val themePresetName = prefs.getString(KEY_THEME_PRESET, ThemePreset.FOLLOW_SYSTEM.name)
        val themePreset = try { ThemePreset.valueOf(themePresetName!!) } catch (_: Exception) { ThemePreset.FOLLOW_SYSTEM }

        val customSeedColor = if (prefs.contains(KEY_CUSTOM_SEED_COLOR)) prefs.getLong(KEY_CUSTOM_SEED_COLOR, 0L) else null

        val themeModeName = prefs.getString(KEY_THEME_MODE, ThemeModeOption.DARK.name)
        val themeMode = try { ThemeModeOption.valueOf(themeModeName!!) } catch (_: Exception) { ThemeModeOption.DARK }

        val amoledBlack = prefs.getBoolean(KEY_AMOLED_BLACK, false)
        val activeDecoderMode = prefs.getString(KEY_ACTIVE_DECODER_MODE, "hw") ?: "hw"

        val excludedJson = prefs.getString(KEY_EXCLUDED_EXTENSIONS, "[]") ?: "[]"
        val excludedList = mutableListOf<String>()
        try {
            val arr = JSONArray(excludedJson)
            for (i in 0 until arr.length()) {
                excludedList.add(arr.getString(i))
            }
        } catch (_: Exception) {}

        val backToList = prefs.getBoolean(KEY_BACK_TO_LIST, false)

        return AppSettings(themePreset, customSeedColor, themeMode, amoledBlack, activeDecoderMode, excludedList, backToList)
    }

    fun saveAppSettings(settings: AppSettings) {
        val editor = prefs.edit()
        editor.putString(KEY_THEME_PRESET, settings.themePreset.name)
        if (settings.customSeedColor != null) {
            editor.putLong(KEY_CUSTOM_SEED_COLOR, settings.customSeedColor)
        } else {
            editor.remove(KEY_CUSTOM_SEED_COLOR)
        }
        editor.putString(KEY_THEME_MODE, settings.themeMode.name)
        editor.putBoolean(KEY_AMOLED_BLACK, settings.amoledBlack)
        editor.putString(KEY_ACTIVE_DECODER_MODE, settings.activeDecoderMode)
        editor.putBoolean(KEY_BACK_TO_LIST, settings.backToList)
        
        val arr = JSONArray()
        settings.excludedExtensions.forEach { arr.put(it) }
        editor.putString(KEY_EXCLUDED_EXTENSIONS, arr.toString())
        editor.apply()
    }

    // ── Player Settings ──────────────────────────────────────────────

    fun loadPlayerSettings(): PlayerSettings {
        val seekForwardSec = prefs.getInt(KEY_SEEK_FORWARD_SEC, 10)
        val seekBackSec = prefs.getInt(KEY_SEEK_BACK_SEC, 10)
        val defaultSpeed = prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f)
        
        val visibleButtonsStr = prefs.getString(KEY_VISIBLE_NAV_BUTTONS, "prev,next") ?: "prev,next"
        val visibleButtons = if (visibleButtonsStr.isEmpty()) emptyList() else visibleButtonsStr.split(",")

        val gesturesEnabled = prefs.getBoolean(KEY_GESTURES_ENABLED, true)

        val resumeModeName = prefs.getString(KEY_RESUME_MODE, ResumeMode.ALWAYS_ASK.name)
        val resumeMode = try { ResumeMode.valueOf(resumeModeName!!) } catch (_: Exception) { ResumeMode.ALWAYS_ASK }

        val hardwareDecoding = prefs.getBoolean(KEY_HARDWARE_DECODING, true)
        val bufferSizeSec = prefs.getInt(KEY_BUFFER_SIZE_SEC, 30)
        val swipeSeekSensitivity = prefs.getInt(KEY_SWIPE_SEEK_SENSITIVITY, 20)

        return PlayerSettings(
            seekForwardSec, seekBackSec, defaultSpeed, visibleButtons,
            gesturesEnabled, resumeMode, hardwareDecoding, bufferSizeSec, swipeSeekSensitivity
        )
    }

    fun savePlayerSettings(settings: PlayerSettings) {
        prefs.edit()
            .putInt(KEY_SEEK_FORWARD_SEC, settings.seekForwardSec)
            .putInt(KEY_SEEK_BACK_SEC, settings.seekBackSec)
            .putFloat(KEY_DEFAULT_SPEED, settings.defaultSpeed)
            .putString(KEY_VISIBLE_NAV_BUTTONS, settings.visibleNavButtons.joinToString(","))
            .putBoolean(KEY_GESTURES_ENABLED, settings.gesturesEnabled)
            .putString(KEY_RESUME_MODE, settings.resumeMode.name)
            .putBoolean(KEY_HARDWARE_DECODING, settings.hardwareDecoding)
            .putInt(KEY_BUFFER_SIZE_SEC, settings.bufferSizeSec)
            .putInt(KEY_SWIPE_SEEK_SENSITIVITY, settings.swipeSeekSensitivity)
            .apply()
    }

    // ── Subtitle Settings ────────────────────────────────────────────

    fun loadSubtitleSettings(): SubtitleSettings {
        val syncOffsetMs = prefs.getInt(KEY_SUBTITLE_SYNC_OFFSET, 0)
        val fontSizeSp = prefs.getFloat(KEY_SUBTITLE_FONT_SIZE, 24.0f)
        
        val positionName = prefs.getString(KEY_SUBTITLE_POSITION, SubtitlePosition.BOTTOM.name)
        val position = try { SubtitlePosition.valueOf(positionName!!) } catch (_: Exception) { SubtitlePosition.BOTTOM }
        val paddingDp = prefs.getInt(KEY_SUBTITLE_PADDING, 20)

        return SubtitleSettings(syncOffsetMs, fontSizeSp, position, paddingDp)
    }

    fun saveSubtitleSettings(settings: SubtitleSettings) {
        prefs.edit()
            .putInt(KEY_SUBTITLE_SYNC_OFFSET, settings.syncOffsetMs)
            .putFloat(KEY_SUBTITLE_FONT_SIZE, settings.fontSizeSp)
            .putString(KEY_SUBTITLE_POSITION, settings.position.name)
            .putInt(KEY_SUBTITLE_PADDING, settings.paddingDp)
            .apply()
    }

    // ── Last Playback Positions ──────────────────────────────────────

    fun loadLastPositions(): Map<String, Long> {
        val json = prefs.getString(KEY_LAST_POSITIONS, "{}") ?: "{}"
        val map = mutableMapOf<String, Long>()
        try {
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getLong(key)
            }
        } catch (_: Exception) {}
        return map
    }

    fun saveLastPosition(videoId: String, positionMs: Long) {
        val map = loadLastPositions().toMutableMap()
        map[videoId] = positionMs
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_LAST_POSITIONS, obj.toString()).apply()
    }

    fun getLastPosition(videoId: String): Long? {
        val map = loadLastPositions()
        val pos = map[videoId]
        if (pos == null || pos <= 0L) return null
        return pos
    }

    fun clearLastPosition(videoId: String) {
        val map = loadLastPositions().toMutableMap()
        map.remove(videoId)
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_LAST_POSITIONS, obj.toString()).apply()
    }

    // ── Locked Video IDs ─────────────────────────────────────────────

    fun loadLockedIds(): Set<String> {
        val set = prefs.getStringSet(KEY_LOCKED_IDS, emptySet()) ?: emptySet()
        return set.toSet()
    }

    fun saveLockedIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_LOCKED_IDS, ids).apply()
    }

    // ── Library UI Settings ──────────────────────────────────────────

    fun getLibraryTab(default: String): String = prefs.getString(KEY_LIBRARY_TAB, default) ?: default
    fun saveLibraryTab(value: String) = prefs.edit().putString(KEY_LIBRARY_TAB, value).apply()

    fun getSortDirection(default: String): String = prefs.getString(KEY_SORT_DIRECTION, default) ?: default
    fun saveSortDirection(value: String) = prefs.edit().putString(KEY_SORT_DIRECTION, value).apply()

    fun getViewMode(default: String): String = prefs.getString(KEY_VIEW_MODE, default) ?: default
    fun saveViewMode(value: String) = prefs.edit().putString(KEY_VIEW_MODE, value).apply()

    fun getSortMode(default: String): String = prefs.getString(KEY_SORT_MODE, default) ?: default
    fun saveSortMode(value: String) = prefs.edit().putString(KEY_SORT_MODE, value).apply()

    fun getShowHidden(default: Boolean): Boolean = prefs.getBoolean(KEY_SHOW_HIDDEN, default)
    fun saveShowHidden(value: Boolean) = prefs.edit().putBoolean(KEY_SHOW_HIDDEN, value).apply()

    fun getLastResumableVideoId(): String? = prefs.getString(KEY_LAST_RESUMABLE_VIDEO_ID, null)
    fun saveLastResumableVideoId(id: String?) = prefs.edit().putString(KEY_LAST_RESUMABLE_VIDEO_ID, id).apply()

    fun hasChosenResumePref(): Boolean = prefs.getBoolean("has_chosen_resume_pref", false)
    fun setHasChosenResumePref(value: Boolean) = prefs.edit().putBoolean("has_chosen_resume_pref", value).apply()

    fun getVideoSubtitleConfig(videoId: String): VideoSubtitleConfig {
        val jsonStr = prefs.getString("sub_config_$videoId", null) ?: return VideoSubtitleConfig()
        return try {
            val obj = JSONObject(jsonStr)
            val addedPaths = mutableListOf<String>()
            val addedArr = obj.optJSONArray("addedPaths")
            if (addedArr != null) {
                for (i in 0 until addedArr.length()) {
                    addedPaths.add(addedArr.getString(i))
                }
            }

            val selectedTrackIds = mutableListOf<String>()
            val selArr = obj.optJSONArray("selectedTrackIds")
            if (selArr != null) {
                for (i in 0 until selArr.length()) {
                    selectedTrackIds.add(selArr.getString(i))
                }
            }

            val positionName = obj.optString("position", SubtitlePosition.BOTTOM.name)
            val position = try { SubtitlePosition.valueOf(positionName) } catch (_: Exception) { SubtitlePosition.BOTTOM }

            val paddingDp = obj.optInt("paddingDp", 16)
            val syncOffsetMs = obj.optInt("syncOffsetMs", 0)

            VideoSubtitleConfig(addedPaths, selectedTrackIds, position, paddingDp, syncOffsetMs)
        } catch (e: Exception) {
            e.printStackTrace()
            VideoSubtitleConfig()
        }
    }

    fun saveVideoSubtitleConfig(videoId: String, config: VideoSubtitleConfig) {
        try {
            val obj = JSONObject()
            val addedArr = JSONArray()
            config.addedPaths.forEach { addedArr.put(it) }
            obj.put("addedPaths", addedArr)

            val selArr = JSONArray()
            config.selectedTrackIds.forEach { selArr.put(it) }
            obj.put("selectedTrackIds", selArr)

            obj.put("position", config.position.name)
            obj.put("paddingDp", config.paddingDp)
            obj.put("syncOffsetMs", config.syncOffsetMs)

            prefs.edit().putString("sub_config_$videoId", obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
