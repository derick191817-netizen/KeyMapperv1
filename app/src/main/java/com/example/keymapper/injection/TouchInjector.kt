package com.example.keymapper.injection

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent

/**
 * Monta eventos multitoque e injeta. Todos os métodos devem ser chamados
 * na thread de [handler] (prioridade URGENT_DISPLAY).
 */
class TouchInjector(
    private val injector: Injector,
    private val displayId: () -> Int,
    private val extraDelayMs: Long
) {
    private val thread = HandlerThread("mapper-inject", Process.THREAD_PRIORITY_URGENT_DISPLAY)
        .also { it.start() }
    val handler = Handler(thread.looper)

    private val pts = LinkedHashMap<Int, FloatArray>()
    private var downTime = 0L

    private val setDisplay = try {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    } catch (e: Throwable) {
        null
    }

    fun isDown(id: Int) = pts.containsKey(id)

    fun down(id: Int, x: Float, y: Float) {
        if (pts.containsKey(id)) return
        val now = SystemClock.uptimeMillis()
        val first = pts.isEmpty()
        if (first) downTime = now
        pts[id] = floatArrayOf(x, y)
        val idx = pts.keys.indexOf(id)
        val action = if (first) MotionEvent.ACTION_DOWN
        else MotionEvent.ACTION_POINTER_DOWN or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        send(action, now)
    }

    fun move(id: Int, x: Float, y: Float) {
        val p = pts[id] ?: return
        p[0] = x
        p[1] = y
        send(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis())
    }

    fun up(id: Int) {
        if (!pts.containsKey(id)) return
        val now = SystemClock.uptimeMillis()
        val idx = pts.keys.indexOf(id)
        val action = if (pts.size == 1) MotionEvent.ACTION_UP
        else MotionEvent.ACTION_POINTER_UP or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        send(action, now)
        pts.remove(id)
    }

    fun releaseAll() {
        pts.keys.toList().forEach { up(it) }
    }

    fun shutdown() {
        handler.post {
            releaseAll()
            thread.quitSafely()
        }
    }

    private fun send(action: Int, now: Long) {
        val n = pts.size
        val props = Array(n) { MotionEvent.PointerProperties() }
        val coords = Array(n) { MotionEvent.PointerCoords() }
        var i = 0
        for ((id, xy) in pts) {
            props[i].id = id
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i].x = xy[0]
            coords[i].y = xy[1]
            coords[i].pressure = 1f
            coords[i].size = 1f
            i++
        }
        val ev = MotionEvent.obtain(
            downTime, now, action, n, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try {
            setDisplay?.invoke(ev, displayId())
        } catch (e: Throwable) {
        }
        injector.inject(ev)
        ev.recycle()
        if (extraDelayMs > 0) SystemClock.sleep(extraDelayMs)
    }
}
