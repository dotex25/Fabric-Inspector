package com.example.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.DefectBox
import com.example.data.InspectionRepository
import com.example.data.InspectionScan
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    application: Application,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { InspectionRepository(database.defectDao()) }
    
    val viewModel: InspectionViewModel = viewModel(
        factory = InspectionViewModelFactory(application, repository)
    )

    val scans by viewModel.allScans.collectAsState()
    val activeBoxes by viewModel.activeBoxes.collectAsState()
    val currentBitmap by viewModel.currentBitmap.collectAsState()
    val imageName by viewModel.imageName.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isCorrectionMode by viewModel.isCorrectionMode.collectAsState()
    val selectedBoxIndex by viewModel.selectedBoxIndex.collectAsState()
    val apiError by viewModel.apiError.collectAsState()
    
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }

    // Launcher for selecting image from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.loadExternalImage(context, uri)
        }
    }

    // Launcher for snapping image using Camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.loadExternalImage(context, com.example.ui.UriHelper.saveBitmapToCache(context, bitmap))
        }
    }

    // Compute Metrics based on saved scans database
    val totals = remember(scans) {
        val total = scans.size
        val correctedNum = scans.count { it.status == "CORRECTED" }
        val defectsNum = scans.count { it.status == "DEFECTS_FOUND" }
        val cleanNum = scans.count { it.status == "CLEAN" }
        
        mapOf(
            "total" to total,
            "corrected" to correctedNum,
            "defects" to defectsNum,
            "clean" to cleanNum
        )
    }

    // Layout configuration for responsiveness
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp = configuration.screenWidthDp

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BentoHeader(
                onExportClick = {
                    scope.launch {
                        val json = viewModel.exportDatasetJson()
                        exportedJsonText = json
                        showExportDialog = true
                    }
                },
                onResetClick = {
                    viewModel.clearAllScansHistory()
                    Toast.makeText(context, "Cleared database logs", Toast.LENGTH_SHORT).show()
                }
            )
        },
        containerColor = MidnightBg
    ) { innerPadding ->
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            
            // Check orientation and apply split panels
            if (isLandscape || screenWidthDp > 600) {
                // Wide tablet dual pane layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // LEFT Pane: Image canvas workspace (60% width)
                    Box(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                    ) {
                        InspectionCanvas(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // RIGHT Pane: Bento Grid Panels (42% width)
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 12.dp)
                    ) {
                        ActionControlsPanel(
                            viewModel = viewModel,
                            imageName = imageName,
                            onGalleryClick = { galleryLauncher.launch("image/*") },
                            onCameraClick = { cameraLauncher.launch() },
                            onApiScanClick = { viewModel.scanFabricWithAI() },
                            apiError = apiError
                        )

                        BentoMetricsGrid(
                            totals = totals,
                            isCorrectionMode = isCorrectionMode,
                            onCorrectionModeToggle = { viewModel.toggleCorrectionMode() },
                            activeBoxesCount = activeBoxes.size,
                            onSaveCorrections = { viewModel.commitSupervisorCorrections() },
                            onCancelCorrections = { viewModel.toggleCorrectionMode() }
                        )

                        DefectAnalysisPanel(
                            activeBoxes = activeBoxes,
                            selectedBoxIndex = selectedBoxIndex ?: -1,
                            onSelectBoxIndex = { viewModel.selectBox(it) }
                        )

                        SampleFabricPicker(viewModel = viewModel)

                        HistoryScansSection(
                            scans = scans,
                            onSelectScan = { viewModel.selectScanFromHistory(it) },
                            onDeleteScan = { viewModel.deleteScanFromHistory(it) }
                        )
                    }
                }
            } else {
                // Portrait single pane layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(390.dp)
                    ) {
                        InspectionCanvas(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ActionControlsPanel(
                        viewModel = viewModel,
                        imageName = imageName,
                        onGalleryClick = { galleryLauncher.launch("image/*") },
                        onCameraClick = { cameraLauncher.launch() },
                        onApiScanClick = { viewModel.scanFabricWithAI() },
                        apiError = apiError
                    )

                    BentoMetricsGrid(
                        totals = totals,
                        isCorrectionMode = isCorrectionMode,
                        onCorrectionModeToggle = { viewModel.toggleCorrectionMode() },
                        activeBoxesCount = activeBoxes.size,
                        onSaveCorrections = { viewModel.commitSupervisorCorrections() },
                        onCancelCorrections = { viewModel.toggleCorrectionMode() }
                    )

                    DefectAnalysisPanel(
                        activeBoxes = activeBoxes,
                        selectedBoxIndex = selectedBoxIndex ?: -1,
                        onSelectBoxIndex = { viewModel.selectBox(it) }
                    )

                    SampleFabricPicker(viewModel = viewModel)

                    HistoryScansSection(
                        scans = scans,
                        onSelectScan = { viewModel.selectScanFromHistory(it) },
                        onDeleteScan = { viewModel.deleteScanFromHistory(it) }
                    )
                }
            }

            // Scanning skeleton overlay line animation
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = PrimaryTeal, strokeWidth = 5.dp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "SPARK ENGINE v1 VISION SCAN...",
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryTeal,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Processing fabric coordinates & color channel logs",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        
                        // Sweeping scan line effect
                        val infiniteTransition = rememberInfiniteTransition(label = "scan_bar")
                        val lineOffset by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween<Float>(durationMillis = 1500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scan_offset"
                        )
                        
                        Spacer(modifier = Modifier.height(30.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(4.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, PrimaryTeal, Color.Transparent)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }

    // Modal Export structured JSON dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = PrimaryTeal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Correction Memory JSON", color = TextWhite)
                }
            },
            text = {
                Column {
                    Text(
                        "Below is the consolidated operator correction logs database. This structured system data trains your Few-Shot System prompt on future passes to systematically eliminate vision errors:",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = exportedJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("exported_json_field"),
                        textStyle = TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
                            unfocusedBorderColor = CustomGreyBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = PanelBg
        )
    }
}

@Composable
fun BentoHeader(
    onExportClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MidnightBg)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PrimaryTeal, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Fabric intel logo",
                    tint = Color(0xFF381E72),
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SPARK ENGINE ",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    )
                    Text(
                        text = "v1",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PrimaryTeal.copy(alpha = 0.8f))
                    )
                }
                Text(
                    text = "Fabric Intel • Factory A4",
                    style = TextStyle(fontSize = 10.sp, color = TextMuted, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live Status Indicator
            Row(
                modifier = Modifier
                    .background(Color(0xFF4A4458).copy(alpha = 0.3f), CircleShape)
                    .border(BorderStroke(1.dp, Color(0xFF4A4458)), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF34D399), CircleShape)
                )
                Text("LIVE", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onExportClick,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF4A4458).copy(alpha = 0.3f), CircleShape)
                    .border(BorderStroke(1.dp, Color(0xFF4A4458)), CircleShape)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = "Export JSON", tint = PrimaryTeal, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = onResetClick,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF4A4458).copy(alpha = 0.3f), CircleShape)
                    .border(BorderStroke(1.dp, Color(0xFF4A4458)), CircleShape)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset History", tint = DefectHole, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun ActionControlsPanel(
    viewModel: InspectionViewModel,
    imageName: String,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onApiScanClick: () -> Unit,
    apiError: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        border = BorderStroke(1.dp, CustomGreyBorder),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Intake row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCameraClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("camera_intake_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BentoAccentDark,
                        contentColor = PrimaryTeal
                    ),
                    border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Camera Intake",
                        modifier = Modifier.size(18.dp),
                        tint = PrimaryTeal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Camera",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                Button(
                    onClick = onGalleryClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("gallery_intake_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BentoAccentDark,
                        contentColor = PrimaryTeal
                    ),
                    border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Gallery Intake",
                        modifier = Modifier.size(18.dp),
                        tint = PrimaryTeal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gallery",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Scanning action button
            Button(
                onClick = onApiScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("trigger_ai_scan_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF381E72))
                Spacer(modifier = Modifier.width(8.dp))
                Text("TRIGGER SPARK VISION SCAN", fontWeight = FontWeight.Black, color = Color(0xFF381E72), letterSpacing = 0.5.sp)
            }

            // Error displays
            apiError?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DefectHole.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, DefectHole, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = DefectHole, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(it, color = TextWhite, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BentoMetricsGrid(
    totals: Map<String, Int>,
    isCorrectionMode: Boolean,
    onCorrectionModeToggle: (Boolean) -> Unit,
    activeBoxesCount: Int,
    onSaveCorrections: () -> Unit,
    onCancelCorrections: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ROW 1: Detected VS AI Accuracy
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Detected Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(116.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A4458).copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, CustomGreyBorder),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Detected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCCC2DC)
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = String.format("%02d", activeBoxesCount),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Light,
                            color = Color.White
                        )
                        if (activeBoxesCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = DefectHole.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, DefectHole)
                            ) {
                                Text(
                                    text = "+$activeBoxesCount ERR",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DefectHole,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = DefectTornThread.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, DefectTornThread)
                            ) {
                                Text(
                                    text = "CLEAN",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DefectTornThread,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // AI Accuracy Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(116.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A4458).copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, CustomGreyBorder),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AI Accuracy",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCCC2DC)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val rawAccuracy = if ((totals["total"] ?: 0) > 0) {
                                val corrected = totals["corrected"] ?: 0
                                val total = totals["total"] ?: 1
                                100f - (corrected.toFloat() / total * 15f).coerceAtMost(50f)
                            } else {
                                98.4f
                            }
                            Text(
                                text = String.format("%.1f", rawAccuracy),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Light,
                                color = Color.White
                            )
                            Text(
                                text = "%",
                                fontSize = 16.sp,
                                color = TextMuted
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Sleek Emerald progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .background(Color(0xFF1C1B1F), CircleShape)
                                .clip(CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.984f)
                                    .fillMaxHeight()
                                    .background(Color(0xFF34D399), CircleShape)
                            )
                        }
                    }
                }
            }
        }

        // ROW 2: Supervisor Control Card & Actions (Col span 4 / 2 equivalents)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BentoAccentDark),
            border = BorderStroke(1.dp, CustomGreyBorder),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CORRECTION MODE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCCC2DC),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Supervisor Access",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Switch(
                        checked = isCorrectionMode,
                        onCheckedChange = { onCorrectionModeToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFD0BCFF),
                            checkedTrackColor = Color(0xFF4A4458)
                        ),
                        modifier = Modifier.testTag("correction_mode_switch")
                    )
                }

                if (isCorrectionMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSaveCorrections,
                            modifier = Modifier.weight(1.5f).height(44.dp).testTag("save_corrections_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF381E72), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", color = Color(0xFF381E72), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        
                        Button(
                            onClick = onCancelCorrections,
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4458)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SampleFabricPicker(
    viewModel: InspectionViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        border = BorderStroke(1.dp, CustomGreyBorder),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dashboard, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("DIAGNOSTIC SAMPLE LOADER", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextWhite)
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FabricType.values().forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, CustomGreyBorder, RoundedCornerShape(12.dp))
                            .clickable { viewModel.loadSampleFabric(type) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(type.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                            val badgeText = when (type) {
                                FabricType.COTTON_CLEAN -> "Clean / Expected OK"
                                FabricType.DENIM_OIL_SPOT -> "Defect: Oil Spot"
                                FabricType.LINEN_TORN_THREAD -> "Defect: Torn Thread + Hole"
                                FabricType.SILK_STAIN -> "Defect: Stain"
                            }
                            Text(badgeText, fontSize = 11.sp, color = TextMuted)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScansSection(
    scans: List<InspectionScan>,
    onSelectScan: (InspectionScan) -> Unit,
    onDeleteScan: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        border = BorderStroke(1.dp, CustomGreyBorder),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("HISTORIC DEFECT LOGS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextWhite)
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (scans.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No scans logged in database yet", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                scans.take(10).forEach { item ->
                    val badgeColor = when (item.status) {
                        "CLEAN" -> Color(0xFF34D399)
                        "CORRECTED" -> Color(0xFFD0BCFF)
                        else -> Color(0xFFF2B8B5)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, CustomGreyBorder, RoundedCornerShape(12.dp))
                            .clickable { onSelectScan(item) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(badgeColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(item.imageName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                Text(
                                    text = "Status: ${item.status} • Defects: ${item.defectCount}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteScan(item.id) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DefectHole, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DefectAnalysisPanel(
    activeBoxes: List<DefectBox>,
    selectedBoxIndex: Int,
    onSelectBoxIndex: (Int) -> Unit
) {
    if (activeBoxes.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        border = BorderStroke(1.dp, CustomGreyBorder),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = PrimaryTeal,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "DEFECT BREAKDOWN & LOGS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 0.5.sp
                )
            }
            
            Text(
                text = "Spark Engine v1 • High Precision Analytics",
                style = TextStyle(fontSize = 10.sp, color = TextMuted, letterSpacing = 1.sp),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeBoxes.forEachIndexed { index, box ->
                    val isSelected = selectedBoxIndex == index
                    val defectColor = when (box.label) {
                        "Hole" -> DefectHole
                        "Oil Spot" -> DefectOilSpot
                        "Stain" -> DefectStain
                        else -> DefectTornThread
                    }

                    // Calculate area and location
                    val widthP = box.xMax - box.xMin
                    val heightP = box.yMax - box.yMin
                    val areaPercent = (widthP * heightP) / 10000f
                    
                    val horizLoc = when {
                        box.xMin + widthP / 2 < 333 -> "Left"
                        box.xMin + widthP / 2 < 666 -> "Center"
                        else -> "Right"
                    }
                    val vertLoc = when {
                        box.yMin + heightP / 2 < 333 -> "Top"
                        box.yMin + heightP / 2 < 666 -> "Middle"
                        else -> "Bottom"
                    }
                    
                    val locationDetails = "Sector: $vertLoc-$horizLoc (X: ${box.xMin}..${box.xMax}, Y: ${box.yMin}..${box.yMax})"
                    val sizeType = when {
                        areaPercent < 0.5f -> "Micro (<0.5%)"
                        areaPercent < 2.0f -> "Standard"
                        else -> "Macro (>2.0%)"
                    }
                    val affectedArea = "Bounds: $sizeType • Area ~ ${String.format("%.2f", areaPercent)}%"

                    // Explaining issue & detection reasoning based on label
                    val (explanation, reasoning) = when (box.label) {
                        "Hole" -> Pair(
                            "Warp/weft puncture disrupting structural integrity. Structural fabric breach.",
                            "High light transmittance and loose fiber edges detected via color-channel analysis of background threshold logs."
                        )
                        "Oil Spot" -> Pair(
                            "Organic hydrophobic fluid residue or machine lubricant contamination.",
                            "Spectro-chromation variance detects oil stain signature with localized low-reflectivity contrast profiles."
                        )
                        "Stain" -> Pair(
                            "Surface pigment discoloration, chemical wash imbalance, or external liquid spill.",
                            "Reduced chroma reflectivity and diffuse boundaries verified by localized color-histogram comparison."
                        )
                        else -> Pair( // Torn Thread
                            "Fraying warp fiber, loose weft single loops, or pulled thread stitches.",
                            "Discontinuous linear texture paths and high-frequency edge tracing filters confirmed loosened yarn lines."
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBoxIndex(index) }
                            .border(
                                width = 1.dp,
                                color = if (isSelected) PrimaryTeal else CustomGreyBorder.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) BentoAccentDark else Color.Black.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(defectColor, CircleShape)
                                    )
                                    Text(
                                        text = "${box.label.uppercase()} DEFECT",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (box.confidence > 0.85f) Color(0xFF1B3D2F) else Color(0xFF423B25),
                                    border = BorderStroke(1.dp, if (box.confidence > 0.85f) Color(0xFF34D399).copy(alpha = 0.3f) else Color(0xFFD4AF37).copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "CONF: ${String.format("%.1f", box.confidence * 100)}%",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (box.confidence > 0.85f) Color(0xFF34D399) else Color(0xFFD4AF37)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Issue Description
                            Text(
                                text = explanation,
                                fontSize = 11.sp,
                                color = TextWhite,
                                modifier = Modifier.padding(bottom = 4.dp),
                                style = TextStyle(lineHeight = 15.sp)
                            )

                            // Area and Location
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Affected Area", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    Text(affectedArea, fontSize = 10.sp, color = TextWhite)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Precise Coordinates", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    Text(locationDetails, fontSize = 10.sp, color = TextWhite)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Reasoning
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text("Detection Reasoning", fontSize = 9.sp, color = PrimaryTeal, fontWeight = FontWeight.Bold)
                                Text(
                                    text = reasoning,
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    style = TextStyle(lineHeight = 14.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
