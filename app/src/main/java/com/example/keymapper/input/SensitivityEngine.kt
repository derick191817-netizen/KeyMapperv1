package com.example.keymapper.input

import com.example.keymapper.data.AppSettings
import kotlin.math.hypot
import kotlin.math.pow

/** Curva + eixos X/Y independentes + acúmulo do resto fracionário (sem pular coordenadas). */
class SensitivityEngine {
    private var remX = 0f
    private var remY = 0f
    var outX = 0
        private set
    var outY = 0
        private set

    fun process(dx: Float, dy: Float, s: AppSettings) {
        val speed = hypot(dx, dy)
        val k = if (speed == 0f) 0f else gain(s, speed) / speed
        val tx = dx * k * s.sensX + remX
        val ty = dy * k * s.sensY + remY
        outX = tx.toInt()
        outY = ty.toInt()
        remX = tx - outX
        remY = ty - outY
    }

    fun reset() {
        remX = 0f; remY = 0f; outX = 0; outY = 0
    }

    private fun gain(s: AppSettings, v: Float): Float = when (s.curve) {
        1 -> v.pow(s.exponent) / REF.pow(s.exponent - 1f)
        2 -> if (v <= s.knee) v else s.knee + (v - s.knee) * s.gain
        else -> v
    }

    private companion object {
        const val REF = 10f
    }
}
