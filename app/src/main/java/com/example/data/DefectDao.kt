package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DefectDao {
    @Query("SELECT * FROM inspection_scans ORDER BY timestamp DESC")
    fun getAllScansFlow(): Flow<List<InspectionScan>>

    @Query("SELECT * FROM inspection_scans ORDER BY timestamp DESC")
    suspend fun getAllScans(): List<InspectionScan>

    @Query("SELECT * FROM defect_boxes WHERE scanId = :scanId")
    fun getBoxesForScanFlow(scanId: String): Flow<List<DefectBox>>

    @Query("SELECT * FROM defect_boxes WHERE scanId = :scanId")
    suspend fun getBoxesForScan(scanId: String): List<DefectBox>

    @Query("SELECT * FROM defect_boxes WHERE isUserCreatedOrModified = 1")
    suspend fun getCorrectionsForFewShot(): List<DefectBox>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: InspectionScan)

    @Update
    suspend fun updateScan(scan: InspectionScan)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoxes(boxes: List<DefectBox>)

    @Query("DELETE FROM defect_boxes WHERE scanId = :scanId")
    suspend fun deleteBoxesForScan(scanId: String)

    @Transaction
    suspend fun replaceBoxesForScan(scanId: String, boxes: List<DefectBox>) {
        deleteBoxesForScan(scanId)
        insertBoxes(boxes)
    }

    @Query("DELETE FROM inspection_scans WHERE id = :scanId")
    suspend fun deleteScanOnly(scanId: String)

    @Transaction
    suspend fun deleteScanWithBoxes(scanId: String) {
        deleteBoxesForScan(scanId)
        deleteScanOnly(scanId)
    }

    @Query("DELETE FROM inspection_scans")
    suspend fun deleteAllScans()

    @Query("DELETE FROM defect_boxes")
    suspend fun deleteAllBoxes()

    @Transaction
    suspend fun clearAllData() {
        deleteAllBoxes()
        deleteAllScans()
    }
}
