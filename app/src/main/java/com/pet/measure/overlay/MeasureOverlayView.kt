package com.pet.measure.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.pet.measure.data.Team
import com.pet.measure.vision.BallKind
import kotlin.math.hypot

object PetColors {
    const val TEAM_A = 0xFF4FA3FF.toInt() // Provence blue
    const val TEAM_B = 0xFFEF6C4D.toInt() // terracotta
    const val NEUTRAL = 0xFFFFFFFF.toInt()
    const val CLOSEST = 0xFFFFC94D.toInt() // sun gold
    const val WOOD = 0xFFE3A857.toInt()
    const val INK = 0xFF15130F.toInt()

    fun team(team: Team) = when (team) {
        Team.A -> TEAM_A
        Team.B -> TEAM_B
        Team.NONE -> NEUTRAL
    }
}

class OverlayBall(
    val id: Int,
    val kind: BallKind,
    val x: Float,
    val y: Float,
    val radius: Float,
    val team: Team,
    val measured: Boolean,
    val closest: Boolean,
)

class OverlayLine(
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    val label: String,
    val team: Team,
    val closest: Boolean,
)

class OverlayState(val balls: List<OverlayBall>, val lines: List<OverlayLine>) {
    companion object {
        val EMPTY = OverlayState(emptyList(), emptyList())
    }
}

/** Draws rings, measuring lines and distance labels on top of the camera image. */
class MeasureOverlayView(context: Context) : View(context) {

    @Volatile
    var state: OverlayState = OverlayState.EMPTY

    var onBallTap: ((OverlayBall) -> Unit)? = null
    var onLongPress: ((x: Float, y: Float, ball: OverlayBall?) -> Unit)? = null

    private val density = resources.displayMetrics.density

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x99000000.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            ballAt(e.x, e.y)?.let { onBallTap?.invoke(it) }
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            onLongPress?.invoke(e.x, e.y, ballAt(e.x, e.y))
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = gestures.onTouchEvent(event)

    private fun ballAt(x: Float, y: Float): OverlayBall? = state.balls
        .filter { hypot(it.x - x, it.y - y) < it.radius * 1.3f + 18 * density }
        .minByOrNull { hypot(it.x - x, it.y - y) }

    override fun onDraw(canvas: Canvas) {
        drawState(canvas, state)
    }

    fun drawState(canvas: Canvas, s: OverlayState) {
        val d = density
        // Rings: faint for balls that are not measured, team colours for measured boules.
        for (b in s.balls) {
            val color = when {
                b.kind == BallKind.JACK -> PetColors.WOOD
                b.closest -> PetColors.CLOSEST
                else -> PetColors.team(b.team)
            }
            val r = b.radius + 3 * d
            shadow.strokeWidth = (if (b.measured || b.kind == BallKind.JACK) 5f else 3f) * d
            canvas.drawCircle(b.x, b.y, r, shadow)
            ring.color = if (b.measured || b.kind == BallKind.JACK) color else withAlpha(color, 0x80)
            ring.strokeWidth = (if (b.closest) 3.5f else if (b.measured || b.kind == BallKind.JACK) 2.5f else 1.5f) * d
            canvas.drawCircle(b.x, b.y, r, ring)
            if (b.kind == BallKind.JACK) {
                dot.color = PetColors.WOOD
                canvas.drawCircle(b.x, b.y, 3 * d, dot)
            }
        }

        // Lines, farthest first so the closest one ends on top.
        for (l in s.lines.reversed()) {
            val color = if (l.closest) PetColors.CLOSEST else PetColors.team(l.team)
            shadow.strokeWidth = 7 * d
            canvas.drawLine(l.x1, l.y1, l.x2, l.y2, shadow)
            line.color = color
            line.strokeWidth = (if (l.closest) 4f else 3f) * d
            canvas.drawLine(l.x1, l.y1, l.x2, l.y2, line)
            dot.color = color
            canvas.drawCircle(l.x1, l.y1, 4 * d, dot)
            canvas.drawCircle(l.x2, l.y2, 4 * d, dot)
        }

        // Labels, placed beside each line's midpoint and nudged apart if they overlap.
        val placed = ArrayList<RectF>()
        for (l in s.lines) {
            val textSize = (if (l.closest) 19f else 16f) * d
            text.textSize = textSize
            val tw = text.measureText(l.label)
            val pw = tw + 20 * d
            val ph = textSize + 14 * d
            val mx = (l.x1 + l.x2) / 2f
            val my = (l.y1 + l.y2) / 2f
            val len = hypot(l.x2 - l.x1, l.y2 - l.y1).coerceAtLeast(1f)
            var nx = -(l.y2 - l.y1) / len
            var ny = (l.x2 - l.x1) / len
            if (ny > 0) { nx = -nx; ny = -ny } // prefer above the line
            val offset = (ph / 2f + 10 * d)
            var rect = RectF()
            for (attempt in 0 until 6) {
                val k = offset + attempt * ph * 0.9f
                val sign = if (attempt % 2 == 0) 1f else -1f
                val cx = mx + nx * k * sign
                val cy = my + ny * k * sign
                rect = RectF(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2)
                if (placed.none { RectF.intersects(it, rect) }) break
            }
            placed.add(rect)
            val color = if (l.closest) PetColors.CLOSEST else PetColors.team(l.team)
            val corner = ph / 2
            pill.color = if (l.closest) PetColors.CLOSEST else 0xE615130F.toInt()
            canvas.drawRoundRect(rect, corner, corner, pill)
            pillBorder.color = color
            pillBorder.strokeWidth = 2 * d
            canvas.drawRoundRect(rect, corner, corner, pillBorder)
            text.color = if (l.closest) PetColors.INK else Color.WHITE
            canvas.drawText(l.label, rect.centerX(), rect.centerY() - (text.descent() + text.ascent()) / 2, text)
        }
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}
