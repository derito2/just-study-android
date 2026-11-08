# Ficha: Flashcards (Fase 3)

**Capturas:**
- `design/png/flashcards-decks.png`: mazos;
- `design/png/flashcards-edit.png`: modo edición con el diálogo de combinar;
- `design/png/flashcards-front.png`: anverso;
- `design/png/flashcards-back.png`: reverso y valoración;
- `design/png/flashcards-typed.png`: Type the answer.

Base: `design/shell.md`. Valores en `design/tokens.json`. Pantalla de 411 × 905 dp.

## A · Mazos (destino Flashcards)

1. **JsTopBar:** Flashcards · 단어 카드; *Words from your own notes. Review them before you forget them, again.* · *네 노트에서 나온 단어. 또 잊어버리기 전에 복습해.*
2. **Resumen:** cuatro Card en una fila, con 8 dp entre ellas y relleno de 10 × 4 dp. Etiqueta `label-caps` de 11 sp en `muted` y valor de 24 sp (800): *New · Due now · Learned · Total*.
3. **Encabezado:** *Continue flashcards* · 단어 카드 계속하기 (`section-title`) y, a la derecha, el lápiz `square-pen` (24 dp, `control`, zona táctil de 48 dp, `aria-label` *Edit decks*). En modo edición, el lápiz pasa a `check` (*Done editing decks*).
4. **Mazos:** cuadrícula de **2 columnas** de 183 dp, con 12 dp entre ellas. Es el componente DeckCard del sistema:
   - **Card:** relleno de 12 dp y 8 dp entre partes, todo centrado.
   - **Fila superior:**
     - a la izquierda, la papelera `trash-2` (20 dp, `danger`, zona táctil de 48 dp, *Delete {mazo}*);
     - a la derecha, el icono de Teacher si el mazo lo creó el Coach (`graduation-cap` de 16 dp en un círculo `tint` de 24 dp, color `brand-text`, *Made by Teacher*);
     - y `badge-canvas` con *"{n} cards"*.
   - **Nombre:** `body-bold`, una línea y elipsis.
   - **Contadores:** tres columnas separadas por un divisor de 2 dp `line`. Número de 20 sp (800) y etiqueta `label-caps` de 11 sp en `muted`:
     - New: `count-new`;
     - Study: `fix`;
     - Due: `wrong`.
   - **Palabra:** la última palabra estudiada o la siguiente que toca, en coreano, a 24 sp (800), una línea. **Sin traducción.** Si el mazo no se ha estudiado: *Not studied yet.* (14 sp, `muted`).
   - **Botón:** StickerButton Small de ancho completo, **Continue** · 계속하기. Sin tarjetas para hoy: *Nothing due* · 복습 없음, deshabilitado.
5. **Debajo de los mazos:**
   - *Create flashcards* · 단어 카드 만들기: Card con el selector *Documents* (que abre una hoja con casillas, como en Quiz), **Create cards** · 카드 만들기 (`Go`) y **Settings** · 설정 (Gray, Small), que abre las opciones de repaso en una hoja.
   - **Historial de repasos** (*Review history* · 복습 기록). Vacío: *No reviews yet. Open a deck.*
6. **Estados:**
   - sin tarjetas: EmptyState con `layers`, *You don't have any cards yet* · 아직 카드가 없어요, *Create cards from a processed document and its deck will show up here.* y **Create cards** (`Go`);
   - cargando: *Loading decks…*;
   - error: el recuadro de error.
7. **Borrar:** la papelera abre un diálogo: *Delete "{mazo}"? Its cards and their progress are deleted too.*, con **Cancel** · 취소 (Gray) y **Yes, delete** · 삭제 (`Danger`). Después, Snackbar *"Deleted {n} cards from {mazo}."* con **Undo**, que restaura las tarjetas y su progreso.

## B · Modo edición

- **Texto bajo el encabezado** (14 sp, `muted`): *Tap a deck's name to rename it. Hold and drag a deck to move it, or drop it on another deck to combine them into one. Your cards and their progress don't change.*
- **Cada mazo:**
  - vibra ±0.6° en un bucle de 280 ms; los alternos empiezan 140 ms después;
  - el nombre se vuelve un campo con una línea inferior de 1 dp `field` (Enter guarda; un nombre vacío vuelve al anterior; máximo 120 caracteres);
  - Continue queda deshabilitado.
- **Arrastrar:** mantener pulsado inicia el arrastre. El mazo arrastrado queda al 50 % de opacidad.
  - **Sobre otro mazo:** aro de 4 dp `brand`; soltarlo abre el diálogo de combinar.
  - **Entre dos mazos:** una línea de 4 dp `brand` en el borde; soltarlo los reordena.
- **Diálogo de combinar** (scrim `scrim`, Card con relleno de 24 dp, centrado):
  - título *Combine into one deck* · 합치기 y debajo los dos nombres (`muted`);
  - campo *Name of the new deck*, obligatorio;
  - **Cancel** · 취소 (Gray) y **Combine** · 합치기 (`Action`), este deshabilitado sin nombre.
  - Al terminar, el mazo arrastrado "vuela" hacia el destino (scale 0.6 + fade, 240 ms) y sale el Snackbar *Combined into "{nombre}".*
- **Reducir movimiento:** sin vibración ni vuelo.

## C · Sesión de repaso (pantalla encima, sin barra inferior)

- **Barra superior pequeña:**
  - `chevron-left` (*Back to decks*) y el nombre del mazo (16 sp, 700, elipsis);
  - a la derecha, *"{n} left"* (12 sp, `muted`).
- **Opciones de la sesión:**
  - una fila de FilterPill que se desplaza en horizontal: *Korean → English · English → Korean · Both*, guardada en `UserPrefs.cardDirection`;
  - debajo, una fila de 48 dp: *Type the answer* · 답 입력하기 con ThemeSwitch a la derecha, guardado en `UserPrefs.typeAnswer`; solo funciona en tarjetas de palabra.
- **Anverso:**
  - Card que ocupa el espacio libre (mínimo 256 dp), con relleno de 32 dp y todo centrado;
  - etiqueta *NEW* (`badge-tint`, MAYÚSCULAS) si es nueva;
  - la palabra (`ko-hero`, 30 sp, 700), o la traducción en inglés si la dirección es English → Korean;
  - con *Type the answer*, un TextField centrado (máximo 320 dp, *Type the meaning* / *Type it in Korean*; Enter muestra la respuesta).
- **Pie:** **Show answer** · 정답 보기, de ancho completo (`Action`).
- **Reverso:**
  - Card con relleno de 24 dp:
    - el coreano (20 sp, 700);
    - la romanización (14 sp, `muted`), si existe;
    - la traducción (20 sp, 800);
    - *Source: {documento}, page {n}* (12 sp, `muted`).
  - Debajo, la Card **From your notes** · 내 노트에서 con las secciones que existan: *Structure*, *Explanation*, *Examples* y *Notes*. Etiqueta `label-caps` en `muted`, el coreano con `font-hangul` y su traducción en `muted`.
- **Valoración:**
  - cuadrícula de 2 × 2 en el pie, con 8 × 12 dp entre botones, sobre el inset del sistema;
  - **Again** 다시 (`Danger`) · **Hard** 어려움 (Gray) · **Good** 알맞음 (`Fix`) · **Easy** 쉬움 (`Go`);
  - cada botón muestra en una tercera línea el intervalo que daría (12 sp, 400, sin mayúsculas), con el formato de `scheduler.label`: `10 min`, `12 h`, `24 h`, `2 d`, `3 mo`, `1 y`;
  - `aria-label` *"{Rating}, next review in {intervalo}"*.
- **Type the answer**, en el reverso, bajo un divisor de 2 dp `line`:
  - El veredicto (16 sp, 700):
    - *Exactly right* y *Right meaning, different words*: `correct`, con `check`;
    - *Almost*: `fill-warn`;
    - *Not quite*: `wrong`, con `x`;
    - *You didn't type an answer*: `muted`.
  - *Your answer:* letra a letra (20 sp, 700):
    - bien: `correct`;
    - mal: fondo `wrong-fill`, color `wrong` y subrayado de 2 dp;
    - de más: fondo `wrong-fill`, color `wrong` y tachada;
    - falta: subrayado discontinuo de 2 dp `wrong`, color `muted`.
  - Debajo, un resumen en 12 sp `muted`: *"1 wrong letter: 간 → 관"*.
- **Giro de la tarjeta:** 400 ms ease-out, en Y, con la cámara a 1000 px. Con *reducir movimiento*, cambio de cara sin girar.
- **Al valorar:** la siguiente tarjeta entra subiendo 8 dp (240 ms).
- **Sin tarjetas pendientes:** EmptyState *No cards due in this deck. Don't get comfortable.* y **Back to decks** · 덱으로 돌아가기.
- **Back del sistema:** vuelve a los mazos sin perder lo valorado (cada valoración se guarda al instante).

## Datos

Lo mismo que la web: tarjetas por documento, SM-2 de `scheduler.py` (pruebas doradas en `golden/sm2.json`), `compareAnswer` (`golden/compare_answer.json`) y las opciones de repaso (Again en minutos; Hard, Good y Easy en horas). Todo en `:core:korean` y `:core:data`; la pantalla solo lee `StateFlow`.
