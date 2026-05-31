package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import coil.compose.AsyncImage
import com.example.data.DefectBox
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun InspectionCanvas(
    viewModel: InspectionViewModel,
    modifier: Modifier = Modifier
) {
    val bitmap by viewModel.currentBitmap.collectAsState()
    val activeBoxes by viewModel.activeBoxes.collectAsState()
    val isCorrectionMode by viewModel.isCorrectionMode.collectAsState()
    val selectedBoxIndex by viewModel.selectedBoxIndex.collectAsState()
    val tempBox by viewModel.tempDrawingBox.collectAsState()

    var canvasWidthPx by remember { mutableStateOf(1f) }
    var canvasHeightPx by remember { mutableStateOf(1f) }

    // Dropdown/Dialog state for labeling a newly drawn box
    var showLabelPrompt by remember { mutableStateOf(false) }
    var newlyDrawnCoords by remember { mutableStateOf<List<Int>?>(null) }
    val labelOptions = listOf("Hole", "Oil Spot", "Stain", "Torn Thread")

    // Gesture type tracking
    var activeGestureType by remember { mutableStateOf("NONE") } // "MOVE", "RESIZE_TL", "RESIZE_TR", "RESIZE_BL", "RESIZE_BR", "DRAW", "NONE"
    var dragStartPoint by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("inspection_canvas_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        border = BorderStroke(1.dp, CustomGreyBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Label status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE INTERACTIVE CANVAS WORKSPACE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCorrectionMode) DefectOilSpot.copy(alpha = 0.15f) else Color(0xFF4A4458).copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, if (isCorrectionMode) DefectOilSpot else CustomGreyBorder)
                ) {
                    Text(
                        text = if (isCorrectionMode) "SUPERVISOR EDIT ACTIVE" else "MONITORING MODE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrectionMode) DefectOilSpot else TextMuted,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .onGloballyPositioned { coordinates ->
                        canvasWidthPx = coordinates.size.width.toFloat()
                        canvasHeightPx = coordinates.size.height.toFloat()
                    }
                    .pointerInput(isCorrectionMode) {
                        if (!isCorrectionMode) return@pointerInput

                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStartPoint = offset
                                val touchXNormalized = ((offset.x / canvasWidthPx) * 1000).toInt()
                                val touchYNormalized = ((offset.y / canvasHeightPx) * 1000).toInt()

                                // 1. Check if we tapped on handles/corners of the SELECTED box to RESIZE
                                if (selectedBoxIndex != null && selectedBoxIndex!! >= 0 && selectedBoxIndex!! < activeBoxes.size) {
                                    val selBox = activeBoxes[selectedBoxIndex!!]
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
                                            // Clicked outside: start DRAW_NEW
                                            activeGestureType = "DRAW"
                                        }
                                    }
                                } else {
                                    // 2. Check if we tapped INSIDE any other existing box to SELECT it
                                    var clickedIndex = -1
                                    for (i in activeBoxes.indices.reversed()) {
                                        val box = activeBoxes[i]
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
                                val startXNorm = ((dragStartPoint.x / canvasWidthPx) * 1000).toInt()
                                val startYNorm = ((dragStartPoint.y / canvasHeightPx) * 1000).toInt()
                                val currentXNorm = ((currentOffset.x / canvasWidthPx) * 1000).toInt()
                                val currentYNorm = ((currentOffset.y / canvasHeightPx) * 1000).toInt()

                                when (activeGestureType) {
                                    "MOVE" -> {
                                        val dxPct = dragAmount.x / canvasWidthPx
                                        val dyPct = dragAmount.y / canvasHeightPx
                                        viewModel.moveSelectedBox(selectedBoxIndex ?: -1, dxPct, dyPct)
                                    }
                                    "DRAW" -> {
                                        val ymin = minOf(startYNorm, currentYNorm)
                                        val xmin = minOf(startXNorm, currentXNorm)
                                        val ymax = maxOf(startYNorm, currentYNorm)
                                        val xmax = maxOf(startXNorm, currentXNorm)
                                        viewModel.setTempDrawingBox(ymin, xmin, ymax, xmax)
                                    }
                                    "RESIZE_TL" -> {
                                        val currentBox = activeBoxes[selectedBoxIndex!!]
                                        viewModel.resizeSelectedBox(selectedBoxIndex!!, currentYNorm, currentXNorm, currentBox.yMax, currentBox.xMax)
                                    }
                                    "RESIZE_TR" -> {
                                        val currentBox = activeBoxes[selectedBoxIndex!!]
                                        viewModel.resizeSelectedBox(selectedBoxIndex!!, currentYNorm, currentBox.xMin, currentBox.yMax, currentXNorm)
                                    }
                                    "RESIZE_BL" -> {
                                        val currentBox = activeBoxes[selectedBoxIndex!!]
                                        viewModel.resizeSelectedBox(selectedBoxIndex!!, currentBox.yMin, currentXNorm, currentYNorm, currentBox.xMax)
                                    }
                                    "RESIZE_BR" -> {
                                        val currentBox = activeBoxes[selectedBoxIndex!!]
                                        viewModel.resizeSelectedBox(selectedBoxIndex!!, currentBox.yMin, currentBox.xMin, currentYNorm, currentXNorm)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (activeGestureType == "DRAW" && tempBox != null) {
                                    val finalBox = tempBox!!
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
                // Base Fabric Image under test
                bitmap?.let {
                    AsyncImage(
                        model = it,
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
                    Text("NO ACTIVE IMAGE FEED LOADED", color = TextMuted)
                }

                // Render bounding boxes
                activeBoxes.forEachIndexed { index, box ->
                    val color = when (box.label) {
                        "Hole" -> DefectHole
                        "Oil Spot" -> DefectOilSpot
                        "Stain" -> DefectStain
                        else -> DefectTornThread
                    }

                    // Convert normalized bounds (0-1000) into actual UI px coordinates
                    val boundsLeft = maxWidth * (box.xMin / 1000f)
                    val boundsTop = maxHeight * (box.yMin / 1000f)
                    val boundsWidth = maxWidth * ((box.xMax - box.xMin) / 1000f)
                    val boundsHeight = maxHeight * ((box.yMax - box.yMin) / 1000f)

                    val isSelected = isCorrectionMode && (selectedBoxIndex == index)

                    Box(
                        modifier = Modifier
                            .offset(x = boundsLeft, y = boundsTop)
                            .size(width = boundsWidth, height = boundsHeight)
                            .border(
                                border = BorderStroke(
                                    width = if (isSelected) 3.dp else 2.dp,
                                    color = if (isSelected) Color.White else color
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .testTag("defect_box_$index")
                    ) {
                        // Label tag at the top of the defect bounding box
                        Row(
                            modifier = Modifier
                                .offset(y = (-18).dp)
                                .background(
                                    if (isSelected) Color.White else color,
                                    RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${box.label} (${(box.confidence * 100).toInt()}%)",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            )
                            if (isCorrectionMode) {
                                Spacer(modifier = Modifier.width(4.dp))
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

                        // Resize Dot handles on corners of selected box
                        if (isSelected) {
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
                }

                // Render dynamic TEMPORARY drawing box bounds in real-time
                tempBox?.let { coords ->
                    val dl = maxWidth * (coords[1] / 1000f)
                    val dt = maxHeight * (coords[0] / 1000f)
                    val dw = maxWidth * ((coords[3] - coords[1]) / 1000f)
                    val dh = maxHeight * ((coords[2] - coords[0]) / 1000f)

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
            }

            // Quick instruction help footer inside the canvas Card
            Text(
                text = if (isCorrectionMode) {
                    "★ Drag center of any box to reposition. Drag corners to resize. Drag blank space to create a NEW box."
                } else {
                    "ℹ Enable Supervisor Correction Mode below to adjust bounding boxes or click-n-drag to catalog new errors."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isCorrectionMode) DefectOilSpot else TextMuted,
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
                Column {
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
                                val badgeColor = when (option) {
                                    "Hole" -> DefectHole
                                    "Oil Spot" -> DefectOilSpot
                                    "Stain" -> DefectStain
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
