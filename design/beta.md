# Ficha: BETA y funciones de estudio (Fase 11)

Base: `design/shell.md`, `design/polish.md` y `design/ajustes.md`. Pantalla de 411 × 905 dp. **Manda sobre las demás fichas donde las contradiga.**

**Reglas:**
- Solo componentes que ya existen: StickerButton, Card, Pill, ResultBanner, TypedResult, HangulKeys, QuizOption, StatStrip, SheetHeader, JsTopBar, States y AiMark.
- Ningún color ni token fuera de `tokens.json` v2. Iconos de Lucide. Labels en Title Case, con su coreano debajo como en el resto de la app.
- Todas las pantallas nuevas se abren **encima del destino actual, sin barra inferior y con flecha atrás**, como Calendar.
- Las cuatro llevan el estado vacío, el de carga y el de error de `shell.md`.
- Con *Quitar animaciones*, nada se mueve. El teléfono no vibra.

---

## 1 · Entrada: More y la pantalla BETA

**More** gana una fila, entre *Quiz Guide* y el divisor: **Beta** `flask-conical` · 베타.

**Pantalla BETA**
- **JsTopBar:** Beta · 베타; *New ways to study. They may still change.* (sin subtítulo coreano).
- Una Card por función, en este orden, con 12 dp entre ellas. Cada Card lleva:
  - el icono (24 dp, `control`);
  - el nombre (`body-bold`) con su coreano (`ko-side`);
  - una línea de 12 sp en `muted`;
  - **Open** · 열기 (StickerButton `Action`, Default), a la derecha.

| Función | Icono | Coreano | Línea |
|---|---|---|---|
| **Listening Lab** | `headphones` | 듣기 연습 | *Dictation, shadowing and hands-free review with your sentences.* |
| **Grammar Lab** | `dumbbell` | 문법 연습 | *Conjugate your verbs with your grammar points.* |
| **My Mistakes** | `notebook-pen` | 내 실수 | *Your mistakes grouped into patterns, ready to practice.* |
| **Reader** | `book-open` | 읽기 | *Read your PDFs and tap a word to see your notes.* |

- **Debajo de las Cards** (12 sp, `muted`): *Time here doesn't count toward your streak.* (D11-2).
- **PR 3:** cada *Open* abre un estado vacío con `flask-conical`: *Coming soon* · 준비 중. Cada PR siguiente sustituye el suyo.

---

## 2 · Sesión de Flashcards (F2)

### 2.1 · Interruptores
Bajo la fila de *Type the answer*, que no cambia, va **una fila nueva de 48 dp partida en dos mitades iguales**. Cada mitad es como *Type the answer*: el nombre a la izquierda y el ThemeSwitch a la derecha, y toda la mitad es táctil.
- **Fill the Gap** · 빈칸
- **Listen Only** · 듣기

Funcionamiento:
- Los dos empiezan apagados. Con los dos apagados, la sesión es igual que hoy.
- Se recuerdan en DataStore, como *Type the answer*.
- *Listen Only* queda deshabilitado (al 50 %, como *Type the answer* bloqueado) si no hay voz coreana. Debajo, el aviso *Needs the phone's Korean voice.* (12 sp, `muted`).
- *Fill the Gap* no se bloquea: si una tarjeta no tiene ejemplo con su palabra, esa tarjeta sale clásica.
- Con los dos encendidos, la app alterna por tarjeta (`modeFor`), siempre entre los modos que esa tarjeta admite.

### 2.2 · Tarjeta Fill the Gap
- **Frente:**
  - `Pill info` *FILL THE GAP* arriba;
  - uno de tus ejemplos con un hueco `____` (subrayado de 2 dp `line`, ancho de 4 caracteres), con `font-hangul`, 22 sp y 700;
  - debajo, su traducción (14 sp, `muted`). Si el ejemplo no tiene traducción, en ese sitio va *Missing word: {significado de la tarjeta}* (14 sp, todo en `muted`). Si el mismo ejemplo está con y sin traducción, gana el que la tiene. La regla del hueco no cambia (solo un eojeol seguro; si no, la tarjeta sale clásica). El dorso no cambia.
  - Con *Type the answer*, el campo de siempre, donde escribes lo que falta.
- **Dorso:**
  - la palabra (como hoy) y la frase completa con la palabra resaltada en `brand-text`, 700;
  - 🔊 de la frase;
  - lo demás del dorso de hoy (significado, notas y fuente) debajo.
  - Con *Type the answer*, TypedResult contra la forma que va en el hueco.

### 2.3 · Tarjeta Listen Only
- **Frente:**
  - `Pill info` *LISTEN*;
  - en el centro, un botón redondo de 72 dp (`action`, `volume-2` de 32 dp, blanco) que suena solo al aparecer la tarjeta y otra vez al tocarlo;
  - *Tap to hear it again.* (12 sp, `muted`).
  - Sin texto coreano en el frente.
  - Con *Type the answer*, escribes lo que oyes (TypedResult en modo coreano).
- **Dorso:** el de hoy, con 🔊.

### 2.4 · 🔊 en todas las tarjetas
- En la cara que lleva el coreano (en cualquier modo): `ReplayButton` pequeño (40 dp, `volume-2` de 20 dp, `action`), arriba a la derecha de la tarjeta, con zona táctil de 48 dp.
- Solo aparece con la voz lista. Con el volumen a 0: *Turn the volume up to hear it.* (el texto de Games).
- Nunca suena solo, salvo el frente de *Listen Only*.

---

## 3 · Grammar Lab (F3)

- **JsTopBar:** Grammar Lab · 문법 연습; *Make the forms yourself with your own verbs.*
- **Cabecera** (Card):
  - selector de punto: un campo como *Documents*, que abre una hoja con tus puntos reconocidos y *All grammar* arriba;
  - **Level** con SelectedGroup Small *1 · 2 · 3*;
  - abajo, 12 sp `muted`: *{n} of your grammar points can be practiced here.*
- **Ejercicio** (Card):
  - enunciado: el verbo de tus notas y su significado (*먹다 · to eat*) y la terminación (`Pill info` *-아서/어서*);
  - **Choose:** 3 o 4 `QuizOption` con las formas;
  - **Produce:** campo de una línea más el teclado. Por defecto `HangulKeys`; en la cabecera, un `keyboard` (24 dp, `control`) cambia al teclado del sistema y se recuerda;
  - **FillExample:** tu ejemplo con `____` y su traducción, y el campo debajo;
  - pie: **Check** · 확인 (`Go`, Large), como en el Quiz. Con el teclado Hangul visible, **Check es su propia tecla** y el botón grande del pie no sale (solo sale con el teclado del sistema).
- **Resultado:**
  - ResultBanner (acierto o fallo) + TypedResult;
  - debajo, la **explicación por reglas** en un `softBox()`: *먹 ends in ㅓ, so -어서: 먹 + 어서 → 먹어서*;
  - con Nano, *Explain more* (Small, `Gray`), que añade la explicación con AiMark;
  - pie: **Next** · 다음 (`Go`, Large).
- **Resumen** a los 10 ejercicios:
  - StatStrip `plain` (*Done · Correct · Missed*);
  - una fila por terminación con aciertos/total;
  - **Practice the ones I missed** (`Action`, Default) y **Done** · 완료 (`Gray`, Default).
- **Vacío:** `dumbbell`, *Nothing to practice yet*, *Add grammar points with a Structure line (e.g. "- A/V + 아서") and some verbs to your notes.*, y **Open Library** (`Action`).

---

## 4 · Listening Lab (F4)

- **JsTopBar:** Listening Lab · 듣기 연습; *Train your ear with your own sentences.*
- **Modo:** SelectedGroup Small a ancho completo: *Dictation · Shadowing · Hands-free*. Se recuerda.
- **Velocidad**, en Dictation y Hands-free: SelectedGroup Small *0.7× · 1× · 1.2×*. Se recuerda.

**Dictation**
- Card con el botón redondo de 72 dp de §2.3 y, al lado, **Replay** y **Slower** (Small, `Gray`, iconos `rotate-ccw` y `turtle`).
- Debajo, el campo de una línea y `HangulKeys` (o el teclado del sistema, igual que en Grammar Lab).
- Pie: **Check** (`Go`, Large).
- Resultado: ResultBanner + TypedResult, y vuelve a sonar la frase. Debajo, la traducción. Pie: **Next**.

**Shadowing**
- La frase en coreano (22 sp) y su traducción.
- El bloque de micrófono del Quiz, sacado a un componente común **sin cambiar cómo se ve**: botón de 96 dp, puntos de nivel y comparación lo oído frente a tus notas.
- Sin nota. Si coincide: ResultBanner `correct` *Matches your notes.*; si no, se puede repetir sin límite.
- Pie: **Next**.

**Hands-free**
- Pantalla grande y simple: el coreano (28 sp) y, al decirse el inglés, su significado (18 sp, `muted`).
- Dos botones Large: **Pause / Resume** (`Action`) y **Next** (`Gray`).
- Arriba: *{n} cards due · not rated here* (12 sp, `muted`).
- La pantalla no se apaga mientras dura. Se pausa al salir o bloquear.

**Vacío:**
- sin frases: *Add sentences with a translation to your notes.*;
- sin voz: *Needs the phone's Korean voice.* (el estado de error de la voz que ya usa Games).

---

## 5 · Reader (F8)

- **Elegir documento:** una lista de tus documentos *Ready* (fila de 56 dp con `file-text`, nombre con `displayName` y páginas en `muted`).
- **JsTopBar:** Reader · 읽기, con el nombre del documento de subtítulo.
- **Texto:**
  - `LazyColumn` por página;
  - cabecera de página *Page {n}* (12 sp, `muted`, con un divisor de 2 dp `line`);
  - los títulos en `section-title`; el resto, a 16 sp, interlineado 1.5, `font-hangul`;
  - las palabras coreanas que se pueden tocar no cambian de color ni se subrayan. Al tocar una, se resalta un instante con fondo `tint` y radio 4.
- **Hoja al tocar una palabra** (`SheetHeader` y la hoja de siempre):
  - lo encontrado (`Gloss`): la palabra y su forma base si iba conjugada (*먹었어요 → 먹다*), la partícula si la llevaba (`Pill neutral` *에서*), el significado de tus notas, 1 o 2 ejemplos y la fuente;
  - 🔊;
  - **Add to deck** · 덱에 추가 (`Action`, Default), que abre la lista de mazos con *New deck* arriba;
  - si no está en tus notas: *Not in your notes.* y las frases donde aparece (como Teacher).
- **Pulsación larga en una línea:** *Explain this sentence* (con Nano y AiMark; sin Nano, la lista de palabras con su significado), *Hear sentence* y *Copy*.
- **Accesibilidad:** TalkBack lee la línea entera, y cada palabra es un enlace con su descripción.
- **Notas de implementación (PR 8, #73):**
  - el resaltado al tocar es el `pressedStyle` del enlace (fondo `tint`) y **no lleva radio 4**: el estilo de un enlace dentro del texto no admite esquinas;
  - una **partícula sola** (`(으)로`, `이/가`…) no se busca como punto de gramática: se quita de la palabra antes de buscar (학교에서 → 학교 + `에서`).

---

## 6 · My Mistakes (F5)

- **JsTopBar:** My Mistakes · 내 실수; *Where you keep slipping, from every part of the app.*
- **Periodo:** SelectedGroup Small *7 days · 30 days · All*.
- **Una Card por patrón**, de más a menos frecuente:
  - título (`body-bold`), por ejemplo *은/는 after a consonant*, y a la derecha `Pill wrong` *×{n}*;
  - hasta 3 ejemplos tuyos: TypedResult pequeño (*Expected* frente a *Yours*) con la fuente en 12 sp `muted` (*Quiz · Grammar Lab · Games…*);
  - **Practice this** · 연습하기 (`Action`, Default). Según el patrón abre:
    - **Grammar Lab**, con ese punto o esos verbos: partícula, armonía vocálica, contracción, irregular o tiempo;
    - **el dictado de Listening Lab**, con esas palabras: 받침 o vocal;
    - **un quiz** con esas palabras (`QuizProposal`) o **un mazo nuevo** *Mistakes · {fecha}*: palabra equivocada o significado. En este caso, dos botones: **Quiz me** y **Make a deck**.
- **Vacío:** `notebook-pen`, *No mistakes yet in this period.*, *Do a quiz or a few games and come back.*
- **Sin resumen semanal y sin nada en el Dashboard.**
- **Notas de implementación (PR 9, #75):**
  - los ejemplos usan `TypedResult` a su **tamaño normal** (no tiene variante pequeña); no se ha creado otra;
  - **sin «Yours»** cuando la fuente solo guarda acierto o fallo (*Again* de Flashcards, rondas perdidas de Games, dictados de Listening Lab): el ejemplo muestra solo *Expected* y la fuente. Por eso un dictado fallado cuenta como *Words you mix up*, y el dictado de Listening Lab lo abren los patrones de 받침 y vocal, que vienen de respuestas escritas;
  - el dictado de *Practice this* recibe las palabras del patrón (`Screen.Listening(keys)`) y usa solo las frases que las contienen; si ninguna, *None of these words has a sentence to dictate.* con Back.

---

## 7 · Juegos nuevos (F7)

Usan la pantalla de Games tal cual: escenario, cinta, pausa, resumen, Beom, Deung y Dal. Cada juego solo añade su tablero, con los colores del grupo `games`.

| Juego | Coreano | Tablero |
|---|---|---|
| **Hangul Wordle** | 한글 워들 | Rejilla de jamo: 6 filas × el largo de la palabra; casillas de 44 dp, radio 8. Arriba, el significado. Debajo, `HangulKeys` en modo jamo. Bien = `correct-fill` con ✓ pequeño; en otra posición = `warn-fill` con •; no está = `canvas` con borde `line`. Con símbolo además del color. |
| **Crossword** | 십자말풀이 | Cuadrícula de sílabas de hasta 7 × 7, dibujada en un solo `Canvas`; casilla de 44 dp como mínimo, con el número de la pista arriba a la izquierda. Pistas numeradas debajo, *Across / Down*. Al tocar una casilla, `HangulKeys`. |
| **Conjugation Runner** | 활용 달리기 | Arriba, el verbo y la terminación (*먹다 · -아요/어요*). Caen 3 o 4 formas como QuizOption anchas. Con *Quitar animaciones* no caen: un contador de 5 s. Abajo, *{n}/10*. |
| **Speak It** | 말해 보기 | Arriba, el inglés. En el centro, el bloque de micrófono común (§4 Shadowing). Si coincide, se celebra y suena la palabra; si no, se ve lo oído frente a lo de tus notas, con **Try again** y **Skip** (Small). Nunca dice «Not quite». |

**Cambios del dueño en el PR 10 (#76):**
- **Crossword:** casillas de 44 dp hasta 7 × 7, sin encogerlas. Cerrado: la cuadrícula y todas las pistas (*Across | Down*, lado a lado). Al tocar una casilla o una pista aparece `HangulKeys` y, encima, solo la pista de esa palabra con *Done* y *Check*; el teclado no lleva la fila de espacio (un crucigrama no tiene espacios). Se cierra con *Done*, al completar la palabra o al tocar fuera.
- **Hangul Wordle:** el significado va siempre arriba; *Starts with…* deja de verse y es el tip (solo nivel 1). Con la fila llena, el teclado no admite más jamo (el toque no hace nada, sin vibrar).
- **Todos los juegos:** sin la línea de abajo con el documento de la palabra (ni *Extra · not from your notes*).
- **Tips:** ver `design/games.md` 6.3.

---

## 8 · Daily Reminder (F1, último PR)

En **Settings**, un grupo nuevo *Study* (con su divisor), entre *Appearance* y *Backup*, con una fila:

- **Daily Reminder** `bell` · *A quiet reminder with your cards due.*
- A la derecha, un ThemeSwitch con la etiqueta de accesibilidad *Daily Reminder* (TalkBack no dice solo «Switch»).
- Encendido, debajo de la línea, la hora *8:00 PM* en `action` en claro y en `brandText` en oscuro (`action` es un color de relleno, igual en los dos temas, y no se lee sobre el fondo oscuro; PR 12), táctil, que abre el `TimePicker` de Material 3 en un diálogo (*Cancel* / *OK*).
- **Al encenderlo por primera vez**, si falta el permiso de notificaciones, Android lo pide. Si lo niegas, el interruptor vuelve a apagado y debajo sale *Notifications are off for this app.* (12 sp, `wrong-ink`).

**Notificación:**
- título *Just Study 한국어*;
- texto **solo con cifras**: *12 cards due · 8-day streak* (o *8-day streak — keep it going* si no hay pendientes y aún no estudiaste hoy);
- icono `ic_stat_processing` (el que ya existe);
- sin vibración, `VISIBILITY_PRIVATE`;
- al tocarla abre la sesión de *All decks*;
- si no hay pendientes y ya estudiaste hoy, no sale.
