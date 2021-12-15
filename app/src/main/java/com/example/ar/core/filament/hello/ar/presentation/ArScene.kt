package com.example.ar.core.filament.hello.ar.presentation

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.ar.core.filament.hello.ar.presentation.gesture.*
import com.example.ar.core.filament.hello.ar.presentation.renderer.*
import com.example.ar.core.filament.hello.ar.presentation.renderer.Filament
import com.example.ar.core.filament.hello.ar.presentation.renderer.opengl.createExternalTextureId
import com.example.ar.core.filament.hello.ar.presentation.renderer.utils.*
import com.google.android.filament.*
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

const val TAP_EVENT_MILLISECOND = 200

class ArScene(
    private val activity: ArActivity,
    private val surfaceView: SurfaceView,
) : DefaultLifecycleObserver, Choreographer.FrameCallback {
    companion object {
        private const val near: Float = 0.1f
        private const val far: Float = 30f
        private const val positionBufferIndex: Int = 0
        private const val uvBufferIndex: Int = 1
        private const val maxFramesPerSecond: Long = 60

        fun Frame.projectionMatrix(): M4 = FloatArray(16)
            .apply { camera.getProjectionMatrix(this, 0, near, far) }
            .let { M4(it) }
    }

    sealed class FrameRate(val factor: Long) {
        object Full : FrameRate(1)
        object Half : FrameRate(2)
        object Third : FrameRate(3)
    }

//    private val displayRotationHelper = DisplayRotationHelper(activity)

    private val filament = Filament(activity, surfaceView)
    private val lightRenderer = LightRenderer(activity, filament)
    private val planeRenderer = PlaneRenderer(activity, filament)
    private val modelRenderer = ModelRenderer(activity, this, filament)

    private val cameraStreamTextureId: Int = createExternalTextureId()
    private val choreographer: Choreographer = Choreographer.getInstance()
    private var lastTick: Long = 0
    private var frameRate: FrameRate = FrameRate.Full
    private var timestamp: Long = 0L

    private lateinit var session: Session
    private lateinit var cameraId: String
    private lateinit var cameraManager: CameraManager

    private lateinit var stream: Stream
    
    private lateinit var flatMaterialInstance: MaterialInstance

    lateinit var frame: Frame


    @Entity
    var flatRenderable: Int = 0

    var displayRotationDegrees: Int = 0

    fun displayConfigurationChange() {
        if (this::frame.isInitialized.not()) return

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

        session.setDisplayGeometry(displayRotation, viewWidth, viewHeight)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        val transformationSystem = TransformationSystem(activity.resources.displayMetrics)

        // pinch gesture events
        transformationSystem.pinchRecognizer.addOnGestureStartedListener(
            object : PinchGestureRecognizer.OnGestureStartedListener {
                override fun onGestureStarted(gesture: PinchGesture) {
                    update(gesture)

                    gesture.setGestureEventListener(
                        object : PinchGesture.OnGestureEventListener {
                            override fun onFinished(gesture: PinchGesture) {
                                update(gesture)
                            }

                            override fun onUpdated(gesture: PinchGesture) {
                                update(gesture)
                            }
                        },
                    )
                }

                fun update(gesture: PinchGesture) {
                    modelRenderer.updateModel(
                        ModelRenderer.ModelEvent.Update(
                            0f, 1f + gesture.gapDeltaInches()
                        )
                    )
                }
            }
        )

        // twist gesture events
        transformationSystem.twistRecognizer.addOnGestureStartedListener(
            object : TwistGestureRecognizer.OnGestureStartedListener {
                override fun onGestureStarted(gesture: TwistGesture) {
                    update(gesture)

                    gesture.setGestureEventListener(
                        object : TwistGesture.OnGestureEventListener {
                            override fun onFinished(gesture: TwistGesture) {
                                update(gesture)
                            }

                            override fun onUpdated(gesture: TwistGesture) {
                                update(gesture)
                            }
                        },
                    )
                }

                fun update(gesture: TwistGesture) {
                    modelRenderer.updateModel(
                        ModelRenderer.ModelEvent.Update(
                            -gesture.deltaRotationDegrees.toRadians, 1f
                        )
                    )
                }
            }
        )

        // drag gesture events
        transformationSystem.dragRecognizer.addOnGestureStartedListener(
            object : DragGestureRecognizer.OnGestureStartedListener {
                override fun onGestureStarted(gesture: DragGesture) {
                    update(
                        surfaceView,
                        gesture,
                    )

                    gesture.setGestureEventListener(
                        object : DragGesture.OnGestureEventListener {
                            override fun onFinished(gesture: DragGesture) {
                                update(
                                    surfaceView,
                                    gesture,
                                )
                            }

                            override fun onUpdated(gesture: DragGesture) {
                                update(
                                    surfaceView,
                                    gesture,
                                )
                            }
                        }
                    )
                }
                fun update(surfaceView: SurfaceView,gesture: DragGesture) {
                    ScreenPosition(
                        x = gesture.position.x / surfaceView.width.toFloat(),
                        y = gesture.position.y / surfaceView.height.toFloat(),
                    )
                        .let { ModelRenderer.ModelEvent.Move(it) }
                        .let { modelRenderer.updateModel(it) }
                }
            },
        )

        // tap and gesture events
        surfaceView.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP &&
                (motionEvent.eventTime - motionEvent.downTime) < TAP_EVENT_MILLISECOND
            ) {
                Pair(
                    surfaceView,
                    motionEvent
                ).let { (surfaceView, motionEvent) ->
                    ScreenPosition(
                        x = motionEvent.x / surfaceView.width.toFloat(),
                        y = motionEvent.y / surfaceView.height.toFloat(),
                    )
                        .let { ModelRenderer.ModelEvent.Move(it) }
                        .let { modelRenderer.updateModel(it) }
                }
            }

            transformationSystem.onTouch(motionEvent)
            true
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)

        choreographer.postFrameCallback(this)

//        displayRotationHelper.onResume()

        session  = activity.arCoreSession.session ?: return

        session.setCameraTextureName(cameraStreamTextureId)
        cameraId = session.cameraConfig.cameraId
        cameraManager = ContextCompat.getSystemService(activity, CameraManager::class.java)!!

        Log.i(TAG, "onResume: camera id $cameraId =====")
    }

    override fun onPause(owner: LifecycleOwner) {
        choreographer.removeFrameCallback(this)

//        displayRotationHelper.onPause()

        super.onPause(owner)

        modelRenderer.destroy()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        choreographer.removeFrameCallback(this)

        filament.destroy()

        super.onDestroy(owner)
    }

    override fun doFrame(frameTimeNanos: Long) {
        // Posts a frame callback to run on the next frame.
        // The callback runs once then is automatically removed.
        choreographer.postFrameCallback(this)

        // limit to max fps
        val nanoTime = System.nanoTime()
        val tick = nanoTime / (TimeUnit.SECONDS.toNanos(1) / maxFramesPerSecond)

        if (lastTick / frameRate.factor == tick / frameRate.factor) {
            return
        }
        lastTick = tick

        // render using frame from last tick to reduce possibility of jitter but increases latency
        if (// only render if we have an ar frame
            timestamp != 0L &&
            filament.uiHelper.isReadyToRender &&
            // This means you are sending frames too quickly to the GPU
            filament.renderer.beginFrame(filament.swapChain!!, frameTimeNanos)
        ) {
            filament.timestamp = timestamp
            filament.renderer.render(filament.view)
            filament.renderer.endFrame()
        }

//        displayRotationHelper.updateSessionIfNeeded(session)

        val frame = session.update()

        // During startup the camera system may not produce actual images immediately. In
        // this common case, a frame with timestamp = 0 will be returned.
        if (frame.timestamp != 0L &&
            frame.timestamp != timestamp
        ) {
            timestamp = frame.timestamp
            updateScene(frame, filament)
            lightRenderer.doFrame(frame)
            planeRenderer.doFrame(frame)
            modelRenderer.doFrame(frame)

        }
    }

    private fun updateScene(frame: Frame, filament: Filament) {
        val firstFrame = this::frame.isInitialized.not()
        this.frame = frame

        if (firstFrame) {
            displayConfigurationChange()
            val camera = frame.camera
            val intrinsics = camera.textureIntrinsics
            val dimensions = intrinsics.imageDimensions
            val width = dimensions[0]
            val height = dimensions[1]

            Log.i(TAG, "updateScene: camera info width $width, height $height")

            stream = Stream
                .Builder()
                .stream(cameraStreamTextureId.toLong()) // todo
                .width(width)
                .height(height)
                .build(filament.engine)

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
                            .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
                            .format(Texture.InternalFormat.RGB8)
                            .build(filament.engine)
                            .apply { setExternalStream(filament.engine, stream) },
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

            initFlat()
        }

        filament.scene.addEntity(flatRenderable)

        flatMaterialInstance.setParameter(
            "uvTransform",
            MaterialInstance.FloatElement.FLOAT4,
            uvTransform().floatArray,
            0,
            4,
        )

        // update camera projection
        filament.camera.setCustomProjection(
            frame.projectionMatrix().floatArray.toDoubleArray(),
            near.toDouble(),
            far.toDouble(),
        )

        val cameraTransform = frame.camera.displayOrientedPose.matrix()
        filament.camera.setModelMatrix(cameraTransform.floatArray)
    }

    private fun initFlat() {
        val tes = tessellation()

        RenderableManager
            .Builder(1)
            .castShadows(false)
            .receiveShadows(false)
            .culling(false)
            .geometry(
                0,
                RenderableManager.PrimitiveType.TRIANGLES,
                VertexBuffer
                    .Builder()
                    .vertexCount(tes.clipPosition.count())
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
                            tes.clipPosition.floatArray.toFloatBuffer(),
                        )

                        vertexBuffer.setBufferAt(
                            filament.engine,
                            uvBufferIndex,
                            tes.uvs.floatArray.toFloatBuffer(),
                        )
                    },
                IndexBuffer
                    .Builder()
                    .indexCount(tes.triangleIndices.size)
                    .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                    .build(filament.engine)
                    .apply { setBuffer(filament.engine, tes.triangleIndices.toShortBuffer()) },
            )
            .material(0, flatMaterialInstance)
            .build(filament.engine, EntityManager.get().create().also { flatRenderable = it })
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
