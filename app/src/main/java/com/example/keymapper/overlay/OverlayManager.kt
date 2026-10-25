package com.example.keymapper.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Display
import android.view.WindowManager
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.input.CaptureView

/**
 * Duas janelas SYSTEM_ALERT_WINDOW no monitor alvo (celular ou DeX):
 *  - HUD: desenha botões, sem receber toque no modo jogo
 *  - Captura: focável, recebe teclado e mouse bruto; não bloqueia toques injetados
 */
class OverlayManager(
    private val ctx: Context,
    private val displays: DisplayHelper,
    private val host: HudView.Host,
    private val refreshHz: () -> Int,
    private val onDelta: (Float, Float) -> Unit,
    private val onButton: (Int, Boolean) -> Unit,
    private val onKey: (Int, Boolean) -> Unit
) {
    private var wm: WindowManager? = null

    var capture: CaptureView? = null
        private set
    var hud: HudView? = null
        private set

    @Volatile
    var display: Display? = null
        private set

    private val base = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    private val hudFlags = base or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    private val hudEditFlags = base or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    private val captureFlags = base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    fun show() {
        hide()
        if (!android.provider.Settings.canDrawOverlays(ctx)) return
        val d = displays.target()
        display = d
        val wctx = displays.windowContext(d)
        val manager = wctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager
        val mode = displays.pickMode(d, refreshHz())

        fun params(flags: Int): WindowManager.LayoutParams {
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            )
            lp.alpha = 0.8f // limite para o toque atravessar a janela no Android 12+
            if (mode != null) {
                lp.preferredDisplayModeId = mode.modeId
                lp.preferredRefreshRate = mode.refreshRate
            }
            return lp
        }

        val h = HudView(wctx, host)
        val c = CaptureView(wctx, onDelta, onButton, onKey)
        manager.addView(h, params(hudFlags))
        manager.addView(c, params(captureFlags))
        hud = h
        capture = c
        c.start()
    }

    fun setEditing(on: Boolean) {
        val h = hud ?: return
        val lp = h.layoutParams as? WindowManager.LayoutParams ?: return
        lp.flags = if (on) hudEditFlags else hudFlags
        wm?.updateViewLayout(h, lp)
        h.editing = on
        if (!on) h.armed = false
        h.postInvalidate()
        if (on) capture?.stop() else capture?.start()
    }

    fun setCapture(on: Boolean) {
        if (on) capture?.start() else capture?.stop()
    }

    fun refresh() {
        hud?.postInvalidate()
    }

    fun hide() {
        capture?.stop()
        try {
            hud?.let { wm?.removeView(it) }
            capture?.let { wm?.removeView(it) }
        } catch (e: Exception) {
        }
        hud = null
        capture = null
        wm = null
    }
}
