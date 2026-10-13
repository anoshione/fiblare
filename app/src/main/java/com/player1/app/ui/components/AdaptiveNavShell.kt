package com.player1.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.player1.app.viewmodel.MainViewModel
import com.player1.app.ui.components.hazeSource

data class NavDestination(
    val icon: ImageVector,
    val label: String,
    val route: String
)

val destinations = listOf(
    NavDestination(Icons.Default.VideoLibrary, "Library", "library"),
    NavDestination(Icons.Default.Search, "Search", "search"),
    NavDestination(Icons.Default.PlayCircle, "Resume", "resume"),
    NavDestination(Icons.Default.Lock, "Vault", "vault"),
    NavDestination(Icons.Default.Tune, "Customization", "customize"),
    NavDestination(Icons.Default.Settings, "Settings", "settings")
)

@Composable
fun AdaptiveNavShell(
    viewModel: MainViewModel,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val hazeState = LocalHazeState.current
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600
    val lastResumableVideoId by viewModel.lastResumableVideoId.collectAsState()

    if (isWide) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                header = {}
            ) {
                Spacer(modifier = Modifier.weight(1f))
                destinations.forEach { dest ->
                    val isSelected = currentRoute == dest.route
                    val isResume = dest.route == "resume"
                    val isResumeEnabled = isResume && lastResumableVideoId != null
                    val isEnabled = if (isResume) isResumeEnabled else !isSelectionMode
                    NavigationRailItem(
                        selected = isSelected && isEnabled,
                        onClick = {
                            if (dest.route == "search" && currentRoute == "settings") {
                                viewModel.settingsSearchActive.value = true
                            } else {
                                onNavigate(dest.route)
                            }
                        },
                        icon = { Icon(dest.icon, null) },
                        label = { Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        enabled = isEnabled,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            disabledIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                        )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .hazeSource(hazeState)
            ) {
                content()
                if (isSelectionMode) {
                    SelectionNavBar(
                        viewModel = viewModel,
                        isVault = currentRoute == "vault",
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
            ) {
                content()
            }

            if (isSelectionMode) {
                SelectionNavBar(
                    viewModel = viewModel,
                    isVault = currentRoute == "vault",
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 24.dp)
                        .height(64.dp)
                        .align(Alignment.BottomCenter)
                        .frostedGlass(
                            hazeState = hazeState,
                            shape = RoundedCornerShape(40.dp),
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            opacity = 0.50f // 50% opacity for navbar
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        destinations.forEach { dest ->
                            val isSelected = currentRoute == dest.route
                            val isResume = dest.route == "resume"
                            val isResumeEnabled = isResume && lastResumableVideoId != null
                            val isClickable = !isResume || isResumeEnabled

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                val tintTarget = if (isResume && !isResumeEnabled) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val tint by androidx.compose.animation.animateColorAsState(tintTarget, androidx.compose.animation.core.tween(500))

                                val indicatorTargetColor = if (isSelected && isClickable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f)
                                val indicatorColor by androidx.compose.animation.animateColorAsState(
                                    targetValue = indicatorTargetColor,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 250,
                                        delayMillis = if (isSelected && isClickable) 250 else 0
                                    )
                                )

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(indicatorColor)
                                        .then(
                                            if (isClickable) Modifier.clickable(
                                                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                if (dest.route == "search" && currentRoute == "settings") {
                                                    viewModel.settingsSearchActive.value = true
                                                } else {
                                                    onNavigate(dest.route)
                                                }
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.label,
                                        tint = tint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
