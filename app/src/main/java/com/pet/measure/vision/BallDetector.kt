package com.pet.measure.vision

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class BallKind { JACK, BOULE }

/** Physical sizes of the balls in metres. */
data class BallSpec(val jackRadius: Float = 0.015f, val bouleRadius: Float = 0.037f) {
    fun radius(kind: BallKind) = if (kind == BallKind.JACK) jackRadius else bouleRadius
}

/** One camera frame prepared for detection. Chroma planes are at half resolution. */
class FrameInput(
    val width: Int,
    val height: Int,
    val luma: ByteArray,
    val chromaU: ByteArray,
    val chromaV: ByteArray,
    val camera: CameraModel,
    /** World height of the ground plane. */
    val groundY: Float,
    /** Optional ARCore depth image in millimetres (same orientation as the camera image). */
    val depth: ShortArray? = null,
    val depthWidth: Int = 0,
    val depthHeight: Int = 0,
    val timestampMs: Long = 0L,
)

data class Detection(
    val kind: BallKind,
    val u: Float,
    val v: Float,
    val radiusPx: Float,
    val score: Float,
    /** World position of the sphere's centre. */
    val center: Vec3,
)

/**
 * Finds the jack and the boules in a camera image.
 *
 * Because ARCore tells us where the ground is, we know how big a ball of a given physical size
 * must look at every pixel. That lets us run a fixed-radius circular Hough transform for each
 * ball size on a thinned edge map, refine each peak with a least-squares circle fit, and then
 * use colour (wood vs metal) and depth (sticks out of the ground) to confirm.
 */
class BallDetector(var spec: BallSpec = BallSpec()) {

    private var w = 0
    private var h = 0
    private lateinit var smooth: IntArray
    private lateinit var gx: IntArray
    private lateinit var gy: IntArray
    private lateinit var mag: FloatArray
    private lateinit var edge: BooleanArray
    private lateinit var accB: FloatArray
    private lateinit var accJ: FloatArray

    private var gridW = 0
    private var gridH = 0
    private lateinit var gridRB: FloatArray
    private lateinit var gridRJ: FloatArray

    private fun ensureBuffers(width: Int, height: Int) {
        if (width == w && height == h) return
        w = width; h = height
        val n = w * h
        smooth = IntArray(n); gx = IntArray(n); gy = IntArray(n)
        mag = FloatArray(n); edge = BooleanArray(n)
        accB = FloatArray(n); accJ = FloatArray(n)
        gridW = w / GRID + 2; gridH = h / GRID + 2
        gridRB = FloatArray(gridW * gridH); gridRJ = FloatArray(gridW * gridH)
    }

    fun detect(input: FrameInput): List<Detection> {
        ensureBuffers(input.width, input.height)
        computeRadiusGrid(input)
        computeEdges(input.luma)
        vote()

        val boules = findBalls(input, BallKind.BOULE, accB, emptyList())
        val jackCandidates = findBalls(input, BallKind.JACK, accJ, boules)
        val jack = jackCandidates.maxByOrNull { it.score }
        return if (jack != null) boules + jack else boules
    }

    // ---- Expected radius per pixel -------------------------------------------------------

    private fun computeRadiusGrid(input: FrameInput) {
        val cam = input.camera
        for (gyi in 0 until gridH) for (gxi in 0 until gridW) {
            val u = (gxi * GRID).toFloat()
            val v = (gyi * GRID).toFloat()
            val i = gyi * gridW + gxi
            gridRB[i] = expectedRadius(cam, u, v, input.groundY, spec.bouleRadius)
            gridRJ[i] = expectedRadius(cam, u, v, input.groundY, spec.jackRadius)
        }
    }

    private fun expectedRadius(cam: CameraModel, u: Float, v: Float, groundY: Float, r: Float): Float {
        val t = cam.depthToPlane(u, v, groundY + r)
        if (t.isNaN() || t < 0.05f) return 0f
        val px = cam.fx * r / t
        return if (px < MIN_RADIUS_PX || px > MAX_RADIUS_PX) 0f else px
    }

    private fun radiusAt(grid: FloatArray, x: Int, y: Int): Float {
        val gxi = (x + GRID / 2) / GRID
        val gyi = (y + GRID / 2) / GRID
        return grid[min(gyi, gridH - 1) * gridW + min(gxi, gridW - 1)]
    }

    // ---- Edges ---------------------------------------------------------------------------

    private fun computeEdges(luma: ByteArray) {
        // 3x3 Gaussian blur.
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val s = (luma[i - w - 1].toInt() and 0xFF) + 2 * (luma[i - w].toInt() and 0xFF) + (luma[i - w + 1].toInt() and 0xFF) +
                    2 * (luma[i - 1].toInt() and 0xFF) + 4 * (luma[i].toInt() and 0xFF) + 2 * (luma[i + 1].toInt() and 0xFF) +
                    (luma[i + w - 1].toInt() and 0xFF) + 2 * (luma[i + w].toInt() and 0xFF) + (luma[i + w + 1].toInt() and 0xFF)
                smooth[i] = s shr 4
            }
        }
        // Sobel + magnitude histogram.
        val hist = IntArray(HIST_BINS)
        java.util.Arrays.fill(mag, 0f)
        for (y in 2 until h - 2) {
            val row = y * w
            for (x in 2 until w - 2) {
                val i = row + x
                val sx = (smooth[i - w + 1] + 2 * smooth[i + 1] + smooth[i + w + 1]) -
                    (smooth[i - w - 1] + 2 * smooth[i - 1] + smooth[i + w - 1])
                val sy = (smooth[i + w - 1] + 2 * smooth[i + w] + smooth[i + w + 1]) -
                    (smooth[i - w - 1] + 2 * smooth[i - w] + smooth[i - w + 1])
                gx[i] = sx; gy[i] = sy
                val m = sqrt((sx * sx + sy * sy).toFloat())
                mag[i] = m
                hist[min(m.toInt(), HIST_BINS - 1)]++
            }
        }
        // Adaptive threshold: keep roughly the strongest fifth of gradients.
        val total = (w - 4) * (h - 4)
        var cum = 0
        var pct = 0
        for (b in HIST_BINS - 1 downTo 0) {
            cum += hist[b]
            if (cum >= total * EDGE_FRACTION) { pct = b; break }
        }
        val thr = max(MIN_EDGE_MAG, pct.toFloat())

        // Non-maximum suppression -> thin edges.
        java.util.Arrays.fill(edge, false)
        for (y in 3 until h - 3) {
            val row = y * w
            for (x in 3 until w - 3) {
                val i = row + x
                val m = mag[i]
                if (m < thr) continue
                val ax = abs(gx[i]); val ay = abs(gy[i])
                val (a, b) = when {
                    ay * 1000 <= ax * 414 -> mag[i - 1] to mag[i + 1]
                    ax * 1000 <= ay * 414 -> mag[i - w] to mag[i + w]
                    (gx[i] > 0) == (gy[i] > 0) -> mag[i - w - 1] to mag[i + w + 1]
                    else -> mag[i - w + 1] to mag[i + w - 1]
                }
                if (m >= a && m >= b) edge[i] = true
            }
        }
    }

    // ---- Hough voting --------------------------------------------------------------------

    private fun vote() {
        java.util.Arrays.fill(accB, 0f)
        java.util.Arrays.fill(accJ, 0f)
        for (y in 3 until h - 3) {
            val row = y * w
            for (x in 3 until w - 3) {
                val i = row + x
                if (!edge[i]) continue
                val inv = 1f / mag[i]
                val nx = gx[i] * inv
                val ny = gy[i] * inv
                splat(accB, x, y, nx, ny, radiusAt(gridRB, x, y))
                splat(accJ, x, y, nx, ny, radiusAt(gridRJ, x, y))
            }
        }
    }

    private fun splat(acc: FloatArray, x: Int, y: Int, nx: Float, ny: Float, r: Float) {
        if (r <= 0f) return
        var cx = (x + nx * r).roundToInt()
        var cy = (y + ny * r).roundToInt()
        if (cx in 0 until w && cy in 0 until h) acc[cy * w + cx] += 1f
        cx = (x - nx * r).roundToInt()
        cy = (y - ny * r).roundToInt()
        if (cx in 0 until w && cy in 0 until h) acc[cy * w + cx] += 1f
    }

    // ---- Peaks, refinement and classification --------------------------------------------

    private class Candidate(val x: Int, val y: Int, val score: Float)

    private fun findBalls(
        input: FrameInput, kind: BallKind, acc: FloatArray, exclude: List<Detection>,
    ): List<Detection> {
        val grid = if (kind == BallKind.BOULE) gridRB else gridRJ
        val candidates = ArrayList<Candidate>()
        val minScore = if (kind == BallKind.BOULE) PEAK_MIN_BOULE else PEAK_MIN_JACK
        for (y in 4 until h - 4) {
            val row = y * w
            for (x in 4 until w - 4) {
                val i = row + x
                if (acc[i] < 2f) continue
                val r = radiusAt(grid, x, y)
                if (r <= 0f) continue
                // 5x5 neighbourhood sum tolerates small radius and edge-position errors.
                var s = 0f
                for (oy in -2..2) {
                    val o = i + oy * w
                    s += acc[o - 2] + acc[o - 1] + acc[o] + acc[o + 1] + acc[o + 2]
                }
                val score = s / (CIRCLE_PIXELS_PER_RADIUS * r)
                if (score < minScore) continue
                candidates.add(Candidate(x, y, score))
            }
        }
        candidates.sortByDescending { it.score }

        val out = ArrayList<Detection>()
        val taken = ArrayList<FloatArray>() // x, y, r of accepted / suppressed peaks
        for (c in candidates) {
            if (out.size >= MAX_PER_KIND) break
            val rExp = radiusAt(grid, c.x, c.y)
            if (taken.any { (it[0] - c.x) * (it[0] - c.x) + (it[1] - c.y) * (it[1] - c.y) < (SUPPRESS * max(it[2], rExp)).let { d -> d * d } }) continue
            taken.add(floatArrayOf(c.x.toFloat(), c.y.toFloat(), rExp))

            val fit = refine(c.x.toFloat(), c.y.toFloat(), rExp) ?: continue
            if (fit.r < rExp * 0.72f || fit.r > rExp * 1.32f) continue
            if (exclude.any { e ->
                    val dx = e.u - fit.x; val dy = e.v - fit.y
                    dx * dx + dy * dy < (e.radiusPx + fit.r * 0.6f).let { it * it }
                }) continue

            val shape = 0.5f * min(c.score, 1.2f) / 1.2f + 0.5f * fit.coverage
            val colour = colourFactor(input, kind, fit.x, fit.y, fit.r)
            val depth = if (kind == BallKind.BOULE) depthFactor(input, fit.x, fit.y) else 1f
            val score = shape * colour * depth
            val threshold = if (kind == BallKind.BOULE) ACCEPT_BOULE else ACCEPT_JACK
            if (score < threshold) continue

            val center = input.camera.intersectHorizontal(fit.x, fit.y, input.groundY + spec.radius(kind)) ?: continue
            out.add(Detection(kind, fit.x, fit.y, fit.r, score, center))
        }
        return out
    }

    private class Fit(val x: Float, val y: Float, val r: Float, val coverage: Float)

    /** Least-squares (Kasa) circle fit on radially-oriented edge pixels near the expected circle. */
    private fun refine(x0: Float, y0: Float, rExp: Float): Fit? {
        var cx = x0; var cy = y0; var r = rExp
        var coverage = 0f
        val bands = floatArrayOf(0.30f, 0.15f, 0.10f)
        for (band in bands) {
            val rMin = r * (1f - band); val rMax = r * (1f + band)
            val x1 = max(3, (cx - rMax - 1).toInt()); val x2 = min(w - 4, (cx + rMax + 1).toInt())
            val y1 = max(3, (cy - rMax - 1).toInt()); val y2 = min(h - 4, (cy + rMax + 1).toInt())
            var n = 0
            var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
            var sxz = 0.0; var syz = 0.0; var sz = 0.0
            val bins = IntArray(ANGLE_BINS)
            for (y in y1..y2) for (x in x1..x2) {
                val i = y * w + x
                if (!edge[i]) continue
                val dx = x - cx; val dy = y - cy
                val d = sqrt(dx * dx + dy * dy)
                if (d < rMin || d > rMax || d < 1e-3f) continue
                val cosA = (gx[i] * dx + gy[i] * dy) / (mag[i] * d)
                if (abs(cosA) < 0.82f) continue
                n++
                val px = dx.toDouble(); val py = dy.toDouble(); val pz = px * px + py * py
                sx += px; sy += py; sxx += px * px; syy += py * py; sxy += px * py
                sxz += px * pz; syz += py * pz; sz += pz
                val a = ((atan2(dy, dx) + PI) / (2 * PI) * ANGLE_BINS).toInt().coerceIn(0, ANGLE_BINS - 1)
                bins[a]++
            }
            if (n < max(10, (rExp * 1.5f).toInt())) return null
            val filled = bins.count { it >= max(1, (rExp * 0.12f).toInt()) }
            coverage = filled.toFloat() / ANGLE_BINS
            if (filled < MIN_FILLED_BINS) return null
            val sol = solve3(
                sxx, sxy, sx, -sxz,
                sxy, syy, sy, -syz,
                sx, sy, n.toDouble(), -sz,
            ) ?: return null
            val ncx = -sol[0] / 2; val ncy = -sol[1] / 2
            val rr = ncx * ncx + ncy * ncy - sol[2]
            if (rr <= 0) return null
            val nr = sqrt(rr).toFloat()
            val nx = cx + ncx.toFloat(); val ny = cy + ncy.toFloat()
            if ((nx - x0) * (nx - x0) + (ny - y0) * (ny - y0) > (0.45f * rExp) * (0.45f * rExp)) return null
            cx = nx; cy = ny; r = nr
        }
        return Fit(cx, cy, r, coverage)
    }

    /** Wood looks warm and saturated; metal looks grey. */
    private fun colourFactor(input: FrameInput, kind: BallKind, x: Float, y: Float, r: Float): Float {
        val cw = input.width / 2
        val ch = input.height / 2
        val rr = r * 0.6f
        var sumC = 0f; var sumWood = 0f; var n = 0
        val step = max(1, (rr / 6f).toInt())
        var yy = -rr
        while (yy <= rr) {
            var xx = -rr
            while (xx <= rr) {
                if (xx * xx + yy * yy <= rr * rr) {
                    val px = ((x + xx) / 2).toInt(); val py = ((y + yy) / 2).toInt()
                    if (px in 0 until cw && py in 0 until ch) {
                        val i = py * cw + px
                        val u = (input.chromaU[i].toInt() and 0xFF) - 128
                        val v = (input.chromaV[i].toInt() and 0xFF) - 128
                        val c = sqrt((u * u + v * v).toFloat())
                        sumC += c
                        sumWood += woodness(u, v, c)
                        n++
                    }
                }
                xx += step
            }
            yy += step
        }
        if (n == 0) return 1f
        return if (kind == BallKind.JACK) {
            0.5f + 0.5f * (sumWood / n)
        } else {
            val chroma = sumC / n
            val metal = (1f - (chroma - 12f) / 28f).coerceIn(0f, 1f)
            0.75f + 0.25f * metal
        }
    }

    private fun woodness(u: Int, v: Int, chroma: Float): Float {
        if (chroma < 8f) return 0f
        // Warm hues (orange, tan, brown) sit around 100°..170° in the (U, V) plane.
        val hue = Math.toDegrees(atan2(v.toDouble(), u.toDouble())).toFloat()
        val hueW = when {
            hue in 100f..170f -> 1f
            hue in 80f..100f -> (hue - 80f) / 20f
            hue in 170f..190f -> (190f - hue) / 20f
            else -> 0f
        }
        val satW = ((chroma - 8f) / 30f).coerceIn(0f, 1f)
        return hueW * satW
    }

    /** A boule rises ~7 cm above the ground; reward depth readings that agree, penalise flat ones. */
    private fun depthFactor(input: FrameInput, x: Float, y: Float): Float {
        val depth = input.depth ?: return 1f
        if (input.depthWidth == 0 || input.depthHeight == 0) return 1f
        val dx = (x * input.depthWidth / input.width).toInt()
        val dy = (y * input.depthHeight / input.height).toInt()
        val values = ArrayList<Int>(9)
        for (oy in -1..1) for (ox in -1..1) {
            val px = dx + ox; val py = dy + oy
            if (px in 0 until input.depthWidth && py in 0 until input.depthHeight) {
                val d = depth[py * input.depthWidth + px].toInt() and 0xFFFF
                if (d in 1..8000) values.add(d)
            }
        }
        if (values.size < 3) return 1f
        values.sort()
        val measured = values[values.size / 2] / 1000f
        val ground = input.camera.depthToPlane(x, y, input.groundY)
        if (ground.isNaN()) return 1f
        val rise = ground - measured
        return when {
            rise > spec.bouleRadius -> 1.1f
            rise > spec.bouleRadius * 0.3f -> 1f
            rise > -0.01f -> 0.8f
            else -> 0.6f
        }
    }

    companion object {
        private const val GRID = 16
        private const val HIST_BINS = 2048
        private const val EDGE_FRACTION = 0.35f
        private const val MIN_EDGE_MAG = 40f
        private const val MIN_RADIUS_PX = 4f
        private const val MAX_RADIUS_PX = 260f
        /** A thin 8-connected digital circle has about 4·√2·r pixels. */
        private const val CIRCLE_PIXELS_PER_RADIUS = 5.66f
        private const val PEAK_MIN_BOULE = 0.32f
        private const val PEAK_MIN_JACK = 0.34f
        private const val ACCEPT_BOULE = 0.42f
        private const val ACCEPT_JACK = 0.40f
        private const val SUPPRESS = 1.4f
        private const val MAX_PER_KIND = 16
        private const val ANGLE_BINS = 16
        private const val MIN_FILLED_BINS = 8

        /** Solves a 3x3 linear system given as rows [a b c | d]; returns null if singular. */
        internal fun solve3(
            a11: Double, a12: Double, a13: Double, b1: Double,
            a21: Double, a22: Double, a23: Double, b2: Double,
            a31: Double, a32: Double, a33: Double, b3: Double,
        ): DoubleArray? {
            val det = a11 * (a22 * a33 - a23 * a32) - a12 * (a21 * a33 - a23 * a31) + a13 * (a21 * a32 - a22 * a31)
            if (abs(det) < 1e-9) return null
            val d1 = b1 * (a22 * a33 - a23 * a32) - a12 * (b2 * a33 - a23 * b3) + a13 * (b2 * a32 - a22 * b3)
            val d2 = a11 * (b2 * a33 - a23 * b3) - b1 * (a21 * a33 - a23 * a31) + a13 * (a21 * b3 - b2 * a31)
            val d3 = a11 * (a22 * b3 - b2 * a32) - a12 * (a21 * b3 - b2 * a31) + b1 * (a21 * a32 - a22 * a31)
            return doubleArrayOf(d1 / det, d2 / det, d3 / det)
        }
    }
}
