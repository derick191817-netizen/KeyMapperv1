@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.keymapper.ui

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.SettingsRepository
import com.example.keymapper.data.keyLabel
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.injection.Activation
import com.example.keymapper.injection.ActivationState
import com.example.keymapper.input.CalibrationAssistant
import com.example.keymapper.input.CaptureView
import com.example.keymapper.service.MappingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private typealias Edit = ((AppSettings) -> AppSettings) -> Unit

private val TABS = listOf("Início", "Mira", "Desempenho", "HUD", "Teclas", "Sistema")

private data class Hw(val name: String, val hz: Int, val ramGb: Int, val res: String, val rates: List<Int>)

private fun readHw(ctx: Context): Hw {
    val dh = DisplayHelper(ctx)
    val disp = dh.target()
    val size = dh.size(disp)
    val rates = dh.rates(disp)
    val mi = ActivityManager.MemoryInfo()
    (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
    val gb = (mi.totalMem / (1024.0 * 1024.0 * 1024.0)).roundToInt()
    val name = Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL
    return Hw(name, rates.maxOrNull() ?: disp.refreshRate.roundToInt(), gb, "${size.x}x${size.y}", rates)
}

@Composable
fun SettingsScreen(repo: SettingsRepository, tick: MutableIntState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val stored by repo.flow.collectAsState(initial = AppSettings())
    var draft by remember { mutableStateOf<AppSettings?>(null) }
    var layoutReset by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }

    val hw = remember(tick.intValue) { readHw(ctx) }
    val state = remember(tick.intValue) { Activation.state(ctx) }
    val running = remember(tick.intValue) { MappingService.running }
    val overlayOk = remember(tick.intValue) { Settings.canDrawOverlays(ctx) }

    val d = draft ?: stored
    val conflict = d.editKey == d.pauseKey
    val dirty = draft != null && draft != stored
    val edit: Edit = { f -> draft = f(draft ?: stored) }

    fun save() {
        val prev = stored
        val cur = draft ?: return
        // posições do HUD são editadas pelo overlay; preserva o que está salvo
        val out = if (layoutReset) cur else cur.copy(
            buttons = prev.buttons, joyX = prev.joyX, joyY = prev.joyY,
            lookX = prev.lookX, lookY = prev.lookY
        )
        scope.launch {
            repo.save(out)
            draft = null
            layoutReset = false
            val r = snack.showSnackbar("Configurações salvas", "Desfazer", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) repo.save(prev)
        }
    }

    fun discard() {
        draft = null
        layoutReset = false
    }

    fun restoreDefaults() {
        val prevDraft = draft
        val prevReset = layoutReset
        draft = AppSettings()
        layoutReset = true
        scope.launch {
            val r = snack.showSnackbar("Padrões carregados. Toque em Salvar para aplicar.", "Desfazer")
            if (r == SnackbarResult.ActionPerformed) {
                draft = prevDraft
                layoutReset = prevReset
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column {
                Header(hw, state, running, dirty)
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    TABS.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { restoreDefaults() }, modifier = Modifier.weight(1.3f)) {
                        Text("Restaurar padrões")
                    }
                    OutlinedButton(onClick = { discard() }, enabled = dirty, modifier = Modifier.weight(1f)) {
                        Text("Descartar")
                    }
                    Button(onClick = { save() }, enabled = dirty && !conflict, modifier = Modifier.weight(1f)) {
                        Text("Salvar")
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> StartTab(ctx, state, overlayOk, running)
                1 -> AimTab(d, edit, hw)
                2 -> PerfTab(d, edit, hw)
                3 -> HudTab(d, edit)
                4 -> KeysTab(d, edit, conflict)
                else -> SystemTab(ctx, d, edit, hw, state, overlayOk, running) { confirmReset = true }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset total") },
            text = { Text("Apaga todas as configurações e o layout do HUD e volta ao padrão de fábrica. Isso é aplicado na hora.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmReset = false
                        scope.launch {
                            repo.save(AppSettings())
                            draft = null
                            layoutReset = false
                            snack.showSnackbar("Reset total concluído")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Resetar tudo") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } }
        )
    }
}

// ---------------------------------------------------------------- cabeçalho

@Composable
private fun Header(hw: Hw, state: ActivationState, running: Boolean, dirty: Boolean) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                "${hw.name} | ${hw.hz}Hz | ${hw.ramGb}GB RAM",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Tela ${hw.res} • Android ${Build.VERSION.RELEASE} • Shizuku: ${stateLabel(state)} • Serviço: ${if (running) "ligado" else "desligado"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (dirty) {
                Text(
                    "Alterações não salvas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

private fun stateLabel(s: ActivationState) = when (s) {
    ActivationState.READY -> "ativo"
    ActivationState.NEEDS_PERMISSION -> "sem permissão"
    ActivationState.SHIZUKU_STOPPED -> "parado"
    ActivationState.SHIZUKU_MISSING -> "não instalado"
}

// ---------------------------------------------------------------- abas

@Composable
private fun StartTab(ctx: Context, state: ActivationState, overlayOk: Boolean, running: Boolean) {
    Section("Ativação")
    Text(
        Activation.guide(state, Build.VERSION.SDK_INT),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium
    )
    ActionRow(
        "Sobreposição de tela", if (overlayOk) "Permitida" else "Necessária para o HUD", "Permitir"
    ) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        )
    }
    ActionRow("Permissão Shizuku", "Estado: ${stateLabel(state)}", "Solicitar") { Activation.requestPermission() }
    ActionRow("Abrir Shizuku", "Para iniciar o serviço (depuração sem fio)", "Abrir") {
        val i = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (i != null) ctx.startActivity(i)
        else Toast.makeText(ctx, "Shizuku não instalado", Toast.LENGTH_SHORT).show()
    }
    ActionRow("Ativar via root", "Inicia o Shizuku com su (opcional)", "Ativar") {
        Thread {
            val ok = Activation.startShizukuViaRoot()
            android.os.Handler(ctx.mainLooper).post {
                Toast.makeText(ctx, if (ok) "Shizuku iniciado via root" else "Root indisponível", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    Section("Controle")
    Row(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MappingService::class.java))
        }, modifier = Modifier.weight(1f), enabled = !running) { Text("Iniciar") }
        OutlinedButton(onClick = {
            ctx.startService(Intent(ctx, MappingService::class.java).setAction(MappingService.ACTION_STOP))
        }, modifier = Modifier.weight(1f), enabled = running) { Text("Parar") }
        OutlinedButton(onClick = {
            ctx.startService(Intent(ctx, MappingService::class.java).setAction(MappingService.ACTION_EDIT))
        }, modifier = Modifier.weight(1f), enabled = running) { Text("Editar HUD") }
    }
    Button(
        onClick = {
            val pm = ctx.packageManager
            val i = listOf("com.dts.freefireth", "com.dts.freefiremax")
                .firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            if (i == null) {
                Toast.makeText(ctx, "Free Fire não encontrado", Toast.LENGTH_SHORT).show()
            } else {
                val id = DisplayHelper(ctx).target().displayId
                val opts = ActivityOptions.makeBasic().setLaunchDisplayId(id)
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts.toBundle())
            }
        },
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) { Text("Iniciar Free Fire") }
}

@Composable
private fun AimTab(d: AppSettings, edit: Edit, hw: Hw) {
    Section("Presets de sensibilidade")
    Row(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { edit { it.copy(sensX = 1f, sensY = 1f, curve = 0, releaseDelayMs = 0) } },
            modifier = Modifier.weight(1f)
        ) { Text("Padrão") }
        OutlinedButton(
            onClick = { edit { it.copy(sensX = 1.6f, sensY = 1.6f, curve = 1, exponent = 1.4f) } },
            modifier = Modifier.weight(1f)
        ) { Text("Mira rápida") }
        OutlinedButton(
            onClick = { edit { it.copy(sensX = 0.7f, sensY = 0.7f, curve = 0) } },
            modifier = Modifier.weight(1f)
        ) { Text("Preciso") }
    }

    Section("Sensibilidade")
    SliderRow("Eixo X", "Velocidade horizontal da câmera", d.sensX, 0.1f, 5f, 0.05f) { v -> edit { it.copy(sensX = v) } }
    SliderRow("Eixo Y", "Velocidade vertical da câmera", d.sensY, 0.1f, 5f, 0.05f) { v -> edit { it.copy(sensY = v) } }
    DropdownRow(
        "Curva de aceleração", "Como a velocidade do mouse vira velocidade da mira",
        listOf(0 to "Linear", 1 to "Exponencial", 2 to "Customizada"), d.curve
    ) { v -> edit { it.copy(curve = v) } }
    if (d.curve == 1) {
        SliderRow("Expoente", "Maior = mais aceleração em movimentos rápidos", d.exponent, 1f, 3f, 0.1f) { v ->
            edit { it.copy(exponent = v) }
        }
    }
    if (d.curve == 2) {
        SliderRow("Joelho", "Velocidade a partir da qual o ganho muda", d.knee, 1f, 30f, 1f, "%.0f") { v ->
            edit { it.copy(knee = v) }
        }
        SliderRow("Ganho acima do joelho", "Multiplicador depois do joelho", d.gain, 0.5f, 3f, 0.1f) { v ->
            edit { it.copy(gain = v) }
        }
    }
    SwitchRow("Inverter eixo Y", "Mouse para cima = mira para baixo", d.invertY) { v -> edit { it.copy(invertY = v) } }
    SliderRow("Zona morta", "Ignora ruído do sensor (contagens do mouse)", d.deadzone, 0f, 5f, 0.1f, "%.1f") { v ->
        edit { it.copy(deadzone = v) }
    }
    SliderRow(
        "Soltar toque quando o mouse parar", "0 a 100 ms (padrão 0)", d.releaseDelayMs.toFloat(), 0f, 100f, 1f, "%.0f ms"
    ) { v -> edit { it.copy(releaseDelayMs = v.toInt()) } }

    Section("Calibração")
    SliderRow(
        "Distância alvo", "Quanto da tela um movimento de ida deve percorrer",
        d.calibTarget, 0.2f, 1f, 0.05f
    ) { v -> edit { it.copy(calibTarget = v) } }
    Calibrator(d.calibTarget,
        onX = { v -> edit { it.copy(sensX = v.coerceIn(0.1f, 5f)) } },
        onY = { v -> edit { it.copy(sensY = v.coerceIn(0.1f, 5f)) } }
    )
}

@Composable
private fun Calibrator(target: Float, onX: (Float) -> Unit, onY: (Float) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val assistant = remember { CalibrationAssistant() }
    var view by remember { mutableStateOf<CaptureView?>(null) }
    var msg by remember { mutableStateOf("") }

    Text(
        "Toque em um botão e, durante 4 s, faça UM movimento de ida com o mouse na distância que deve " +
            "equivaler ao alvo. O app calcula a sensibilidade e coloca no rascunho (salve para aplicar).",
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall
    )
    AndroidView(
        factory = { c ->
            CaptureView(c, { dx, dy -> assistant.feed(dx, dy) }, { _, _ -> }, { _, _ -> }).also { view = it }
        },
        modifier = Modifier.size(1.dp)
    )

    fun run(horizontal: Boolean) {
        val v = view ?: return
        val dh = DisplayHelper(ctx)
        val size = dh.size(dh.target())
        assistant.reset()
        msg = "Mova o mouse agora..."
        v.start()
        scope.launch {
            delay(4000)
            v.stop()
            val px = (if (horizontal) size.x else size.y) * target
            val s = if (horizontal) assistant.suggestX(px) else assistant.suggestY(px)
            if (s == null) {
                msg = "Nenhum movimento detectado. Tente de novo."
            } else {
                msg = "Sensibilidade sugerida: ${String.format("%.2f", s)}"
                if (horizontal) onX(s) else onY(s)
            }
        }
    }

    Row(
        Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = { run(true) }, modifier = Modifier.weight(1f)) { Text("Calibrar X") }
        Button(onClick = { run(false) }, modifier = Modifier.weight(1f)) { Text("Calibrar Y") }
    }
    if (msg.isNotEmpty()) Text(msg, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun PerfTab(d: AppSettings, edit: Edit, hw: Hw) {
    val low = hw.ramGb < 4
    val delay = when (d.perfMode) {
        1 -> 0
        2 -> 5
        else -> if (low) 5 else 0
    }
    Section("Taxa de atualização")
    DropdownRow(
        "Taxa do overlay", "Pede ao sistema esta taxa para a janela do HUD. O sistema pode limitar.",
        listOf(0 to "Automático (máximo)") + hw.rates.map { it to "$it Hz" }, d.refreshHz
    ) { v -> edit { it.copy(refreshHz = v) } }
    InfoRow("Taxas suportadas", hw.rates.joinToString(" / ") { "$it Hz" }.ifEmpty { "—" })

    Section("Modo de desempenho")
    DropdownRow(
        "Modo", "Aplica ao reiniciar o serviço",
        listOf(0 to "Equilibrado", 1 to "Alta performance", 2 to "Economia"), d.perfMode
    ) { v -> edit { it.copy(perfMode = v) } }
    InfoRow("Folga entre eventos", "$delay ms")
    InfoRow("Prioridade da injeção", "URGENT_DISPLAY (máxima)")
    InfoRow("RAM detectada", "${hw.ramGb} GB" + if (low && d.perfMode == 0) " (folga automática de 5 ms)" else "")
    Text(
        "Alta performance remove a folga mesmo em aparelhos fracos; use se não houver travamentos. " +
            "Economia mantém 5 ms de folga sempre.",
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun HudTab(d: AppSettings, edit: Edit) {
    Section("Exibição")
    SwitchRow("Mostrar HUD durante o jogo", "Desligado: nada é desenhado (menos sobreposição)", d.showHud) { v ->
        edit { it.copy(showHud = v) }
    }
    SliderRow("Opacidade geral do HUD", "Multiplica a opacidade de todos os botões", d.hudAlpha, 0.2f, 1f, 0.05f) { v ->
        edit { it.copy(hudAlpha = v) }
    }
    Section("Áreas")
    SliderRow("Raio do joystick (WASD)", "Fração do menor lado da tela", d.joyR, 0.03f, 0.3f, 0.01f) { v ->
        edit { it.copy(joyR = v) }
    }
    SliderRow("Raio da área de câmera", "Ao sair dela, o arrasto recomeça do centro", d.lookR, 0.05f, 0.5f, 0.01f) { v ->
        edit { it.copy(lookR = v) }
    }
    Text(
        "Posição, tamanho, opacidade e tipo de cada botão são editados no próprio HUD: " +
            "use \"Editar HUD\" na aba Início ou a tecla de atalho.",
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun KeysTab(d: AppSettings, edit: Edit, conflict: Boolean) {
    val fKeys = (0..11).map { (KeyEvent.KEYCODE_F1 + it) to "F${it + 1}" }
    Section("Atalhos globais")
    DropdownRow("Editar HUD", "Liga/desliga o modo de edição", fKeys, d.editKey) { v -> edit { it.copy(editKey = v) } }
    DropdownRow("Pausar mapeamento", "Libera o mouse e os toques", fKeys, d.pauseKey) { v -> edit { it.copy(pauseKey = v) } }
    if (conflict) {
        Text(
            "Os dois atalhos não podem ser a mesma tecla.",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
    Section("Mapeamentos atuais")
    InfoRow("Movimento", "W A S D")
    InfoRow("Câmera", "Mouse")
    d.buttons.forEach { b ->
        val kind = when (b.kind) {
            1 -> "aderência"
            2 -> "macro (${b.macro.size} botões)"
            else -> "segurar"
        }
        InfoRow(keyLabel(b.key), kind)
    }
}

@Composable
private fun SystemTab(
    ctx: Context, d: AppSettings, edit: Edit, hw: Hw, state: ActivationState,
    overlayOk: Boolean, running: Boolean, onReset: () -> Unit
) {
    val version = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
    Section("Inicialização")
    SwitchRow("Iniciar ao ligar o celular", "Sobe o serviço depois do boot", d.autoStart) { v ->
        edit { it.copy(autoStart = v) }
    }
    Section("Diagnóstico")
    InfoRow("Versão do app", version)
    InfoRow("Aparelho", hw.name)
    InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    InfoRow("Camada privilegiada", stateLabel(state))
    ActionRow("Relatório de bug", "Compartilha um log com modelo, versão e estado", "Gerar") {
        val text = buildString {
            appendLine("KeyMapper $version")
            appendLine("Aparelho: ${hw.name}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Tela: ${hw.res} @ ${hw.hz} Hz | RAM: ${hw.ramGb} GB")
            appendLine("Shizuku: ${stateLabel(state)} | Overlay: $overlayOk | Serviço: $running")
            appendLine("Configurações: ${d.toJson()}")
        }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Relatório KeyMapper $version")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, "Enviar relatório"))
    }
    Section("Zona de perigo")
    Button(
        onClick = onReset,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) { Text("Reset total") }
}

// ---------------------------------------------------------------- componentes

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
    )
}

private val transparent @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun InfoRow(title: String, value: String) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
        colors = transparent
    )
    HorizontalDivider()
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = transparent
    )
    HorizontalDivider()
}

@Composable
private fun ActionRow(title: String, sub: String, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = { OutlinedButton(onClick = onClick) { Text(label) } },
        colors = transparent
    )
    HorizontalDivider()
}

@Composable
private fun <T> DropdownRow(
    title: String, sub: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = {
            Box {
                TextButton(onClick = { open = true }) {
                    Text(options.firstOrNull { it.first == selected }?.second ?: "?")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    options.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            onSelect(value)
                            open = false
                        })
                    }
                }
            }
        },
        colors = transparent
    )
    HorizontalDivider()
}

@Composable
private fun SliderRow(
    title: String, sub: String, value: Float, min: Float, max: Float, step: Float,
    fmt: String = "%.2f", onChange: (Float) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = { Text(String.format(fmt, value), style = MaterialTheme.typography.titleSmall) },
        colors = transparent
    )
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { onChange((value - step).coerceIn(min, max)) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp)
        ) { Text("-") }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { onChange((Math.round(it / step) * step).coerceIn(min, max)) },
            valueRange = min..max,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        OutlinedButton(
            onClick = { onChange((value + step).coerceIn(min, max)) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp)
        ) { Text("+") }
    }
    HorizontalDivider()
}
