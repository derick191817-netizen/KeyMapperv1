package com.example.keymapper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.keyLabel
import kotlin.math.hypot
import kotlin.math.min

/** HUD translúcido: desenha os botões fixados e, no modo edição, deixa arrastar/editar. */
class HudView(ctx: Context, private val host: Host) : View(ctx) {

    interface Host {
        fun settings(): AppSettings
        fun update(s: AppSettings)
        fun persist()
        fun setEditing(on: Boolean)
        fun arm()
        fun closeAll()
    }

    var editing = false
    var armed = false

    private var selected = NONE
    private var dragging = false
    private var ga = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hits = ArrayList<Pair<RectF, () -> Unit>>()
    private val dp = resources.displayMetrics.density

    private companion object {
        const val NONE = -1000
        const val JOY = -10
        const val LOOK = -11
    }

    // ---------- desenho ----------

    override fun onDraw(c: Canvas) {
        hits.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        val m = min(w, h)
        val s = host.settings()
        ga = s.hudAlpha
        if (!editing && !s.showHud) return
        val selBtn = s.buttons.firstOrNull { it.id == selected }
        val macroSel = if (selBtn != null && selBtn.kind == Kind.MACRO) selBtn else null

        drawArea(c, s.lookX * w, s.lookY * h, s.lookR * m, "OLHAR", selected == LOOK)
        drawArea(c, s.joyX * w, s.joyY * h, s.joyR * m, "WASD", selected == JOY)

        for (b in s.buttons) {
            val cx = b.x * w
            val cy = b.y * h
            val r = b.size * m / 2
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((204 * b.opacity * ga).toInt().coerceIn(15, 255), 0, 0, 0)
            c.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * dp
            paint.color = when {
                editing && selected == b.id -> Color.YELLOW
                macroSel != null && macroSel.macro.contains(b.id) -> Color.GREEN
                else -> Color.argb((170 * ga).toInt(), 255, 255, 255)
            }
            c.drawCircle(cx, cy, r, paint)
            val label = keyLabel(b.key) + when (b.kind) {
                Kind.TOGGLE -> "•"
                Kind.MACRO -> "+"
                else -> ""
            }
            text(c, label, cx, cy, r * 0.55f, Color.WHITE)
        }

        drawToolbar(c)
        if (editing) drawPanel(c, w, h, macroSel != null)
    }

    private fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        c.drawText(t, x, y - (paint.descent() + paint.ascent()) / 2, paint)
    }

    private fun drawArea(c: Canvas, cx: Float, cy: Float, r: Float, label: String, sel: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(if (editing) 50 else 22, 0, 0, 0)
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * dp
        paint.color = if (editing && sel) Color.YELLOW else Color.argb(90, 255, 255, 255)
        c.drawCircle(cx, cy, r, paint)
        text(c, label, cx, cy, 14 * dp, Color.argb(140, 255, 255, 255))
    }

    private fun chip(c: Canvas, label: String, cx: Float, cy: Float, action: () -> Unit) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(204, 0, 0, 0)
        c.drawCircle(cx, cy, 16 * dp, paint)
        text(c, label, cx, cy, 15 * dp, Color.WHITE)
        hits.add(RectF(cx - 18 * dp, cy - 18 * dp, cx + 18 * dp, cy + 18 * dp) to action)
    }

    private fun drawToolbar(c: Canvas) {
        val cx = 70 * dp
        val cy = 74 * dp
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(204, 0, 0, 0)
        c.drawCircle(cx, cy, 56 * dp, paint)
        text(c, "W", cx, cy - 28 * dp, 16 * dp, Color.WHITE)
        text(c, "A", cx - 28 * dp, cy, 16 * dp, Color.WHITE)
        text(c, "S", cx, cy + 28 * dp, 16 * dp, Color.WHITE)
        text(c, "D", cx + 28 * dp, cy, 16 * dp, Color.WHITE)
        chip(c, "X", 150 * dp, 30 * dp) { host.closeAll() }
        chip(c, "⚙", 190 * dp, 30 * dp) { host.setEditing(!editing) }
        chip(c, "+", 230 * dp, 30 * dp) {
            if (editing) {
                armed = true
                host.arm()
            }
        }
        if (editing && armed) {
            text(c, "Pressione uma tecla ou escolha:", 330 * dp, 30 * dp, 13 * dp, Color.WHITE)
            chip(c, "M1", 150 * dp, 74 * dp) { addButton(-1) }
            chip(c, "M2", 190 * dp, 74 * dp) { addButton(-2) }
        }
    }

    private fun drawPanel(c: Canvas, w: Float, h: Float, macroMode: Boolean) {
        if (selected == NONE) return
        val items = ArrayList<Pair<String, () -> Unit>>()
        items.add("Tam -" to { sizeDelta(-0.01f) })
        items.add("Tam +" to { sizeDelta(0.01f) })
        if (selected >= 0) {
            items.add("Op -" to { opDelta(-0.1f) })
            items.add("Op +" to { opDelta(0.1f) })
            items.add("Tipo" to { cycleKind() })
            items.add("Apagar" to { delete() })
        }
        val bw = 62 * dp
        val bh = 40 * dp
        val gap = 8 * dp
        val total = items.size * bw + (items.size - 1) * gap
        var x = (w - total) / 2
        val y = h - bh - 24 * dp
        for ((label, act) in items) {
            val r = RectF(x, y, x + bw, y + bh)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(220, 30, 30, 30)
            c.drawRoundRect(r, 10 * dp, 10 * dp, paint)
            text(c, label, r.centerX(), r.centerY(), 14 * dp, Color.WHITE)
            hits.add(r to act)
            x += bw + gap
        }
        if (macroMode) {
            text(c, "Macro: toque nos botões que ela aciona (verde)", w / 2, y - 18 * dp, 14 * dp, Color.GREEN)
        }
    }

    // ---------- edição ----------

    private fun mutate(f: (AppSettings) -> AppSettings) {
        host.update(f(host.settings()))
        invalidate()
    }

    fun addButton(key: Int) {
        val id = (host.settings().buttons.maxOfOrNull { it.id } ?: -1) + 1
        mutate { it.copy(buttons = it.buttons + HudButton(id, key, 0.5f, 0.5f)) }
        selected = id
        armed = false
        host.persist()
    }

    private fun sizeDelta(d: Float) {
        mutate { s ->
            when (selected) {
                JOY -> s.copy(joyR = (s.joyR + d).coerceIn(0.03f, 0.3f))
                LOOK -> s.copy(lookR = (s.lookR + d * 2).coerceIn(0.05f, 0.5f))
                else -> s.copy(buttons = s.buttons.map {
                    if (it.id == selected) it.copy(size = (it.size + d).coerceIn(0.03f, 0.3f)) else it
                })
            }
        }
        host.persist()
    }

    private fun opDelta(d: Float) {
        mutate { s ->
            s.copy(buttons = s.buttons.map {
                if (it.id == selected) it.copy(opacity = (it.opacity + d).coerceIn(0.1f, 1f)) else it
            })
        }
        host.persist()
    }

    private fun cycleKind() {
        mutate { s ->
            s.copy(buttons = s.buttons.map {
                if (it.id == selected) it.copy(kind = (it.kind + 1) % 3) else it
            })
        }
        host.persist()
    }

    private fun delete() {
        val id = selected
        mutate { s ->
            s.copy(buttons = s.buttons.filter { it.id != id }
                .map { it.copy(macro = it.macro.filter { m -> m != id }) })
        }
        selected = NONE
        host.persist()
    }

    private fun toggleMacro(macroId: Int, target: Int) {
        mutate { s ->
            s.copy(buttons = s.buttons.map {
                if (it.id == macroId) {
                    it.copy(macro = if (it.macro.contains(target)) it.macro - target else it.macro + target)
                } else it
            })
        }
        host.persist()
    }

    private fun pick(x: Float, y: Float): Int {
        val w = width.toFloat()
        val h = height.toFloat()
        val m = min(w, h)
        val s = host.settings()
        for (b in s.buttons.asReversed()) {
            if (hypot(x - b.x * w, y - b.y * h) <= b.size * m / 2) return b.id
        }
        if (hypot(x - s.joyX * w, y - s.joyY * h) <= s.joyR * m) return JOY
        if (hypot(x - s.lookX * w, y - s.lookY * h) <= s.lookR * m) return LOOK
        return NONE
    }

    private fun moveSelected(fx: Float, fy: Float) {
        val x = fx.coerceIn(0f, 1f)
        val y = fy.coerceIn(0f, 1f)
        mutate { s ->
            when (selected) {
                JOY -> s.copy(joyX = x, joyY = y)
                LOOK -> s.copy(lookX = x, lookY = y)
                else -> s.copy(buttons = s.buttons.map {
                    if (it.id == selected) it.copy(x = x, y = y) else it
                })
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editing) return false
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hits.firstOrNull { it.first.contains(x, y) }
                if (hit != null) {
                    hit.second.invoke()
                    invalidate()
                    return true
                }
                val picked = pick(x, y)
                val cur = host.settings().buttons.firstOrNull { it.id == selected }
                if (cur != null && cur.kind == Kind.MACRO && picked >= 0 && picked != cur.id) {
                    toggleMacro(cur.id, picked)
                    return true
                }
                selected = picked
                dragging = picked != NONE
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) moveSelected(x / width, y / height)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                host.persist()
            }
        }
        return true
    }
}
