package com.scamshield.app.data

import com.scamshield.app.BuildConfig
import com.scamshield.app.data.prefs.SettingsRepository
import com.scamshield.app.data.remote.BreachApi
import com.scamshield.app.data.remote.BreachDto
import javax.inject.Inject
import javax.inject.Singleton

sealed class BreachLookup {
    data object NotConfigured : BreachLookup()
    data object Clean : BreachLookup()
    data class Found(val breaches: List<BreachDto>) : BreachLookup()
    data class Error(val message: String) : BreachLookup()
}

/** Pro feature: "Has my e-mail leaked?" via Have I Been Pwned. */
@Singleton
class BreachRepository @Inject constructor(
    private val api: BreachApi,
    private val settings: SettingsRepository
) {
    val isConfigured: Boolean get() = BreachApi.isConfigured

    suspend fun check(email: String): BreachLookup {
        if (!isConfigured) return BreachLookup.NotConfigured
        val account = email.trim().lowercase()
        if (!account.contains("@")) return BreachLookup.Error("Enter a valid e-mail address")
        settings.setBreachEmail(account)
        return runCatching {
            val response = api.breaches(account, BuildConfig.HIBP_API_KEY)
            when (response.code()) {
                200 -> response.body()?.takeIf { it.isNotEmpty() }?.let { BreachLookup.Found(it.sortedByDescending { b -> b.breachDate }) } ?: BreachLookup.Clean
                404 -> BreachLookup.Clean
                401 -> BreachLookup.Error("Invalid HIBP API key")
                429 -> BreachLookup.Error("Too many requests, try again in a minute")
                else -> BreachLookup.Error("Lookup failed (${response.code()})")
            }
        }.getOrElse { BreachLookup.Error(it.message ?: "Network error") }
    }
}
