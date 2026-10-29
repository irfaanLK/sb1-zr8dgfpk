package com.pet.measure.vision

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

/**
 * Runs the detector on real court photos (jack and boules on asphalt), scaled to the sizes the
 * balls have in the ARCore camera image from roughly 0.7–1.4 m, and with the predicted ball size
 * off by up to ±20% to mimic ground-height and boule-size errors.
 *
 * The photos are stored as gzipped raw RGB (big-endian width and height, then pixels) because
 * Android unit tests cannot use java.awt to decode JPEGs.
 */
class RealPhotoTest {
    private val cameraHeight = 1.0f

    private class Rgb(val width: Int, val height: Int, val pixels: ByteArray)

    private fun load(name: String, scale: Double): Rgb {
        val src = DataInputStream(GZIPInputStream(javaClass.getResourceAsStream("/photos/$name.rgb.gz"))).use { input ->
            val w = input.readInt(); val h = input.readInt()
            Rgb(w, h, ByteArray(w * h * 3).also { input.readFully(it) })
        }
        // Bilinear resize.
        val w = (src.width * scale).toInt(); val h = (src.height * scale).toInt()
        val out = ByteArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = ((x + 0.5) / scale - 0.5).coerceIn(0.0, src.width - 1.001)
            val sy = ((y + 0.5) / scale - 0.5).coerceIn(0.0, src.height - 1.001)
            val x0 = sx.toInt(); val y0 = sy.toInt()
            val fx = sx - x0; val fy = sy - y0
            for (c in 0 until 3) {
                fun px(xx: Int, yy: Int) = (src.pixels[(yy * src.width + xx) * 3 + c].toInt() and 0xFF).toDouble()
                val v = px(x0, y0) * (1 - fx) * (1 - fy) + px(x0 + 1, y0) * fx * (1 - fy) +
                    px(x0, y0 + 1) * (1 - fx) * fy + px(x0 + 1, y0 + 1) * fx * fy
                out[(y * w + x) * 3 + c] = v.toInt().coerceIn(0, 255).toByte()
            }
        }
        return Rgb(w, h, out)
    }

    /** Converts to YUV 4:2:0 and pairs it with a camera looking straight down. */
    private fun frame(img: Rgb, fx: Float): FrameInput {
        val w = img.width and 1.inv(); val h = img.height and 1.inv()
        val luma = ByteArray(w * h); val cw = w / 2; val ch = h / 2
        val cu = ByteArray(cw * ch); val cv = ByteArray(cw * ch)
        for (y in 0 until h) for (x in 0 until w) {
            val o = (y * img.width + x) * 3
            val r = img.pixels[o].toInt() and 255
            val g = img.pixels[o + 1].toInt() and 255
            val b = img.pixels[o + 2].toInt() and 255
            val yy = 0.299f * r + 0.587f * g + 0.114f * b
            luma[y * w + x] = yy.toInt().coerceIn(0, 255).toByte()
            if (x % 2 == 0 && y % 2 == 0) {
                cu[(y / 2) * cw + x / 2] = (128 + 0.492f * (b - yy)).toInt().coerceIn(0, 255).toByte()
                cv[(y / 2) * cw + x / 2] = (128 + 0.877f * (r - yy)).toInt().coerceIn(0, 255).toByte()
            }
        }
        val down = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 0f)
        val cam = CameraModel(w, h, fx, fx, w / 2f, h / 2f, down, Vec3(0f, cameraHeight, 0f))
        return FrameInput(w, h, luma, cu, cv, cam, groundY = 0f)
    }

    /**
     * @param radiusInPhoto the ball's radius in pixels in the full-size photo.
     * @param physicalRadius the ball's real radius in metres.
     */
    private fun sweep(name: String, radiusInPhoto: Double, physicalRadius: Float, kind: BallKind, sizes: List<Double>) {
        val failures = ArrayList<String>()
        for (target in sizes) {
            val img = load(name, target / radiusInPhoto)
            val fxTrue = (target * (cameraHeight - physicalRadius) / physicalRadius).toFloat()
            for (err in listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f)) {
                val dets = BallDetector(BallSpec()).detect(frame(img, fxTrue * err))
                val found = dets.count { it.kind == kind }
                if (found != 1) failures.add("radius ${target}px, size error x$err: found $found")
            }
        }
        assertEquals("$name: $failures", emptyList<String>(), failures)
    }

    @Test fun woodenJackOnAsphalt() =
        sweep("jack_asphalt", 22.75, 0.015f, BallKind.JACK, listOf(10.0, 14.0, 20.0))

    @Test fun shinyBouleOnAsphalt() =
        sweep("boule_shiny_asphalt", 59.5, 0.037f, BallKind.BOULE, listOf(20.0, 30.0, 45.0))

    @Test fun matteBouleOnAsphalt() =
        sweep("boule_matte_asphalt", 78.5, 0.037f, BallKind.BOULE, listOf(20.0, 30.0, 45.0))

    @Test fun noFalseJackOnBoulePhotos() {
        for ((name, radius) in listOf("boule_shiny_asphalt" to 59.5, "boule_matte_asphalt" to 78.5)) {
            val img = load(name, 30.0 / radius)
            val fx = (30.0 * (cameraHeight - 0.037f) / 0.037f).toFloat()
            val dets = BallDetector(BallSpec()).detect(frame(img, fx))
            assertEquals("$name: $dets", 0, dets.count { it.kind == BallKind.JACK })
        }
    }
}
