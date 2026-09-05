package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import pro.bixplayer.player.data.api.DeviceApi
import pro.bixplayer.player.data.api.DeviceAuthInterceptor
import pro.bixplayer.player.data.api.DeviceRegistrar
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * A rotated device token must heal itself: on 401 the app re-registers with the same
 * `device_id` and replays the original request once.
 */
class DeviceAuthInterceptorTest {

    private lateinit var server: MockWebServer
    private val moshi = Moshi.Builder().build()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun api(store: FakeDeviceStore): DeviceApi {
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
        return api
    }

    @Test
    fun `401 triggers a re-registration and replays the request`() = runTest {
        val store = FakeDeviceStore(initialToken = "old-token")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setBody("""{"mac_address":"02:50:50:AA:BB:CC","token":"new-token"}""")
        )
        server.enqueue(
            MockResponse().setBody(
                """{"registered":true,"mac_address":"02:50:50:AA:BB:CC","status":"active",
                    "playlists":[],"banners":[]}"""
            )
        )

        val config = api(store).config()

        assertThat(config.status).isEqualTo("active")
        assertThat(store.currentToken()).isEqualTo("new-token")
        assertThat(store.saveCredentialsCalls).isEqualTo(1)

        val first = server.takeRequest()
        assertThat(first.path).isEqualTo("/api/v1/device/config")
        assertThat(first.getHeader("Authorization")).isEqualTo("Bearer old-token")

        val register = server.takeRequest()
        assertThat(register.path).isEqualTo("/api/v1/device/register")
        // the register call itself must never carry the dead token
        assertThat(register.getHeader("Authorization")).isNull()
        assertThat(register.body.readUtf8()).contains("device-hash")

        val replay = server.takeRequest()
        assertThat(replay.path).isEqualTo("/api/v1/device/config")
        assertThat(replay.getHeader("Authorization")).isEqualTo("Bearer new-token")
    }

    @Test
    fun `gives up after one retry instead of looping`() = runTest {
        val store = FakeDeviceStore(initialToken = "old-token")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setBody("""{"mac_address":"02:50:50:AA:BB:CC","token":"new-token"}""")
        )
        // the replay is rejected too: the call must fail, not retry forever
        server.enqueue(MockResponse().setResponseCode(401))

        val error = runCatching { api(store).config() }.exceptionOrNull()

        assertThat(error).isInstanceOf(HttpException::class.java)
        assertThat((error as HttpException).code()).isEqualTo(401)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `when re-registration fails the original error surfaces`() = runTest {
        val store = FakeDeviceStore(initialToken = "old-token")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(500)) // register fails
        server.enqueue(MockResponse().setResponseCode(401)) // replay with the old token

        val error = runCatching { api(store).config() }.exceptionOrNull()

        assertThat(error).isInstanceOf(HttpException::class.java)
        assertThat(store.currentToken()).isEqualTo("old-token")
    }

    @Test
    fun `requests without a stored token are sent unauthenticated`() = runTest {
        val store = FakeDeviceStore(initialToken = null)
        server.enqueue(
            MockResponse().setBody(
                """{"registered":false,"mac_address":"02:50:50:00:00:01",
                    "status":"unregistered","playlists":[],"banners":[]}"""
            )
        )

        api(store).config()

        assertThat(server.takeRequest().getHeader("Authorization")).isNull()
    }
}
