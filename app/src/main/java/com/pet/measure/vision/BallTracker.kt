package com.pet.measure.vision

/** A ball followed over time in world space. */
class Track(
    val id: Int,
    val kind: BallKind,
    var position: Vec3,
    val manual: Boolean,
) {
    var hits = 1
    var misses = 0
    var confidence = 0f
    /** The user removed this ball: keep it so it is not detected again, but never show it. */
    var hidden = false

    val confirmed get() = !hidden && (manual || hits >= CONFIRM_HITS)

    companion object {
        const val CONFIRM_HITS = 3
    }
}

/**
 * Turns noisy per-frame detections into stable balls: matches detections to existing tracks,
 * smooths positions, and drops tracks that stop being seen while in view.
 */
class BallTracker {
    private var nextId = 1
    private val tracks = ArrayList<Track>()

    @Synchronized
    fun snapshot(): List<TrackSnapshot> = tracks.map { TrackSnapshot(it.id, it.kind, it.position, it.confirmed, it.confidence, it.manual) }

    @Synchronized
    fun reset() {
        tracks.clear()
    }

    @Synchronized
    fun update(detections: List<Detection>, camera: CameraModel) {
        val matched = HashSet<Int>()
        val usedDetections = BooleanArray(detections.size)

        // Greedy nearest-neighbour association, closest pairs first.
        val pairs = ArrayList<Triple<Int, Int, Float>>()
        for ((di, d) in detections.withIndex()) {
            for ((ti, t) in tracks.withIndex()) {
                if (t.kind != d.kind) continue
                val dist = (t.position - d.center).length()
                if (dist < gate(d.kind)) pairs.add(Triple(di, ti, dist))
            }
        }
        pairs.sortBy { it.third }
        for ((di, ti, _) in pairs) {
            if (usedDetections[di] || ti in matched) continue
            usedDetections[di] = true
            matched.add(ti)
            val t = tracks[ti]
            val d = detections[di]
            val alpha = if (t.hits < 4) 0.5f else SMOOTHING
            t.position = lerp(t.position, d.center, alpha)
            t.hits++
            t.misses = 0
            t.confidence = if (t.confidence == 0f) d.score else t.confidence * 0.7f + d.score * 0.3f
        }

        // Age the tracks that existed before this frame and were not seen.
        val existing = tracks.size
        val stale = ArrayList<Track>()
        for (ti in 0 until existing) {
            if (ti in matched) continue
            val t = tracks[ti]
            if (!camera.isInImage(t.position, 8f)) continue // out of view: keep it as it was
            t.misses++
            t.confidence *= 0.9f
            val limit = if (t.manual || t.hidden) Int.MAX_VALUE else if (t.hits >= Track.CONFIRM_HITS) MAX_MISSES else 2
            if (t.misses > limit) stale.add(t)
        }
        tracks.removeAll(stale.toSet())

        for ((di, d) in detections.withIndex()) {
            if (usedDetections[di]) continue
            tracks.add(Track(nextId++, d.kind, d.center, manual = false).also { it.confidence = d.score })
        }

        mergeDuplicates()
    }

    private fun mergeDuplicates() {
        var i = 0
        while (i < tracks.size) {
            var j = i + 1
            while (j < tracks.size) {
                val a = tracks[i]; val b = tracks[j]
                if (a.kind == b.kind && (a.position - b.position).length() < gate(a.kind) * 0.5f) {
                    val keepA = a.manual || a.hidden || (!b.manual && !b.hidden && a.hits >= b.hits)
                    if (keepA) tracks.removeAt(j) else { tracks.removeAt(i); i--; break }
                } else j++
            }
            i++
        }
    }

    @Synchronized
    fun addManual(kind: BallKind, center: Vec3): Int {
        if (kind == BallKind.JACK) tracks.removeAll { it.kind == BallKind.JACK }
        val t = Track(nextId++, kind, center, manual = true).also { it.confidence = 1f; it.hits = Track.CONFIRM_HITS }
        tracks.add(t)
        return t.id
    }

    @Synchronized
    fun hide(id: Int) {
        tracks.firstOrNull { it.id == id }?.let { if (it.manual) tracks.remove(it) else it.hidden = true }
    }

    private fun gate(kind: BallKind) = if (kind == BallKind.JACK) 0.04f else 0.05f

    companion object {
        private const val SMOOTHING = 0.3f
        private const val MAX_MISSES = 6
    }
}

data class TrackSnapshot(
    val id: Int,
    val kind: BallKind,
    val position: Vec3,
    val confirmed: Boolean,
    val confidence: Float,
    val manual: Boolean,
)

/** Distance from the jack to one boule, measured between the closest surfaces. */
data class BouleDistance(
    val bouleId: Int,
    val distanceM: Float,
    /** Point on the jack's surface facing the boule. */
    val fromPoint: Vec3,
    /** Point on the boule's surface facing the jack. */
    val toPoint: Vec3,
    val bouleCenter: Vec3,
)

object Measurement {
    /** Picks the jack: a manual one wins, then the most confident confirmed one. */
    fun pickJack(tracks: List<TrackSnapshot>): TrackSnapshot? =
        tracks.filter { it.kind == BallKind.JACK && it.confirmed }
            .maxWithOrNull(compareBy<TrackSnapshot> { it.manual }.thenBy { it.confidence })

    /** Edge-to-edge distances from the jack to each confirmed boule, closest first. */
    fun distances(jack: TrackSnapshot, tracks: List<TrackSnapshot>, spec: BallSpec): List<BouleDistance> =
        tracks.filter { it.kind == BallKind.BOULE && it.confirmed }
            .map { b ->
                val dir = (b.position - jack.position)
                val centres = dir.length()
                val unit = dir.normalized()
                BouleDistance(
                    bouleId = b.id,
                    distanceM = (centres - spec.jackRadius - spec.bouleRadius).coerceAtLeast(0f),
                    fromPoint = jack.position + unit * spec.jackRadius,
                    toPoint = b.position - unit * spec.bouleRadius,
                    bouleCenter = b.position,
                )
            }
            .sortedBy { it.distanceM }
}
