package com.player1.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.player1.app.model.ThemeModeOption
import com.player1.app.model.ThemePreset
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState

// Custom color helpers for blending
private fun Color.darken(fraction: Float): Color {
    return Color(
        red = this.red * (1 - fraction),
        green = this.green * (1 - fraction),
        blue = this.blue * (1 - fraction),
        alpha = this.alpha
    )
}

private fun Color.lighten(fraction: Float): Color {
    return Color(
        red = this.red + (1 - this.red) * fraction,
        green = this.green + (1 - this.green) * fraction,
        blue = this.blue + (1 - this.blue) * fraction,
        alpha = this.alpha
    )
}

/**
 * ICON AND TEXT STATE STANDARD (As requested for AI reference):
 * All icons and texts used everywhere will have 3 primary states:
 * 1. Disabled: Deactivated button color (e.g., MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)).
 *    Used when an action is unavailable (like the resume button with no video).
 * 2. Active (Muted): Muted Material You/Theme color (e.g., MaterialTheme.colorScheme.onSurfaceVariant).
 *    Used for regular icons like the navbar and context menus.
 * 3. Colored (Vibrant): Most vibrant Material You/Theme color (e.g., MaterialTheme.colorScheme.primary).
 *    Used for highlighted actions and hollow stroked buttons.
 */

private fun Color.blendWith(other: Color, ratio: Float): Color {
    val inverseRatio = 1 - ratio
    return Color(
        red = this.red * inverseRatio + other.red * ratio,
        green = this.green * inverseRatio + other.green * ratio,
        blue = this.blue * inverseRatio + other.blue * ratio,
        alpha = this.alpha * inverseRatio + other.alpha * ratio
    )
}

fun getSchemeForSeed(seed: Color, isDark: Boolean): ColorScheme {
    return if (isDark) {
        val primary = seed.lighten(0.2f)
        val primaryContainer = seed.darken(0.6f)
        val onPrimaryContainer = seed.lighten(0.4f)
        
        val background = Color(0xFF121212).blendWith(seed, 0.05f)
        val surface = Color(0xFF121212).blendWith(seed, 0.08f)
        val surfaceVariant = Color(0xFF49454F).blendWith(seed, 0.2f)
        val onSurface = Color(0xFFE3E3E3).blendWith(seed, 0.1f)
        val onSurfaceVariant = Color(0xFFCAC4D0).blendWith(seed, 0.3f)
        val outline = Color(0xFF938F99).blendWith(seed, 0.2f)
        
        darkColorScheme(
            primary = primary,
            onPrimary = Color.Black,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = seed.blendWith(Color(0xFFCAC4D0), 0.6f),
            onSecondary = Color.Black,
            secondaryContainer = seed.darken(0.5f).blendWith(Color(0xFF49454F), 0.5f),
            onSecondaryContainer = seed.lighten(0.2f).blendWith(Color(0xFFCAC4D0), 0.5f),
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline
        )
    } else {
        val primary = seed.darken(0.1f)
        val primaryContainer = seed.lighten(0.7f)
        val onPrimaryContainer = seed.darken(0.4f)
        
        val background = Color(0xFFFBFCFD).blendWith(seed, 0.03f)
        val surface = Color(0xFFFBFCFD).blendWith(seed, 0.06f)
        val surfaceVariant = Color(0xFFE7E0EC).blendWith(seed, 0.15f)
        val onSurface = Color(0xFF1B1B1F).blendWith(seed, 0.1f)
        val onSurfaceVariant = Color(0xFF49454F).blendWith(seed, 0.3f)
        val outline = Color(0xFF79747E).blendWith(seed, 0.2f)

        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = seed.blendWith(Color(0xFF79747E), 0.6f),
            onSecondary = Color.White,
            secondaryContainer = seed.lighten(0.6f).blendWith(Color(0xFFE7E0EC), 0.5f),
            onSecondaryContainer = seed.darken(0.3f).blendWith(Color(0xFF49454F), 0.5f),
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline
        )
    }
}

@Composable
fun Player1Theme(
    themePreset: ThemePreset,
    themeMode: ThemeModeOption,
    amoledBlack: Boolean,
    customSeedColor: Long?,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        ThemeModeOption.DARK -> true
        ThemeModeOption.LIGHT -> false
        ThemeModeOption.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColorScheme = when {
        themePreset == ThemePreset.FOLLOW_SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themePreset == ThemePreset.CUSTOM && customSeedColor != null -> {
            getSchemeForSeed(Color(customSeedColor), darkTheme)
        }
        else -> {
            // Use specific preset or fallback to Lavender
            val seedVal = themePreset.seedColor ?: ThemePreset.LAVENDER.seedColor!!
            getSchemeForSeed(Color(seedVal), darkTheme)
        }
    }

    // Apply AMOLED black optimization if dark mode is active
    val finalColorScheme = if (darkTheme && amoledBlack) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF0C0C0C),
            surfaceContainer = Color(0xFF080808),
            surfaceContainerLow = Color(0xFF050505)
        )
    } else {
        baseColorScheme
    }

    MaterialTheme(
        colorScheme = finalColorScheme
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides FadeIndication,
            content = content
        )
    }
}

object FadeIndication : androidx.compose.foundation.Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.foundation.IndicationInstance {
        val isPressed = interactionSource.collectIsPressedAsState()
        val alphaState = androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isPressed.value) 0.3f else 1f,
            animationSpec = androidx.compose.animation.core.tween(500)
        )
        return remember(interactionSource) {
            object : androidx.compose.foundation.IndicationInstance {
                override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawIndication() {
                    val alpha = alphaState.value
                    if (alpha < 1f) {
                        drawContext.canvas.saveLayer(
                            androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, size),
                            androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha }
                        )
                        drawContent()
                        drawContext.canvas.restore()
                    } else {
                        drawContent()
                    }
                }
            }
        }
    }
}
