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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private data class RenderRequest(val photo: Int, val edit: EditState)

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
    var isLoading by mutableStateOf(false)
        private set
    var isExporting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    val hasPhoto get() = original != null

    private var photoUri: Uri? = null
    private var photoVersion = 0
    private var previewSource: Bitmap? = null
    private var thumbnailSource: Bitmap? = null
    private val requests = MutableStateFlow(RenderRequest(0, EditState()))

    init {
        viewModelScope.launch {
            luts = withContext(Dispatchers.IO) { Lut.loadAll(application.assets) }
            makeThumbnails()
        }
        // collectLatest cancels a render in progress when a newer edit arrives.
        viewModelScope.launch {
            requests.collectLatest { request ->
                val source = previewSource ?: return@collectLatest
                val lut = lutFor(request.edit.lutId)
                preview = Processor.apply(request.edit, lut, source).asImageBitmap()
            }
        }
    }

    fun update(newEdit: EditState) {
        edit = newEdit
        requests.value = RenderRequest(photoVersion, newEdit)
    }

    fun load(uri: Uri) {
        viewModelScope.launch {
            isLoading = true
            try {
                val context = getApplication<Application>()
                val (previewBitmap, thumbBitmap) = withContext(Dispatchers.IO) {
                    PhotoIO.decode(context, uri, 1600) to PhotoIO.decode(context, uri, 240)
                }
                if (previewBitmap == null || thumbBitmap == null) {
                    message = "Couldn't open that photo."
                    return@launch
                }
                photoUri = uri
                photoVersion++
                previewSource = previewBitmap
                thumbnailSource = thumbBitmap
                original = previewBitmap.asImageBitmap()
                preview = original
                originalThumbnail = thumbBitmap.asImageBitmap()
                thumbnails = emptyMap()
                update(EditState())
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
                    PhotoIO.save(context, Processor.apply(edit, lutFor(edit.lutId), full))
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
        if (luts.isEmpty()) return
        viewModelScope.launch {
            val rendered = luts.associate { lut ->
                lut.id to Processor.apply(EditState(lutId = lut.id), lut, source).asImageBitmap()
            }
            if (source === thumbnailSource) thumbnails = rendered
        }
    }
}
