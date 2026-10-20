package io.github.pranavm716.vgmixr.data.stream

import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Test

class Ipv4OnlyDnsTest {
    private val v4a = InetAddress.getByName("142.250.72.110")
    private val v4b = InetAddress.getByName("142.250.72.142")
    private val v6 = InetAddress.getByName("2607:f8b0:4005:80f::200e")

    private fun dns(vararg answer: InetAddress) = Ipv4OnlyDns(Dns { listOf(*answer) })

    @Test
    fun `only IPv4 addresses are kept when the host has any`() {
        assertEquals(listOf(v4a, v4b), dns(v6, v4a, v4b).lookup("www.youtube.com"))
    }

    @Test
    fun `an IPv6-only network keeps its IPv6 addresses rather than failing`() {
        assertEquals(listOf(v6), dns(v6).lookup("www.youtube.com"))
    }
}
