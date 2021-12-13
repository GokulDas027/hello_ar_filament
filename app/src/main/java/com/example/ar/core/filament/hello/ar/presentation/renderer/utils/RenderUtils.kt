package com.example.ar.core.filament.hello.ar.presentation.renderer.utils

class ModelBuffers(val clipPosition: V2A, val uvs: V2A, val triangleIndices: ShortArray)

fun tessellation(): ModelBuffers {
    val tesWidth: Int = 1
    val tesHeight: Int = 1

    val clipPosition: V2A = (((tesWidth * tesHeight) + tesWidth + tesHeight + 1) * dimenV2A)
        .let { FloatArray(it) }
        .let { V2A(it) }

    val uvs: V2A = (((tesWidth * tesHeight) + tesWidth + tesHeight + 1) * dimenV2A)
        .let { FloatArray(it) }
        .let { V2A(it) }

    for (k in 0..tesHeight) {
        val v = k.toFloat() / tesHeight.toFloat()
        val y = (k.toFloat() / tesHeight.toFloat()) * 2f - 1f

        for (i in 0..tesWidth) {
            val u = i.toFloat() / tesWidth.toFloat()
            val x = (i.toFloat() / tesWidth.toFloat()) * 2f - 1f
            clipPosition.set(k * (tesWidth + 1) + i, x, y)
            uvs.set(k * (tesWidth + 1) + i, u, v)
        }
    }

    val triangleIndices = ShortArray(tesWidth * tesHeight * 6)

    for (k in 0 until tesHeight) {
        for (i in 0 until tesWidth) {
            triangleIndices[((k * tesWidth + i) * 6) + 0] =
                ((k * (tesWidth + 1)) + i + 0).toShort()
            triangleIndices[((k * tesWidth + i) * 6) + 1] =
                ((k * (tesWidth + 1)) + i + 1).toShort()
            triangleIndices[((k * tesWidth + i) * 6) + 2] =
                ((k + 1) * (tesWidth + 1) + i).toShort()

            triangleIndices[((k * tesWidth + i) * 6) + 3] =
                ((k + 1) * (tesWidth + 1) + i).toShort()
            triangleIndices[((k * tesWidth + i) * 6) + 4] =
                ((k * (tesWidth + 1)) + i + 1).toShort()
            triangleIndices[((k * tesWidth + i) * 6) + 5] =
                ((k + 1) * (tesWidth + 1) + i + 1).toShort()
        }
    }

    return ModelBuffers(clipPosition, uvs, triangleIndices)
}

// These coefficients came out Filament IndirectLight java doc for irradiance.
private val environmentalHdrToFilamentShCoefficients =
    floatArrayOf(
        0.282095f, -0.325735f, 0.325735f,
        -0.325735f, 0.273137f, -0.273137f,
        0.078848f, -0.273137f, 0.136569f
    )

fun getEnvironmentalHdrSphericalHarmonics(sphericalHarmonics: FloatArray): FloatArray =
    FloatArray(27)
        .also { irradianceData ->
            for (index in 0 until 27 step 3) {
                mulV3(
                    irradianceData,
                    index,
                    sphericalHarmonics,
                    index,
                    environmentalHdrToFilamentShCoefficients[index / 3]
                )
            }
        }
