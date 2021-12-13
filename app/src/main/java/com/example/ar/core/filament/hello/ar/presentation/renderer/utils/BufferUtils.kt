package com.example.ar.core.filament.hello.ar.presentation.renderer.utils

import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/** Float Buffer **/

fun FloatArray.toFloatBuffer(): FloatBuffer = ByteBuffer
    .allocateDirect(size * Float.size)
    .order(ByteOrder.nativeOrder())
    .asFloatBuffer()
    .also { floatBuffer ->
        floatBuffer.put(this)
        floatBuffer.rewind()
    }

fun FloatBuffer.polygonToVertices(m: M4): V4A {
    val f = FloatArray((capacity() / 2) * 4)
    val v = FloatArray(4)
    v[1] = 0f
    v[3] = 1f
    rewind()

    for (i in f.indices step 4) {
        v[0] = get()
        v[2] = get()
        Matrix.multiplyMV(f, i, m.floatArray, 0, v, 0)
    }

    return V4A(f)
}

fun FloatBuffer.polygonToUV(): V2A {
    val f = V2A(FloatArray(capacity()))
    rewind()

    for (i in f.indices) {
        f.set(i, get() * 10f, get() * 5f)
    }

    return f
}

fun FloatArray.toDoubleArray(): DoubleArray = DoubleArray(size)
    .also { doubleArray ->
        for (i in indices) {
            doubleArray[i] = this[i].toDouble()
        }
    }

/** Short Buffer **/

fun ShortArray.toShortBuffer(): ShortBuffer = ShortBuffer
    .allocate(size)
    .also { shortBuffer ->
        shortBuffer.put(this)
        shortBuffer.rewind()
    }

