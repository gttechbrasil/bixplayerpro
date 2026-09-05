package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import pro.bixplayer.player.data.playlist.XtreamClient
import pro.bixplayer.player.data.playlist.XtreamCredentials
import pro.bixplayer.player.data.playlist.XtreamException

class XtreamCredentialsTest {

    @Test
    fun `extracts credentials and base url from a get_php link`() {
        val creds = XtreamCredentials.from(
            "http://servidor.tv:8080/get.php?username=u123&password=p456&type=m3u_plus&output=hls"
        )
        assertThat(creds).isNotNull()
        assertThat(creds!!.username).isEqualTo("u123")
        assertThat(creds.password).isEqualTo("p456")
        assertThat(creds.baseUrl.toString()).isEqualTo("http://servidor.tv:8080/")
    }

    @Test
    fun `returns null for a plain m3u link`() {
        assertThat(XtreamCredentials.from("http://servidor.tv/lista.m3u8")).isNull()
        assertThat(XtreamCredentials.from("http://servidor.tv/get.php?username=u")).isNull()
        assertThat(XtreamCredentials.from("nao-e-url")).isNull()
        assertThat(XtreamCredentials.from("http://servidor.tv/get.php?username=&password=")).isNull()
    }

    @Test
    fun `keeps https and the default port`() {
        val creds = XtreamCredentials.from("https://servidor.tv/get.php?username=u&password=p")
        assertThat(creds!!.baseUrl.toString()).isEqualTo("https://servidor.tv/")
    }
}

class XtreamClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: XtreamClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = XtreamClient(OkHttpClient(), Moshi.Builder().build())
    }

    @After fun tearDown() = server.shutdown()

    private fun credentials() = XtreamCredentials.from(
        server.url("/get.php").newBuilder()
            .addQueryParameter("username", "u")
            .addQueryParameter("password", "p")
            .build()
            .toString()
    )!!

    @Test
    fun `login sends the credentials and returns the account info`() {
        server.enqueue(
            MockResponse().setBody(
                """{"user_info":{"auth":1,"status":"Active","exp_date":"1790000000","max_connections":"2"}}"""
            )
        )

        val info = client.login(credentials())

        assertThat(info.auth).isEqualTo(1)
        assertThat(info.status).isEqualTo("Active")
        val request = server.takeRequest()
        assertThat(request.path).contains("player_api.php")
        assertThat(request.path).contains("username=u")
        assertThat(request.path).contains("password=p")
        assertThat(request.getHeader("User-Agent")).isEqualTo("smart-tv")
    }

    @Test
    fun `login rejects a disabled or unauthenticated account`() {
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":0,"status":"Active"}}"""))
        val denied = runCatching { client.login(credentials()) }.exceptionOrNull()
        assertThat(denied).isInstanceOf(XtreamException::class.java)
        assertThat(denied!!.message).contains("recusados")

        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"Disabled"}}"""))
        assertThat(runCatching { client.login(credentials()) }.exceptionOrNull())
            .isInstanceOf(XtreamException::class.java)
    }

    @Test
    fun `a server error becomes a translated exception`() {
        server.enqueue(MockResponse().setResponseCode(502))
        val error = runCatching { client.login(credentials()) }.exceptionOrNull()
        assertThat(error).isInstanceOf(XtreamException::class.java)
        assertThat(error!!.message).contains("502")
    }

    @Test
    fun `maps live categories and streams`() {
        server.enqueue(
            MockResponse().setBody(
                """[{"category_id":"1","category_name":"Abertos"},
                    {"category_id":"2","category_name":"Esportes"},
                    {"category_id":"","category_name":"Inválida"}]"""
            )
        )
        val categories = client.liveCategories(credentials())
        assertThat(categories.map { it.categoryName }).containsExactly("Abertos", "Esportes")

        server.enqueue(
            MockResponse().setBody(
                """[{"stream_id":11,"name":"Globo","stream_icon":"http://cdn/g.png",
                     "category_id":"1","epg_channel_id":"globo.br","num":1,"container_extension":"m3u8"},
                    {"stream_id":null,"name":"Quebrado"},
                    {"stream_id":12,"name":""}]"""
            )
        )
        val streams = client.liveStreams(credentials())
        assertThat(streams).hasSize(1)
        assertThat(streams.single().name).isEqualTo("Globo")
        assertThat(server.takeRequest().path).contains("get_live_categories")
        assertThat(server.takeRequest().path).contains("get_live_streams")
    }

    @Test
    fun `builds the playback url with the subscriber credentials`() {
        val creds = XtreamCredentials.from("http://serv.tv:8080/get.php?username=u1&password=p1")!!
        assertThat(client.liveStreamUrl(creds, 42, "m3u8"))
            .isEqualTo("http://serv.tv:8080/live/u1/p1/42.m3u8")
        // no extension from the provider: ts is the safe default
        assertThat(client.liveStreamUrl(creds, 42, "")).isEqualTo("http://serv.tv:8080/live/u1/p1/42.ts")
    }

    @Test
    fun `malformed json yields an empty list instead of crashing the sync`() {
        server.enqueue(MockResponse().setBody("isto nao e json"))
        assertThat(client.liveCategories(credentials())).isEmpty()
    }
}
