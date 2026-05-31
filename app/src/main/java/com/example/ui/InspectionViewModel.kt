package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiScanner
import com.example.api.RetrofitClient
import com.squareup.moshi.Types
import com.example.data.AppDatabase
import com.example.data.DefectBox
import com.example.data.InspectionRepository
import com.example.data.InspectionScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

class InspectionViewModel(
    application: Application,
    private val repository: InspectionRepository
) : AndroidViewModel(application) {

    // List of historical scans
    val allScans: StateFlow<List<InspectionScan>> = repository.allScans
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _imageName = MutableStateFlow<String>("Factory Intake Sample #01")
    val imageName: StateFlow<String> = _imageName.asStateFlow()

    private val _activeBoxes = MutableStateFlow<List<DefectBox>>(emptyList())
    val activeBoxes: StateFlow<List<DefectBox>> = _activeBoxes.asStateFlow()

    private val _selectedScanId = MutableStateFlow<String?>(null)
    val selectedScanId: StateFlow<String?> = _selectedScanId.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isCorrectionMode = MutableStateFlow<Boolean>(false)
    val isCorrectionMode: StateFlow<Boolean> = _isCorrectionMode.asStateFlow()

    private val _selectedBoxIndex = MutableStateFlow<Int?>(-1)
    val selectedBoxIndex: StateFlow<Int?> = _selectedBoxIndex.asStateFlow()

    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    // Temporary box being drawn by user: [ymin, xmin, ymax, xmax] coordinates from 0 to 1000
    private val _tempDrawingBox = MutableStateFlow<List<Int>?>(null)
    val tempDrawingBox: StateFlow<List<Int>?> = _tempDrawingBox.asStateFlow()

    init {
        // Automatically load a default cream cotton fabric for immediate display
        loadSampleFabric(FabricType.DENIM_OIL_SPOT)
    }

    // Load sample fabric generated programmatically
    fun loadSampleFabric(type: FabricType) {
        viewModelScope.launch {
            _isLoading.value = true
            _apiError.value = null
            _isCorrectionMode.value = false
            _selectedBoxIndex.value = -1
            _selectedScanId.value = null
            _imageName.value = "${type.displayName} - Logged"
            
            val bitmap = withContext(Dispatchers.Default) {
                SampleFabricGenerator.generateFabric(type)
            }
            _currentBitmap.value = bitmap
            _activeBoxes.value = emptyList()
            _isLoading.value = false
        }
    }

    // Load image from gallery Uri
    fun loadExternalImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _apiError.value = null
            _isCorrectionMode.value = false
            _selectedBoxIndex.value = -1
            _selectedScanId.value = null
            _imageName.value = "Imported Fabric File"
            
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    var inputStream: InputStream? = null
                    try {
                        inputStream = context.contentResolver.openInputStream(uri)
                        BitmapFactory.decodeStream(inputStream)
                    } finally {
                        inputStream?.close()
                    }
                }
                if (bitmap != null) {
                    _currentBitmap.value = bitmap
                    _activeBoxes.value = emptyList()
                } else {
                    _apiError.value = "Failed to load imported image"
                }
            } catch (e: Exception) {
                _apiError.value = "Error: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Trigger vision scanning via Gemini 2.5/3.5 REST API with past operator corrections integrated
    fun scanFabricWithAI() {
        val bitmap = _currentBitmap.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _apiError.value = null
            _selectedBoxIndex.value = -1
            
            try {
                // Fetch previous corrections to pass to client systeminstructions (Few-shot learning)
                val historicalCorrectionsList = withContext(Dispatchers.IO) {
                    repository.getCorrectionsMemory()
                }

                val results = GeminiScanner.inspectFabric(bitmap, historicalCorrectionsList)
                
                // Map the api results to Local DefectBox entities
                val boxes = results.map { api ->
                    val coords = api.box2d // guaranteed 4 coordinates
                    val yMin = coords.getOrElse(0) { 0 }
                    val xMin = coords.getOrElse(1) { 0 }
                    val yMax = coords.getOrElse(2) { 1000 }
                    val xMax = coords.getOrElse(3) { 1000 }
                    
                    DefectBox(
                        scanId = "", // Filled when saving to Room
                        yMin = yMin,
                        xMin = xMin,
                        yMax = yMax,
                        xMax = xMax,
                        label = api.label,
                        confidence = api.confidence
                    )
                }

                _activeBoxes.value = boxes
                
                // Save this scan into the Local SQLite database
                val isClean = boxes.isEmpty()
                val savedId = repository.saveNewScan(
                    imageUri = "local://temp_" + System.currentTimeMillis(),
                    imageName = _imageName.value,
                    boxes = boxes,
                    isClean = isClean
                )
                _selectedScanId.value = savedId

            } catch (e: Exception) {
                _apiError.value = e.localizedMessage ?: "Unknown API vision error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // SELECT box on the canvas
    fun selectBox(index: Int) {
        if (_isCorrectionMode.value) {
            _selectedBoxIndex.value = index
        }
    }

    fun toggleCorrectionMode() {
        _isCorrectionMode.value = !_isCorrectionMode.value
        if (!_isCorrectionMode.value) {
            _selectedBoxIndex.value = -1
        }
    }

    // Drag / Move selected box
    fun moveSelectedBox(index: Int, dxPercent: Float, dyPercent: Float) {
        val currentList = _activeBoxes.value.toMutableList()
        if (index in currentList.indices) {
            val box = currentList[index]
            
            val scaleDx = (dxPercent * 1000).toInt()
            val scaleDy = (dyPercent * 1000).toInt()
            
            val newXMin = (box.xMin + scaleDx).coerceIn(0, 1000)
            val newXMax = (box.xMax + scaleDx).coerceIn(0, 1000)
            val newYMin = (box.yMin + scaleDy).coerceIn(0, 1000)
            val newYMax = (box.yMax + scaleDy).coerceIn(0, 1000)
            
            val width = newXMax - newXMin
            val height = newYMax - newYMin
            
            currentList[index] = box.copy(
                xMin = newXMin,
                xMax = (newXMin + width).coerceAtMost(1000),
                yMin = newYMin,
                yMax = (newYMin + height).coerceAtMost(1000),
                isUserCreatedOrModified = true
            )
            _activeBoxes.value = currentList
        }
    }

    // Resize selected box from a specific handle/corner
    fun resizeSelectedBox(index: Int, ymin: Int, xmin: Int, ymax: Int, xmax: Int) {
        val currentList = _activeBoxes.value.toMutableList()
        if (index in currentList.indices) {
            val box = currentList[index]
            currentList[index] = box.copy(
                yMin = ymin.coerceIn(0, 1000),
                xMin = xmin.coerceIn(0, 1000),
                yMax = ymax.coerceIn(ymin + 10, 1000),
                xMax = xmax.coerceIn(xmin + 10, 1000),
                isUserCreatedOrModified = true
            )
            _activeBoxes.value = currentList
        }
    }

    // Update label of box
    fun updateBoxLabel(index: Int, newLabel: String) {
        val currentList = _activeBoxes.value.toMutableList()
        if (index in currentList.indices) {
            val box = currentList[index]
            currentList[index] = box.copy(
                label = newLabel,
                isUserCreatedOrModified = true
            )
            _activeBoxes.value = currentList
        }
    }

    // Remove box item
    fun deleteBox(index: Int) {
        val currentList = _activeBoxes.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _activeBoxes.value = currentList
            _selectedBoxIndex.value = -1
        }
    }

    // Set temp coordinates when supervisor draws a new box
    fun setTempDrawingBox(ymin: Int, xmin: Int, ymax: Int, xmax: Int) {
        _tempDrawingBox.value = listOf(
            ymin.coerceIn(0, 1000),
            xmin.coerceIn(0, 1000),
            ymax.coerceIn(0, 1000),
            xmax.coerceIn(0, 1000)
        )
    }

    // Clear the temp drawing state once box creation is submitted
    fun clearTempDrawingBox() {
        _tempDrawingBox.value = null
    }

    // Add drawn box with selected label
    fun addNewDrawnBox(ymin: Int, xmin: Int, ymax: Int, xmax: Int, label: String) {
        val activeScanId = _selectedScanId.value ?: ""
        val newBox = DefectBox(
            scanId = activeScanId,
            yMin = ymin,
            xMin = xmin,
            yMax = ymax,
            xMax = xmax,
            label = label,
            confidence = 1.0f, // Operator corrected is 100%
            isUserCreatedOrModified = true
        )
        val currentList = _activeBoxes.value.toMutableList()
        currentList.add(newBox)
        _activeBoxes.value = currentList
        _selectedBoxIndex.value = currentList.size - 1
        _tempDrawingBox.value = null
    }

    // Persist operator's correction modifications to local memory (Room database)
    fun commitSupervisorCorrections() {
        val scanId = _selectedScanId.value
        val currentList = _activeBoxes.value
        
        viewModelScope.launch {
            if (scanId != null) {
                // Update existing record
                repository.saveCorrection(scanId, currentList)
            } else {
                // No current scan registered. Create a mock or write a brand new scan entry!
                val newScanId = repository.saveNewScan(
                    imageUri = "local://correction_" + System.currentTimeMillis(),
                    imageName = _imageName.value,
                    boxes = currentList,
                    isClean = currentList.isEmpty()
                )
                _selectedScanId.value = newScanId
            }
            // Exit correction mode and refresh
            _isCorrectionMode.value = false
            _selectedBoxIndex.value = -1
        }
    }

    // Pick and load a scan from historical logs right pane
    fun selectScanFromHistory(history: InspectionScan) {
        viewModelScope.launch {
            _isLoading.value = true
            _selectedScanId.value = history.id
            _imageName.value = history.imageName
            _apiError.value = null
            _isCorrectionMode.value = false
            _selectedBoxIndex.value = -1
            
            // Re-generate correct mock backdrop bitmap based on display label or use a standard cotton representation
            val bitmap = withContext(Dispatchers.Default) {
                if (history.imageName.contains("Denim")) {
                    SampleFabricGenerator.generateFabric(FabricType.DENIM_OIL_SPOT)
                } else if (history.imageName.contains("Linen")) {
                    SampleFabricGenerator.generateFabric(FabricType.LINEN_TORN_THREAD)
                } else if (history.imageName.contains("Silk")) {
                    SampleFabricGenerator.generateFabric(FabricType.SILK_STAIN)
                } else {
                    SampleFabricGenerator.generateFabric(FabricType.COTTON_CLEAN)
                }
            }
            _currentBitmap.value = bitmap
            
            // Load saved coordinates from the Local Room Database
            val boxesList = repository.fetchBoxesDirectly(history.id)
            _activeBoxes.value = boxesList
            _isLoading.value = false
        }
    }

    // Clear all history
    fun clearAllScansHistory() {
        viewModelScope.launch {
            repository.clearDatabase()
            _activeBoxes.value = emptyList()
            _selectedScanId.value = null
            _selectedBoxIndex.value = -1
            _isCorrectionMode.value = false
        }
    }

    // Delete single scan from history
    fun deleteScanFromHistory(scanId: String) {
        viewModelScope.launch {
            repository.deleteScan(scanId)
            if (_selectedScanId.value == scanId) {
                _selectedScanId.value = null
                _activeBoxes.value = emptyList()
                _selectedBoxIndex.value = -1
            }
        }
    }

    // Generate JSON structured dump of the operator's corrected dataset for few-shot exports or offline storage
    suspend fun exportDatasetJson(): String {
        return withContext(Dispatchers.Default) {
            val scans = repository.listScansDirectly()
            val list = mutableListOf<Map<String, Any>>()
            
            for (scan in scans) {
                val boxes = repository.fetchBoxesDirectly(scan.id)
                val boxesList = boxes.map { box ->
                    mapOf(
                        "label" to box.label,
                        "box_2d" to listOf(box.yMin, box.xMin, box.yMax, box.xMax),
                        "is_corrected" to box.isUserCreatedOrModified,
                        "confidence" to box.confidence
                    )
                }
                list.add(
                    mapOf(
                        "scan_id" to scan.id,
                        "image_name" to scan.imageName,
                        "timestamp" to scan.timestamp,
                        "status" to scan.status,
                        "defect_boxes" to boxesList
                    )
                )
            }

            // Simple manually serializing to keep Moshi references elegant and reliable
            val moshi = RetrofitClient.getMoshi()
            val listMy = Types.newParameterizedType(List::class.java, Map::class.java)
            val adapter = moshi.adapter<List<Map<String, Any>>>(listMy)
            adapter.indent("  ").toJson(list)
        }
    }
}

// Factory to create the ViewModel which requires Application Context and Repository
class InspectionViewModelFactory(
    private val application: Application,
    private val repository: InspectionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InspectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InspectionViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
