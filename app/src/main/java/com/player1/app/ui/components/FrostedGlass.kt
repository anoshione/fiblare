package com.player1.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues

val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

object FrostedGlassTracker {
    var activePopupsCount by mutableStateOf(0)
}

val SharedHazeStyle = HazeStyle(
    tint = Color.White.copy(alpha = 0.10f), // Default 10% white tint for navbar
    blurRadius = 24.dp,
    noiseFactor = 0.1f // Subtle grain noise to enhance the glass look
)

/**
 * Custom haze source modifier that forces draw invalidation
 * when the count of active popups changes, forcing Compose to redraw and clear 
 * any leftover blur backdrops.
 */
fun Modifier.hazeSource(hazeState: HazeState?): Modifier = this.then(
    if (hazeState != null) {
        Modifier
            .drawWithContent {
                // Read activePopupsCount in draw phase to force redraw on popup toggle
                val dummy = FrostedGlassTracker.activePopupsCount
                drawContent()
            }
            .haze(hazeState)
    } else {
        Modifier
    }
)

/**
 * Reusable frosted glass modifier for background elements using dev.chrisbanes.haze.
 */
@Composable
fun Modifier.frostedGlass(
    hazeState: HazeState?,
    shape: Shape = RoundedCornerShape(24.dp),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), // Matching navbar stroke
    borderWidth: Dp = 1.dp,
    opacity: Float = 0.50f, // Default to 50% opacity for dialogs/popups
    transitionAlpha: Float = 1f
): Modifier {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseTint = if (isDark) Color.Black else Color.White

    if (hazeState != null) {
        androidx.compose.runtime.DisposableEffect(hazeState) {
            FrostedGlassTracker.activePopupsCount++
            onDispose {
                FrostedGlassTracker.activePopupsCount--
            }
        }
    }

    return this.then(
        if (hazeState != null) {
            Modifier
                .hazeChild(
                    state = hazeState,
                    style = HazeStyle(
                        tint = baseTint.copy(alpha = opacity * transitionAlpha),
                        blurRadius = (24 * transitionAlpha).dp,
                        noiseFactor = 0.1f * transitionAlpha
                    ),
                    shape = shape
                )
                .border(borderWidth, borderColor.copy(alpha = borderColor.alpha * transitionAlpha), shape)
        } else {
            // Fallback to transparent tint if HazeState is not present
            Modifier
                .clip(shape)
                .background(baseTint.copy(alpha = (opacity + 0.15f) * transitionAlpha))
                .border(borderWidth, borderColor.copy(alpha = borderColor.alpha * transitionAlpha), shape)
        }
    )
}

/**
 * Reusable container that applies a premium frosted glass background.
 */
@Composable
fun FrostedGlassContainer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderColor: Color = Color.Transparent, // Will default inside frostedGlass
    borderWidth: Dp = 1.dp,
    opacity: Float = 0.50f,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = LocalHazeState.current
    Box(
        modifier = if (borderColor == Color.Transparent) {
            modifier.frostedGlass(
                hazeState = hazeState,
                shape = shape,
                borderWidth = borderWidth,
                opacity = opacity
            )
        } else {
            modifier.frostedGlass(
                hazeState = hazeState,
                shape = shape,
                borderColor = borderColor,
                borderWidth = borderWidth,
                opacity = opacity
            )
        },
        content = content
    )
}

/**
 * Custom popup position provider to position context menus.
 */
private class DropdownMenuPositionProvider(
    private val contentOffset: DpOffset,
    private val density: Density
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val xOffset = with(density) { contentOffset.x.roundToPx() }
        val yOffset = with(density) { contentOffset.y.roundToPx() }

        var x = anchorBounds.left + xOffset
        var y = anchorBounds.bottom + yOffset

        if (x + popupContentSize.width > windowSize.width) {
            x = anchorBounds.right - popupContentSize.width - xOffset
        }
        if (x < 0) {
            x = 0
        }

        if (y + popupContentSize.height > windowSize.height) {
            y = anchorBounds.top - popupContentSize.height - yOffset
        }
        if (y < 0) {
            y = 0
        }

        return IntOffset(x, y)
    }
}

/**
 * Custom double-layer-free FrostedGlassAlertDialog.
 */
@Composable
fun FrostedGlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    properties: DialogProperties = DialogProperties()
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        // Clear dim flags to prevent the background screen from darkening
        val dialogWindowProvider = androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .frostedGlass(
                    hazeState = LocalHazeState.current,
                    shape = shape,
                    opacity = 0.50f
                )
                .padding(24.dp)
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (icon != null) {
                        Box(modifier = Modifier.padding(bottom = 16.dp)) {
                            icon()
                        }
                    }
                    if (title != null) {
                        Box(modifier = Modifier.padding(bottom = 16.dp)) {
                            CompositionLocalProvider(
                                LocalTextStyle provides MaterialTheme.typography.headlineSmall
                            ) {
                                title()
                            }
                        }
                    }
                    if (text != null) {
                        Box(modifier = Modifier.padding(bottom = 24.dp)) {
                            CompositionLocalProvider(
                                LocalTextStyle provides MaterialTheme.typography.bodyMedium
                            ) {
                                text()
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
fun FrostedGlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val transitionState = androidx.compose.runtime.remember { androidx.compose.animation.core.MutableTransitionState(false) }
    transitionState.targetState = expanded

    var hasOpened by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (expanded) {
        hasOpened = true
    }

    androidx.compose.runtime.LaunchedEffect(transitionState.currentState, transitionState.targetState) {
        if (hasOpened && !transitionState.currentState && !transitionState.targetState) {
            onDismissFinished?.invoke()
            hasOpened = false
        }
    }

    if (transitionState.currentState || transitionState.targetState) {
        val transition = androidx.compose.animation.core.updateTransition(transitionState, label = "dropdown")
        val alpha by transition.animateFloat(
            transitionSpec = { androidx.compose.animation.core.tween(500) },
            label = "alpha"
        ) { isVisible ->
            if (isVisible) 1f else 0f
        }

        val density = androidx.compose.ui.platform.LocalDensity.current
        val popupPositionProvider = androidx.compose.runtime.remember(offset, density) {
            DropdownMenuPositionProvider(offset, density)
        }
        androidx.compose.ui.window.Popup(
            onDismissRequest = onDismissRequest,
            popupPositionProvider = popupPositionProvider,
            properties = properties
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Column(
                    modifier = modifier
                        .width(IntrinsicSize.Max)
                        .graphicsLayer { this.alpha = alpha }
                        .frostedGlass(
                            hazeState = LocalHazeState.current,
                            shape = RoundedCornerShape(16.dp),
                            opacity = 0.50f,
                            transitionAlpha = alpha
                        )
                        .padding(vertical = 8.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * Reusable DropdownMenuItem styled with symmetric padding and rounded pill shape.
 */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(
        textColor = MaterialTheme.colorScheme.onSurface,
        leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    ),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    androidx.compose.material3.DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp)),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    )
}
