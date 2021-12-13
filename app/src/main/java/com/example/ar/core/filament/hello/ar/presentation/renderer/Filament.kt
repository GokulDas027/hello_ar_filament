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
    private val eglContext: EGLContext =
        createEglContext() ?: throw (Throwable("Couldn't Create EGL context"))

    //  Engine is filament's main entry-point.
    //  An Engine instance main function is to keep track of all resources created by the user
    //  and manage the rendering thread as well as the hardware renderer.
    val engine: Engine = Engine.create(eglContext)

    // A renderer instance is tied to a single surface (SurfaceView, TextureView, etc.)
    val renderer: Renderer = engine.createRenderer()

    // A scene holds all the renderable, lights, etc. to be drawn
    val scene: Scene = engine.createScene()

    // Camera represents the perception through which the scene is viewed.
    val camera: Camera = engine
        .createCamera(engine.entityManager.create())
        .also { camera ->
            // Set the exposure on the camera, this exposure follows the sunny f/16 rule
            // Since we've defined a light that has the same intensity as the sun, it
            // guarantees a proper exposure
            camera.setExposure(16f, 1f / 125f, 100f)
        }

    // A view defines a viewport, a scene and a camera for rendering
    val view: View = engine
        .createView()
        .also { view ->
            view.camera = camera
            view.scene = scene
        }

    // Consumes a blob of glTF 2.0 content (either JSON or GLB) and produces a FilamentAsset object.
    // AssetLoader does not fetch external buffer data or create textures on its own.
    // Clients can use the provided ResourceLoader class for this, which obtains the URI list from the asset.
    val assetLoader = AssetLoader(engine, UbershaderLoader(engine), EntityManager.get())
    val resourceLoader = ResourceLoader(engine)

    // A SwapChain represents an Operating System's native renderable surface.
    // Typically it's a native window or a view.
    var swapChain: SwapChain? = null

    // DisplayHelper is provided by Filament to manage the display
    val displayHelper = DisplayHelper(context)

    // // UiHelper is provided by Filament to manage either a SurfaceView, TextureView,
    // or a SurfaceHolder so it can be used to render into with Filament.
    val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
        // Sets the renderer callback that will be notified
        // when the native surface is created, destroyed or resized.
        renderCallback = SurfaceCallback()

        // Associate UiHelper with a SurfaceView. As soon as SurfaceView is ready
        attachTo(surfaceView)
    }

    fun destroy() {
        // Always detach the surface before destroying the engine
        uiHelper.detach()

        // Cleanup all resources
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)

        // Engine.destroyEntity() destroys Filament related resources only
        // (components), not the entity itself
        val entityManager = EntityManager.get()
        entityManager.destroy(camera.entity)

        // Destroying the engine will free up any resource you may have forgotten
        // to destroy, but it's recommended to do the cleanup properly
        engine.destroy()

        destroyEglContext(eglContext)
    }

    inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface)
            displayHelper.attach(renderer, surfaceView.display)
        }

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

        override fun onResized(width: Int, height: Int) {
            val aspect = width.toDouble() / height.toDouble()
            camera.setProjection(45.0, aspect, 0.1, 20.0, Camera.Fov.VERTICAL)

            view.viewport = Viewport(0, 0, width, height)
        }
    }
}
