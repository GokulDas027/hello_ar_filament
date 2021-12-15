package com.example.ar.core.filament.hello.ar.presentation.renderer.utils

import android.app.Activity
import android.opengl.Matrix
import android.os.Build
import android.view.Surface
import android.view.View
import com.google.ar.core.Pose
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

inline val Float.Companion.size get() = java.lang.Float.BYTES

inline val Float.Companion.degreesInTau: Float get() = 360f
inline val Float.Companion.tau: Float get() = PI.toFloat() * 2f
inline val Float.toDegrees: Float get() = this * (Float.degreesInTau / Float.tau)
inline val Float.toRadians: Float get() = this * (Float.tau / Float.degreesInTau)

inline val Float.clampToTau: Float
    get() =
        when {
            this < 0f ->
                this + ceil(-this / Float.tau) * Float.tau
            this >= Float.tau ->
                this - floor(this / Float.tau) * Float.tau
            else ->
                this
        }

data class V2A(val floatArray: FloatArray)

data class V3(val floatArray: FloatArray)

data class V3A(val floatArray: FloatArray)

data class V4A(val floatArray: FloatArray)

data class M4(val floatArray: FloatArray)

@JvmInline
value class TriangleIndexArray(val shortArray: ShortArray)

inline fun triangleIndexArrayCreate(
    count: Int,
    i1: (Int) -> Short,
    i2: (Int) -> Short,
    i3: (Int) -> Short
): TriangleIndexArray {
    val triangleIndexArray = TriangleIndexArray(ShortArray(count * 3))

    for (i in 0 until count) {
        val k = i * 3
        triangleIndexArray.shortArray[k + 0] = i1(i)
        triangleIndexArray.shortArray[k + 1] = i2(i)
        triangleIndexArray.shortArray[k + 2] = i3(i)
    }

    return triangleIndexArray
}

fun m4Identity(): M4 = FloatArray(16)
    .also { Matrix.setIdentityM(it, 0) }
    .let { M4(it) }

fun M4.scale(x: Float, y: Float, z: Float): M4 = FloatArray(16)
    .also { Matrix.scaleM(it, 0, floatArray, 0, x, y, z) }
    .let { M4(it) }

fun M4.rotate(angle: Float, x: Float, y: Float, z: Float): M4 = FloatArray(16)
    .also { Matrix.rotateM(it, 0, floatArray, 0, angle, x, y, z) }
    .let { M4(it) }

fun M4.translate(x: Float, y: Float, z: Float): M4 = FloatArray(16)
    .also { Matrix.translateM(it, 0, floatArray, 0, x, y, z) }
    .let { M4(it) }

fun M4.multiply(m: M4): M4 = FloatArray(16)
    .also { Matrix.multiplyMM(it, 0, floatArray, 0, m.floatArray, 0) }
    .let { M4(it) }

fun M4.invert(): M4 = FloatArray(16)
    .also { Matrix.invertM(it, 0, floatArray, 0) }
    .let { M4(it) }

fun m4Rotate(angle: Float, x: Float, y: Float, z: Float): M4 = FloatArray(16)
    .also { Matrix.setRotateM(it, 0, angle, x, y, z) }
    .let { M4(it) }

inline fun v2aCreate(count: Int, x: (Int) -> Float, y: (Int) -> Float): V2A =
    V2A(FloatArray(count * dimenV2A))
        .also {
            for (i in it.indices) {
                it.set(i, x(i), y(i))
            }
        }

/** 3D Vector **/

const val dimenV2A: Int = 2
inline val V2A.dimen: Int get() = dimenV2A
fun V2A.count(): Int = floatArray.size / dimen
inline val V2A.indices: IntRange get() = IntRange(0, count() - 1)

fun V2A.set(i: Int, x: Float, y: Float) {
    floatArray[(i * dimen) + 0] = x
    floatArray[(i * dimen) + 1] = y
}

/** 3D vector **/

const val dimenV3A: Int = 3
inline val V3A.dimen: Int get() = dimenV3A

fun V3A.set(i: Int, x: Float, y: Float, z: Float) {
    floatArray[(i * dimen) + 0] = x
    floatArray[(i * dimen) + 1] = y
    floatArray[(i * dimen) + 2] = z
}

fun mulV3(r: FloatArray, ri: Int, v: FloatArray, vi: Int, s: Float) {
    r[ri + 0] = v[vi + 0] * s
    r[ri + 1] = v[vi + 1] * s
    r[ri + 2] = v[vi + 2] * s
}

fun V3.dot(v: V3): Float =
    x * v.x + y * v.y + z * v.z

fun V3.neg(): V3 =
    v3(
        -x,
        -y,
        -z
    )

val v3Origin: V3 = v3(0f, 0f, 0f)

fun v3(x: Float, y: Float, z: Float): V3 = FloatArray(3)
    .let { V3(it) }
    .also {
        it.x = x
        it.y = y
        it.z = z
    }

inline var V3.x: Float
    get() = floatArray[0]
    set(x) {
        floatArray[0] = x
    }

inline var V3.y: Float
    get() = floatArray[1]
    set(y) {
        floatArray[1] = y
    }

inline var V3.z: Float
    get() = floatArray[2]
    set(z) {
        floatArray[2] = z
    }

fun V3.normalize(): V3 =
    scale(1f / magnitude())

fun V3.magnitude(): Float =
    sqrt(dot(this))

fun V3.scale(s: Float): V3 =
    v3(
        x * s,
        y * s,
        z * s
    )

fun V3.div(d: Float): V3 =
    v3(
        x / d,
        y / d,
        z / d
    )

/**
 * Subtract vectors
 * */
fun V3.sub(v: V3): V3 =
    v3(
        x - v.x,
        y - v.y,
        z - v.z
    )

/**
 * Check vectors for equivalence
 * */
fun V3.eq(v: V3): Boolean = x == v.x &&
        y == v.y &&
        z == v.z

/** 4D vector **/

const val dimenV4A: Int = 4
inline val V4A.dimen: Int get() = dimenV4A
inline val V4A.count: Int get() = floatArray.size / dimen

fun V4A.getX(i: Int): Float = floatArray[(i * dimen) + 0]
fun V4A.getY(i: Int): Float = floatArray[(i * dimen) + 1]
fun V4A.getZ(i: Int): Float = floatArray[(i * dimen) + 2]
fun V4A.getW(i: Int): Float = floatArray[(i * dimen) + 3]

fun V4A.set(i: Int, x: Float, y: Float, z: Float, w: Float) {
    floatArray[(i * dimen) + 0] = x
    floatArray[(i * dimen) + 1] = y
    floatArray[(i * dimen) + 2] = z
    floatArray[(i * dimen) + 3] = w
}

// uses world space to determine UV coordinates for better stability
fun V4A.horizontalToUV(): V2A = v2aCreate(count, { i -> getX(i) * 10f }, { i -> getZ(i) * 5f })

/** Activity Utils **/

fun Pose.matrix(): M4 = FloatArray(16)
    .also { toMatrix(it, 0) }
    .let { M4(it) }
