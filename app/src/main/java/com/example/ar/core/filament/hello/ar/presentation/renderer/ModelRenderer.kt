package com.example.ar.core.filament.hello.ar.presentation.renderer

import android.content.Context
import com.example.ar.core.filament.hello.ar.presentation.ArScene
import com.example.ar.core.filament.hello.ar.presentation.renderer.utils.*
import com.google.android.filament.gltfio.FilamentAsset
import com.google.ar.core.Frame
import com.google.ar.core.Point
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

data class ScreenPosition(val x: Float, val y: Float)

/**
 * Render and Transform models
 *
 * @param context Application context
 * @param arScene ArScene instance
 * @param filament Filament instance
 */
class ModelRenderer(
    context: Context,
    private val arScene: ArScene,
    private val filament: Filament
) {
    sealed class ModelEvent {
        data class Move(val screenPosition: ScreenPosition) : ModelEvent()
        data class Update(val rotate: Float, val scale: Float) : ModelEvent()
    }

    private val canDrawBehavior: MutableStateFlow<Unit?> = MutableStateFlow(null)

    // Model transformations
    private var translation: V3 = v3Origin
    private var rotate: Float = 0f
    private var scale: Float = 1f

    private lateinit var filamentAsset: FilamentAsset

    private val coroutineScope: CoroutineScope =
        CoroutineScope(Dispatchers.Main)

    init {
        coroutineScope.launch {
            filamentAsset =
                withContext(Dispatchers.IO) {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    context.assets
                        .open("perry_the_platypus.glb")
                        .use { input ->
                            val bytes = ByteArray(input.available())
                            input.read(bytes)
                            filament.assetLoader.createAssetFromBinary(ByteBuffer.wrap(bytes))!!
                        }
                }.also { filament.resourceLoader.loadResources(it) }
        }
    }

    fun doFrame(frame: Frame) {
        coroutineScope.launch {
            doFrameEvents(frame)
        }
    }

    private suspend fun doFrameEvents(frame: Frame) {
        canDrawBehavior.filterNotNull().first()

        // update animator
        val animator = filamentAsset.animator

        if (animator.animationCount > 0) {
            animator.applyAnimation(
                0,
                (frame.timestamp /
                        TimeUnit.SECONDS.toNanos(1).toDouble())
                    .toFloat() %
                        animator.getAnimationDuration(0),
            )

            animator.updateBoneMatrices()
        }

        filament.scene.addEntities(filamentAsset.entities)

        filament.engine.transformManager.setTransform(
            filament.engine.transformManager.getInstance(filamentAsset.root),
            m4Identity()
                .translate(translation.x, translation.y, translation.z)
                .rotate(rotate.toDegrees, 0f, 1f, 0f)
                .scale(scale, scale, scale)
                .floatArray,
        )
    }

    fun updateModel(modelEvent: ModelEvent) {
        coroutineScope.launch {
            updateModelEvents(modelEvent)
        }
    }

    private fun updateModelEvents(modelEvent: ModelEvent) {
        when (modelEvent) {
            is ModelEvent.Move -> {
                // translation
                arScene.frame.hitTest(
                    filament.surfaceView.width.toFloat() * modelEvent.screenPosition.x,
                    filament.surfaceView.height.toFloat() * modelEvent.screenPosition.y,
                )
                    .maxByOrNull { it.trackable is Point }
                    ?.let {
                        canDrawBehavior.tryEmit(Unit)
                        translation = V3(it.hitPose.translation)
                    }
            }
            is ModelEvent.Update -> {
                // rotation and scale
                Pair((rotate + modelEvent.rotate).clampToTau, scale * modelEvent.scale)
                    .let { (r, s) ->
                        rotate = r
                        scale = s
                    }
            }
        }
    }

    fun destroy() {
        coroutineScope.cancel()
    }
}
