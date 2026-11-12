# Ficha: Quiz (Fase 3)

**Capturas:**
- `design/png/quiz-home.png`: lo que se ve al abrir;
- `design/png/quiz-home-full.png`: la página entera;
- `design/png/quiz-question.png`: pregunta de opciones;
- `design/png/quiz-wrong.png`: respuesta escrita incorrecta;
- `design/png/quiz-results.png`: resultados.

Base: `design/shell.md`. Valores en `design/tokens.json`. Pantalla de 411 × 905 dp.

## A · Inicio del quiz (destino Quiz)

1. **JsTopBar:** Quiz · 퀴즈; *Questions built only from your own notes. No excuses if you fail.* · *네 노트로만 만든 문제. 틀려도 변명은 없어.*
2. **New quiz** · 새 퀴즈: Card con relleno de 16 dp y 16 dp entre bloques.
   - **Questions** · 문제 수: un selector con − y + (StickerButton Gray Small de 48 dp de ancho, iconos `minus` y `plus` de 20 dp) y el número en medio (24 sp, 800). Va de 1 a 10 (5 por defecto), con *1 to 10* en 12 sp `muted`. En 1 se deshabilita −, y en 10, +.
   - **Documents** · 문서: un campo con el texto *All documents* y `chevron-left` girado −90°. Abre una hoja con casillas, una por documento procesado, más *All documents*.
   - **Question types** · 문제 유형: casillas en **2 columnas**. Cada una lleva la casilla de 18 dp (marcada: `action` con `check` blanco; sin marcar: borde de 2 dp `control`), el nombre (`body-bold`), el coreano (11 sp) y la pista (12 sp, `muted`). Las 8 opciones, en este orden:
     - *Korean → English* · 한국어 → 영어 · *Type the meaning*
     - *Write in Korean* · 한국어로 쓰기 · *Type the Korean*
     - *Vocabulary recognition* · 어휘 알아보기 · *Pick the meaning*
     - *English → Korean* · 영어 → 한국어 · *Pick the Korean word*
     - *Fill in the blank* · 빈칸 채우기 · *Pick what goes in the gap*
     - *Multiple choice* · 객관식 · *Pick one answer*
     - *Grammar selection* · 문법 고르기 · *Pick the correct form*
     - *Short answer* · 단답형 · *Type a short answer*

     Todas marcadas por defecto menos *Short answer*. Toda la fila es la zona táctil (48 dp de alto como mínimo). Sin ninguna marcada: *Choose at least one question type.* en `wrong`, y Generate quiz deshabilitado.
   - **Difficulty** · 난이도: igual, en 2 columnas, con *1 · Recall* 기억하기, *2 · Similar options* 비슷한 선택지 y *3 · Apply a rule* 규칙 적용. Debajo del título, *Only questions written by the model change with it.* (12 sp, `muted`). Solo cuenta si hay marcado algún tipo que escribe el modelo; si no, la sección queda al 50 % de opacidad. Sin ninguna marcada: *Choose at least one difficulty.*
   - **Botones**, de ancho completo y con 8 dp entre ellos: **Generate quiz** · 퀴즈 만들기 (`Go`) y **Practice saved questions** · 저장된 문제 연습 (`Action`). Si no hay preguntas guardadas: Snackbar *You have no saved questions right now.*
   - Las opciones se guardan entre sesiones (DataStore).
3. **Quiz for your weaknesses** · 약점 퀴즈 (WeaknessPanel), sobre la foto `flags.jpg`:
   - el velo `photo-veil-strong`, recortada (Crop) con alineación vertical 0.30, radio 12 y relleno de 16 dp;
   - debajo del título, *Questions you got wrong and haven't fixed yet, from all your quizzes.* (12 sp, `on-hero-soft`);
   - cada error: fondo `on-photo-item`, radio 8, relleno de 8 × 12 dp; el documento en negrita con elipsis y *"{fecha} · {tipo} · wrong {n}×"* (12 sp, `on-hero-soft`). Nunca la pregunta ni la respuesta. Se ven como mucho 5;
   - **Fix these weaknesses** · 약점 고치기 (`Fix`, de ancho completo).
   - Vacío: *No mistakes to fix. For now.* y el botón deshabilitado. Cargando: *Loading…*.
4. **Quiz history** · 퀴즈 기록: una Card con una fila por quiz, separadas por un divisor de 2 dp `line`:
   - un aro de 44 dp con *"{aciertos}/{total}"* (12 sp, 800). Color del aro: 80 % o más, `correct-strong`; 50–79 %, `fix`; menos, `wrong`;
   - el documento (700, elipsis) y la fecha (12 sp, `muted`);
   - **Details** · 자세히 (Gray, Small), que abre las respuestas de ese quiz (como los resultados, sección D).
   - Vacío: *No quizzes yet. Take one.*

### A.1 · Hasta la fase 6: lo que necesita el modelo (R-007)

Sin modelo en el teléfono, *Fill in the blank*, *Short answer* y el bloque *Difficulty* se ven pero no se pueden usar. Captura: no hace falta; es la misma casilla de arriba con estos cambios.

- **Casillas *Fill in the blank* y *Short answer*:**
  - toda la fila al **50 % de opacidad** (`Modifier.alpha(0.5f)`), como ya hace *Difficulty*;
  - la casilla de 18 dp siempre **sin marcar** (borde de 2 dp `control`), aunque `UserPrefs` la tenga marcada. La preferencia guardada **no se toca**;
  - la pista (*Pick what goes in the gap* / *Type a short answer*) se sustituye por **Needs on-device AI** (12 sp, `muted`, la misma línea y el mismo estilo que la pista);
  - no responde al toque ni tiene ripple. TalkBack lee "{nombre}, needs on-device AI, unavailable" (`disabled()` en la semántica).
- **Bloque *Difficulty*:** siempre al 50 % y sin responder al toque. La línea *Only questions written by the model change with it.* se sustituye por **Needs on-device AI** (12 sp, `muted`). Las casillas muestran lo guardado, sin cambiarlo. Nunca sale *Choose at least one difficulty.*
- **Validación:** solo cuentan los 6 tipos por reglas. Con ninguno de ellos marcado: *Choose at least one question type.* y **Generate quiz** deshabilitado.
- **Nada más cambia:** ni el orden de las 8 casillas, ni la rejilla de 2 columnas, ni los colores.
- **En la fase 6:** con Nano `AVAILABLE`, todo vuelve a la sección A. Con Nano no disponible, sigue como aquí.

### A.2 · En la fase 6

- ***Fill in the blank*:** queda habilitado siempre, con o sin modelo, porque lo hacen las reglas (R-017). Vuelve su pista *Pick what goes in the gap*.
- **Con Nano `Available`:**
  - *Short answer* y *Difficulty* vuelven a la sección A;
  - *Difficulty* solo cuenta si hay marcado *Multiple choice* o *Short answer*; si no, queda al 50 % como en la sección A.
- **Con Nano no disponible:** *Short answer* y *Difficulty* siguen como en A.1.
- **Juez de significado:** debajo del resultado de una respuesta escrita aceptada por el modelo, *accepted: same meaning* (12 sp, `correct`) con AiMark corto.
- **Explicación del error:** debajo de la respuesta correcta, en un recuadro `canvas` con AiMark. Sin modelo, la explicación del punto de gramática tal cual, si existe, sin AiMark.

## B · Generando

Tras **Generate quiz**, la pantalla de la pregunta muestra el estado de carga de `shell.md` con *Creating your quiz…* (las preguntas por reglas tardan menos de 300 ms, así que casi nunca se ve). Si no se pudo crear ninguna: el recuadro de error con *No questions could be created this time.* y *Try other question types, another document, or try again.*, más **Back** (Gray).

## C · Pregunta (pantalla encima, sin barra inferior)

- **Barra superior pequeña:**
  - `x` a la izquierda (*Leave quiz*), que abre un diálogo: *Leave this quiz? The answers you already gave are saved, but the rest of the quiz is lost.*, con **Stay** (Gray) y **Leave** (`Danger`); el Back del sistema hace lo mismo;
  - el título *Quiz* y, a la derecha, *"{n} of {total}"* (12 sp, `muted`).
- **Progreso:** barra de 6 dp (pista `canvas`, relleno `brand`), con un margen lateral de 16 dp.
- **This quiz:** tres columnas *Done* 완료 · *Correct* 정답 · *Left* 남음. Etiqueta `label-caps` en `muted` y valor de 20 sp (800); *Correct* en `correct`.
- **Card de la pregunta** (relleno de 20 dp, 12 dp entre bloques):
  - fila superior (12 sp, `muted`): el tipo con su coreano a la izquierda y el documento a la derecha;
  - la pregunta: `ko-prompt` (20 sp, 700) si es coreano, o 20 sp (700) en inglés;
  - la pista del tipo (12 sp, `muted`);
  - **opciones** (QuizOption):
    - de ancho completo, 48 dp como mínimo de alto, relleno de 12 × 16 dp, radio 12 y fondo `canvas`;
    - el número (12 sp, `muted`) y el texto;
    - al tocar una, se envía.
  - **Respuesta escrita:** etiqueta *Your answer* / *Your answer in Korean*, el TextField y **Check** · 확인 (`Action`, de ancho completo en el pie; deshabilitado si está vacío). El teclado empuja el pie (`imePadding`).
- **Tras responder:**
  - **Opciones:**
    - la correcta: `correct-fill`, `check` y *correct* (`correct`), con un `pop` (escala 1.04, 240 ms, spring);
    - la elegida, si era incorrecta: `wrong-fill`, `x` y *your answer* (`wrong`), con `shake` (±6 dp, 320 ms).
  - **Respuesta escrita:**
    - el campo pasa a borde `wrong-border` (o queda normal si acertaste);
    - debajo, el recuadro `fill-correct` o `fill-wrong`: título de 16 sp (700) con `check` y *Correct!*, o con `x` y *Not quite*; y *Your answer:* / *Correct answer:* (14 sp; la etiqueta en `fill-muted`).
  - **Pie:** **Next question** · 다음 문제 (`Action`, ancho completo); en la última, **See results** · 결과 보기.
  - Reducir movimiento: sin `pop` ni `shake`.

## D · Resultados (pantalla encima, sin barra inferior)

- **Barra superior pequeña:** `x` (*Close results*), que vuelve al inicio del quiz.
- **Card** (relleno de 20 dp):
  - *"{aciertos} / {total} correct"* (24 sp, 800);
  - debajo, la frase según la nota (14 sp, `muted`), seguida de *Your answers were saved and count towards your progress.*:
    - todo bien: *Perfect. Now do it again tomorrow.*
    - la mitad o más: *Not bad, but not enough. Go again.*
    - menos de la mitad: *That was weak. Go again.*
  - **Lista** dentro de un recuadro con borde de 2 dp `line` y radio 12, con un divisor de 2 dp entre filas. Cada fila:
    - `check` (`correct`) o `x` (`wrong`), de 20 dp;
    - *"{n}. {pregunta}"* (500);
    - *Your answer:* en negrita, `correct` o `wrong`;
    - si fallaste, *Correct answer:* en negrita (`ink`).
- **Pie:** **New quiz** · 새 퀴즈 (`Action`, ancho completo).

## Datos

Las preguntas por reglas, `is_correct` y `widen_answer` son los de la web (pruebas doradas en `golden/text.json`). Las preguntas que escribe el modelo (Fill in the blank, Multiple choice y Short answer con dificultad) se dejan para la fase 6. Hasta entonces, si solo se eligen esos tipos, se generan por reglas cuando la web también lo hace, o sale el mensaje de *No questions could be created*. Cada respuesta se guarda al instante: una pregunta acertada descansa 7 días antes de volver.
