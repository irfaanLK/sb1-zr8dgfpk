package com.pet.measure.vision

import kotlin.math.sqrt

/** Small immutable 3D vector in metres, world space (ARCore: +Y is up). */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun length() = sqrt(dot(this))
    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-9f) this else this * (1f / l)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}

fun lerp(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t

/**
 * Pinhole model of the CPU camera image at the moment it was captured.
 *
 * Follows ARCore's Camera.getPose() convention: +X right, +Y up, -Z forward, where
 * right/up are relative to the image readout (u grows right, v grows down).
 *
 * @param rotation camera-to-world rotation, 3x3 row-major.
 */
class CameraModel(
    val width: Int,
    val height: Int,
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val rotation: FloatArray,
    val position: Vec3,
) {
    /** World-space ray direction for an image pixel. Its camera-space z component is -1. */
    fun rayDirection(u: Float, v: Float): Vec3 {
        val dx = (u - cx) / fx
        val dy = -(v - cy) / fy
        val dz = -1f
        val r = rotation
        return Vec3(
            r[0] * dx + r[1] * dy + r[2] * dz,
            r[3] * dx + r[4] * dy + r[5] * dz,
            r[6] * dx + r[7] * dy + r[8] * dz,
        )
    }

    /** Depth along the optical axis at which the pixel ray meets the plane y = [planeY], or NaN. */
    fun depthToPlane(u: Float, v: Float, planeY: Float): Float {
        val d = rayDirection(u, v)
        if (kotlin.math.abs(d.y) < 1e-6f) return Float.NaN
        val t = (planeY - position.y) / d.y
        return if (t > 0f) t else Float.NaN
    }

    /** Point where the pixel ray meets the horizontal plane y = [planeY]. */
    fun intersectHorizontal(u: Float, v: Float, planeY: Float): Vec3? {
        val t = depthToPlane(u, v, planeY)
        if (t.isNaN()) return null
        return position + rayDirection(u, v) * t
    }

    /** Projects a world point into image pixels; returns null when it is behind the camera. */
    fun project(p: Vec3): FloatArray? {
        val d = p - position
        val r = rotation
        // R^T * d
        val xc = r[0] * d.x + r[3] * d.y + r[6] * d.z
        val yc = r[1] * d.x + r[4] * d.y + r[7] * d.z
        val zc = r[2] * d.x + r[5] * d.y + r[8] * d.z
        if (zc > -1e-4f) return null
        return floatArrayOf(cx + fx * (xc / -zc), cy - fy * (yc / -zc))
    }

    fun isInImage(p: Vec3, margin: Float): Boolean {
        val uv = project(p) ?: return false
        return uv[0] >= margin && uv[1] >= margin && uv[0] < width - margin && uv[1] < height - margin
    }

    companion object {
        /** Builds a model from a column-major 4x4 pose matrix (as Pose.toMatrix produces). */
        fun fromPoseMatrix(
            m: FloatArray, width: Int, height: Int, fx: Float, fy: Float, cx: Float, cy: Float,
        ): CameraModel {
            val r = FloatArray(9)
            for (i in 0 until 3) for (j in 0 until 3) r[i * 3 + j] = m[j * 4 + i]
            return CameraModel(width, height, fx, fy, cx, cy, r, Vec3(m[12], m[13], m[14]))
        }
    }
}
