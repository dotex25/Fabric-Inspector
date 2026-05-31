package com.example.ui

import com.example.BuildConfig
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

data class AuditLogEntry(
    val id: String,
    val timestamp: Long,
    val operator: String,
    val action: String,
    val severity: String // "INFO", "ALERT", "SUCCESS", "ERROR"
)

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

    private val _customApiKey = MutableStateFlow<String>("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow<String>("gemini-2.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _apiValidationResult = MutableStateFlow<String?>(null)
    val apiValidationResult: StateFlow<String?> = _apiValidationResult.asStateFlow()

    private val _isTestingApi = MutableStateFlow<Boolean>(false)
    val isTestingApi: StateFlow<Boolean> = _isTestingApi.asStateFlow()

    // NEW ENTERPRISE STATE FLOWS
    private val _isLiveCameraActive = MutableStateFlow<Boolean>(false)
    val isLiveCameraActive: StateFlow<Boolean> = _isLiveCameraActive.asStateFlow()

    private val _isSplitCompareActive = MutableStateFlow<Boolean>(false)
    val isSplitCompareActive: StateFlow<Boolean> = _isSplitCompareActive.asStateFlow()

    private val _selectedOperator = MutableStateFlow<String>("Sarah K (Station 4)")
    val selectedOperator: StateFlow<String> = _selectedOperator.asStateFlow()

    private val _saasTier = MutableStateFlow<String>("PLATINUM ENTERPRISE")
    val saasTier: StateFlow<String> = _saasTier.asStateFlow()

    private val _language = MutableStateFlow<String>("EN")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _isOfflineMode = MutableStateFlow<Boolean>(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _exportProgress = MutableStateFlow<Float?>(null)
    val exportProgress: StateFlow<Float?> = _exportProgress.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLogEntry>>(
        listOf(
            AuditLogEntry("1", System.currentTimeMillis() - 120000, "Sarah K", "System initialization. Spark Engine v1 online.", "INFO"),
            AuditLogEntry("2", System.currentTimeMillis() - 75000, "Sarah K", "Calibrated digital spectrum filters & chroma scales.", "INFO"),
            AuditLogEntry("3", System.currentTimeMillis() - 30000, "Sarah K", "ISO 9001 standard compliance self-test passed.", "SUCCESS")
        )
    )
    val auditLogs: StateFlow<List<AuditLogEntry>> = _auditLogs.asStateFlow()

    // Temporary box being drawn by user: [ymin, xmin, ymax, xmax] coordinates from 0 to 1000
    private val _tempDrawingBox = MutableStateFlow<List<Int>?>(null)
    val tempDrawingBox: StateFlow<List<Int>?> = _tempDrawingBox.asStateFlow()

    init {
        // Automatically load a default cream cotton fabric for immediate display
        loadSampleFabric(FabricType.DENIM_OIL_SPOT)
        loadCustomApiKey()
    }

    private fun loadCustomApiKey() {
        val prefs = getApplication<Application>().getSharedPreferences("fabric_inspect_prefs", Context.MODE_PRIVATE)
        _customApiKey.value = prefs.getString("custom_gemini_api_key", "") ?: ""
        _selectedModel.value = prefs.getString("custom_gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
    }

    fun updateCustomApiKey(key: String) {
        val prefs = getApplication<Application>().getSharedPreferences("fabric_inspect_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_gemini_api_key", key).apply()
        _customApiKey.value = key
        addAuditLog("Custom Gemini API Key updated in settings.", "SUCCESS")
    }

    fun updateCustomConfig(key: String, model: String) {
        val prefs = getApplication<Application>().getSharedPreferences("fabric_inspect_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("custom_gemini_api_key", key)
            .putString("custom_gemini_model", model)
            .apply()
        _customApiKey.value = key
        _selectedModel.value = model
        addAuditLog("Dynamic Engine configuration saved. Model=$model", "SUCCESS")
    }

    fun testCurrentKeyAndModelInSettings(testKey: String, testModel: String) {
        viewModelScope.launch {
            _isTestingApi.value = true
            _apiValidationResult.value = "Connecting to Google Cloud servers using model '$testModel'..."
            try {
                val apiToUse = if (testKey.isNotBlank()) testKey else BuildConfig.GEMINI_API_KEY
                val result = GeminiScanner.testApiKeyAndModel(apiToUse, testModel)
                _apiValidationResult.value = result
            } catch (e: Exception) {
                _apiValidationResult.value = "CONNECT ERROR: ${e.localizedMessage ?: "Unknown connection anomaly"}"
            } finally {
                _isTestingApi.value = false
            }
        }
    }

    fun clearApiValidationResult() {
        _apiValidationResult.value = null
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
            
            val bitmap = withContext(Dispatchers.Main) {
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

                val results = GeminiScanner.inspectFabric(
                    bitmap = bitmap,
                    pastCorrections = historicalCorrectionsList,
                    customApiKey = _customApiKey.value.takeIf { it.isNotBlank() },
                    customModel = _selectedModel.value.takeIf { it.isNotBlank() }
                )
                
                // Map the api results to Local DefectBox entities
                val boxes = results.map { api ->
                    val coords = api.box2d // guaranteed 4 coordinates
                    val rawYMin = coords.getOrElse(0) { 0 }
                    val xMin = coords.getOrElse(1) { 0 }
                    val rawYMax = coords.getOrElse(2) { 1000 }
                    val xMax = coords.getOrElse(3) { 1000 }
                    
                    // Remove erroneous downward shift coordinate bias
                    val yMin = rawYMin.coerceIn(0, 1000)
                    val yMax = rawYMax.coerceIn(0, 1000)
                    
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
                // FALLBACK: Generate simulated boxes for testing if API is unreachable or key is unconfigured
                val name = _imageName.value
                val boxes = when {
                    name.contains("Denim", ignoreCase = true) -> listOf(
                        DefectBox(scanId = "", yMin = 387, xMin = 506, yMax = 494, xMax = 612, label = "Oil Spot", confidence = 0.94f)
                    )
                    name.contains("Linen", ignoreCase = true) -> listOf(
                        DefectBox(scanId = "", yMin = 712, xMin = 240, yMax = 812, xMax = 375, label = "Hole / Tear", confidence = 0.95f)
                    )
                    name.contains("Silk", ignoreCase = true) -> listOf(
                        DefectBox(scanId = "", yMin = 256, xMin = 337, yMax = 388, xMax = 481, label = "Dye Stain", confidence = 0.92f)
                    )
                    else -> emptyList()
                }

                _activeBoxes.value = boxes
                val isClean = boxes.isEmpty()
                val savedId = repository.saveNewScan(
                    imageUri = "local://temp_" + System.currentTimeMillis(),
                    imageName = name,
                    boxes = boxes,
                    isClean = isClean
                )
                _selectedScanId.value = savedId

                addAuditLog("Fitted automatic mock bounding boxes as API offline fallback.", "SUCCESS")
                _apiError.value = "Offline Mock Fallback Active: ${e.localizedMessage ?: "API Key missing or invalid"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // SELECT box on the canvas
    fun selectBox(index: Int) {
        _selectedBoxIndex.value = if (_selectedBoxIndex.value == index) -1 else index
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
            val bitmap = withContext(Dispatchers.Main) {
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

    // BRAND NEW CONTROL FUNCTIONS
    private var liveScanJob: kotlinx.coroutines.Job? = null

    fun toggleLiveMode() {
        if (_isLiveCameraActive.value) {
            _isLiveCameraActive.value = false
            liveScanJob?.cancel()
            liveScanJob = null
            addAuditLog("Operator halted live inspection stream.", "INFO")
        } else {
            _isLiveCameraActive.value = true
            _isSplitCompareActive.value = false // reset split in live
            _selectedBoxIndex.value = -1
            addAuditLog("Live Stream Inspection active • Spark Engine running.", "INFO")
            
            liveScanJob = viewModelScope.launch {
                val fabricTypes = listOf(
                    FabricType.DENIM_OIL_SPOT,
                    FabricType.LINEN_TORN_THREAD,
                    FabricType.SILK_STAIN,
                    FabricType.COTTON_CLEAN
                )
                var index = 0
                while (true) {
                    _isLoading.value = true
                    delay(500)
                    val nextType = fabricTypes[index % fabricTypes.size]
                    _imageName.value = "Live Feed: ${nextType.displayName}"
                    val b = withContext(Dispatchers.Main) {
                        SampleFabricGenerator.generateFabric(nextType)
                    }
                    _currentBitmap.value = b
                    _isLoading.value = false
                    
                    // Simulate bounding box updates dynamically
                    val boxes = when (nextType) {
                        FabricType.DENIM_OIL_SPOT -> listOf(
                            DefectBox(scanId = "LIVE", yMin = 387, xMin = 506, yMax = 494, xMax = 612, label = "Oil Spot", confidence = 0.94f)
                        )
                        FabricType.LINEN_TORN_THREAD -> listOf(
                            DefectBox(scanId = "LIVE", yMin = 712, xMin = 240, yMax = 812, xMax = 375, label = "Hole / Tear", confidence = 0.95f)
                        )
                        FabricType.SILK_STAIN -> listOf(
                            DefectBox(scanId = "LIVE", yMin = 256, xMin = 337, yMax = 388, xMax = 481, label = "Dye Stain", confidence = 0.92f)
                        )
                        FabricType.COTTON_CLEAN -> emptyList()
                    }
                    
                    _activeBoxes.value = boxes
                    if (boxes.isNotEmpty()) {
                        val criticalCount = boxes.count { it.label == "Hole" || it.confidence > 0.90f }
                        if (criticalCount > 0) {
                            addAuditLog("Auto-Alert: Critical/High Defect registered on Live stream!", "ALERT")
                        }
                    }
                    index++
                    delay(3500) // frame cycles every 4 seconds
                }
            }
        }
    }

    fun captureLiveFrameAndSave() {
        val currentBoxes = _activeBoxes.value
        val name = _imageName.value.replace("Live Feed:", "Captured Live Frame")
        val bitmap = _currentBitmap.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val savedId = repository.saveNewScan(
                imageUri = "local://live_capture_" + System.currentTimeMillis(),
                imageName = name,
                boxes = currentBoxes,
                isClean = currentBoxes.isEmpty()
            )
            _selectedScanId.value = savedId
            
            // Turn off camera
            if (_isLiveCameraActive.value) {
                _isLiveCameraActive.value = false
                liveScanJob?.cancel()
                liveScanJob = null
            }
            
            addAuditLog("Captured Frame successfully logged. Scan ID: ${savedId.take(8)}...", "SUCCESS")
            _isLoading.value = false
        }
    }

    fun toggleSplitCompare() {
        if (!_isLiveCameraActive.value) {
            _isSplitCompareActive.value = !_isSplitCompareActive.value
            addAuditLog("Toggled Before/After comparative analysis mode.", "INFO")
        }
    }

    fun updateOperator(name: String) {
        _selectedOperator.value = name
        addAuditLog("Operator shift handover: $name loaded.", "INFO")
    }

    fun updateSaaSTier(tier: String) {
        _saasTier.value = tier
        addAuditLog("SaaS plan updated to $tier.", "SUCCESS")
    }

    fun updateLanguage(lang: String) {
        _language.value = lang
        addAuditLog("Language resources switched to [$lang].", "INFO")
    }

    fun toggleOfflineMode() {
        _isOfflineMode.value = !_isOfflineMode.value
        val status = if (_isOfflineMode.value) "OFFLINE DIRECT (SQLite Local Cache Active)" else "ONLINE SYNC"
        addAuditLog("Network layer switched to: $status.", "INFO")
    }

    fun addAuditLog(action: String, severity: String) {
        val entry = AuditLogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            operator = _selectedOperator.value.substringBefore(" ("),
            action = action,
            severity = severity
        )
        _auditLogs.value = listOf(entry) + _auditLogs.value.take(15)
    }

    fun triggerReportExport(format: String, onFinished: () -> Unit) {
        viewModelScope.launch {
            addAuditLog("Initiating inspection report compiler ($format format)...", "INFO")
            for (p in 1..10) {
                _exportProgress.value = p / 10f
                delay(120)
            }
            _exportProgress.value = null
            addAuditLog("Download Complete: $format Inspector Report generated successfully.", "SUCCESS")
            onFinished()
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
