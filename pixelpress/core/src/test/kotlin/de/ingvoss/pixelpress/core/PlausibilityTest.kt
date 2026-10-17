package de.ingvoss.pixelpress.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlausibilityTest {
    private val mb = 1024L * 1024

    private fun check(
        mode: ConversionMode,
        input: Long,
        output: Long,
        inMs: Long? = 60_000,
        outMs: Long? = 60_000
    ) = Plausibility.check(mode, input, output, inMs, outMs)

    @Test
    fun umpackenBleibtInDerNaehe() {
        assertTrue(check(ConversionMode.COPY, 100 * mb, 99 * mb).ok)
        assertTrue(check(ConversionMode.COPY, 100 * mb, 108 * mb).ok)
        assertEquals(Reason.OUTPUT_TOO_SMALL, check(ConversionMode.COPY, 100 * mb, 50 * mb).reason)
        assertEquals(Reason.OUTPUT_TOO_LARGE, check(ConversionMode.COPY, 100 * mb, 130 * mb).reason)
    }

    @Test
    fun neuberechnungDarfStarkSchrumpfen() {
        assertTrue(check(ConversionMode.TRANSCODE, 1000 * mb, 40 * mb).ok) // 4 %
        assertTrue(check(ConversionMode.TRANSCODE, 1000 * mb, 8 * mb).ok) // 0,8 %
        assertEquals(Reason.OUTPUT_TOO_SMALL, check(ConversionMode.TRANSCODE, 1000 * mb, 3 * mb).reason) // 0,3 %
        assertEquals(Reason.OUTPUT_TOO_LARGE, check(ConversionMode.TRANSCODE, 100 * mb, 400 * mb).reason)
    }

    @Test
    fun leereOderWinzigeAusgabeFaelltDurch() {
        assertEquals(Reason.OUTPUT_MISSING_OR_EMPTY, check(ConversionMode.TRANSCODE, 100 * mb, 0).reason)
        assertEquals(Reason.OUTPUT_TOO_SMALL, check(ConversionMode.TRANSCODE, 1 * mb, 10 * 1024).reason)
        assertEquals(Reason.INPUT_UNREADABLE, check(ConversionMode.TRANSCODE, 0, 100).reason)
    }

    @Test
    fun laengeMussPassen() {
        assertTrue(check(ConversionMode.TRANSCODE, 100 * mb, 30 * mb, 60_000, 59_000).ok)
        assertTrue(check(ConversionMode.TRANSCODE, 100 * mb, 30 * mb, 3_600_000, 3_550_000).ok) // 3 % von 1 h = 108 s
        val verdict = check(ConversionMode.TRANSCODE, 100 * mb, 30 * mb, 60_000, 20_000)
        assertFalse(verdict.ok)
        assertEquals(Reason.DURATION_MISMATCH, verdict.reason)
    }

    @Test
    fun unbekannteLaengeUeberspringtNurDieLaengenpruefung() {
        assertTrue(check(ConversionMode.TRANSCODE, 100 * mb, 30 * mb, null, 20_000).ok)
        assertTrue(check(ConversionMode.TRANSCODE, 100 * mb, 30 * mb, 60_000, null).ok)
    }

    @Test
    fun verhaeltnisWirdGemeldet() {
        assertEquals(0.3, check(ConversionMode.TRANSCODE, 100 * mb, 30 * mb).ratio!!, 1e-9)
    }
}
