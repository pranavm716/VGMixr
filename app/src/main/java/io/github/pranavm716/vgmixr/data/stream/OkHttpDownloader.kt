package io.github.pranavm716.vgmixr.data.stream

import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** NewPipe's HTTP client. */
class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
            ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == TOO_MANY_REQUESTS) {
                throw ReCaptchaException("reCaptcha challenge requested", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString()
            )
        }
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429

        // NewPipe's own user agent: YouTube serves the desktop pages the extractor parses.
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}

/**
 * Connects over IPv4 when the host has an IPv4 address. YouTube judges anonymous requests by
 * address, and flags home IPv6 ranges (shared with the pipeline's yt-dlp runs) more readily than
 * the household's IPv4 address. An IPv6-only network (some mobile carriers) keeps IPv6.
 */
class Ipv4OnlyDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = delegate.lookup(hostname)
        return all.filterIsInstance<Inet4Address>().ifEmpty { all }
    }
}

/** The client for YouTube's API: IPv4 first, NewPipe's usual 30 s timeouts. */
fun youTubeClient(): OkHttpClient = OkHttpClient.Builder()
    .dns(Ipv4OnlyDns())
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
