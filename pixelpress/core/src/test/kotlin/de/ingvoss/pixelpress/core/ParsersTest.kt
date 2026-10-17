package de.ingvoss.pixelpress.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    @Test
    fun probeLiestCodecsUndDauer() {
        val out = """
            streams.stream.0.codec_name="mjpeg"
            streams.stream.0.codec_type="video"
            streams.stream.1.codec_name="pcm_s16le"
            streams.stream.1.codec_type="audio"
            format.duration="125.480000"
        """.trimIndent()
        val info = ProbeParser.parse(out)
        assertEquals("mjpeg", info.videoCodec)
        assertEquals("pcm_s16le", info.audioCodec)
        assertEquals(125_480L, info.durationMs)
    }

    @Test
    fun probeNimmtJeErsteSpurUndKommtMitLuecken() {
        val out = """
            streams.stream.2.codec_name="aac"
            streams.stream.2.codec_type="audio"
            streams.stream.0.codec_name="h264"
            streams.stream.0.codec_type="video"
            streams.stream.1.codec_name="hevc"
            streams.stream.1.codec_type="video"
            format.duration="N/A"
        """.trimIndent()
        val info = ProbeParser.parse(out)
        assertEquals("h264", info.videoCodec)
        assertEquals("aac", info.audioCodec)
        assertNull(info.durationMs)
    }

    @Test
    fun probeOhneBildOderTonspur() {
        val info = ProbeParser.parse("streams.stream.0.codec_name=\"aac\"\nstreams.stream.0.codec_type=\"audio\"\n")
        assertNull(info.videoCodec)
        assertEquals("aac", info.audioCodec)
        assertEquals(StreamInfo(null, null, null), ProbeParser.parse(""))
    }

    @Test
    fun fortschrittLiestZeitGeschwindigkeitUndEnde() {
        val parser = ProgressParser()
        listOf("frame=100", "out_time_us=N/A", "out_time_us=30000000", "speed=2.5x", "progress=continue").forEach(parser::feed)
        assertEquals(30_000L, parser.outTimeMs)
        assertEquals(2.5, parser.speed!!, 1e-9)
        assertFalse(parser.ended)
        assertEquals(0.5f, parser.fraction(60_000)!!, 1e-6f)
        parser.feed("progress=end")
        assertTrue(parser.ended)
    }

    @Test
    fun fortschrittBegrenztUndOhneGesamtdauer() {
        val parser = ProgressParser()
        parser.feed("out_time_ms=90000000") // in Wahrheit Mikrosekunden
        assertEquals(1f, parser.fraction(60_000)!!, 1e-6f)
        assertNull(parser.fraction(null))
        assertNull(parser.fraction(0))
        parser.feed("quatsch")
        parser.feed("speed=N/A")
        assertNull(parser.speed)
    }

    @Test
    fun formateSindDeutsch() {
        assertEquals("1,5 MB", Formats.bytes(1_572_864))
        assertEquals("1:02:03", Formats.duration(3_723_000))
    }
}
