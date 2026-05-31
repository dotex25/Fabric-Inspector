package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inspection_scans")
data class InspectionScan(
    @PrimaryKey val id: String,
    val imageUri: String,
    val imageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "CLEAN", "DEFECTS_FOUND", "CORRECTED"
    val defectCount: Int
)

@Entity(tableName = "defect_boxes")
data class DefectBox(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val scanId: String,
    val yMin: Int, // 0-1000 normalized
    val xMin: Int, // 0-1000 normalized
    val yMax: Int,
    val xMax: Int,
    val label: String,
    val confidence: Float,
    val isUserCreatedOrModified: Boolean = false
)
