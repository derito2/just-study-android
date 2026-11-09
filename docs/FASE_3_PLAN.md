# Fase 3 — Núcleo sin IA: plan de implementación

> **Para quien lo ejecute (Claude Code):** cada tarea llega como un prompt corto de la sesión de diseño; este documento es el plan. Sub-skill: `superpowers:executing-plans`, con TDD (`superpowers:test-driven-development`). **Nada se mergea sin la confirmación del usuario en esa tarea.** Cada tarea dice si hace falta el teléfono (📱).

**Objetivo:** estudiar en el teléfono con tus datos importados y sin modelo. Dashboard con la gráfica de progreso; Flashcards (SM-2, mazos, Type the answer); Quiz por reglas con *Fix these weaknesses* e historial; Grammar y Calendar. Todo con el mismo comportamiento que la web, comprobado con pruebas doradas.

**Arquitectura:**
- `:core:korean` (JVM puro): las reglas portadas de la web.
- `:core:data`: repositorios sobre Room que hacen lo mismo que los servicios del web (`progress_service`, `flashcard_service` y `quiz_service`). El reloj y la zona horaria se inyectan.
- `:app`: un ViewModel por pantalla con `StateFlow<UiState>`, y pantallas Compose según `design/*.md`.

Nada de `:core:pdf` ni `:core:ai` (fases 4 y 6).

**Base:**
- `docs/PLAN.md`: puntos 7 y 9;
- `design/dashboard.md`, `design/flashcards.md`, `design/quiz.md` y `design/grammar-calendar-settings.md` (Grammar y Calendar);
- `design/png/*.png`;
- `golden/`.

## Restricciones globales

- **Mismo comportamiento que la web.** Si una prueba dorada no coincide, se arregla el Kotlin, nunca la dorada. Si la dorada parece mal, se para y se pregunta.
- **Proyecto web de solo lectura.** Las doradas nuevas se generan con `tools/golden` y el venv del web.
- **Sin colores, tamaños ni textos fuera de `design/`.** Si falta algo, se para y se pregunta.
- **Nada pesado en el hilo principal:** Room con Flow y `Dispatchers.IO`.
- **Metas medidas en el OPPO:** generar un quiz por reglas en menos de 300 ms; pasar de tarjeta en menos de 16 ms por fotograma.
- **Fechas:** instantes en UTC; los días, en la zona del teléfono. Ningún `Instant.now()` ni `ZoneId.systemDefault()` fuera de `AppClock`.
- **Logs sin texto de tus notas ni de tus respuestas.** Sin ajustes del teléfono (CLAUDE.md).
- **Un PR por tarea**, con el CI en verde.

## Riesgos que hay que probar (y en qué tarea)

1. **El cambio de día a medianoche, en la zona del teléfono,** rompe la racha o el calendario. Prueba con el reloj fijado a las 23:59 y a las 00:01 (tarea 2).
2. **Valorar la misma tarjeta dos veces seguidas** (doble toque): solo cuenta una valoración (tarea 4).
3. **Un quiz con tipos que no pueden salir de tus notas:** el mensaje *No questions could be created*, sin cuelgues ni preguntas vacías (tarea 5).
4. **Letra al 130 %** en Dashboard, mazos, el formulario del quiz y Calendar: nada se corta ni se monta (capturas Roborazzi en las tareas 3 a 6).
5. **Datos grandes:** 475 tarjetas y 336 preguntas importadas. La lista de mazos, el historial y All vocabulary no se traban: LazyColumn con `key`, y una medida de fotogramas en el OPPO (tareas 4 y 5).

---

## Tarea 1 — `:core:korean`: reglas con sus doradas

📱 **Teléfono: no hace falta.**

**Archivos** (en `core/korean/src/main/kotlin/com/dero/juststudykorean/korean/`):
- `Sm2.kt`:
  - `data class CardState(val ease: Double = 2.5, val intervalHours: Int = 0, val repetitions: Int = 0)`;
  - `data class Scheduling(val againMinutes: Int = 10, val hardHours: Int = 12, val goodHours: Int = 24, val easyHours: Int = 48)`;
  - `enum class Rating { Again, Hard, Good, Easy }`;
  - `data class Next(val ease: Double, val intervalHours: Int, val repetitions: Int, val dueInMinutes: Long)`;
  - `fun schedule(state: CardState, rating: Rating, cfg: Scheduling = Scheduling()): Next`;
  - `fun label(dueInMinutes: Long): String`.

  Python redondea a par: usar `Math.rint`, no `roundToInt`.
- `CompareAnswer.kt`: `fun compareAnswer(typed: String, answer: String, korean: Boolean): Comparison`. `Comparison` y `Letter` con los mismos campos que en `compare.ts`.
- `Text.kt` (ampliar): `norm`, `isCorrect(type: String, given: String, correct: String): Boolean`, `englishAnswers`, `englishSenses` e `initialConsonant`. `isGrounded` y `leaksAnswer` ya existen.
- `Chunker.kt`: `fun chunkDocument(documentId: Long, filename: String, pages: List<ExtractedPage>): List<Chunk>`, con `data class ExtractedPage` igual que una página de `data/extracted` y `data class Chunk` igual que `chunker.Chunk`, incluido `chromaId`.
- `Pairs.kt`: `fun extractPairs(chunk: Chunk): List<Pair>`.
- `GrammarExtract.kt`: `fun extractGrammar(chunk: Chunk): List<GrammarPoint>`.
- `QuizBuilders.kt`:
  - `fun widenAnswer(answer: String, pool: List<Pair>, prompt: String = "", text: String = ""): String?`;
  - los constructores por reglas de `quiz/builders.py` (`buildFromPair` y `buildBlank`) y `quiz/grammar.py` (`buildGrammar`), con `kotlin.random.Random(seed)` inyectado.

**Pruebas** (en `core/korean/src/test/...`, una clase por dorada, leyendo `../../golden/`): `Sm2GoldenTest`, `CompareAnswerGoldenTest`, `TextGoldenTest`, `ChunkerGoldenTest`, `PairsGoldenTest` y `GrammarGoldenTest`.

- [ ] **Paso 1:** las 6 clases de prueba, que cargan **todos** los casos de su JSON y comparan campo a campo. En rojo.
- [ ] **Paso 2:** implementar archivo por archivo, hasta que cada dorada quede en verde.
- [ ] **Paso 3: constructores del quiz.** Si `golden/` no trae casos de `build_from_pair`, `build_blank` ni `build_grammar` (el azar dependía de la semilla), se añaden a `tools/golden/cases.py`: semilla fija y los pares y gramática de los PDFs de prueba como entrada. Se regenera `golden/` y el diff se revisa en el PR.
- [ ] **Paso 4:** `./gradlew :core:korean:test` en verde. Commit.

**Parar si:** el azar de Python (`random.Random`) no se puede reproducir en Kotlin. En ese caso se dice antes de cambiar el orden de las opciones; la alternativa es portar el generador Mersenne Twister.

---

## Tarea 2 — `:core:data`: repositorios, con doradas de base de datos

📱 **Teléfono: no hace falta** (pruebas JVM con Room en memoria y Robolectric).

**Doradas nuevas:**
- `tools/golden/export_db_golden.py`: crea una base SQLite sintética con los modelos del web, rellena con datos fijos (documentos y tarjetas de los PDFs de prueba, intentos y repasos con fechas fijas en `America/Mexico_City`) y llama a los servicios del web con un `now` fijo.
- Escribe `golden/db/fixture.jsbackup.zip` (la base en formato v1) y un JSON por función:
  - `progress_summary`, `weekly`, `calendar` (2 meses), `timeline` (day, week, month y year) y `topics` (Fix this next);
  - `flashcard_decks`, `flashcard_stats`, `due_cards` y `history`;
  - `quiz_mistakes`, `quiz_history` y `saved_questions`.
- Si un servicio no corre en SQLite (SQL propio de MySQL), se para y se dice cuál.

**Archivos** (en `core/data/src/main/java/com/dero/juststudykorean/data/`):
- `time/AppClock.kt`: `interface AppClock { fun now(): Instant; val zone: ZoneId }`, `SystemClock` y un `FixedClock` para las pruebas.
- `progress/ProgressRepository.kt`:
  - `suspend fun summary(): ProgressSummary`;
  - `suspend fun weekly(): Weekly`;
  - `fun calendar(year: Int, month: Int): Flow<CalendarMonth>`;
  - `suspend fun timeline(unit: TimelineUnit): Timeline`;
  - `suspend fun recommended(limit: Int = 3): List<Recommendation>`.
- `flashcards/FlashcardRepository.kt`:
  - `fun decks(): Flow<List<Deck>>`;
  - `fun stats(): Flow<FlashcardStats>`;
  - `suspend fun queue(deckId: Long, direction: CardDirection): List<StudyCard>`;
  - `suspend fun review(cardId: Long, rating: Rating, elapsedMs: Long): Next`;
  - `suspend fun history(limit: Int): List<ReviewItem>`;
  - `suspend fun rename(deckId: Long, name: String)`;
  - `suspend fun reorder(deckIds: List<Long>)`;
  - `suspend fun merge(deckIds: List<Long>, name: String): Long`;
  - `suspend fun delete(deckId: Long): DeletedDeck`;
  - `suspend fun undoDelete(deleted: DeletedDeck)`;
  - `suspend fun createCards(documentIds: List<Long>): CreateResult`.
- `quiz/QuizRepository.kt`:
  - `suspend fun generate(request: QuizRequest): QuizRun?`;
  - `suspend fun answer(runId: Long, questionId: Long, given: String, elapsedMs: Long): Grade`;
  - `fun mistakes(limit: Int = 5): Flow<List<Mistake>>`;
  - `fun history(): Flow<List<HistoryCard>>`;
  - `suspend fun practiceSaved(): QuizRun?`;
  - `suspend fun fixWeaknesses(): QuizRun?`.
- `grammar/GrammarRepository.kt`: `fun points(filter: GrammarFilter, query: String): Flow<List<GrammarPointUi>>`.

**Pruebas:**
- `ProgressGoldenTest`, `FlashcardGoldenTest` y `QuizGoldenTest`: importan `golden/db/fixture.jsbackup.zip` con `BackupImporter`, fijan el `FixedClock` y comparan con los JSON.
- `MidnightTest`: el riesgo 1, con 23:59 y 00:01 en la zona del teléfono.

- [ ] **Paso 1:** `export_db_golden.py`, con su prueba Python de determinismo (dos ejecuciones, los mismos sha256). Se genera `golden/db/`.
- [ ] **Paso 2:** las pruebas Kotlin, en rojo.
- [ ] **Paso 3:** los repositorios, hasta que queden en verde.
- [ ] **Paso 4:** `./gradlew test` en verde. Commit.

---

## Tarea 3 — Dashboard

📱 **Teléfono: sí** al final (ver la pantalla con tus datos y medir).

**Archivos:**
- `app/.../ui/dashboard/DashboardViewModel.kt` y `DashboardScreen.kt`;
- componentes nuevos en `ui/components/`: `StatTile.kt`, `TodayHero.kt`, `CalendarMonthCompact.kt`, `ProgressChart.kt` (dibujado con Canvas) y `SelectedGroup.kt`;
- `app/src/main/res/drawable-nodpi/dashboard.webp`: copia de `motivationPics/dashboard.jpeg` del web, de 1080 px de ancho como máximo. La foto es tuya y no va al repo público si algún día lo es; de momento el repo es privado.

**Según:** `design/dashboard.md` y `design/png/dashboard*.png`.

- [ ] **Paso 1:** capturas Roborazzi en rojo: `dashboardStudied`, `dashboardEmpty`, `dashboardDark`, `dashboardFullChartWeek` y `dashboardAt130Percent`, con datos falsos fijos.
- [ ] **Paso 2:** implementar hasta que coincidan a ojo con los PNG de `design/` y grabarlas.
- [ ] **Paso 3:** *Practice this* crea un quiz de 5 preguntas del tema (QuizRepository) y abre Quiz. *Ask what to do* abre Teacher (la pantalla provisional hasta la fase 5).
- [ ] **Paso 4:** 📱 en el OPPO con tu respaldo importado: las cifras de los KPI y del calendario coinciden con las que daba la web para la misma fecha (`build/poc/web-progress.json` sirve de referencia). Captura del teléfono. Commit.

---

## Tarea 4 — Flashcards

📱 **Teléfono: sí** (sesión de repaso real y medida de fotogramas).

**Archivos:**
- `app/.../ui/flashcards/`: `DecksScreen.kt`, `DecksViewModel.kt`, `StudyScreen.kt`, `StudyViewModel.kt` y `MergeDialog.kt`;
- componentes: `DeckCard.kt`, `Flashcard.kt` (con el giro), `RatingButtons.kt`, `TypedResult.kt`, `FilterPill.kt` y `TextField.kt`.

**Según:** `design/flashcards.md` y `design/png/flashcards-*.png`.

- [ ] **Paso 1:** capturas en rojo: `decks`, `decksEdit`, `mergeDialog`, `front`, `back`, `typedAlmost`, `noCardsDue` y `decksAt130Percent`.
- [ ] **Paso 2:** implementar y grabarlas.
- [ ] **Paso 3:** pruebas de comportamiento en la JVM con un repositorio falso:
  - `doubleTapRatesOnce` (riesgo 2);
  - `deleteThenUndoRestoresCards`;
  - `renameEmptyKeepsOldName`;
  - `mergeNeedsName`.
- [ ] **Paso 4:** 📱 en el OPPO, 20 tarjetas seguidas con tus datos. Los fotogramas de pasar de tarjeta deben quedar por debajo de 16 ms (`adb shell dumpsys gfxinfo com.dero.juststudykorean`: percentil 90). Arrastrar para reordenar y combinar funciona con el dedo. Commit.

---

## Tarea 5 — Quiz por reglas

📱 **Teléfono: sí** (medir el tiempo de generar y hacer un quiz completo).

**Archivos:**
- `app/.../ui/quiz/`: `QuizHomeScreen.kt`, `QuizHomeViewModel.kt`, `QuestionScreen.kt`, `QuestionViewModel.kt`, `ResultsScreen.kt` y `DocumentsSheet.kt`;
- componentes: `QuizOption.kt`, `Feedback.kt`, `WeaknessPanel.kt`, `QuizHistoryCard.kt` y `Stepper.kt`;
- `res/drawable-nodpi/flags.webp`, con el mismo criterio que la foto del Dashboard.

**Según:** `design/quiz.md` y `design/png/quiz-*.png`. Los tipos que escribe el modelo (Fill in the blank, Multiple choice y Short answer) se generan por reglas cuando la web también lo hacía; si no, cuentan como no generables.

- [ ] **Paso 1:** capturas en rojo: `homeTop`, `homeFull`, `questionChoice`, `questionWrongTyped`, `results`, `leaveDialog` y `homeAt130Percent`.
- [ ] **Paso 2:** implementar y grabarlas.
- [ ] **Paso 3:** pruebas en la JVM:
  - `onlyModelTypesShowsNoQuestions` (riesgo 3);
  - `answerIsSavedImmediately`;
  - `correctQuestionRestsSevenDays`;
  - `formOptionsPersist`.
- [ ] **Paso 4:** 📱 en el OPPO con tus datos: generar 10 preguntas en menos de 300 ms (medido en el ViewModel y en logcat, sin contenido), hacer un quiz entero y *Fix these weaknesses*. Commit.

---

## Tarea 6 — Grammar y Calendar

📱 **Teléfono: sí** al final.

**Archivos:**
- `app/.../ui/grammar/GrammarScreen.kt` y `GrammarViewModel.kt`, con `GrammarCard.kt`;
- `app/.../ui/calendar/CalendarScreen.kt` y `CalendarViewModel.kt`, con `CalendarMonthFull.kt`.

Se sustituyen las pantallas provisionales.

**Según:** `design/grammar-calendar-settings.md`.

- [ ] **Paso 1:** capturas en rojo: `grammarList`, `grammarOpen`, `grammarEmpty`, `grammarNoMatch`, `calendarMonth`, `calendarDaySelected` y `calendarAt130Percent`.
- [ ] **Paso 2:** implementar y grabarlas.
- [ ] **Paso 3:** pruebas en la JVM:
  - `searchFiltersWithinCategory`;
  - `onlyOneCardOpen`;
  - `nextMonthDisabledOnCurrentMonth`;
  - `dayDetailText`, que compara con el texto de `describe()` de la web.
- [ ] **Paso 4:** 📱 en el OPPO con tus datos: el calendario de noviembre coincide con el de la web. Commit.

---

## Al terminar la fase

- [ ] `docs/DECISIONS.md`: cómo se reprodujo el azar de Python y cualquier diferencia aceptada con la web, explicada.
- [ ] `mem_save` con `topic_key` `android/fase-3`.
- [ ] Tabla de metas medidas en el OPPO, en el último PR: generar un quiz, el percentil 90 de fotogramas al pasar de tarjeta y la apertura de la app.
