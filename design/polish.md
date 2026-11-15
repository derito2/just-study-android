# Ficha: pulido de la interfaz (Fase 10)

Base: `design/shell.md` y las fichas de cada pantalla; esta ficha **manda sobre ellas donde las contradiga**. Valores en `design/tokens.json` (versión 2). Pantalla de 411 × 905 dp.

**Origen:** `qa/ronda-2/RECORRIDO.md` y sus capturas (`qa/ronda-2/capturas/`, fuera de git). Cada cambio cita la captura que lo justifica.

**No cambia:** `background` (claro y oscuro), `action`, `brand`, la barra inferior, los marcos de tarjeta (borde 2 dp `line`, radio 16), el lenguaje de StickerButton (relleno + canto, hundirse al pulsar, sin ripple), sin vibración, offline, logs sin texto de notas. No es un rediseño: mismas pantallas, mismo orden de destinos.

**Referencia de criterio** (Duolingo y apps bien acabadas): una sola acción principal por pantalla; lo que más importa, más grande; lo destructivo, discreto; cada estado (vacío, cargando, error) dice qué hacer después.

Los ID (`P-xx`) son los que usa `docs/FASE_10_PLAN.md`.

---

## 1 · Reglas comunes

### P-01 · Tres tamaños de botón, con regla de uso

Hoy hay dos (44 + 4 y 36 + 3) usados sin criterio: *Practice this* del hero sale pequeño (`dashboard_arriba_claro`), los cuatro de valorar ocupan 150 dp (`flashcards_sesion_reverso_claro`), las píldoras de mes del calendario pesan como una acción (`calendar_mes_oscuro`).

| Tamaño | Alto visible + canto | Texto | Coreano debajo | Uso |
|---|---|---|---|---|
| **Large** (nuevo) | 52 + 4 dp | 15 sp, 700, MAYÚSCULAS, tracking 0.053em | 11 sp | **La acción del pie de pantalla**, ancho completo: *Show answer*, *Check*, *Next question*, *See results*, *New quiz*, *Generate quiz*, *Resume*, *Play again* |
| **Default** | 44 + 4 dp (sin cambio) | 13 sp | 11 sp | Acciones dentro de una tarjeta: *Continue*, *Practice this*, *Choose files*, *Fix these weaknesses* |
| **Small** | 36 + 3 dp (sin cambio) | 12 sp | 10.5 sp | Acciones secundarias y de fila: *Details*, *Add all*, *Try again*, *Done* de edición |

- Nombres en `tokens.json → size`: `button-lg-h` = 52px (añadir). Radio 12 en los tres.
- **Una sola acción `Go` (verde) por pantalla.** Si hay dos acciones, la segunda es `Action` o `Gray`.
- **Lo destructivo nunca es un botón relleno dentro de una lista**: es un icono `trash-2` en `wrong-ink` (zona táctil 48 dp) que abre su diálogo; el botón `Danger` solo vive dentro del diálogo de confirmación.
- Los botones de un mismo grupo tienen el mismo tamaño.

### P-02 · Componentes repetidos que pasan a ser uno

| Componente único | Sustituye a | Variantes |
|---|---|---|
| **StatStrip** | *Done/Correct/Left* del Quiz (`QuestionScreen`), las 4 cifras de PauseSheet y SessionSummary (`GamesScreen`), *New/Due now/Learned/Total* (`DecksScreen`) | `plain` (sin marco: etiqueta `label-caps` `muted` + su coreano `ko-side`, valor 20 sp 800) y `tiled` (cada cifra en su marco de tarjeta, valor 22 sp; es el `StatTile` actual). Columnas de igual ancho; el valor lleva color solo si se pasa (`correct-ink`, `wrong-ink`). |
| **Pill** | `Pill` de `DocumentCard` y `SettingsScreen`, *PRACTICE* de `SpeakingBlock`, la etiqueta de prioridad del hero, *213 cards* de `DeckCard`, la píldora de aviso de `Audio.kt` y de `TeacherScreen` | Tonos: `neutral` (`canvas` + `muted`), `info` (`tint` + `brand-text`), `correct` (`correct-fill` + `correct-ink`), `wrong` (`wrong-fill` + `wrong-ink`), `warn` (`warn-fill` + `fill-warn`). Alto 24 dp, radio completo, relleno 2 × 10 dp, texto `chip` (12 sp, 700), icono opcional de 12 dp. En oscuro, `neutral` lleva borde 1 dp `line`. `AiMark` se queda como está (es un `Pill neutral` con icono). |
| **ResultBanner** | `AnswerFeedback` (`Feedback.kt`), `FeedbackRibbon` (`GamesScreen.kt`), el recuadro de error de `States.kt` | Tonos `correct`, `wrong`, `neutral`, `warn`. Fondo `*-fill`, **borde 2 dp** (`correct-deco`, `wrong-border`, `tint-border`, `warn`), radio 12 (16 en Games), relleno 16 dp. Icono 20 dp + título 16 sp 700 en `correct-ink` / `wrong-ink`; cuerpo en `fill-ink`, etiquetas en `fill-muted`. Ranura opcional de acción a la derecha (ReplayButton pequeño, *Try again*). |
| **SearchField** | el buscador de Grammar y el de All vocabulary | Uno solo, **siempre arriba**, pegado bajo la barra superior (ver P-21). |
| **SheetHeader** | cabeceras sueltas de MoreSheet, PauseSheet, DocumentsSheet | Asa 32 × 4 dp, 12 dp, título `section-title` con su coreano, 12 dp. |

Regla: **dentro de un `*-fill` nunca se usa `onSurface`/`ink`/`muted` del tema**, solo `fill-ink`, `fill-muted`, `fill-warn`, `correct-ink`, `wrong-ink` (causa de H-01).

### P-03 · Contenedores en oscuro (H-11)

`canvas`, `surface` y `background` son el mismo color en oscuro y **no se tocan**. Todo recuadro `canvas` que en claro se distingue por su relleno lleva en oscuro **borde de 2 dp `line`** (el mismo borde de las tarjetas): opción del Quiz sin elegir, caja de explicación del fallo, caja de comparación de *Say it in Korean*, recuadros `canvas` de PauseSheet. (`quiz_pregunta_opcion_oscuro`, `quiz_opcion_fallo_explicacion_oscuro`: las opciones parecen texto suelto.)

Un solo modificador: `Modifier.softBox()` = fondo `canvas`, radio 12 y, en oscuro, borde 2 dp `line`.

### P-04 · Nombres de archivo

`Korean_Notes_Example_test….` y `Anki_vocabulary_1….` (`library_lista_claro`, `flashcards_mazos_claro`) cortan justo lo que distingue un archivo de otro.

- Nunca se muestra la extensión `.pdf`.
- Hasta **2 líneas**; si no cabe, **elipsis en medio** conservando los últimos 6 caracteres (`Anki_vocabu…lary_2`). Una función `displayName(file, maxChars)` en `Format.kt`, con prueba.
- TalkBack lee el nombre entero.

### P-05 · Estados

| Estado | Regla | Cambios concretos |
|---|---|---|
| **Vacío** | Icono 40 dp, título, una línea y **una** acción | Dashboard sin datos (P-10), Games con 0 rondas (ver `games-characters.md` 8) |
| **Cargando** | Texto + barra tras 300 ms (sin cambio). **Nunca una pantalla atenuada sin nada** | Importar respaldo (P-51) |
| **Error** | `ResultBanner wrong` con qué pasó y qué hacer | Mensajes nuevos de H-09: *Not enough space on the phone.* + *Free up some space and try again.* (subir PDF, importar, exportar); *Games couldn't start this time.* + **Try again** (Gray, Small) |

### P-06 · Microanimaciones con sentido

Solo las que informan de algo. Todas usan los tiempos de `shell.md` (150 / 240 / 400 ms) y se leen en fase de dibujo. Con *Quitar animaciones*: ninguna.

| ID | Dónde | Qué se mueve | Tiempo y curva | Qué comunica |
|---|---|---|---|---|
| M-1 | Barra de progreso del Quiz y de una sesión de tarjetas | el ancho del relleno | 240 ms ease-out | avanzaste una |
| M-2 | Cifra *Correct* del Quiz al acertar | escala 1 → 1.18 → 1 | 240 ms `Spring` | sumó |
| M-3 | Resultados del Quiz: *"{n} / {total}"* | cuenta de 0 a n (un paso por acierto, mínimo 60 ms por paso) | 600 ms máx., ease-out | el resultado se «gana» |
| M-4 | Tarjeta valorada (Flashcards) | sale: x 0 → ±40 dp (izquierda con *Again*, derecha con el resto) y opacidad → 0; la siguiente entra con `Enter` | 150 ms ease-out + 240 ms | la valoración se envió y a qué lado |
| M-5 | KPI del Dashboard, **solo la primera vez por arranque** | cuenta de 0 al valor | 400 ms ease-out | los datos acaban de calcularse |
| M-6 | Celda de hoy del calendario al abrir | el anillo `brand` se dibuja (barrido 0 → 360°) | 400 ms ease-in-out, una vez | dónde estás |
| M-7 | `ResultBanner` al aparecer | y: 8 dp → 0, opacidad 0 → 1 | 240 ms `Spring` | respuesta comprobada |
| M-8 | Sección plegable (P-30) | alto + giro del chevron 0 → 180° | 240 ms ease-in-out | se abre/cierra |

Se quedan como están: `pop`/`shake` de las opciones, el giro de la tarjeta, los iconos de la barra inferior.

---

## 2 · Dashboard

Capturas: `dashboard_vacio_oscuro`, `dashboard_arriba_claro`, `dashboard_medio_claro`, `dashboard_arriba_oscuro`.

### P-10 · Hero vacío con una acción
Con 0 respuestas el hero solo regaña (*Answer some quiz questions first. I can't read your mind.*) y no ofrece nada.
- Sin documentos: debajo del texto, **Go to Library** · 자료실로 (`Go`, Default, ancho completo).
- Con documentos y sin respuestas: **Start a quiz** · 퀴즈 시작 (`Go`, Default, ancho completo): abre Quiz con el formulario.
- El texto no cambia (es el tono del producto).

### P-11 · Hero con datos: chips en vez de una frase de 4 líneas
Hoy: *"mastery 10 % (1 answer) · not reviewed in 25 days · 1 flashcard due · Also: …"* en 4 líneas sobre la foto, y dos botones pequeños con «아니면» en medio.

```
TODAY'S SESSION 오늘의 공부
Study Dumbass!! Obsession!!
FIX THIS NEXT 다음에 고칠 것
-어서/아서                  [! High priority]
[mastery 10 %] [25 days ago] [1 card due]      ← chips
Also: -거야, -거예요, (으)ㄹ 만하다             ← 1 línea, elipsis
┌──────────── PRACTICE THIS 연습하기 ──────────┐  Go, Default, ancho completo
└──────────────────────────────────────────────┘
┌──────────── ASK WHAT TO DO 뭘 할지 묻기 ─────┐  Action, Default, ancho completo
```
- **Patrón** en `ko-title` (18 sp, 800, blanco) con la píldora de prioridad a su derecha (colores `priority-*-on-photo`, sin cambio).
- **Chips:** fondo `on-photo-item`, radio completo, relleno 4 × 10 dp, texto `chip` blanco. Uno por dato, en este orden y solo si existe: *mastery {n} %* · *{n} days ago* (o *never reviewed*) · *{n} card(s) due*. FlowRow, 6 dp entre chips.
- ***Also:*** 12 sp `on-hero-soft`, una línea con elipsis.
- **Botones** apilados, ancho completo, 8 dp entre ellos. Se quita «아니면».
- El relleno del hero pasa de 16 a 20 dp (`space-5`, como dice su token).

### P-12 · Tendencia de los KPI sin dramatismo
`0 words` con **↘ −100 %** en rojo (`dashboard_arriba_claro`) es correcto y alarmista.
- Si el valor de esta semana es 0: en vez de la flecha y el porcentaje, **none yet this week** (`trend`, `muted`), sin color.
- Si la semana pasada fue 0 y esta no: **new this week** (`trend`, `correct-ink`).
- En el resto de casos, como hoy; el texto usa `correct-ink` / `wrong-ink`.

### P-13 · Calendario compacto
- **Hoy:** anillo de 2 dp `brand` **por fuera** de la celda (separado 2 dp) y número en 800. Una celda estudiada de hoy conserva su relleno dentro del anillo. Así «hoy» y «estudiado» no se confunden (`dashboard_medio_claro`: el 15 parece una celda más).
- **Leyenda:** rejilla de 2 × 2, 12 sp `muted`, cuadro de muestra de 12 dp con su símbolo dentro (✓ ~ !). Hoy va en una fila de 4 ilegible.
- **Oscuro:** las celdas usan los `*-fill` nuevos (antes, bloques casi blancos: `dashboard_arriba_oscuro`); número en `fill-ink`, símbolo en `correct-ink` / `fill-warn` / `wrong-ink`.

### P-14 · Gráfica
Las barras de *New flashcards* (todas a 500) tapan la línea de tiempo (`dashboard_medio_claro`).
- Barras: ancho 6 dp, radio superior 3, **opacidad 0.35**, dibujadas detrás.
- Líneas: 2.5 dp, puntos de 4 dp, dibujadas encima.
- El eje izquierdo es el de **minutos** (la línea principal); las barras se escalan a su propio máximo sin eje (su valor sale al tocar el día). La leyenda dice *bars: cards per day*.

---

## 3 · Library

Capturas: `library_lista_claro`, `library_detalles_claro`, `library_vocabulario_claro`, `library_lista_oscuro`.

### P-20 · Un solo primario
*All vocabulary* (amarillo, ancho completo) compite con *Choose files*. Pasa a ser una **fila de navegación** dentro de una tarjeta, debajo del bloque de subida:

`[book-text 24 dp]  All vocabulary 모든 단어            475 words  ›`

Fila de 56 dp, `body-bold`, la cifra en 12 sp `muted`, `chevron-left` girado 180°. Toda la fila es táctil. `Fix` (amarillo) queda solo para *Fix these weaknesses* y *Good*.

### P-21 · Buscador de All vocabulary arriba
Hoy está abajo, pegado a la barra del sistema. Sube bajo la barra superior (`SearchField`, fijo al desplazar), como en Grammar. La lista ocupa el resto.

### P-22 · Tarjeta de documento
```
Korean_Notes_Example_test                    [Ready]  🗑
Uploaded Nov 9, 2026 · 2 pages · 1457 characters
[ DETAILS 자세히 ]              ⟳ Reprocess    ↓ Save PDF
```
- **Borrar:** icono `trash-2` 20 dp `wrong-ink` arriba a la derecha (como `DeckCard`); abre el diálogo de siempre. Desaparece el botón rojo relleno, que pesaba tanto como *Details*.
- **Reprocess** y **Save PDF:** icono 20 dp `control` con su **etiqueta visible** (12 sp, 700, `muted`) a la derecha del icono; zona táctil 48 dp. Hoy son iconos sin nombre.
- Metadatos en una sola línea (12 sp, `muted`); *0 tables* no se muestra cuando es 0.
- Nombre según P-04.

### P-23 · Estado vacío
*No documents yet. Upload a PDF and get to work.* pasa a ser el estado *Vacío* de `shell.md` (icono `file-text`, título **No documents yet** · 아직 문서가 없어요, la línea, sin botón: la acción ya está arriba).

---

## 4 · Flashcards

Capturas: `flashcards_mazos_claro`, `flashcards_edicion_claro`, `flashcards_sesion_reverso_claro`, `flashcards_sesion_escribir_claro`.

### P-25 · DeckCard: el nombre manda
Los contadores (28 sp, tres colores) pesan más que el nombre, que además sale cortado.
- Orden: **nombre** (`section-title`, 2 líneas, P-04) → `Pill neutral` *213 cards* → contadores → última palabra / *Not studied yet.* → **Continue**.
- Contadores: dígito **20 sp** 800 (antes 28), etiqueta `label-caps` 10.5 sp. Colores con `correct-ink` (New), `count-learn` (Study), `wrong-ink` (Due).
- La papelera solo se ve en modo edición.

### P-26 · Modo edición con salida clara
- La ✓ suelta de arriba pasa a **Done** · 완료 (`Action`, Small), en el mismo sitio.
- En edición, los botones *Continue* **se ocultan** (hoy salen grises y parecen rotos) y en su lugar va *Drag to move · tap the name to rename* (12 sp, `muted`).
- El párrafo de ayuda de arriba baja a 2 líneas: *Tap a name to rename. Hold and drag to move or combine decks.*

### P-27 · *Type the answer* en mazos sin palabras
Hoy el aviso *Typing works on word cards.* sale **dentro de la tarjeta**, en el sitio del contenido. En un mazo sin tarjetas de palabra el interruptor queda deshabilitado (pista `disabled-fill`) y el aviso va **bajo su etiqueta** (12 sp, `muted`). La tarjeta muestra su contenido normal.

### P-28 · Valorar en una fila
Los 4 botones en rejilla 2 × 2 ocupan ~150 dp. Pasan a **una fila de 4**, 8 dp entre ellos, cada uno de 64 + 4 dp de alto:

`AGAIN` / `다시` / **10 min** — tres líneas: nombre (12 sp, 700, MAYÚSCULAS), coreano (10.5 sp), intervalo (12 sp, 800).

Colores sin cambio (Danger, Gray, Fix con `on-fix`, Go). El espacio ganado es para la tarjeta.

---

## 5 · Quiz

Capturas: `quiz_formulario_*`, `quiz_pregunta_opcion_oscuro`, `quiz_opcion_*`, `quiz_escrita_*`, `quiz_hablada_*`, `quiz_resultados_oscuro`.

Los colores de acierto/fallo en oscuro están en `design/quiz.md`, sección E (nueva).

### P-30 · Formulario: *Generate quiz* a la vista
Hoy hay 3 pantallas de scroll antes del botón (`quiz_formulario_arriba_claro` → `_medio_`).
- **Question types** y **Difficulty** pasan a ser **secciones plegables**. Cerradas muestran un resumen en una línea: *Question types · 7 of 9 selected* / *Difficulty · 1, 2*, con `chevron-left` girado −90° (M-8).
- Abiertas por defecto **solo la primera vez**; tras generar un quiz se recuerdan cerradas (DataStore, `quizFormCollapsed`).
- **Difficulty** no se muestra si no aplica (sin Nano o sin tipos del modelo marcados): hoy ocupa media pantalla al 50 %.
- **Questions** y **Documents** en la misma fila cuando el ancho lo permite (stepper a la izquierda, campo a la derecha con `weight(1f)`); con la letra al 130 %, apilados.
- **Generate quiz** (`Go`, **Large**) y **Practice saved questions** (`Action`, Default).
- Error *Choose at least one question type.*: abre la sección sola.

### P-31 · Pregunta
- Pie: **Next question** / **Check** / **See results** en **Large**.
- *This quiz* usa `StatStrip plain`; *Correct* en `correct-ink` (hoy `correct` sobre oscuro: 3.0:1).
- Opciones sin elegir: `softBox()` (P-03).
- **Tras fallar una pregunta cuya respuesta correcta es coreano**, y si hay voz coreana sin red: `ReplayButton` pequeño (40 dp, `action`, `volume-2` 20 dp) en la ranura derecha del `ResultBanner`, que dice la respuesta correcta. No suena solo. Sin voz, no se muestra. (Duolingo repite la frase correcta con audio; aquí, a petición.)

### P-32 · *Say it in Korean*
- **El contenido se desplaza** (`verticalScroll`) con relleno inferior = alto del pie: hoy *Listen* queda cortado por *Next question* (`quiz_hablada_respuesta_oscuro`).
- **Show answer** deja de ser texto suelto: StickerButton `Gray`, Small.
- Puntos de nivel a los lados del micrófono: 6 dp (antes ~3), `field` en reposo y `wrong-border` al escuchar; 5 por lado.
- La caja de comparación usa `softBox()`.

### P-33 · Resultados
- M-3 en la cifra. El aviso *"{k} of these was written by AI…"* a 12 sp `muted` en una línea propia con AiMark corto delante (hoy se parte en dos alturas).
- *Your answer:* en `correct-ink` / `wrong-ink`.
- Pie: **New quiz** en Large.

---

## 6 · Teacher y Coach

Capturas: `teacher_vacio_oscuro`, `teacher_respuesta_oscuro`, `coach_respuesta_oscuro`, `teacher_teclado_oscuro`.

- **P-35 · Sugerencias:** tras el primer mensaje, la fila *How do I say…? / What does … mean? / Example with…* se pliega en un botón `lightbulb` (48 dp, `control`) a la izquierda del campo; al tocarlo, la fila vuelve sobre el campo. Así la respuesta gana ~70 dp (`coach_respuesta_oscuro`: la respuesta no cabe).
- **P-36 · Campo y Send:** el campo crece de 1 a 4 líneas; **Send** (`Go`) mide lo mismo que una línea del campo (52 dp con el canto) y queda alineado abajo. Deshabilitado con el campo vacío.
- **P-37 · Coach: primero los datos.** Dentro de la tarjeta del Coach, el orden pasa a: `Pill` *COACH* → bloque de datos (*Fix first*, *Cards due*, botón del quiz) → la frase del modelo con su AiMark. Hoy la frase genérica va primero y empuja los datos fuera de la pantalla.
- **P-38 · Selector *Ask my notes / What should I study?*:** el segmento activo lleva además un `check` de 14 dp delante del texto; el inactivo, no. El estado ya no depende solo de azul contra gris.

---

## 7 · Grammar, Calendar, Settings, MoreSheet

| ID | Pantalla | Captura | Cambio |
|---|---|---|---|
| P-40 | Grammar | `grammar_lista_oscuro` | El chevron de cada tarjeta apunta **abajo** cerrado y **arriba** abierto (`chevron-left` girado −90° / 90°, M-8). Hoy apunta a la izquierda. |
| P-41 | Grammar | `grammar_lista_oscuro` | Filtros sin elegir en oscuro: borde interior 2 dp `line`, como ya dice el token `pill` (no está aplicado). |
| P-42 | Grammar | — | Resumen de la tarjeta cerrada: 2 líneas (sin cambio), pero el patrón en `ko-title` nunca se trunca: baja a 2 líneas. |
| P-45 | Calendar | `calendar_mes_oscuro` | Las dos píldoras *← OCTOBER / DECEMBER →* pasan a una cabecera: `chevron-left` (48 dp) · **2026년 11월** (`section-title`, centrado) · chevron derecho (48 dp; deshabilitado en el mes actual). El nombre del mes en inglés va en el `contentDescription` ("Previous month, October"). |
| P-46 | Calendar | `calendar_mes_oscuro` | Celdas con los `*-fill` oscuros; hoy con el anillo de P-13; leyenda 2 × 2 (P-13). |
| P-47 | Calendar | — | *Days are counted in America/Mexico_City.* sube a 12 sp `muted` **dentro** de la tarjeta, bajo la leyenda, con el icono `clock` de 14 dp. |
| P-50 | Settings | `settings_dialogo_importar_claro` | Diálogo de importar: título **Replace everything?** (16 sp, 700); texto *Everything in the app now is deleted and replaced with the backup. This can't be undone.*; botones **Cancel** (Gray) y **Replace** (Danger). |
| P-51 | Settings | `settings_importando_claro` | Mientras importa: diálogo no cerrable con **Importing your backup…** y LinearProgressIndicator (pista `tint`, barra `brand`). Nunca la pantalla atenuada sin nada. |
| P-52 | Settings | `settings_arriba_claro` | *Version 0.11.0*: centrada, 12 sp `muted`, con 24 dp por encima y 24 dp por debajo. |
| P-55 | MoreSheet | `moresheet_datos_oscuro` | `SheetHeader` con **More** · 더 보기. Filas de 56 dp (ya lo son); relleno vertical de la hoja 8 dp (hoy sobra aire arriba y abajo). |

---

## 8 · Capturas de prueba nuevas (Roborazzi, claro y oscuro)

`ResultBanner` en sus 4 tonos · `QuizOption` en sus 4 estados · `Pill` en sus 5 tonos · `StatStrip` en sus 2 variantes · StickerButton en sus 3 tamaños · DeckCard normal y en edición · DocumentCard · hero vacío y con chips · calendario compacto con hoy estudiado y sin estudiar · fila de valorar · formulario del Quiz plegado.

## 9 · Lo que esta ficha deja igual a propósito

- Texto blanco sobre `go` (2.1:1, valor de origen): cambiarlo es cambiar el estilo del botón.
- El tono de los textos (*Study Dumbass!!*, *That was weak.*).
- *Learned* (Flashcards) frente a *Words learned this week* (Dashboard): es H-03 (modelo de palabra), no un problema de diseño; ver `docs/FASE_10_PLAN.md`, «Para después».
