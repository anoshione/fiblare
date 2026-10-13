package com.player1.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.player1.app.ui.components.AdaptiveNavShell
import com.player1.app.ui.components.FrostedGlassAlertDialog
import com.player1.app.ui.components.LocalHazeState
import com.player1.app.ui.screens.*
import com.player1.app.ui.theme.Player1Theme
import com.player1.app.viewmodel.MainViewModel
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.CompositionLocalProvider
import com.player1.app.model.ResumeMode

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application)
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.scanVideos()
            viewModel.clearSelection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val app = application as MainApplication
        checkPermissions()

        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.vaultAuthenticated.value = false
            }
        }
        lifecycle.addObserver(lifecycleObserver)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.deleteRequestIntent.collect { pendingIntent ->
                    val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    deleteLauncher.launch(intentSenderRequest)
                }
            }
        }

        setContent {
            val appSettings by viewModel.appSettings.collectAsState()

            Player1Theme(
                themePreset = appSettings.themePreset,
                themeMode = appSettings.themeMode,
                amoledBlack = appSettings.amoledBlack,
                customSeedColor = appSettings.customSeedColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val hazeState = remember { HazeState() }
                    CompositionLocalProvider(LocalHazeState provides hazeState) {
                        val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: "library"

                    val hasChosenResumePref by viewModel.hasChosenResumePref.collectAsState()
                    val lastId by viewModel.lastResumableVideoId.collectAsState()
                    var showOneTimeResumePrompt by remember { mutableStateOf(false) }

                    val playVideoWithResumeCheck: (String) -> Unit = { id ->
                        val lastPos = app.settingsRepository.getLastPosition(id)
                        val hasLastPos = lastPos != null && (lastPos ?: 0L) > 5000L
                        if (hasLastPos && !hasChosenResumePref) {
                            viewModel.setLastResumableVideoId(id)
                            showOneTimeResumePrompt = true
                        } else {
                            navController.navigate("player/$id")
                        }
                    }

                    val onNavigate: (String) -> Unit = { route ->
                        if (route == "resume") {
                            val id = lastId
                            if (id != null) {
                                if (!hasChosenResumePref) {
                                    showOneTimeResumePrompt = true
                                } else {
                                    navController.navigate("player/$id")
                                }
                            }
                        } else {
                            navController.navigate(route)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        androidx.navigation.compose.NavHost(
                            navController = navController,
                            startDestination = "library",
                            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500)) },
                            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500)) },
                            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500)) },
                            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500)) }
                        ) {
                            composable("library") {
                                AdaptiveNavShell(viewModel, currentRoute, onNavigate = onNavigate) {
                                    LibraryScreen(viewModel, onNavigateToPlayer = playVideoWithResumeCheck)
                                }
                            }
                            composable("search") {
                                AdaptiveNavShell(viewModel, currentRoute, onNavigate = onNavigate) {
                                    SearchScreen(viewModel, onNavigateToPlayer = playVideoWithResumeCheck)
                                }
                            }

                            composable("vault") {
                                AdaptiveNavShell(viewModel, currentRoute, onNavigate = onNavigate) {
                                    VaultScreen(viewModel, onNavigateToPlayer = playVideoWithResumeCheck)
                                }
                            }
                            composable("customize") {
                                AdaptiveNavShell(viewModel, currentRoute, onNavigate = onNavigate) {
                                    ButtonCustomizationScreen(
                                        viewModel = viewModel,
                                        onNavigateToThemePicker = { navController.navigate("customize/theme") }
                                    )
                                }
                            }
                            composable("customize/theme") {
                                ThemePickerScreen(viewModel)
                            }
                            composable("settings") {
                                AdaptiveNavShell(viewModel, currentRoute, onNavigate = onNavigate) {
                                    SettingsScreen(
                                        viewModel = viewModel
                                    )
                                }
                            }
                            composable(
                                route = "player/{videoId}",
                                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                                PlayerScreen(
                                    videoId = videoId,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        if (showOneTimeResumePrompt) {
                            FrostedGlassAlertDialog(
                                onDismissRequest = { showOneTimeResumePrompt = false },
                                title = { Text("Resume Preference") },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Choose how you would like to resume video playbacks by default:")
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                viewModel.updatePlayerSettings { it.copy(resumeMode = ResumeMode.ALWAYS_RESUME) }
                                                viewModel.setHasChosenResumePref(true)
                                                showOneTimeResumePrompt = false
                                                lastId?.let { navController.navigate("player/$it") }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Resume From Last Position")
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.updatePlayerSettings { it.copy(resumeMode = ResumeMode.ALWAYS_FROM_BEGINNING) }
                                                viewModel.setHasChosenResumePref(true)
                                                showOneTimeResumePrompt = false
                                                lastId?.let { navController.navigate("player/$it") }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Start From Beginning")
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.updatePlayerSettings { it.copy(resumeMode = ResumeMode.ALWAYS_ASK) }
                                                viewModel.setHasChosenResumePref(true)
                                                showOneTimeResumePrompt = false
                                                lastId?.let { navController.navigate("player/$it") }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Always Ask")
                                        }
                                        TextButton(
                                            onClick = { showOneTimeResumePrompt = false },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                },
                                confirmButton = {}
                            )
                        }
                    }
                }
                }
            }
        }
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanVideos()
        }
    }
}
