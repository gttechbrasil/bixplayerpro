package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import pro.bixplayer.player.data.api.DeviceApi
import pro.bixplayer.player.data.api.DeviceAuthInterceptor
import pro.bixplayer.player.data.api.DeviceRegistrar
import pro.bixplayer.player.data.api.dto.DeviceConfigDto
import pro.bixplayer.player.data.repository.ConfigRepository
import pro.bixplayer.player.data.repository.ErrorMessages
import pro.bixplayer.player.domain.model.ConfigState
import pro.bixplayer.player.domain.model.DeviceStatus
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

private const val CONFIG_JSON = """
{"registered": true, "mac_address": "02:50:50:AA:BB:CC", "status": "active",
 "playlists": [{"id": 5, "name": "Lista", "url": "http://a.tv/l.m3u", "type": "m3u"}],
 "banners": [], "theme": "default", "pin": "0000",
 "min_app_version": "1.0.0", "apk_url": "", "platform_name": "Bix"}
"""

class ConfigRepositoryTest {

    private lateinit var server: MockWebServer
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(DeviceConfigDto::class.java)
    private val messages = object : ErrorMessages {
        override fun forThrowable(error: Throwable?) = "erro"
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun buildApi(store: FakeDeviceStore): Pair<DeviceApi, DeviceRegistrar> {
        lateinit var api: DeviceApi
        val registrar = DeviceRegistrar(
            apiProvider = { api },
            prefs = store,
            deviceIdProvider = { "device-hash" },
            appType = "tv",
            appVersion = "1.0.0",
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(DeviceAuthInterceptor(store, registrar))
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DeviceApi::class.java)
        return api to registrar
    }

    private fun repository(store: FakeDeviceStore): ConfigRepository {
        val (api, registrar) = buildApi(store)
        return ConfigRepository(api, store, registrar, adapter, messages)
    }

    @Test
    fun `registers first when there is no token and then caches the config`() = runTest {
        val store = FakeDeviceStore()
        server.enqueue(
            MockResponse().setBody("""{"mac_address":"02:50:50:AA:BB:CC","token":"tok-1"}""")
        )
        server.enqueue(MockResponse().setBody(CONFIG_JSON))

        val state = repository(store).refresh()

        assertThat(state).isInstanceOf(ConfigState.Ready::class.java)
        val config = (state as ConfigState.Ready).config
        assertThat(config.status).isEqualTo(DeviceStatus.ACTIVE)
        assertThat(config.fromCache).isFalse()
        assertThat(store.currentToken()).isEqualTo("tok-1")
        assertThat(store.savedConfigJson).isNotNull()
        // the first playlist becomes the active one
        assertThat(store.currentActivePlaylistId()).isEqualTo(5L)

        assertThat(server.takeRequest().path).isEqualTo("/api/v1/device/register")
        val configRequest = server.takeRequest()
        assertThat(configRequest.path).isEqualTo("/api/v1/device/config")
        assertThat(configRequest.getHeader("Authorization")).isEqualTo("Bearer tok-1")
    }

    @Test
    fun `falls back to the cached config when the network fails`() = runTest {
        val store = FakeDeviceStore(initialToken = "tok-1", initialConfigJson = CONFIG_JSON)
        // no enqueued response and the server is stopped: the call fails at the socket
        server.shutdown()

        val state = repository(store).refresh()

        assertThat(state).isInstanceOf(ConfigState.Ready::class.java)
        val config = (state as ConfigState.Ready).config
        assertThat(config.fromCache).isTrue()
        assertThat(config.status).isEqualTo(DeviceStatus.ACTIVE)
        assertThat(config.playlists).hasSize(1)
    }

    @Test
    fun `reports a failure when the network fails and there is no cache`() = runTest {
        val store = FakeDeviceStore(initialToken = "tok-1")
        server.shutdown()

        val state = repository(store).refresh()

        assertThat(state).isInstanceOf(ConfigState.Failed::class.java)
        assertThat((state as ConfigState.Failed).message).isEqualTo("erro")
        assertThat(state.cause).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `a corrupt cache is discarded instead of crashing`() = runTest {
        val store = FakeDeviceStore(initialToken = "tok-1", initialConfigJson = "{not json")
        server.shutdown()

        val state = repository(store).refresh()

        assertThat(state).isInstanceOf(ConfigState.Failed::class.java)
    }

    @Test
    fun `server error also falls back to the cache`() = runTest {
        val store = FakeDeviceStore(initialToken = "tok-1", initialConfigJson = CONFIG_JSON)
        server.enqueue(MockResponse().setResponseCode(500))

        val state = repository(store).refresh()

        assertThat(state).isInstanceOf(ConfigState.Ready::class.java)
        assertThat((state as ConfigState.Ready).config.fromCache).isTrue()
    }
}
