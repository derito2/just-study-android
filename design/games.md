# Ficha: Games (Fase 8) — pantalla, escenario y movimiento

Base: `design/shell.md`. Valores en `design/tokens.json`. Pantalla de 411 × 905 dp. Los 13 minijuegos están en `design/games-minigames.md`; la arquitectura y las tareas, en `docs/FASE_8_PLAN.md`.

**No hay PNG de referencia:** todo lo de esta ficha se dibuja con Compose (Canvas y componentes del sistema). Las medidas de aquí mandan.

**Reglas que no cambian:** UI en inglés con su coreano al lado o debajo; sin vibración; sin sonidos de efecto (el único audio es la voz coreana de los juegos de escucha); sin internet; nada de texto de las notas en logs.

## 1 · Idea

Games es una sola pantalla a pantalla completa (sin barra inferior) con dos zonas:

- **El escenario (Stage):** arriba, una escena nocturna con el lenguaje de las pantallas de carga (`splash_flash1`: luna, montañas en capas, pagoda). Siempre es de noche, en tema claro y en oscuro, igual que la pantalla de carga y que *Today's session*.
- **El tablero (Board):** debajo, una hoja `surface` con las esquinas de arriba redondeadas donde se juega. Sigue el tema (claro u oscuro).

La escena no es un adorno: **la luna es el reloj** de los juegos con tiempo, **los faroles son las vidas** de Hangman y la secuencia de Word Recall, y **los pétalos** celebran las rachas. Así cada juego usa el mismo escenario de una forma distinta y la pantalla nunca parece un formulario del Quiz.

```
┌───────────────────────────────────────────┐
│ ⏸   Word Match 짝 맞추기         ✓ 9  🔥 4 │  ← GameTopBar sobre la escena
│   · ·        ·            ◯ luna          │
│  ⛩ pagoda   ▲▲ montañas ▲▲▲               │  Stage: 168 dp (más el inset de estado)
│╭─────────────────────────────────────────╮│
││ Match each word with its meaning.       ││  ← instrucción (1 línea)
││ 뜻과 짝을 맞추세요                       ││
││                                         ││
││            área del minijuego           ││  Board
││                                         ││
││ ┌─────────────────────────────────────┐ ││
││ │ ✓ Correct!                          │ ││  ← FeedbackRibbon (solo tras responder)
│╰─┴─────────────────────────────────────┴─╯│
└───────────────────────────────────────────┘
```

## 2 · Entrada y navegación

- **MoreSheet:** fila nueva **Games** · 게임, la primera, con el icono `gamepad-2`. Orden: Games · Grammar · Calendar · divisor · Dark mode · Settings.
- **Abre encima del destino actual** como pantalla completa (`Screen.Games`, sin barra inferior), igual que una sesión de tarjetas. Back del sistema y el botón de pausa abren la hoja de pausa; nunca se sale sin pasar por ella.
- **No hay catálogo ni menú de juegos.** Al entrar se juega.
- Iconos nuevos de Lucide (MIT), en `design/icons/` y en `Lucide`: `gamepad-2`, `pause`, `play`, `flame`, `volume-2`, `delete`, `mic`, `lightbulb` (y `clock`, que ya está en `design/icons/` pero no en `Lucide`). Se animan una vez al pulsarlos, como los demás.

## 3 · Colores del escenario

Grupo nuevo `games` en `tokens.json`. Son **los mismos valores de las pantallas de carga** (`design/splash-icon-context.txt`, puntos 2 y 3), no una paleta nueva. Iguales en claro y oscuro.

| Token | Valor | Uso |
|---|---|---|
| `night` | `#00143E` | cielo, base (hoy `Navy` en `Splash.kt`: pasa a usar el token) |
| `night-deep` | `#000E2C` | arriba del cielo, montaña cercana, pagoda |
| `night-shade` | `#002864` | montaña media |
| `sky-mid` | `#468CE1` | montaña lejana (35 %), anillo del reloj gastado |
| `sky-light` | `#BFD9FF` | luna, textos secundarios sobre la escena |
| `lantern` | `#FFD27A` | farol encendido, llama de la racha |
| `lantern-red` | `#E5484D` | cuerpo del farol |
| `petal` | `#F58FB0` | pétalos de racha |

El horizonte usa `action` (`#0047A0`). El tablero usa solo tokens que ya existen (`surface`, `canvas`, `tint`, `line`, `field`, `correct-*`, `wrong-*`, `brand`, `action`, `go`, `fix`).

**Texto sobre la escena:** blanco (títulos) y `sky-light` (secundario). Contraste sobre `night`: blanco 17:1, `sky-light` 12:1.

## 4 · Escenario (GameStage)

Alto: **168 dp** más el inset de la barra de estado; ancho completo; de borde a borde. Un solo `Canvas` (`drawWithCache`), sin imágenes.

Coordenadas en dp dentro de 411 × 168 (y = 0 justo debajo del inset):

| Capa | Dibujo |
|---|---|
| Cielo | degradado vertical `night-deep` (y 0) → `night` (y 70) → `action` (y 150) |
| Estrellas | 14 puntos blancos de 1–2 dp, posiciones fijas (semilla 7), opacidad 0.5–0.9, solo con y < 90 |
| Luna | disco de 44 dp, centro (322, 66), `sky-light`; halo radial de 84 dp, `sky-light` del 25 % al 0 % |
| Montaña lejana | polígono suave, cimas en (60, 96), (170, 84), (300, 100); `sky-mid` al 35 % |
| Montaña media | cimas en (0, 118), (120, 104), (250, 112), (411, 98); `night-shade` |
| Pagoda | silueta de dos tejados curvos (como `splash_flash1`), base 56 × 46 dp, apoyada en (46, 132); `night-deep` |
| Montaña cercana | cimas en (0, 140), (90, 130), (330, 138), (411, 126), hasta y 168; `night-deep` |

Debajo de todo, el tablero tapa de y = 144 hacia abajo (se monta 24 dp sobre la escena).

**Movimiento propio de la escena** (todo en fase de dibujo; nada recompone):

- **Estrellas:** cada una oscila su opacidad ±0.25 con periodo propio de 2.4–4.0 s (seno). Es el único bucle sin fin de Games.
- **Paralaje al cambiar de juego:** lejana −6 dp, media −12 dp, cercana y pagoda −20 dp en x, 240 ms ease-in-out, y vuelven a 0 en otros 240 ms. La luna no se mueve.
- **Quieto** con *Quitar animaciones*, con `LocalFrozenMotion` (capturas) y cuando la pantalla no está en `RESUMED`.

### 4.1 · La luna como reloj (MoonTimer)

Solo en rondas con tiempo (Word Recall al memorizar, Hangul Typing, Memory Flip nivel 3 no lo usa).

- Anillo de 3 dp, radio 29 dp, centrado en la luna, extremos redondos.
- Empieza completo (360°) en blanco y se consume en sentido horario desde las 12; lo consumido queda en `sky-mid` al 40 %.
- Lineal en el tiempo. En el último 25 %, el anillo pasa a `lantern` (fundido de 240 ms) y la luna late una vez por segundo: escala 1 → 1.06 → 1, 400 ms ease-in-out.
- Al agotarse: el anillo desaparece en 150 ms. Sin parpadeos rojos.
- En pausa, el anillo se congela.
- TalkBack: la ronda anuncia *"{n} seconds"* al empezar y *"5 seconds left"* una vez.
- Con *Quitar animaciones*: el anillo salta en pasos de 1 s.

### 4.2 · Atrezo del juego (Stage prop)

Cada minijuego puede dibujar un elemento sobre la escena, entre la montaña media y la cercana. Solo dos lo usan: los faroles de **Hangman** y los de **Word Recall** (ver sus fichas). Al cambiar de juego el atrezo sale con fundido de 150 ms.

## 5 · GameTopBar

Sobre la escena, alto 56 dp, fondo transparente.

- **Izquierda:** `pause` (48 dp, blanco). *contentDescription*: "Pause game".
- **Centro, alineado a la izquierda tras el botón:** nombre del juego en `section-title` (16 sp, 700, blanco) y su coreano con `KoSide` en `sky-light`.
- **Derecha, dos píldoras** (alto 28 dp, fondo blanco al 12 %, radio completo, relleno 4 × 10 dp, 8 dp entre ellas):
  - `check` 14 dp + número de rondas ganadas en la sesión (`chip`, 12 sp, 700, blanco). TalkBack: "{n} rounds won".
  - `flame` 14 dp en `lantern` + racha actual (solo si la racha ≥ 2). TalkBack: "Streak {n}".

**Racha** = rondas ganadas seguidas en esta sesión. Una ronda perdida la pone a 0; una ronda neutra (ver 8) no la toca.

## 6 · Tablero (Board)

- Hoja `surface`, radio superior **24 dp**, borde superior de 2 dp `line`, desde y = 144 dp hasta abajo. Relleno: 16 dp a los lados, 16 dp arriba, y abajo 16 dp más el inset de navegación.
- **Instrucción** (arriba, fija): una línea en `body-bold` (14 sp, 700, `ink`) y debajo su coreano en `ko-below` (11 sp, `muted`). Máximo 2 líneas con la letra al 130 %.
- **Pista** (opcional, a la derecha de la instrucción): píldora `tint` con `lightbulb` 14 dp y el significado en `chip` (12 sp, 700, `brand-text`). Es el significado de tus notas; si no cabe, baja a una segunda fila.
- **Área del minijuego:** el resto. Cada juego la reparte como dice su ficha. Si el contenido no cabe con la letra al 130 %, el área hace scroll vertical; nunca se recorta.
- **Marca de origen** (abajo a la izquierda, 12 sp, `muted`): el documento de donde sale el contenido de la ronda, sin extensión y con elipsis. Si el contenido no es de tus notas (solo los pares de sonido de Sound Detective): **Extra · not from your notes**.

### 6.1 · Piezas comunes

| Pieza | Medidas y estados |
|---|---|
| **Tile** (ficha) | Mínimo 48 × 48 dp, relleno 10 × 14 dp, radio 12, fondo `canvas`, borde 2 dp `line` y canto inferior de 3 dp `field` (el mismo lenguaje que StickerButton). Coreano en `ko-title` (18 sp, 800); inglés en `body-bold`. **Pulsada:** baja 3 dp y pierde el canto, 90 ms. **Elegida:** borde `brand`, fondo `tint`. **Correcta:** fondo `correct-fill`, borde `correct-deco`, `check` de 14 dp en la esquina superior derecha. **Incorrecta:** fondo `wrong-fill`, borde `wrong-border`, `x` de 14 dp. **Usada / vacía:** opacidad 0.35 y sin canto. Nunca se distingue un estado solo por el color: siempre lleva icono o borde. |
| **Slot** (hueco) | Mismo tamaño que la ficha que espera; borde de 2 dp `field` a rayas (4 dp de trazo, 4 dp de hueco), radio 12, sin fondo. Con una ficha encima en vuelo, borde `brand`. |
| **Bloque silábico** | Tile cuadrada de 56 dp con una sílaba (`ko-prompt`, 20 sp, 700). En pantallas con más de 6 bloques en fila, 48 dp. |
| **GameButton** | StickerButton de siempre. Solo aparece donde la ficha del juego lo pide (*Check*). |
| **ReplayButton** | Circular de 56 dp, fondo `action`, icono `volume-2` blanco de 24 dp. Ver `games-minigames.md`, sección *Audio*. |

### 6.2 · Movimientos comunes

Se añaden a `Motion` (los tiempos que ya existen no cambian):

| Nombre | Valor | Uso |
|---|---|---|
| `Fly` | `spring(dampingRatio = 0.72, stiffness = 520)` | una ficha que viaja a un hueco, a un contenedor o de vuelta |
| `Pop` | escala 1 → 1.10 → 1, 240 ms, curva `Spring` | acierto de una pieza |
| `Shake` | x: 0 → −6 → 6 → −4 → 4 → 0 dp, 320 ms, lineal entre puntos | error de una pieza (el `shake` del Quiz) |
| `Stagger` | 40 ms entre piezas | entradas y salidas en grupo |
| `Flip` | 400 ms, ease-in-out, la misma `cameraDistance` de `FlipCard`, cambia de cara a 90° | el giro de `FlipCard` (R-004): misma técnica, sin recomponer por fotograma |
| `Enter` | opacidad 0 → 1 y y: 12 dp → 0, 240 ms ease-out | piezas que aparecen |
| `Exit` | opacidad 1 → 0 y escala 1 → 0.96, 150 ms ease-out | piezas que se van |

**Regla de rendimiento:** toda animación continua se lee en la fase de dibujo o de layout (`graphicsLayer { }`, `offset { }`, `drawWithCache`). Ninguna lee `.value` en composición. Como mucho 16 piezas animadas a la vez.

**Con *Quitar animaciones*:** no hay `Fly`, `Pop`, `Shake`, `Flip`, paralaje, pétalos ni faroles que suben: las piezas cambian de sitio y de estado en el mismo fotograma. **Los tiempos de lectura no cambian** (cuánto dura el aviso, cuánto se ve una secuencia).

## 7 · Ciclo de una ronda

```
Intro del juego → Jugar → Feedback → Transición → Intro del siguiente → …
     520 ms                 ver 7.2      480 ms
```

### 7.1 · Intro del juego

1. El tablero ya está vacío. En la barra, el nombre del juego nuevo entra desde abajo (y: 8 dp → 0, opacidad 0 → 1, 240 ms ease-out).
2. La instrucción aparece con `Enter`.
3. Las piezas entran con `Enter` y `Stagger` (de arriba abajo y de izquierda a derecha). Tope: 400 ms en total; si hay más de 10 piezas, el stagger baja a 24 ms.
4. Se puede tocar desde que la primera pieza es visible. La intro nunca bloquea.

### 7.2 · Feedback (FeedbackRibbon)

Cinta que sube desde el borde inferior del tablero, por encima del contenido; no lo tapa entero ni cambia su tamaño.

- Alto mínimo 64 dp, ancho completo menos 16 dp a cada lado, radio 16, a 16 dp del inset inferior.
- Entrada: y: 24 dp → 0 y opacidad 0 → 1, 240 ms, curva `Spring`. Salida: 150 ms ease-out.

| Resultado | Aspecto | Texto | Dura |
|---|---|---|---|
| **Ganada** | fondo `correct-fill`, borde 2 dp `correct-deco`, `check` 20 dp `correct` | **Correct!** · 정답! (16 sp, 700, `correct`) | 900 ms |
| **Perdida** | fondo `wrong-fill`, borde 2 dp `wrong-border`, `x` 20 dp `wrong` | **Not quite** · 아쉬워요 y debajo *Answer:* con la respuesta (14 sp; coreano en `ko-body`; etiqueta en `fill-muted`) | 1800 ms |
| **Neutra** | fondo `tint`, borde 2 dp `tint-border`, `lightbulb` 20 dp `brand-text` | el texto que diga la ficha del juego (p. ej. *Your notes say it this way:*) | 1800 ms |
| **Tiempo agotado** | como *Perdida*, con `clock` | **Time's up** · 시간 종료 y *Answer:* | 1800 ms |

- **Un toque en cualquier parte** adelanta a la siguiente ronda, pero no antes de 350 ms (para que el aviso se llegue a ver).
- Con TalkBack activo la cinta no avanza sola: muestra **Next** (Action, Small) y espera.
- Texto de la cinta: `liveRegion = Polite`.
- La frase de ánimo cambia con la racha (solo en *Ganada*): racha 3 → **Three in a row!**; 5 → **On fire!**; 10 → **Unstoppable!**; el resto, **Correct!**. Tras dos rondas perdidas seguidas, la siguiente *Perdida* dice **Almost. Keep going.** en vez de *Not quite*. Nunca hay textos de reproche (Games no usa el tono duro del Dashboard).

### 7.3 · Racha (StreakBurst)

Al llegar a racha 3, 5, 10 y luego cada 10:

- **Píldora de racha:** `Pop` (1 → 1.25 → 1, 320 ms) y la llama pasa de `lantern` a blanco y vuelve (240 ms).
- **Pétalos:** 12 pétalos `petal` (elipses de 6 × 10 dp, giradas) salen de la esquina superior derecha de la escena y cruzan en diagonal hacia abajo a la izquierda. Cada uno: 900–1300 ms, trayectoria con un seno de ±14 dp de amplitud, rotación de 180–360°, opacidad 1 → 0 en el último 30 %. Se dibujan en el `Canvas` de la escena; no existen como composables.
- No retrasa nada: ocurre encima del feedback y de la transición.

### 7.4 · Transición entre juegos

480 ms en total, tras cerrar la cinta:

1. **0–150 ms:** las piezas del tablero salen con `Exit` (todas a la vez) y la instrucción se desvanece.
2. **0–480 ms:** paralaje de la escena (4).
3. **120–360 ms:** el nombre del juego en la barra sale hacia arriba (y: 0 → −8 dp, opacidad → 0) y entra el nuevo.
4. **240 ms:** empieza la intro del juego siguiente (7.1). La ronda siguiente ya está preparada (se genera mientras se juega la actual): nunca hay indicador de carga entre rondas.

Si por lo que sea la siguiente ronda no está lista a los 480 ms: el tablero muestra el estado *Cargando* de `shell.md` (*Loading…*) a partir de 300 ms más.

### 7.5 · Resultado de una ronda

Cada juego termina en uno de cuatro resultados: **won**, **lost**, **neutral** (no cuenta ni a favor ni en contra, no toca la racha) o **skipped** (la ronda no se pudo jugar: sin voz, pausa larga; no se guarda como intento). Qué es cada cosa lo dice la ficha de cada juego.

## 8 · Apertura de la sesión

La primera vez que entras en la pantalla (no al volver de la pausa):

1. **0–700 ms:** la escena ocupa **toda la pantalla** (como una pantalla de carga): mismo dibujo, escalado para llenar el alto, con la luna en (322, 300). En el centro, **Games** (Poppins 800, 34 sp, blanco) y debajo **게임** (18 sp, `sky-light`). El título entra como en la pantalla de carga: opacidad 0 → 1 y sube 14 dp, de 60 a 380 ms.
2. **700–1100 ms:** la escena se recoge hasta sus 168 dp (alto animado, 400 ms ease-in-out); la luna viaja a su sitio; el título se encoge y se desvanece; el tablero sube desde abajo (y: 100 % → 0, mismo tiempo y curva).
3. **1100 ms:** intro del primer juego (7.1).

- **Un toque** salta al paso 2 al instante.
- Con *Quitar animaciones*: directo al paso 3.
- No se repite al girar el teléfono ni al volver de segundo plano (`rememberSaveable`).
- Mientras tanto, el motor prepara la sesión. Si a los 1100 ms no hay ronda: *Loading…* en el tablero.

## 9 · Pausa

Se abre con el botón `pause`, con Back, y sola al perder el foco (llamada, otra app, pantalla apagada, pérdida del foco de audio).

- **Al pausar:** se paran el reloj de la luna, las cuentas atrás y la voz; se congela el tablero (no se puede tocar) y se cubre con un velo `scrim`.
- **PauseSheet** (ModalBottomSheet como MoreSheet):
  - título **Paused** · 일시정지 (`section-title`);
  - cuatro cifras en fila (etiqueta `label-caps` `muted`, valor 20 sp 800): **Rounds** 라운드 · **Won** 정답 (en `correct`) · **Best streak** 최고 연속 · **Time** 시간;
  - una fila con interruptor: **Timer in typing games** · 타자 시간 제한 (ThemeSwitch; guardado en DataStore; encendido por defecto);
  - si los juegos de escucha están apagados por falta de voz: recuadro `canvas` con `volume-2` y *Listening games are off: no offline Korean voice on this phone. Install one in Android's text-to-speech settings.* (12 sp, `muted`). Sin botón: la app no abre ajustes del sistema;
  - **Resume** · 계속하기 (`Go`, ancho completo) y **End session** · 끝내기 (`Gray`, ancho completo), con 8 dp entre ellos.
- **Cerrar la hoja** (arrastrar, tocar fuera, Back) = *Resume*.
- **Al reanudar:** cuenta atrás **3 · 2 · 1** en el centro del tablero solo si la ronda tiene reloj (cada número 600 ms: escala 0.8 → 1, opacidad 0 → 1 → 0; 34 sp, 800, `brand`). Sin reloj, se sigue al instante.
- **Word Recall en fase de memorizar** y **cualquier audio a medias** no se pueden reanudar con justicia: la ronda se repite desde el principio con el mismo contenido.
- Si la pausa dura más de 30 min, la sesión se cierra sola y al volver se ve el resumen (10).

## 10 · Fin de la sesión (SessionSummary)

Tras **End session**. Sustituye al tablero; la escena vuelve a crecer a pantalla completa (la inversa del paso 2 de la apertura, 400 ms).

- **Faroles que suben:** uno por cada 3 rondas ganadas (máximo 12). Cada farol (cuerpo `lantern-red` de 18 × 24 dp con esquinas de 6 dp, luz `lantern` dentro, halo de 40 dp al 30 %) sale desde debajo de la pantalla en una x al azar y sube 110 % del alto en 2600–3600 ms, con deriva lateral de ±12 dp (seno) y escala 1 → 0.7. Salen escalonados cada 180 ms. Con 0 rondas ganadas no hay faroles.
- **Encima, una Card** (`surface`, radio 16, relleno 20 dp, centrada, margen lateral 16 dp), que entra con `Enter` a los 300 ms:
  - **Nice session** · 수고했어요 (24 sp, 800). Con 0 rondas: **See you next time** · 다음에 봐요;
  - las mismas cuatro cifras de la pausa;
  - **Words to review** · 복습할 단어 (`label-caps`, `muted`): hasta 5 elementos fallados en la sesión, cada uno *coreano* (`ko-body`, 700) y su significado (`body`, `muted`). Sin fallos: *Nothing to review. Clean session.*;
  - **Play again** · 다시 하기 (`Go`) y **Done** · 완료 (`Action`), ancho completo.
- **Done** y Back vuelven a la pantalla desde la que se abrió Games.
- Con *Quitar animaciones*: sin faroles; la Card aparece directamente.

## 11 · Estados

| Estado | Qué se ve |
|---|---|
| **Sin documentos** | Escena normal; en el tablero, el estado *Vacío* de `shell.md`: icono `gamepad-2`, **No notes to play with yet** · 아직 노트가 없어요, *Add a PDF in Library and your words become games.*, botón **Go to Library** (`Go`). |
| **Contenido insuficiente para todos los juegos** | El mismo estado con *Your notes need at least 8 words to start playing.* |
| **Un juego sin contenido** | No se ve nada: ese juego no sale. |
| **Cargando** (más de 300 ms) | *Loading…* de `shell.md` en el tablero. |
| **Error al preparar una ronda** | No se muestra error: se salta a otro juego. Tres fallos seguidos → recuadro de error de `shell.md` con *Games couldn't start this time.* y **Back** (`Gray`). |
| **Procesando un PDF** | Se puede jugar igual (Games no usa el modelo). |

## 12 · Accesibilidad

- Zona táctil mínima de 48 dp en todo.
- **Todo lo que se arrastra también se puede hacer con toques** (tocar la ficha y luego el destino). Cada ficha dice cómo en su ficha de juego.
- Fichas: `contentDescription` con su texto y su estado ("selected", "matched", "wrong"). Huecos: "Empty slot {n} of {total}".
- Ningún estado depende solo del color (6.1).
- Letra al 130 %: las fichas crecen y el área hace scroll; las rejillas pasan a menos columnas donde la ficha lo dice.
- Con TalkBack: sin avance automático (7.2) y los relojes se desactivan (las rondas con reloj se juegan sin él).
- Reduce movimiento: 6.2.

## 13 · Capturas de prueba (Roborazzi)

Con `LocalFrozenMotion`, en claro y oscuro: escena + tablero vacío; una ronda de cada uno de los 13 juegos en su estado inicial; cinta *Correct!* y *Not quite*; PauseSheet; SessionSummary (sin faroles); estado sin documentos; Word Match al 130 %. Ninguna captura con una animación en marcha.
