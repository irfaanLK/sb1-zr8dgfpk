package com.pet.measure.vision

import java.util.concurrent.ForkJoinPool
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
    /** Optional diagnostics: receives one line per candidate considered. */
    var trace: ((String) -> Unit)? = null

    private var w = 0
    private var h = 0
    private lateinit var smooth: IntArray
    private lateinit var gx: IntArray
    private lateinit var gy: IntArray
    private lateinit var mag: FloatArray
    private lateinit var edge: BooleanArray
    private lateinit var accB: FloatArray
    private lateinit var tmpB: FloatArray
    private lateinit var accJ: FloatArray
    private lateinit var tmpJ: FloatArray
    private lateinit var bestB: FloatArray
    private lateinit var bestJ: FloatArray
    private lateinit var scaleB: ByteArray
    private lateinit var scaleJ: ByteArray
    private var edgeIdx = IntArray(0)
    private var edgeNx = FloatArray(0)
    private var edgeNy = FloatArray(0)
    private var edgeCount = 0

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
        accB = FloatArray(n); tmpB = FloatArray(n)
        accJ = FloatArray(n); tmpJ = FloatArray(n)
        bestB = FloatArray(n); bestJ = FloatArray(n)
        scaleB = ByteArray(n); scaleJ = ByteArray(n)
        gridW = w / GRID + 2; gridH = h / GRID + 2
        gridRB = FloatArray(gridW * gridH); gridRJ = FloatArray(gridW * gridH)
    }

    fun detect(input: FrameInput): List<Detection> {
        ensureBuffers(input.width, input.height)
        computeRadiusGrid(input)
        computeEdges(input.luma)
        collectEdges()
        // The two ball sizes are independent: vote for the jack on a second core.
        val jackVotes = ForkJoinPool.commonPool().submit { vote(gridRJ, bestJ, scaleJ, accJ, tmpJ) }
        vote(gridRB, bestB, scaleB, accB, tmpB)
        jackVotes.get()

        val boules = findBalls(input, BallKind.BOULE)
        val jacks = findBalls(input, BallKind.JACK).sortedByDescending { it.score }

        // Seen from above, the jack can never sit inside a boule's outline, so when a jack and a
        // boule overlap one of them is false: a highlight on the boule, or a shadow ring around
        // the jack. Keep whichever is the more convincing.
        val jack = jacks.firstOrNull { j ->
            boules.none { b -> overlaps(j, b) && b.score >= j.score }
        } ?: return boules
        return boules.filterNot { overlaps(jack, it) } + jack
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

    private fun collectEdges() {
        var n = 0
        for (i in edge.indices) if (edge[i]) n++
        if (edgeIdx.size < n) {
            edgeIdx = IntArray(n); edgeNx = FloatArray(n); edgeNy = FloatArray(n)
        }
        var k = 0
        for (y in 3 until h - 3) {
            val row = y * w
            for (x in 3 until w - 3) {
                val i = row + x
                if (!edge[i]) continue
                val inv = 1f / mag[i]
                edgeIdx[k] = i; edgeNx[k] = gx[i] * inv; edgeNy[k] = gy[i] * inv
                k++
            }
        }
        edgeCount = k
    }

    /**
     * Votes for circle centres at several radii around the expected one, because the predicted
     * size is never exact (ground height error, boule size, dark reflections on the rim).
     * Keeps, per pixel, the best normalised score over the radius scales and which scale won.
     */
    private fun vote(grid: FloatArray, best: FloatArray, bestScale: ByteArray, acc: FloatArray, tmp: FloatArray) {
        java.util.Arrays.fill(best, 0f)
        for ((si, scale) in SCALES.withIndex()) {
            java.util.Arrays.fill(acc, 0f)
            for (k in 0 until edgeCount) {
                val i = edgeIdx[k]
                val x = i % w; val y = i / w
                val r = radiusAt(grid, x, y) * scale
                if (r <= 0f) continue
                val nx = edgeNx[k]; val ny = edgeNy[k]
                var cx = (x + nx * r).roundToInt()
                var cy = (y + ny * r).roundToInt()
                if (cx in 0 until w && cy in 0 until h) acc[cy * w + cx] += 1f
                cx = (x - nx * r).roundToInt()
                cy = (y - ny * r).roundToInt()
                if (cx in 0 until w && cy in 0 until h) acc[cy * w + cx] += 1f
            }
            // 5x5 box sum (separable) tolerates small radius and edge-position errors.
            for (y in 0 until h) {
                val row = y * w
                for (x in 2 until w - 2) {
                    val i = row + x
                    tmp[i] = acc[i - 2] + acc[i - 1] + acc[i] + acc[i + 1] + acc[i + 2]
                }
            }
            for (y in 4 until h - 4) {
                val row = y * w
                for (x in 4 until w - 4) {
                    val i = row + x
                    val sum = tmp[i - 2 * w] + tmp[i - w] + tmp[i] + tmp[i + w] + tmp[i + 2 * w]
                    if (sum < 3f) continue
                    val r = radiusAt(grid, x, y) * scale
                    if (r <= 0f) continue
                    val score = sum / (CIRCLE_PIXELS_PER_RADIUS * r)
                    if (score > best[i]) {
                        best[i] = score
                        bestScale[i] = si.toByte()
                    }
                }
            }
        }
    }

    // ---- Peaks, refinement and classification --------------------------------------------

    private class Candidate(val x: Int, val y: Int, val score: Float, val radius: Float)

    private fun dist2(a: Detection, b: Detection) = (a.u - b.u) * (a.u - b.u) + (a.v - b.v) * (a.v - b.v)
    private fun overlaps(j: Detection, b: Detection) = dist2(j, b) < (b.radiusPx + j.radiusPx * 0.6f).let { it * it }

    private fun findBalls(input: FrameInput, kind: BallKind): List<Detection> {
        val grid = if (kind == BallKind.BOULE) gridRB else gridRJ
        val best = if (kind == BallKind.BOULE) bestB else bestJ
        val bestScale = if (kind == BallKind.BOULE) scaleB else scaleJ
        val candidates = ArrayList<Candidate>()
        val minScore = if (kind == BallKind.BOULE) PEAK_MIN_BOULE else PEAK_MIN_JACK
        for (y in 4 until h - 4) {
            val row = y * w
            for (x in 4 until w - 4) {
                val i = row + x
                val score = best[i]
                if (score < minScore) continue
                // Keep 3x3 local maxima only.
                if (score < best[i - 1] || score < best[i + 1] || score < best[i - w] || score < best[i + w] ||
                    score < best[i - w - 1] || score < best[i - w + 1] || score < best[i + w - 1] || score < best[i + w + 1]
                ) continue
                candidates.add(Candidate(x, y, score, radiusAt(grid, x, y) * SCALES[bestScale[i].toInt()]))
            }
        }
        candidates.sortByDescending { it.score }

        val out = ArrayList<Detection>()
        var attempts = 0
        for (c in candidates) {
            if (out.size >= MAX_PER_KIND || attempts >= MAX_ATTEMPTS) break
            val rNominal = radiusAt(grid, c.x, c.y)
            // Skip peaks that sit on a ball we already accepted.
            if (out.any { (it.u - c.x) * (it.u - c.x) + (it.v - c.y) * (it.v - c.y) < (SUPPRESS * it.radiusPx).let { d -> d * d } }) continue
            attempts++

            val fit = refine(c.x.toFloat(), c.y.toFloat(), c.radius)
            trace?.invoke("$kind cand ${c.x},${c.y} s=${"%.2f".format(c.score)} r=${"%.1f".format(c.radius)} nom=${"%.1f".format(rNominal)} fit=${fit?.let { "r=%.1f cov=%.2f".format(it.r, it.coverage) }}")
            if (fit == null) continue
            if (fit.r < rNominal * MIN_SIZE || fit.r > rNominal * MAX_SIZE) continue

            val look = appearanceFactor(input, fit.x, fit.y, fit.r)
            trace?.invoke("   look=${"%.2f".format(look)}")
            if (look < MIN_APPEARANCE) continue

            val shape = 0.5f * min(c.score, 1.2f) / 1.2f + 0.5f * fit.coverage
            val colour = colourFactor(input, kind, fit.x, fit.y, fit.r)
            // The jack is natural wood: a grey or black circle is a stone, not the jack.
            if (kind == BallKind.JACK && colour < MIN_JACK_COLOUR) continue
            val depth = if (kind == BallKind.BOULE) depthFactor(input, fit.x, fit.y) else 1f
            val score = shape * colour * depth * (0.6f + 0.4f * look)
            val threshold = if (kind == BallKind.BOULE) ACCEPT_BOULE else ACCEPT_JACK
            trace?.invoke("   shape=${"%.2f".format(shape)} colour=${"%.2f".format(colour)} depth=$depth score=${"%.2f".format(score)}")
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

    /**
     * How much the disk looks like an object rather than a patch of ground, from 0 to 1.
     * A ball differs in brightness or colour from the ground around it, or is smoother inside
     * than the ground is. A circle traced through gravel or asphalt texture is none of these.
     */
    private fun appearanceFactor(input: FrameInput, x: Float, y: Float, r: Float): Float {
        val luma = input.luma
        val cw = input.width / 2
        var sumIn = 0f; var nIn = 0; var edgeIn = 0
        var sumOut = 0f; var sumOut2 = 0f; var nOut = 0; var edgeOut = 0
        var uIn = 0f; var vIn = 0f; var uOut = 0f; var vOut = 0f
        val rIn = r * 0.75f; val rOut1 = r * 1.2f; val rOut2 = r * 1.6f
        val x1 = max(3, (x - rOut2).toInt()); val x2 = min(w - 4, (x + rOut2).toInt())
        val y1 = max(3, (y - rOut2).toInt()); val y2 = min(h - 4, (y + rOut2).toInt())
        for (py in y1..y2) for (px in x1..x2) {
            val dx = px - x; val dy = py - y
            val d = sqrt(dx * dx + dy * dy)
            val i = py * w + px
            val l = (luma[i].toInt() and 0xFF).toFloat()
            val ci = (py / 2) * cw + px / 2
            val u = (input.chromaU[ci].toInt() and 0xFF).toFloat()
            val v = (input.chromaV[ci].toInt() and 0xFF).toFloat()
            if (d <= rIn) {
                sumIn += l; nIn++; if (edge[i]) edgeIn++
                uIn += u; vIn += v
            } else if (d in rOut1..rOut2) {
                sumOut += l; sumOut2 += l * l; nOut++; if (edge[i]) edgeOut++
                uOut += u; vOut += v
            }
        }
        if (nIn == 0 || nOut == 0) return 0f
        val meanIn = sumIn / nIn
        val meanOut = sumOut / nOut
        val stdOut = sqrt(max(0f, sumOut2 / nOut - meanOut * meanOut))
        val contrast = abs(meanIn - meanOut) / (stdOut + 8f)
        val contrastW = ((contrast - 0.25f) / 0.75f).coerceIn(0f, 1f)

        val densityIn = edgeIn.toFloat() / nIn
        val densityOut = edgeOut.toFloat() / nOut
        val smoothW = if (densityOut < 0.03f) 0f else (1f - densityIn / densityOut).coerceIn(0f, 1f)
        val du = uIn / nIn - uOut / nOut
        val dv = vIn / nIn - vOut / nOut
        val colourW = ((sqrt(du * du + dv * dv) - 5f) / 12f).coerceIn(0f, 1f)
        return maxOf(contrastW, colourW, 0.5f * contrastW + 0.5f * smoothW)
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
            // A wood-coloured disk is the jack seen at the wrong size, never a boule.
            if (sumWood / n > 0.45f) return 0f
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
        private const val PEAK_MIN_BOULE = 0.26f
        private const val PEAK_MIN_JACK = 0.30f
        private const val ACCEPT_BOULE = 0.5f
        private const val ACCEPT_JACK = 0.40f
        private const val SUPPRESS = 1.4f
        /** Radius scales searched around the predicted size. */
        private val SCALES = floatArrayOf(0.76f, 0.86f, 0.95f, 1.05f, 1.14f, 1.24f)
        private const val MIN_SIZE = 0.68f
        private const val MAX_SIZE = 1.32f
        private const val MAX_ATTEMPTS = 150
        private const val MIN_JACK_COLOUR = 0.6f
        private const val MIN_APPEARANCE = 0.55f
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
