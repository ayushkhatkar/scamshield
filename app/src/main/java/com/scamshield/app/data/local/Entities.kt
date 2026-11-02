package com.scamshield.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Every analysis the user ran (links, QR, messages, numbers). Powers history and the Safety Score. */
@Entity(tableName = "scans")
data class ScanRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val category: String,
    val subject: String,
    val score: Int,
    val level: String,
    val summary: String,
    val recommendation: String,
    val reasonsJson: String,
    val factsJson: String,
    val source: String
)

/**
 * Known scam / verified numbers. Sources:
 *  LOCAL    - reported by this user
 *  COMMUNITY- aggregated reports synced from Firestore / your backend
 *  REMOTE   - curated blocklist from your backend
 *  VERIFIED - paid "Verified Business" registry (shows a green badge instead of a warning)
 */
@Entity(tableName = "scam_numbers")
data class ScamNumberEntity(
    @PrimaryKey val number: String,
    val label: String,
    val reports: Int,
    val source: String,
    val businessName: String? = null,
    val updatedAt: Long
)

@Entity(tableName = "call_events")
data class CallEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val timestamp: Long,
    val score: Int,
    val level: String,
    /** BLOCKED, WARNED or ALLOWED */
    val action: String,
    val reason: String
)

@Entity(tableName = "app_risks")
data class AppRiskEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val score: Int,
    val level: String,
    val summary: String,
    val reasonsJson: String,
    val installer: String?,
    val scannedAt: Long,
    val ignored: Boolean = false
)
