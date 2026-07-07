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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.foundation.isSystemInDarkTheme
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

class MainActivity : ComponentActivity() {
    private val viewModel: NfcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
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
    var profileToDelete by remember { mutableStateOf<NfcProfile?>(null) }
    var activeQrProfile by remember { mutableStateOf<NfcProfile?>(null) }

    LaunchedEffect(activeQrProfile) {
        if (activeQrProfile != null) {
            viewModel.setActiveProfile(activeQrProfile!!)
            viewModel.setEmulationEnabled(true)
        }
    }

    if (activeQrProfile != null) {
        BackHandler {
            activeQrProfile = null
            viewModel.setEmulationEnabled(false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    } else {
                        Box(modifier = Modifier.padding(start = 12.dp)) {
                            NfcWalletLogo()
                        }
                    }
                },
                title = {
                    Text(
                        text = if (showSettings) "Settings" else "TapLink",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
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
                    containerColor = ImmersiveBg,
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
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = ImmersiveBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Dynamic NFC Status Notice Panel aligned to top only if NOT supported
            if (!isNfcSupported) {
                NfcStatusPanel(
                    isSupported = false,
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
            }

            if (!showSettings) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp)
                ) {
                    // Saved profiles List in two columns (No Stored Profiles text as requested)
                    if (profiles.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
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
                        }
                    } else {
                        items(profiles, key = { it.id }) { profile ->
                            val isActive = profile.id == activeProfileId
                            ProfileGridCard(
                                profile = profile,
                                isActive = isActive,
                                onSelect = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeQrProfile = profile
                                },
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    profileToDelete = profile
                                }
                            )
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

    // Delete Confirmation Dialog
    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Link Profile", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${profileToDelete?.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        profileToDelete?.let {
                            viewModel.deleteProfile(it)
                        }
                        profileToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF25242A),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f)
        )
    }

        if (activeQrProfile != null) {
            QrBroadcastingScreen(
                profile = activeQrProfile!!,
                onClose = {
                    activeQrProfile = null
                    viewModel.setEmulationEnabled(false)
                }
            )
        }
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
    onDelete: () -> Unit,
    onCardTap: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onCardTap != null) {
                    Modifier.clickable { onCardTap() }
                } else {
                    Modifier
                }
            )
            .testTag("active_spotlight_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF25242A)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, ImmersiveBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!profile.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.imageUrl,
                        contentDescription = "Link thumbnail",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1C24)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1C24)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Link Icon",
                            tint = ImmersivePrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "ACTIVE LINK",
                        style = MaterialTheme.typography.labelSmall,
                        color = ImmersivePrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = profile.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = profile.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = ImmersiveText.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!profile.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = profile.description,
                style = MaterialTheme.typography.bodySmall,
                color = ImmersiveText.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // HCE Emulation Switch Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1C24), shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isEmulating && isNfcEnabled) Icons.Default.Sensors else Icons.Default.SensorsOff,
                    contentDescription = null,
                    tint = if (isEmulating && isNfcEnabled) ImmersivePrimary else ImmersiveText.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (!isNfcEnabled) "NFC Is Disabled" else if (isEmulating) "HCE Broadcasting" else "Emulation Paused",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isEmulating && isNfcEnabled) Color.White else ImmersiveText.copy(alpha = 0.6f)
                    )
                    Text(
                        text = if (!isNfcEnabled) "Enable NFC in settings" else if (isEmulating) "Tap device to reader" else "Broadcasting is offline",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = ImmersiveText.copy(alpha = 0.4f)
                    )
                }
            }

            Switch(
                checked = isEmulating,
                onCheckedChange = { onEmulateToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ImmersiveOnPrimary,
                    checkedTrackColor = ImmersivePrimary,
                    uncheckedThumbColor = ImmersiveText.copy(alpha = 0.5f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier.testTag("hce_emulation_switch")
            )
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

    // Resolve Composable theme colors outside Canvas context
    val primaryColor = ImmersivePrimary
    val cardActiveColor = ImmersiveCardActive

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
                    color = primaryColor,
                    radius = radius * scale,
                    center = center,
                    alpha = alpha,
                    style = Stroke(width = 3.dp.toPx())
                )
                // Wave 2 offset
                drawCircle(
                    color = primaryColor,
                    radius = radius * ((scale + 0.35f) % 0.9f + 0.5f),
                    center = center,
                    alpha = (alpha * 1.2f).coerceIn(0f, 1f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Core emitter button
            drawCircle(
                color = cardActiveColor,
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

fun getProfileCardColor(title: String): Color {
    val lower = title.lowercase()
    return when {
        lower.contains("link") || lower.contains("tree") -> Color(0xFFD4E543) // Bright Lime Green
        lower.contains("star") || lower.contains("coffee") || lower.contains("starbucks") -> Color(0xFF1E7154) // Teal/Green
        lower.contains("indigo") || lower.contains("flight") -> Color(0xFFD0D7DE) // Slate Light Grey
        else -> {
            val hash = Math.abs(title.hashCode()) % 5
            when (hash) {
                0 -> Color(0xFFD4E543) // Lime Green
                1 -> Color(0xFF1E7154) // Emerald Teal
                2 -> Color(0xFFD0D7DE) // Slate Silver
                3 -> Color(0xFF4285F4) // Google Blue
                else -> Color(0xFFAB47BC) // Purple
            }
        }
    }
}

fun isProfileColorLight(color: Color): Boolean {
    return color == Color(0xFFD4E543) || color == Color(0xFFD0D7DE)
}

@Composable
fun NfcWalletLogo() {
    val brandColor = Color(0xFF3B4CB3) // Brand royal blue logo color
    Canvas(
        modifier = Modifier.size(36.dp)
    ) {
        val scaleX = size.width / 108f
        val scaleY = size.height / 108f

        // 1. Top-Left Loop
        val path1 = Path().apply {
            moveTo(24f * scaleX, 32f * scaleY)
            lineTo(52f * scaleX, 32f * scaleY)
            cubicTo(
                60f * scaleX, 32f * scaleY,
                64f * scaleX, 38f * scaleY,
                58f * scaleX, 46f * scaleY
            )
            lineTo(38f * scaleX, 70f * scaleY)
            cubicTo(
                32f * scaleX, 78f * scaleY,
                36f * scaleX, 84f * scaleY,
                44f * scaleX, 84f * scaleY
            )
            lineTo(52f * scaleX, 84f * scaleY)
        }
        drawPath(
            path = path1,
            color = brandColor,
            style = Stroke(
                width = 10f * scaleX,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 2. Bottom-Right Loop (rotated 180 deg around (54,54))
        val path2 = Path().apply {
            moveTo((108f - 24f) * scaleX, (108f - 32f) * scaleY)
            lineTo((108f - 52f) * scaleX, (108f - 32f) * scaleY)
            cubicTo(
                (108f - 60f) * scaleX, (108f - 32f) * scaleY,
                (108f - 64f) * scaleX, (108f - 38f) * scaleY,
                (108f - 58f) * scaleX, (108f - 46f) * scaleY
            )
            lineTo((108f - 38f) * scaleX, (108f - 70f) * scaleY)
            cubicTo(
                (108f - 32f) * scaleX, (108f - 78f) * scaleY,
                (108f - 36f) * scaleX, (108f - 84f) * scaleY,
                (108f - 44f) * scaleX, (108f - 84f) * scaleY
            )
            lineTo((108f - 52f) * scaleX, (108f - 84f) * scaleY)
        }
        drawPath(
            path = path2,
            color = brandColor,
            style = Stroke(
                width = 10f * scaleX,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 3. Central Pill
        drawLine(
            color = brandColor,
            start = androidx.compose.ui.geometry.Offset(48f * scaleX, 61.2f * scaleY),
            end = androidx.compose.ui.geometry.Offset(60f * scaleX, 46.8f * scaleY),
            strokeWidth = 10f * scaleX,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun UserAvatar(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable { onClick() }
            .background(
                androidx.compose.ui.graphics.Brush.sweepGradient(
                    listOf(
                        Color(0xFFEA4335),
                        Color(0xFFAB47BC),
                        Color(0xFF4285F4),
                        Color(0xFF34A853),
                        Color(0xFFEA4335)
                    )
                )
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFF1E1C24)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileGridCard(
    profile: NfcProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColor = ImmersiveCardBg
    val contentColor = ImmersiveText
    val subColor = ImmersiveText.copy(alpha = 0.6f)
    val strokeColor = if (isActive) ImmersivePrimary else ImmersiveBorder.copy(alpha = 0.3f)
    val strokeWidth = if (isActive) 2.5.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onDelete
            )
            .testTag("profile_card_${profile.id}"),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(strokeWidth, strokeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. OG Image / Banner Area (Flush to top and sides)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color(0xFF131216)), // Subtle ultra-dark background for image holder
                contentAlignment = Alignment.Center
            ) {
                if (!profile.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.imageUrl,
                        contentDescription = "OG Image for ${profile.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Beautiful minimalist placeholder matching the image
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = ImmersivePrimary.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Link Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = ImmersiveText.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // 2. Horizontal Divider separating top and bottom half
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ImmersiveBorder.copy(alpha = 0.5f))
            )

            // 3. Lower Section (URL Title and Link)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = profile.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                
                val cleanUrl = profile.url
                    .replace("https://", "")
                    .replace("http://", "")
                    .replace("www.", "")
                
                Text(
                    text = cleanUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

private val ImmersiveBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

private val ImmersiveText: Color
    @Composable
    get() = MaterialTheme.colorScheme.onBackground

private val ImmersivePrimary: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

private val ImmersiveOnPrimary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onPrimary

private val ImmersiveCardBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant

private val ImmersiveCardActive: Color
    @Composable
    get() = MaterialTheme.colorScheme.secondaryContainer

private val ImmersiveBorder: Color
    @Composable
    get() = MaterialTheme.colorScheme.outlineVariant

private val ImmersiveWarnBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.errorContainer

private val ImmersiveWarnText: Color
    @Composable
    get() = MaterialTheme.colorScheme.onErrorContainer

private val ImmersiveWarnBorder: Color
    @Composable
    get() = MaterialTheme.colorScheme.error

private val ImmersiveSuccessBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.primaryContainer

private val ImmersiveSuccessText: Color
    @Composable
    get() = MaterialTheme.colorScheme.onPrimaryContainer

private val ImmersiveSuccessBorder: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

@Composable
fun QrBroadcastingScreen(
    profile: NfcProfile,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImmersiveBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close / Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = ImmersiveText,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "QR & NFC Broadcast",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Beautiful Card matching the mockup exactly
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = ImmersiveCardBg),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.5.dp, ImmersiveBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Top Left Area: URL Title and Link
                    Column {
                        Text(
                            text = profile.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = profile.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ImmersiveText.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Centered Large QR Code container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Nested Card matching the mockup style
                        Card(
                            modifier = Modifier
                                .size(240.dp)
                                .aspectRatio(1f),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.5.dp, ImmersiveBorder.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val qrBitmap = remember(profile.url) {
                                    generateQrCodeBitmap(profile.url, 400)
                                }
                                if (qrBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "QR Code for ${profile.url}",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = "QR Code",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Spacer at the bottom
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Status Indicator at the bottom of the page
            Spacer(modifier = Modifier.height(16.dp))
            BroadcastingStatusIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun BroadcastingStatusIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFF10B981).copy(alpha = 0.4f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(Color(0xFF10B981), shape = androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "BROADCASTING VIA NFC & QR",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF10B981),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

fun generateQrCodeBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(
                    x, 
                    y, 
                    if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
