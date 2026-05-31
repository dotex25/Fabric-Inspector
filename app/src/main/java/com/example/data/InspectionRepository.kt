package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class InspectionRepository(private val defectDao: DefectDao) {

    val allScans: Flow<List<InspectionScan>> = defectDao.getAllScansFlow()

    fun getBoxesForScan(scanId: String): Flow<List<DefectBox>> {
        return defectDao.getBoxesForScanFlow(scanId)
    }

    suspend fun listScansDirectly(): List<InspectionScan> {
        return defectDao.getAllScans()
    }

    suspend fun fetchBoxesDirectly(scanId: String): List<DefectBox> {
        return defectDao.getBoxesForScan(scanId)
    }

    suspend fun getCorrectionsMemory(): List<DefectBox> {
        return defectDao.getCorrectionsForFewShot()
    }

    suspend fun saveNewScan(imageUri: String, imageName: String, boxes: List<DefectBox>, isClean: Boolean): String {
        val scanId = UUID.randomUUID().toString()
        val status = if (isClean) "CLEAN" else "DEFECTS_FOUND"
        val scan = InspectionScan(
            id = scanId,
            imageUri = imageUri,
            imageName = imageName,
            status = status,
            defectCount = boxes.size
        )
        defectDao.insertScan(scan)
        // Set the scan ID for each box before inserting
        val preparedBoxes = boxes.map { it.copy(scanId = scanId) }
        defectDao.insertBoxes(preparedBoxes)
        return scanId
    }

    suspend fun saveCorrection(scanId: String, updatedBoxes: List<DefectBox>) {
        // Find existing scan
        val scans = defectDao.getAllScans()
        val existingScan = scans.find { it.id == scanId }
        
        if (existingScan != null) {
            val status = if (updatedBoxes.isEmpty()) "CLEAN" else "CORRECTED"
            val updatedScan = existingScan.copy(
                status = status,
                defectCount = updatedBoxes.size
            )
            defectDao.updateScan(updatedScan)
            defectDao.replaceBoxesForScan(scanId, updatedBoxes)
        }
    }

    suspend fun deleteScan(scanId: String) {
        defectDao.deleteScanWithBoxes(scanId)
    }

    suspend fun clearDatabase() {
        defectDao.clearAllData()
    }
}
