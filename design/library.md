# Ficha: Library (Fase 4)

Sin captura propia. Usa los componentes del Design System: Card, StickerButton, Feedback, States y ProgressBar. Base: `design/shell.md`. Pantalla de 411 × 905 dp.

## Estructura (destino Library)

1. **JsTopBar:** Library · 자료실; *Your study PDFs. No notes, no progress.* · *공부용 PDF. 노트가 없으면 발전도 없어.*
2. **Subir PDF:** Card con borde discontinuo de 2 dp `field`, relleno de 24 dp y todo centrado:
   - *Upload a PDF* · PDF 올리기 (`body-bold`);
   - *Digital PDFs only, one or several at once. Scanned PDFs are not supported (no OCR).* (12 sp, `muted`);
   - **Choose files** · 파일 선택 (`Action`), que abre el selector del sistema (SAF, `application/pdf`, varios a la vez). Mientras copia: *Uploading…* · 올리는 중…, deshabilitado.
3. **All vocabulary** · 모든 단어 (`Fix`, de ancho completo): abre la lista de palabras (sección C).
4. **Documentos:** una Card por PDF, la más nueva arriba, con 12 dp entre ellas.

## A · Card de un documento

- **Fila superior:**
  - el nombre del archivo (`body-bold`, `font-hangul`, elipsis) y debajo *Uploaded {fecha}* (12 sp, `muted`);
  - a la derecha, la etiqueta de estado (píldora, 12 sp):

    | Estado | Fondo | Texto |
    |---|---|---|
    | *Queued* | `canvas` | `muted` |
    | *Processing…* | `tint` | `brand-text` |
    | *Ready* | `correct-fill` | `correct` |
    | *Failed* | `wrong-fill` | `wrong` |
    | *Not supported* | `warn-fill` | `fill-warn` |

  - y el botón **Reprocess** (`refresh-cw`, 20 dp, `control`, zona táctil de 48 dp), deshabilitado mientras procesa.
- **Procesando:** ProgressBar con el nombre de la etapa debajo (12 sp, `muted`): *Extracting text · Parsing · Chunking · Indexing*. Si ColorOS cierra la app, al volver sigue desde la última etapa guardada. Mientras procesa, una notificación con el progreso.
- **Ready:** *"{n} pages · {n} tables · {n} characters"* (12 sp, `muted`).
- **Failed / Not supported:** el mensaje del parser en `wrong` (14 sp), los mismos que la web: escaneado, con imágenes, cifrado o corrupto.
- **Avisos:** cada uno en una píldora `warn-fill` con texto `fill-warn` (12 sp).
- **Acciones:**
  - **Details** · 자세히 (Gray, Small), que despliega *What the app extracted* (sección B);
  - **Delete** · 삭제 (`Danger`, Small): diálogo *Delete this document and its extracted text?*, con una casilla *Also delete its flashcards and their review history* (sin marcar por defecto) y los botones **Cancel** (Gray) y **Delete** (`Danger`);
  - a la derecha, **Save PDF** (`download`, 20 dp, `control`): guarda una copia con el selector del sistema (CreateDocument).

## B · What the app extracted (dentro de la Card)

- **Pestañas** (SelectedGroup Gray Small): *Words detected ({n})* · *Grammar detected ({n})* · *Fragments ({n})*.
- **Words:** *Words detected, as the quiz and flashcards use them*; filas con el coreano (700) a la izquierda y el significado (`muted`) a la derecha.
- **Grammar:** *Pattern* / *Particle*, con *Meaning: …* y su ejemplo.
- **Fragments:** cada fragmento con su página (*쪽*). Vacío: *No fragments: your teacher has nothing to read here.*
- **Cargando:** *Loading what the app read…*

## C · All vocabulary (pantalla encima, con flecha atrás)

- **Barra superior pequeña:** *All vocabulary* · 모든 단어.
- **Lista:** LazyColumn de filas de 48 dp con divisor `line`: el coreano a la izquierda (`font-hangul`, 700, `ink`) y el inglés a la derecha (`muted`, alineado a la derecha).
- **Buscador:** fijo abajo, encima del teclado. TextField *Search in Korean or English*, con `search` a la izquierda y `x` para borrar. Filtra mientras escribes.
- **Estados:** *Loading…* · *No vocabulary in your documents yet.* · *Nothing matches "{texto}".* · *Could not load your vocabulary.*

## D · Repetidos (después de procesar)

Un diálogo por cada repetido, *Already in the app* · 이미 있어요, con *(n of m)* si hay varios:

- **Palabra repetida:** *{doc} repeats a word you already study. It stays as one word: its meanings and examples are combined (one flashcard).*
- **Gramática repetida:** *{doc} repeats a grammar point you already study. Your first version stays; the repeated one from this PDF is not saved again.*

Debajo, tres recuadros: *Now in the app* (`canvas`), *In the new PDF* (`tint`) y *Combined* (borde de 2 dp `line`). Botones: **Skip all** · 모두 건너뛰기 (Gray) y **Skip** · 건너뛰기 (`Action`).

## E · Notas desordenadas (fase 6)

Si las reglas no entienden un fragmento (`needs_llm_pairs`), el modelo propone pares *coreano = inglés*, validados como en la web. Solo corre con la app abierta: si sales, se pausa y la Card dice *"Waiting for you to open the app to read messy notes."* (12 sp, `muted`). Los pares que crea el modelo llevan AiMark en *Words detected*.

**Aceptar pares (fase 7):** en *Words detected*, los pares del modelo van agrupados al principio, bajo *Found by AI in messy notes* (`section-title` de 14 sp, con AiMark).
- Cada fila lleva a la derecha **Add** (Gray, Small).
- Arriba del grupo, **Add all** (`Action`, Small).
- **Al añadir:** el par pasa a ser una palabra normal del documento, igual que las de reglas, y la fila pierde AiMark y el botón. Solo se puede añadir si el coreano **y** el inglés aparecen literales en ese fragmento de tus notas: entonces son tuyos y el modelo solo los encontró.
- Un par que no cumple eso no se muestra.
- **Sin aceptar:** no están en Quiz, Flashcards ni Teacher.
- Snackbar *Added {n} words.* con **Undo**.

## Estados de la pantalla

- **Sin documentos:** la Card de subir y debajo *No documents yet. Upload a PDF and get to work.* (14 sp, `muted`).
- **Error:** *Could not load documents.* (sin "Is the backend running?": aquí no hay backend), con *Try again*.
