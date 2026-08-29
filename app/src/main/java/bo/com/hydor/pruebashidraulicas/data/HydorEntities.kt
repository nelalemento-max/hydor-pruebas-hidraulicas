package bo.com.hydor.pruebashidraulicas.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val company: String = "",
    val location: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sections")
data class SectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val battery: String,
    val neighborhood: String,
    val startValve: String,
    val endValve: String,
    val diameterInches: String,
    val lengthMeters: Double
)

@Entity(tableName = "tests")
data class HydraulicTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long,
    val operatorName: String,
    val nominalPressureBar: Double,
    val targetPressureBar: Double,
    val maxAllowedDropBar: Double,
    val durationMinutes: Int,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String = "IN_PROGRESS"
)

@Entity(tableName = "readings")
data class PressureReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testId: Long,
    val capturedAt: Long,
    val detectedPressureBar: Double?,
    val confirmedPressureBar: Double,
    val imagePath: String?,
    val detectionConfidence: Double?,
    val source: String = "CAMERA_CONFIRMED"
)
