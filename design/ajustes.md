# Ficha: ajustes del dueño (después de 0.12.0)

Pedidos por el dueño el 2026-11-22. **Esta ficha manda sobre `shell.md`, `dashboard.md`, `library.md`, `flashcards.md`, `quiz.md`, `teacher.md` y `polish.md` donde las contradiga.** Pantalla de 411 × 905 dp. Iconos de Lucide. Labels en Title Case. Ningún color ni token nuevo: solo los de `tokens.json` v2.

El dueño cambia aquí reglas que antes eran fijas: **la barra inferior** (AJ-01) y **el tamaño de *Generate quiz*** (AJ-30, contra P-01/P-30).

Los ID (`AJ-xx`) son los que usan el prompt y los PR.

---

## 1 · Navegación

### AJ-01 · Barra inferior con 7 destinos y desplazamiento horizontal
- Orden: **Dash** `layout-grid` · **Library** `book-text` · **Flashcards** `layers` · **Quiz** `rocket` · **Teacher** `graduation-cap` · **Games** `gamepad-2` · **Grammar** `languages`.
- **Dash** es solo la etiqueta visible. Para TalkBack sigue siendo *Dashboard*.
- **Quiz** cambia de icono: `circle-help` → `rocket`. Se añade `design/icons/rocket.svg`, descargado de Lucide (ISC) y convertido a `ImageVector`, como los demás.
- La barra se desplaza en horizontal: `Row` con `horizontalScroll`, sin `NavigationBar` de ancho repartido. Cada destino mide **76 dp** de ancho. En 411 dp se ven 5 enteros y medio *Games*: ese medio es la pista de que hay más. No lleva flechas ni puntos.
- Al elegir un destino que no está entero a la vista, la barra se desplaza hasta que lo esté (`animateScrollTo`, 240 ms; con *Quitar animaciones*, al instante).
- No cambia nada más: el alto (80 dp + inset), el fondo `action`, las etiquetas `nav-label` blancas, el indicador `nav-active` de 64 × 32 dp, el trazo 2.5 del activo ni la animación del icono.
- **Games** sigue abriendo su pantalla completa, sin barra, encima del destino actual (`Screen.Games`), igual que hoy desde More. Al salir se vuelve al destino que había. En la barra, Games nunca queda marcado como activo.
- **Grammar** pasa a ser un destino de la barra (`TopLevel.Grammar`): sin flecha atrás y con **More** a la derecha de JsTopBar, como Library. El contenido no cambia. Su *Open Library* del estado vacío lleva a Library.

### AJ-02 · MoreSheet
Orden nuevo: **Calendar** `calendar-days` 달력 · **Quiz Guide** `book-open-text` 퀴즈 안내 (nuevo, AJ-40) · divisor · **Dark mode** `moon` + ThemeSwitch · **Settings** `settings` 설정.
Se quitan **Games** y **Grammar**. `book-open-text` entra en `design/icons/` igual que `rocket`.

---

## 2 · Dashboard

### AJ-10 · Sin resumen semanal
- Se quita la caja del resumen semanal (uso 8 del plan, 10.1: *"…keep practicing that grammar point"*, con AiMark *not from your notes*).
- Tampoco se genera: sin esa caja no se le pide nada a Nano. Hay que quitar la llamada y la ruta de `DashboardViewModel` que la pide. Su prueba se borra o se cambia a «no se pide».
- El resto del Dashboard no cambia.

---

## 3 · Library

### AJ-20 · Library como en 0.11.0
- Se deshace el commit `e45d895` (P-20, P-22 y P-23) **en `LibraryScreen.kt`, `DocumentCard.kt` y `LibraryViewModel.kt`**:
  - *All vocabulary* vuelve a ser el botón amarillo (`Fix`) de ancho completo;
  - la tarjeta de documento vuelve a ser la de 0.11.0, con sus botones y su borrar;
  - el estado vacío vuelve a ser la línea *No documents yet. Upload a PDF and get to work.*
- **Se queda:**
  - el enlace *How to write notes: Notes template* bajo esa línea (`library.md`, *Sin documentos*);
  - el buscador arriba en la pantalla *All vocabulary* (P-21, es otra pantalla).
- **Referencia:** las capturas `library_*.png` de `73fb5eb` (0.11.1, misma Library que 0.11.0). Las nuevas deben ser iguales a esas. Lo único distinto permitido es el enlace del estado vacío y las diferencias que traigan componentes comunes cambiados después (Pill, nombres sin `.pdf`): se listan en el PR.

---

## 4 · Flashcards

### AJ-25 · Encabezado
- *Continue flashcards* · 단어 카드 계속하기 → **Decks Created** · 만든 덱.
- A la derecha, dos iconos de 24 dp con zona táctil de 48 dp, en este orden:
  1. **`sliders-horizontal`**: cambia la vista de los mazos entre **cuadrícula** (la de hoy) y **lista** (AJ-27).
     - Color `control`.
     - TalkBack: *Show decks as a list* / *Show decks as a grid*.
     - La vista elegida se recuerda (DataStore, `decksLayout` = `grid` | `list`; por defecto `grid`).
     - Solo cambia cómo se ven: mismos datos y mismas acciones.
  2. **`square-pen`**: el modo edición.
     - Apagado: `control`, TalkBack *Edit decks*.
     - Encendido: **`action`** (azul), TalkBack *Done editing decks* y estado *on*.
     - Se toca otra vez para salir. **Ya no hay botón *Done*.**
- `sliders-horizontal` sigue activo en modo edición.

### AJ-26 · Modo edición
- **Sin *Done*** (AJ-25) y **sin** la línea *Drag to move · tap the name to rename* dentro de cada mazo.
- El párrafo de arriba (*Tap a name to rename. Hold and drag to move or combine decks.*) se queda.
- **Borrar:** en la esquina superior derecha de cada mazo, una cruz **`x`**:
  - 20 dp, `wrong-ink`, zona táctil de 48 dp;
  - TalkBack *Delete {mazo}*;
  - abre el diálogo de siempre.
  - Sustituye a `trash-2`, que desaparece de los mazos.
  - Solo se ve en modo edición.
  - Si el mazo lleva el icono de Teacher, ese icono se corre a la izquierda de la cruz.
- *Continue* se oculta en modo edición, pero **su hueco se mantiene** (invisible): el mazo no cambia de tamaño (AJ-28).
- Arrastrar para mover o combinar y tocar el nombre para renombrar siguen igual, en las dos vistas.

### AJ-27 · Vista de lista
Una fila por mazo, a ancho completo, con el marco de tarjeta de siempre: borde 2 dp `line`, radio 16, relleno 12 dp, 8 dp entre filas. **No lleva la flecha `›`** de la referencia.

```
┌──────────────────────────────────────────────────────────────────┐
│ Anki_vocabulary_1 │ 212 │  0  │  1  │ [ CONTINUE ]     │
│ (213 cards)       │ NEW │STUDY│ DUE │ [  계속하기 ]     │
└──────────────────────────────────────────────────────────────────┘
```

De izquierda a derecha, con divisores verticales de 2 dp `line`:
1. **Nombre** (`body-bold`, una línea, AJ-29) y debajo el `Pill neutral` *{n} cards*. Ocupa el ancho que sobra (`weight(1f)`).
2. **New · Study · Due**:
   - tres columnas de 44 dp;
   - dígito de 20 sp (800) y etiqueta `label-caps` de 10.5 sp;
   - colores de P-25: `correct-ink`, `count-learn` y `wrong-ink`.
3. **Última palabra / *Not studied yet.***: solo si la fila mide **560 dp o más** (tableta, horizontal), con `weight(1f)` y una línea. En el OPPO en vertical (379 dp útiles) no cabe y no se muestra.
4. **Continue** · 계속하기: StickerButton Small (`Action`), con *Nothing due* deshabilitado igual que hoy.

En la vista de lista no se muestra el resumen *New · Due now · Learned · Total* de arriba (tampoco en edición). En la de cuadrícula se queda como hoy.

### AJ-28 · Tamaño fijo del mazo
- Cada mazo tiene un **alto fijo**, en las dos vistas, pase lo que pase: nombre largo, *Nothing due*, edición, letra del sistema.
- **Cuadrícula:** el alto que tiene hoy un mazo con el nombre en una línea. Mídelo en la captura `flashcards_decks` y anótalo aquí: `deck-grid-h = ___ dp`.
- **Lista:** **88 dp**.
- Con la letra al 130 % o más, el alto fijo sube en la misma proporción, para que no se corte nada; sigue siendo igual para todos los mazos.
- Lo que no cabe se recorta con elipsis, nunca hace crecer el mazo.

### AJ-29 · Nombre largo que se desplaza solo
- El nombre del mazo va siempre en **una línea**.
- Si no cabe, se desplaza solo en horizontal, despacio, con `Modifier.basicMarquee` de Compose Foundation (sin dependencias nuevas):
  - velocidad 24 dp/s;
  - espera de 1500 ms antes de la primera pasada y de 2000 ms entre pasadas;
  - repite sin fin mientras esté en pantalla.
- Si cabe, no se mueve.
- Con *Quitar animaciones* (`reduceMotion()`) no se mueve: elipsis en medio (`displayName`, P-04).
- TalkBack lee el nombre entero.

---

## 5 · Quiz

### AJ-30 · *Generate quiz* y *Practice saved questions* del tamaño normal
- Los dos pasan de **Large** a **Default** (44 + 4 dp, texto 13 sp, coreano 11 sp): el tamaño de los botones de *Today's session* del Dashboard.
- Colores sin cambio: `Go` y `Action`.
- Los botones Large del pie de las pantallas de pregunta, resultados y Games no cambian.

### AJ-31 · Questions y Documents en una sola fila
- **Stepper:**
  - botones − y + de **40 dp** de ancho (antes 48), siguen Small;
  - el número a 20 sp (antes 24);
  - **se quita el texto *1 to …***;
  - TalkBack sigue diciendo *Questions: {n}*.
- **Documents:** el campo va en **una línea**, sin partirse:
  - *All documents*, el nombre de un documento con elipsis en medio (`displayName`), o *{n} documents*;
  - alto fijo de 48 dp.
- Con la letra al 130 % o más siguen apilados, como hoy (P-30).
- Si con esto Questions y Documents aún no caben en una fila a 100 % de letra, la etiqueta *Questions* · 문제 수 va encima del stepper y el stepper se queda a la izquierda del campo. Nada salta de línea al elegir un documento.

### AJ-32 · Hasta 25 preguntas
- El rango del stepper pasa de **1–10** a **1–25**.
- Comprobar que el generador por reglas y las preguntas del modelo (`AI_MAX_QUESTIONS` = 3, no cambia) aceptan 25 sin otro tope.
- Una preferencia guardada mayor de 25 se lee como 25.
- Si las notas no dan para 25 preguntas, pasa lo de hoy: salen las que haya.

---

## 6 · Teacher

### AJ-35 · Sin la línea de documentos
Se quita *Answers come from all your documents.* bajo el composer, también de la regla del teclado (`teacher.md` 6). El selector *Ask my notes / What should I study?* no cambia (lo decide el dueño más adelante).

---

## 7 · Quiz Guide (nuevo, desde More)

### AJ-40 · Pantalla
- Se abre encima del destino actual, con flecha atrás, como Calendar (`MoreDestination.QuizGuide`).
- **JsTopBar:** Quiz Guide · 퀴즈 안내; *What each question type asks and what Difficulty changes.* (sin subtítulo coreano).
- **Contenido** en un `LazyColumn` con 12 dp entre bloques:
  1. **Question types** · 문제 유형 (`section-title`). Una Card por tipo, en el orden del formulario. Cada Card lleva:
     - el nombre (`body-bold`) con su coreano (`ko-side`);
     - la pista del formulario (12 sp, `muted`);
     - el texto (14 sp, `onSurface`);
     - debajo, un `Pill`: *By rules* (`neutral`), *On-device AI* (`info`) o *Practice · not scored* (`warn`).
  2. **Difficulty** · 난이도 (`section-title`): una Card con el texto general y luego los 3 niveles. Cada nivel lleva el nombre (`body-bold`) con su coreano y su texto (14 sp).
- Es solo texto: sin botones ni estados de carga.
- Los textos viven en un solo sitio (`QuizGuide.kt` junto a `QUIZ_TYPES` y `LEVELS`), para que formulario y guía no se separen.

### AJ-41 · Textos (en inglés; adaptados de la web a lo que hace la app Android hoy)

| Tipo | Pill | Texto |
|---|---|---|
| Korean → English · 한국어 → 영어 | By rules | Shows a Korean word from your notes; you type its English meaning. Made by rules from your word pairs, so Difficulty doesn't change it. With On-device AI ready, an answer that means the same in other words also counts. |
| Write in Korean · 한국어로 쓰기 | By rules | Shows the English meaning; you type the Korean. Words or sentences; spaces and punctuation don't count. Made by rules, so Difficulty doesn't change it. |
| Vocabulary recognition · 어휘 알아보기 | By rules | Shows a Korean word; you pick its meaning from 4 options taken from other words of the same document. Made by rules, so Difficulty doesn't change it. |
| English → Korean · 영어 → 한국어 | By rules | Shows an English meaning; you pick the Korean word from 4 options of the same document. Made by rules, so Difficulty doesn't change it. |
| Fill in the blank · 빈칸 채우기 | By rules | A Korean sentence from your notes with a gap; you pick what goes in it. Made by rules from your sentences, so Difficulty doesn't change it. |
| Multiple choice · 객관식 | By rules | A question about your notes with 4 options. Your grammar points become rule-based questions. With On-device AI ready, the model also writes a few (at most 3 per quiz), and only those follow Difficulty. |
| Grammar selection · 문법 고르기 | By rules | Shows what a grammar pattern means; you pick the pattern, e.g. -(으)니까. Made from the grammar points in your notes (their *Anki options*, if they have them). Difficulty doesn't change it. |
| Short answer · 단답형 | On-device AI | An open question about your notes that you answer in English. Written by the model, so it needs On-device AI ready and follows Difficulty. |
| Say it in Korean · 한국어로 말하기 | Practice · not scored | You say a Korean word or sentence from your notes out loud; the app shows what it heard next to your notes. Practice only: it never counts in your score. Needs the microphone; audio and what you said are never saved. |

**Difficulty** · 난이도 — texto general: *Difficulty only changes questions written by the model: Multiple choice (the model's ones) and Short answer, and only when On-device AI is ready. Pick one or more levels; with several, they take turns. Everything else is made by rules from your notes and stays the same.*

| Nivel | Texto |
|---|---|
| 1 · Recall · 기억하기 | Remember one item: what a word means, or the key word missing from a sentence. |
| 2 · Similar options · 비슷한 선택지 | Choose between look-alike options (e.g. 춥다 / 차갑다 / 시원하다), so you need to know the exact one. |
| 3 · Apply a rule · 규칙 적용 | Use a grammar rule: the gap is a particle or an ending (e.g. 오니까 vs 와서), or the answer isn't written in your notes and you build it. |

**Antes de copiar estos textos**, Code comprueba cada frase contra el código. Si algo no es verdad hoy en Android, **para y lo dice; no corrige el texto por su cuenta**. Por ejemplo:
- que la calificación por significado de *Korean → English* use Nano;
- el tope de 3 preguntas del modelo;
- cómo hace *Fill in the blank* las reglas (R-017);
- que *Difficulty* solo aplique a *Multiple choice* y *Short answer* (`levelsCount`).
