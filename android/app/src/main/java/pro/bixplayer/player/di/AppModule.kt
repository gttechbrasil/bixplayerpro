package pro.bixplayer.player.di

import android.content.Context
import androidx.room.Room
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import pro.bixplayer.player.BuildConfig
import pro.bixplayer.player.R
import pro.bixplayer.player.data.api.DeviceApi
import pro.bixplayer.player.data.api.DeviceAuthInterceptor
import pro.bixplayer.player.data.api.DeviceRegistrar
import pro.bixplayer.player.data.datastore.DevicePreferences
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.db.BixDatabase
import pro.bixplayer.player.data.db.CategoryDao
import pro.bixplayer.player.data.db.ChannelDao
import pro.bixplayer.player.data.db.FavoriteDao
import pro.bixplayer.player.data.db.PlaylistSyncDao
import pro.bixplayer.player.data.playlist.XtreamClient
import pro.bixplayer.player.data.api.dto.DeviceConfigDto
import pro.bixplayer.player.data.repository.DefaultErrorMessages
import pro.bixplayer.player.data.repository.ErrorMessages
import pro.bixplayer.player.player.PlayerMessages
import pro.bixplayer.player.util.AppLocale
import pro.bixplayer.player.util.DeviceIdentity
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Parsing a 5.000-channel playlist must never touch the main thread. */
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideConfigAdapter(moshi: Moshi): JsonAdapter<DeviceConfigDto> =
        moshi.adapter(DeviceConfigDto::class.java)

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DevicePreferences =
        DevicePreferences(context)

    @Provides
    @Singleton
    fun provideDeviceStore(prefs: DevicePreferences): DeviceStore = prefs

    @Provides
    @Singleton
    fun provideRegistrar(
        @ApplicationContext context: Context,
        prefs: DeviceStore,
        apiHolder: DeviceApiHolder,
    ): DeviceRegistrar = DeviceRegistrar(
        apiProvider = { apiHolder.api },
        prefs = prefs,
        deviceIdProvider = { DeviceIdentity.deviceId(context) },
        // The TV build reports "tv"; the phone activity overrides it at runtime in the M4.
        appType = if (context.resources.getBoolean(R.bool.is_tv_device)) "tv" else "mobile",
        appVersion = BuildConfig.VERSION_NAME,
    )

    @Provides
    @Singleton
    fun provideOkHttp(
        prefs: DeviceStore,
        registrar: DeviceRegistrar,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(DeviceAuthInterceptor(prefs, registrar))
        .apply {
            if (BuildConfig.NETWORK_LOGGING) {
                addInterceptor(
                    HttpLoggingInterceptor { message -> Timber.tag("http").d(message) }
                        .apply { level = HttpLoggingInterceptor.Level.BODY }
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideDeviceApi(retrofit: Retrofit, holder: DeviceApiHolder): DeviceApi =
        retrofit.create(DeviceApi::class.java).also { holder.api = it }

    @Provides
    @Singleton
    fun provideXtreamClient(client: OkHttpClient, moshi: Moshi): XtreamClient =
        XtreamClient(client, moshi)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BixDatabase =
        Room.databaseBuilder(context, BixDatabase::class.java, BixDatabase.NAME)
            // Content is a cache of the provider's lists; rebuilding it is cheaper than migrating.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideCategoryDao(db: BixDatabase): CategoryDao = db.categoryDao()

    @Provides fun provideChannelDao(db: BixDatabase): ChannelDao = db.channelDao()

    @Provides fun provideFavoriteDao(db: BixDatabase): FavoriteDao = db.favoriteDao()

    @Provides fun providePlaylistSyncDao(db: BixDatabase): PlaylistSyncDao = db.playlistSyncDao()

    @Provides
    @Singleton
    fun provideErrorMessages(locale: AppLocale): ErrorMessages = ResourceErrorMessages(locale)

    @Provides
    @Singleton
    fun providePlayerMessages(locale: AppLocale): PlayerMessages = PlayerMessages(locale)
}

/**
 * Breaks the cycle between OkHttp (which needs the registrar to heal a 401) and Retrofit
 * (which needs OkHttp to build the API). The holder is filled the moment the API is created.
 */
@Singleton
class DeviceApiHolder @Inject constructor() {
    lateinit var api: DeviceApi
}

/** [ErrorMessages] that re-reads the strings on every call, so a language change applies at once. */
class ResourceErrorMessages(private val locale: AppLocale) : ErrorMessages {
    override fun forThrowable(error: Throwable?): String = DefaultErrorMessages(
        network = locale.string(R.string.error_network),
        server = locale.string(R.string.error_server),
        unknown = locale.string(R.string.error_unknown),
    ).forThrowable(error)
}
