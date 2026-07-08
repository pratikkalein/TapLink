package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NfcProfile
import com.example.ui.NfcViewModel
import com.example.ui.theme.TapLinkTheme
import com.example.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    private val viewModel: NfcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

            TapLinkTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) viewModel.checkNfcStatus()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TapLinkTheme.colors.background
                ) {
                    TapLinkApp(viewModel)
                }
            }
        }
    }
}

private enum class BroadcastMode { NFC, QR }

@Composable
fun TapLinkApp(viewModel: NfcViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val isNfcEnabled by viewModel.isNfcEnabled.collectAsStateWithLifecycle()
    val isNfcSupported by viewModel.isNfcSupported.collectAsStateWithLifecycle()
    val isEmulating by viewModel.isEmulating.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showManageCategories by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var profileToDelete by remember { mutableStateOf<NfcProfile?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var broadcasting by remember { mutableStateOf<NfcProfile?>(null) }

    fun tick() {
        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(broadcasting) {
        broadcasting?.let {
            viewModel.setActiveProfile(it)
            viewModel.setEmulationEnabled(true)
        }
    }

    // Back navigation priority: broadcasting overlay > settings > default
    BackHandler(enabled = broadcasting != null) {
        broadcasting = null
        viewModel.setEmulationEnabled(false)
    }
    BackHandler(enabled = broadcasting == null && showManageCategories) { showManageCategories = false }
    BackHandler(enabled = broadcasting == null && showSettings && !showManageCategories) { showSettings = false }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showSettings) {
            SettingsScreen(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                hapticsEnabled = hapticsEnabled,
                onBack = { showSettings = false },
                onThemeChange = { viewModel.setThemeMode(it) },
                onDynamicChange = { viewModel.setDynamicColor(it) },
                onHapticsChange = { viewModel.setHapticsEnabled(it) },
                onManageCategories = { showManageCategories = true },
                onClearAll = { showClearConfirm = true }
            )
        } else {
            HomeScreen(
                profiles = profiles,
                categories = categories,
                activeProfileId = activeProfileId,
                isEmulating = isEmulating,
                isNfcSupported = isNfcSupported,
                isNfcEnabled = isNfcEnabled,
                selectedCategory = selectedCategory,
                onCategorySelect = { selectedCategory = it },
                onOpenSettings = { showSettings = true },
                onOpenTag = { tick(); broadcasting = it },
                onDeleteTag = { tick(); profileToDelete = it },
                onCreateTag = { tick(); showAddSheet = true },
                onEnableNfc = { openNfcSettings(context) }
            )
        }

        if (showManageCategories) {
            ManageCategoriesScreen(
                categories = categories,
                onBack = { showManageCategories = false },
                onAdd = { tick(); viewModel.addCategory(it) },
                onDelete = { viewModel.deleteCategory(it) },
                onReorder = { viewModel.reorderCategories(it) }
            )
        }

        broadcasting?.let { profile ->
            BroadcastingScreen(
                profile = profile,
                onClose = {
                    broadcasting = null
                    viewModel.setEmulationEnabled(false)
                }
            )
        }
    }

    if (showAddSheet) {
        NewTagSheet(
            categories = categories,
            onAddCategory = { viewModel.addCategory(it) },
            onDismiss = { showAddSheet = false },
            onSave = { title, url, category ->
                tick()
                viewModel.addProfile(title, url, category)
                showAddSheet = false
            }
        )
    }

    profileToDelete?.let { profile ->
        val colors = TapLinkTheme.colors
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete tag", fontWeight = FontWeight.Bold, color = colors.onSurface) },
            text = { Text("Remove \"${profile.title}\"?", color = colors.onSurface.copy(alpha = 0.8f)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile)
                    profileToDelete = null
                }) { Text("Delete", color = colors.danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel", color = colors.cancel)
                }
            },
            containerColor = colors.sheet
        )
    }

    if (showClearConfirm) {
        val colors = TapLinkTheme.colors
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all tags", fontWeight = FontWeight.Bold, color = colors.onSurface) },
            text = { Text("This permanently deletes every saved tag.", color = colors.onSurface.copy(alpha = 0.8f)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllTags()
                    showClearConfirm = false
                }) { Text("Clear all", color = colors.danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = colors.cancel)
                }
            },
            containerColor = colors.sheet
        )
    }
}

// ───────────────────────────── Home ─────────────────────────────

@Composable
private fun HomeScreen(
    profiles: List<NfcProfile>,
    categories: List<String>,
    activeProfileId: Int,
    isEmulating: Boolean,
    isNfcSupported: Boolean,
    isNfcEnabled: Boolean,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTag: (NfcProfile) -> Unit,
    onDeleteTag: (NfcProfile) -> Unit,
    onCreateTag: () -> Unit,
    onEnableNfc: () -> Unit,
) {
    val colors = TapLinkTheme.colors
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            HomeHeader(onOpenSettings = onOpenSettings)

            if (!isNfcSupported || !isNfcEnabled) {
                NfcNotice(
                    supported = isNfcSupported,
                    onEnable = onEnableNfc,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            if (profiles.isEmpty()) {
                EmptyState(onCreateTag = onCreateTag, navBottom = navBottom)
                return@Column
            }

            // Filter chips
            val chips = remember(profiles, categories) { categoriesFor(profiles, categories) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { cat ->
                    FilterChip(
                        label = cat,
                        selected = cat == selectedCategory,
                        onClick = { onCategorySelect(cat) }
                    )
                }
            }

            val visible = remember(profiles, selectedCategory) {
                if (selectedCategory == "All") profiles else profiles.filter { it.category == selectedCategory }
            }
            // Vertical centred card carousel: active card in the middle at full size,
            // two cards fanned above and two below, driven by the pager's scroll position.
            val cardHeight = 250.dp
            val peek = 70.dp
            val visiblePerSide = 2

            val density = LocalDensity.current
            val cardHeightPx = with(density) { cardHeight.toPx() }
            val peekPx = with(density) { peek.toPx() }

            val pagerState = rememberPagerState(pageCount = { visible.size })
            val stackHeight = cardHeight + peek * (visiblePerSide * 2)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier
                        .height(stackHeight)
                        .fillMaxWidth(),
                    pageSize = PageSize.Fixed(cardHeight),
                    pageSpacing = 0.dp,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = peek * visiblePerSide,
                        bottom = peek * visiblePerSide
                    ),
                    beyondViewportPageCount = visiblePerSide
                ) { page ->
                    val profile = visible[page]
                    val isFront = page == pagerState.currentPage
                    val isLive = profile.id == activeProfileId && isEmulating

                    val pageOffset =
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val dist = abs(pageOffset)

                    TagCard(
                        profile = profile,
                        accent = page % 2 == 1,
                        isLive = isLive,
                        height = cardHeight,
                        showDelete = isFront,
                        onClick = { if (isFront) onOpenTag(profile) },
                        onDelete = { if (isFront) onDeleteTag(profile) },
                        modifier = Modifier
                            .graphicsLayer {
                                val scale = 1f - 0.05f * dist
                                scaleX = scale
                                scaleY = scale
                                translationY = pageOffset * (cardHeightPx - peekPx)
                                alpha = ((visiblePerSide + 1f) - dist).coerceIn(0f, 1f)
                            }
                            .zIndex(-dist)
                    )
                }
            }
        }

        if (profiles.isNotEmpty()) {
            SquircleFab(
                onClick = onCreateTag,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = navBottom + 20.dp)
            )
        }
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    val colors = TapLinkTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        DisplayTitle("YOUR\nTAGS", fontSize = 62.sp)
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.iconBtnBg)
                .clickable { onOpenSettings() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Settings, "Settings", tint = colors.iconBtnTint, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun EmptyState(onCreateTag: () -> Unit, navBottom: Dp) {
    val colors = TapLinkTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .dashedRoundedBorder(colors.chipBorder, 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Contactless, null, tint = colors.bodyMuted, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Nothing to tap yet",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.display,
                letterSpacing = (-0.4).sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Save a link as a tag and your phone becomes the NFC card that shares it.",
                fontSize = 15.sp,
                color = colors.bodyMuted,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
        // CTA bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(colors.tagAddCard)
                .clickable { onCreateTag() }
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 28.dp + navBottom),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Create your first tag", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = colors.onTagAddCard, letterSpacing = (-0.4).sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Takes about ten seconds", fontSize = 13.sp, color = colors.onTagAddCard.copy(alpha = 0.55f))
                }
                AddButton()
            }
        }
    }
}

@Composable
private fun TagCard(
    profile: NfcProfile,
    accent: Boolean,
    isLive: Boolean,
    height: Dp,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = TapLinkTheme.colors
    val bg = if (accent) colors.accentCard else colors.surface
    val title = if (accent) colors.onAccentCard else colors.onSurface
    val sub = if (accent) colors.onAccentCardMuted else colors.onSurfaceMuted
    // Every card in the carousel is a standalone fixed-height card, so all four
    // corners are rounded and the height is identical for every card, including
    // the last one.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(32.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    profile.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = title,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 28.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                if (isLive) {
                    LiveBadge()
                    Spacer(Modifier.width(8.dp))
                }
                if (showDelete) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(sub.copy(alpha = 0.14f))
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete ${profile.title}",
                            tint = sub,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    subtitleFor(profile),
                    fontSize = 14.sp,
                    color = sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Icon(iconForProfile(profile), null, tint = title, modifier = Modifier.size(56.dp))
            }
        }
    }
}

@Composable
private fun SquircleFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TapLinkTheme.colors
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.tagAddCard)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "New tag",
            tint = colors.onTagAddCard,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun AddButton() {
    val colors = TapLinkTheme.colors
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.addBtnBg),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, "Create tag", tint = colors.addBtnIcon, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun LiveBadge() {
    val colors = TapLinkTheme.colors
    val transition = rememberInfiniteTransition(label = "live")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "dot"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.liveBg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { alpha = dotAlpha }
                .clip(CircleShape)
                .background(colors.live)
        )
        Text("LIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.live)
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = TapLinkTheme.colors
    val shape = RoundedCornerShape(if (selected) 12.dp else 22.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(colors.chipSelBg)
                else Modifier.border(1.5.dp, colors.chipBorder, shape)
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = colors.chipSelText, modifier = Modifier.size(18.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) colors.chipSelText else colors.chipText
        )
    }
}

@Composable
private fun NfcNotice(supported: Boolean, onEnable: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TapLinkTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.iconBtnBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, null, tint = colors.bodyMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (!supported) "NFC isn't available on this device" else "NFC is off — tags won't broadcast",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.bodyMuted,
            modifier = Modifier.weight(1f)
        )
        if (supported) {
            Text(
                "ENABLE",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.display,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onEnable() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// ───────────────────────────── New tag sheet ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTagSheet(
    categories: List<String>,
    onAddCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String, category: String) -> Unit,
) {
    val colors = TapLinkTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(0) } // 1 = title, 2 = url

    var category by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var addingCategory by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }

    // Keep a valid selection when the category list changes underneath us.
    LaunchedEffect(categories) {
        if (category.isBlank() || category !in categories) {
            category = categories.firstOrNull() ?: ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.sheet,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.handle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "NEW TAG",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = colors.onSurface,
                letterSpacing = (-1.5).sp,
                lineHeight = 44.sp
            )

            SheetField(
                label = "TITLE",
                value = title,
                onValueChange = { title = it },
                placeholder = "Portfolio",
                focused = focused == 1,
                onFocusChange = { focused = if (it) 1 else 0 }
            )
            SheetField(
                label = "DESTINATION URL",
                value = url,
                onValueChange = { url = it; urlError = false },
                placeholder = "https://",
                keyboardType = KeyboardType.Uri,
                isError = urlError,
                focused = focused == 2,
                onFocusChange = { focused = if (it) 2 else 0 }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { opt ->
                    SheetCategoryChip(
                        label = opt,
                        selected = opt == category,
                        onClick = { category = opt }
                    )
                }
                // Inline "add new category" chip.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, colors.fieldBorder, RoundedCornerShape(20.dp))
                        .clickable { addingCategory = !addingCategory }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Add, null, tint = colors.fieldLabel, modifier = Modifier.size(16.dp))
                    Text("New", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.fieldLabel)
                }
            }

            if (addingCategory) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SheetField(
                            label = "NEW CATEGORY",
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            placeholder = "e.g. Work",
                            focused = true,
                            onFocusChange = {}
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.primaryBtn)
                            .clickable {
                                val name = newCategory.trim()
                                if (name.isNotEmpty()) {
                                    onAddCategory(name)
                                    category = name
                                    newCategory = ""
                                    addingCategory = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, null, tint = colors.onPrimaryBtn, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(29.dp))
                        .background(colors.primaryBtn)
                        .clickable {
                            if (isValidUrl(url)) onSave(title.trim(), url.trim(), category)
                            else urlError = true
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Save, null, tint = colors.onPrimaryBtn, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save tag", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = colors.onPrimaryBtn)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.cancel)
                }
            }
        }
    }
}

@Composable
private fun SheetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
) {
    val colors = TapLinkTheme.colors
    val active = focused || isError
    val borderColor = when {
        isError -> colors.danger
        active -> colors.fieldActiveBorder
        else -> colors.fieldBorder
    }
    val labelColor = if (active) colors.fieldActiveBorder else colors.fieldLabel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (active) 2.dp else 1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = labelColor)
        Spacer(Modifier.height(2.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.fieldValue, fontSize = 17.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.fieldActiveBorder),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.isFocused) },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 17.sp, color = colors.fieldPlaceholder)
                }
                inner()
            }
        )
    }
}

@Composable
private fun SheetCategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = TapLinkTheme.colors
    val shape = RoundedCornerShape(if (selected) 12.dp else 20.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(colors.fieldActiveBorder)
                else Modifier.border(1.5.dp, colors.fieldBorder, shape)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else colors.fieldLabel
        )
    }
}

// ───────────────────────────── Broadcasting / QR ─────────────────────────────

@Composable
private fun BroadcastingScreen(profile: NfcProfile, onClose: () -> Unit) {
    val colors = TapLinkTheme.colors
    val context = LocalContext.current
    var mode by remember { mutableStateOf(BroadcastMode.NFC) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: back + NFC/QR toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.iconBtnTint, modifier = Modifier.size(24.dp))
                }
                SegmentedToggle(
                    options = listOf("NFC", "QR"),
                    selectedIndex = if (mode == BroadcastMode.NFC) 0 else 1,
                    onSelect = { mode = if (it == 0) BroadcastMode.NFC else BroadcastMode.QR }
                )
                Spacer(Modifier.size(48.dp))
            }

            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                DisplayTitle(if (mode == BroadcastMode.NFC) "READY\nTO TAP" else "SCAN\nME", fontSize = 54.sp)
            }

            if (mode == BroadcastMode.NFC) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RadarPulse(color = colors.pulse)
                    BroadcastCard(profile)
                }
                StopBar(onClose = onClose)
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    QrCard(profile)
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = colors.bodyMuted, modifier = Modifier.size(18.dp))
                        Text("Point any camera at the code", fontSize = 13.sp, color = colors.bodyMuted)
                    }
                }
                ShareSaveBar(
                    onShare = { shareUrl(context, profile.url) },
                    onSave = { saveQrToGallery(context, profile.url) }
                )
            }
        }
    }
}

@Composable
private fun BroadcastCard(profile: NfcProfile) {
    val colors = TapLinkTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(colors.accentCard)
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    profile.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onAccentCard,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                LiveBadge()
            }
            Spacer(Modifier.height(34.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    subtitleFor(profile),
                    fontSize = 14.sp,
                    color = colors.onAccentCardMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Filled.Contactless, null, tint = colors.onAccentCard, modifier = Modifier.size(56.dp))
            }
        }
    }
}

@Composable
private fun QrCard(profile: NfcProfile) {
    val qrBitmap = remember(profile.url) { generateQrCodeBitmap(profile.url, 600) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFFFAF9FD))
            .padding(28.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(profile.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B1B1F), letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.QrCode2, null, tint = Color(0xFF66676F), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(18.dp))
            Box(contentAlignment = Alignment.Center) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR code for ${profile.url}",
                        modifier = Modifier.size(230.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                        Text("QR", color = Color(0xFF141414), fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFAF9FD)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Contactless, null, tint = Color(0xFF141414), modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(cleanUrl(profile.url), fontSize = 13.sp, color = Color(0xFF8F909A))
        }
    }
}

@Composable
private fun RadarPulse(color: Color) {
    val transition = rememberInfiniteTransition(label = "radar")
    val spec = infiniteRepeatable<Float>(tween(2400, easing = LinearEasing), RepeatMode.Restart)
    val s1 by transition.animateFloat(1f, 2.1f, spec, label = "s1")
    val a1 by transition.animateFloat(0.55f, 0f, spec, label = "a1")
    val s2 by transition.animateFloat(1f, 2.1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart, StartOffset(1200)), label = "s2")
    val a2 by transition.animateFloat(0.55f, 0f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart, StartOffset(1200)), label = "a2")
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer { scaleX = s1; scaleY = s1; alpha = a1 }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer { scaleX = s2; scaleY = s2; alpha = a2 }
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun StopBar(onClose: () -> Unit) {
    val colors = TapLinkTheme.colors
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(colors.tagAddCard)
            .clickable { onClose() }
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + navBottom)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stop broadcasting", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = colors.onTagAddCard, letterSpacing = (-0.4).sp)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.addBtnBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Stop, null, tint = colors.addBtnIcon, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun ShareSaveBar(onShare: () -> Unit, onSave: () -> Unit) {
    val colors = TapLinkTheme.colors
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BottomActionButton(Icons.Filled.Share, "Share", Modifier.weight(1f), navBottom, onShare)
        BottomActionButton(Icons.Filled.Download, "Save", Modifier.weight(1f), navBottom, onSave)
    }
}

@Composable
private fun BottomActionButton(icon: ImageVector, label: String, modifier: Modifier, navBottom: Dp, onClick: () -> Unit) {
    val colors = TapLinkTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(colors.barSurface)
            .clickable { onClick() }
            .padding(top = 22.dp, bottom = 22.dp + navBottom),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.onBarSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = colors.onBarSurface)
    }
}

// ───────────────────────────── Settings ─────────────────────────────

@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onManageCategories: () -> Unit,
    onClearAll: () -> Unit,
) {
    val colors = TapLinkTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.iconBtnBg)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.iconBtnTint, modifier = Modifier.size(24.dp))
            }
        }
        Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)) {
            DisplayTitle("SETTINGS", fontSize = 52.sp)
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Theme
            SettingRow(
                icon = Icons.Filled.Brightness6,
                title = "Theme",
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
            ) {
                SegmentedToggle(
                    options = listOf("Light", "Auto", "Dark"),
                    selectedIndex = when (themeMode) {
                        ThemeMode.LIGHT -> 0; ThemeMode.AUTO -> 1; ThemeMode.DARK -> 2
                    },
                    onSelect = { onThemeChange(ThemeMode.values()[it]) },
                    small = true
                )
            }
            // Dynamic color
            SettingRow(
                icon = Icons.Filled.Palette,
                title = "Dynamic color",
                subtitle = "Follow wallpaper palette",
                shape = RoundedCornerShape(12.dp)
            ) {
                TapLinkSwitch(checked = dynamicColor, onCheckedChange = onDynamicChange)
            }
            // Haptics
            SettingRow(
                icon = Icons.Filled.Vibration,
                title = "Haptics on read",
                shape = RoundedCornerShape(12.dp)
            ) {
                TapLinkSwitch(checked = hapticsEnabled, onCheckedChange = onHapticsChange)
            }
            // Manage categories
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .clickable { onManageCategories() }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Filled.Sell, null, tint = colors.fieldLabel, modifier = Modifier.size(26.dp))
                Text(
                    "Manage categories",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.fieldLabel, modifier = Modifier.size(20.dp))
            }
            // Clear all
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(colors.surface)
                    .clickable { onClearAll() }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Filled.Delete, null, tint = colors.danger, modifier = Modifier.size(26.dp))
                Text("Clear all tags", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.danger)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "TapLink 2.0 · NFC HCE emulation",
                fontSize = 13.sp,
                color = colors.footer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )
            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

// ───────────────────────────── Manage categories ─────────────────────────────

@Composable
private fun ManageCategoriesScreen(
    categories: List<String>,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val colors = TapLinkTheme.colors
    val density = LocalDensity.current

    val itemHeight = 56.dp
    val gap = 10.dp
    val slotPx = with(density) { (itemHeight + gap).toPx() }

    // Local working copy so drag reordering is smooth; resets whenever the
    // persisted list changes (add / delete / committed reorder).
    var working by remember(categories) { mutableStateOf(categories) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var newCategory by remember { mutableStateOf("") }

    val targetIndex = draggingIndex?.let {
        (it + (dragOffset / slotPx).roundToInt()).coerceIn(0, working.lastIndex.coerceAtLeast(0))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.iconBtnBg)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.iconBtnTint, modifier = Modifier.size(24.dp))
                }
            }
            Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)) {
                DisplayTitle("CATEGORIES", fontSize = 44.sp)
            }

            // Add field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.5.dp, colors.fieldBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.fieldValue, fontSize = 16.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.fieldActiveBorder),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (newCategory.isEmpty()) {
                                Text("Add a category", fontSize = 16.sp, color = colors.fieldPlaceholder)
                            }
                            inner()
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.primaryBtn)
                        .clickable {
                            val name = newCategory.trim()
                            if (name.isNotEmpty()) {
                                onAdd(name)
                                newCategory = ""
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, "Add category", tint = colors.onPrimaryBtn, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            if (working.isEmpty()) {
                Text(
                    "No categories yet. Add one above.",
                    fontSize = 14.sp,
                    color = colors.bodyMuted,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    working.forEachIndexed { index, name ->
                        val dragging = draggingIndex == index
                        val translation = when {
                            dragging -> dragOffset
                            draggingIndex != null && targetIndex != null -> {
                                val d = draggingIndex!!
                                val t = targetIndex
                                when {
                                    d < t && index in (d + 1)..t -> -slotPx
                                    d > t && index in t until d -> slotPx
                                    else -> 0f
                                }
                            }
                            else -> 0f
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = translation }
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (dragging) colors.accentCard else colors.surface)
                                .padding(start = 6.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                "Reorder",
                                tint = if (dragging) colors.onAccentCardMuted else colors.fieldLabel,
                                modifier = Modifier
                                    .size(44.dp)
                                    .padding(10.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingIndex = index
                                                dragOffset = 0f
                                            },
                                            onDragEnd = {
                                                val d = draggingIndex
                                                if (d != null) {
                                                    val t = (d + (dragOffset / slotPx).roundToInt())
                                                        .coerceIn(0, working.lastIndex)
                                                    if (t != d) {
                                                        val newList = working.toMutableList()
                                                            .also { it.add(t, it.removeAt(d)) }
                                                        working = newList
                                                        onReorder(newList)
                                                    }
                                                }
                                                draggingIndex = null
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggingIndex = null
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount.y
                                            }
                                        )
                                    }
                            )
                            Text(
                                name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (dragging) colors.onAccentCard else colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { onDelete(name) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    "Delete $name",
                                    tint = if (dragging) colors.onAccentCardMuted else colors.danger,
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

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    shape: RoundedCornerShape,
    trailing: @Composable () -> Unit,
) {
    val colors = TapLinkTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = colors.fieldLabel, modifier = Modifier.size(26.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = colors.fieldLabel)
        }
        trailing()
    }
}

@Composable
private fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    small: Boolean = false,
) {
    val colors = TapLinkTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(if (small) 18.dp else 22.dp))
            .background(colors.segBg)
            .padding(if (small) 4.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(if (small) 0.dp else 8.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(if (small) 14.dp else 16.dp))
                    .then(if (selected) Modifier.background(colors.segSelBg) else Modifier)
                    .clickable { onSelect(index) }
                    .padding(horizontal = if (small) 14.dp else 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) colors.segSelText else colors.segText
                )
            }
        }
    }
}

@Composable
private fun TapLinkSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = TapLinkTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = if (checked) {
            { Icon(Icons.Filled.Check, null, tint = colors.toggleOn, modifier = Modifier.size(16.dp)) }
        } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.toggleThumb,
            checkedTrackColor = colors.toggleOn,
            checkedBorderColor = colors.toggleOn,
            uncheckedThumbColor = colors.iconBtnTint,
            uncheckedTrackColor = colors.segBg,
            uncheckedBorderColor = colors.chipBorder
        )
    )
}

// ───────────────────────────── Shared bits ─────────────────────────────

@Composable
private fun DisplayTitle(text: String, fontSize: TextUnit) {
    val colors = TapLinkTheme.colors
    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = (fontSize.value * 0.92f).sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-2).sp,
        color = colors.display
    )
}

private fun Modifier.dashedRoundedBorder(color: Color, radius: Dp) = this.drawBehind {
    val stroke = Stroke(
        width = 2.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
    )
    val r = radius.toPx()
    drawRoundRect(
        color = color,
        style = stroke,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
    )
}

private fun categoriesFor(profiles: List<NfcProfile>, categories: List<String>): List<String> {
    // User-managed categories keep their order; any category a tag uses but that
    // isn't in the managed list is appended so every tag stays reachable via a chip.
    val orphans = profiles.map { it.category }.filter { it !in categories }.distinct().sorted()
    return listOf("All") + categories + orphans
}

private fun iconForProfile(profile: NfcProfile): ImageVector {
    val url = profile.url.lowercase()
    return when {
        url.contains("youtu") -> Icons.Filled.SmartDisplay
        profile.category.equals("Social", ignoreCase = true) -> Icons.Filled.Group
        else -> Icons.Filled.Language
    }
}

private fun cleanUrl(url: String): String =
    url.replace("https://", "").replace("http://", "").replace("www.", "").trimEnd('/')

private fun subtitleFor(profile: NfcProfile): String {
    val host = cleanUrl(profile.url)
    return if (profile.category.isBlank()) host else "$host · ${profile.category}"
}

private fun isValidUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return false
    val regex = "^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d+)?(/\\S*)?$".toRegex()
    return regex.matches(trimmed)
}

private fun openNfcSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        } catch (_: Exception) {
        }
    }
}

private fun shareUrl(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, "Share link"))
}

private fun saveQrToGallery(context: Context, url: String) {
    val bmp = generateQrCodeBitmap(url, 800)
    if (bmp == null) {
        Toast.makeText(context, "Couldn't generate QR", Toast.LENGTH_SHORT).show()
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "taplink-qr-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TapLink")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(context, "Saved to Photos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Couldn't save QR", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Saving needs Android 10+", Toast.LENGTH_SHORT).show()
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
                    x, y,
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
