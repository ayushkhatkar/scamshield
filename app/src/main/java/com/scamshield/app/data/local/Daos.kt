package com.scamshield.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Insert
    suspend fun insert(entity: ScanRecordEntity): Long

    @Query("SELECT * FROM scans ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ScanRecordEntity>>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun byId(id: Long): ScanRecordEntity?

    @Query("SELECT COUNT(*) FROM scans WHERE level IN ('SUSPICIOUS','DANGEROUS')")
    fun threatCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scans")
    fun totalCount(): Flow<Int>

    @Query("DELETE FROM scans")
    suspend fun clear()
}

@Dao
interface ScamNumberDao {
    @Query("SELECT * FROM scam_numbers WHERE number = :number")
    suspend fun get(number: String): ScamNumberEntity?

    @Upsert
    suspend fun upsert(entity: ScamNumberEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ScamNumberEntity>)

    @Query("SELECT * FROM scam_numbers WHERE source = 'LOCAL' ORDER BY updatedAt DESC")
    fun localReports(): Flow<List<ScamNumberEntity>>

    @Query("SELECT COUNT(*) FROM scam_numbers WHERE source != 'VERIFIED'")
    fun blocklistSize(): Flow<Int>

    @Query("DELETE FROM scam_numbers WHERE number = :number AND source = 'LOCAL'")
    suspend fun deleteLocal(number: String)
}

@Dao
interface CallEventDao {
    @Insert
    suspend fun insert(entity: CallEventEntity)

    @Query("SELECT * FROM call_events ORDER BY timestamp DESC LIMIT 100")
    fun recent(): Flow<List<CallEventEntity>>

    @Query("SELECT COUNT(*) FROM call_events WHERE number = :number AND timestamp > :since")
    suspend fun countFrom(number: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM call_events WHERE action = 'BLOCKED'")
    fun blockedCount(): Flow<Int>

    @Query("DELETE FROM call_events")
    suspend fun clear()
}

@Dao
interface AppRiskDao {
    @Upsert
    suspend fun upsertAll(entities: List<AppRiskEntity>)

    @Query("SELECT * FROM app_risks ORDER BY score DESC, appName ASC")
    fun all(): Flow<List<AppRiskEntity>>

    @Query("SELECT * FROM app_risks WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppRiskEntity?

    @Query("DELETE FROM app_risks WHERE packageName NOT IN (:keep)")
    suspend fun deleteNotIn(keep: List<String>)

    @Query("UPDATE app_risks SET ignored = :ignored WHERE packageName = :packageName")
    suspend fun setIgnored(packageName: String, ignored: Boolean)

    @Query("SELECT COUNT(*) FROM app_risks WHERE level IN ('SUSPICIOUS','DANGEROUS') AND ignored = 0")
    fun riskyCount(): Flow<Int>

    @Query("SELECT MAX(scannedAt) FROM app_risks")
    fun lastScanTime(): Flow<Long?>
}
