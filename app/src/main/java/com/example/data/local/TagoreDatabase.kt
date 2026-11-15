package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.UUID

@Database(
    entities = [
        MemoryEntity::class,
        ConversationMessageEntity::class,
        UserPreferencesEntity::class,
        ProductivityEntity::class,
        SmartDeviceEntity::class,
        EcosystemDeviceEntity::class,
        EncryptedLogEntity::class,
        SyncQueueEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class TagoreDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun productivityDao(): ProductivityDao
    abstract fun smartDeviceDao(): SmartDeviceDao
    abstract fun ecosystemDeviceDao(): EcosystemDeviceDao
    abstract fun encryptedLogDao(): EncryptedLogDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: TagoreDatabase? = null

        fun getInstance(context: Context): TagoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TagoreDatabase::class.java,
                    "tagore_offline_vault.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        suspend fun seedHardwareDefaultsIfEmpty(db: TagoreDatabase) {
            val prefsDao = db.userPreferencesDao()
            if (prefsDao.getPreferences() == null) {
                prefsDao.upsertPreferences(UserPreferencesEntity())
            }

            val smartDao = db.smartDeviceDao()
            if (smartDao.getAllDevices().isEmpty()) {
                smartDao.insertAll(
                    listOf(
                        SmartDeviceEntity(
                            remoteId = "dev_living_light_${UUID.randomUUID().toString().take(6)}",
                            name = "Living Room Main Light",
                            room = "Living Room",
                            category = "LIGHT",
                            isOn = true,
                            level = 80,
                            temperature = 24,
                            isLocked = false,
                            pendingSync = false
                        ),
                        SmartDeviceEntity(
                            remoteId = "dev_bedroom_ac_${UUID.randomUUID().toString().take(6)}",
                            name = "Bedroom Inverter AC",
                            room = "Bedroom",
                            category = "AC",
                            isOn = false,
                            level = 60,
                            temperature = 22,
                            isLocked = false,
                            pendingSync = false
                        ),
                        SmartDeviceEntity(
                            remoteId = "dev_office_fan_${UUID.randomUUID().toString().take(6)}",
                            name = "Studio Smart Fan",
                            room = "Office",
                            category = "FAN",
                            isOn = true,
                            level = 65,
                            temperature = 24,
                            isLocked = false,
                            pendingSync = false
                        ),
                        SmartDeviceEntity(
                            remoteId = "dev_office_desk_${UUID.randomUUID().toString().take(6)}",
                            name = "Workstation Ambient Strip",
                            room = "Office",
                            category = "LIGHT",
                            isOn = true,
                            level = 90,
                            temperature = 24,
                            isLocked = false,
                            pendingSync = false
                        ),
                        SmartDeviceEntity(
                            remoteId = "dev_main_lock_${UUID.randomUUID().toString().take(6)}",
                            name = "Main Entrance Smart Lock",
                            room = "Security",
                            category = "LOCK",
                            isOn = true,
                            level = 100,
                            temperature = 24,
                            isLocked = true,
                            pendingSync = false
                        ),
                        SmartDeviceEntity(
                            remoteId = "dev_living_tv_${UUID.randomUUID().toString().take(6)}",
                            name = "Living Room OLED TV",
                            room = "Living Room",
                            category = "TV",
                            isOn = false,
                            level = 45,
                            temperature = 24,
                            isLocked = false,
                            pendingSync = false
                        )
                    )
                )
            }

            val ecoDao = db.ecosystemDeviceDao()
            if (ecoDao.getAllEcosystemDevices().isEmpty()) {
                ecoDao.insertAll(
                    listOf(
                        EcosystemDeviceEntity(
                            remoteId = "eco_phone_primary",
                            name = "Android Phone (Primary)",
                            formFactor = "PHONE",
                            status = "ONLINE",
                            batteryPercent = 88,
                            lastCommand = "Holographic Ring Core Active",
                            pendingSync = false
                        ),
                        EcosystemDeviceEntity(
                            remoteId = "eco_laptop_hub",
                            name = "Workstation Laptop",
                            formFactor = "LAPTOP",
                            status = "ONLINE",
                            batteryPercent = 94,
                            lastCommand = "Local LAN Discovery Ready",
                            pendingSync = false
                        ),
                        EcosystemDeviceEntity(
                            remoteId = "eco_wear_watch",
                            name = "TAGORE Smartwatch",
                            formFactor = "WATCH",
                            status = "ONLINE",
                            batteryPercent = 76,
                            lastCommand = "Mirroring Ring State",
                            pendingSync = false
                        ),
                        EcosystemDeviceEntity(
                            remoteId = "eco_studio_tablet",
                            name = "Studio Tablet",
                            formFactor = "TABLET",
                            status = "STANDBY",
                            batteryPercent = 64,
                            lastCommand = "Document & 360° Canvas Ready",
                            pendingSync = false
                        )
                    )
                )
            }
        }
    }
}
