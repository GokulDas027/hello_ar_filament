package com.example.ar.core.filament.hello.ar.presentation.gesture

import android.util.DisplayMetrics
import android.view.MotionEvent

/**
 * Manage gesture events dispatching and listening
 */
class TransformationSystem(displayMetrics: DisplayMetrics) {
    // utilities to manage gesture
    private val gesturePointersUtility: GesturePointersUtility =
        GesturePointersUtility(displayMetrics)

    // list of active gesture recognizers listening
    private val recognizers: MutableList<BaseGestureRecognizer<*>> = mutableListOf()

    val dragRecognizer: DragGestureRecognizer = DragGestureRecognizer(gesturePointersUtility)
        .also { addGestureRecognizer(it) }

    val pinchRecognizer: PinchGestureRecognizer = PinchGestureRecognizer(gesturePointersUtility)
        .also { addGestureRecognizer(it) }

    val twistRecognizer: TwistGestureRecognizer = TwistGestureRecognizer(gesturePointersUtility)
        .also { addGestureRecognizer(it) }

    /**
     * Adds a gesture recognizer to this transformation system. Touch events will be dispatched to
     * the recognizer when [onTouch] is called.
     * */
    private fun addGestureRecognizer(gestureRecognizer: BaseGestureRecognizer<*>) {
        recognizers.add(gestureRecognizer)
    }

    /**
     * Dispatches touch events to the gesture recognizers contained by this transformation system.
     * */
    fun onTouch(motionEvent: MotionEvent) {
        for (recognizer in recognizers) {
            recognizer.onTouch(motionEvent)
        }
    }
}
