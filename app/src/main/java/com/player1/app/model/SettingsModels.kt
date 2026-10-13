package com.player1.app.model

enum class ThemePreset(val displayName: String, val seedColor: Long?) {
    FOLLOW_SYSTEM("Follow system", null),
    LAVENDER("Lavender", 0xFF6750A4L),
    SAKURA("Sakura", 0xFFE91E8CL),
    OCEAN("Ocean", 0xFF006494L),
    FOREST("Forest", 0xFF2E7D32L),
    SUNSET("Sunset", 0xFFE65100L),
    MIDNIGHT("Midnight", 0xFF1A237EL),
    ROSE("Rose", 0xFFB71C1CL),
    TEAL("Teal", 0xFF00695CL),
    CUSTOM("Custom", null);
}

enum class ThemeModeOption {
    DARK, SYSTEM, LIGHT
}

data class AppSettings(
    val themePreset: ThemePreset = ThemePreset.FOLLOW_SYSTEM,
    val customSeedColor: Long? = null,
    val themeMode: ThemeModeOption = ThemeModeOption.DARK,
    val amoledBlack: Boolean = false,
    val activeDecoderMode: String = "hw",
    val excludedExtensions: List<String> = emptyList(),
    val backToList: Boolean = false
)

enum class ResumeMode {
    ALWAYS_RESUME,
    ALWAYS_ASK,
    ALWAYS_FROM_BEGINNING;

    val displayName: String
        get() = when (this) {
            ALWAYS_RESUME -> "Always resume"
            ALWAYS_ASK -> "Always ask"
            ALWAYS_FROM_BEGINNING -> "From beginning"
        }
}

data class PlayerSettings(
    val seekForwardSec: Int = 10,
    val seekBackSec: Int = 10,
    val defaultSpeed: Float = 1.0f,
    val visibleNavButtons: List<String> = listOf("prev", "next"),
    val gesturesEnabled: Boolean = true,
    val resumeMode: ResumeMode = ResumeMode.ALWAYS_ASK,
    val hardwareDecoding: Boolean = true,
    val bufferSizeSec: Int = 30,
    val swipeSeekSensitivity: Int = 20
)

enum class SubtitlePosition {
    TOP, BOTTOM
}

data class SubtitleSettings(
    val syncOffsetMs: Int = 0,
    val fontSizeSp: Float = 24.0f,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
    val paddingDp: Int = 20
)

data class VideoSubtitleConfig(
    val addedPaths: List<String> = emptyList(),
    val selectedTrackIds: List<String> = emptyList(),
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
    val paddingDp: Int = 20,
    val syncOffsetMs: Int = 0
)
