package nl.family7.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class Family7RepositoryTest {

    @Test
    fun testBase36Unpacker() {
        val sample = "1b1b0d0a"
        val sb = StringBuilder()
        var s = 0
        while (s < sample.length) {
            val chunk = sample.substring(s, minOf(s + 2, sample.length))
            val code = chunk.toInt(36)
            sb.append(code.toChar())
            s += 2
        }
        assertEquals("//\r\n", sb.toString())
    }

    @Test
    fun testStreampartnerUnpack() {
        fun unpackStreampartner(w: String, i: String, s: String, e: String): String {
            var lIll = 0
            var ll1I = 0
            var Il1l = 0
            val ll1l = StringBuilder()
            val l1lI = StringBuilder()

            while (true) {
                if (lIll < 5) {
                    if (lIll < w.length) l1lI.append(w[lIll])
                } else if (lIll < w.length) {
                    ll1l.append(w[lIll])
                }
                lIll++

                if (ll1I < 5) {
                    if (ll1I < i.length) l1lI.append(i[ll1I])
                } else if (ll1I < i.length) {
                    ll1l.append(i[ll1I])
                }
                ll1I++

                if (Il1l < 5) {
                    if (Il1l < s.length) l1lI.append(s[Il1l])
                } else if (Il1l < s.length) {
                    ll1l.append(s[Il1l])
                }
                Il1l++

                if (w.length + i.length + s.length + e.length == ll1l.length + l1lI.length + e.length) break
            }

            val lI1l = ll1l.toString()
            val I1lI = l1lI.toString()
            if (I1lI.isEmpty()) return ""

            ll1I = 0
            val l1ll = StringBuilder()
            var pos = 0
            while (pos < lI1l.length) {
                var ll11 = -1
                if (I1lI[ll1I].code % 2 != 0) ll11 = 1
                val chunk = lI1l.substring(pos, minOf(pos + 2, lI1l.length))
                val charCode = chunk.toInt(36) - ll11
                l1ll.append(charCode.toChar())
                ll1I++
                if (ll1I >= I1lI.length) ll1I = 0
                pos += 2
            }
            return l1ll.toString()
        }

        // Test with empty check
        val result = unpackStreampartner("", "", "", "")
        assertEquals("", result)
    }

    @Test
    fun testSlugExtraction() {
        val url = "https://www.family7.nl/plus/programmas/het-spoor-van-de-koning?ref=home#section"
        val clean = url.substringBefore("?").substringBefore("#").trimEnd('/')
        val slug = clean.substringAfterLast("/")
        assertEquals("het-spoor-van-de-koning", slug)
    }
}
