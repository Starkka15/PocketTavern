package com.pockettavern.app.ui.screens.theme

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pockettavern.app.ui.theme.AvatarShape
import com.pockettavern.app.ui.theme.BackgroundScaleMode
import com.pockettavern.app.ui.theme.PocketTavernColors
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeBuilderScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ThemeBuilderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showNameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collectLatest { success ->
            if (success) onSaved()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setBackgroundUri(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_theme)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNameDialog = true },
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                text = { Text(stringResource(R.string.save_theme)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Pinned preview area ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // 1. Main menu preview (with background image)
                MainMenuPreview(state = state, modifier = Modifier.fillMaxWidth())

                // 2. Avatar shape chips — between main menu and chat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.avatars),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        AvatarShape.CIRCLE to "Circle",
                        AvatarShape.ROUNDED_SQUARE to "Rounded",
                        AvatarShape.SQUARE to "Square"
                    ).forEach { (shape, label) ->
                        FilterChip(
                            selected = state.colors.avatarShape == shape,
                            onClick = { viewModel.updateColors { it.copy(avatarShape = shape) } },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. Chat preview (no background image, character name as label)
                ChatPreview(state = state, modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {

            // ── Background Image ──────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.background_image)) }
            item {
                Text(stringResource(R.string.main_menu_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                BackgroundSection(
                    state = state,
                    onPickImage = { imagePicker.launch("image/*") },
                    onClearImage = { viewModel.setBackgroundUri(null) },
                    onOpacityChange = viewModel::setBackgroundOpacity,
                    onScaleModeChange = viewModel::setBackgroundScaleMode
                )
            }

            // ── Layout ───────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.layout)) }
            item {
                ColorPickerRow("Background", state.colors.background) { c ->
                    viewModel.updateColors { it.copy(background = c) }
                }
            }
            item {
                ColorPickerRow("Surface / Cards", state.colors.surface) { c ->
                    viewModel.updateColors { it.copy(surface = c) }
                }
            }

            // ── Accent ───────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.accent)) }
            item {
                ColorPickerRow("Accent / Primary", state.colors.accentPrimary) { c ->
                    viewModel.updateColors { it.copy(accentPrimary = c) }
                }
            }
            item {
                ColorPickerRow("Border", state.colors.borderColor) { c ->
                    viewModel.updateColors { it.copy(borderColor = c) }
                }
            }

            // ── Text ─────────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.text)) }
            item {
                ColorPickerRow("Primary Text", state.colors.textPrimary) { c ->
                    viewModel.updateColors { it.copy(textPrimary = c) }
                }
            }
            item {
                ColorPickerRow("Secondary Text", state.colors.textSecondary) { c ->
                    viewModel.updateColors { it.copy(textSecondary = c) }
                }
            }
            item {
                ColorPickerRow("Quote Text", state.colors.quoteTextColor) { c ->
                    viewModel.updateColors { it.copy(quoteTextColor = c) }
                }
            }
            item {
                ColorPickerRow("Italic Text", state.colors.italicTextColor) { c ->
                    viewModel.updateColors { it.copy(italicTextColor = c) }
                }
            }
            item {
                ColorPickerRow("Code Background", state.colors.codeBackgroundColor) { c ->
                    viewModel.updateColors { it.copy(codeBackgroundColor = c) }
                }
            }

            // ── User Bubble ──────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.user_bubble)) }
            item {
                ColorPickerRow("Bubble Color", state.colors.userBubble) { c ->
                    viewModel.updateColors { it.copy(userBubble = c) }
                }
            }
            item {
                ColorPickerRow("Bubble Text", state.colors.userBubbleText) { c ->
                    viewModel.updateColors { it.copy(userBubbleText = c) }
                }
            }

            // ── Assistant Bubble ─────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.assistant_bubble)) }
            item {
                ColorPickerRow("Bubble Color", state.colors.assistantBubble) { c ->
                    viewModel.updateColors { it.copy(assistantBubble = c) }
                }
            }
            item {
                ColorPickerRow("Bubble Text", state.colors.assistantBubbleText) { c ->
                    viewModel.updateColors { it.copy(assistantBubbleText = c) }
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }   // LazyColumn
        }   // Column
    }       // Scaffold

    if (showNameDialog) {
        SaveThemeDialog(
            onDismiss = { showNameDialog = false },
            onConfirm = { name ->
                showNameDialog = false
                viewModel.saveTheme(name)
            }
        )
    }
}

// ── Preview composables ───────────────────────────────────────────────────────

@Composable
private fun MainMenuPreview(state: ThemeBuilderUiState, modifier: Modifier = Modifier) {
    val c = state.colors
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        Box(modifier = Modifier.matchParentSize().background(c.background))
        if (state.backgroundUri != null) {
            AsyncImage(
                model = state.backgroundUri,
                contentDescription = null,
                modifier = Modifier.matchParentSize().alpha(state.backgroundOpacity),
                contentScale = when (state.backgroundScaleMode) {
                    BackgroundScaleMode.FILL    -> ContentScale.Crop
                    BackgroundScaleMode.FIT     -> ContentScale.Fit
                    BackgroundScaleMode.STRETCH -> ContentScale.FillBounds
                }
            )
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.main_menu), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface.copy(alpha = 0.75f))
                    .border(1.5.dp, c.accentPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                            .background(c.accentPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(c.accentPrimary))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.characters), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(stringResource(R.string.browse_and_chat_with_your_characters),
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPreview(state: ThemeBuilderUiState, modifier: Modifier = Modifier) {
    val c = state.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.background)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.chat), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)

            // Character bubble — name label above text inside bubble, no avatar circle
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PreviewBubbleBox(isUser = false, c) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.aria),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.accentPrimary
                        )
                        Text(
                            buildAnnotatedString {
                                append("Once upon a time, ")
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = c.italicTextColor)) {
                                    append("in a land far away")
                                }
                                append(", a ")
                                withStyle(SpanStyle(color = c.quoteTextColor)) { append("\"hero\"") }
                                append(" set out.")
                            },
                            color = c.assistantBubbleText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // User bubble — right-aligned, no avatar, no name
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PreviewBubbleBox(isUser = true, c) {
                    Text(stringResource(R.string.tell_me_more), color = c.userBubbleText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PreviewBubbleBox(isUser: Boolean, c: PocketTavernColors, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .clip(
                if (isUser) RoundedCornerShape(12.dp, 2.dp, 12.dp, 12.dp)
                else        RoundedCornerShape(2.dp, 12.dp, 12.dp, 12.dp)
            )
            .background(if (isUser) c.userBubble else c.assistantBubble)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) { content() }
}

// ── Background Section ────────────────────────────────────────────────────────

@Composable
private fun BackgroundSection(
    state: ThemeBuilderUiState,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onScaleModeChange: (BackgroundScaleMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.backgroundUri != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = state.backgroundUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp, 48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.opacity_percent, (state.backgroundOpacity * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = state.backgroundOpacity,
                        onValueChange = onOpacityChange,
                        valueRange = 0.05f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(onClick = onClearImage) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_background),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    BackgroundScaleMode.FILL to "Fill",
                    BackgroundScaleMode.FIT to "Fit",
                    BackgroundScaleMode.STRETCH to "Stretch"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = state.backgroundScaleMode == mode,
                        onClick = { onScaleModeChange(mode) },
                        label = { Text(label) }
                    )
                }
            }
        } else {
            OutlinedButton(
                onClick = onPickImage,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_background_image))
            }
        }
    }
}

// ── Color Picker ──────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ColorPickerRow(
    label: String,
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.s_06x).format(color.toArgb() and 0xFFFFFF),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Box(modifier = Modifier.padding(12.dp)) {
                    HsvColorPicker(color = color, onColorChanged = onColorChanged)
                }
            }
        }
    }
}

// ── HSV Color Wheel ───────────────────────────────────────────────────────────

private val colorWheelBitmap: Bitmap by lazy {
    val size = 512
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    val cx = size / 2f
    val cy = size / 2f
    val r = cx
    for (y in 0 until size) {
        for (x in 0 until size) {
            val dx = x - cx
            val dy = y - cy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= r) {
                val hue = ((atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat() + 360f) % 360f
                val sat = (dist / r).coerceIn(0f, 1f)
                pixels[y * size + x] = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f))
            }
        }
    }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    bmp
}

@Composable
private fun HsvColorPicker(color: Color, onColorChanged: (Color) -> Unit) {
    // Initialize from current color once on entry — no key so recomposition from
    // parent (caused by our own onColorChanged calls) doesn't re-derive and jump.
    val initHsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var value by remember { mutableFloatStateOf(initHsv[2]) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Color wheel
        val wheelBmp = colorWheelBitmap

        Canvas(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        processWheelTouch(offset, size.width.toFloat(), size.height.toFloat()) { h, s ->
                            hue = h; sat = s
                            onColorChanged(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, value))))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            processWheelTouch(offset, size.width.toFloat(), size.height.toFloat()) { h, s ->
                                hue = h; sat = s
                                onColorChanged(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, value))))
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            processWheelTouch(change.position, size.width.toFloat(), size.height.toFloat()) { h, s ->
                                hue = h; sat = s
                                onColorChanged(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, value))))
                            }
                        }
                    )
                }
        ) {
            // Draw wheel bitmap scaled to canvas size
            val srcRect = android.graphics.Rect(0, 0, wheelBmp.width, wheelBmp.height)
            val dstRect = android.graphics.RectF(0f, 0f, size.width, size.height)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawBitmap(wheelBmp, srcRect, dstRect, null)
            }
            // Draw value (brightness) darkness overlay
            if (value < 1f) {
                drawCircle(Color.Black.copy(alpha = 1f - value))
            }
            // Indicator dot
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rMax = min(cx, cy)
            val angle = hue * PI.toFloat() / 180f
            val ix = cx + sat * rMax * cos(angle)
            val iy = cy + sat * rMax * sin(angle)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(ix, iy))
            drawCircle(Color.Black, radius = 9.dp.toPx(), center = Offset(ix, iy),
                style = Stroke(width = 1.5.dp.toPx()))
        }

        // Brightness slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.brightness), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(70.dp))
            Slider(
                value = value,
                onValueChange = { v ->
                    value = v
                    onColorChanged(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, v))))
                },
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }

        // Color swatch preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            )
            Text(stringResource(R.string.s_06x).format(color.toArgb() and 0xFFFFFF),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun processWheelTouch(
    offset: Offset,
    width: Float,
    height: Float,
    onHueSat: (Float, Float) -> Unit
) {
    val cx = width / 2f
    val cy = height / 2f
    val r = min(cx, cy)
    val dx = offset.x - cx
    val dy = offset.y - cy
    val dist = sqrt(dx * dx + dy * dy)
    val h = ((atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat() + 360f) % 360f
    val s = (dist / r).coerceIn(0f, 1f)
    onHueSat(h, s)
}

// ── Save Dialog ───────────────────────────────────────────────────────────────

@Composable
private fun SaveThemeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.name_your_theme)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.theme_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
