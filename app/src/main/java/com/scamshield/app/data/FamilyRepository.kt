package com.scamshield.app.data

import android.content.Context
import android.os.Build
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.messaging
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.engine.RiskLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

data class FamilyEvent(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val score: Int,
    val level: RiskLevel,
    val memberName: String,
    val createdAt: Long
)

data class FamilyMember(val uid: String, val name: String, val role: FamilyRole, val device: String, val joinedAt: Long)

/**
 * Family mode on Firebase (anonymous auth + Firestore + FCM).
 *
 * Data model:
 *   families/{code}                      { createdAt, createdBy }
 *   families/{code}/members/{uid}        { name, role, fcmToken, device, joinedAt }
 *   families/{code}/events/{autoId}      { type, title, detail, score, level, memberUid, memberName, createdAt }
 *   reports/{autoId}                     { number, label, uid, createdAt }   (community scam reports)
 *
 * The Cloud Function in /firebase/functions pushes every new event to the family's guardians.
 * Everything here is a no-op when google-services.json is absent, so the rest of the app still builds and runs.
 */
@Singleton
class FamilyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) {
    val isAvailable: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    private val db get() = Firebase.firestore
    private val auth get() = Firebase.auth

    private suspend fun ensureUid(): String {
        auth.currentUser?.uid?.let { return it }
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Anonymous sign-in failed")
    }

    private fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private fun generateCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I confusion
        val rnd = SecureRandom()
        return (1..6).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }

    /** Guardian creates a family and gets a 6-character code to share with parents. */
    suspend fun createFamily(guardianName: String): String {
        check(isAvailable) { "Firebase is not configured" }
        val uid = ensureUid()
        val code = generateCode()
        db.collection("families").document(code)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp(), "createdBy" to uid)).await()
        joinAsMember(code, uid, guardianName, FamilyRole.GUARDIAN)
        settings.setFamily(code, FamilyRole.GUARDIAN)
        settings.setDisplayName(guardianName)
        return code
    }

    /** Parent (protected member) or another guardian joins with the code. */
    suspend fun joinFamily(code: String, name: String, role: FamilyRole) {
        check(isAvailable) { "Firebase is not configured" }
        val normalized = code.trim().uppercase()
        val uid = ensureUid()
        val doc = db.collection("families").document(normalized).get().await()
        check(doc.exists()) { "No family found with code $normalized" }
        joinAsMember(normalized, uid, name, role)
        settings.setFamily(normalized, role)
        settings.setDisplayName(name)
    }

    private suspend fun joinAsMember(code: String, uid: String, name: String, role: FamilyRole) {
        val token = runCatching { Firebase.messaging.token.await() }.getOrNull()
        db.collection("families").document(code).collection("members").document(uid).set(
            mapOf(
                "name" to name,
                "role" to role.name,
                "fcmToken" to token,
                "device" to deviceLabel(),
                "joinedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        token?.let { settings.setFcmToken(it) }
    }

    suspend fun leaveFamily() {
        val s = settings.current()
        val code = s.familyCode
        if (isAvailable && code != null) {
            runCatching {
                val uid = ensureUid()
                db.collection("families").document(code).collection("members").document(uid).delete().await()
            }
        }
        settings.setFamily(null, null)
    }

    /** Called from FamilyMessagingService.onNewToken. */
    suspend fun updateFcmToken(token: String) {
        settings.setFcmToken(token)
        val s = settings.current()
        val code = s.familyCode ?: return
        if (!isAvailable) return
        runCatching {
            val uid = ensureUid()
            db.collection("families").document(code).collection("members").document(uid)
                .update("fcmToken", token).await()
        }
    }

    /**
     * Posts an event to the family feed if this device is a protected member (e.g. a parent's phone).
     * Guardians receive it as a push (via the Cloud Function) or via the polling worker.
     */
    suspend fun postEventIfProtected(type: String, title: String, detail: String, score: Int, level: RiskLevel) {
        val s = settings.current()
        val code = s.familyCode ?: return
        if (s.familyRole != FamilyRole.PROTECTED || !isAvailable) return
        runCatching {
            val uid = ensureUid()
            db.collection("families").document(code).collection("events").add(
                mapOf(
                    "type" to type,
                    "title" to title,
                    "detail" to detail.take(500),
                    "score" to score,
                    "level" to level.name,
                    "memberUid" to uid,
                    "memberName" to s.displayName.ifBlank { deviceLabel() },
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    /** Protected member can send a manual "I'm fine / test" event. */
    suspend fun postTestEvent() = postEventIfProtected(
        "TEST", "Test alert", "This is a test from the protected phone. Family alerts are working.", 0, RiskLevel.SAFE
    )

    fun observeEvents(code: String): Flow<List<FamilyEvent>> {
        if (!isAvailable) return flowOf(emptyList())
        return callbackFlow {
            val registration = db.collection("families").document(code).collection("events")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val events = snapshot.documents.map { d ->
                        FamilyEvent(
                            id = d.id,
                            type = d.getString("type") ?: "",
                            title = d.getString("title") ?: "",
                            detail = d.getString("detail") ?: "",
                            score = (d.getLong("score") ?: 0L).toInt(),
                            level = runCatching { RiskLevel.valueOf(d.getString("level") ?: "SAFE") }.getOrDefault(RiskLevel.SAFE),
                            memberName = d.getString("memberName") ?: "",
                            createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    }
                    trySend(events)
                }
            awaitClose { registration.remove() }
        }
    }

    fun observeMembers(code: String): Flow<List<FamilyMember>> {
        if (!isAvailable) return flowOf(emptyList())
        return callbackFlow {
            val registration = db.collection("families").document(code).collection("members")
                .addSnapshotListener { snapshot, _ ->
                    val members = snapshot?.documents?.map { d ->
                        FamilyMember(
                            uid = d.id,
                            name = d.getString("name") ?: "",
                            role = runCatching { FamilyRole.valueOf(d.getString("role") ?: "PROTECTED") }.getOrDefault(FamilyRole.PROTECTED),
                            device = d.getString("device") ?: "",
                            joinedAt = d.getTimestamp("joinedAt")?.toDate()?.time ?: 0L
                        )
                    }.orEmpty()
                    trySend(members)
                }
            awaitClose { registration.remove() }
        }
    }

    /** One-shot fetch used by the background polling worker (fallback when push is not deployed). */
    suspend fun fetchEventsSince(code: String, sinceMillis: Long): List<FamilyEvent> {
        if (!isAvailable) return emptyList()
        return runCatching {
            val snap = db.collection("families").document(code).collection("events")
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(20).get().await()
            snap.documents.mapNotNull { d ->
                val created = d.getTimestamp("createdAt")?.toDate()?.time ?: return@mapNotNull null
                if (created <= sinceMillis) return@mapNotNull null
                FamilyEvent(
                    id = d.id,
                    type = d.getString("type") ?: "",
                    title = d.getString("title") ?: "",
                    detail = d.getString("detail") ?: "",
                    score = (d.getLong("score") ?: 0L).toInt(),
                    level = runCatching { RiskLevel.valueOf(d.getString("level") ?: "SAFE") }.getOrDefault(RiskLevel.SAFE),
                    memberName = d.getString("memberName") ?: "",
                    createdAt = created
                )
            }
        }.getOrDefault(emptyList())
    }

    data class CommunityNumber(val number: String, val label: String, val reports: Int, val updatedAt: Long)

    /** Aggregated community blocklist (public read; written only by the Cloud Function). */
    suspend fun fetchCommunityBlocklist(sinceMillis: Long, limit: Long = 2000): List<CommunityNumber> {
        if (!isAvailable) return emptyList()
        return runCatching {
            val snap = db.collection("scam_numbers")
                .whereGreaterThan("updatedAt", Timestamp(Date(sinceMillis)))
                .limit(limit)
                .get().await()
            snap.documents.mapNotNull { d ->
                val number = d.getString("number") ?: d.id
                CommunityNumber(
                    number = number,
                    label = d.getString("label") ?: "Reported by community",
                    reports = (d.getLong("reports") ?: 1L).toInt(),
                    updatedAt = d.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Community report of a scam number (also sent to your backend by ScamNumberRepository). */
    suspend fun submitCommunityReport(number: String, label: String) {
        if (!isAvailable) return
        runCatching {
            val uid = ensureUid()
            db.collection("reports").add(
                mapOf("number" to number, "label" to label, "uid" to uid, "createdAt" to FieldValue.serverTimestamp())
            ).await()
        }
    }
}
