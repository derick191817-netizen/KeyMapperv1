package com.example.keymapper.data

import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

object Kind {
    const val TAP = 0      // segura enquanto a tecla está pressionada
    const val TOGGLE = 1   // aperta uma vez e mantém (aderência)
    const val MACRO = 2    // aciona vários botões de uma vez
}

/** Teclas do mouse usam códigos negativos: M1 = -1, M2 = -2, M3 = -3. */
fun keyLabel(code: Int): String = when (code) {
    -1 -> "M1"
    -2 -> "M2"
    -3 -> "M3"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
}

data class HudButton(
    val id: Int,
    val key: Int,
    val x: Float,              // posição em fração da tela (0..1)
    val y: Float,
    val size: Float = 0.07f,   // diâmetro em fração do menor lado da tela
    val opacity: Float = 1f,
    val kind: Int = Kind.TAP,
    val macro: List<Int> = emptyList()
)

fun defaultButtons(): List<HudButton> = listOf(
    HudButton(0, -1, 0.88f, 0.62f, 0.10f),
    HudButton(1, KeyEvent.KEYCODE_SPACE, 0.92f, 0.80f),
    HudButton(2, KeyEvent.KEYCODE_CTRL_LEFT, 0.80f, 0.86f, kind = Kind.TOGGLE),
    HudButton(3, KeyEvent.KEYCODE_R, 0.76f, 0.72f),
    HudButton(4, KeyEvent.KEYCODE_V, 0.96f, 0.50f, kind = Kind.MACRO, macro = listOf(1, 0))
)

data class AppSettings(
    val sensX: Float = 1f,
    val sensY: Float = 1f,
    val curve: Int = 0,            // 0 linear, 1 exponencial, 2 customizada
    val exponent: Float = 1.5f,
    val knee: Float = 8f,
    val gain: Float = 1.5f,
    val releaseDelayMs: Int = 0,   // 0..100 ms
    val joyX: Float = 0.14f,
    val joyY: Float = 0.72f,
    val joyR: Float = 0.10f,
    val lookX: Float = 0.70f,
    val lookY: Float = 0.50f,
    val lookR: Float = 0.20f,
    val autoStart: Boolean = false,
    val invertY: Boolean = false,
    val deadzone: Float = 0f,          // ignora ruído do sensor (contagens do mouse)
    val calibTarget: Float = 0.5f,     // fração da tela que a calibração usa como alvo
    val refreshHz: Int = 0,            // 0 = automático (máximo suportado)
    val perfMode: Int = 0,             // 0 equilibrado, 1 alta performance, 2 economia
    val showHud: Boolean = true,
    val hudAlpha: Float = 1f,
    val editKey: Int = KeyEvent.KEYCODE_F1,
    val pauseKey: Int = KeyEvent.KEYCODE_F2,
    val buttons: List<HudButton> = defaultButtons()
) {
    fun toJson(): String = JSONObject().apply {
        put("sensX", sensX.toDouble()); put("sensY", sensY.toDouble())
        put("curve", curve); put("exponent", exponent.toDouble())
        put("knee", knee.toDouble()); put("gain", gain.toDouble())
        put("releaseDelayMs", releaseDelayMs)
        put("joyX", joyX.toDouble()); put("joyY", joyY.toDouble()); put("joyR", joyR.toDouble())
        put("lookX", lookX.toDouble()); put("lookY", lookY.toDouble()); put("lookR", lookR.toDouble())
        put("autoStart", autoStart)
        put("invertY", invertY); put("deadzone", deadzone.toDouble())
        put("calibTarget", calibTarget.toDouble()); put("refreshHz", refreshHz)
        put("perfMode", perfMode); put("showHud", showHud)
        put("hudAlpha", hudAlpha.toDouble())
        put("editKey", editKey); put("pauseKey", pauseKey)
        put("buttons", JSONArray().apply {
            buttons.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id); put("key", b.key)
                    put("x", b.x.toDouble()); put("y", b.y.toDouble())
                    put("size", b.size.toDouble()); put("opacity", b.opacity.toDouble())
                    put("kind", b.kind); put("macro", JSONArray(b.macro))
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(text: String): AppSettings = try {
            val o = JSONObject(text)
            val d = AppSettings()
            fun f(k: String, def: Float) = o.optDouble(k, def.toDouble()).toFloat()
            AppSettings(
                sensX = f("sensX", d.sensX), sensY = f("sensY", d.sensY),
                curve = o.optInt("curve", d.curve), exponent = f("exponent", d.exponent),
                knee = f("knee", d.knee), gain = f("gain", d.gain),
                releaseDelayMs = o.optInt("releaseDelayMs", d.releaseDelayMs),
                joyX = f("joyX", d.joyX), joyY = f("joyY", d.joyY), joyR = f("joyR", d.joyR),
                lookX = f("lookX", d.lookX), lookY = f("lookY", d.lookY), lookR = f("lookR", d.lookR),
                autoStart = o.optBoolean("autoStart", false),
                invertY = o.optBoolean("invertY", false), deadzone = f("deadzone", d.deadzone),
                calibTarget = f("calibTarget", d.calibTarget), refreshHz = o.optInt("refreshHz", 0),
                perfMode = o.optInt("perfMode", 0), showHud = o.optBoolean("showHud", true),
                hudAlpha = f("hudAlpha", d.hudAlpha),
                editKey = o.optInt("editKey", d.editKey), pauseKey = o.optInt("pauseKey", d.pauseKey),
                buttons = o.optJSONArray("buttons")?.let { a ->
                    (0 until a.length()).map { i ->
                        val b = a.getJSONObject(i)
                        HudButton(
                            id = b.getInt("id"), key = b.getInt("key"),
                            x = b.getDouble("x").toFloat(), y = b.getDouble("y").toFloat(),
                            size = b.optDouble("size", 0.07).toFloat(),
                            opacity = b.optDouble("opacity", 1.0).toFloat(),
                            kind = b.optInt("kind", Kind.TAP),
                            macro = b.optJSONArray("macro")?.let { m ->
                                (0 until m.length()).map { m.getInt(it) }
                            } ?: emptyList()
                        )
                    }
                } ?: defaultButtons()
            )
        } catch (e: Exception) {
            AppSettings()
        }
    }
}
