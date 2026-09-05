package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import pro.bixplayer.player.data.repository.DefaultErrorMessages
import retrofit2.HttpException
import retrofit2.Response

/**
 * The API answers in Portuguese with `{"detail": {"message": ...}}`. Showing a generic
 * "server error" instead of that text sent a user chasing a bad URL when the real problem was
 * that the device was not registered — hence this test.
 */
class ErrorMessagesTest {

    private val messages = DefaultErrorMessages(
        network = "Sem conexão com a internet.",
        server = "O servidor não respondeu. Tente novamente.",
        unknown = "Ocorreu um erro inesperado.",
    )

    private fun httpError(code: Int, body: String): HttpException =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun `prefers the message the API sent`() {
        val error = httpError(
            403,
            """{"detail":{"message":"Dispositivo não cadastrado. Informe o MAC ao seu revendedor.","code":"device_not_registered"}}""",
        )
        assertThat(messages.forThrowable(error))
            .isEqualTo("Dispositivo não cadastrado. Informe o MAC ao seu revendedor.")
    }

    @Test
    fun `falls back to the generic server message when the body has none`() {
        assertThat(messages.forThrowable(httpError(500, "Internal Server Error")))
            .isEqualTo("O servidor não respondeu. Tente novamente.")
        assertThat(messages.forThrowable(httpError(502, """{"detail":{"code":"x"}}""")))
            .isEqualTo("O servidor não respondeu. Tente novamente.")
        assertThat(messages.forThrowable(httpError(400, """{"detail":{"message":"  "}}""")))
            .isEqualTo("O servidor não respondeu. Tente novamente.")
    }

    @Test
    fun `network and unknown failures keep their own text`() {
        assertThat(messages.forThrowable(IOException("offline")))
            .isEqualTo("Sem conexão com a internet.")
        assertThat(messages.forThrowable(IllegalStateException()))
            .isEqualTo("Ocorreu um erro inesperado.")
        assertThat(messages.forThrowable(null)).isEqualTo("Ocorreu um erro inesperado.")
    }
}
