package com.example.keymapper.mapping

import android.graphics.Point
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.injection.TouchInjector
import com.example.keymapper.input.SensitivityEngine
import kotlin.math.hypot
import kotlin.math.min

/**
 * Converte teclas/mouse em toques. Tudo roda na thread do TouchInjector.
 * WASD = joystick, movimento do mouse = arrasto de câmera, demais teclas = botões do HUD.
 */
class MappingEngine(
    private val touch: TouchInjector,
    private val screen: () -> Point,
    private val cfg: () -> AppSettings
) {
    private companion object {
        const val JOY = 0
        const val LOOK = 1
    }

    private val sens = SensitivityEngine()
    private val keys = HashSet<Int>()
    private val toggled = HashSet<Int>()
    private var joyActive = false
    private var lookActive = false
    private var lx = 0f
    private var ly = 0f

    @Volatile
    var paused = false

    private val releaseLook = Runnable { endLook() }

    private fun pid(b: HudButton) = 2 + (b.id % 28)

    fun onKey(code: Int, down: Boolean) {
        if (paused) return
        touch.handler.post { handleKey(code, down) }
    }

    fun onMouseButton(bit: Int, down: Boolean) {
        val code = when (bit) {
            MotionEvent.BUTTON_PRIMARY -> -1
            MotionEvent.BUTTON_SECONDARY -> -2
            else -> -3
        }
        onKey(code, down)
    }

    fun onDelta(dx: Float, dy: Float) {
        if (paused) return
        touch.handler.post { handleLook(dx, dy) }
    }

    fun releaseAll() {
        touch.handler.post {
            touch.handler.removeCallbacks(releaseLook)
            keys.clear()
            toggled.clear()
            joyActive = false
            lookActive = false
            sens.reset()
            touch.releaseAll()
        }
    }

    // ---- teclas ----

    private fun handleKey(code: Int, down: Boolean) {
        val s = cfg()
        if (code == KeyEvent.KEYCODE_W || code == KeyEvent.KEYCODE_A ||
            code == KeyEvent.KEYCODE_S || code == KeyEvent.KEYCODE_D
        ) {
            if (down) keys.add(code) else keys.remove(code)
            updateJoy(s)
            return
        }
        for (b in s.buttons) {
            if (b.key != code) continue
            when (b.kind) {
                Kind.TAP -> if (down) press(b, s) else release(b)
                Kind.TOGGLE -> if (down) {
                    if (toggled.remove(b.id)) release(b) else {
                        toggled.add(b.id)
                        press(b, s)
                    }
                }
                Kind.MACRO -> for (id in b.macro) {
                    val t = s.buttons.firstOrNull { it.id == id } ?: continue
                    if (down) press(t, s) else if (!toggled.contains(t.id)) release(t)
                }
            }
        }
    }

    private fun press(b: HudButton, s: AppSettings) {
        val p = pid(b)
        if (touch.isDown(p)) return
        val sz = screen()
        touch.down(p, b.x * sz.x, b.y * sz.y)
    }

    private fun release(b: HudButton) {
        touch.up(pid(b))
    }

    // ---- joystick (WASD) ----

    private fun updateJoy(s: AppSettings) {
        val vx = (if (keys.contains(KeyEvent.KEYCODE_D)) 1 else 0) - (if (keys.contains(KeyEvent.KEYCODE_A)) 1 else 0)
        val vy = (if (keys.contains(KeyEvent.KEYCODE_S)) 1 else 0) - (if (keys.contains(KeyEvent.KEYCODE_W)) 1 else 0)
        if (vx == 0 && vy == 0) {
            if (joyActive) {
                touch.up(JOY)
                joyActive = false
            }
            return
        }
        val sz = screen()
        val len = hypot(vx.toFloat(), vy.toFloat())
        val cx = s.joyX * sz.x
        val cy = s.joyY * sz.y
        val r = s.joyR * min(sz.x, sz.y)
        if (!joyActive) {
            touch.down(JOY, cx, cy)
            joyActive = true
        }
        touch.move(JOY, cx + vx / len * r, cy + vy / len * r)
    }

    // ---- câmera (mouse) ----

    private fun handleLook(dx: Float, dy: Float) {
        val s = cfg()
        val ady = if (s.invertY) -dy else dy
        if (hypot(dx, ady) < s.deadzone) return
        sens.process(dx, ady, s)
        val ox = sens.outX
        val oy = sens.outY
        if (ox == 0 && oy == 0) return
        val sz = screen()
        val cx = s.lookX * sz.x
        val cy = s.lookY * sz.y
        val r = s.lookR * min(sz.x, sz.y)
        if (!lookActive) {
            lx = cx
            ly = cy
            touch.down(LOOK, lx, ly)
            lookActive = true
        }
        lx += ox
        ly += oy
        val inside = hypot(lx - cx, ly - cy) <= r &&
            lx >= 0f && ly >= 0f && lx <= sz.x && ly <= sz.y
        if (inside) {
            touch.move(LOOK, lx, ly)
        } else {
            // chegou na borda da área: finaliza o arrasto e recomeça do centro
            touch.move(LOOK, lx.coerceIn(0f, sz.x - 1f), ly.coerceIn(0f, sz.y - 1f))
            touch.up(LOOK)
            lx = cx
            ly = cy
            touch.down(LOOK, lx, ly)
        }
        touch.handler.removeCallbacks(releaseLook)
        touch.handler.postDelayed(releaseLook, s.releaseDelayMs.toLong())
    }

    private fun endLook() {
        if (lookActive) {
            touch.up(LOOK)
            lookActive = false
            sens.reset()
        }
    }
}
