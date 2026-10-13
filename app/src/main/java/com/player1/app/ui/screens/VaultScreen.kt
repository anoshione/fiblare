package com.player1.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.player1.app.MainApplication
import com.player1.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: MainViewModel,
    onNavigateToPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MainApplication
    val prefs = remember { context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE) }

    var hasPin by remember { mutableStateOf(prefs.getString("vault_pin", null) != null) }
    val isAuthenticated by viewModel.vaultAuthenticated.collectAsState()
    val lockedVideos by viewModel.vaultVideos.collectAsState()

    // Setup & Verification States
    var setupPhase by remember { mutableStateOf(1) } // 1: Enter PIN, 2: Confirm PIN, 3: Security Question Setup
    var tempPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var isForgotPassword by remember { mutableStateOf(false) }
    var forgotAnswerInput by remember { mutableStateOf("") }
    var isPinError by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf("") }

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedVideoIds.collectAsState()

    BackHandler(isSelectionMode) {
        viewModel.clearSelection()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.vaultAuthenticated.value = false
            viewModel.clearSelection()
        }
    }

    if (!hasPin) {
        // SETUP FLOW
        Scaffold(
            topBar = { TopAppBar(title = { Text("Setup Vault") }) },
            modifier = modifier
        ) { paddingValues ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))

                when (setupPhase) {
                    1 -> {
                        Text("Create 4-Digit PIN", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        PinDots(length = tempPin.length, isError = false)
                        Spacer(modifier = Modifier.height(32.dp))
                        PinKeypad(
                            onDigitClick = { digit ->
                                if (tempPin.length < 4) {
                                    tempPin += digit
                                    if (tempPin.length == 4) {
                                        setupPhase = 2
                                    }
                                }
                            },
                            onDeleteClick = {
                                if (tempPin.isNotEmpty()) {
                                    tempPin = tempPin.dropLast(1)
                                }
                            }
                        )
                    }
                    2 -> {
                        Text("Confirm 4-Digit PIN", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        PinDots(length = confirmPin.length, isError = setupError.isNotEmpty())
                        if (setupError.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(setupError, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        PinKeypad(
                            onDigitClick = { digit ->
                                if (confirmPin.length < 4) {
                                    confirmPin += digit
                                    if (confirmPin.length == 4) {
                                        if (confirmPin == tempPin) {
                                            setupError = ""
                                            setupPhase = 3
                                        } else {
                                            setupError = "PINs do not match. Start over."
                                            confirmPin = ""
                                            tempPin = ""
                                            setupPhase = 1
                                        }
                                    }
                                }
                            },
                            onDeleteClick = {
                                if (confirmPin.isNotEmpty()) {
                                    confirmPin = confirmPin.dropLast(1)
                                }
                            }
                        )
                    }
                    3 -> {
                        Text("Security Question", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Question: What was the name of your first pet?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = securityAnswer,
                            onValueChange = { securityAnswer = it },
                            label = { Text("Answer") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This answer will be used to recover access if you forget your PIN later.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (securityAnswer.trim().isNotEmpty()) {
                                    prefs.edit()
                                        .putString("vault_pin", tempPin)
                                        .putString("vault_security_answer", securityAnswer.trim().lowercase())
                                        .apply()
                                    hasPin = true
                                    viewModel.vaultAuthenticated.value = true
                                } else {
                                    Toast.makeText(context, "Please enter an answer", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save & Finish")
                        }
                    }
                }
            }
        }
        return
    }

    if (!isAuthenticated) {
        // UNLOCK OR FORGOT PIN FLOW
        Scaffold(
            topBar = { TopAppBar(title = { Text("Vault Access") }) },
            modifier = modifier
        ) { paddingValues ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (!isForgotPassword) {
                    Text("Enter PIN", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    PinDots(length = pinInput.length, isError = isPinError)
                    Spacer(modifier = Modifier.height(32.dp))
                    PinKeypad(
                        onDigitClick = { digit ->
                            if (pinInput.length < 4) {
                                isPinError = false
                                pinInput += digit
                                if (pinInput.length == 4) {
                                    val savedPin = prefs.getString("vault_pin", "")
                                    if (pinInput == savedPin) {
                                        viewModel.vaultAuthenticated.value = true
                                    } else {
                                        isPinError = true
                                        pinInput = ""
                                        Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onDeleteClick = {
                            if (pinInput.isNotEmpty()) {
                                pinInput = pinInput.dropLast(1)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { isForgotPassword = true }) {
                        Text("Forgot PIN?")
                    }
                } else {
                    Text("Forgot PIN", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Question: What was the name of your first pet?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = forgotAnswerInput,
                        onValueChange = { forgotAnswerInput = it },
                        label = { Text("Answer") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val savedAnswer = prefs.getString("vault_security_answer", "")
                            if (forgotAnswerInput.trim().lowercase() == savedAnswer) {
                                // Correct answer -> reset PIN
                                isForgotPassword = false
                                hasPin = false
                                setupPhase = 1
                                tempPin = ""
                                confirmPin = ""
                                securityAnswer = ""
                                pinInput = ""
                                forgotAnswerInput = ""
                            } else {
                                Toast.makeText(context, "Incorrect answer", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verify Answer")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { isForgotPassword = false }) {
                        Text("Cancel")
                    }
                }
            }
        }
        return
    }

    // MAIN VAULT SCREEN (AUTHENTICATED)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (lockedVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Vault is empty",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Long-press videos in the library to lock them",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(bottom = 80.dp)
            ) {
                items(lockedVideos, key = { it.id }) { video ->
                    val isSelected = selectedIds.contains(video.id)
                    VideoGridItem(
                        video = video,
                        app = app,
                        viewModel = viewModel,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleSelection(video.id)
                            } else {
                                onNavigateToPlayer(video.id)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                viewModel.isSelectionMode.value = true
                                viewModel.toggleSelection(video.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PinDots(
    length: Int,
    maxLength: Int = 4,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        for (i in 0 until maxLength) {
            val filled = i < length
            val color = if (isError) {
                MaterialTheme.colorScheme.error
            } else if (filled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun PinKeypad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "delete")
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(16.dp)
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.2f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isNotEmpty()) {
                            if (item == "delete") {
                                TextButton(onClick = onDeleteClick) {
                                    Text(
                                        "DEL",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onDigitClick(item) },
                                    shape = CircleShape,
                                    modifier = Modifier.size(72.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = item,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
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
