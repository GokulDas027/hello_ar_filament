package com.example.ar.core.filament.hello.ar.presentation.renderer

import android.content.Context
import android.opengl.EGLContext
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderLoader
import com.example.ar.core.filament.hello.ar.presentation.renderer.opengl.createEglContext
import com.example.ar.core.filament.hello.ar.presentation.renderer.opengl.destroyEglContext

/**
 * Create, Setup and Manage Filament Engine
 */
class Filament(context: Context, val surfaceView: SurfaceView) {
    var timestamp: Long = 0L

    /** Create an EglContext and EglSurface to draw with OpenGL ES (GLES) */
    private val eglContext: EGLContext = createEglContext()!!

    /**
     * Engine is filament's main entry-point.
     * An Engine instance main function is to keep track of all resources created by the user
     * and manage the rendering thread as well as the hardware renderer.
     */
    val engine: Engine = Engine.create(eglContext)

    /**
     * A Renderer instance represents an operating system's window.
     * Typically, applications create a Renderer per window. The Renderer generates drawing commands
     * for the render thread and manages frame latency. A Renderer generates drawing commands
     * from a View, itself containing a Scene description
     */
    val renderer: Renderer = engine.createRenderer()

    /**
     * A Scene is a flat container of RenderableManager and LightManager components.
     *
     * A Scene doesn't provide a hierarchy of objects, i.e.: it's not a scene-graph.
     *
     * However, it manages the list of objects to render and the list of lights.
     * These can be added or removed from a Scene at any time. Moreover clients can use
     * TransformManager to create a graph of transforms.
     *
     * A RenderableManager component must be added to a Scene in order to be rendered,
     * and the Scene must be provided to a View.
     */
    val scene: Scene = engine.createScene()

    /**
     * Camera represents the eye through which the scene is viewed.
     * A Camera has a position and orientation and controls the projection and exposure parameters.
     */
    val camera: Camera = engine
        .createCamera(engine.entityManager.create())
        .also { camera ->
            // Set the exposure on the camera, this exposure follows the sunny f/16 rule
            // Since we've defined a light that has the same intensity as the sun, it
            // guarantees a proper exposure
            camera.setExposure(16f, 1f / 125f, 100f)
        }

    /**
     * View Encompasses all the state needed for rendering a Scene.
     * including scene, camera, viewport and some other rendering parameters
     */
    val view: View = engine
        .createView()
        .also { view ->
            view.camera = camera
            view.scene = scene
        }

    // Consumes a blob of glTF 2.0 content (either JSON or GLB) and produces a FilamentAsset object.
    // AssetLoader does not fetch external buffer data or create textures on its own.
    // Clients can use the provided ResourceLoader class for this, which obtains the URI list from the asset.
    val assetLoader =
        AssetLoader(engine, UbershaderLoader(engine), EntityManager.get())

    val resourceLoader = ResourceLoader(engine)

    // A SwapChain represents an Operating System's native renderable surface.
    var swapChain: SwapChain? = null

    // Creates a DisplayHelper which helps managing a Display.
    val displayHelper = DisplayHelper(context)

    // UiHelper is a simple class that can manage either a SurfaceView, TextureView,
    // or a SurfaceHolder so it can be used to render into with Filament.
    val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
        // Sets the renderer callback that will be notified
        // when the native surface is created, destroyed or resized.
        renderCallback = object : UiHelper.RendererCallback {
            // Called when the underlying native window has changed.
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
                displayHelper.attach(renderer, surfaceView.display)
            }

            // Called when the surface is going away.
            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    // Required to ensure we don't return before Filament is done executing the
                    // destroySwapChain command, otherwise Android might destroy the Surface
                    // too early
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            // Called when the underlying native window has been resized.
            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
            }
        }

        // Associate UiHelper with a SurfaceView. As soon as SurfaceView is ready
        attachTo(surfaceView)
    }

    fun destroy() {
        // Always detach the surface before destroying the engine
        uiHelper.detach()
        engine.destroy()
        destroyEglContext(eglContext)
    }
}