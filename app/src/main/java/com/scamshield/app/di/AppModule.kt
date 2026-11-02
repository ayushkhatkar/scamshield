package com.scamshield.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.scamshield.app.BuildConfig
import com.scamshield.app.data.ScanMapper
import com.scamshield.app.data.local.AppDatabase
import com.scamshield.app.data.local.AppRiskDao
import com.scamshield.app.data.local.CallEventDao
import com.scamshield.app.data.local.ScamNumberDao
import com.scamshield.app.data.local.ScanDao
import com.scamshield.app.data.remote.BreachApi
import com.scamshield.app.data.remote.SafeBrowsingApi
import com.scamshield.app.data.remote.ScamShieldApi
import com.scamshield.engine.ScamShieldEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Application-wide coroutine scope for fire-and-forget work (notifications, family events). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "scamshield_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideScanMapper(json: Json): ScanMapper = ScanMapper(json)

    @Provides
    @Singleton
    fun provideEngine(): ScamShieldEngine = ScamShieldEngine()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideSafeBrowsingApi(client: OkHttpClient, json: Json): SafeBrowsingApi =
        retrofit(SafeBrowsingApi.BASE_URL, client, json).create(SafeBrowsingApi::class.java)

    @Provides
    @Singleton
    fun provideBreachApi(client: OkHttpClient, json: Json): BreachApi =
        retrofit(BreachApi.BASE_URL, client, json).create(BreachApi::class.java)

    @Provides
    @Singleton
    fun provideScamShieldApi(client: OkHttpClient, json: Json): ScamShieldApi =
        retrofit(ScamShieldApi.baseUrl.trimEnd('/') + "/", client, json).create(ScamShieldApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "scamshield.db")
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides fun provideScanDao(db: AppDatabase): ScanDao = db.scanDao()
    @Provides fun provideScamNumberDao(db: AppDatabase): ScamNumberDao = db.scamNumberDao()
    @Provides fun provideCallEventDao(db: AppDatabase): CallEventDao = db.callEventDao()
    @Provides fun provideAppRiskDao(db: AppDatabase): AppRiskDao = db.appRiskDao()
}
