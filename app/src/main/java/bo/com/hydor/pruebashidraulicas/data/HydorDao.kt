package bo.com.hydor.pruebashidraulicas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HydorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: SectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTest(test: HydraulicTestEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingRaw(reading: PressureReadingEntity): Long

    @Query("SELECT COUNT(*) FROM readings WHERE testId = :testId")
    suspend fun getReadingCount(testId: Long): Int

    @Transaction
    suspend fun insertReading(reading: PressureReadingEntity): Long {
        val existingCount = getReadingCount(reading.testId)
        if (existingCount == 0 && reading.source != "PROGRAMMED") {
            val test = getTest(reading.testId)
            if (test != null) {
                insertReadingRaw(
                    PressureReadingEntity(
                        testId = reading.testId,
                        capturedAt = if (test.startedAt > 0L) test.startedAt else (reading.capturedAt - 1L),
                        detectedPressureBar = null,
                        confirmedPressureBar = test.targetPressureBar,
                        imagePath = null,
                        detectionConfidence = null,
                        source = "PROGRAMMED"
                    )
                )
            }
        }
        return insertReadingRaw(reading)
    }

    @Query("SELECT * FROM projects WHERE name = :name LIMIT 1")
    suspend fun findProjectByName(name: String): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    suspend fun getProjects(): List<ProjectEntity>

    @Query("SELECT * FROM sections WHERE projectId = :projectId ORDER BY id DESC")
    suspend fun getSectionsForProject(projectId: Long): List<SectionEntity>

    @Query("SELECT * FROM tests WHERE id = :testId LIMIT 1")
    suspend fun getTest(testId: Long): HydraulicTestEntity?

    @Query("SELECT * FROM tests WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveTest(): HydraulicTestEntity?

    @Query("SELECT * FROM sections WHERE id = :sectionId LIMIT 1")
    suspend fun getSection(sectionId: Long): SectionEntity?

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProject(projectId: Long): ProjectEntity?

    @Query("SELECT * FROM readings WHERE testId = :testId ORDER BY capturedAt ASC")
    suspend fun getReadings(testId: Long): List<PressureReadingEntity>

    @Query("UPDATE readings SET confirmedPressureBar = :pressureBar WHERE id = :readingId")
    suspend fun updateConfirmedReading(readingId: Long, pressureBar: Double)

    @Query("DELETE FROM readings WHERE id = :readingId AND source != 'PROGRAMMED'")
    suspend fun deleteEditableReading(readingId: Long)

    @Query("UPDATE tests SET startedAt = :startedAt, status = 'IN_PROGRESS' WHERE id = :testId")
    suspend fun startTest(testId: Long, startedAt: Long)

    @Query("UPDATE tests SET finishedAt = :finishedAt, status = :status WHERE id = :testId")
    suspend fun finishTest(testId: Long, finishedAt: Long, status: String)

    @Query("SELECT * FROM tests ORDER BY id DESC")
    suspend fun getAllTests(): List<HydraulicTestEntity>
}
