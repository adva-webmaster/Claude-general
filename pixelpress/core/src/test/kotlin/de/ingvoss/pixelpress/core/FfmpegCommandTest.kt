package de.ingvoss.pixelpress.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegCommandTest {
    private fun info(video: String?, audio: String?) = StreamInfo(video, audio, 1000)

    @Test
    fun umpackenNurBeiPassendenCodecs() {
        assertTrue(FfmpegCommand.canCopy(info("h264", "aac")))
        assertTrue(FfmpegCommand.canCopy(info("hevc", "ac3")))
        assertTrue(FfmpegCommand.canCopy(info("h264", null)))
        assertFalse(FfmpegCommand.canCopy(info("mjpeg", "pcm_s16le")))
        assertFalse(FfmpegCommand.canCopy(info("mpeg4", "mp3")))
        assertFalse(FfmpegCommand.canCopy(info("h264", "pcm_s16le")))
        assertFalse(FfmpegCommand.canCopy(info(null, "aac")))
    }

    @Test
    fun neuberechnungNutztX264UndAac() {
        val args = FfmpegCommand.convertArgs("/a/in.avi", "/a/out.mp4.part", ConversionMode.TRANSCODE, Quality.BALANCED, info("mjpeg", "pcm_s16le"))
        assertEquals("/a/in.avi", args[args.indexOf("-i") + 1])
        assertEquals("libx264", args[args.indexOf("-c:v") + 1])
        assertEquals("veryfast", args[args.indexOf("-preset") + 1])
        assertEquals("23", args[args.indexOf("-crf") + 1])
        assertEquals("aac", args[args.indexOf("-c:a") + 1])
        assertEquals("yuv420p", args[args.indexOf("-pix_fmt") + 1])
        assertTrue(args.contains("-sn") && args.contains("-dn"))
        assertEquals("/a/out.mp4.part", args.last())
        assertEquals("mp4", args[args.indexOf("-f") + 1])
        assertEquals("pipe:1", args[args.indexOf("-progress") + 1])
    }

    @Test
    fun qualitaetsstufenAendernPresetUndCrf() {
        fun crf(q: Quality) = FfmpegCommand.convertArgs("i", "o", ConversionMode.TRANSCODE, q, info("mjpeg", null)).let { it[it.indexOf("-crf") + 1] }
        assertEquals("28", crf(Quality.FAST))
        assertEquals("23", crf(Quality.BALANCED))
        assertEquals("20", crf(Quality.HIGH))
    }

    @Test
    fun umpackenKopiertOhneNeuberechnung() {
        val args = FfmpegCommand.convertArgs("i", "o", ConversionMode.COPY, Quality.BALANCED, info("h264", "aac"))
        assertEquals("copy", args[args.indexOf("-c") + 1])
        assertFalse(args.contains("libx264"))
        assertFalse(args.contains("-tag:v"))
    }

    @Test
    fun hevcBekommtDenAppleKompatiblenTag() {
        val args = FfmpegCommand.convertArgs("i", "o", ConversionMode.COPY, Quality.BALANCED, info("hevc", "aac"))
        assertEquals("hvc1", args[args.indexOf("-tag:v") + 1])
    }

    @Test
    fun mp4WirdSchnellStartfaehig() {
        val args = FfmpegCommand.convertArgs("i", "o", ConversionMode.COPY, Quality.BALANCED, info("h264", "aac"))
        assertEquals("+faststart", args[args.indexOf("-movflags") + 1])
    }

    @Test
    fun probeAufrufFragtCodecsUndDauerImFlatFormat() {
        val args = FfmpegCommand.probeArgs("/a/x.avi")
        assertEquals("flat", args[args.indexOf("-of") + 1])
        assertEquals("/a/x.avi", args.last())
        assertTrue(args[args.indexOf("-show_entries") + 1].contains("format=duration"))
    }
}
