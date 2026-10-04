package com.example.filmlab

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val model: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                EditorScreen(model)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        model.commit() // save the latest change when the app is left
    }
}

private val panelColor = Color(0xFF121212)

@Composable
fun EditorScreen(model: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) model.load(uri)
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    var showingCredits by remember { mutableStateOf(false) }

    LaunchedEffect(model.message) {
        model.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            model.message = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = pickPhoto) { Text("Photos") }
            TextButton(onClick = model::undo, enabled = model.canUndo) { Text("↶", fontSize = 22.sp) }
            TextButton(onClick = model::redo, enabled = model.canRedo) { Text("↷", fontSize = 22.sp) }
            Spacer(Modifier.weight(1f))
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                TextButton(onClick = { menuOpen = true }) { Text("•••") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Revert to Original") },
                        enabled = model.hasPhoto && model.edit != EditState(),
                        onClick = {
                            menuOpen = false
                            model.revertToOriginal()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Credits") },
                        onClick = {
                            menuOpen = false
                            showingCredits = true
                        },
                    )
                }
            }
            if (model.isExporting) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = model::export, enabled = model.hasPhoto) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Preview(model, pickPhoto)
        }

        if (model.hasPhoto) {
            Controls(model)
        }
    }

    if (showingCredits) {
        CreditsDialog(onDismiss = { showingCredits = false })
    }
}

@Composable
private fun Preview(model: EditorViewModel, pickPhoto: () -> Unit) {
    var showingOriginal by remember { mutableStateOf(false) }
    val image = if (showingOriginal) model.original else model.preview
    when {
        image != null && model.isCropping -> CropEditor(image, model.edit.crop.rect, model.cropRatio) { rect ->
            model.update(model.edit.copy(crop = model.edit.crop.copy(rect = rect)))
        }
        image != null -> Box(contentAlignment = Alignment.TopCenter) {
            Image(
                bitmap = image,
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    // Press and hold to compare with the original.
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            showingOriginal = true
                            tryAwaitRelease()
                            showingOriginal = false
                        })
                    },
            )
            if (showingOriginal) {
                Text(
                    "Original",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .background(Color(0x99000000), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        model.isLoading -> CircularProgressIndicator()
        else -> Button(onClick = pickPhoto) { Text("Choose a Photo") }
    }
}

@Composable
private fun Controls(model: EditorViewModel) {
    var tab by remember { mutableStateOf(0) }
    var adjustment by remember { mutableStateOf<Adjustment?>(Adjustment.Exposure) }
    LaunchedEffect(tab) { model.showCropBox(tab == 3) }

    Column(
        Modifier
            .fillMaxWidth()
            .height(290.dp)
            .background(panelColor)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        when (tab) {
            0 -> LooksPanel(model)
            1 -> AdjustPanel(model, adjustment) { adjustment = it }
            2 -> EffectsPanel(model)
            3 -> CropPanel(model)
            else -> FramePanel(model)
        }
        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = tab, containerColor = panelColor) {
            listOf("Looks", "Adjust", "Effects", "Crop", "Frame").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title, maxLines = 1, fontSize = 13.sp) })
            }
        }
    }
}

@Composable
private fun LooksPanel(model: EditorViewModel) {
    val edit = model.edit
    var naming by remember { mutableStateOf<PresetNaming?>(null) }
    var categoryName by remember { mutableStateOf<String?>(null) }
    val category = model.categories.firstOrNull { it.name == categoryName } ?: model.categories.firstOrNull()
    val lutsInCategory = category?.lutIds.orEmpty().mapNotNull { id -> model.luts.firstOrNull { it.id == id } }
    naming?.let { current ->
        PresetNameDialog(
            title = if (current.preset == null) "Save Preset" else "Rename Preset",
            initial = current.name,
            onDismiss = { naming = null },
        ) { name ->
            naming = null
            if (current.preset == null) model.savePreset(name) else model.renamePreset(current.preset, name)
        }
    }
    Column {
        if (edit.lutId != null) {
            LabeledSlider("Strength", edit.intensity, 0f..1f) { model.update(edit.copy(intensity = it)) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(model.categories, key = { it.name }) { c ->
                ToolChip(c.name, c == category, changed = false) { categoryName = c.name }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LookTile("Original", model.originalThumbnail, edit.lutId == null) {
                    model.update(edit.copy(lutId = null, intensity = 1f))
                }
            }
            item { NewPresetTile { naming = PresetNaming(null, "") } }
            items(model.presets, key = { it.id }) { preset ->
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    LookTile(
                        preset.name, model.presetThumbnails[preset.id], model.isApplied(preset),
                        onLongClick = { menuOpen = true },
                    ) { model.applyPreset(preset) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = {
                            menuOpen = false
                            naming = PresetNaming(preset, preset.name)
                        })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            menuOpen = false
                            model.deletePreset(preset)
                        })
                    }
                }
            }
            item {
                Box(Modifier.padding(top = 6.dp).size(1.dp, 56.dp).background(Color(0xFF404040)))
            }
            items(lutsInCategory, key = { it.id }) { lut ->
                LookTile(lut.name, model.thumbnails[lut.id], edit.lutId == lut.id) {
                    if (edit.lutId != lut.id) model.update(edit.copy(lutId = lut.id, intensity = 1f))
                }
            }
        }
    }
}

/** Which preset is being named: null for a new one. */
private data class PresetNaming(val preset: Preset?, val name: String)

@Composable
private fun PresetNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                Spacer(Modifier.height(8.dp))
                Text("Saves the look, adjustments and effects. Crop isn't included.", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewPresetTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Canvas(Modifier.size(68.dp)) {
            drawRoundRect(
                color = Color(0xFF666666),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))),
            )
            val c = center
            val arm = 9.dp.toPx()
            drawLine(Color.Gray, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = 2.dp.toPx())
            drawLine(Color.Gray, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = 2.dp.toPx())
        }
        Spacer(Modifier.height(6.dp))
        Text("New", fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun LookTile(
    name: String,
    thumbnail: ImageBitmap?,
    selected: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val press = if (onLongClick == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { currentOnClick() },
                onLongPress = { currentOnLongClick?.invoke() },
            )
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = press,
    ) {
        val shape = RoundedCornerShape(6.dp)
        val tile = Modifier
            .size(68.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, if (selected) Color.White else Color.Transparent), shape)
        if (thumbnail != null) {
            Image(thumbnail, contentDescription = name, contentScale = ContentScale.Crop, modifier = tile)
        } else {
            Box(tile.background(Color(0xFF333333)))
        }
        Spacer(Modifier.height(6.dp))
        Text(name, fontSize = 11.sp, color = if (selected) Color.White else Color.Gray)
    }
}

/** [adjustment] null means the HSL tool is showing. */
@Composable
private fun AdjustPanel(model: EditorViewModel, adjustment: Adjustment?, onSelect: (Adjustment?) -> Unit) {
    val edit = model.edit
    Column {
        if (adjustment == null) {
            HslControls(model)
        } else {
            LabeledSlider(adjustment.title, adjustment.get(edit), adjustment.range) {
                model.update(adjustment.set(edit, it))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(Adjustment.entries) { item ->
                ToolChip(item.title, item == adjustment, item.get(edit) != 0f) { onSelect(item) }
            }
            item { ToolChip("HSL", adjustment == null, edit.hasHsl) { onSelect(null) } }
            item {
                TextButton(
                    onClick = { model.update(edit.withoutAdjustments()) },
                    enabled = edit != edit.withoutAdjustments(),
                ) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun ToolChip(title: String, selected: Boolean, changed: Boolean, onClick: () -> Unit) {
    Text(
        if (changed) "$title •" else title,
        fontSize = 13.sp,
        color = if (selected) Color.White else Color.Gray,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color(0xFF3A3A3A) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun HslControls(model: EditorViewModel) {
    var band by remember { mutableStateOf(HslBand.Red) }
    val edit = model.edit
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            HslBand.entries.forEach { b ->
                Box(
                    Modifier
                        .size(28.dp)
                        .border(2.dp, if (b == band) Color.White else Color.Transparent, CircleShape)
                        .padding(4.dp)
                        .background(Color.hsv(b.hue, 0.75f, 0.95f), CircleShape)
                        .clickable { band = b },
                )
            }
        }
        HslSlider("Hue", edit.hslHue[band.ordinal]) {
            model.update(edit.copy(hslHue = edit.hslHue.toMutableList().also { list -> list[band.ordinal] = it }))
        }
        HslSlider("Saturation", edit.hslSaturation[band.ordinal]) {
            model.update(edit.copy(hslSaturation = edit.hslSaturation.toMutableList().also { list -> list[band.ordinal] = it }))
        }
        HslSlider("Luminance", edit.hslLuminance[band.ordinal]) {
            model.update(edit.copy(hslLuminance = edit.hslLuminance.toMutableList().also { list -> list[band.ordinal] = it }))
        }
    }
}

@Composable
private fun HslSlider(title: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(72.dp))
        Slider(value = value, onValueChange = onChange, valueRange = -1f..1f, modifier = Modifier.weight(1f))
        Text(
            "${(value * 100).roundToInt()}",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun EffectsPanel(model: EditorViewModel) {
    val edit = model.edit
    Column {
        if (edit.leak != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                LabeledSlider("Light Leak", edit.leakAmount, 0f..1f, Modifier.weight(1f)) {
                    model.update(edit.copy(leakAmount = it))
                }
                TextButton(
                    onClick = { model.update(edit.copy(leakPlacement = (edit.leakPlacement + 1) % LightLeak.PLACEMENT_COUNT)) },
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("Shift") }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LookTile("None", model.originalThumbnail, edit.leak == null) {
                    model.update(edit.copy(leak = null))
                }
            }
            items(LightLeak.styles, key = { it.id }) { style ->
                LookTile(style.name, model.leakThumbnails[style.id], edit.leak == style.id) {
                    if (edit.leak != style.id) model.update(edit.copy(leak = style.id, leakAmount = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun FramePanel(model: EditorViewModel) {
    val edit = model.edit
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(FrameStyle.entries) { style ->
                ToolChip(style.title, edit.frame == style, changed = false) { model.update(edit.copy(frame = style)) }
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrameColor.entries.forEach { color ->
                val selected = edit.frameColor == color
                val enabled = edit.frame.hasColor
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = enabled) { model.update(edit.copy(frameColor = color)) },
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .border(2.dp, if (selected && enabled) Color.White else Color.Transparent, CircleShape)
                            .padding(3.dp)
                            .background(Color(color.argb), CircleShape)
                            .border(1.dp, Color.Gray, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(color.title, fontSize = 12.sp, color = if (enabled) Color.White else Color.DarkGray)
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Date stamp", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.width(8.dp))
            Text(model.dateText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9E40))
            Spacer(Modifier.weight(1f))
            Switch(checked = edit.dateStamp, onCheckedChange = { model.update(edit.copy(dateStamp = it)) })
        }
    }
}

@Composable
private fun CropPanel(model: EditorViewModel) {
    val crop = model.edit.crop
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            LabeledSlider(
                "Straighten", crop.straighten, -20f..20f, Modifier.weight(1f),
                format = { "%.1f°".format(it) },
            ) { model.update(model.edit.copy(crop = crop.copy(straighten = it))) }
            TextButton(onClick = model::rotateClockwise) { Text("Rotate") }
            TextButton(onClick = model::flip, modifier = Modifier.padding(end = 8.dp)) { Text("Flip") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(CropAspect.entries) { aspect ->
                val selected = crop.aspect == aspect
                Text(
                    aspect.title,
                    fontSize = 13.sp,
                    color = if (selected) Color.White else Color.Gray,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) Color(0xFF3A3A3A) else Color.Transparent)
                        .clickable { model.setAspect(aspect) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            item {
                TextButton(onClick = model::resetCrop, enabled = crop != CropState()) { Text("Reset") }
            }
        }
    }
}

/** The whole straightened photo with a crop box that can be moved and resized. */
@Composable
private fun CropEditor(image: ImageBitmap, rect: NRect, ratio: Float?, onChange: (NRect) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        val aspect = image.width.toFloat() / image.height
        val boxWidth = if (maxWidth / maxHeight > aspect) maxHeight * aspect else maxWidth
        Box(Modifier.size(boxWidth, boxWidth / aspect)) {
            Image(image, contentDescription = "Photo", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            CropBox(rect, ratio, onChange)
        }
    }
}

@Composable
private fun CropBox(rect: NRect, ratio: Float?, onChange: (NRect) -> Unit) {
    val currentRect by rememberUpdatedState(rect)
    val currentRatio by rememberUpdatedState(ratio)
    val currentOnChange by rememberUpdatedState(onChange)
    val handleReach = with(LocalDensity.current) { 32.dp.toPx() }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var start = NRect.Full
                var target = -1 // corner 0-3, 4 to move the box, -1 for nothing
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { position ->
                        start = currentRect
                        total = Offset.Zero
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val corners = listOf(
                            Offset(start.left * w, start.top * h),
                            Offset(start.right * w, start.top * h),
                            Offset(start.right * w, start.bottom * h),
                            Offset(start.left * w, start.bottom * h),
                        )
                        target = corners.indexOfFirst { (it - position).getDistance() <= handleReach }
                        val inside = position.x in start.left * w..start.right * w &&
                            position.y in start.top * h..start.bottom * h
                        if (target < 0 && inside) target = 4
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        total += amount
                        val dx = total.x / size.width
                        val dy = total.y / size.height
                        when (target) {
                            in 0..3 -> currentOnChange(CropMath.dragCorner(target, start, dx, dy, currentRatio))
                            4 -> currentOnChange(CropMath.move(start, dx, dy))
                        }
                    },
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val l = rect.left * w
        val t = rect.top * h
        val r = rect.right * w
        val b = rect.bottom * h
        val dim = Color.Black.copy(alpha = 0.6f)
        drawRect(dim, Offset.Zero, Size(w, t))
        drawRect(dim, Offset(0f, b), Size(w, h - b))
        drawRect(dim, Offset(0f, t), Size(l, b - t))
        drawRect(dim, Offset(r, t), Size(w - r, b - t))

        val grid = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            val x = l + (r - l) * i / 3
            val y = t + (b - t) * i / 3
            drawLine(grid, Offset(x, t), Offset(x, b), strokeWidth = 1f)
            drawLine(grid, Offset(l, y), Offset(r, y), strokeWidth = 1f)
        }
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 1.5.dp.toPx()))
        for (corner in listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b))) {
            drawCircle(Color.White, radius = 7.dp.toPx(), center = corner)
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    format: (Float) -> String = { "${(it * 100).roundToInt()}" },
    onChange: (Float) -> Unit,
) {
    Column(modifier.padding(horizontal = 16.dp)) {
        Row {
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.weight(1f))
            Text(format(value), fontSize = 12.sp, color = Color.Gray)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun CreditsDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { uriHandler.openUri("https://creativecommons.org/licenses/by-sa/4.0/") }) {
                Text("Licence")
            }
        },
        title = { Text("Credits") },
        text = {
            Text(
                "Most looks are adapted from the RawTherapee Film Simulation Collection by Pat David, " +
                    "Pavlov Dmitry and Michael Ezra, and from the PictureFX CLUTs by Marc Roovers " +
                    "(Heat, Breeze, Sepia, Antique, Silver), both licensed under CC BY-SA 4.0. They were " +
                    "converted from Hald CLUT images to 33-point .cube LUTs and renamed. The adapted files " +
                    "are shared under the same licence.\n\n" +
                    "1975, Memory, Sunbleached, Expired, Tide and Disposable are FilmLab originals, " +
                    "dedicated to the public domain (CC0).\n\n" +
                    "FilmLab is not affiliated with or endorsed by any film manufacturer."
            )
        },
    )
}
