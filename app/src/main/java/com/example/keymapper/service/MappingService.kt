package com.example.keymapper.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.SettingsRepository
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.injection.ShizukuInjector
import com.example.keymapper.injection.TouchInjector
import com.example.keymapper.mapping.MappingEngine
import com.example.keymapper.overlay.HudView
import com.example.keymapper.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Motor em foreground: START_STICKY + PARTIAL_WAKE_LOCK. Se o sistema
 * encerrar o serviço, ele reinicia e o overlay é restaurado no onCreate.
 */
class MappingService : Service(), HudView.Host {

    companion object {
        const val ACTION_STOP = "com.example.keymapper.STOP"
        const val ACTION_EDIT = "com.example.keymapper.EDIT"

        @Volatile
        var running = false
        private const val CHANNEL = "mapping"
        private const val NOTIF_ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repo: SettingsRepository
    private lateinit var displays: DisplayHelper
    private var wake: PowerManager.WakeLock? = null

    @Volatile
    private var cfg = AppSettings()

    @Volatile
    private var screen = Point(1920, 1080)
    private var touch: TouchInjector? = null
    private var engine: MappingEngine? = null
    private var overlay: OverlayManager? = null
    private var editing = false
    private var paused = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { scope.launch { rebuild() } }
        override fun onDisplayRemoved(displayId: Int) { scope.launch { rebuild() } }
        override fun onDisplayChanged(displayId: Int) { scope.launch { rebuild() } }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        repo = SettingsRepository(this)
        displays = DisplayHelper(this)
        createChannel()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "keymapper:mapping").apply { acquire() }

        scope.launch {
            repo.flow.collect { s ->
                val hzChanged = touch != null && s.refreshHz != cfg.refreshHz
                cfg = s
                if (touch == null) setup() else if (hzChanged) rebuild() else overlay?.refresh()
            }
        }
    }

    private fun setup() {
        val injector = ShizukuInjector()
        // Aparelhos com menos de 4 GB de RAM: pequena folga entre eventos para evitar ANR
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val low = mi.totalMem < 4L * 1024 * 1024 * 1024
        val extra = when (cfg.perfMode) {
            1 -> 0L            // alta performance: sem folga
            2 -> 5L            // economia
            else -> if (low) 5L else 0L
        }

        val t = TouchInjector(injector, { overlay?.display?.displayId ?: 0 }, extra)
        touch = t
        val e = MappingEngine(t, { screen }, { cfg })
        engine = e
        overlay = OverlayManager(
            this, displays, this,
            refreshHz = { cfg.refreshHz },
            onDelta = { dx, dy -> if (!editing) e.onDelta(dx, dy) },
            onButton = { bit, down -> if (!editing) e.onMouseButton(bit, down) },
            onKey = { code, down -> handleKey(code, down) }
        )
        displays.register(displayListener, null)
        rebuild()
    }

    private fun rebuild() {
        val ov = overlay ?: return
        screen = displays.size(displays.target())
        editing = false
        paused = false
        engine?.paused = false
        ov.show() // restaura o overlay
    }

    private fun handleKey(code: Int, down: Boolean) {
        if (down && code == cfg.editKey) {
            setEditing(!editing)
            return
        }
        if (down && code == cfg.pauseKey && !editing) {
            paused = !paused
            engine?.paused = paused
            engine?.releaseAll()
            overlay?.setCapture(true) // mantém foco para receber F2 de novo
            return
        }
        if (editing) {
            if (down) overlay?.hud?.let { if (it.armed) it.addButton(code) }
            return
        }
        engine?.onKey(code, down)
    }

    // ---- HudView.Host ----

    override fun settings(): AppSettings = cfg
    override fun update(s: AppSettings) { cfg = s }
    override fun persist() { scope.launch { repo.save(cfg) } }
    override fun arm() {}
    override fun closeAll() { stopSelf() }

    override fun setEditing(on: Boolean) {
        editing = on
        engine?.releaseAll()
        overlay?.setEditing(on)
    }

    // ---- ciclo de vida ----

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_EDIT -> setEditing(!editing)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        try { displays.unregister(displayListener) } catch (e: Exception) {}
        overlay?.hide()
        touch?.shutdown()
        wake?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Mapeamento", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("KeyMapper ativo")
            .setContentText("Atalhos de editar HUD e pausar configuráveis no app")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
}
