package io.github.pranavm716.vgmixr.data.stream

import java.net.HttpURLConnection
import java.net.URL
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** NewPipe's HTTP client, on the JDK's `HttpURLConnection` so the app needs no OkHttp. */
class HttpUrlConnectionDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = URL(request.url()).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.httpMethod()
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                values.forEachIndexed { i, value ->
                    if (i == 0) {
                        connection.setRequestProperty(name, value)
                    } else {
                        connection.addRequestProperty(name, value)
                    }
                }
            }
            request.dataToSend()?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }

            val code = connection.responseCode
            if (code == TOO_MANY_REQUESTS) {
                throw ReCaptchaException("reCaptcha challenge requested", request.url())
            }
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes().decodeToString() }.orEmpty()
            val headers = connection.headerFields.filterKeys { it != null }
            return Response(
                code,
                connection.responseMessage,
                headers,
                body,
                connection.url.toString()
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val TOO_MANY_REQUESTS = 429

        // NewPipe's own user agent: YouTube serves the desktop pages the extractor parses.
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
