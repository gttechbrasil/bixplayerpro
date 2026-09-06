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

/** VOD, series and EPG calls, including the loose number-or-string fields real panels send. */
class XtreamVodTest {

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
    fun `vod streams tolerate numeric and string rating, added and year`() {
        server.enqueue(
            MockResponse().setBody(
                """[{"stream_id":501,"name":"Rocky","stream_icon":"http://cdn/r.jpg","category_id":"9",
                     "added":"1700000000","rating":8.5,"year":1976,"container_extension":"mkv"},
                    {"stream_id":502,"name":"Alien","added":1700000100,"rating":"7","year":"1979"},
                    {"stream_id":null,"name":"Quebrado"}]"""
            )
        )
        val vod = client.vodStreams(credentials())
        assertThat(vod).hasSize(2)
        assertThat(XtreamClient.long(vod[0].added)).isEqualTo(1700000000L)
        assertThat(XtreamClient.text(vod[0].rating)).isEqualTo("8.5")
        assertThat(XtreamClient.text(vod[0].year)).isEqualTo("1976")
        assertThat(XtreamClient.text(vod[1].rating)).isEqualTo("7")
        assertThat(XtreamClient.text(vod[1].year)).isEqualTo("1979")
        assertThat(server.takeRequest().path).contains("get_vod_streams")
    }

    @Test
    fun `vod info maps plot, cast and duration in either shape`() {
        server.enqueue(
            MockResponse().setBody(
                """{"info":{"name":"Rocky","plot":"Boxeador.","cast":"Stallone","director":"Avildsen",
                     "genre":"Drama","releasedate":"1976-11-21","duration_secs":"7140","duration":"01:59:00",
                     "movie_image":"http://cdn/r.jpg","backdrop_path":["http://cdn/b.jpg"],"rating":"8.1"},
                    "movie_data":{"stream_id":501}}"""
            )
        )
        val info = client.vodInfo(credentials(), 501)!!
        assertThat(info.plot).isEqualTo("Boxeador.")
        assertThat(info.cast).isEqualTo("Stallone")
        assertThat(XtreamClient.int(info.durationSecs)).isEqualTo(7140)
        assertThat((info.backdropPath as List<*>).first()).isEqualTo("http://cdn/b.jpg")
        assertThat(server.takeRequest().path).contains("action=get_vod_info&vod_id=501")
    }

    @Test
    fun `series info groups episodes by season and builds their urls`() {
        server.enqueue(
            MockResponse().setBody(
                """{"info":{"name":"Dark","cover":"http://cdn/d.jpg","plot":"Winden.","genre":"Ficção"},
                    "episodes":{"1":[{"id":"9001","episode_num":1,"title":"Segredos","container_extension":"mp4",
                                       "season":1,"info":{"plot":"...","duration_secs":3120}},
                                      {"id":9002,"episode_num":"2","title":"Mentiras","container_extension":"mkv","season":1}],
                                 "2":[{"id":"9101","episode_num":1,"title":"Começos e fins","season":2}]}}"""
            )
        )
        val info = client.seriesInfo(credentials(), 77)!!
        assertThat(info.info?.name).isEqualTo("Dark")
        assertThat(info.episodes!!.keys).containsExactly("1", "2")
        val first = info.episodes!!.getValue("1")
        assertThat(XtreamClient.text(first[0].id)).isEqualTo("9001")
        assertThat(XtreamClient.text(first[1].id)).isEqualTo("9002")
        assertThat(XtreamClient.int(first[1].episodeNum)).isEqualTo(2)
        assertThat(XtreamClient.int(first[0].info?.durationSecs)).isEqualTo(3120)

        val creds = XtreamCredentials.from("http://serv.tv:8080/get.php?username=u1&password=p1")!!
        assertThat(client.episodeStreamUrl(creds, "9001", "mp4")).isEqualTo("http://serv.tv:8080/series/u1/p1/9001.mp4")
        assertThat(client.vodStreamUrl(creds, 501, null)).isEqualTo("http://serv.tv:8080/movie/u1/p1/501.mp4")
        assertThat(client.xmltvUrl(creds)).isEqualTo("http://serv.tv:8080/xmltv.php?username=u1&password=p1")
    }

    @Test
    fun `short epg listings are returned as sent`() {
        server.enqueue(
            MockResponse().setBody(
                """{"epg_listings":[{"title":"Sm9ybmFs","description":"Tm90w61jaWFz","start_timestamp":"1700000000","stop_timestamp":"1700001800"}]}"""
            )
        )
        val listings = client.shortEpg(credentials(), 11)
        assertThat(listings).hasSize(1)
        assertThat(XtreamClient.long(listings[0].startTimestamp)).isEqualTo(1700000000L)
        assertThat(server.takeRequest().path).contains("get_short_epg")
    }

    @Test
    fun `loose scalar helpers`() {
        assertThat(XtreamClient.text(0)).isNull()
        assertThat(XtreamClient.text("0")).isNull()
        assertThat(XtreamClient.text("null")).isNull()
        assertThat(XtreamClient.text(2019.0)).isEqualTo("2019")
        assertThat(XtreamClient.text(7.5)).isEqualTo("7.5")
        assertThat(XtreamClient.long("12.0")).isEqualTo(12L)
        assertThat(XtreamClient.int("abc")).isNull()
    }
}
