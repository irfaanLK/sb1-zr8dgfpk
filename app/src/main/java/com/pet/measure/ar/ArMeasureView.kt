package com.pet.measure.ar

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import com.google.ar.core.Camera
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.pet.measure.data.Team
import com.pet.measure.overlay.MeasureOverlayView
import com.pet.measure.overlay.OverlayBall
import com.pet.measure.overlay.OverlayLine
import com.pet.measure.overlay.OverlayState
import com.pet.measure.vision.BallDetector
import com.pet.measure.vision.BallKind
import com.pet.measure.vision.BallSpec
import com.pet.measure.vision.BallTracker
import com.pet.measure.vision.CameraModel
import com.pet.measure.vision.FrameInput
import com.pet.measure.vision.Measurement
import com.pet.measure.vision.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

enum class MeasureStatus { STARTING, SCAN, FIND_JACK, FIND_BOULES, MEASURING, LOCKED, LOST, LOST_DARK, LOST_FAST, LOST_TEXTURE }

data class UiDistance(val bouleId: Int, val distanceM: Float, val team: Team)

data class MeasureUiState(
    val status: MeasureStatus = MeasureStatus.STARTING,
    val distances: List<UiDistance> = emptyList(),
    val bouleCount: Int = 0,
    val jackFound: Boolean = false,
)

/** State that outlives the view, so leaving the screen does not lose the measurement. */
class MeasureSessionState {
    val tracker = BallTracker()
    val teams = java.util.concurrent.ConcurrentHashMap<Int, Team>()
    @Volatile var locked = false
}

/**
 * Camera preview + AR tracking + ball detection + overlay.
 *
 * The GL thread runs ARCore and projects the tracked balls every frame; a background thread runs
 * the (slower) detector on copies of the CPU camera image.
 */
class ArMeasureView(context: Context, private val shared: MeasureSessionState) : FrameLayout(context), GLSurfaceView.Renderer {

    private val glView = GLSurfaceView(context)
    val overlay = MeasureOverlayView(context)
    private val background = BackgroundRenderer()
    private val main = Handler(Looper.getMainLooper())

    @Volatile var session: Session? = null
    @Volatile var spec = BallSpec()
    @Volatile var shownCount = 3
    @Volatile var formatLabel: (Float) -> String = { "${(it * 100).toInt()} cm" }
    var onState: ((MeasureUiState) -> Unit)? = null

    private val detector = BallDetector()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "pet-detector").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val detecting = AtomicBoolean(false)
    private var lastDetectAt = 0L
    private var lastPublishAt = 0L
    private var groundY = Float.NaN
    private var lastGroundSampleAt = 0L
    private var viewWidth = 1
    private var viewHeight = 1
    private var displayChanged = true

    private val pending = ConcurrentLinkedQueue<(Frame, Camera) -> Unit>()
    @Volatile private var captureCallback: ((Bitmap?) -> Unit)? = null

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val poseMatrix = FloatArray(16)

    init {
        glView.preserveEGLContextOnPause = true
        glView.setEGLContextClientVersion(2)
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        glView.setRenderer(this)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        addView(glView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun onResume() = glView.onResume()
    fun onPause() = glView.onPause()

    fun release() {
        worker.shutdownNow()
    }

    fun reset() {
        shared.tracker.reset()
        shared.teams.clear()
    }

    fun cycleTeam(ball: OverlayBall): Team {
        val next = (shared.teams[ball.id] ?: Team.NONE).next()
        shared.teams[ball.id] = next
        return next
    }

    fun removeBall(id: Int) {
        shared.tracker.hide(id)
        shared.teams.remove(id)
    }

    /** Places a ball at a screen point; [onResult] gets false if no ground was found there. */
    fun addBallAt(kind: BallKind, x: Float, y: Float, onResult: (Boolean) -> Unit) {
        pending.add { frame, camera ->
            val hit = frame.hitTest(x, y).firstOrNull {
                val t = it.trackable
                t is Plane && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING
            }
            if (hit == null) {
                main.post { onResult(false) }
                return@add
            }
            val g = Vec3(hit.hitPose.tx(), hit.hitPose.ty(), hit.hitPose.tz())
            val o = camera.pose.let { Vec3(it.tx(), it.ty(), it.tz()) }
            val r = spec.radius(kind)
            // Slide along the viewing ray up to the height of the ball's centre.
            val s = (o.y - (g.y + r)) / (o.y - g.y)
            val centre = o + (g - o) * s.coerceIn(0f, 1f)
            shared.tracker.addManual(kind, centre)
            main.post { onResult(true) }
        }
    }

    /** Captures the camera image with the overlay drawn on top. */
    fun capture(onResult: (Bitmap?) -> Unit) {
        captureCallback = { bmp ->
            main.post {
                if (bmp == null) {
                    onResult(null)
                } else {
                    overlay.drawState(android.graphics.Canvas(bmp), overlay.state)
                    onResult(bmp)
                }
            }
        }
    }

    // ---- GLSurfaceView.Renderer ----------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.08f, 0.07f, 0.06f, 1f)
        background.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewWidth = width
        viewHeight = height
        displayChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = session ?: return
        if (displayChanged) {
            @Suppress("DEPRECATION")
            val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay?.rotation ?: Surface.ROTATION_0
            session.setDisplayGeometry(rotation, viewWidth, viewHeight)
            displayChanged = false
        }
        session.setCameraTextureName(background.textureId)
        val frame = try {
            session.update()
        } catch (e: Exception) {
            return
        }
        val camera = frame.camera
        background.draw(frame)

        while (true) {
            val action = pending.poll() ?: break
            action(frame, camera)
        }

        val now = SystemClock.uptimeMillis()
        if (camera.trackingState != TrackingState.TRACKING) {
            groundY = Float.NaN
            overlay.state = OverlayState.EMPTY
            overlay.postInvalidate()
            publish(now, statusForFailure(camera.trackingFailureReason), emptyList(), 0, false)
            finishCapture()
            return
        }

        updateGround(frame, session, now)
        if (!shared.locked && !groundY.isNaN() && now - lastDetectAt > DETECT_INTERVAL_MS && !detecting.get()) {
            submitDetection(frame, camera)
            lastDetectAt = now
        }

        // Measure and project.
        val tracks = shared.tracker.snapshot()
        val jack = Measurement.pickJack(tracks)
        val distances = if (jack != null) Measurement.distances(jack, tracks, spec) else emptyList()
        val shown = distances.take(shownCount)

        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projMatrix, 0, 0.05f, 50f)
        Matrix.multiplyMM(viewProj, 0, projMatrix, 0, viewMatrix, 0)
        // Camera right vector in world space (first row of the view rotation).
        val right = Vec3(viewMatrix[0], viewMatrix[4], viewMatrix[8])

        val balls = ArrayList<OverlayBall>()
        val shownIds = shown.map { it.bouleId }.toSet()
        val closestId = shown.firstOrNull()?.bouleId
        for (t in tracks) {
            if (!t.confirmed) continue
            if (t.kind == BallKind.JACK && t.id != jack?.id) continue
            val c = toScreen(t.position) ?: continue
            val edge = toScreen(t.position + right * spec.radius(t.kind)) ?: continue
            val radius = kotlin.math.hypot(edge[0] - c[0], edge[1] - c[1])
            balls.add(
                OverlayBall(
                    t.id, t.kind, c[0], c[1], radius, shared.teams[t.id] ?: Team.NONE,
                    measured = t.id in shownIds, closest = t.id == closestId,
                ),
            )
        }
        val lines = shown.mapNotNull { d ->
            val a = toScreen(d.fromPoint) ?: return@mapNotNull null
            val b = toScreen(d.toPoint) ?: return@mapNotNull null
            OverlayLine(a[0], a[1], b[0], b[1], formatLabel(d.distanceM), shared.teams[d.bouleId] ?: Team.NONE, d.bouleId == closestId)
        }
        overlay.state = OverlayState(balls, lines)
        overlay.postInvalidate()

        val bouleCount = tracks.count { it.kind == BallKind.BOULE && it.confirmed }
        val status = when {
            groundY.isNaN() -> MeasureStatus.SCAN
            shared.locked -> MeasureStatus.LOCKED
            jack == null -> MeasureStatus.FIND_JACK
            distances.isEmpty() -> MeasureStatus.FIND_BOULES
            else -> MeasureStatus.MEASURING
        }
        publish(now, status, shown.map { UiDistance(it.bouleId, it.distanceM, shared.teams[it.bouleId] ?: Team.NONE) }, bouleCount, jack != null)
        finishCapture()
    }

    private fun finishCapture() {
        val callback = captureCallback ?: return
        captureCallback = null
        val w = viewWidth; val h = viewHeight
        val bmp = try {
            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            buf.rewind()
            raw.copyPixelsFromBuffer(buf)
            // GL rows start at the bottom.
            val flip = android.graphics.Matrix().apply { preScale(1f, -1f) }
            Bitmap.createBitmap(raw, 0, 0, w, h, flip, false).copy(Bitmap.Config.ARGB_8888, true).also { raw.recycle() }
        } catch (e: Throwable) {
            null
        }
        callback(bmp)
    }

    private fun toScreen(p: Vec3): FloatArray? {
        val v = floatArrayOf(p.x, p.y, p.z, 1f)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, viewProj, 0, v, 0)
        if (out[3] <= 1e-5f) return null
        val nx = out[0] / out[3]
        val ny = out[1] / out[3]
        return floatArrayOf((nx + 1f) / 2f * viewWidth, (1f - ny) / 2f * viewHeight)
    }

    /**
     * Ground height. Prefers the plane under the screen centre, then the largest plane. Before
     * ARCore has found a plane, samples depth and feature points across the view, so measuring
     * can start within a moment instead of waiting for plane detection.
     */
    private fun updateGround(frame: Frame, session: Session, now: Long) {
        val centreHit = frame.hitTest(viewWidth / 2f, viewHeight / 2f).firstOrNull {
            val t = it.trackable
            t is Plane && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING && t.trackingState == TrackingState.TRACKING
        }
        var y = centreHit?.hitPose?.ty() ?: session.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.subsumedBy == null }
            .maxByOrNull { it.extentX * it.extentZ }
            ?.centerPose?.ty()
        if (y == null) {
            if (now - lastGroundSampleAt < GROUND_SAMPLE_INTERVAL_MS) return
            lastGroundSampleAt = now
            y = sampledGround(frame) ?: return
        }
        groundY = if (groundY.isNaN()) y else groundY * 0.8f + y * 0.2f
    }

    /** Low-median height of depth/feature hits on a 5x5 grid; balls only push a few samples up. */
    private fun sampledGround(frame: Frame): Float? {
        val heights = ArrayList<Float>(25)
        for (gy in 1..5) for (gx in 1..5) {
            val hit = frame.hitTest(viewWidth * gx / 6f, viewHeight * gy / 6f).firstOrNull {
                val t = it.trackable
                t is DepthPoint || t is Point || t is Plane
            } ?: continue
            heights.add(hit.hitPose.ty())
        }
        if (heights.size < 6) return null
        heights.sort()
        return heights[heights.size * 2 / 5]
    }

    private fun submitDetection(frame: Frame, camera: Camera) {
        val input = try {
            frame.acquireCameraImage().use { image ->
                val w = image.width
                val h = image.height
                val planes = image.planes
                val luma = ByteArray(w * h)
                val yPlane = planes[0]
                val yBuf = yPlane.buffer
                if (yPlane.rowStride == w && yPlane.pixelStride == 1) {
                    yBuf.position(0); yBuf.get(luma, 0, w * h)
                } else {
                    for (row in 0 until h) {
                        val base = row * yPlane.rowStride
                        for (col in 0 until w) luma[row * w + col] = yBuf.get(base + col * yPlane.pixelStride)
                    }
                }
                val cw = w / 2; val ch = h / 2
                val u = ByteArray(cw * ch); val v = ByteArray(cw * ch)
                val uPlane = planes[1]; val vPlane = planes[2]
                val uBuf = uPlane.buffer; val vBuf = vPlane.buffer
                for (row in 0 until ch) {
                    val ub = row * uPlane.rowStride
                    val vb = row * vPlane.rowStride
                    for (col in 0 until cw) {
                        u[row * cw + col] = uBuf.get(ub + col * uPlane.pixelStride)
                        v[row * cw + col] = vBuf.get(vb + col * vPlane.pixelStride)
                    }
                }
                val intr = camera.imageIntrinsics
                val f = intr.focalLength
                val c = intr.principalPoint
                val dims = intr.imageDimensions
                // Intrinsics may be reported for a different size than the CPU image: rescale.
                val sx = w.toFloat() / dims[0]
                val sy = h.toFloat() / dims[1]
                camera.pose.toMatrix(poseMatrix, 0)
                val model = CameraModel.fromPoseMatrix(poseMatrix, w, h, f[0] * sx, f[1] * sy, c[0] * sx, c[1] * sy)

                var depth: ShortArray? = null
                var dw = 0; var dh = 0
                try {
                    frame.acquireDepthImage16Bits().use { d ->
                        dw = d.width; dh = d.height
                        val p = d.planes[0]
                        val buf = p.buffer.order(ByteOrder.LITTLE_ENDIAN)
                        val arr = ShortArray(dw * dh)
                        for (row in 0 until dh) for (col in 0 until dw) {
                            arr[row * dw + col] = buf.getShort(row * p.rowStride + col * p.pixelStride)
                        }
                        depth = arr
                    }
                } catch (_: Exception) {
                    // Depth is optional: not available yet, or not supported on this device.
                }
                FrameInput(w, h, luma, u, v, model, groundY, depth, dw, dh, SystemClock.uptimeMillis())
            }
        } catch (_: NotYetAvailableException) {
            return
        } catch (_: Exception) {
            return
        }

        detecting.set(true)
        val specNow = spec
        worker.execute {
            try {
                detector.spec = specNow
                val dets = detector.detect(input)
                if (!shared.locked) shared.tracker.update(dets, input.camera)
            } catch (_: Throwable) {
            } finally {
                detecting.set(false)
            }
        }
    }

    private fun publish(now: Long, status: MeasureStatus, distances: List<UiDistance>, bouleCount: Int, jackFound: Boolean) {
        if (now - lastPublishAt < PUBLISH_INTERVAL_MS) return
        lastPublishAt = now
        val state = MeasureUiState(status, distances, bouleCount, jackFound)
        main.post { onState?.invoke(state) }
    }

    private fun statusForFailure(reason: TrackingFailureReason) = when (reason) {
        TrackingFailureReason.NONE -> MeasureStatus.SCAN
        TrackingFailureReason.INSUFFICIENT_LIGHT -> MeasureStatus.LOST_DARK
        TrackingFailureReason.EXCESSIVE_MOTION -> MeasureStatus.LOST_FAST
        TrackingFailureReason.INSUFFICIENT_FEATURES -> MeasureStatus.LOST_TEXTURE
        else -> MeasureStatus.LOST
    }

    companion object {
        private const val DETECT_INTERVAL_MS = 90L
        private const val PUBLISH_INTERVAL_MS = 150L
        private const val GROUND_SAMPLE_INTERVAL_MS = 250L
    }
}
