# Ficha: Games como videojuego — personajes, escena y animación (Fase 10)

Base: `design/games.md` (ciclo de ronda, tablero, piezas, pausa, resumen) y `design/games-minigames.md` (los 13 juegos). **Esta ficha manda sobre `games.md` en las secciones 4 (escenario), 7.2 (cinta), 7.3 (racha), 8 (apertura), 9 (pausa: solo lo que se añade) y 10 (resumen).** Las reglas de cada minijuego no cambian.

**Idea:** hoy Games es un tablero con un decorado (`games_ronda1_oscuro`, `games_wordsort_oscuro`: una franja azul, una luna y nada que reaccione). Pasa a tener **reparto**: un personaje que juega contigo y reacciona a cada toque con animación de dibujo animado (anticipación, estirar y aplastar, rebote, reacción exagerada, ritmo rápido), y un **momento de aprendizaje** fijo en cada ronda: la palabra, su significado y su sonido.

**Originalidad:** personajes y escenas propios. La referencia (Dumb Ways to Die) es solo de *cómo se mueve* un personaje, no de su forma: nada de cuerpos de judía, ni sus colores, ni sus gags.

**Reglas que no cambian:** sin vibración; sin efectos de sonido (el único audio sigue siendo la voz coreana del teléfono, ver 6); offline; todo dibujado con `Canvas` de Compose, **sin Lottie, sin imágenes y sin dependencias nuevas**; nada de texto de las notas en logs. **Colores:** solo el grupo `games` de `tokens.json` (`night`, `night-deep`, `night-shade`, `sky-mid`, `sky-light`, `lantern`, `lantern-red`, `petal`), blanco y `action`. Ningún color nuevo.

---

## 1 · Reparto

| Personaje | Quién es | Papel en el juego |
|---|---|---|
| **Beom** · 범 | Cachorro de tigre blanco (백호), redondo, cabezón, curioso y dramático | El compañero de juego: mira lo que tocas, celebra, se viene abajo cuando fallas y señala la respuesta. **Nunca se burla.** |
| **Deung** · 등 | Farolito rojo flotante, su amigo | El marcador vivo: su llama es la **racha**; sus hermanos son las **vidas** de Hangman y la secuencia de Word Recall. |
| **Dal** · 달 | La luna | Sigue siendo el **reloj** (`games.md` 4.1). Ahora tiene cara, solo cuando importa. |

```
   ✦        ·      ╭───╮ Dal            ✓ 9  🔥 4
        ·         ( ◡ ◡ )
  ⛩            ·   ╰───╯        ▲▲
        /\_/\    ▯ Deung     ▲▲▲▲▲▲
       ( •ㅅ• )  ◉
   ~~~~╰─UU─╯~~~~~~~~~~~~~~~~~~~~~~~~~   ← cresta donde vive Beom
  ╭───────────────────────────────────╮  ← tablero
```

## 2 · Escenario v2 (GameStage)

Alto: **196 dp** (antes 168) más el inset de estado. El tablero empieza en y = 172 (se monta 24 dp). Un solo `Canvas` con `drawWithCache`; las capas fijas se construyen una vez por tamaño.

Coordenadas en dp sobre 411 × 196. Capas, de atrás adelante:

| # | Capa | Dibujo |
|---|---|---|
| 1 | Cielo | degradado vertical `night-deep` (0) → `night` (90) → `action` (184) |
| 2 | Resplandor del horizonte | degradado radial centrado en (262, 150), radio 190: `sky-mid` 28 % → 0 % |
| 3 | Estrellas | 22 puntos blancos de 1–2 dp (semilla 7, y < 100, opacidad 0.4–0.9) y 3 destellos de cuatro puntas de 6 dp en (38, 30), (150, 22), (372, 92) |
| 4 | Dal | disco de 44 dp, centro **(262, 74)** (antes (322, 66): chocaba con las píldoras de la barra, ver `games_ronda1_oscuro`); relleno radial blanco (centro, desplazado −6, −6) → `sky-light`; 3 cráteres (elipses `sky-mid` al 18 %); halo radial de 92 dp `sky-light` 30 % → 0 % |
| 5 | Montaña lejana | curva suave (Bézier cuadráticas), cimas (40, 112), (150, 96), (290, 116), (400, 104); `sky-mid` 35 % |
| 6 | Niebla 1 | banda horizontal y 116–140, degradado vertical blanco 0 % → 9 % → 0 % |
| 7 | Montaña media | cimas (0, 136), (110, 118), (240, 130), (411, 112); degradado vertical `night-shade` → `night` |
| 8 | Pagoda | dos tejados curvos y remate, 40 × 34 dp, apoyada en (352, 140); `night-deep`; dos ventanas de 3 × 4 dp `lantern` al 80 % |
| 9 | Niebla 2 | banda y 140–158, blanco 0 % → 6 % → 0 % |
| 10 | Atrezo del juego | faroles de Hangman / Word Recall (`games.md` 4.2), ahora con cara (5.3) |
| 11 | Cresta cercana | loma ancha: (0, 160) → meseta y = 166 entre x 70 y 250 → (411, 150), hasta y 196; `night-deep`; borde superior con una línea de 1 dp `night-shade` (luz de luna) |
| 12 | Hierba | 9 briznas (trazos de 1.5 dp, 5–8 dp de alto, `night-shade`) sobre la meseta, semilla fija |
| 13 | **Beom** | pies en **(150, 166)** |
| 14 | **Deung** | centro en reposo **(206, 112)** |
| 15 | Partículas | pétalos, destellos, polvo, gotas (5) |

Movimiento propio de la escena (sin cambio de `games.md` 4: parpadeo de estrellas y paralaje al cambiar de juego) más: las bandas de niebla derivan en x ±10 dp con periodo de 14 s y 19 s (seno); las briznas se inclinan ±6° con periodo de 3.2 s.

## 3 · Beom: construcción

Lienzo propio de 72 × 80 dp. Origen **(0, 0) = centro de los pies**; y negativa hacia arriba. Formas planas, sin contorno.

| Parte | Forma (dp) | Color | Se anima con |
|---|---|---|---|
| Sombra | elipse 44 × 8 en (0, 1) | `night-deep` 45 % | se encoge al saltar: ancho × (1 − alto/80) |
| Cola | trazo de 7 dp, extremos redondos, curva de (−18, −14) a (−34, −38); dos anillos de 3 dp; punta blanca de 6 dp | `sky-light`; anillos `night-shade` | `tail` −1…1 = giro ±18° en la raíz; `tailPuff` 0…1 = grosor 7 → 10 dp |
| Pies | 2 cápsulas 14 × 8 en (±11, −4) | `sky-light` | — |
| Cuerpo | rectángulo redondeado 44 × 34, radio 17, centro (0, −20) | `sky-light` | `sx`, `sy` con pivote en los pies |
| Barriga | elipse 26 × 20 en (0, −16) | blanco | con el cuerpo |
| Brazos | 2 cápsulas 9 × 18, pivote en el hombro (±19, −30) | `sky-light` | `armL`, `armR`: 15° en reposo (hacia fuera), 150° = arriba |
| Cabeza | superelipse 52 × 44 (n = 2.6), centro (0, −50) | `sky-light` | `tilt` (grados), pivote en (0, −32) |
| Orejas | triángulos redondeados 14 × 13 en (±18, −70); interior de 7 × 6 | `sky-light`; interior `petal` 60 % | `earL`, `earR` ±30°, pivote en su base |
| Rayas | frente: 3 arcos de 3 dp en (0, −64) y (±7, −62); mejillas: 2 por lado de 2.5 dp en (±23, −48) y (±22, −42) | `night-shade` | con la cabeza |
| Hocico | elipse 18 × 12 en (0, −42) | blanco | — |
| Nariz | triángulo redondeado 6 × 4 en (0, −45) | `petal` | — |
| Colorete | 2 círculos de 8 dp en (±19, −42) | `petal` 35 % | `blush` 0…1 (opacidad × 0.35…0.8) |
| Ojos | en (±11, −50); ver tabla | `night-deep`, brillo blanco | `eyes`, `lookX/lookY` (±2 dp), parpadeo |
| Boca | en (0, −38); ver tabla | `night-deep`, lengua `petal` | `mouth` |

**Ojos** (`eyes`): `Open` elipse 7 × 9 con brillo de 2.5 dp arriba a la izquierda · `Happy` arco ∩ de 9 dp, trazo 3 · `Squeeze` «> <», trazo 3 · `Wide` círculo de 10 dp, pupila de 4 y brillo · `Sleepy` línea de 8 dp algo caída · `Shut` arco ∪.
**Boca** (`mouth`): `Smile` «w» de dos arcos, trazo 2 · `Open` forma de D de 10 × 8 con lengua · `O` círculo de 6 dp · `Wobble` onda de 12 dp · `Flat` línea de 6 dp.

**Pose:** `BeomPose(sx, sy, y, rot, tilt, earL, earR, armL, armR, tail, tailPuff, eyes, mouth, lookX, lookY, blush)`. Una animación es una lista de fotogramas clave `(ms, pose, curva)`; entre dos claves se interpola cada número; `eyes` y `mouth` cambian en la clave. **Arrastre (follow-through):** orejas y cola leen el reloj con **60 ms de retraso** y sobrepasan un 20 % su valor final.

**Parpadeo:** cada 3.2–5.5 s (al azar, semilla por sesión), 120 ms (alto del ojo 1 → 0.1 → 1). Solo con `eyes = Open`.

## 4 · Deung y Dal

**Deung** (lienzo 28 × 44 dp, origen en el centro del cuerpo):

| Parte | Forma | Color |
|---|---|---|
| Halo | radial de 44 dp | `lantern` 30 % → 0 %, × `glow` |
| Cordel | línea de 1.5 dp, 10 dp hacia arriba | `sky-light` 60 % |
| Tapa y base | 12 × 4 (radio 2) arriba; 10 × 3 abajo | `night-deep` |
| Cuerpo | rectángulo redondeado 22 × 24, radio 9 | `lantern-red` |
| Costillas | 3 líneas verticales de 1 dp | `night-deep` 25 % |
| Luz | radial de 9 dp en el centro | `lantern` 90 % → 0 %, × `glow` |
| Cara | 2 ojos de 3 dp en (±4, −1); boca: arco de 4 dp | `night-deep` |
| Borla | 2 trazos de 6 dp bajo la base | `lantern` |
| Llama de racha | lágrima 8 × 12 sobre la tapa (solo racha ≥ 3) | `lantern`, núcleo blanco |

Parámetros: `x, y, tilt, glow (0…1.6), sx, sy, eyes (Open/Happy/Shut/Wide)`.

**Dal:** su cara son dos ojos y una boca en `night-shade` al 50 %, dibujados sobre el disco. Estados: `Calm` (sin cara; por defecto) · `Nervous` (último 25 % del reloj: ojos de punto de 4 dp muy abiertos, boca en onda, una gota `sky-mid` a la derecha) · `Sleepy` (pausa: ojos `Shut`) · `Proud` (resumen: ojos `Happy`, sonrisa).

## 5 · Animaciones

**Curvas** (las tres de `shell.md` más dos nuevas en `Motion`):
`EaseOut` (0.2, 0.8, 0.2, 1) · `EaseInOut` (0.4, 0, 0.2, 1) · `Spring` (0.34, 1.56, 0.64, 1) · **`EaseIn`** (0.5, 0, 1, 1) · **`Bounce`** = `spring(dampingRatio = 0.45, stiffness = 380)`.

Notación: `(sx, sy)` = escala del cuerpo con pivote en los pies; `y` = altura del salto en dp (positiva = arriba). Los tiempos son desde el inicio de la animación.

### 5.1 · Reposo y atención

| ID | Cuándo | Dura · curva | Qué se mueve |
|---|---|---|---|
| **A0 Idle** | siempre que no haya otra | bucle | **Beom:** respira `(1, 1) ↔ (0.985, 1.03)`, periodo 2400 ms, `EaseInOut`; cola `tail` ±0.4, periodo 1800 ms; parpadeo. **Deung:** flota y ±3 dp, periodo 2400 ms (seno), `glow` 1 ± 0.08. Cada 9–14 s, un gesto al azar: sacude una oreja (±25°, 200 ms) o mira al tablero (`lookY` +2, 600 ms). |
| **A1 Anticipa** | tocas o arrastras una ficha | 120 ms `EaseOut`; se mantiene; vuelve en 150 ms | **Beom:** `tilt` +6°, orejas +8°, `eyes = Open`, `lookY` +2; las pupilas siguen la x del dedo (`lookX` = −2…2 a lo ancho del tablero). **La ficha:** escala 1 → 1.08 y sube 4 dp con sombra (`night-deep` 20 %, 6 dp) en 90 ms `EaseOut`; al soltarla vuelve con `Fly`. |
| **A11 Escucha** | suena la voz | mientras suena | **Beom:** `armR` 140° (mano en la oreja), `tilt` +8° hacia el ReplayButton, orejas +15°, `eyes = Happy`. Entrada y salida 150 ms. |
| **A12 Piensa** | 8 s sin tocar nada | 900 ms, una vez cada 8 s | **Beom:** `armR` 120° ± 10° tres veces (300 ms cada una, `EaseInOut`), `eyes = Open`, `lookX` +2 (mira la pista si existe). A los 16 s, **Deung** deriva 10 dp hacia la esquina de la pista y vuelve (600 ms). Nunca se revela nada solo. |

### 5.2 · Acierto, fallo, tiempo

| ID | Cuándo | Total | Fotogramas clave |
|---|---|---|---|
| **A2 Acierto parcial** | una pieza correcta dentro de la ronda (un par, una sílaba) | **400 ms** | 0–70 `(1.08, 0.92)` `EaseIn` · 70–200 `y` 10, `(0.94, 1.08)`, `eyes = Happy` `EaseOut` · 200–280 `y` 0, `(1.06, 0.94)` `EaseIn` · 280–400 `(1, 1)` `Bounce`. **Deung:** `glow` 1 → 1.3 → 1 en 300 ms. |
| **A3 Ronda ganada** | resultado *won* | **900 ms** | **0–110 anticipación:** `(1.18, 0.80)`, brazos 0°, `eyes = Squeeze`; `EaseIn`. **110–330 despegue:** `y` 0 → 34, `(0.86, 1.20)`, brazos 150°, orejas −20°, `tail` +1, `mouth = Open`, `eyes = Happy`; `EaseOut`. **330–520 caída:** `y` 34 → 0, `tilt` −8° → +8°; `EaseIn`. **520–620 aterrizaje:** `(1.22, 0.78)`; salen 2 nubes de polvo (elipses `sky-light` 40 %, 10 → 18 dp, a ±20 dp de los pies, se desvanecen en 220 ms). **620–900:** `(1, 1)` con `Bounce`, brazos a 15°, `blush` 1. **Deung:** media órbita sobre la cabeza de Beom (radio 30 dp, 500 ms `EaseInOut`), `glow` 1.4, y 4 destellos de cuatro puntas (`lantern`, 6 dp) que salen 24 dp en radial y se apagan en 400 ms. |
| **A4 Ronda perdida** | resultado *lost* | **1000 ms** + mantiene | **0–90 susto:** `y` 6, `(0.90, 1.14)`, `eyes = Wide`, orejas +25°, `mouth = O`, `tailPuff` 1; `EaseOut`. **90–400 se desinfla:** `(1.20, 0.82)`, `tilt` 14°, orejas −30° (con arrastre), brazos 0°, `tail` −1; `Bounce`. **400–1000 tambaleo:** `tilt` 14 → −8 → 4 → 0 (200 ms cada tramo, `EaseInOut`), `eyes = Squeeze` → `Sleepy`, `mouth = Wobble`; una gota `sky-mid` de 4 dp cae 12 dp desde la sien y se apaga (400 ms). **Mantiene** hasta que se cierra la tarjeta: `eyes = Open`, `lookY` +2 y `armR` 120° **señalando la respuesta** de la LearnCard. **Deung:** `glow` 1 → 0.5 (240 ms), `tilt` 12° hacia Beom y se acerca 8 dp. Sin destellos rojos ni gestos de burla. |
| **A5 Tiempo agotado** | *Time's up* | 1150 ms | 0–150: Beom mira a Dal (`lookY` −2, `tilt` −6°), Dal pasa a `Nervous` con un «!» blanco de 14 sp que salta (escala 0 → 1.2 → 1, 150 ms). Después, **A4** completa. |
| **A13 Vida perdida** | Hangman: se apaga un farol | 330 ms | El farol hermano: `eyes = Shut`, `glow` → 0.15 y cae 10° (240 ms `EaseOut`): **se duerme**, no muere. Beom hace solo el tramo 0–90 de A4 y vuelve. |

### 5.3 · Racha

La racha vive en **la llama de Deung**: con racha ≥ 3 lleva la lágrima `lantern` sobre la tapa, que parpadea (alto × 0.9…1.1, 300 ms, `EaseInOut`) y `glow` base 1.3. La píldora de la barra sigue igual (`games.md` 7.3).

| Racha | Texto | Animación (encima de A3) |
|---|---|---|
| **3** | Three in a row! | La llama **se enciende**: escala 0 → 1.3 → 1 (320 ms `Spring`). Pétalos: los 12 de `games.md` 7.3. |
| **5** | On fire! | **Doble salto:** A3 con dos despegues seguidos (el segundo al 70 % de altura); total 1200 ms. Deung da una **órbita completa** (700 ms). 12 pétalos. |
| **10**, y cada 10 | Unstoppable! | **Voltereta:** A3 con `y` 48 y 420 ms de vuelo; `rot` 0° → −360° durante el vuelo; aterrizaje `(1.28, 0.74)`. El halo de Dal late 92 → 118 → 92 dp (400 ms). 20 pétalos. Deung suelta 3 farolillos que suben (los de `games.md` 10, 1200 ms). |
| **Se pierde** (de ≥ 3 a 0) | — | La llama se apaga: escala → 0 en 150 ms y un anillo de humo `sky-light` 30 % (6 → 16 dp, 300 ms). Beom la mira 300 ms (`lookX` +2). Va dentro de A4, sin alargarla. |

### 5.4 · Subir de nivel

Cuando el motor sube el nivel de un juego (1 → 2, 2 → 3). Ocurre **durante la transición y la intro** del juego siguiente; no bloquea ni retrasa nada. **1400 ms.**

1. **0–240:** junto al nombre del juego baja una píldora **LEVEL 2** · 레벨 2 (fondo blanco 12 %, texto blanco `chip`): y −20 → 0, `Spring`.
2. **0–360:** Beom **gira sobre sí mismo**: `sx` 1 → 0.1 → 1 dos veces (180 ms cada giro, `EaseInOut`), con `y` 16 en arco.
3. **360–700:** cae en pose «¡tachán!»: `(1.12, 0.90)` → `(1, 1)` con `Bounce`, brazos 150°, `eyes = Happy`, `mouth = Open`.
4. **400–1000:** 3 destellos `lantern` de 8 dp alrededor de la cabeza (escala 0 → 1 → 0, 500 ms cada uno, 80 ms entre ellos).
5. **0–480:** la cresta cercana sube 4 dp y vuelve (`EaseInOut`): el suelo «acusa» el salto.
6. La píldora se queda 2000 ms y se desvanece en 240 ms.

**Bajar de nivel no se anima ni se anuncia.**

### 5.5 · Apertura, pausa y fin de sesión

| ID | Cuándo | Qué pasa |
|---|---|---|
| **A8 Entrada** | apertura (`games.md` 8), paso 2 (700–1100 ms) | Mientras la escena se recoge, Beom **entra desde la izquierda**: x −40 → 150 en dos saltos (200 ms cada uno, `y` 12, con su estirar/aplastar de A2). Deung le sigue 80 ms después. Al llegar **saluda**: `armR` 150° ± 15° dos veces (400 ms). |
| **A9 Pausa** | hoja de pausa abierta | Beom se sienta y **dormita**: `(1.04, 0.96)`, `eyes = Sleepy`, tres «z» (10 sp, `sky-light`) suben 16 dp y se apagan, una cada 600 ms. Deung `glow` 0.6. Dal `Sleepy`. **Al reanudar:** respingo de 240 ms (`(0.92, 1.10)`, `eyes = Wide` → `Open`, `Bounce`). |
| **A10 Fin** | resumen (`games.md` 10) | La escena crece a pantalla completa; Beom queda en el tercio inferior a escala 1.6 y Deung a su lado. Según la sesión: **≥ 60 % ganadas:** baile de la victoria, 4 tiempos de 300 ms (`tilt` ±10° alternando `(1.08, 0.94)` y `(0.96, 1.06)`), dos veces, y **reverencia** (`tilt` 20°, 300 ms); sigue con la mirada los faroles que suben. **Menos del 60 %, con rondas:** asiente dos veces (`tilt` 0 → 10 → 0, 240 ms) y levanta el brazo. **0 rondas:** bostezo (`mouth = Open` 600 ms, brazos 150° estirándose) y saludo. Dal `Proud`. |

**Resumen con 0 rondas** (`games_resumen_sin_rondas_oscuro`: una sesión vacía decía *Clean session* y ofrecía *Play again* en verde): título **See you next time** · 다음에 봐요, cifras ocultas, texto *No rounds this time.* (14 sp, `muted`), botones **Play** · 시작 (`Go`) y **Done** (`Action`). *Nothing to review. Clean session.* solo sale con ≥ 1 ronda jugada y 0 fallos.

## 6 · El aprendizaje, a la vista (LearnCard)

Sustituye al contenido de la cinta de feedback (`games.md` 7.2). Mismo sitio, misma entrada y salida; usa `ResultBanner` (`polish.md`, P-02) con radio 16.

```
┌─────────────────────────────────────────────┐
│ ✓ Correct!  정답!                      (🔊) │  título + ReplayButton de 40 dp
│ 마당                                        │  la palabra: 24 sp, 700, `fill-ink`
│ Yard                                        │  su significado: 14 sp, `fill-muted`
│ [NEW 새 단어]   o   [★ Mastered]            │  Pill, solo si aplica
└─────────────────────────────────────────────┘
```

- **Qué palabra:** la de la ronda. Si la ronda tiene varias (Word Match, Memory Flip, Word Sort, Listen & Guess): en *lost*, la **primera que fallaste**; en *won*, la que tiene **menos aciertos acumulados** (`game_item_progress`). En juegos de frase (Sentence Builder, Grammar Fixer): la frase en `ko-prompt` (20 sp) y su traducción.
- **En *lost*:** además, encima de la palabra, *Answer:* (`fill-muted`). En Grammar Fixer, la partícula correcta va subrayada con 2 dp `correct-deco`.
- **El sonido:** 200 ms después de aparecer, **la voz coreana dice la palabra una vez** (la misma voz sin red de los juegos de escucha). Mientras suena, **cada bloque silábico de la palabra hace `Pop`** por turno (escala 1 → 1.12 → 1, 120 ms): con `UtteranceProgressListener.onRangeStart` si el motor lo da; si no, un bloque cada 280 ms. El ReplayButton la repite cuantas veces quieras (no cuenta para nada).
  - No suena si: no hay voz coreana sin red, el volumen multimedia es 0, otra app tiene el audio, TalkBack está activo (lo lee TalkBack) o el ajuste está apagado. **Nunca sale un aviso por esto**: la tarjeta se ve igual, sin sonido.
  - En los juegos de escucha (6 y 13) no suena al ganar (ya la oíste); al perder, sí.
- **Tiempos:** *won*: 900 ms o hasta 300 ms después de acabar la voz (tope 2500 ms). *lost* / *time's up*: 2200 ms o hasta 600 ms después de la voz (tope 4000 ms). Un toque avanza (no antes de 350 ms) y **corta la voz**.
- **Ajuste** en PauseSheet, bajo *Timer in typing games*: **Say the word after each round** · 단어 읽어 주기 (ThemeSwitch, encendido por defecto, DataStore `gamesSpeakWord`). Sin voz instalada: la fila sale deshabilitada con *No offline Korean voice on this phone.*
- **Palabra nueva** (primera vez que sale, `seen == 0`): durante la intro, la ficha lleva `Pill info` **NEW** · 새 단어 pegada a su esquina superior izquierda (se va al primer toque) y Beom abre los ojos (`eyes = Wide`, 300 ms).
- **Palabra dominada** (su racha llega a 3 en esta ronda): `Pill correct` **★ Mastered** en la tarjeta y un destello `lantern` sobre la palabra (escala 0 → 1 → 0, 500 ms).
- **Resumen de sesión, *Words to review*:** cada fila entra con `Enter` y 80 ms de escalón; tocar una fila **dice la palabra** (icono `volume-2` de 16 dp `muted` a la derecha). Beom mira la lista (`lookY` +2).

**Decisión que cambia (anotar en DECISIONS como D10-2):** D8-5 sigue («sin efectos de sonido»), pero la voz coreana pasa de «solo en los dos juegos de escucha» a «también al final de cada ronda, con un ajuste para apagarla». No hace falta ningún permiso nuevo (D8-8 ya cubre la voz).

## 7 · Tablero: lo que se retoca

| ID | Captura | Cambio |
|---|---|---|
| G-1 | todas las de Games | Instrucción: inglés a **16 sp** 700 (antes 14) y coreano a **12 sp** `muted` (antes 11): era lo más pequeño de la pantalla siendo lo primero que hay que leer. |
| G-2 | `games_wordsort_oscuro` | Word Sort deja el 60 % vacío. Los contenedores ocupan el alto disponible (`weight(1f)`, mínimo 140 dp) y la bandeja de fichas queda pegada abajo, con fichas de 56 dp de alto. |
| G-3 | `games_ronda1_oscuro` | Sentence Builder: la traducción pasa a 16 sp y la fila de huecos y la de fichas se centran en el alto libre; *Check* en **Large** al pie. |
| G-4 | `games_listenguess_oscuro` | ReplayButton centrado; el contador *1 / 3* a su izquierda y VoiceBars a su derecha, alineados en una fila de 56 dp. |
| G-5 | todas | Fichas en oscuro: `softBox()` no aplica (ya tienen borde); estados *Correcta* / *Incorrecta* con los `*-fill` oscuros nuevos y sus bordes. |
| G-6 | `games_pausa_oscuro` | PauseSheet usa `SheetHeader` y `StatStrip plain`; *Resume* en Large. |

## 8 · Reglas de rendimiento y accesibilidad

- **Un solo `Canvas`** para escena, personajes y partículas. Las formas fijas, en `drawWithCache`; la pose se aplica con transformaciones (`withTransform`). Nada lee estado animado en composición.
- **Presupuesto:** Beom ≤ 40 operaciones de dibujo, Deung ≤ 14, partículas ≤ 32 a la vez. Medir en el OPPO con el macrobenchmark de Games: CPU por fotograma **P90 ≤ 8 ms** (hoy 6.4) y **P99 ≤ 12 ms** (hoy 9.6). Si se pasa: primero se quitan niebla y hierba, después las partículas de polvo.
- **Bucles sin fin:** estrellas, niebla, hierba y A0. Todos se paran fuera de `RESUMED`, en pausa y con la hoja de pausa abierta (queda A9 quieto tras su entrada).
- **Con *Quitar animaciones* y `LocalFrozenMotion`:** nada se mueve. Beom **cambia de pose en un fotograma**: reposo, contento (`eyes = Happy`, brazos 150°) en *won*, triste (`eyes = Sleepy`, `tilt` 10°, señalando) en *lost*. Deung cambia su `glow`. La LearnCard y la voz funcionan igual.
- **TalkBack:** los personajes son decoración (`clearAndSetSemantics {}`); todo lo que comunican ya lo dice la tarjeta (`liveRegion = Polite`): "Correct. 마당. Yard." Las píldoras *NEW* y *Mastered* se leen.
- **Nunca un estado solo por el color ni solo por el personaje:** el resultado siempre está también en la tarjeta.

## 9 · Cómo se organiza el código (sugerencia para Code)

`ui/games/stage/`: `StageScene.kt` (capas 1–12), `Beom.kt` (pose + dibujo), `Deung.kt`, `Dal.kt`, `Particles.kt`, `StageDirector.kt` (traduce los eventos del juego —`PieceTouched`, `PieceCorrect`, `RoundWon`, `RoundLost`, `TimedOut`, `Streak(n)`, `StreakLost`, `LevelUp`, `Paused`, `Resumed`, `SessionEnded`— a pistas de fotogramas clave; una animación nueva interrumpe a la anterior partiendo de la pose actual). `ui/games/LearnCard.kt`.

Pruebas: las pistas son datos → prueba JVM de que cada animación dura lo que dice esta ficha y termina en la pose de reposo; `StageDirector` con eventos encadenados (ganar durante A4, pausar durante A3); Roborazzi con `LocalFrozenMotion` de las tres poses, de Deung con y sin llama, de Dal en sus 4 estados y de la LearnCard (won, lost, NEW, Mastered), en claro y oscuro.

## 10 · Dependencias

Ninguna. Se descartó Lottie (y Rive): con tres personajes hechos de menos de 60 formas, `Canvas` da el mismo resultado sin añadir 0.3–2 MB, sin archivos de animación que mantener fuera del código y sin una licencia más en *Licenses*.
