package com.pet.measure.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Renders a synthetic top-down view of a petanque terrain (gravel, a wooden jack, metal boules)
 * and checks that the detector finds the balls and measures the right distances.
 */
class BallDetectorTest {

    private val width = 1280
    private val height = 720
    private val spec = BallSpec(jackRadius = 0.015f, bouleRadius = 0.037f)

    /** Camera 1.2 m above flat ground, tilted [tiltDeg] away from straight down. */
    private fun camera(heightM: Float = 1.2f, tiltDeg: Float = 0f): CameraModel {
        val t = Math.toRadians(tiltDeg.toDouble()).toFloat()
        // Columns: camera X, Y, Z axes expressed in world space.
        val xAxis = Vec3(1f, 0f, 0f)
        val yAxis = Vec3(0f, sin(t), -cos(t))
        val zAxis = Vec3(0f, cos(t), sin(t))
        val r = floatArrayOf(
            xAxis.x, yAxis.x, zAxis.x,
            xAxis.y, yAxis.y, zAxis.y,
            xAxis.z, yAxis.z, zAxis.z,
        )
        return CameraModel(width, height, 1000f, 1000f, width / 2f, height / 2f, r, Vec3(0f, heightM, 0f))
    }

    private class Ball(val kind: BallKind, val ground: Vec3)

    private data class Rgb(val r: Float, val g: Float, val b: Float)

    private fun render(cam: CameraModel, balls: List<Ball>, terrain: String, seed: Int): FrameInput {
        val rnd = Random(seed)
        val rgb = Array(height) { Array(width) { Rgb(0f, 0f, 0f) } }
        // Terrain texture.
        val base = when (terrain) {
            "sand" -> Rgb(190f, 172f, 130f)
            "grass" -> Rgb(80f, 120f, 60f)
            else -> Rgb(140f, 136f, 128f)
        }
        for (y in 0 until height) for (x in 0 until width) {
            val n = rnd.nextFloat() * 24f - 12f
            rgb[y][x] = Rgb(base.r + n, base.g + n, base.b + n)
        }
        if (terrain == "gravel") {
            repeat(9000) {
                val cx = rnd.nextInt(width); val cy = rnd.nextInt(height)
                val rad = 2 + rnd.nextInt(5)
                val shade = 90f + rnd.nextFloat() * 110f
                for (yy in -rad..rad) for (xx in -rad..rad) {
                    if (xx * xx + yy * yy > rad * rad) continue
                    val px = cx + xx; val py = cy + yy
                    if (px in 0 until width && py in 0 until height) rgb[py][px] = Rgb(shade, shade * 0.97f, shade * 0.92f)
                }
            }
        }
        // Balls, far ones first.
        for (b in balls.sortedByDescending { (it.ground - cam.position).length() }) {
            val radius = spec.radius(b.kind)
            val centre = Vec3(b.ground.x, b.ground.y + radius, b.ground.z)
            val uv = cam.project(centre)!!
            val depth = (centre - cam.position).length()
            val rpx = cam.fx * radius / depth
            val x1 = (uv[0] - rpx - 2).toInt(); val x2 = (uv[0] + rpx + 2).toInt()
            val y1 = (uv[1] - rpx - 2).toInt(); val y2 = (uv[1] + rpx + 2).toInt()
            for (y in y1..y2) for (x in x1..x2) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = (x - uv[0]) / rpx; val dy = (y - uv[1]) / rpx
                val d2 = dx * dx + dy * dy
                if (d2 > 1f) continue
                val nz = sqrt(1f - d2)
                val light = 0.35f + 0.65f * (0.6f * nz + 0.4f * (-dx * 0.5f - dy * 0.5f + nz * 0.7f)).coerceIn(0f, 1f)
                rgb[y][x] = if (b.kind == BallKind.JACK) {
                    Rgb(205f * light, 150f * light, 90f * light)
                } else {
                    val spec = if (dx < -0.2f && dx > -0.5f && dy < -0.2f && dy > -0.5f) 80f else 0f
                    val groove = if ((x + y) % 9 == 0) -25f else 0f
                    val g = 60f + 140f * light + spec + groove
                    Rgb(g, g, g * 1.02f)
                }
            }
        }
        // Convert to YUV 4:2:0.
        val luma = ByteArray(width * height)
        val cw = width / 2; val ch = height / 2
        val cu = ByteArray(cw * ch); val cv = ByteArray(cw * ch)
        for (y in 0 until height) for (x in 0 until width) {
            val p = rgb[y][x]
            val yy = 0.299f * p.r + 0.587f * p.g + 0.114f * p.b
            luma[y * width + x] = yy.toInt().coerceIn(0, 255).toByte()
            if (x % 2 == 0 && y % 2 == 0) {
                val u = 128 + 0.492f * (p.b - yy)
                val v = 128 + 0.877f * (p.r - yy)
                cu[(y / 2) * cw + x / 2] = u.toInt().coerceIn(0, 255).toByte()
                cv[(y / 2) * cw + x / 2] = v.toInt().coerceIn(0, 255).toByte()
            }
        }
        return FrameInput(width, height, luma, cu, cv, cam, groundY = 0f)
    }

    private fun groundPointAt(cam: CameraModel, u: Float, v: Float) = cam.intersectHorizontal(u, v, 0f)!!

    private fun scene(cam: CameraModel): List<Ball> {
        val jack = groundPointAt(cam, 640f, 360f)
        return listOf(
            Ball(BallKind.JACK, jack),
            Ball(BallKind.BOULE, jack + Vec3(0.12f, 0f, 0.03f)),
            Ball(BallKind.BOULE, jack + Vec3(-0.20f, 0f, -0.05f)),
            Ball(BallKind.BOULE, jack + Vec3(0.05f, 0f, -0.23f)),
            Ball(BallKind.BOULE, jack + Vec3(-0.08f, 0f, 0.15f)),
        )
    }

    private fun expectedDistance(jack: Vec3, boule: Vec3): Float {
        val a = Vec3(jack.x, spec.jackRadius, jack.z)
        val b = Vec3(boule.x, spec.bouleRadius, boule.z)
        return (b - a).length() - spec.jackRadius - spec.bouleRadius
    }

    private fun runScene(terrain: String, tilt: Float, seed: Int, tolerance: Float) {
        val cam = camera(tiltDeg = tilt)
        val balls = scene(cam)
        val frame = render(cam, balls, terrain, seed)
        val start = System.nanoTime()
        val dets = BallDetector(spec).detect(frame)
        val ms = (System.nanoTime() - start) / 1e6
        println("$terrain tilt=$tilt: ${dets.size} detections in ${"%.0f".format(ms)} ms")
        dets.forEach { println("  ${it.kind} u=${"%.1f".format(it.u)} v=${"%.1f".format(it.v)} r=${"%.1f".format(it.radiusPx)} score=${"%.2f".format(it.score)}") }

        val jack = dets.filter { it.kind == BallKind.JACK }
        assertEquals("exactly one jack", 1, jack.size)
        val boules = dets.filter { it.kind == BallKind.BOULE }
        assertEquals("all boules found, no false positives", 4, boules.size)

        val jackTruth = balls[0].ground
        for (truth in balls.drop(1)) {
            val det = boules.minByOrNull { (Vec3(it.center.x, 0f, it.center.z) - truth.ground).length() }
            assertNotNull(det)
            val measured = (det!!.center - jack[0].center).length() - spec.jackRadius - spec.bouleRadius
            val expected = expectedDistance(jackTruth, truth.ground)
            println("  expected ${"%.2f".format(expected * 100)} cm, measured ${"%.2f".format(measured * 100)} cm")
            assertTrue("distance error ${measured - expected}", kotlin.math.abs(measured - expected) < tolerance)
        }
    }

    @Test fun gravelTopDown() = runScene("gravel", 0f, 1, 0.006f)
    @Test fun gravelTilted() = runScene("gravel", 25f, 2, 0.008f)
    @Test fun sandTopDown() = runScene("sand", 0f, 3, 0.006f)
    @Test fun grassTilted() = runScene("grass", 15f, 4, 0.008f)

    @Test fun emptyTerrainHasNoBalls() {
        for ((i, terrain) in listOf("gravel", "sand", "grass").withIndex()) {
            val cam = camera(tiltDeg = 10f)
            val dets = BallDetector(spec).detect(render(cam, emptyList(), terrain, 10 + i))
            assertEquals("false positives on $terrain: $dets", 0, dets.size)
        }
    }

    @Test fun trackerConfirmsAfterSeveralFrames() {
        val cam = camera()
        val frame = render(cam, scene(cam), "gravel", 5)
        val detector = BallDetector(spec)
        val tracker = BallTracker()
        repeat(4) { tracker.update(detector.detect(frame), cam) }
        val snap = tracker.snapshot()
        val jack = Measurement.pickJack(snap)
        assertNotNull(jack)
        val dist = Measurement.distances(jack!!, snap, spec)
        assertEquals(4, dist.size)
        assertTrue(dist.zipWithNext().all { (a, b) -> a.distanceM <= b.distanceM })
    }

    @Test fun projectionRoundTrip() {
        val cam = camera(tiltDeg = 20f)
        val p = cam.intersectHorizontal(300f, 200f, 0.02f)!!
        val uv = cam.project(p)!!
        assertEquals(300f, uv[0], 0.01f)
        assertEquals(200f, uv[1], 0.01f)
    }
}
