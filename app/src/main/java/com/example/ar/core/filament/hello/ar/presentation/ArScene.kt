package com.example.ar.core.filament.hello.ar.presentation

import android.annotation.SuppressLint
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.ar.core.filament.hello.ar.presentation.gesture.*
import com.example.ar.core.filament.hello.ar.presentation.renderer.*
import com.example.ar.core.filament.hello.ar.presentation.renderer.Filament
import com.example.ar.core.filament.hello.ar.presentation.renderer.utils.*
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.util.concurrent.TimeUnit

const val TAP_EVENT_MILLISECOND = 200

class ArScene(
    private val activity: ArActivity,
    private val surfaceView: SurfaceView,
) : DefaultLifecycleObserver, Choreographer.FrameCallback {
    companion object {
        private const val near: Float = 0.1f
        private const val far: Float = 30f
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

    private val filament = Filament(activity, surfaceView)
    private val lightRenderer = LightRenderer(activity, filament)
    private val planeRenderer = PlaneRenderer(activity, filament)
    private val modelRenderer = ModelRenderer(activity, this, filament)
    private val backgroundRenderer = BackgroundRenderer(activity, filament)
    private val choreographer: Choreographer = Choreographer.getInstance()
    private var lastTick: Long = 0
    private var frameRate: FrameRate = FrameRate.Full
    private var timestamp: Long = 0L

    private lateinit var session: Session

    lateinit var frame: Frame

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

        session  = activity.arCoreSession.session ?: return

        backgroundRenderer.resume(session)
    }

    override fun onPause(owner: LifecycleOwner) {
        choreographer.removeFrameCallback(this)

        super.onPause(owner)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        choreographer.removeFrameCallback(this)

        backgroundRenderer.destroy()
        modelRenderer.destroy()
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

        val frame = session.update()

        // During startup the camera system may not produce actual images immediately. In
        // this common case, a frame with timestamp = 0 will be returned.
        if (frame.timestamp != 0L &&
            frame.timestamp != timestamp
        ) {
            timestamp = frame.timestamp
            updateScene(frame)
        }
    }

    private fun updateScene(frame: Frame) {
        val firstFrame = this::frame.isInitialized.not()
        this.frame = frame

        if (firstFrame) {
            displayConfigurationChange()
            backgroundRenderer.setupScene(frame)
        }
        backgroundRenderer.doFrame(frame)
        lightRenderer.doFrame(frame)
        planeRenderer.doFrame(frame)
        modelRenderer.doFrame(frame)
    }

    fun displayConfigurationChange() {
        if (this::frame.isInitialized.not()) return

        backgroundRenderer.displayConfigurationChange(frame, surfaceView)
    }
}
