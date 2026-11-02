package com.scamshield.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ScanRecordEntity::class, ScamNumberEntity::class, CallEventEntity::class, AppRiskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun scamNumberDao(): ScamNumberDao
    abstract fun callEventDao(): CallEventDao
    abstract fun appRiskDao(): AppRiskDao
}
