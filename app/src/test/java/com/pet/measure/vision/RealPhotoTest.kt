package com.pet.measure.vision

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Runs the detector on real court photos (jack and boules on asphalt), scaled to the sizes the
 * balls have in the ARCore camera image from roughly 0.7–1.4 m, and with the predicted ball size
 * off by up to ±20% to mimic ground-height and boule-size errors.
 */
class RealPhotoTest {
    private val cameraHeight = 1.0f

    private fun load(name: String, scale: Double): BufferedImage {
        val src = javaClass.getResourceAsStream("/photos/$name").use { ImageIO.read(it) }
        val w = (src.width * scale).toInt(); val h = (src.height * scale).toInt()
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    /** Converts to YUV 4:2:0 and pairs it with a camera looking straight down. */
    private fun frame(img: BufferedImage, fx: Float): FrameInput {
        val w = img.width and 1.inv(); val h = img.height and 1.inv()
        val luma = ByteArray(w * h); val cw = w / 2; val ch = h / 2
        val cu = ByteArray(cw * ch); val cv = ByteArray(cw * ch)
        for (y in 0 until h) for (x in 0 until w) {
            val p = img.getRGB(x, y)
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
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
        sweep("jack_asphalt.jpg", 45.5, 0.015f, BallKind.JACK, listOf(10.0, 14.0, 20.0, 30.0))

    @Test fun shinyBouleOnAsphalt() =
        sweep("boule_shiny_asphalt.jpg", 119.0, 0.037f, BallKind.BOULE, listOf(20.0, 30.0, 45.0))

    @Test fun matteBouleOnAsphalt() =
        sweep("boule_matte_asphalt.jpg", 157.0, 0.037f, BallKind.BOULE, listOf(20.0, 30.0, 45.0))

    @Test fun noFalseJackOnBoulePhotos() {
        for (name in listOf("boule_shiny_asphalt.jpg", "boule_matte_asphalt.jpg")) {
            val img = load(name, 30.0 / 140)
            val fx = (30.0 * (cameraHeight - 0.037f) / 0.037f).toFloat()
            val dets = BallDetector(BallSpec()).detect(frame(img, fx))
            assertEquals("$name: $dets", 0, dets.count { it.kind == BallKind.JACK })
        }
    }
}
