package com.scamshield.app.data

import com.scamshield.app.data.local.AppRiskEntity
import com.scamshield.app.data.local.ScanRecordEntity
import com.scamshield.engine.RiskLevel
import com.scamshield.engine.RiskReason
import com.scamshield.engine.RiskResult
import com.scamshield.engine.ScanCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ScanSource { MANUAL, SHARE, PROCESS_TEXT, OPEN_LINK, QR, CALL, FAMILY }

enum class Entitlement {
    FREE, PRO, FAMILY;

    val isPremium: Boolean get() = this != FREE
    val isFamily: Boolean get() = this == FAMILY
}

enum class FamilyRole { GUARDIAN, PROTECTED }

/** A persisted analysis. */
data class ScanRecord(
    val id: Long,
    val timestamp: Long,
    val result: RiskResult,
    val source: ScanSource
)

data class AppRisk(
    val packageName: String,
    val appName: String,
    val score: Int,
    val level: RiskLevel,
    val summary: String,
    val reasons: List<RiskReason>,
    val installer: String?,
    val scannedAt: Long,
    val ignored: Boolean
)

@Serializable
data class ReasonDto(val code: String, val title: String, val detail: String, val weight: Int)

/** Converts engine results to/from Room entities. Kept here so the engine stays dependency-free. */
class ScanMapper(private val json: Json) {

    fun reasonsToJson(reasons: List<RiskReason>): String =
        json.encodeToString(reasons.map { ReasonDto(it.code, it.title, it.detail, it.weight) })

    fun reasonsFromJson(text: String): List<RiskReason> = runCatching {
        json.decodeFromString<List<ReasonDto>>(text).map { RiskReason(it.code, it.title, it.detail, it.weight) }
    }.getOrDefault(emptyList())

    fun factsToJson(facts: Map<String, String>): String = json.encodeToString(facts)

    fun factsFromJson(text: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(text) }.getOrDefault(emptyMap())

    fun toEntity(result: RiskResult, source: ScanSource, timestamp: Long = System.currentTimeMillis()) = ScanRecordEntity(
        timestamp = timestamp,
        category = result.category.name,
        subject = result.subject,
        score = result.score,
        level = result.level.name,
        summary = result.summary,
        recommendation = result.recommendation,
        reasonsJson = reasonsToJson(result.reasons),
        factsJson = factsToJson(result.facts),
        source = source.name
    )

    fun toModel(e: ScanRecordEntity) = ScanRecord(
        id = e.id,
        timestamp = e.timestamp,
        source = runCatching { ScanSource.valueOf(e.source) }.getOrDefault(ScanSource.MANUAL),
        result = RiskResult(
            category = runCatching { ScanCategory.valueOf(e.category) }.getOrDefault(ScanCategory.MESSAGE),
            subject = e.subject,
            score = e.score,
            level = runCatching { RiskLevel.valueOf(e.level) }.getOrDefault(RiskLevel.CAUTION),
            summary = e.summary,
            recommendation = e.recommendation,
            reasons = reasonsFromJson(e.reasonsJson),
            facts = factsFromJson(e.factsJson)
        )
    )

    fun toAppRisk(e: AppRiskEntity) = AppRisk(
        packageName = e.packageName,
        appName = e.appName,
        score = e.score,
        level = runCatching { RiskLevel.valueOf(e.level) }.getOrDefault(RiskLevel.SAFE),
        summary = e.summary,
        reasons = reasonsFromJson(e.reasonsJson),
        installer = e.installer,
        scannedAt = e.scannedAt,
        ignored = e.ignored
    )
}
