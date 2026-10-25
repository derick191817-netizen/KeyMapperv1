# KeyMapper

Mapeador de teclado e mouse para Android (Kotlin + Jetpack Compose) que converte teclas e
movimento do mouse em toques, com HUD arrastável estilo emulador e suporte a Samsung DeX.

> **Importante:** este projeto **não** inclui anti-detecção (randomização de toque, jitter) nem
> controle de recuo automático. Os recursos de mira são: sensibilidade, calibração e precisão
> sub-pixel. Free Fire é um jogo de terceiros: verifique os termos de uso do jogo antes de usar.

## Recursos

- **Serviço em foreground** (`START_STICKY` + `PARTIAL_WAKE_LOCK`); reinicia e restaura o overlay.
- **Injeção de toque via Shizuku** (`IInputManager.injectInputEvent`), multitoque real.
- **Ativação**: Shizuku por depuração sem fio (Android 11+, pareamento por código de 6 dígitos),
  script de PC para Android 10 ou inferior (`scripts/ativar.bat` / `ativar.sh`) e root (inicia o Shizuku via `su`).
  A tela inicial detecta o estado e mostra o passo a passo (inclui dicas de MIUI/HyperOS).
- **Mouse bruto** via Pointer Capture (sem aceleração do Android, sem cursor).
- **Sensibilidade**: curva linear, exponencial ou customizada (joelho + ganho), eixos X e Y independentes,
  acúmulo do resto fracionário (sem "pixel skipping") e assistente de calibração.
- **HUD**: botões fixados na tela, arrastar, tamanho, opacidade, tipo (segurar / aderência / macro), apagar.
- **Aderência**: modo toggle (agachar, correr, mirar). **Macros**: uma tecla aciona vários botões (ex.: pular + atirar).
- **DeX / monitor externo**: usa `DisplayManager`; coordenadas em fração da tela, então o HUD
  se adapta ao trocar de monitor. Pede o maior refresh rate suportado à janela do overlay.
- **Aparelhos fracos**: com menos de 4 GB de RAM, aplica 5 ms de folga entre eventos.
- Thread de injeção com `THREAD_PRIORITY_URGENT_DISPLAY`; "soltar toque quando o mouse parar" de 0 a 100 ms.

## Tela de configurações (estilo emulador)

Cabeçalho com o hardware detectado (modelo, Hz, RAM, tela) e abas: **Início**, **Mira**, **Desempenho**,
**HUD**, **Teclas** e **Sistema**. Cada opção é uma linha com título, subtítulo e controle à direita.
As mudanças vão para um rascunho: **Salvar**, **Descartar** ou **Desfazer** (Snackbar) e **Restaurar padrões**
em todas as abas; o **Reset total** fica na aba Sistema, com confirmação.

- **Mira:** presets (Padrão, Mira rápida, Preciso), eixos X/Y, curva, inverter Y, zona morta, soltar toque (0 a 100 ms) e calibração com distância alvo.
- **Desempenho:** taxa de atualização do overlay (lista real do aparelho), modo (equilibrado, alta performance, economia) e folga entre eventos.
- **HUD:** mostrar/ocultar, opacidade geral e raio das áreas de joystick e câmera.
- **Teclas:** atalhos de editar HUD e pausar (F1 a F12) e lista dos mapeamentos.
- **Sistema:** iniciar no boot, diagnóstico, relatório de bug (compartilhar) e reset total.

## Como usar

1. Instale o **Shizuku** e inicie-o (veja o guia na tela inicial do app).
2. Abra o KeyMapper: permita **Overlay** e a **Permissão Shizuku**.
3. Toque em **Iniciar**, depois em **Iniciar Free Fire**.
4. Atalhos padrão: **F1** alterna o modo edição do HUD, **F2** pausa/retoma o mapeamento (configuráveis na aba Teclas).
5. No modo edição: toque em **+** e pressione uma tecla (ou M1/M2) para criar o botão, arraste até o lugar certo.
   As áreas **OLHAR** (câmera) e **WASD** também podem ser movidas e redimensionadas.
   Para uma macro, mude o tipo do botão até aparecer "+" e toque nos botões que ela aciona (ficam verdes).

## Compilar (GitHub Actions)

Suba a pasta para um repositório. O workflow `.github/workflows/build.yml` gera o APK em
**Actions › execução › Artifacts › keymapper-debug**.

## Limitações conhecidas

- Não foi testado em aparelho real; o caminho do Shizuku usa APIs internas do Android e pode variar por versão.
- A janela de captura precisa de foco para o Pointer Capture; o jogo pode reagir à perda de foco. Teste no seu aparelho.
- O toque do mouse como câmera recomeça do centro da área ao chegar na borda dela.
