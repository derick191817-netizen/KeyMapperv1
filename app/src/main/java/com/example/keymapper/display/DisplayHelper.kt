package com.example.keymapper.display

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.view.Display
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/** Detecta o monitor externo (DeX) e as taxas de atualização suportadas. */
class DisplayHelper(private val ctx: Context) {
    private val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun target(): Display =
        dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
            ?: dm.getDisplay(Display.DEFAULT_DISPLAY)

    @Suppress("DEPRECATION")
    fun size(d: Display): Point {
        val p = Point()
        d.getRealSize(p)
        return p
    }

    /** Modos com a mesma resolução da tela atual. */
    fun modes(d: Display): List<Display.Mode> {
        val cur = d.mode
        return d.supportedModes.filter {
            it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight
        }
    }

    fun rates(d: Display): List<Int> =
        modes(d).map { it.refreshRate.roundToInt() }.distinct().sorted()

    fun bestMode(d: Display): Display.Mode? = modes(d).maxByOrNull { it.refreshRate }

    /** hz <= 0: o maior suportado; senão o modo mais próximo de [hz]. */
    fun pickMode(d: Display, hz: Int): Display.Mode? =
        if (hz <= 0) bestMode(d) else modes(d).minByOrNull { abs(it.refreshRate - hz) }

    fun windowContext(d: Display): Context {
        val dc = ctx.createDisplayContext(d)
        return if (Build.VERSION.SDK_INT >= 30)
            dc.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        else dc
    }

    fun register(l: DisplayManager.DisplayListener, h: Handler?) = dm.registerDisplayListener(l, h)
    fun unregister(l: DisplayManager.DisplayListener) = dm.unregisterDisplayListener(l)
}
