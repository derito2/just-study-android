# Auditoría 2 — Just Study 한국어 (Android 0.11.0)

Fecha: 2026-11-15 · Base: `main` en `62778b1` (PR #32, fase 9, mergeado). Solo lectura: no se cambió código. Este documento no contiene texto de las notas del dueño; las pruebas en el teléfono se hicieron con la app QA (`.qa`, release con otro id) y se desinstaló al terminar. Rutas relativas a la raíz; `A/` = `app/src/main/java/com/dero/juststudykorean/`, `D/` = `core/data/src/main/java/com/dero/juststudykorean/data/`.

Convenciones: **[V]** verificado (código leído o medido en el OPPO); **[H]** hipótesis que hay que medir (se dice cómo). Lo que `docs/RECOMMENDATIONS.md` (tabla *Estado*) marca como hecho no se repite: R-001…R-018 están cerradas y aquí solo se señala lo que **queda fuera** de ellas.

Decisiones que se respetan: sin `INTERNET` ni APIs remotas (solo descargas del sistema: Nano, voces); sin vibración (R-010); nada de texto de notas en repo ni logs; `.jsbackup.zip` v1 no cambia; colores de la web (DECISIONS 2026-11-12, *Contraste*); doradas del web como referencia (DECISIONS 2026-11-09/13/14).

## 1. Evaluación general

Base sana y disciplinada. Cifras medidas hoy en el OPPO con el release (R8 activo, solo arm64): **arranque en frío 137 ms** (`am start -W`, 1 medida; antes 301–310 ms en el perfil de la fase 5), **APK 25,1 MB** (53 MB antes de R-003), 1 única actividad exportada, `INTERNET` no solicitado por la app real (`dumpsys package`: solo `POST_NOTIFICATIONS` concedido y `RECORD_AUDIO` sin conceder), 13 llamadas a `Log` revisadas: solo ids, cuentas y nombres de clase. Cero `GlobalScope`; un único `collectAsState` sin ciclo de vida (`A/MainActivity.kt:58`, justificado en RECOMMENDATIONS §6).

Lo que más pesa hoy no es el rendimiento sino **tres cosas de producto/calidad**:

1. **Los recuadros de acierto/fallo del Quiz en oscuro son ilegibles** (texto claro sobre relleno claro, 1,06:1) y deslumbran (15:1 contra el fondo). Bug confirmado en el teléfono (H-01).
2. **11 de las 14 pantallas de carga no existen en el release**: el encogido de recursos las borra (H-02). Lo que se ve «pixelado» son las 3 que sí se envían (§9).
3. **No hay un modelo único de «palabra»**: Flashcards, Quiz y Games guardan su progreso por separado y «dominada» significa tres cosas distintas (H-03).

Riesgo a medio plazo: casi todo el cálculo de Dashboard, Quiz e historial **lee tablas enteras** y filtra en Kotlin (H-04); R-001 solo cubrió las rutas por respuesta/valoración.

## 2. Arquitectura real

```
:app  (Compose, Hilt, Navigation 3)         ~ 100 archivos, 13 ViewModels (@HiltViewModel)
  ui/{dashboard,library,flashcards,quiz,teacher,games,grammar,calendar,screens,components,theme,navigation,splash}
  processing/ProcessingService (servicio dataSync propio, sin WorkManager)
:core:data  (Room 3 + SQLite bundled, DataStore, respaldo)   ~ 40 archivos
  db/ (23 entidades: 18 de la web 1:1 + notes_fts + processing_jobs + 3 de Games), repositorios, audio/ (TTS), speech/ (STT)
:core:ai    único módulo que ve ML Kit: NanoGate (1 llamada, 2 s de hueco, validar antes de mostrar)
:core:korean  Kotlin/JVM puro: reglas portadas de la web, doradas, constructores de Quiz/Games/Teacher/Coach
:core:pdf   MuPDF 1.28.5 (parser, títulos, tablas con líneas dibujadas)
:macrobenchmark
```

- **Límites:** `core:korean` no depende de Android (prueba `NoMlKitInScreensTest` impide ML Kit fuera de `:core:ai`). `:core:data` expone `api(:core:korean/:core:pdf/:core:ai)`: la UI ve tipos de dominio sin pasar por interfaces; aceptable con 1 app y 8 GB de Mac (RECOMMENDATIONS §6, «Más módulos Gradle»).
- **Estado:** un `StateFlow` por ViewModel (`MutableStateFlow` privado + `asStateFlow`), una pila de Navigation 3 por pestaña con `rememberViewModelStoreNavEntryDecorator` (el ViewModel muere al salir de la pila). El quiz en curso sobrevive a la muerte del proceso con `SavedStateHandle` (R-008, `A/ui/quiz/QuizViewModels.kt:217-233`); Games igual (`GamesViewModel.kt:94-96`).
- **Lógica en composables:** no se encontró lógica de negocio en `@Composable` fuera de presentación (los cálculos viven en `core:*`).
- **Memoria de proceso que no está en Room** (por decisión): preguntas del modelo (`QuizRepository` `aiRuns`/`aiAnswers`/`spokenRuns`/`aiFailed`, `ConcurrentHashMap`, líneas 83-84, 206, 215), caché de Nano, lo oído por el micrófono. Se pierden al morir el proceso; es lo previsto (DECISIONS 2026-11-12, D9-6).
- **Tamaño:** archivos más grandes `FlashcardRepository.kt` 599 líneas, `Teacher.kt` 501, `QuizBuilders.kt` 486, `GamesScreen.kt` 471, `QuizViewModels.kt` 419, `DecksScreen.kt` 416. Ninguno es un problema de legibilidad por sí solo; `FlashcardRepository` mezcla decks, repaso, creación de tarjetas y fusión (H-05).

## 3. Hallazgos (ordenados por gravedad)

### H-01 — Recuadros de acierto/fallo en oscuro: texto ilegible y deslumbramiento
- **Gravedad:** Alta · **Tipo:** bug confirmado [V].
- **Qué pasa hoy:** en modo oscuro los rellenos `correctFill` `#EAFFDB` y `wrongFill` `#FFF0F0` son los mismos que en claro (`A/ui/theme/JustStudyColors.kt:214-220` reutiliza `Tokens.CorrectFill/WrongFill`). Dentro de ellos, el valor de la respuesta usa `MaterialTheme.colorScheme.onSurface`, que en oscuro es `#F8FAFC` (`A/ui/components/Feedback.kt:87-88`): **1,01:1 sobre el relleno verde y 1,06:1 sobre el rojo**. Los rótulos «Your answer:» usan `fillMuted` (correcto, 4,84:1) pero el valor no: el recuadro enseña etiquetas y deja el contenido invisible. En `QuizOption.kt:62` el número de la opción usa `muted` `#94A3B8` sobre el relleno pálido: 2,4:1.
- **Medido en el OPPO** (píxeles de las capturas `quiz_*_oscuro.png`; fondo `#1E203B`, luminancia 0,016): recuadros verde y rojo, luminancia 0,941 y 0,899 → **15,0:1 y 14,3:1 contra el fondo** (un bloque casi blanco en una pantalla casi negra). Tinta `correct` `#2F7A00` / `wrong` `#C62828` sobre su relleno: 5,08:1 (esto sí pasa). Tinta clara del valor: 1,02–1,06:1.
- **Mismo relleno en otros sitios:** calendario (`CalendarMonthCompact.kt:45-47`), píldora de documento (`DocumentCard.kt:145-146`) y *Ready* de Settings (`SettingsScreen.kt:162`), resultado de Games (`GamesScreen.kt:321-324`), fichas (`Pieces.kt:93-94`), caja de error (`States.kt:97`). En las capturas, las celdas 4!/6✓/7✓ del calendario en oscuro son bloques blancos.
- **Por qué importa:** es la pantalla más repetida del producto y el único sitio donde el usuario necesita **leer** su fallo.
- **Solución (2 pasos, el 1.º sin tocar colores de la web):** (1) dentro de todo relleno pálido usar `fillInk`/`fillMuted` (ya existen en `JustStudyColors.kt:55-56`, 13,5:1 sobre el verde) para el valor, el número de opción y los rótulos → arregla la legibilidad sin cambiar ningún token. (2) Decisión del dueño: variantes oscuras `correctFillDark`/`wrongFillDark` (relleno teñido oscuro con tinta clara) para bajar el brillo; **esto se aparta de DECISIONS 2026-11-12** («los colores no se cambian, son los de la web») y por eso queda como pregunta (§11).
- **Beneficio:** legibilidad de la pantalla central y menos deslumbramiento de noche. **Complejidad:** S (paso 1), M (paso 2: 4 tokens + capturas). **Riesgo:** bajo; regrabar solo capturas oscuras de Quiz/Calendar/Library.
- **Pruebas:** `TokensTest`: para cada par (tinta, relleno) usado, contraste ≥ 4,5:1 en ambos temas; Roborazzi oscuro de `AnswerFeedback` correcto/incorrecto y de `QuizOption` en los 4 estados; regla de lint propia o prueba que prohíba `onSurface` dentro de `background(correctFill|wrongFill)`.

### H-02 — El release no incluye 11 de las 14 pantallas de carga
- **Gravedad:** Alta · **Tipo:** bug confirmado [V].
- **Qué pasa hoy:** `A/ui/splash/Splash.kt:56-60` busca `splash_photo_01..11` con `getIdentifier` (por nombre). El release tiene `optimization { enable = true }` (`app/build.gradle.kts`) y el encogido de recursos quita lo que no se referencia por `R.drawable`. No hay `res/raw/keep.xml` ni `tools:keep` (el comentario de `Splash.kt:54` lo avisa: «si algún día se activa el encogido…»). **Evidencia:** el APK release contiene **6 WebP** (los 3 *flash*, `dashboard`, `flags`, icono) frente a los 16 de `res/drawable-nodpi`; en **10 arranques en frío seguidos solo salieron los 3 *flash*** (con 14 imágenes la probabilidad de eso sin quitar nada es ≈ 2·10⁻⁷).
- **Por qué importa:** las 11 escenas dibujadas (más nítidas, medido en §9) nunca se ven; el pixelado que el dueño percibe es el de las 3 que sí salen.
- **Solución:** `app/src/main/res/raw/keep.xml` con `<resources xmlns:tools="…" tools:keep="@drawable/splash_photo_*" />`. Sin esos archivos (CI) no pasa nada: el patrón no encuentra coincidencias. **Beneficio:** aparecen las 14. **Complejidad:** S. **Riesgo:** bajo (+0,45 MB de APK).
- **Pruebas:** script de CI local (no en GitHub: las fotos no están) que cuente WebP del APK y avise si faltan; prueba instrumentada «`getIdentifier` encuentra N» en el OPPO; ver capturas `qa/ronda-2/capturas/carga_lanzamiento_*.png`.

### H-03 — Sin un modelo común de palabra y progreso; «dominada» significa tres cosas
- **Gravedad:** Alta · **Tipo:** debilidad de diseño [V].
- **Qué pasa hoy (punto 12 del encargo):**

| Fuente | Clave | Qué guarda | «Dominada»/«difícil» |
|---|---|---|---|
| Flashcards (`flashcards`, `flashcard_reviews`) | `id` de tarjeta; `korean_word` + mazo | SM-2: `ease`, `interval_hours`, `repetitions`, `due_at` | **«Learned» = `repetitions >= 1`** (`D/flashcards/FlashcardRepository.kt:218-226`): una sola valoración *Good* ya cuenta como aprendida |
| Quiz (`question_attempts`, `user_topic_progress`) | `question_id`, `topic_id` (tema = sección del PDF) | aciertos por pregunta y por tema; `mastery` = aciertos de los últimos 10 intentos del tema (`ProgressRepository.kt:392-401`) | «débil» = último intento fallido (`QuizRepository.kt:110-113`) |
| Games (`game_item_progress`, solo teléfono, fuera del respaldo) | `"w:"+norm(coreano)` | `seen/correct/wrong/streak` | **«mastered» = `streak >= 3`** (`D/db/GameTables.kt:96`); «débil» = `wrong > correct` o fallo en 7 días (`korean/games/Selection.kt` `itemWeight`) |

- **Consecuencia:** lo que fallas en Games no cambia el mazo, ni el Quiz de debilidades ni el Dashboard; el Dashboard («Words learned this week») y Flashcards («Learned») cuentan distinto: en la app QA el Dashboard mostraba 0 palabras mientras Flashcards decía 5 aprendidas. Games sí suma tiempo y racha (D8-4) pero no conocimiento.
- **Duplicados y traducciones en conflicto entre documentos:** una palabra en varios documentos acaba en **una tarjeta** con los significados unidos (`FlashcardRepository.kt:576-598` `mergeRepeatedCards` + `joinedMeaning`, p. ej. «A / B»); el conflicto no se avisa ni se puede elegir. Games deduplica por `"w:"+norm` (`GameBankBuilder.kt:35-38`) fusionando ejemplos. Las tablas `vocabulary`, `examples` y `ml_topic_scores` (de la web) no tienen ningún uso fuera de sus DAO generados [V: búsqueda por texto sin resultados].
- **Propuesta (sin implementarla):**
  1. **Fase A — modelo de lectura, sin migración:** `WordProgress` en `:core:data`, calculado al vuelo desde las tres fuentes con la clave `norm(coreano)` (la misma de Games). Definiciones únicas, documentadas en DECISIONS: **dominada** = tarjeta con `interval_hours ≥ 504` (21 d) y sin *Again* en las últimas 5 valoraciones, o racha de juegos ≥ 3 en ≥ 2 días distintos; **difícil** = ≥ 3 *Again* en las últimas 10 valoraciones, o `wrong > correct` en juegos, o su último intento de Quiz fallido.
  2. **Fase B — tabla solo del teléfono** `word_state(word_key PK, first_seen_at, last_event_at, again_count, wrong_count, correct_count, source_mask)` y `question_words(question_id, word_key)` rellenada al generar preguntas. **Migración v5 → v6:** `CREATE TABLE IF NOT EXISTS` ×2 + relleno desde `flashcards`/`flashcard_reviews` y `game_item_progress` (quitando `w:`); lo del Quiz solo desde la fecha de la migración (las preguntas antiguas no llevan palabra). `BackupSchema` no las lista: **`.jsbackup.zip` v1 no cambia** (igual que Games, D8-3); se pierden al desinstalar y se reconstruyen desde las tarjetas.
  3. Games y Quiz empiezan a escribir `word_state`; el Dashboard y «Weakness» leen de ahí.
- **Beneficio:** una sola verdad para «qué sé / qué fallo»; permite repasar en tarjetas lo que falló en Games. **Complejidad:** M (A), L (B). **Riesgo:** medio: toca el significado de números ya visibles; las doradas del web no cubren Games ni «Learned» nuevo.
- **Pruebas:** pruebas de tabla `(eventos → estado)` para dominada/difícil; `MigrationTest` v5→v6 con `schemas/6.json` y comprobación de que las 18 tablas del respaldo quedan idénticas (sha256, como `PhoneMigrationCheck`); `BackupUnchangedTest` ya existe para Games y se extiende.

### H-04 — Lecturas de tabla completa fuera de las rutas calientes
- **Gravedad:** Media · **Tipo:** riesgo de rendimiento [H] (medir).
- **Qué pasa:** R-001 dejó dirigidas solo `review`, `answer` y la lista de mazos (`D/db/HotDaos.kt`). El resto sigue con `dao.all()` + filtro: `ProgressRepository.kt:141-143, 156, 171, 177, 186-190, 219-221, 255-256, 294-296, 317, 332-337` (Dashboard: resumen, semana, calendario, gráfica, recomendados), `QuizRepository.kt:95-96, 103, 111, 118-119, 132-133, 193-194, 267, 273` (errores, historial, resultados), `CoachEngine.kt:117-123`, `FlashcardRepository.kt:168, 221, 237, 272`, `GrammarRepository.kt:54-56`, `DocumentRepository.kt:83` (`document(id)` = toda la tabla para una fila). El Dashboard encadena ~10 de esas lecturas en cada apertura y en cada cambio de 6 tablas (`DashboardViewModel.kt:81-86`, flujo de `calendar`).
- **Por qué:** coste lineal con el historial (meses de uso); con el volumen actual (475 tarjetas, decenas de intentos) no se nota (`HotPathScaleTest` solo mide las rutas de R-001).
- **Solución:** agregados SQL (`COUNT/SUM … GROUP BY día`) en `HotDao` para resumen, semana, calendario y gráfica; `WHERE id = :id` para `document(id)`; `historyNow` y `mistakesNow` con `GROUP BY question_id`. Mantener la equivalencia con las doradas (`ProgressGoldenTest`, `ReplayGoldenTest`). **Beneficio:** tiempo de apertura plano. **Complejidad:** M. **Riesgo:** medio (cifras del Dashboard; las doradas las protegen).
- **Pruebas:** extender `HotPathScaleTest` con 50 000 intentos y 20 000 valoraciones sintéticos y exigir < 100 ms por función; macrobenchmark del Dashboard con esa base (la app benchmark ya existe).

### H-05 — Trabajo de CPU dentro de transacciones de escritura y un bucle cuadrático
- **Gravedad:** Media · **Tipo:** debilidad [V].
- **Qué pasa:** `QuizRepository.generateQuestions` (`:266-273`), `FlashcardRepository.createCards` (`:449-453`) y `DocumentIndexer.index` (`:151-153`) llaman a `notes.chunks(...)` y construyen preguntas/tarjetas **dentro** de `withWriteTransaction`: el bloqueo de escritura de SQLite dura lo que tarde el CPU (lectura de JSON, troceado, `extractPairs`). En `mergeRepeatedCards`, por cada tarjeta repetida se lee `flashcardReviewsDao().all()` completo (`FlashcardRepository.kt:594`): O(repetidas × valoraciones).
- **Por qué importa:** mientras corre, una valoración de tarjeta o una respuesta de Quiz espera el bloqueo (en la práctica, el servicio de PDF y el repaso no coinciden, pero nada lo impide).
- **Solución:** calcular fuera de la transacción y abrir solo para escribir en lote; en `mergeRepeatedCards`, una consulta `UPDATE flashcard_reviews SET flashcard_id = :keep WHERE flashcard_id = :other`. **Complejidad:** S-M. **Riesgo:** bajo-medio (repetir doradas de `FlashcardGoldenTest` y `RefreshCardsTest`).
- **Pruebas:** prueba con base de 5 000 valoraciones y 200 duplicados (tiempo y resultado idéntico a hoy); prueba de concurrencia: `review()` durante `createCards()` no espera más de X ms.

### H-06 — Integridad: sin claves foráneas ni limpieza declarada
- **Gravedad:** Media · **Tipo:** debilidad [V].
- **Qué pasa:** `D/db/Entities.kt` está generado 1:1 del `models.py` de la web y no declara ningún `@ForeignKey` (0 coincidencias). La integridad se mantiene a mano: `deleteOrphanTopics` (`D/db/AppTables.kt`), `mapNotNull { questions[...] }` «como la web» (`ProgressRepository.kt:143-146`), `DocumentRepository.delete`. `questions.topic_id` y `flashcards.topic_id` no tienen índice y se consultan con `JOIN` en cada respuesta (`HotDao.topicTotals/latestAttemptsOfTopic`); con cientos de preguntas es irrelevante.
- **Solución:** no añadir FK (reescribiría el esquema compartido con el respaldo v1); añadir una **prueba de integridad** que tras cada operación de borrado/importación recorra las relaciones (`PRAGMA foreign_key_check` sobre un esquema de prueba) y un índice en `questions(topic_id)` solo si H-04 lo mide necesario (migración `CREATE INDEX`, no toca datos). **Complejidad:** S. **Riesgo:** bajo.
- **Pruebas:** `IntegrityTest`: importar la base dorada → borrar documento con/sin tarjetas → reprocesar → ninguna fila huérfana.

### H-07 — Lector de PDF: memoria proporcional al tamaño y casos sin prueba
- **Gravedad:** Media · **Tipo:** especulativo que hay que medir [H].
- **Qué pasa:** `PdfParser.parse` (`core/pdf/.../PdfParser.kt:87`) lee **todas** las páginas a objetos `Glyph`/`CellLetter` (una letra = un objeto, dos listas) antes de generar líneas; el máximo de subida es 25 MB (`ProcessingQueue.kt:50`) y no hay prueba de más de 30 páginas (`tools/fixtures/long_pdf.py`: 147–259 MB de PSS, DECISIONS 2026-11-12). Sin `largeHeap`. Otros casos: PDF con **una** página con imagen grande se rechaza entero (`PdfParser.kt:101-102`, como la web); una página escaneada dentro de un PDF digital pasa si el resto tiene texto y ninguna imagen supera el 10 %; **no hay fixture de dos columnas** (el orden sale de MuPDF, `Lines.kt` solo une filas por altura) ni de tablas sin líneas (decisión 2026-11-09). Duplicados: solo por sha256 (`ProcessingQueue.kt:83`); un PDF re-exportado con otros bytes entra y se ve en *Duplicates* tras procesarse. Reanudar: bien resuelto por etapas (`ProcessingQueue.kt:121-156`, `ProcessingQueueTest`).
- **Solución:** (a) medir con un PDF de 300 páginas (`long_pdf.py`) y registrar `Runtime.maxMemory` y PSS; (b) si pasa de ~150 MB de montón, convertir cada página a líneas al leerla (se guardan líneas, no glifos; `bodyFontSize` solo necesita líneas); (c) añadir fixtures de dos columnas y de PDF mixto al `golden/parser`; (d) decisión del dueño sobre «importar con aviso las páginas con imagen» (se aparta de la web, §11).
- **Complejidad:** S (medir) / M (streaming). **Riesgo:** bajo si `RawGoldenTest` (63/63 páginas) sigue en verde.
- **Pruebas:** androidTest `LongPdfMemoryTest` (300 páginas) con tope; `ParserGoldenTest` con los dos fixtures nuevos.

### H-08 — Offline: lo que la app controla está bien; lo que hace Play Services/AICore no se puede ver
- **Gravedad:** Baja · **Tipo:** especulativo [H].
- **Evidencia [V]:** `AndroidManifest.xml:5-9` quita `INTERNET` y `ACCESS_NETWORK_STATE` con `tools:node="remove"`; el release unido solo trae `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE(_DATA_SYNC)`, `RECORD_AUDIO`, `BIND_SERVICE` de AICore y el permiso de receptor de AndroidX (`signature`); `tools/ci/check_manifest.py` lo comprueba en CI; la app real instalada no solicita `INTERNET`. Sin ese permiso el proceso de la app **no puede abrir sockets**, así que ni la telemetría de ML Kit ni nada de terceros sale desde el proceso de la app. Sesión completa en modo avión con Nano, Teacher, Quiz, tarjeta y reproceso de PDF (DECISIONS 2026-11-12), voz y STT en modo avión (2026-11-14).
- **Estados de Nano, TTS y STT:** Nano `Unavailable/Downloadable/Downloading/Available`, la descarga solo con el botón (`A/ui/screens/SettingsScreen.kt`, `core/ai/NanoState.kt:35-48`); TTS solo con voz `ko` instalada y sin red (`TtsKoreanVoice.kt:26`); STT con `createOnDeviceSpeechRecognizer` y `EXTRA_PREFER_OFFLINE` (`PlatformKoreanSpeech.kt:55, 61`).
- **Lo que no se puede asegurar:** el servicio AICore y Play Services (otros procesos, otros permisos) pueden registrar uso por su cuenta; no es observable desde la app.
- **Solución:** prueba de «cero bytes»: tras una sesión de QA, `dumpsys netstats --uid` de la app debe dar 0 B (documentar en el procedimiento de ronda). Aviso en *Licenses/On-device AI* de que la descarga del modelo la hace el sistema. **Complejidad:** S. **Riesgo:** nulo.
- **Pruebas:** paso fijo de QA; `check_manifest.py` ya cubre el resto.

### H-09 — Errores de espacio, subida y copia
- **Gravedad:** Media · **Tipo:** debilidad / oportunidad de UX [V].
- **Qué pasa:** cualquier excepción al copiar el PDF (incluido **disco lleno**) sale como *«Could not read that file.»* (`A/ui/library/LibraryViewModel.kt:137-138`): mensaje falso para «sin espacio». No hay comprobación de espacio libre antes de copiar 25 MB (`DocumentFiles.receive`, `DocumentFiles.kt:27-31`) ni antes de importar un respaldo (el importador prepara un directorio de *staging* y lo renombra, `BackupImporter.kt:84`, lo que necesita ≈ 2× el tamaño). Exportar abre el destino con `"wt"` (`BackupExporter.kt:31`): si falla a mitad, queda un `.zip` truncado en la carpeta elegida. Exportar lee las 18 tablas con sentencias separadas, sin una transacción de lectura (`BackupExporter.kt:34-39`): si el servicio de PDF escribe entre dos tablas, el respaldo puede salir mezclado [H].
- **Cargas infinitas:** revisadas, con salida: estudio (`StudyViewModel.kt:99-101`), voz de Games con tope de 4 s (`GamesViewModel.kt:87`), Nano con plazo por uso. Sin comprobar [H]: `GamesViewModel.start()` si `engine.start` falla queda en el estado inicial (la excepción se traga con `runCatchingCancellable`, `:88`).
- **Solución:** `catch (IOException)` separado con mensaje «Not enough space on the phone» y comprobación de `usableSpace` (tamaño × 2) antes de copiar/importar; borrar el destino parcial del export si falla; envolver el volcado en una transacción de lectura; estado de error con *Retry* en Games. **Complejidad:** S. **Riesgo:** bajo.
- **Pruebas:** `InputStream` falso que lanza `IOException("No space left on device")` (JVM); exportador contra un `OutputStream` que falla en la tabla 5 → comprobar que no deja archivo; prueba del estado de error de Games con un motor que falla.

### H-10 — Pruebas y CI: lo que falta para no romper Quiz, Flashcards, Games y Library
- **Gravedad:** Media · **Tipo:** debilidad [V].
- **Qué hay:** 183 pruebas JVM en `:app`, 74 en `:core:data`, 134 en `:core:korean`, 10 en `:core:ai`; **dispositivo**: 10 en `:app`, 11 en `:core:data`, 2 en `:core:ai`, 4 en `:core:pdf` (MuPDF solo existe en el teléfono). CI (`.github/workflows/ci.yml`) corre `test`, `verifyRoborazziDebug`, `lint`, ambos `assemble*`, **compila** las androidTest y `check_manifest`; **no ejecuta ninguna prueba de dispositivo** (sin emulador, decisión de RECOMMENDATIONS §6).
- **Huecos:** (1) ningún ViewModel de **Library** (subida, reproceso, borrado) ni de **Settings** (importar/exportar con errores) ni **Dashboard** tiene prueba de comportamiento; (2) **no hay `kotlinx-coroutines-test`** (`libs.versions.toml`: 0 coincidencias): las pruebas esperan en tiempo real con `idleUntil` (la causa de los cuelgues de CI de la fase 2-3: DataStore + `runBlocking`); (3) **ninguna prueba de contraste** entre tinta y relleno (por eso H-01 llegó al teléfono con las capturas «verdes»: las capturas solo comparan con ellas mismas); (4) las capturas de Quiz en oscuro no incluyen la pantalla de respuesta con relleno (`QuizScreenshotTest` fija el estado de la pregunta, no el de acierto/fallo en oscuro); (5) las doradas no cubren Games ni el resumen con juegos (`GameSessionEngineTest` sí cubre el motor).
- **Solución y estrategia:** §6.

### H-11 — Oscuro: `canvas`, `surface` y `background` son el mismo color
- **Gravedad:** Baja · **Tipo:** oportunidad de UX [V].
- **Qué pasa:** `Tokens.kt:11,13,15`: `BackgroundDark = SurfaceDark = CanvasDark = #1E203B`. Las opciones del Quiz sin elegir (`QuizOption`, `canvas`) y la caja de la explicación de un fallo (`QuestionScreen.kt:250-258`, `c.canvas`) **no tienen contenedor visible** en oscuro (capturas `quiz_opcion_*_oscuro.png`): parece texto suelto sobre el fondo; en claro (`#F2F7FD` sobre blanco) sí hay caja.
- **Solución:** borde de 1 dp con `line` o un `canvas` oscuro 4–6 % más claro (`#262A4A`) solo en oscuro; los tokens son un archivo de diseño, así que lo decide la sesión de diseño. **Complejidad:** S. **Riesgo:** bajo. **Pruebas:** Roborazzi oscuro de `QuizOption`.

### H-12 — Bloqueo con `runBlocking` en la búsqueda de Teacher
- **Gravedad:** Baja · **Tipo:** debilidad [V].
- **Qué pasa:** `D/teacher/NotesSearch.kt:28` usa `runBlocking` para leer SQLite desde la interfaz síncrona `NotesStore`; hoy solo se llama desde `Dispatchers.Default` bajo un `Mutex` (`TeacherService.kt:61-72`). Si alguien lo llamase desde el hilo principal sería un ANR garantizado.
- **Solución:** `NotesStore` suspendible (la regla de `Teacher` ya es pura) o `@WorkerThread` + prueba que falle si se llama en `Looper.getMainLooper()`. **Complejidad:** M. **Riesgo:** medio (la dorada `TeacherFtsGoldenTest` protege el resultado). Prioridad baja.

### H-13 — Dependencias: sin escáner de vulnerabilidades y una dependencia beta
- **Gravedad:** Baja · **Tipo:** debilidad [V] / especulativo [H].
- **Qué hay:** `libs.versions.toml`: AGP 9.5.1, Kotlin 2.4.0, Compose BOM 2026.09.00, Room 3.0.3, `sqlite-bundled` 2.7.1 (trae su propio SQLite), **`genai-prompt` 1.0.0-beta5** (beta: la API puede romperse), MuPDF 1.28.5 fijado (AGPL, pantalla de licencias en Settings). **No se ejecutó ningún análisis de CVE** (no hay herramienta en el repo; sin red en esta auditoría se dice «no verificado»). Secretos: sin `.jks`/`keystore`/`local.properties` rastreados (`git ls-files`), la firma sale de `~/.gradle/gradle.properties` (R-016); sin claves en el código (búsqueda por texto).
- **Migraciones desde versiones anteriores [V]:** `DbModule.kt` registra `MIGRATION_1_2…4_5` y **no** usa `fallbackToDestructiveMigration` (un salto sin migración falla en vez de borrar); `MigrationTest.kt:35-62` valida cada paso contra `schemas/N.json` y `PhoneMigrationCheck` compara las 18 tablas por sha256 en el OPPO (v2→v3, v3→v4, 18/18 iguales). La v4→v5 (Games) se probó en JVM; su comprobación en teléfono no está registrada.
- **Solución:** Dependabot de GitHub (sin dependencia nueva) y revisar cada subida de MuPDF/`sqlite-bundled` con las doradas; plan de salida si `genai-prompt` cambia de API (todo está tras `NanoModel`, `core/ai/NanoModel.kt`). **Complejidad:** S. **Riesgo:** nulo.

### H-14 — Pequeñas observaciones
- `ProcessingService.onStartCommand` llama a `startForeground` en cada arranque (correcto), con `onTimeout` (Android 15) y `START_NOT_STICKY`; sin permiso de notificaciones sigue funcionando (`ProcessingService.kt:29-33, 64-68`).
- `MainActivity.onCreate` lanza `ensureIndex()` en cada arranque (`:52-55`): barato (un `COUNT`), pero tras una importación reconstruye el índice completo en el hilo de la actividad (`SettingsViewModel.kt:58`); medir con una base grande [H].
- `Splash.kt:56-60`: 11 llamadas a `getIdentifier` por arranque (microsegundos; desaparecen si se usa un `listOf(R.drawable…)` con `tools:keep`).
- Todas las `LazyColumn`/`LazyVerticalGrid` pasan `key` (`items(` sin `key` en la misma línea: ninguna). El `Splash` y `Games` leen estado animado solo en dibujo/layout (`graphicsLayer {}`/`offset {}`), correcto; `GameStage.kt:73` respeta *Quitar animaciones*.

## 4. Informe de rendimiento

| Qué | Dato | Fuente |
|---|---|---|
| Arranque en frío del release | **137 ms** (1 medida; la actividad abre con el splash encima) | `am start -W`, hoy |
| Arranque antes | 301–310 ms (debug/perfil fase 5); 318 ms debug (auditoría 1) | engram, RECOMMENDATIONS |
| APK release (arm64) | **25,06 MB** (23,9 MiB) | `app-qa.apk` |
| Juegos: CPU por fotograma | P50 4,7 · P90 6,4 · P99 9,6 ms; retraso P99 16 ms | DECISIONS 2026-11-14 |
| PDF de 30 páginas | 242–288 ms, PSS 147–259 MB | DECISIONS 2026-11-12 |
| Teacher (reglas) | p50 45 ms, p90 109 ms (primer arranque 1,3 s); índice ~438 ms perezoso | engram (fase 5) |
| Nano | juez p50 2,6 s; explicación p50 3,2 s; lote de preguntas ~14 s (3 preguntas ≈ 3 s cada una con el quiz ya abierto) | DECISIONS 2026-11-12 |
| Memoria con pantalla de carga | un WebP 1080×2400 se decodifica a 10,4 MB (ARGB_8888) en el hilo principal | cálculo; **no medido en el teléfono** |
| Batería | **no medida** (no se hizo `batterystats` en esta ronda); lo único recurrente son `rememberInfiniteTransition` de Games (`GameStage.kt:75-77`), solo con Games delante | [H] |
| CPU/jank Compose | informes de estabilidad del compilador **no generados** (RECOMMENDATIONS ya lo dejó sin verificar) | [H] |

Para cerrar lo no medido: `./gradlew :app:assembleRelease -Pandroidx.enableComposeCompilerReports=true` (solo local), `adb shell dumpsys batterystats` tras una sesión de 15 min, y el macrobenchmark del Dashboard con la base sintética de H-04.

## 5. Informe offline

Resumen: **sin `INTERNET` en el manifiesto unido ni en la app instalada** (verificado hoy), servicios de terceros solo ML Kit `genai-prompt` (todo detrás de `NanoGate`), voz y reconocimiento del sistema forzados a «sin red» (`TtsKoreanVoice.kt:26`, `PlatformKoreanSpeech.kt:55,61`), modo avión probado en las fases 7 y 8. Telemetría de ML Kit: no puede salir del proceso de la app (sin sockets); AICore/Play Services son procesos del sistema (H-08). Las únicas descargas son las del sistema (modelo de Nano, voces) y la app no las inicia sin el botón.

## 6. Estrategia de pruebas

Objetivo: que un cambio en Quiz, Flashcards, Games o Library falle en CI antes de llegar al teléfono, sin emulador (8 GB de Mac).

1. **Contraste como prueba** (H-01): `ContrastTest` en `:app` que recorre los pares (tinta, fondo) que la UI usa en claro y oscuro y exige ≥ 4,5:1 (3:1 para texto grande y bordes). Es JVM puro (valores de `Tokens`).
2. **Capturas de los estados que importan:** Roborazzi en oscuro de acierto/fallo (opción y escrita), de Calendar y de la píldora *Ready*. Hoy solo hay capturas de la pregunta.
3. **Corrutinas sin tiempo real:** adoptar `kotlinx-coroutines-test` (tabla del §8) para sustituir `idleUntil` y los `delay` reales; elimina la familia de cuelgues de CI.
4. **ViewModels sin prueba:** `LibraryViewModel` (subida OK, rechazada, `IOException`, duplicado), `SettingsViewModel` (importar/exportar con error), `DashboardViewModel` (carga, error + *Retry*, `practice`). Con repositorios reales sobre la base dorada en memoria, como `QuizBehaviourTest`.
5. **Integridad y migración:** `IntegrityTest` (H-06); `MigrationTest` ya valida 1→5; añadir v5→v6 si se hace H-03 y una **comprobación en el OPPO** de cada migración nueva (`PhoneMigrationCheck`) antes de instalar.
6. **Escala:** `HotPathScaleTest` ampliado a Dashboard/historial (H-04) y `LongPdfMemoryTest` de 300 páginas (H-07).
7. **Errores:** `IOException` de disco lleno al subir/exportar/importar (H-09).
8. **CI:** mantener compilando las androidTest; añadir un trabajo opcional `workflow_dispatch` que **solo imprima** el recuento de WebP del APK (H-02) cuando existan las escenas, y Dependabot (H-13). Las pruebas de dispositivo siguen siendo manuales en el OPPO (QA app `.qa`), con una lista fija: `connected*` nunca; `adb install` + `am instrument` (como la ronda 1).
9. **Procedimiento de ronda QA:** al final, `dumpsys netstats` de la app QA = 0 B y desinstalar (H-08).

## 7. Hoja de ruta (fases pequeñas e independientes)

| Fase | Contenido | Esfuerzo | Depende de | Notas |
|---|---|---|---|---|
| **R1** | H-01 paso 1 (`fillInk/fillMuted` dentro de rellenos) + `ContrastTest` + capturas oscuras | S | — | 1 PR; regrabar 6–8 capturas |
| **R2** | H-02 `keep.xml` de las escenas + comprobación del APK | S | — | decide también el §9 |
| **R3** | H-09 errores de espacio/subida/export + estado de error de Games | S | — | |
| **R4** | Pruebas: `coroutines-test`, ViewModels de Library/Settings/Dashboard, `IntegrityTest` | M | tabla §8 aprobada | |
| **R5** | H-04 agregados SQL del Dashboard e historial + escala | M | R4 | primero medir |
| **R6** | H-05 transacciones sin CPU y `UPDATE` en lote | S-M | R4 | |
| **R7** | H-03 fase A: `WordProgress` de lectura y definiciones únicas | M | decisión del dueño (§11) | cambia números visibles |
| **R8** | H-07 medir PDF de 300 páginas; fixtures de columnas | S | — | streaming solo si hace falta |
| **R9** | H-03 fase B: `word_state` (migración v6, solo teléfono) | L | R7 | respaldo v1 intacto |
| **R10** | Pantalla de carga: escenas en vector/`Canvas` o exportación 2× (§9) | M | R2 | |
| **R11** | H-01 paso 2 (variantes oscuras) y H-11 (contenedores en oscuro) | M | diseño | cambia tokens |
| **R12** | H-12, H-13, H-08 | S | — | housekeeping |

Orden sugerido: R1 → R2 → R3 → R4 → (R5, R6 y R8 en paralelo) → R7 → R9; R10 y R11 cuando diseño entregue.

## 8. Dependencias o modelos nuevos propuestos (ninguno añadido)

**8.1 `org.jetbrains.kotlinx:kotlinx-coroutines-test`** (solo `testImplementation`)

| Campo | Valor |
|---|---|
| Por qué | `runTest`/`advanceUntilIdle`/`TestScope` para sustituir esperas en tiempo real; ayuda a cerrar la familia de cuelgues de CI (DECISIONS 2026-11-11) |
| ¿Offline? | Sí (solo pruebas) |
| Descarga | solo Gradle en el Mac/CI; nada en el teléfono |
| Tamaño | ~0,3 MB, **0 B en la APK** |
| RAM/CPU/batería | sin efecto en la app |
| Licencia | Apache-2.0 |
| ¿Basta lo nativo? | Parcialmente: `runBlocking` + `idleUntil` (lo actual) funciona pero depende del tiempo real y ya causó cuelgues; no hay equivalente en el JDK |
| Versión | misma que la de `kotlinx-coroutines` que ya llega transitivamente |

**8.2 Dependabot** (configuración de GitHub, no una dependencia del proyecto)

| Campo | Valor |
|---|---|
| Por qué | alertas de CVE y subidas de `sqlite-bundled`, Compose, AGP |
| ¿Offline? | Es de CI/GitHub, no de la app |
| Descarga / tamaño / RAM | ninguno en el teléfono; un `.github/dependabot.yml` |
| Licencia | servicio gratuito de GitHub |
| ¿Basta lo nativo? | Sí, es lo nativo de GitHub; **no** se propone OWASP dependency-check (necesita la base NVD por red y tiempo de CI) |

**Modelos de IA / voces nuevos:** ninguno. La decisión «solo Gemini Nano» y el descarte de Gemma/embeddings (DECISIONS 2026-11-08, RECOMMENDATIONS §6) se mantienen: la coincidencia fuzzy de H-03 se resuelve con `norm()` sin modelo.

## 9. Anexo C — Pantallas de carga «pixeladas»: diagnóstico (sin cambiar nada)

**Datos medidos**

| Dato | Valor |
|---|---|
| Resolución actual del OPPO | **1080 × 2376** (`wm size`; densidad 420, `wm density` base 560). El panel ofrece además **1264 × 2780** (`dumpsys display`: modos a 60/90/120 Hz en ambas): hoy ColorOS está en el modo menor |
| WebP de las 14 escenas | **1080 × 2400** todos (`sips`); `drawable-nodpi`, sin reducción |
| Codificación | *flash1-3*: `splash_art.py:63`, WebP con pérdida `quality=88` (cuantizador base 15, filtro de bucle 3–4); *photo01-11*: `splash_scenes.py:460`, `quality=92` (cuantizador 10; en `photo_05` un segmento con filtro 63, fuerte) · 4:2:0 · 32–55 KB por imagen (0,10–0,17 bits/píxel) |
| Cómo se dibuja | `Image(painterResource, ContentScale.Crop)` + `graphicsLayer` con zoom de 1,00 a 1,06 durante 1,5 s (`Splash.kt:108-111`) |
| Fuente de los *flash* | `picsIcons/flash1.png` 1080×2400 (PNG de 745 KB) |

**Diagnóstico**

1. **A resolución actual, escala 1:1** (1080 de ancho en 1080 de pantalla; `Crop` recorta 12 px arriba y abajo). Por eso un recorte 3× del píxel de la captura del teléfono y del WebP son iguales de blandos: **la app no estropea la imagen al mostrarla**, salvo el zoom (punto 4).
2. **Compresión: no es la causa principal.** El espectro de la fuente y el del WebP tienen la misma energía de alta frecuencia (2,97 % por encima de la mitad de Nyquist en ambos). Varianza del laplaciano: fuente *flash1* **3,6**, WebP 1,4, *flash2* 5,9, mientras que las escenas dibujadas dan **59–139**: los *flash* son blandos de origen (rasterizados a baja frecuencia, degradados y bordes difusos), y además se les aplicó `UnsharpMask` y un cambio de color antes de guardar. No se puede medir «pérdida frente al original» porque la fuente se editó (texto borrado, tono de azul), así que esta conclusión es de energía espectral, no de PSNR.
3. **Cuantización en degradados:** a 0,1 bit/píxel, VP8 4:2:0 con bloques de 16×16 deja **bandas y bloques** en los cielos y el lago, y suaviza el borde fino de la pagoda y la luna (visible en la captura de recorte).
4. **Zoom de 1,06 con muestreo bilineal:** `graphicsLayer` reescala la textura en la GPU cada fotograma; al final hay ~6 % de ampliación y desenfoque. Si el dueño usa el modo **1264×2780**, la escala base ya es 1,17× + 1,06 = **1,24×**, y ahí sí se ve pixelado.
5. **Lo más importante (H-02):** el release solo envía los 3 *flash*; las 11 escenas dibujadas, las más nítidas, **no están en la APK**. Lo que el dueño ve son siempre esas 3.

**Solución técnica propuesta (por orden de coste/beneficio)**

| Opción | Qué | Coste | Efecto |
|---|---|---|---|
| **A** (R2) | `keep.xml` para que salgan las 11 escenas dibujadas | S | 11 de 14 pantallas nítidas |
| **B** | Redibujar los 3 *flash* con las mismas formas de `splash_scenes.py` (vector) y exportar a **1264 × 2780** y 1080 × 2400 (carpeta `drawable-nodpi` + `-xhdpi`, o `Canvas` en tiempo de ejecución) | M | nitidez a cualquier resolución |
| **C** | Quitar o reducir el zoom a ≤ 1,02 y usar `FilterQuality.High` en el `Image` | S | menos desenfoque en 1,5 s |
| **D** | Cuantizar mejor: `quality=95`, `method=6`, **`sharp_yuv`**/`-sharp_yuv`, y dithering de los degradados | S | menos bandas; +30–60 KB por imagen |
| **E** | Dibujar las escenas con `Canvas`/`ImageVector` en la app (sin archivos) | L | resolución infinita; APK −0,45 MB; hay que reescribir `splash_scenes.py` en Kotlin |

Recomendación: **A + C + D ya** (todo S) y **B** si el dueño usa el modo 1264. E solo si se quiere eliminar los archivos del repositorio. Decisión previa que se respeta: las fotos personales y las ilustraciones hechas a partir de ellas no van a GitHub (DECISIONS 2026-11-11): A y D no cambian eso.

## 10. Ficha del recorrido visual (resumen; el informe completo está fuera de git)

Capturas en `qa/ronda-2/capturas/` y análisis por pantalla en `qa/ronda-2/RECORRIDO.md` (fuera de git, citan las notas del dueño). Cubierto: Dashboard, Library (lista, detalles, borrar, All vocabulary), Flashcards (mazos, edición, sesión frente/reverso, borrar), Quiz (formulario, pregunta, acierto/fallo en opción y escrita, resultados, Say it in Korean sin micrófono, resultados con práctica), Teacher y Coach, Grammar, Calendar, Settings, MoreSheet, Games (vacío, pausa, 5 juegos, resumen), estados vacíos, carga. **No cubierto:** juegos restantes en claro, *Say it in Korean* con voz (la habla el dueño), estados de error (permiso denegado, sin espacio: requieren cambiar ajustes del teléfono, prohibido), TalkBack.

## 11. Preguntas abiertas para el dueño

1. **H-01 paso 2:** ¿se aceptan rellenos oscuros propios para acierto/fallo (se aparta de «los colores son los de la web», DECISIONS 2026-11-12)? Si no, solo el paso 1 (tinta oscura dentro del recuadro claro).
2. **H-03:** ¿«dominada» = intervalo ≥ 21 días sin *Again* reciente, o prefieres otra definición? ¿Lo que falles en Games debe poder entrar a un mazo?
3. **H-02/§9:** ¿qué resolución usa el OPPO de verdad (FHD+ 1080 o QHD+ 1264)? Decide si hace falta la opción B.
4. **H-07:** ¿se aceptan PDF con una página con imagen importando el resto con un aviso (diferencia con la web)?
5. **H-13:** ¿activar Dependabot en el repositorio?
