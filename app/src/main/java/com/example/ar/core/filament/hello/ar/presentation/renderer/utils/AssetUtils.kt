package com.example.ar.core.filament.hello.ar.presentation.renderer.utils

import android.content.Context
import java.nio.ByteBuffer
import java.nio.channels.Channels


fun Context.readUncompressedAsset(@Suppress("SameParameterValue") assetName: String): ByteBuffer {
    assets.openFd(assetName).use { fd ->
        val input = fd.createInputStream()
        val dst = ByteBuffer.allocate(fd.length.toInt())

        val src = Channels.newChannel(input)
        src.read(dst)
        src.close()

        return dst.apply { rewind() }
    }
}
