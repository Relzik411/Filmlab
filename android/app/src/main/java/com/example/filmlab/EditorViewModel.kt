package com.example.filmlab

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private data class RenderRequest(val photo: Int, val edit: EditState, val cropping: Boolean)

    var luts by mutableStateOf<List<Lut>>(emptyList())
        private set
    var edit by mutableStateOf(EditState())
        private set
    var preview by mutableStateOf<ImageBitmap?>(null)
        private set
    var original by mutableStateOf<ImageBitmap?>(null)
        private set
    var originalThumbnail by mutableStateOf<ImageBitmap?>(null)
        private set
    var thumbnails by mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
        private set
    var leakThumbnails by mutableStateOf<Map<Int, ImageBitmap>>(emptyMap())
        private set
    /** While true the preview shows the whole straightened photo so the crop box can be edited. */
    var isCropping by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isExporting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private var previewSize by mutableStateOf(1 to 1)

    val hasPhoto get() = original != null

    /** The crop box's width/height ratio in normalized units, or null for a free crop. */
    val cropRatio: Float?
        get() {
            val (w, h) = Geometry.frameSize(previewSize.first, previewSize.second, edit.crop)
            return CropMath.normalizedRatio(edit.crop.aspect.ratio(w.toFloat(), h.toFloat()), w.toFloat(), h.toFloat())
        }

    private var photoUri: Uri? = null
    private var photoVersion = 0
    private var previewSource: Bitmap? = null
    private var thumbnailSource: Bitmap? = null
    private var comparisonCrop: CropState? = null
    private var history = History(EditState())
    /** Fingerprint of the open photo, under which its edits are saved. */
    private var photoKey: String? = null
    private var recordJob: Job? = null
    private val requests = MutableStateFlow(RenderRequest(0, EditState(), true))

    init {
        viewModelScope.launch {
            luts = withContext(Dispatchers.IO) { Lut.loadAll(application.assets) }
            makeThumbnails()
        }
        // collectLatest cancels a render in progress when a newer edit arrives.
        viewModelScope.launch {
            requests.collectLatest { request ->
                val source = previewSource ?: return@collectLatest
                val edit = request.edit
                val lut = lutFor(edit.lutId)
                preview = withContext(Dispatchers.Default) {
                    Processor.apply(edit, lut, Geometry.apply(source, edit.crop, request.cropping))
                }.asImageBitmap()

                // "Hold to compare" shows the original with the same crop.
                if (request.cropping && edit.crop != comparisonCrop) {
                    original = withContext(Dispatchers.Default) {
                        Geometry.apply(source, edit.crop, cropping = true)
                    }.asImageBitmap()
                    comparisonCrop = edit.crop
                }
            }
        }
    }

    fun update(newEdit: EditState) {
        edit = newEdit
        requestRender()
        scheduleRecord()
        refreshHistory()
    }

    /** Records any pending change as an undo step and saves the photo's edits. */
    fun commit() {
        recordJob?.cancel()
        if (history.record(edit)) persist()
        refreshHistory()
    }

    fun undo() {
        val previous = history.undo(edit) ?: return
        showHistoryState(previous)
    }

    fun redo() {
        val next = history.redo(edit) ?: return
        showHistoryState(next)
    }

    fun revertToOriginal() {
        commit()
        edit = EditState()
        requestRender()
        commit()
    }

    private fun showHistoryState(state: EditState) {
        recordJob?.cancel()
        edit = state
        requestRender()
        persist()
        refreshHistory()
    }

    /** A step is recorded once editing pauses, so one slider drag is one undo step. */
    private fun scheduleRecord() {
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            delay(500)
            commit()
        }
    }

    private fun refreshHistory() {
        canUndo = hasPhoto && history.canUndo(edit)
        canRedo = history.canRedo(edit)
    }

    private fun persist() {
        val key = photoKey ?: return
        val state = history.committed
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { EditStore.save(context, key, state) }
    }

    private fun requestRender() {
        var edit = edit
        // While the crop box is being edited the whole photo is shown, so moving the box changes nothing.
        if (isCropping) edit = edit.copy(crop = edit.crop.copy(rect = NRect.Full))
        requests.value = RenderRequest(photoVersion, edit, cropping = !isCropping)
    }

    fun showCropBox(cropping: Boolean) {
        if (cropping == isCropping) return
        isCropping = cropping
        requestRender()
    }

    fun setAspect(aspect: CropAspect) {
        val withAspect = edit.copy(crop = edit.crop.copy(aspect = aspect))
        edit = withAspect
        update(withAspect.copy(crop = withAspect.crop.copy(rect = CropMath.fitted(cropRatio))))
    }

    fun rotateClockwise() {
        val turned = edit.copy(crop = edit.crop.copy(quarterTurns = (edit.crop.quarterTurns + 1) % 4))
        edit = turned
        // The frame's shape changed, so refit the box.
        update(turned.copy(crop = turned.crop.copy(rect = CropMath.fitted(cropRatio))))
    }

    fun flip() {
        val r = edit.crop.rect
        update(edit.copy(crop = edit.crop.copy(flipped = !edit.crop.flipped, rect = NRect(1 - r.right, r.top, 1 - r.left, r.bottom))))
    }

    fun resetCrop() = update(edit.copy(crop = CropState()))

    fun load(uri: Uri) {
        viewModelScope.launch {
            isLoading = true
            try {
                val context = getApplication<Application>()
                val (previewBitmap, thumbBitmap) = withContext(Dispatchers.IO) {
                    PhotoIO.decode(context, uri, 1600) to PhotoIO.decode(context, uri, 240)
                }
                val (key, saved) = withContext(Dispatchers.IO) {
                    val key = EditStore.key(context, uri)
                    key to key?.let { EditStore.load(context, it) }
                }
                if (previewBitmap == null || thumbBitmap == null) {
                    message = "Couldn't open that photo."
                    return@launch
                }
                commit() // the previous photo's last change
                photoUri = uri
                photoKey = key
                photoVersion++
                previewSource = previewBitmap
                previewSize = previewBitmap.width to previewBitmap.height
                thumbnailSource = thumbBitmap
                original = previewBitmap.asImageBitmap()
                comparisonCrop = CropState()
                preview = original
                originalThumbnail = thumbBitmap.asImageBitmap()
                thumbnails = emptyMap()
                leakThumbnails = emptyMap()
                // Saved edits come back as one undo step, so Undo returns to the original.
                history = History(EditState())
                edit = saved ?: EditState()
                history.record(edit)
                requestRender()
                refreshHistory()
                makeThumbnails()
            } finally {
                isLoading = false
            }
        }
    }

    fun export() {
        val uri = photoUri ?: return
        val edit = edit
        viewModelScope.launch {
            isExporting = true
            try {
                val context = getApplication<Application>()
                withContext(Dispatchers.IO) {
                    val full = PhotoIO.decode(context, uri, 6000) ?: error("Couldn't open the photo.")
                    val shaped = Geometry.apply(full, edit.crop, cropping = true)
                    PhotoIO.save(context, Processor.apply(edit, lutFor(edit.lutId), shaped))
                }
                message = "Saved to Pictures/FilmLab"
            } catch (e: Exception) {
                message = e.message ?: "Couldn't save the photo."
            } finally {
                isExporting = false
            }
        }
    }

    private fun lutFor(id: String?) = luts.firstOrNull { it.id == id }

    private fun makeThumbnails() {
        val source = thumbnailSource ?: return
        val luts = luts
        viewModelScope.launch {
            val leaks = LightLeak.styles.associate { style ->
                val leakOnly = EditState(leak = style.id, leakAmount = 1f)
                style.id to Processor.apply(leakOnly, null, source).asImageBitmap()
            }
            val looks = luts.associate { lut ->
                lut.id to Processor.apply(EditState(lutId = lut.id), lut, source).asImageBitmap()
            }
            if (source === thumbnailSource) {
                leakThumbnails = leaks
                if (looks.isNotEmpty()) thumbnails = looks
            }
        }
    }
}
