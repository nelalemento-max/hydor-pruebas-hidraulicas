package bo.com.hydor.pruebashidraulicas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        SectionEntity::class,
        HydraulicTestEntity::class,
        PressureReadingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class HydorDatabase : RoomDatabase() {
    companion object {
        @Volatile private var INSTANCE: HydorDatabase? = null

        fun getInstance(context: Context): HydorDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HydorDatabase::class.java,
                    "hydor_field.db"
                ).build().also { INSTANCE = it }
            }
    }
}
