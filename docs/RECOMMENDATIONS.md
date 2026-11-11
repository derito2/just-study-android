# Recomendaciones — auditoría de Just Study 한국어 (Android)

Fecha: 2026-11-10 · Rama auditada: `main` en `f71510b` (fase 4 mergeada) más el cambio local de icono y pantallas de carga del mismo día (sin commit). Este documento se basta solo: quien lo ejecute no necesita el contexto de la auditoría.

Convenciones: **[V]** hecho verificado leyendo el código o ejecutando algo; **[H]** hipótesis sin verificar (se dice cómo verificarla). Rutas relativas a la raíz del repo. `A/` = `app/src/main/java/com/dero/juststudykorean/`, `D/` = `core/data/src/main/java/com/dero/juststudykorean/data/`.

## Estado

Se actualiza en cada PR.

| ID | Estado | PR |
|---|---|---|
| R-001 | pendiente |  |
| R-002 | pendiente |  |
| R-003 | hecho | #20 |
| R-004 | hecho | #20 |
| R-005 | hecho (wiggle: solo se recompone una envoltura, ver DECISIONS) | #20 |
| R-006 | hecho (el teclado tapaba *Show answer* en el OPPO) | #20 |
| R-007 | en PR #21 | #21 |
| R-008 | en PR #21 | #21 |
| R-009 | hecho | #20 |
| R-010 | pendiente |  |
| R-011 | hecho | #20 |
| R-012 | hecho | #20 |
| R-013 | pendiente (fase 6) |  |
| R-014 | pendiente |  |
| R-015 | icono temático hecho (#19); notificación y `onTimeout` pendientes (fase 5) | #19 |
| R-016 | pendiente |  |
| R-017 | pendiente (fase 6) |  |
| R-018 | hecho | #19 |

## 1. Resumen ejecutivo

Estado: base sana. 118 pruebas JVM en verde, lint sin errores, manifiesto unido solo con los 3 permisos permitidos, repositorios portados con doradas, estados vacío/carga/error en todas las pantallas, `reduceMotion()` respetado. El modelo aún no se usa en ningún sitio (no existe `:core:ai`; es la fase 6).

Los 5 hallazgos de más valor:
1. **Cada tarjeta calificada y cada respuesta de quiz leen tablas enteras** (`dao.all()` + filtro en Kotlin) y ninguna tabla tiene índices: el coste crece con tu historial (R-001, R-002).
2. **La APK lleva MuPDF para 4 arquitecturas**: 32 MB de 53 (release) no se usan nunca en el OPPO (R-003).
3. **El giro de la tarjeta recompone en cada fotograma** (lee `angle.value` en composición), justo la interacción más repetida (R-004).
4. **El formulario de Quiz ofrece tipos y dificultad que solo escribe el modelo**, que aún no existe: elegir solo esos da un quiz fallido (R-007).
5. **Un quiz a medias se pierde si ColorOS mata el proceso**: la ruta guardada es `Question(null)` y al volver se genera otro (R-008).

Riesgos mayores: degradación gradual con meses de uso (1); release sin firma configurada, así que actualizar sin perder datos depende de una clave que solo vive en este Mac (R-016); en la fase 6, mostrar texto del modelo en streaming antes de validarlo rompería "Teacher nunca inventa" (R-013).

## 2. Cómo se auditó

**Leído:** `CLAUDE.md`; `docs/PLAN.md` (puntos 0, 3–5, 7–12); `docs/DECISIONS.md` entero; `docs/FASE_5_PLAN.md`; `docs/poc/2-nano.md`; `.github/workflows/ci.yml`; `gradle/libs.versions.toml` y los 4 `build.gradle.kts`; manifiesto y `res/xml`; la skill `ml-kit-genai-prompt-api` (referencias). Código: `MainActivity`, `ProcessingService`, navegación, todos los ViewModels, `FlashcardRepository`, `ProgressRepository`, `QuizRepository`, `QuizGenerator`, `DocumentRepository`, `DocumentIndexer`, `NotesSource`, `Entities.kt`, `Daos.kt`, `AppTables.kt`, y de la UI `StudyScreen`, `Flashcard`, `DeckCard`, `StickerButton`, `QuizHomeScreen`. Del web: `backend/app/database/models.py` (índices) y el historial de git.

**No existen** (el encargo los nombraba): `docs/ANDROID_PLAN.md` (es `docs/PLAN.md`), `docs/CHANGELOG.md`, y un Design System en el repo (hay `design/*.md` y `design/tokens.json`). **El encargo decía WorkManager y Gemma/LiteRT-LM:** DECISIONS los descartó (servicio propio en primer plano; solo Gemini Nano). **Las fases 5–7 sí están planeadas** (`docs/FASE_5_PLAN.md`, `docs/PLAN.md` punto 12).

**Ejecutado (Mac, 2026-11-10):**

| Qué | Resultado |
|---|---|
| `./gradlew assembleDebug` | OK. APK debug 84,5 MB |
| `./gradlew test` | OK. 118 pruebas, 0 fallos (`:core:korean` 24, `:core:data` 29, `:app` 65) |
| `./gradlew lint` | 0 errores, 12 avisos (los relevantes están en R-005 y R-015) |
| `./gradlew assembleRelease` | OK. `app-release-unsigned.apk` 53,4 MB: dex 5,3 · `lib/arm64-v8a` 11,6 · `armeabi-v7a` 8,4 · `x86` 11,8 · `x86_64` 11,9 · res 1,9 |
| `./gradlew verifyRoborazziDebug` | OK: las capturas commiteadas coinciden (build completo con las 4 tareas: 8 min 9 s) |
| `python3 tools/ci/check_manifest.py` | OK en debug y release |
| `adb install -r` + abrir en el OPPO | Abre sin fallos; `am start -W` TotalTime 318 ms (debug, 1 medida) |

**Sin verificar:** informes de estabilidad del compilador de Compose (requieren tocar el build); pruebas instrumentadas (`connectedAndroidTest`); perfiles en el OPPO (jank, memoria, batería); el hilo en que Room 3 ejecuta el bloque de `withWriteTransaction`; contraste de colores y TalkBack en el teléfono; todo lo de Nano (no hay código que auditar, solo el plan y la PoC 2).

## 3. Tabla de recomendaciones

| ID | Área | Título | Impacto | Esfuerzo | Riesgo | Fase |
|---|---|---|---|---|---|---|
| R-001 | Datos / rendimiento | Consultas SQL dirigidas en las rutas calientes | alto | M | medio | 5 (antes de Teacher) |
| R-002 | Datos | Índices de Room (v2 → v3), los mismos del web | medio | S | medio | 5, tras R-001 |
| R-003 | Build / APK | Solo `arm64-v8a` en la APK | alto | S | bajo | ya |
| R-004 | Animación | Giro de tarjeta sin recomponer por fotograma | alto | S | bajo | ya |
| R-005 | Animación | Wiggle y botones: leer la animación en dibujo/layout | medio | S | bajo | ya |
| R-006 | UX | Teclado en la sesión de repaso (`imePadding`) | medio | S | bajo | ya |
| R-007 | UX / paridad | Formulario de Quiz: tipos y dificultad que necesitan modelo | alto | S | bajo | 5 |
| R-008 | Robustez (ColorOS) | El quiz en curso sobrevive a la muerte del proceso | medio | S | bajo | 5 |
| R-009 | Errores | Excepciones sin capturar en `viewModelScope` | medio | S | bajo | ya |
| R-010 | UX de estudio | Respuesta háptica al calificar y al responder | medio | S | bajo | 5 (con diseño) |
| R-011 | Accesibilidad | Botón "Clear search" de 32 dp | bajo | S | bajo | ya |
| R-012 | Rendimiento | All vocabulary: no recalcular por tecla | medio | S | bajo | ya |
| R-013 | IA (fase 6) | Puerta única de Nano: cola, validar antes de mostrar, caché | alto | M | medio | 6 |
| R-014 | IA / recuperación | Búsqueda FTS con raíces coreanas y prefijo | alto | S | medio | 5 (tarea 1) |
| R-015 | Servicio | `ProcessingService`: `onTimeout`, abrir la app al tocar, icono propio | bajo | S | bajo | 5 |
| R-016 | Build / datos | Firma de release reproducible (adelantar de la fase 7) | medio | S | medio | ya |
| R-017 | Quiz sin modelo | Fill in the blank por reglas | medio | M | medio | 6 (decisión) |
| R-018 | Arranque | Pantalla de carga: poder saltarla y medirla | bajo | S | bajo | ya |

**Ya planeado (no son hallazgos; no duplicar):** Teacher y Coach por reglas, `NotesSearch` sobre FTS y `selfcheck` (fase 5); los 8 usos del modelo, sus validadores, la marca "AI", el fallback sin modelo y la carga bajo demanda (fase 6, `docs/PLAN.md` 10.1); Baseline Profiles, macrobenchmark, R8 afinado, ANR, perfiladores y auditoría de seguridad (fase 7); respaldo cifrado y huella (opcionales, punto 8).

## 4. Detalle de cada recomendación

### R-001 — Consultas SQL dirigidas en las rutas calientes

- **Problema [V]:** `D/db/Daos.kt` (generado, línea 1) solo tiene `SELECT * FROM <tabla> ORDER BY id`. Los repositorios cargan tablas enteras y filtran en Kotlin, también en cada interacción:
  - Calificar una tarjeta, `FlashcardRepository.review` (`D/flashcards/FlashcardRepository.kt:323`): todas las tarjetas para encontrar una (`:325`), y por `ProgressRepository.attachSession` (`D/progress/ProgressRepository.kt:359`) todas las sesiones (`:361`) y todos los `study_session_topics` (`:371`).
  - Responder una pregunta, `QuizRepository.answer` (`D/quiz/QuizRepository.kt:244`): todas las preguntas (`:245`), todos los quizzes (`:247`), `attachSession` y `recomputeTopic` (`ProgressRepository.kt:378`: todas las preguntas y **todos los intentos**, `:379-380`).
  - Lista de mazos, `decksNow` (`FlashcardRepository.kt:152`): todo `flashcard_reviews` (`:154`) y todas las tarjetas en cada invalidación; `reviewedIds` (`:129`) repite la lectura completa en `statsNow` (`:208`) y `dueCards` (`:223`); `history(30)` (`:260`) ordena todo el historial para quedarse con 30.
  - `D/` cuenta 116 llamadas a `.all()` (35 en Flashcards, 27 en Progress, 24 en Quiz).
  Hoy hay 1265 filas y no se nota. `flashcard_reviews`, `question_attempts` y `study_sessions` crecen sin tope con el estudio.
  **[H]** Si Room 3 ejecuta el bloque de `withWriteTransaction` en el hilo que llama, el filtrado de `review` y `answer` corre en el hilo principal (los llama `viewModelScope`). Verificar con una aserción de hilo en una prueba instrumentada.
- **Cambio propuesto:**
  1. Crear `D/db/HotDaos.kt` (a mano; `Daos.kt` no se edita) con un `@Dao` `HotDao` registrado en la base. Como `JustStudyDatabase.kt` es generado, añadir el DAO desde `tools/data/gen_room_entities.py` (una línea fija) o con una función de extensión que use `db.useReaderConnection` como ya hace `ProgressRepository.nextId` (`:393`).
  2. Consultas: tarjeta por id; pregunta por id; quiz por id; `SELECT DISTINCT flashcard_id FROM flashcard_reviews`; `SELECT flashcard_id, MAX(reviewed_at) … GROUP BY flashcard_id`; últimas N reseñas con `ORDER BY reviewed_at DESC, id DESC LIMIT :n` (con el filtro de mazo aplicado después solo si `deckId != null`, pidiendo entonces sin `LIMIT`); última sesión por `mode` con `ORDER BY started_at DESC, id DESC LIMIT 1`; `EXISTS` en `study_session_topics`; intentos de un tema con `JOIN questions`.
  3. Sustituir solo en: `review`, `reviewedIds`, `decksNow` (el mapa `last`), `history`, `attachSession`, `recomputeTopic`, `QuizRepository.answer`, `QuizRepository.run` (`:159-161`).
  4. Envolver `review` y `answer` en `withContext(Dispatchers.IO)` (como el resto de lecturas).
- **Archivos:** `D/db/HotDaos.kt` (nuevo), `tools/data/gen_room_entities.py` y los 3 generados si se registra el DAO, `FlashcardRepository.kt`, `ProgressRepository.kt`, `QuizRepository.kt`; prueba nueva `core/data/src/test/.../HotPathScaleTest.kt`.
- **Aceptación:** `./gradlew :core:data:test` en verde sin tocar ninguna dorada (`FlashcardGoldenTest`, `QuizGoldenTest`, `ProgressGoldenTest`, `ReplayGoldenTest`: las 18 tablas idénticas). `HotPathScaleTest`: con 50 000 reseñas y 20 000 intentos sintéticos, `review()` y `answer()` tardan menos de 2× lo que tardan con la base dorada (JVM/Robolectric). En el OPPO, `ReplayGoldenTest` instrumentada sigue 16/16.
- **Dependencias:** ninguna. R-002 solo rinde después de esta.
- **Qué NO hacer:** no cambiar el orden ni el contenido de ningún resultado (las doradas mandan); no tocar `decksNow` más allá del mapa `last`; no introducir Paging; no editar a mano los archivos generados; no reescribir `createCards`, `merge` ni `delete` (se usan poco).

### R-002 — Índices de Room (v2 → v3), los mismos del web

- **Problema [V]:** ningún `@Entity` de `D/db/Entities.kt` declara índices (18 entidades, solo `primaryKeys`). El web sí: `backend/app/database/models.py` tiene `index=True` en `documents.sha256` (línea 28), `documents.status` (31), `study_sessions.started_at` (138), `question_attempts.quiz_run_id` (171) y `.created_at` (176), `flashcards.due_at` (256) y `.custom_deck_id` (263), entre otros. `tools/data/gen_room_entities.py` no los porta (no contiene `Index`). `docs/PLAN.md` punto 9 pide "índices en las columnas de búsqueda".
- **Cambio propuesto:**
  1. En `gen_room_entities.py`, emitir `indices = [Index("col")]` para cada columna con `index=True` del web, y además `flashcard_reviews(flashcard_id, reviewed_at)` y `question_attempts(question_id)` (los usa R-001).
  2. Regenerar; subir la base a `version = 3`; `MIGRATION_2_3` con los `CREATE INDEX IF NOT EXISTS` exactos del esquema exportado (`core/data/schemas/…/3.json`).
  3. Ampliar `MigrationTest` (v2 → v3: todas las filas iguales, esquema válido).
- **Archivos:** `tools/data/gen_room_entities.py`, `D/db/Entities.kt` y `JustStudyDatabase.kt` (regenerados), `D/db/AppTables.kt` (donde está `MIGRATION_1_2`), `D/db/DbModule.kt` (`addMigrations`), `core/data/schemas/`, `MigrationTest.kt`.
- **Aceptación:** `MigrationTest` en verde; `BackupTest` (OPPO) 6/6 y la exportación con los mismos sha256 por tabla (los índices no cambian filas); `EXPLAIN QUERY PLAN` de la consulta de últimas reseñas dice `USING INDEX`.
- **Dependencias:** R-001.
- **Qué NO hacer:** no añadir claves foráneas ni cambiar columnas (cambiaría el respaldo `.jsbackup.zip` v1); no indexar tablas pequeñas (`deck_places`, `deck_groups`, `custom_decks`).

### R-003 — Solo `arm64-v8a` en la APK

- **Problema [V]:** `app-release-unsigned.apk` pesa 53,4 MB; 32,1 MB son `libmupdf_java.so` y `libsqliteJni.so` para `armeabi-v7a`, `x86` y `x86_64`. La app es solo para un OPPO Find X8 Pro (arm64). `app/build.gradle.kts` no tiene `abiFilters`.
- **Cambio propuesto:** en `app/build.gradle.kts`, `defaultConfig { ndk { abiFilters += "arm64-v8a" } }`.
- **Archivos:** `app/build.gradle.kts`.
- **Aceptación:** `unzip -l app/build/outputs/apk/release/*.apk | grep "lib/"` solo lista `lib/arm64-v8a/`; la APK de release pesa menos de 22 MB; CI en verde; en el OPPO la app abre y `:core:pdf:connectedDebugAndroidTest` (`ParserGoldenTest`) pasa.
- **Dependencias:** ninguna.
- **Qué NO hacer:** no usar splits ni App Bundle (no hay Play Store); no tocar la versión de MuPDF (fijada por las doradas).

### R-004 — Giro de tarjeta sin recomponer por fotograma

- **Problema [V]:** `FlipCard` (`A/ui/components/Flashcard.kt:51`) lee `angle.value` en composición (`:59`) y construye un `Modifier.graphicsLayer` nuevo por valor (`:60`). Durante los 400 ms del giro, `FlipCard` y la cara visible (`CardFront`/`CardBack`, con su texto y el campo de escribir) se recomponen en cada fotograma: unas 48 veces a 120 Hz. Es la animación más repetida de la app y la meta del plan es < 16 ms por fotograma.
- **Cambio propuesto:**
  1. `val showFront by remember { derivedStateOf { angle.value < 90f } }`.
  2. `val layer = Modifier.graphicsLayer { val a = angle.value; rotationY = if (a < 90f) a else a - 180f; cameraDistance = 1000f * density }` (la lectura queda en la fase de dibujo).
  3. `if (showFront) front(modifier.then(layer)) else back(modifier.then(layer))`.
- **Archivos:** `A/ui/components/Flashcard.kt`.
- **Aceptación:** `FlashcardsScreenshotTest` y `FlashcardsBehaviourTest` en verde sin regrabar capturas. En Layout Inspector (OPPO, build debug), un giro suma como mucho 2 recomposiciones de `CardFront`/`CardBack` (hoy, una por fotograma).
- **Dependencias:** ninguna.
- **Qué NO hacer:** no cambiar duración, curva ni distancia de cámara (son del diseño); no componer las dos caras a la vez.

### R-005 — Wiggle y botones: leer la animación en dibujo/layout

- **Problema [V]:** `DeckCard` lee el ángulo del wiggle en composición (`A/ui/components/DeckCard.kt:75-76`, `.value`) y lo aplica con `.rotate(angle)` (`:81`): en modo edición **todas** las tarjetas de mazo se recomponen en cada fotograma, sin fin. Lint marca `UseOfNonLambdaOffsetOverload` en `A/ui/components/StickerButton.kt:108` y `A/ui/components/ThemeSwitch.kt:45` (`offset(y = sink)` con un valor animado: recompone por fotograma al pulsar).
- **Cambio propuesto:** en `DeckCard`, guardar el `State<Float>` (sin `.value`) y usar `Modifier.graphicsLayer { rotationZ = angle.value }`; en `StickerButton` y `ThemeSwitch`, `Modifier.offset { IntOffset(0, sink.roundToPx()) }` (y el equivalente en x para el interruptor).
- **Archivos:** `DeckCard.kt`, `StickerButton.kt`, `ThemeSwitch.kt`.
- **Aceptación:** `./gradlew lint` ya no lista `UseOfNonLambdaOffsetOverload`; `verifyRoborazziDebug` en verde sin regrabar; en Layout Inspector, con el modo edición abierto 5 s, el contador de recomposiciones de `DeckCard` no crece.
- **Dependencias:** ninguna.
- **Qué NO hacer:** no cambiar la amplitud (±0,6°), los 140 ms ni el desfase alterno.

### R-006 — Teclado en la sesión de repaso

- **Problema [H]:** `StudyContent` (`A/ui/flashcards/StudyScreen.kt:100`) usa `statusBarsPadding().navigationBarsPadding()` sin `imePadding()`, y con *Type the answer* tiene un campo de texto dentro de la tarjeta. `QuestionScreen` (`A/ui/quiz/QuestionScreen.kt:86`) sí lo lleva. La app es edge to edge y el manifiesto no fija `windowSoftInputMode`, así que el contenido no se encoge solo: el teclado puede tapar el campo o el botón *Show answer*.
- **Cambio propuesto:** primero comprobar en el OPPO (mazo de palabras, *Type the answer* activado, Gboard coreano abierto). Si tapa algo, añadir `.imePadding()` tras `.navigationBarsPadding()` en `StudyScreen.kt:100`.
- **Archivos:** `A/ui/flashcards/StudyScreen.kt`.
- **Aceptación:** con el teclado abierto se ven enteros la palabra, el campo y *Show answer*; `FlashcardsScreenshotTest` sin cambios.
- **Dependencias:** ninguna.
- **Qué NO hacer:** no poner `adjustResize` en el manifiesto (cambia todas las pantallas); no tocar si la comprobación en el teléfono sale bien.

### R-007 — Formulario de Quiz: tipos y dificultad que necesitan modelo

- **Problema [V]:** el formulario lista 8 tipos (`A/ui/quiz/QuizViewModels.kt:37-46`), incluidos *Fill in the blank* y *Short answer*, y la sección *Difficulty* (`A/ui/quiz/QuizHomeScreen.kt:121-129`). En el teléfono no hay modelo: `RuleRun.step` devuelve `false` para esos grupos ("written by the model: none on the phone yet", `D/quiz/QuizGenerator.kt:149-154`) y `QuestionViewModel.generate` ni siquiera envía `levels` (`QuizViewModels.kt:165`). Elegir solo *Fill in the blank* o *Short answer* acaba en la pantalla de quiz fallido; marcar dificultades no cambia nada.
- **Cambio propuesto (hasta la fase 6):** en `QuizHomeScreen`, mostrar *Fill in the blank*, *Short answer* y el bloque *Difficulty* deshabilitados con la nota "Needs on-device AI" (misma opacidad 0,5 que ya usa el bloque), y no contar esos tipos al validar `ok` (`:131`). `QuestionViewModel.generate` filtra los tipos sin generador antes de pedir el quiz. El texto y el aspecto exactos los decide la sesión de diseño (pregunta abierta 1).
- **Archivos:** `QuizHomeScreen.kt`, `QuizViewModels.kt`, `design/quiz.md` (lo edita diseño), `QuizBehaviourTest.kt`, capturas de `QuizScreenshotTest`.
- **Aceptación:** prueba nueva en `QuizBehaviourTest`: con solo tipos de modelo marcados, *Generate quiz* está deshabilitado; con tipos mezclados, el quiz sale con `count` preguntas de los tipos por reglas. Ningún camino llega a la pantalla de fallo por elegir tipos.
- **Dependencias:** en la fase 6, R-013 los reactiva; R-017 puede adelantar *Fill in the blank*.
- **Qué NO hacer:** no borrar los tipos de `QUIZ_TYPES` ni de `UserPrefs` (el respaldo y el web los conocen); no cambiar `QuizGenerator` (sus doradas dependen del reparto actual).

### R-008 — El quiz en curso sobrevive a la muerte del proceso

- **Problema [V]:** *Generate quiz* navega a `Screen.Question(null)` (`A/ui/navigation/AppScaffold.kt:57`) y esa ruta se guarda tal cual (`Screen.encode`, `A/ui/navigation/AppNavigation.kt`). Si ColorOS mata el proceso a mitad de un quiz, al volver `QuestionViewModel.open(null)` (`A/ui/quiz/QuizViewModels.kt:146-158`) **genera un quiz nuevo**. Además `open(runId)` empieza siempre en `index = 0` y sin respuestas, aunque estén guardadas en `question_attempts`.
- **Cambio propuesto:**
  1. Tras generar, sustituir la ruta: `QuestionScreen` recibe `onCreated: (Long) -> Unit` y `AppScaffold` hace `nav.push(TopLevel.Quiz, Screen.Question(id), replace = true)`.
  2. `QuizRepository.answersOf(runId)`: la última respuesta por pregunta de ese quiz (misma lógica que `results`, `D/quiz/QuizRepository.kt:166`).
  3. `QuestionViewModel.open(runId)` rellena `answers` con ellas y pone `index` en la primera pregunta sin responder (o `finished` si no queda ninguna).
- **Archivos:** `AppScaffold.kt`, `QuestionScreen.kt`, `QuizViewModels.kt`, `QuizRepository.kt`, `QuizBehaviourTest.kt`.
- **Aceptación:** prueba: generar, responder 2 de 5, crear un `QuestionViewModel` nuevo con ese `runId` → `index == 2`, `answers.size == 2`, y `quiz_runs` sigue con una fila. En el OPPO: quiz a medias, `adb shell am kill com.dero.juststudykorean` con la app en segundo plano, volver → misma pregunta.
- **Dependencias:** ninguna.
- **Qué NO hacer:** no persistir el texto a medio escribir; no cambiar el comportamiento de abrir un quiz terminado desde el historial (`Results`).

### R-009 — Excepciones sin capturar en `viewModelScope`

- **Problema [V]:** `QuestionViewModel.answer` (`A/ui/quiz/QuizViewModels.kt:179-186`) usa `try/finally` sin `catch`: un `QuizException` o un fallo de SQLite cierra la app. `DecksViewModel.undo` (`A/ui/flashcards/DecksViewModel.kt:93`) tampoco captura. Varios `runCatching` envuelven llamadas `suspend` (`QuizViewModels.kt:99,100,107,112,207`; `A/ui/dashboard/DashboardViewModel.kt:97`) y `StudyViewModel` captura `Exception` (`A/ui/flashcards/StudyViewModel.kt:87,117`): ambos se tragan `CancellationException`.
- **Cambio propuesto:** `answer`: `catch (e: Exception)` que relanza `CancellationException` y deja un `error: String?` en `QuestionUi` que la pantalla muestra con el `Snackbar` existente. `undo`: lo mismo con `DecksMessage("Couldn't restore the deck.")`. Añadir un `inline fun <T> runCatchingCancellable` (5 líneas, en `A/ui/components/` o donde vivan los helpers de UI) y usarlo en los sitios citados.
- **Archivos:** `QuizViewModels.kt`, `QuestionScreen.kt`, `DecksViewModel.kt`, `DashboardViewModel.kt`, `StudyViewModel.kt`.
- **Aceptación:** prueba con un repositorio que lanza en `answer`: el estado lleva el error y la prueba no revienta. `grep -n "runCatching {" A/ui` no devuelve llamadas `suspend` sin el helper.
- **Dependencias:** ninguna.
- **Qué NO hacer:** no añadir un `CoroutineExceptionHandler` global que oculte fallos; no registrar el texto de la respuesta en logs.

### R-010 — Respuesta háptica al calificar y al responder

- **Oportunidad [V]:** no hay ninguna llamada háptica (`grep -ri haptic A/` no devuelve nada). Calificar tarjetas y responder son las acciones más repetidas, a una mano y a menudo sin mirar los botones.
- **Cambio propuesto:** `LocalHapticFeedback.current.performHapticFeedback(...)` en: los 4 botones de `RatingButtons`, acierto y fallo en `QuizOption` y en `TypedResult`, y el giro (*Show answer*). No pide permisos y respeta el ajuste de vibración del sistema. Usar los tipos `Confirm`/`Reject` si existen en la versión de Compose del BOM 2026.09.00 (**[H]**, comprobar con `android docs`); si no, `LongPress`/`TextHandleMove`.
- **Archivos:** `A/ui/components/RatingButtons.kt`, `QuizOption.kt`, `TypedResult.kt`, `A/ui/flashcards/StudyScreen.kt`; `design/shell.md` (lo edita diseño).
- **Aceptación:** en el OPPO se nota un toque distinto para acierto y fallo; con la vibración táctil del sistema apagada, nada vibra. Pruebas JVM sin cambios.
- **Dependencias:** aprobación de la sesión de diseño (pregunta abierta 2).
- **Qué NO hacer:** no pedir el permiso `VIBRATE` (no está permitido); no vibrar al navegar ni al escribir.

### R-011 — Botón "Clear search" de 32 dp

- **Problema [V]:** `IconButton({ onQuery("") }, Modifier.size(32.dp))` en `A/ui/grammar/GrammarScreen.kt:72` y `A/ui/library/AllVocabularyScreen.kt:82`: el modificador externo limita el objetivo táctil a 32 dp, por debajo de los 48 dp que fija el propio sistema de diseño (`docs/PLAN.md` punto 5).
- **Cambio propuesto:** objetivo de 48 dp con el icono del mismo tamaño visual (quitar `Modifier.size(32.dp)` y compensar el hueco con el padding del `trailing` de `JsTextField`).
- **Archivos:** `GrammarScreen.kt`, `AllVocabularyScreen.kt`, quizá `A/ui/components/TextField.kt`.
- **Aceptación:** prueba de Compose `assertTouchHeightIsEqualTo(48.dp)` sobre el nodo "Clear search"; capturas iguales o regrabadas con el campo a la misma altura.
- **Dependencias:** ninguna.
- **Qué NO hacer:** no agrandar el icono ni el campo.

### R-012 — All vocabulary: no recalcular por tecla

- **Problema [V]:** `AllVocabularyViewModel.words` (`A/ui/library/LibraryViewModel.kt:172-174`) llama a `repo.vocabulary(q)` en cada cambio (con 120 ms de espera), y `DocumentRepository.vocabulary` (`D/documents/DocumentRepository.kt:96-102`) vuelve a construir y ordenar todas las palabras con `mergedWords` (`FlashcardRepository.kt:484`: `extractPairs` sobre cada fragmento de cada documento) antes de filtrar. `docs/PLAN.md` punto 9 preveía Paging; con esta corrección no hace falta.
- **Cambio propuesto:** el ViewModel carga la lista completa una vez (`vocabulary("")`, y otra vez con `retry`) y filtra en memoria con la misma regla (`norm(q) in norm(korean) || in norm(english)`, pasando a `:core:korean`/`:core:data` una función `matches(word, q)` para no duplicarla).
- **Archivos:** `LibraryViewModel.kt`, `DocumentRepository.kt`.
- **Aceptación:** prueba: 3 consultas seguidas llaman al repositorio 1 vez y dan las mismas listas que `repo.vocabulary(q)`.
- **Dependencias:** ninguna.
- **Qué NO hacer:** no añadir Paging ni una tabla nueva; no cambiar el orden (`sortKey`).

### R-013 — Puerta única de Nano: cola, validar antes de mostrar, caché

- **Contexto [V]:** no hay código de modelo. Los 8 usos, sus validadores, el fallback y la marca "AI" **ya están planeados** (`docs/PLAN.md` 10.1). Esta recomendación fija lo que el plan no concreta, a partir de `docs/poc/2-nano.md`: `BUSY` en 8 de 57 llamadas sin pausa y 0 con 2 s; `BACKGROUND_USE_BLOCKED` con la pantalla bloqueada; sin el prompt de sistema en inglés, 6/30 explicaciones válidas; coreano inventado antes de validar en 3/30 explicaciones y 3/30 Multiple choice.
- **Cambio propuesto (al crear `:core:ai`):**
  1. **Una sola puerta** (`NanoGate`, `@Singleton`): un `Mutex`, 2 s entre llamadas, un reintento con `BUSY`, y rechazo inmediato (→ versión sin modelo) si `ProcessingQueue.hasWork()`, si la app no está en primer plano o si el estado no es `AVAILABLE`. Ninguna pantalla llama a ML Kit directamente.
  2. **Validar antes de mostrar.** No pintar en streaming texto sin validar en Teacher, explicación de error ni Grammar: se acumula, pasa su validador (coreano solo de la entrada, inglés, citas) y entonces se muestra; mientras, el *Thinking… N s* del diseño. Si se quiere ver avance, validar y soltar **frase a frase**. El primer token a 0,39 s no compensa enseñar coreano inventado durante 3 s.
  3. **Plazo por uso** con `withTimeoutOrNull` (los p90 de la PoC + margen: Coach 3 s, juez 2 s, explicación 6 s) → versión sin modelo, sin aviso de error.
  4. **Juez de significado: salida estructurada** (sí/no + razón corta) y temperatura baja (0,2), solo para aprobar inglés. La skill del repo documenta `@Generable`, `generateTypedContentRequest` e `isStructuredOutputFeatureAvailable()` (`.claude/skills/ml-kit-genai-prompt-api/references/structured-output.md`); **[H]** comprobar que existen en `genai-prompt` 1.0.0-beta5 antes de usarlos; si no, parsear `YES`/`NO` en la primera línea.
  5. **Prefijo en caché** para la instrucción fija ("You are an English-speaking tutor…"): `promptPrefix = PromptPrefix(...)` (`references/prefix-caching.md`, misma comprobación **[H]**).
  6. **Caché de respuestas en memoria** por `(versión del prompt, hash de la entrada)`: explicación sencilla de un punto de gramática y explicación del mismo error. Solo memoria de proceso (ver pregunta abierta 3 para persistirla).
  7. **Presupuesto de entrada:** como mucho 4 fragmentos por pregunta de Teacher y recorte con `countTokens()` (**[H]**, misma comprobación).
- **Reglas que siguen siendo reglas (no mover al modelo):** corrección (`isCorrect`, `compareAnswer`), SM-2, constructores de quiz, intención y cifras del Coach, conjugación, todo el coreano que se muestra. **Al modelo, solo:** redactar en inglés a partir de hechos ya calculados y aprobar sinónimos en inglés.
- **Archivos:** `core/ai/` (nuevo: `NanoGate.kt`, un validador y un prompt versionado por uso), `settings.gradle.kts`, `docs/DECISIONS.md`.
- **Aceptación:** pruebas JVM con un modelo falso: (a) dos llamadas seguidas salen separadas ≥ 2 s; (b) con trabajo en la cola de PDFs la puerta devuelve "sin modelo" sin llamar; (c) una respuesta con una palabra coreana fuera de la entrada nunca llega a la UI; (d) al vencer el plazo sale la versión sin modelo. En el OPPO: repetir las tandas de la PoC 2 por la puerta, 0 `BUSY` tras el reintento.
- **Dependencias:** fase 5 terminada; R-014 para el uso 4.
- **Qué NO hacer:** no añadir `INTERNET`; no reabrir Gemma/LiteRT-LM ni embeddings (DECISIONS 2026-11-08); no guardar salidas del modelo en tablas del respaldo; no usar el modelo con un PDF procesándose.

### R-014 — Búsqueda FTS con raíces coreanas y prefijo

- **Problema [V + H]:** `notes_fts` es FTS4 con `tokenize=unicode61` (`D/db/AppTables.kt:24-28` y `:103-105`). **[V]** Aún no hay ninguna consulta `MATCH` en el código principal (llega con `NotesSearch`, fase 5, tarea 1). **[H]** `unicode61` corta por espacios y puntuación: `학교에서` es un solo token, así que `MATCH '학교'` no encuentra un fragmento que solo diga `학교에서`. Si `NotesSearch` busca la palabra tal cual, Teacher dirá "Your notes don't cover this" para palabras que sí están, y en la fase 6 el modelo recibirá fragmentos peores.
- **Cambio propuesto (dentro de la tarea 1 de la fase 5):** `NotesSearch` construye la consulta con las raíces de la pregunta (`korean_cores`/`lemmas`, que la tarea ya porta) como términos de prefijo unidos con `OR` (`"학교"* OR "가"*`), filtra después con `has_form` para quitar falsos positivos del prefijo, y ordena por número de raíces distintas encontradas y luego por `rowid`. Escapar comillas del texto del usuario.
- **Archivos:** `core/data/.../teacher/NotesSearch.kt` (lo crea la fase 5), `AppTables.kt` (la consulta), prueba `NotesSearchTest.kt`.
- **Aceptación:** prueba Robolectric y la misma instrumentada (el SQLite del teléfono es el empaquetado): indexado un fragmento con `학교에서 공부해요`, buscar `학교` y `공부하다` lo devuelve; buscar `학` solo no devuelve `학생` como coincidencia válida tras `has_form`. `TeacherGoldenTest` mantiene el 100 % de texto.
- **Dependencias:** tarea 1 de `docs/FASE_5_PLAN.md`.
- **Qué NO hacer:** no migrar a FTS5 ni cambiar el tokenizador (obliga a migración y a reindexar; pregunta abierta 4 si la prueba falla); no añadir embeddings.

### R-015 — `ProcessingService`: `onTimeout`, abrir la app al tocar, icono propio

- **Problema [V]:** `A/processing/ProcessingService.kt` no implementa `onTimeout`; **[H]** desde Android 15 un servicio `dataSync` tiene un tope diario (6 h) y, al agotarse, el sistema llama a `Service.onTimeout(int, int)` y espera `stopSelf()` en segundos (comprobar con `android docs foreground service timeout`). La notificación (`:68-76`) no lleva `setContentIntent`: tocarla no hace nada. Su icono pequeño es `R.drawable.ic_launcher_foreground` (`:69`), el vector por defecto de la plantilla de Android. `ProcessingService.start` puede lanzar `ForegroundServiceStartNotAllowedException` si se llama con la app ya en segundo plano (`MainActivity.kt:38-41` lo llama tras un `suspend`).
- **Cambio propuesto:** `override fun onTimeout(startId: Int, fgsType: Int) { run?.cancel(); stopSelf() }` (la cola ya reanuda al abrir); `setContentIntent` con un `PendingIntent` inmutable a `MainActivity`; `try/catch` de `IllegalStateException` en `start` (la cola sigue en Room); icono de notificación monocromo propio (lo entrega diseño) y borrar entonces `res/drawable/ic_launcher_foreground.xml`.
- **Archivos:** `ProcessingService.kt`, `res/drawable/ic_stat_processing.xml` (nuevo), `ProcessingQueueTest` no cambia.
- **Aceptación:** tocar la notificación abre la app; `tools/ci/check_manifest.py` sigue OK (solo `MainActivity` exported); `android-intent-security`: `PendingIntent` con `FLAG_IMMUTABLE` e intent explícito.
- **Dependencias:** icono de diseño.
- **Qué NO hacer:** no volver a WorkManager ni pedir `WAKE_LOCK` o `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (DECISIONS 2026-11-09); no poner nombres de archivo en la notificación.

### R-016 — Firma de release reproducible (adelantar de la fase 7)

- **Problema [V]:** `app/build.gradle.kts:26-31` no tiene `signingConfig`; `assembleRelease` produce `app-release-unsigned.apk`. La app instalada hoy es la de debug, firmada con `~/.android/debug.keystore` de este Mac. `docs/PLAN.md` punto 11 ("Actualizar la APK sin perder datos: misma clave de firma") depende de una clave que no está respaldada: si se pierde, actualizar exige desinstalar y los datos solo vuelven por *Import backup*. **Ya planeado** para la fase 7 ("clave de firma fuera del repo"); se propone adelantarlo porque el teléfono ya tiene datos reales.
- **Cambio propuesto:** crear un keystore de release fuera del repo; `signingConfigs.release` que lee ruta y contraseñas de `~/.gradle/gradle.properties` (si faltan, release queda sin firmar, como hoy, para que el CI no cambie); documentar en `docs/DECISIONS.md` el paso único "exportar respaldo → desinstalar debug → instalar release → importar".
- **Archivos:** `app/build.gradle.kts`, `docs/DECISIONS.md`. Nada en el repo con claves (`*.jks` ya está prohibido en `CLAUDE.md`).
- **Aceptación:** en el Mac, `./gradlew assembleRelease` da `app-release.apk` y `apksigner verify` lo acepta; en CI sigue saliendo sin firmar y en verde; tras el cambio de debug a release, *Import backup* devuelve las mismas 18 tablas y sha256.
- **Dependencias:** decisión del dueño (pregunta abierta 5).
- **Qué NO hacer:** no commitear el keystore ni contraseñas; no firmar en CI; no hacer el cambio debug → release sin exportar antes un respaldo.

### R-017 — Fill in the blank por reglas

- **Oportunidad [V]:** en el web, *Fill in the blank* lo propone el modelo (`GeneratedBlank`) y lo valida `buildBlank` (`core/korean/src/main/kotlin/com/dero/juststudykorean/korean/QuizBuilders.kt:238`). Los puntos de gramática ya guardan ejemplos de tus notas con su patrón (`grammar_points.examples`, usados en `FlashcardRepository.details`, `:290`). Con ellos se puede construir el hueco sin modelo: quitar del ejemplo la forma del patrón (localizada con `has_form`) y ofrecer como distractores otros patrones del mismo documento. Cero coreano inventado por construcción, y el tipo deja de depender de la fase 6.
- **Cambio propuesto:** `buildBlankFromExample(point, example, pool, rng)` en `QuizBuilders.kt`, que arma un `GeneratedBlank` y lo pasa por el mismo `buildBlank`; un grupo nuevo en `RuleRun` solo cuando `fill_blank` está pedido. Empezar por las pruebas (ejemplo con la forma al final, con partícula, y uno donde `has_form` no la encuentra → no se genera).
- **Archivos:** `QuizBuilders.kt`, `D/quiz/QuizGenerator.kt`, pruebas nuevas en `core/korean` y `QuizGoldenTest` (casos nuevos, sin tocar los existentes).
- **Aceptación:** las 826 doradas de `golden/builders.json` y `QuizGoldenTest` siguen en verde con peticiones que no incluyen `fill_blank`; con `fill_blank`, toda pregunta generada pasa `buildBlank` y su frase aparece literal en el texto del documento.
- **Dependencias:** R-007 (dejaría de estar deshabilitado); decisión del dueño, porque es comportamiento que el web no tiene (pregunta abierta 6).
- **Qué NO hacer:** no cambiar el reparto de tipos para las peticiones actuales (rompe doradas); no inventar frases ni conjugar: solo ejemplos literales de las notas.

### R-018 — Pantalla de carga: poder saltarla y medirla

- **Contexto [V]:** el 2026-11-10 se añadió `SplashOverlay` (`A/ui/splash/Splash.kt`, llamado en `A/MainActivity.kt:67`): una de 14 imágenes al azar (`res/drawable-nodpi/splash_*.webp`, 1080×2400, 1,2 MB en total) durante 1500 ms sobre la app, con zoom lento y título. Fue una petición expresa del dueño. Costes: 1,5 s sin poder tocar la app en cada apertura (`pointerInput` vacío, `Splash.kt`), y la decodificación de un WebP de 1080×2400 (~10 MB en memoria) en el hilo principal durante el arranque. La meta del plan es abrir en < 1 s.
- **Cambio propuesto:** (1) un toque cierra la pantalla de carga al instante; (2) medir en el OPPO con build de release el tiempo hasta el primer fotograma con y sin ella (`adb shell am start -W`, 5 arranques en frío cada uno) y anotarlo en DECISIONS; (3) una captura Roborazzi de `SplashOverlay` con una imagen fija; (4) si la medida supera 1 s hasta poder usar la app, bajar a 1000 ms (pregunta abierta 7).
- **Archivos:** `Splash.kt`, prueba nueva `app/src/test/java/com/dero/juststudykorean/ui/splash/SplashScreenshotTest.kt`, `docs/DECISIONS.md`.
- **Aceptación:** tocar durante la pantalla de carga muestra la app en el siguiente fotograma; con "Quitar animaciones" la imagen queda fija y desaparece sola; la captura está en `app/src/test/screenshots/`.
- **Dependencias:** ninguna.
- **Qué NO hacer:** no cambiar las imágenes, los colores ni el texto ("Just Study Korean" / "by DERO"); no usar la API `SplashScreen` del sistema para esto (solo admite el icono); no mostrarla al volver de segundo plano.

## 5. Orden de implementación sugerido

Cada lote es un PR independiente, con CI en verde.

1. **Lote A — tamaño y fluidez inmediatos:** R-003, R-004, R-005. Sin riesgo funcional; capturas sin regrabar.
2. **Lote B — errores y detalles de UI:** R-009, R-011, R-012, R-006 (este último solo si la comprobación en el OPPO lo confirma).
3. **Lote C — Quiz honesto y resistente:** R-007 (tras la respuesta de diseño), R-008.
4. **Lote D — datos:** R-001. PR propio, con `HotPathScaleTest` y las doradas instrumentadas en el OPPO.
5. **Lote E — índices:** R-002 (migración v3). Exportar un respaldo antes de instalar.
6. **Lote F — firma:** R-016, cuando el dueño decida (pregunta 5).
7. **Dentro de la fase 5:** R-014 en la tarea 1; R-015 y R-010 al final de la fase; R-018 cuando convenga.
8. **Fase 6:** R-013 como primera tarea (antes de cualquier uso del modelo); R-017 si se aprueba.

## 6. Ideas descartadas y por qué

- **WorkManager para procesar PDFs:** necesita `WAKE_LOCK`, permiso no permitido (DECISIONS 2026-11-09). El servicio propio ya reanuda por etapas.
- **Gemma 4 / LiteRT-LM y embeddings (EmbeddingGemma):** Nano pasó la PoC 2; Gemma son ~2,6 GB para mejorar solo el juez de significado (DECISIONS 2026-11-08). FTS + raíces (R-014) cubre la recuperación.
- **Añadir `INTERNET`** para cualquier cosa (modelos, telemetría, fuentes): decisión de privacidad.
- **Mover al modelo la corrección de respuestas o la generación de coreano:** 67 % de aciertos como juez (PoC 2) y coreano inventado en ~10 % de salidas sin validar.
- **Streaming token a token en Teacher:** enseña texto sin validar (R-013, punto 2).
- **Paging 3 en All vocabulary e historial:** las listas son de cientos de filas; R-012 quita el coste real.
- **Reescribir los repositorios en SQL puro:** perdería la equivalencia línea a línea con el Python que hace baratas las doradas. R-001 toca solo las rutas calientes.
- **Más módulos Gradle (`:feature:*`):** el Mac tiene 8 GB y el plan pide pocos módulos (punto 3).
- **Emulador en CI para las pruebas instrumentadas:** MuPDF y Nano solo se validan de verdad en el OPPO, y R-003 quita las ABI x86.
- **Icono monocromo (themed icon) generado a partir del arte:** el arte es un raster con degradados; un monocromo automático queda ilegible. Lint lo avisa (`MonochromeLauncherIcon`, 2 avisos) y se acepta hasta que diseño entregue un glifo.
- **Pedir exención de optimización de batería desde la app:** requiere un permiso no permitido; ColorOS se configura a mano (plan, punto 2).
- **`collectAsState` → `collectAsStateWithLifecycle` en `A/MainActivity.kt:44`:** es el tema de la app y debe seguir vivo mientras exista la actividad.

## 7. Preguntas abiertas para el dueño

1. **R-007:** hasta la fase 6, ¿los tipos *Fill in the blank* y *Short answer* y el bloque *Difficulty* se muestran deshabilitados con una nota, o se ocultan?
   - **Respuesta del dueño (2026-11-11):** deshabilitados, con la nota **Needs on-device AI** (`design/quiz.md`, A.1).
2. **R-010:** ¿quieres vibración al calificar y al acertar o fallar? Si sí, diseño fija en qué acciones.
   - **Respuesta del dueño (2026-11-11):** vibración sí, solo en las acciones de `design/shell.md` (Respuesta háptica).
3. **R-013:** ¿la caché de explicaciones del modelo puede guardarse en disco (una tabla fuera del respaldo, borrable desde Settings) o solo en memoria? El plan dice que lo que crea el modelo "nunca se guarda como si fuera tuyo".
   - **Respuesta del dueño (2026-11-11):** solo en memoria, nunca en disco.
4. **R-014:** si la búsqueda por prefijo no basta en tus notas reales, ¿se acepta una migración a otro tokenizador (reindexar todo) en la fase 5?
   - **Respuesta del dueño (2026-11-11):** cambiar de tokenizador (y reindexar) solo si la búsqueda por prefijo falla con las notas reales; en ese caso se para antes y se avisa.
5. **R-016:** ¿se crea ya la clave de release y se hace el cambio único debug → release (exportar, desinstalar, instalar, importar), o se espera a la fase 7?
   - **Respuesta del dueño (2026-11-11):** crear ya la clave de release, fuera del repo. El cambio debug → release lo hace el dueño después, con respaldo antes; Claude Code no desinstala nada.
6. **R-017:** ¿se acepta *Fill in the blank* por reglas aunque el web no lo tenga (sin dorada del web, con pruebas propias)?
   - **Respuesta del dueño (2026-11-11):** sí, *Fill in the blank* por reglas, en la fase 6.
7. **R-018:** ¿1500 ms de pantalla de carga está bien o prefieres 1000 ms? ¿Las 11 fotos personales (algunas con personas) deben ir al repositorio de GitHub o quedarse solo en tu Mac?
   - **Respuesta del dueño (2026-11-11):** 1500 ms. Ni las 11 fotos personales ni las ilustraciones hechas a partir de ellas van a GitHub.
8. **Nombre visible:** el lanzador muestra `JustStudyKorean` (`app/src/main/res/values/strings.xml`, `app_name`). ¿Se cambia a "Just Study Korean" o "Just Study 한국어"?
   - **Respuesta del dueño (2026-11-11):** **Just Study 한국어**.
