package com.example

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NfcProfile
import com.example.ui.NfcViewModel
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    private val viewModel: NfcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                // Monitor NFC state on activity resume
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.checkNfcStatus()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ImmersiveBg
                ) {
                    NfcEmulatorApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcEmulatorApp(viewModel: NfcViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val isNfcEnabled by viewModel.isNfcEnabled.collectAsStateWithLifecycle()
    val isNfcSupported by viewModel.isNfcSupported.collectAsStateWithLifecycle()
    val isEmulating by viewModel.isEmulating.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = ImmersiveText
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(ImmersivePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = ImmersiveOnPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (showSettings) "Settings" else "TapLink",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (!showSettings) {
                                Text(
                                    text = "HCE EMULATOR ACTIVE",
                                    fontWeight = FontWeight.Bold,
                                    color = ImmersivePrimary,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = ImmersiveText
                            )
                        }
                    } else {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Settings",
                                tint = ImmersiveText
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ImmersiveCardBg,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (!showSettings) {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showAddDialog = true
                    },
                    containerColor = ImmersivePrimary,
                    contentColor = ImmersiveOnPrimary,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("add_profile_fab"),
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Card",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        containerColor = ImmersiveBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Dynamic NFC Status Notice Panel aligned to top
            NfcStatusPanel(
                isSupported = isNfcSupported,
                isEnabled = isNfcEnabled,
                onEnableClick = {
                    try {
                        val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                            context.startActivity(intent)
                        } catch (ex: Exception) {
                            // Fallback
                        }
                    }
                }
            )

            if (!showSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Active Profile Section
                    val activeProfile = profiles.find { it.id == activeProfileId }
                    if (activeProfile != null) {
                        Text(
                            text = "ACTIVE PROFILE",
                            style = MaterialTheme.typography.labelMedium,
                            color = ImmersivePrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        ActiveSpotlightCard(
                            profile = activeProfile,
                            isNfcEnabled = isNfcEnabled,
                            isEmulating = isEmulating,
                            onEmulateToggle = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setEmulationEnabled(enabled)
                            },
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deleteProfile(activeProfile)
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        NfcSignalWave(isActive = isEmulating && isNfcEnabled)
                    } else {
                        // Show default signal wave as fallback placeholder if no active profile selected
                        NfcSignalWave(isActive = false)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. Stored Profiles Section Header
                    Text(
                        text = "STORED PROFILES",
                        style = MaterialTheme.typography.labelMedium,
                        color = ImmersiveText.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    // 3. Saved profiles List
                    if (profiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inbox,
                                    contentDescription = "Empty",
                                    tint = ImmersiveText.copy(alpha = 0.2f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No stored profiles found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ImmersiveText.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = "Tap the plus action button to add profiles.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ImmersiveText.copy(alpha = 0.3f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp) // Space for FAB
                        ) {
                            items(profiles, key = { it.id }) { profile ->
                                val isActive = profile.id == activeProfileId
                                ProfileCard(
                                    profile = profile,
                                    isActive = isActive,
                                    onSelect = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setActiveProfile(profile)
                                    },
                                    onDelete = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.deleteProfile(profile)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                SettingsScreen(
                    isEmulating = isEmulating,
                    onEmulateToggle = { enabled ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setEmulationEnabled(enabled)
                    },
                    isNfcEnabled = isNfcEnabled
                )
            }
        }
    }

    // Add Profile Modal Dialog
    if (showAddDialog) {
        AddProfileDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, url ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addProfile(title, url)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun SettingsScreen(
    isEmulating: Boolean,
    onEmulateToggle: (Boolean) -> Unit,
    isNfcEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "EMULATION SETTINGS",
            style = MaterialTheme.typography.labelMedium,
            color = ImmersivePrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Power settings card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ImmersiveCardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ImmersiveBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isEmulating && isNfcEnabled) Color(0xFF10B981).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isEmulating && isNfcEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = null,
                            tint = if (isEmulating && isNfcEnabled) Color(0xFF10B981) else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "NFC Emulation Power",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEmulating && isNfcEnabled) "Active broadcasting enabled" else "Broadcasting suspended",
                            style = MaterialTheme.typography.bodySmall,
                            color = ImmersiveText.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Switch(
                    checked = isEmulating,
                    onCheckedChange = onEmulateToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ImmersiveOnPrimary,
                        checkedTrackColor = ImmersivePrimary,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "TRANSACTION LOGS",
            style = MaterialTheme.typography.labelMedium,
            color = ImmersiveText.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Logs container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = ImmersiveCardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ImmersiveBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Logs",
                        tint = ImmersivePrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Transaction Logs",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No NFC tap events recorded. Connect a reader device to begin capturing logs.",
                        color = ImmersiveText.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveSpotlightCard(
    profile: NfcProfile,
    isNfcEnabled: Boolean,
    isEmulating: Boolean,
    onEmulateToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_spotlight_card"),
        colors = CardDefaults.cardColors(containerColor = ImmersiveCardActive),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, ImmersivePrimary),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = ImmersivePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = profile.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ImmersivePrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                // Radio-like active badge matching HTML
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isEmulating) ImmersivePrimary else Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(ImmersiveOnPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Interactive Emulation Toggle Switch Badge Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .clickable { onEmulateToggle(!isEmulating) }
                        .background(if (isEmulating && isNfcEnabled) Color(0xFF10B981) else Color(0xFF6B7280))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isEmulating && isNfcEnabled) Icons.Default.Sensors else Icons.Default.SensorsOff,
                        contentDescription = if (isEmulating) "Broadcasting Active" else "Broadcasting Paused",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (!isNfcEnabled) "NFC DISABLED" else if (isEmulating) "BROADCASTING" else "PAUSED",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AID: D276...0101",
                        style = MaterialTheme.typography.labelSmall,
                        color = ImmersiveText.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_spotlight_profile")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete profile",
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NfcSignalWave(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(130.dp)) {
            val center = size.center
            val radius = size.minDimension / 2

            if (isActive) {
                // Wave 1
                drawCircle(
                    color = ImmersivePrimary,
                    radius = radius * scale,
                    center = center,
                    alpha = alpha,
                    style = Stroke(width = 3.dp.toPx())
                )
                // Wave 2 offset
                drawCircle(
                    color = ImmersivePrimary,
                    radius = radius * ((scale + 0.35f) % 0.9f + 0.5f),
                    center = center,
                    alpha = (alpha * 1.2f).coerceIn(0f, 1f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Core emitter button
            drawCircle(
                color = ImmersiveCardActive,
                radius = radius * 0.45f,
                center = center
            )
        }


    }
}

@Composable
fun NfcStatusPanel(
    isSupported: Boolean,
    isEnabled: Boolean,
    onEnableClick: () -> Unit
) {
    val containerColor = when {
        !isSupported -> ImmersiveWarnBg
        !isEnabled -> ImmersiveWarnBg
        else -> ImmersiveSuccessBg
    }

    val outlineColor = when {
        !isSupported -> ImmersiveWarnBorder
        !isEnabled -> ImmersiveWarnBorder
        else -> ImmersiveSuccessBorder
    }

    val textColor = when {
        !isSupported -> ImmersiveWarnText
        !isEnabled -> ImmersiveWarnText
        else -> ImmersiveSuccessText
    }

    val titleText = when {
        !isSupported -> "NFC UNSUPPORTED"
        !isEnabled -> "NFC IS DISABLED"
        else -> "NFC HCE EMULATION READY"
    }

    val descriptionText = when {
        !isSupported -> "This device does not support NFC card emulation hardware."
        !isEnabled -> "Enable system NFC to allow background URL broadcasting on tap."
        else -> "Your phone will broadcast the active card's URL to any NFC reader!"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nfc_status_card"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(0.dp), // Screen-aligned banner style
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        !isSupported -> Icons.Default.Error
                        !isEnabled -> Icons.Default.Warning
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSupported && !isEnabled) "NFC is currently disabled" else descriptionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isSupported && !isEnabled) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "ENABLE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier
                        .clickable { onEnableClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("enable_nfc_button")
                )
            }
        }
    }
}

@Composable
fun ProfileCard(
    profile: NfcProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = if (isActive) ImmersiveCardActive else ImmersiveCardBg
    val strokeColor = if (isActive) ImmersivePrimary else ImmersiveBorder

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("profile_card_${profile.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, strokeColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Selection Indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) ImmersivePrimary else Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = if (isActive) ImmersivePrimary else ImmersiveText.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ImmersiveOnPrimary)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = profile.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = ImmersiveText.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_profile_${profile.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Profile ${profile.title}",
                    tint = Color(0xFFEF4444).copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, url: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    
    var titleError by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Create NFC Card",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = it.isBlank()
                    },
                    label = { Text("Card Title") },
                    placeholder = { Text("e.g. Personal Website") },
                    isError = titleError,
                    supportingText = {
                        if (titleError) {
                            Text("Title cannot be empty")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ImmersivePrimary,
                        focusedLabelColor = ImmersivePrimary,
                        unfocusedBorderColor = Color.Gray,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_title_input")
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = it.isBlank()
                    },
                    label = { Text("Destination URL") },
                    placeholder = { Text("e.g. jdoe.dev/bio") },
                    isError = urlError,
                    supportingText = {
                        if (urlError) {
                            Text("URL cannot be empty")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ImmersivePrimary,
                        focusedLabelColor = ImmersivePrimary,
                        unfocusedBorderColor = Color.Gray,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isTitleBlank = title.isBlank()
                    val isUrlBlank = url.isBlank()
                    titleError = isTitleBlank
                    urlError = isUrlBlank

                    if (!isTitleBlank && !isUrlBlank) {
                        onSave(title.trim(), url.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImmersivePrimary,
                    contentColor = ImmersiveOnPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("dialog_save_button")
            ) {
                Text("SAVE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray),
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text("CANCEL", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = ImmersiveCardBg,
        textContentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("add_profile_dialog")
    )
}
