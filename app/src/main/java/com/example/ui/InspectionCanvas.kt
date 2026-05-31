package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import com.example.data.DefectBox
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun getAdaptiveBorderColor(): Color {
    val dark = isSystemInDarkTheme()
    return if (dark) Color(0xFF4A4458) else Color(0xFFD1D5DB)
}

@Composable
fun getAdaptiveMutedTextColor(): Color {
    val dark = isSystemInDarkTheme()
    return if (dark) Color(0xFF938F99) else Color(0xFF4B5563)
}

@Composable
fun InspectionCanvas(
    viewModel: InspectionViewModel,
    modifier: Modifier = Modifier
) {
    val bitmap by viewModel.currentBitmap.collectAsState()
    val activeBoxesState = viewModel.activeBoxes.collectAsState()
    val activeBoxes by activeBoxesState
    val isCorrectionMode by viewModel.isCorrectionMode.collectAsState()
    val selectedBoxIndexState = viewModel.selectedBoxIndex.collectAsState()
    val selectedBoxIndex by selectedBoxIndexState
    val tempBoxState = viewModel.tempDrawingBox.collectAsState()
    val tempBox by tempBoxState

    // BRAND NEW ENTERPRISE STATES
    val isLiveCameraActive by viewModel.isLiveCameraActive.collectAsState()
    val isSplitCompareActive by viewModel.isSplitCompareActive.collectAsState()
    var showOriginalOnlyInCompare by remember { mutableStateOf(false) }

    var sweepY by remember { mutableStateOf(0f) }
    LaunchedEffect(isLiveCameraActive) {
        if (isLiveCameraActive) {
            while (true) {
                for (i in 0..100 step 2) {
                    sweepY = i / 100f
                    kotlinx.coroutines.delay(20)
                }
                for (i in 100 downTo 0 step 2) {
                    sweepY = i / 100f
                    kotlinx.coroutines.delay(20)
                }
            }
        }
    }

    var canvasWidthPx by remember { mutableStateOf(1f) }
    var canvasHeightPx by remember { mutableStateOf(1f) }

    // Dropdown/Dialog state for labeling a newly drawn box
    var showLabelPrompt by remember { mutableStateOf(false) }
    var newlyDrawnCoords by remember { mutableStateOf<List<Int>?>(null) }
    val labelOptions = listOf(
        "Hole / Tear", "Slub", "Contaminated Thread", "Snag",
        "Skipped Stitch", "Open Seam", "Seam Puckering", "Uneven Stitching", "Wavy Seam",
        "Oil Spot", "Dye Stain", "Uncut Thread", "Iron Burn", "Tailor Mark"
    )

    // Gesture type tracking
    var activeGestureType by remember { mutableStateOf("NONE") } // "MOVE", "RESIZE_TL", "RESIZE_TR", "RESIZE_BL", "RESIZE_BR", "DRAW", "NONE"
    var dragStartPoint by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current

    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = getAdaptiveBorderColor()
    val textMuted = getAdaptiveMutedTextColor()
    val onSurface = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("inspection_canvas_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Before / After Compare layout bar inside card header
            if (isSplitCompareActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(6.dp)
                        ) {}
                        Text(
                            text = "COMPARISON ASSISTANT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = { showOriginalOnlyInCompare = true },
                            shape = RoundedCornerShape(8.dp),
                            color = if (showOriginalOnlyInCompare) MaterialTheme.colorScheme.primary else Color.Transparent,
                            border = BorderStroke(1.dp, if (showOriginalOnlyInCompare) Color.Transparent else borderCol)
                        ) {
                            Text(
                                text = "BEFORE (RAW)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = if (showOriginalOnlyInCompare) Color.White else textMuted
                            )
                        }

                        Surface(
                            onClick = { showOriginalOnlyInCompare = false },
                            shape = RoundedCornerShape(8.dp),
                            color = if (!showOriginalOnlyInCompare) MaterialTheme.colorScheme.primary else Color.Transparent,
                            border = BorderStroke(1.dp, if (!showOriginalOnlyInCompare) Color.Transparent else borderCol)
                        ) {
                            Text(
                                text = "AFTER (ANALYZED)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = if (!showOriginalOnlyInCompare) Color.White else textMuted
                            )
                        }
                    }
                }
            }

            // Label status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLiveCameraActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFFE11D48), CircleShape) // Ruby pulsing recording dot
                        )
                    }
                    Text(
                        text = if (isLiveCameraActive) "REAL-TIME MATRIX VISION STREAM" else "LIVE INTERACTIVE CANVAS WORKSPACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = textMuted,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLiveCameraActive) Color(0xFFE11D48).copy(alpha = 0.1f) else if (isCorrectionMode) DefectOilSpot.copy(alpha = 0.15f) else Color(0xFF4A4458).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isLiveCameraActive) Color(0xFFE11D48) else if (isCorrectionMode) DefectOilSpot else borderCol)
                ) {
                    Text(
                        text = if (isLiveCameraActive) "LIVE ACQUISITION SCAN" else if (isCorrectionMode) "SUPERVISOR EDIT ACTIVE" else "MONITORING MODE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isLiveCameraActive) Color(0xFFE11D48) else if (isCorrectionMode) DefectOilSpot else textMuted,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .onGloballyPositioned { coordinates ->
                        canvasWidthPx = coordinates.size.width.toFloat()
                        canvasHeightPx = coordinates.size.height.toFloat()
                    }
                    .pointerInput(isCorrectionMode, showOriginalOnlyInCompare, bitmap, canvasWidthPx, canvasHeightPx) {
                        if (!isCorrectionMode || showOriginalOnlyInCompare) return@pointerInput

                        // Calculate dynamic scale factors inside pixel coordinate domain safely
                        val safeCanvasW = if (canvasWidthPx > 0f) canvasWidthPx else 1f
                        val safeCanvasH = if (canvasHeightPx > 0f) canvasHeightPx else 1f

                        val (displayedWidthPx, displayedHeightPx) = if (bitmap != null) {
                            val imgW = bitmap!!.width.toFloat()
                            val imgH = bitmap!!.height.toFloat()
                            if (imgW > 0f && imgH > 0f) {
                                val imageAspect = imgW / imgH
                                val containerAspect = safeCanvasW / safeCanvasH
                                if (imageAspect > containerAspect) {
                                    Pair(safeCanvasW, safeCanvasW / imageAspect)
                                } else {
                                    Pair(safeCanvasH * imageAspect, safeCanvasH)
                                }
                            } else {
                                Pair(safeCanvasW, safeCanvasH)
                            }
                        } else {
                            Pair(safeCanvasW, safeCanvasH)
                        }

                        val offsetXPx = if (bitmap != null) (safeCanvasW - displayedWidthPx) / 2 else 0f
                        val offsetYPx = if (bitmap != null) (safeCanvasH - displayedHeightPx) / 2 else 0f

                        val w = if (displayedWidthPx > 0f) displayedWidthPx else 1f
                        val h = if (displayedHeightPx > 0f) displayedHeightPx else 1f

                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStartPoint = offset
                                val relativeX = offset.x - offsetXPx
                                val relativeY = offset.y - offsetYPx
                                val pctX = (relativeX / w).let { if (it.isNaN()) 0f else it }.coerceIn(0f, 1f)
                                val pctY = (relativeY / h).let { if (it.isNaN()) 0f else it }.coerceIn(0f, 1f)
                                val touchXNormalized = (pctX * 1000).toInt()
                                val touchYNormalized = (pctY * 1000).toInt()

                                val currentActiveBoxes = activeBoxesState.value
                                val currentSelectedBoxIndex = selectedBoxIndexState.value ?: -1

                                // 1. Check if we tapped on handles/corners of the SELECTED box to RESIZE
                                if (currentSelectedBoxIndex >= 0 && currentSelectedBoxIndex < currentActiveBoxes.size) {
                                    val selBox = currentActiveBoxes[currentSelectedBoxIndex]
                                    val threshold = 35 // normalized units tolerance (~3.5% area screen)

                                    when {
                                        abs(touchXNormalized - selBox.xMin) < threshold && abs(touchYNormalized - selBox.yMin) < threshold -> {
                                            activeGestureType = "RESIZE_TL"
                                        }
                                        abs(touchXNormalized - selBox.xMax) < threshold && abs(touchYNormalized - selBox.yMin) < threshold -> {
                                            activeGestureType = "RESIZE_TR"
                                        }
                                        abs(touchXNormalized - selBox.xMin) < threshold && abs(touchYNormalized - selBox.yMax) < threshold -> {
                                            activeGestureType = "RESIZE_BL"
                                        }
                                        abs(touchXNormalized - selBox.xMax) < threshold && abs(touchYNormalized - selBox.yMax) < threshold -> {
                                            activeGestureType = "RESIZE_BR"
                                        }
                                        // inside box boundaries -> MOVE
                                        touchXNormalized in selBox.xMin..selBox.xMax && touchYNormalized in selBox.yMin..selBox.yMax -> {
                                            activeGestureType = "MOVE"
                                        }
                                        else -> {
                                            // Clicked outside: check if clicked on any OTHER box to select, else DRAW
                                            var clickedIndex = -1
                                            for (i in currentActiveBoxes.indices.reversed()) {
                                                val box = currentActiveBoxes[i]
                                                if (touchXNormalized in box.xMin..box.xMax && touchYNormalized in box.yMin..box.yMax) {
                                                    clickedIndex = i
                                                    break
                                                }
                                            }

                                            if (clickedIndex != -1) {
                                                viewModel.selectBox(clickedIndex)
                                                activeGestureType = "MOVE"
                                            } else {
                                                activeGestureType = "DRAW"
                                            }
                                        }
                                    }
                                } else {
                                    // 2. Check if we tapped INSIDE any other existing box to SELECT it
                                    var clickedIndex = -1
                                    for (i in currentActiveBoxes.indices.reversed()) {
                                        val box = currentActiveBoxes[i]
                                        if (touchXNormalized in box.xMin..box.xMax && touchYNormalized in box.yMin..box.yMax) {
                                            clickedIndex = i
                                            break
                                        }
                                    }

                                    if (clickedIndex != -1) {
                                        viewModel.selectBox(clickedIndex)
                                        activeGestureType = "MOVE"
                                    } else {
                                        activeGestureType = "DRAW"
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val currentOffset = change.position
                                val relativeStartX = dragStartPoint.x - offsetXPx
                                val relativeStartY = dragStartPoint.y - offsetYPx
                                val relativeCurrentX = currentOffset.x - offsetXPx
                                val relativeCurrentY = currentOffset.y - offsetYPx

                                val pctStartX = (relativeStartX / w).let { if (it.isNaN()) 0f else it }.coerceIn(0f, 1f)
                                val pctStartY = (relativeStartY / h).let { if (it.isNaN()) 0f else it }.coerceIn(0f, 1f)
                                val pctCurrentX = (relativeCurrentX / w).let { if (it.isNaN()) 0f else it }.coerceIn(0f, 1f)
                                val pctCurrentY = (relativeCurrentY / h).let { if (it.isNaN()) 0f else it }.coerceIn(0f, 1f)

                                val startXNorm = (pctStartX * 1000).toInt()
                                val startYNorm = (pctStartY * 1000).toInt()
                                val currentXNorm = (pctCurrentX * 1000).toInt()
                                val currentYNorm = (pctCurrentY * 1000).toInt()

                                val currentSelectedBoxIndex = selectedBoxIndexState.value ?: -1
                                val currentActiveBoxes = activeBoxesState.value

                                when (activeGestureType) {
                                    "MOVE" -> {
                                        val dxPct = if (w > 0f) dragAmount.x / w else 0f
                                        val dyPct = if (h > 0f) dragAmount.y / h else 0f
                                        viewModel.moveSelectedBox(currentSelectedBoxIndex, dxPct, dyPct)
                                    }
                                    "DRAW" -> {
                                        val ymin = minOf(startYNorm, currentYNorm)
                                        val xmin = minOf(startXNorm, currentXNorm)
                                        val ymax = maxOf(startYNorm, currentYNorm)
                                        val xmax = maxOf(startXNorm, currentXNorm)
                                        viewModel.setTempDrawingBox(ymin, xmin, ymax, xmax)
                                    }
                                    "RESIZE_TL" -> {
                                        if (currentSelectedBoxIndex in currentActiveBoxes.indices) {
                                            val currentBox = currentActiveBoxes[currentSelectedBoxIndex]
                                            viewModel.resizeSelectedBox(currentSelectedBoxIndex, currentYNorm, currentXNorm, currentBox.yMax, currentBox.xMax)
                                        }
                                    }
                                    "RESIZE_TR" -> {
                                        if (currentSelectedBoxIndex in currentActiveBoxes.indices) {
                                            val currentBox = currentActiveBoxes[currentSelectedBoxIndex]
                                            viewModel.resizeSelectedBox(currentSelectedBoxIndex, currentYNorm, currentBox.xMin, currentBox.yMax, currentXNorm)
                                        }
                                    }
                                    "RESIZE_BL" -> {
                                        if (currentSelectedBoxIndex in currentActiveBoxes.indices) {
                                            val currentBox = currentActiveBoxes[currentSelectedBoxIndex]
                                            viewModel.resizeSelectedBox(currentSelectedBoxIndex, currentBox.yMin, currentXNorm, currentYNorm, currentBox.xMax)
                                        }
                                    }
                                    "RESIZE_BR" -> {
                                        if (currentSelectedBoxIndex in currentActiveBoxes.indices) {
                                            val currentBox = currentActiveBoxes[currentSelectedBoxIndex]
                                            viewModel.resizeSelectedBox(currentSelectedBoxIndex, currentBox.yMin, currentBox.xMin, currentYNorm, currentXNorm)
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                val currentTempBox = tempBoxState.value
                                if (activeGestureType == "DRAW" && currentTempBox != null) {
                                    val finalBox = currentTempBox
                                    val widthVal = abs(finalBox[3] - finalBox[1])
                                    val heightVal = abs(finalBox[2] - finalBox[0])

                                    if (widthVal > 15 && heightVal > 15) {
                                        // Save drawn coords temporarily
                                        newlyDrawnCoords = finalBox
                                        showLabelPrompt = true
                                    } else {
                                        viewModel.clearTempDrawingBox()
                                    }
                                }
                                activeGestureType = "NONE"
                            }
                        )
                    }
            ) {
                // Calculate dynamic Dp display sizes for correct canvas overlay layout matching ContentScale.Fit
                val containerWidthDp = maxWidth.value
                val containerHeightDp = maxHeight.value

                val (displayedWidthDp, displayedHeightDp) = if (bitmap != null) {
                    val imgW = bitmap!!.width.toFloat()
                    val imgH = bitmap!!.height.toFloat()
                    if (imgW > 0f && imgH > 0f) {
                        val imageAspect = imgW / imgH
                        val containerAspect = containerWidthDp / containerHeightDp
                        if (imageAspect > containerAspect) {
                            Pair(containerWidthDp, containerWidthDp / imageAspect)
                        } else {
                            Pair(containerHeightDp * imageAspect, containerHeightDp)
                        }
                    } else {
                        Pair(containerWidthDp, containerHeightDp)
                    }
                } else {
                    Pair(containerWidthDp, containerHeightDp)
                }

                val offsetXDp = if (bitmap != null) (containerWidthDp - displayedWidthDp) / 2 else 0f
                val offsetYDp = if (bitmap != null) (containerHeightDp - displayedHeightDp) / 2 else 0f

                val displayedWidth = displayedWidthDp.dp
                val displayedHeight = displayedHeightDp.dp
                val offsetX = offsetXDp.dp
                val offsetY = offsetYDp.dp

                // Base Fabric Image under test
                bitmap?.let {
                    val imgBitmap = remember(it) { it.asImageBitmap() }
                    Image(
                        bitmap = imgBitmap,
                        contentDescription = "Fabric Visual Feed",
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("fabric_under_test"),
                        contentScale = ContentScale.Fit
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("NO ACTIVE IMAGE FEED LOADED", color = textMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Render bounding boxes if we are not hiding them in comparison raw view
                if (!showOriginalOnlyInCompare) {
                    activeBoxes.forEachIndexed { index, box ->
                        val color = when {
                            box.label.contains("Hole", ignoreCase = true) || box.label.contains("Tear", ignoreCase = true) -> DefectHole
                            box.label.contains("Oil", ignoreCase = true) || box.label.contains("Grease", ignoreCase = true) -> DefectOilSpot
                            box.label.contains("Stain", ignoreCase = true) || box.label.contains("Dye", ignoreCase = true) -> DefectStain
                            else -> DefectTornThread
                        }

                        // Convert normalized bounds (0-1000) into actual UI px coordinates relative to the fit-scaled image canvas safely
                        val realXMin = minOf(box.xMin, box.xMax)
                        val realXMax = maxOf(box.xMin, box.xMax)
                        val realYMin = minOf(box.yMin, box.yMax)
                        val realYMax = maxOf(box.yMin, box.yMax)

                        val boundsLeft = offsetX + displayedWidth * (realXMin / 1000f)
                        val boundsTop = offsetY + displayedHeight * (realYMin / 1000f)
                        val boundsWidth = displayedWidth * ((realXMax - realXMin) / 1000f)
                        val boundsHeight = displayedHeight * ((realYMax - realYMin) / 1000f)

                        val isSelected = (selectedBoxIndex == index)

                        Box(
                            modifier = Modifier
                                .offset(x = boundsLeft, y = boundsTop)
                                .size(width = maxOf(boundsWidth, 14.dp), height = maxOf(boundsHeight, 14.dp))
                                .border(
                                    border = BorderStroke(
                                        width = if (isSelected) 3.dp else 2.dp,
                                        color = if (isSelected) Color.White else color
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .testTag("defect_box_$index")
                        ) {
                            // Resize Dot handles on corners of selected box
                            if (isSelected && isCorrectionMode) {
                                // Top-Left Dot
                                Box(modifier = Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp).size(8.dp).background(Color.White, CircleShape))
                                // Top-Right Dot
                                Box(modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(8.dp).background(Color.White, CircleShape))
                                // Bottom-Left Dot
                                Box(modifier = Modifier.align(Alignment.BottomStart).offset((-4).dp, 4.dp).size(8.dp).background(Color.White, CircleShape))
                                // Bottom-Right Dot
                                Box(modifier = Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(8.dp).background(Color.White, CircleShape))
                            }
                        }

                        // Render label tag as a sibling above/below the bounding box so it never clips by small widths
                        val labelTop = if (boundsTop < 22.dp) boundsTop + maxOf(boundsHeight, 14.dp) + 4.dp else boundsTop - 22.dp
                        Box(
                            modifier = Modifier
                                .offset(x = boundsLeft, y = labelTop)
                                .widthIn(min = 120.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) Color.White else color,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isSelected) Color.Black else Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${box.label} (${(box.confidence * 100).toInt()}%)",
                                    style = androidx.compose.ui.text.TextStyle(
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (isCorrectionMode) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete box",
                                        tint = if (isSelected) Color.Black else Color.White,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable {
                                                viewModel.deleteBox(index)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // Render dynamic TEMPORARY drawing box bounds in real-time
                tempBox?.let { coords ->
                    val dl = offsetX + displayedWidth * (coords[1] / 1000f)
                    val dt = offsetY + displayedHeight * (coords[0] / 1000f)
                    val dw = displayedWidth * ((coords[3] - coords[1]) / 1000f)
                    val dh = displayedHeight * ((coords[2] - coords[0]) / 1000f)

                    Box(
                        modifier = Modifier
                            .offset(x = dl, y = dt)
                            .size(width = dw, height = dh)
                            .border(BorderStroke(1.5.dp, Color.White), RoundedCornerShape(1.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.15f),
                                size = size
                            )
                        }
                    }
                }

                // Render Live Camera laser sweeping line mapped to the actual image boundaries
                if (isLiveCameraActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .offset(y = offsetY + displayedHeight * sweepY)
                            .height(3.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFE11D48).copy(alpha = 0.1f),
                                        Color(0xFFE11D48),
                                        Color(0xFFE11D48).copy(alpha = 0.1f)
                                    )
                                )
                            )
                    )
                }
            }

            // Quick instruction help footer inside the canvas Card
            Text(
                text = if (isLiveCameraActive) {
                    "★ Live stream is active! Real-time telemetry is updating. Click 'CAPTURE RUNNING FRAME' below to log frame."
                } else if (isCorrectionMode) {
                    "★ Drag center of any box to reposition. Drag corners to resize. Drag blank space to create a NEW box."
                } else {
                    "ℹ Enable Supervisor Correction Mode below to adjust bounding boxes or click-n-drag to catalog new errors."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isCorrectionMode) DefectOilSpot else textMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    // Modal popup to request a classification label for a newly drawn defect box
    if (showLabelPrompt && newlyDrawnCoords != null) {
        AlertDialog(
            onDismissRequest = {
                showLabelPrompt = false
                viewModel.clearTempDrawingBox()
            },
            title = { Text("Select Defect Category", color = TextWhite) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Identify the defect category for the freshly drawn bounding box:", color = TextMuted, modifier = Modifier.padding(bottom = 12.dp))
                    labelOptions.forEach { option ->
                        Button(
                            onClick = {
                                val coords = newlyDrawnCoords!!
                                viewModel.addNewDrawnBox(
                                    ymin = coords[0],
                                    xmin = coords[1],
                                    ymax = coords[2],
                                    xmax = coords[3],
                                    label = option
                                )
                                showLabelPrompt = false
                                newlyDrawnCoords = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CustomGreyBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(option, color = TextWhite)
                                val badgeColor = when {
                                    option.contains("Hole", ignoreCase = true) || option.contains("Tear", ignoreCase = true) -> DefectHole
                                    option.contains("Oil", ignoreCase = true) || option.contains("Grease", ignoreCase = true) -> DefectOilSpot
                                    option.contains("Stain", ignoreCase = true) || option.contains("Dye", ignoreCase = true) -> DefectStain
                                    else -> DefectTornThread
                                }
                                Box(modifier = Modifier.size(10.dp).background(badgeColor, CircleShape))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLabelPrompt = false
                        viewModel.clearTempDrawingBox()
                    }
                ) {
                    Text("Cancel", color = DefectHole)
                }
            },
            containerColor = PanelBg
        )
    }
}
