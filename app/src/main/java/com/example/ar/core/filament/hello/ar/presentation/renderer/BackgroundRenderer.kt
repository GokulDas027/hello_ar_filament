package com.example.ar.core.filament.hello.ar.presentation.renderer

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image
import android.os.Build
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import com.example.ar.core.filament.hello.ar.presentation.ArActivity
import com.example.ar.core.filament.hello.ar.presentation.ArScene.Companion.projectionMatrix
import com.example.ar.core.filament.hello.ar.presentation.TAG
import com.example.ar.core.filament.hello.ar.presentation.renderer.opengl.createExternalTextureId
import com.example.ar.core.filament.hello.ar.presentation.renderer.utils.*
import com.google.android.filament.*
import com.google.ar.core.ArImage
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import kotlin.math.roundToInt

class BackgroundRenderer(
    private val activity: ArActivity,
    private val filament: Filament
) {
    companion object {
        private const val near: Float = 0.1f
        private const val far: Float = 30f
        private const val positionBufferIndex: Int = 0
        private const val uvBufferIndex: Int = 1
    }

    private val cameraStreamTextureId: Int = createExternalTextureId()
    private val entityManager: EntityManager = EntityManager.get()

    private lateinit var depthTexture: Texture
    private lateinit var flatMaterialInstance: MaterialInstance
    private lateinit var depthMaterialInstance: MaterialInstance

    private lateinit var cameraId: String
    private lateinit var cameraManager: CameraManager

    private var session: Session? = null
    private var hasDepthImage: Boolean = false
    private var displayRotationDegrees: Int = 0
    private var frameTextureDimensions = IntArray(2)

    @Entity
    var flatRenderable: Int = 0
    @Entity
    var depthRenderable: Int = 0

    fun resume(session: Session) {
        this.session = session
        session.setCameraTextureName(cameraStreamTextureId)
        cameraId = session.cameraConfig.cameraId
        cameraManager = ContextCompat.getSystemService(activity, CameraManager::class.java)!!
    }

    fun setupScene(frame: Frame) {
        val camera = frame.camera
        val intrinsics = camera.textureIntrinsics
        frameTextureDimensions = intrinsics.imageDimensions

        setupFlatBackground()
    }

    fun doFrame(frame: Frame) {
        (if (session?.isDepthModeSupported(Config.DepthMode.AUTOMATIC) == true) Unit else null)
            ?.let {
                if (hasDepthImage.not()) {
                    try {
                        val depthImage = frame.acquireDepthImage() as ArImage

                        if (depthImage.planes[0].buffer[0] != 0.toByte()) {
                            hasDepthImage = true

                            if (this::depthTexture.isInitialized.not()) {
                                setupDepthBackground(depthImage)
                            }

                            updateDepthBackground(depthImage)
                        } else {
                            null
                        }
                    } catch (error: Throwable) {
                        null
                    }
                } else Unit
            }
            ?: run {
                updateFlatBackground()
            }

        // update camera projection
        filament.camera.setCustomProjection(
            frame.projectionMatrix().floatArray.toDoubleArray(),
            near.toDouble(),
            far.toDouble(),
        )

        val cameraTransform = frame.camera.displayOrientedPose.matrix()
        filament.camera.setModelMatrix(cameraTransform.floatArray)
        val instance = filament.engine.transformManager.create(depthRenderable)
        filament.engine.transformManager.setTransform(instance, cameraTransform.floatArray)
    }

    fun destroy() {
        session = null

        filament.engine.destroyEntity(flatRenderable)
        filament.engine.destroyEntity(depthRenderable)
        filament.engine.destroyTexture(depthTexture)
        filament.engine.destroyMaterialInstance(flatMaterialInstance)
        filament.engine.destroyMaterialInstance(depthMaterialInstance)

        entityManager.destroy(flatRenderable)
        entityManager.destroy(depthRenderable)
    }

    private fun setupFlatBackground() {
        flatMaterialInstance = activity
            .readUncompressedAsset("materials/flat.filamat")
            .let { byteBuffer ->
                Material
                    .Builder()
                    .payload(byteBuffer, byteBuffer.remaining())
            }
            .build(filament.engine)
            .createInstance()
            .also { materialInstance ->
                materialInstance.setParameter(
                    "cameraTexture",
                    Texture
                        .Builder()
                        .importTexture(cameraStreamTextureId.toLong())
                        .width(frameTextureDimensions[0])
                        .height(frameTextureDimensions[1])
                        .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
                        .format(Texture.InternalFormat.RGB8)
                        .build(filament.engine),
                    TextureSampler(
                        TextureSampler.MinFilter.LINEAR,
                        TextureSampler.MagFilter.LINEAR,
                        TextureSampler.WrapMode.CLAMP_TO_EDGE,
                    )
                )

                materialInstance.setParameter(
                    "uvTransform",
                    MaterialInstance.FloatElement.FLOAT4,
                    m4Identity().floatArray,
                    0,
                    4,
                )
            }

        val flatVertexData = tessellation()

        flatRenderable = entityManager.create()

        val flatVertexBuffer: VertexBuffer = VertexBuffer.Builder()
            .vertexCount(flatVertexData.clipPosition.count())
            .bufferCount(2)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION,
                positionBufferIndex,
                VertexBuffer.AttributeType.FLOAT2,
                0,
                0,
            )
            .attribute(
                VertexBuffer.VertexAttribute.UV0,
                uvBufferIndex,
                VertexBuffer.AttributeType.FLOAT2,
                0,
                0,
            )
            .build(filament.engine).also { vertexBuffer ->
                vertexBuffer.setBufferAt(
                    filament.engine,
                    positionBufferIndex,
                    flatVertexData.clipPosition.floatArray.toFloatBuffer(),
                )

                vertexBuffer.setBufferAt(
                    filament.engine,
                    uvBufferIndex,
                    flatVertexData.uvs.floatArray.toFloatBuffer(),
                )
            }

        val flatIndexBuffer: IndexBuffer = IndexBuffer
            .Builder()
            .indexCount(flatVertexData.triangleIndices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(filament.engine)
            .apply { setBuffer(filament.engine, flatVertexData.triangleIndices.toShortBuffer()) }

        RenderableManager
            .Builder(1)
            .castShadows(false)
            .receiveShadows(false)
            .culling(false)
            .geometry(
                0,
                RenderableManager.PrimitiveType.TRIANGLES,
                flatVertexBuffer,
                flatIndexBuffer,
            )
            .material(0, flatMaterialInstance)
            .build(filament.engine, flatRenderable)
    }

    private fun updateFlatBackground() {
        flatMaterialInstance.setParameter(
            "uvTransform",
            MaterialInstance.FloatElement.FLOAT4,
            uvTransform().floatArray,
            0,
            4,
        )

        filament.scene.removeEntity(depthRenderable)
        filament.scene.addEntity(flatRenderable)
    }

    private fun setupDepthBackground(depthImage: Image) {
        depthMaterialInstance = activity
            .readUncompressedAsset("materials/depth.filamat")
            .let { byteBuffer ->
                Material
                    .Builder()
                    .payload(byteBuffer, byteBuffer.remaining())
            }
            .build(filament.engine)
            .createInstance()
            .also { materialInstance ->
                materialInstance.setParameter(
                    "cameraTexture",
                    Texture
                        .Builder()
                        .importTexture(cameraStreamTextureId.toLong())
                        .width(frameTextureDimensions[0])
                        .height(frameTextureDimensions[1])
                        .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
                        .format(Texture.InternalFormat.RGB8)
                        .build(filament.engine),
                    TextureSampler(
                        TextureSampler.MinFilter.LINEAR,
                        TextureSampler.MagFilter.LINEAR,
                        TextureSampler.WrapMode.CLAMP_TO_EDGE,
                    ),
                    //.also { it.anisotropy = 8.0f }
                )

                materialInstance.setParameter(
                    "depthTexture",
                    Texture
                        .Builder()
                        .width(depthImage.width)
                        .height(depthImage.height)
                        .sampler(Texture.Sampler.SAMPLER_2D)
                        .format(Texture.InternalFormat.RG8)
                        .levels(1)
                        .build(filament.engine)
                        .also { depthTexture = it },
                    TextureSampler(), //.also { it.anisotropy = 8.0f }
                )

                materialInstance.setParameter(
                    "uvTransform",
                    MaterialInstance.FloatElement.FLOAT4,
                    m4Identity().floatArray,
                    0,
                    4,
                )
            }

        val depthVertexData = tessellation()

        depthRenderable = entityManager.create()

        val depthVertexBuffer: VertexBuffer = VertexBuffer
            .Builder()
            .vertexCount(depthVertexData.clipPosition.count())
            .bufferCount(2)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION,
                positionBufferIndex,
                VertexBuffer.AttributeType.FLOAT2,
                0,
                0,
            )
            .attribute(
                VertexBuffer.VertexAttribute.UV0,
                uvBufferIndex,
                VertexBuffer.AttributeType.FLOAT2,
                0,
                0,
            )
            .build(filament.engine)
            .also { vertexBuffer ->
                vertexBuffer.setBufferAt(
                    filament.engine,
                    positionBufferIndex,
                    depthVertexData.clipPosition.floatArray.toFloatBuffer()
                )

                vertexBuffer.setBufferAt(
                    filament.engine,
                    uvBufferIndex,
                    depthVertexData.uvs.floatArray.toFloatBuffer()
                )
            }

        val depthIndexBuffer = IndexBuffer
            .Builder()
            .indexCount(depthVertexData.triangleIndices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(filament.engine)
            .apply { setBuffer(filament.engine, depthVertexData.triangleIndices.toShortBuffer()) }

        RenderableManager
            .Builder(1)
            .castShadows(false)
            .receiveShadows(false)
            .culling(false)
            .geometry(
                0,
                RenderableManager.PrimitiveType.TRIANGLES,
                depthVertexBuffer,
                depthIndexBuffer
            )
            .material(0, depthMaterialInstance)
            .build(filament.engine, depthRenderable)
    }

    private fun updateDepthBackground(depthImage: Image) {
        depthTexture.setImage(
            filament.engine,
            0,
            Texture.PixelBufferDescriptor(
                depthImage.planes[0].buffer,
                Texture.Format.RG,
                Texture.Type.UBYTE,
                1,
                0,
                0,
                0,
                @Suppress("DEPRECATION")
                (Handler()),
            ) {
                depthImage.close()
                hasDepthImage = false
            }
        )

        depthMaterialInstance.setParameter(
            "uvTransform",
            MaterialInstance.FloatElement.FLOAT4,
            uvTransform().floatArray,
            0,
            4,
        )

        filament.scene.removeEntity(flatRenderable)
        filament.scene.addEntity(depthRenderable)
    }

    fun displayConfigurationChange(frame: Frame, surfaceView: SurfaceView) {
        val intrinsics = frame.camera.textureIntrinsics
        val dimensions = intrinsics.imageDimensions

        val displayWidth: Int
        val displayHeight: Int
        val displayRotation: Int

        DisplayMetrics()
            .also { displayMetrics ->
                @Suppress("DEPRECATION")
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display
                else activity.windowManager.defaultDisplay)!!
                    .also { display ->
                        display.getRealMetrics(displayMetrics)
                        displayRotation = display.rotation
                    }

                displayWidth = displayMetrics.widthPixels
                displayHeight = displayMetrics.heightPixels
            }

        displayRotationDegrees =
            when (displayRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> throw Exception("Invalid Display Rotation")
            }

        Log.i(TAG, "displayConfigurationChange: $displayRotationDegrees")

        // camera width and height relative to display
        val cameraWidth: Int
        val cameraHeight: Int

        when (cameraManager
            .getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SENSOR_ORIENTATION)!!) {
            0, 180 -> when (displayRotation) {
                Surface.ROTATION_0, Surface.ROTATION_180 -> {
                    cameraWidth = dimensions[0]
                    cameraHeight = dimensions[1]
                }
                else -> {
                    cameraWidth = dimensions[1]
                    cameraHeight = dimensions[0]
                }
            }
            else -> when (displayRotation) {
                Surface.ROTATION_0, Surface.ROTATION_180 -> {
                    cameraWidth = dimensions[1]
                    cameraHeight = dimensions[0]
                }
                else -> {
                    cameraWidth = dimensions[0]
                    cameraHeight = dimensions[1]
                }
            }
        }

        val cameraRatio: Float = cameraWidth.toFloat() / cameraHeight.toFloat()
        val displayRatio: Float = displayWidth.toFloat() / displayHeight.toFloat()

        val viewWidth: Int
        val viewHeight: Int

        if (displayRatio < cameraRatio) {
            // width constrained
            viewWidth = displayWidth
            viewHeight = (displayWidth.toFloat() / cameraRatio).roundToInt()
        } else {
            // height constrained
            viewWidth = (displayHeight.toFloat() * cameraRatio).roundToInt()
            viewHeight = displayHeight
        }

        surfaceView.updateLayoutParams<FrameLayout.LayoutParams> {
            width = viewWidth
            height = viewHeight
        }

        session?.setDisplayGeometry(displayRotation, viewWidth, viewHeight)
    }

    private fun uvTransform(): M4 = m4Identity()
        .translate(.5f, .5f, 0f)
        .rotate(imageRotation().toFloat(), 0f, 0f, -1f)
        .translate(-.5f, -.5f, 0f)

    private fun imageRotation(): Int = (cameraManager
        .getCameraCharacteristics(cameraId)
        .get(CameraCharacteristics.SENSOR_ORIENTATION)!! +
            when (displayRotationDegrees) {
                0 -> 90
                90 -> 0
                180 -> 270
                270 -> 180
                else -> throw Exception()
            } + 270) % 360
}
