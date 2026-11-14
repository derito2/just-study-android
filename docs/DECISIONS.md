# Decisiones — Just Study Android

Una entrada por decisión, la más reciente arriba: fecha, decisión, por qué y alternativas descartadas. Las de los PoC (fase 1) también van a Engram (`android/*`).

## 2026-11-13 — Ronda 1: el lector y Teacher se apartan de la web
**Decisión del dueño:** en estos puntos la app puede dar algo distinto que la web (`backend/app`), y las doradas que cambien se regeneran:
- **R1-15:** debajo de una palabra, una línea `X -> Y`, `X → Y`, `X - Y`, `X = Y` o `== X` cuya parte coreana no usa la palabra (ni conjugada) es una **nota** de la entrada (sinónimo, comparación), no su ejemplo. La que sí la usa sigue siendo ejemplo (`이 중에서 하나를 고르세요 → Choose one`). También vale para `Example:` con flecha.
- **R1-16:** las frases numeradas dentro de una explicación (trozo `explanation`) ilustran gramática: no son tarjetas.
- **R1-21:** un ejemplo de gramática termina con su coreano; una línea sin hangul que sigue es la continuación de la traducción.
- **R1-01** (Teacher): una palabra sin entrada se busca en tus frases de ejemplo: en coreano dentro de la frase (학교 → 학교가, 학교를) y, en *How do I say school?*, en su traducción. Se responde con la frase y su fuente. *학교를 어떻게 말해요?* se entiende como pregunta por 학교. Si no aparece en inglés, la pregunta sigue al modelo (R1-17).
- **R1-12** (Teacher): al revisar una frase, un punto de gramática cuya estructura lista una forma (`+기 전에`) cuya palabra está en tu frase (전에) sale en *Grammar in your sentence*.
**Cómo llega a tus tarjetas:** solo al reprocesar el documento (`DocumentIndexer.refreshCards`): la tarjeta cuyo ejemplo es ahora una nota toma el ejemplo de la palabra, y la frase de gramática que era tarjeta se quita (sus repasos se quedan: es tu historial). Los mazos del Coach y los ejemplos que vienen de otro documento no se tocan. Lo que se calcula al vuelo (reverso *From your notes*, quiz, Teacher) cambia al instalar.
**Doradas:** ninguna de `golden/` cambia con estas reglas (sus notas sintéticas no tienen esos casos); las pruebas nuevas están en `ReaderRoundOneTest` y `RefreshCardsTest`.
**Descartado:** elegir siempre "la primera frase que contenga la palabra" sin mirar el tipo de línea: cambiaba 34 tarjetas de tus notas en vez de 17, la mayoría sin error (el ejemplo era la propia palabra).

## 2026-11-12 — Fase 7, tareas 6 y 7: robustez en el OPPO y versión 0.9.0
**Todo sobre la copia (app de benchmark), con los ajustes que el dueño autorizó, cada uno restaurado y comprobado:**
- *Background process limit* → *No background processes*: con un quiz a la mitad (1 respondida), ColorOS mató el proceso en segundo plano; al volver, el mismo quiz en la pregunta 2 con la respuesta guardada y sin quiz nuevo (R-008 con el `SavedStateHandle`). La pregunta del modelo, que vive solo en memoria, se pierde (4 de 5): es lo esperado. Los otros dos casos (a mitad de una respuesta del modelo y a mitad de procesar) no se pudieron provocar: ColorOS mantuvo la app viva (adj 430) más de 30 s, el modelo responde en ~3 s y el PDF de 30 páginas se procesa en 0,25 s. Reanudar por etapas tiene sus pruebas en `ProcessingQueueTest`.
- **Modo avión**, sesión completa: Dashboard con el modelo, Teacher (reglas 27 ms), un quiz completo con la pregunta del modelo (2,5 s), el juez y la explicación de errores, una tarjeta calificada y reprocesar un PDF de 30 páginas (288 ms, PSS 259 MB). Todo funciona; solo la descarga de Nano necesita red.
- **PDF largo** (`tools/fixtures/long_pdf.py`: 30 páginas que repiten las de los PDF sintéticos, 224 KB, en `assets/extra`): con la pantalla apagándose, 242 ms, PSS 147 MB. Cierra lo pendiente de la fase 4.
- **Letra al 130 %**: Dashboard, Teacher, Flashcards, una tarjeta y una pregunta del quiz sin textos fuera de pantalla ni botones solapados (volcado de UI).
- **TalkBack**: se activó, pero abrió su pantalla de bienvenida (no se aceptan diálogos por el dueño) y sin oírlo no se sabe qué lee. Queda la revisión por el árbol de accesibilidad (tarea 5).
- **R-015** (tocar la notificación de procesado): sin comprobar. Con 0,25 s por PDF la notificación no da tiempo a tocarla; su `PendingIntent` es explícito a `MainActivity`.
**Tarea 7:** respaldo antes del reinicio (`Download/juststudy-backup-before-reset.jsbackup.zip`). Se borraron los datos de prueba de Code editando una copia del respaldo y cargándola con *Import backup*: 4 quizzes (51–54), sus 28 preguntas, 1 intento, 1 sesión y su tema, el mazo *Your mistakes* y sus 10 tarjetas; el progreso del tema 70 vuelve a su valor anterior. Las 18 tablas de la app quedaron idénticas a lo planeado (sha256), y los PDF y textos, sin cambios. Release 0.9.0 instalada con `install -r` (sin etiqueta, por decisión del dueño); la app de benchmark y los APK de prueba, desinstalados.

## 2026-11-12 — Fase 7, tarea 5: seguridad y accesibilidad
**Seguridad (manifiesto unido de release, skills de permisos e intents):** sin hallazgos. Permisos: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` + `DATA_SYNC`, `BIND_SERVICE` de AICore y el `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` de AndroidX (`signature`). Solo `MainActivity` exported; sin `INTERNET`; no debuggable; `allowBackup=false` y reglas de extracción que excluyen todo; el `PendingIntent` de la notificación es explícito e inmutable; ninguna actividad lee extras de un intent. Logs: solo ids, etapas, tiempos, cuentas y nombres de clase de error.
**Accesibilidad (volcado de UI en el OPPO, sin TalkBack):** Dashboard, Teacher, Flashcards y una tarjeta sin controles sin nombre. Hallazgo arreglado con prueba: los campos de texto eran *edit box* sin nombre (la respuesta del quiz y las búsquedas): el del quiz toma su etiqueta y `JsTextField` toma su *placeholder* como nombre. Con el teclado abierto (Gboard, borde superior y = 1503 px): el campo y *CHECK* del quiz y la búsqueda de *All vocabulary* quedan visibles; Teacher ya se midió en la fase 5.
**Contraste (WCAG, texto normal 4,5:1): los colores no se cambian, son los de la web (decisión del dueño).** Medidos: claro: `faint` 2,1 sobre el fondo (se usa en 1 texto). Oscuro: `correct` 2,95, `wrong` 2,82, `danger` 3,05 y `fillWarn` 2,67 sobre el fondo (se usan en 5, 10, 2 y 1 textos). `correctStrong` (4,2 claro / 3,7 oscuro) solo pasa como texto grande.

## 2026-11-12 — Fase 7: el «cuelgue» de las preguntas del modelo, y los pares aceptados al reprocesar
**Causa (encontrada en ~35 min):** no era ML Kit. Al crear el quiz, `QuestionScreen` cambiaba la ruta `Question(null)` por `Question(runId)` (R-008); era una entrada nueva de navegación con un **ViewModel nuevo**, así que el viejo se limpiaba y su `viewModelScope`, el que escribía las preguntas del modelo (A.3), se cancelaba. Una cancelación no deja log, por eso la puerta no registraba nada. Antes de A.3 las preguntas se escribían antes de crear el quiz y no pasaba. La prueba de Robolectric creaba el ViewModel a mano, sin la navegación.
**Lo que se probó:** `QuestionProbeTest` (instrumentada en `:core:ai`, notas sintéticas) en el OPPO: límite 16 384 tokens, *system prompt* disponible; Coach 113 tokens, preguntas 239 / 354 / 830 tokens (vocabulario 100 y 450 caracteres, gramática 1500): todas `ok` en ~2 s de modelo, con temperatura 0 o por defecto y con 200 o 96 tokens de salida. Luego, logs temporales por etapa en la app de benchmark: la llamada empezaba y no volvía ni el plazo de 6 s: el llamador estaba cancelado.
**Arreglo:** el id del quiz se guarda en el `SavedStateHandle` del ViewModel (sobrevive igual a un proceso matado) y la ruta ya no se reemplaza; en el OPPO la pregunta del modelo llega en ~3 s y el quiz queda con 5. **Además, la puerta ya no depende de que ML Kit atienda la cancelación:** cada llamada corre en su propio scope; al vencer el plazo la puerta suelta el `Mutex` y devuelve la versión sin modelo; la llamada se abandona y su respuesta tardía se ignora; mientras siga viva, la siguiente la espera solo con su propio plazo (`stuck`), y el quiz deja de pedir más preguntas.
**Pares de notas desordenadas al reprocesar:** se perdían (el texto extraído se reescribe desde el PDF). Ahora, al guardar la nueva extracción, las líneas `Coreano = English` del texto anterior que el nuevo no tiene se añaden de nuevo a su página (MuPDF da el mismo texto con el mismo PDF, así que son solo las añadidas). Comprobado en la copia (app de benchmark): `에 = Place or time` sigue tras *Reprocess*. No hay diálogo de reprocesar, por eso se conservan en vez de avisar.

## 2026-11-12 — Fase 6: IA en el teléfono con Gemini Nano
**Puerta (R-013):** módulo `:core:ai`, el único que ve ML Kit (`NoMlKitInScreensTest`). `NanoGate`: una llamada a la vez, 2 s entre llamadas, un reintento con `BUSY`, y ninguna llamada con un PDF en la cola, la app en segundo plano o Nano sin estar *Available*. Todo se valida antes de mostrarse (sin streaming); si el validador rechaza, sale la versión sin modelo, sin aviso. Caché solo en memoria por (versión del prompt, entrada). Los prompts y validadores, uno por uso, en `:core:korean` (`korean/ai`). Logs: uso, tiempo y resultado, nunca texto.
**Plazos finales:** juez 2 s, Coach y Dashboard 3 s, explicación del error, preguntas, Teacher y Grammar 6 s, notas desordenadas 10 s. Cuentan solo la respuesta del modelo: en el OPPO, con la espera de 2 s dentro del plazo, el juez y el Coach vencían siempre (se corrigió y tiene prueba).
**Lo que trae `genai-prompt` 1.0.0-beta5:** `SystemInstruction` (se usa para *"You are an English-speaking tutor…"*), `PromptPrefix`, `countTokens`, salida estructurada (`generateTypedContentRequest`) e `isStructuredOutputFeatureAvailable`. **No se usan:** la salida estructurada pide el compilador KSP `genai-schema-compiler` (dependencia nueva): las salidas son líneas fijas (`QUESTION:`/`ANSWER:`/`WRONG:`, `YES`/`NO`…) que se analizan con estrictez. El prefijo en caché, porque los prompts tienen menos de 200 palabras (regla de la skill). `countTokens`: el presupuesto de Teacher va por fragmentos (4) y caracteres (3500).
**Permiso nuevo (con tu OK):** `com.google.android.apps.aicore.service.BIND_SERVICE`, de `genai-prompt`, para hablar con AICore. Sin `INTERNET`; los componentes que añade ML Kit son `exported=false`. Añadido a `tools/ci/check_manifest.py`.
**Lo que crea el modelo no se guarda como tuyo:**
- las preguntas del modelo (*Multiple choice*, *Short answer*) viven solo en memoria, con id negativo: nunca en `questions` ni en `question_attempts`, ni en el historial ni en tus errores (tu decisión). Como mucho 3 por quiz; las reglas hacen el resto;
- si el juez aprueba una respuesta en inglés que las reglas rechazaron, solo se guarda `is_correct = 1` (tu decisión), nunca su texto;
- las notas desordenadas: pares solo en memoria, con AiMark en *Words detected*, y nunca se vuelven tarjetas (el diseño E no tiene botón de aceptar);
- la frase de práctica (Grammar) lleva *PRACTICE* y no se guarda.
**Sin Nano, las pantallas son las de la fase 5:** la explicación del error, la frase de la semana y los botones de Grammar solo aparecen con Nano *Available* (el plan pedía plantilla sin modelo en el Dashboard y la explicación del punto en el quiz, pero eso cambiaba las pantallas sin Nano, riesgo 2; ninguna tiene sitio en el diseño sin modelo). La única pantalla que cambia sin Nano es el formulario del quiz: *Fill in the blank* queda habilitado siempre (`design/quiz.md` A.2).
**R-017:** `buildBlankFromExample`: la forma del patrón sale, literal y pegada a una raíz, de un ejemplo de tus notas; las opciones son las formas de otros patrones del mismo documento; pasa por `buildBlank`. Solo cuando se pide `fill_blank` (las doradas no lo piden: `QuizGoldenTest` y `ReplayGoldenTest` sin cambios). Con los PDF de prueba salen 4 huecos, todos literales.
**Notas desordenadas (uso 6):** no son una etapa de la cola, sino que corren cuando el documento ya está *ready*: la puerta no llama al modelo con un PDF en la cola. Solo con la app delante: en segundo plano esperan (*Waiting for you to open the app to read messy notes.*) y siguen al volver a Library.
**En el OPPO (modo avión, sobre una copia de tu base; ninguna escritura en la tuya: 18/18 tablas iguales):** el modelo ya estaba *Available* (no hizo falta descargar). Tandas de la PoC 2 por la puerta: 0 `BUSY` tras el reintento (Coach p90 2,4 s; explicación p90 4,5 s; Multiple choice p90 4,5 s; juez p90 1,8 s). Los 8 usos (tiempos con la espera de 2 s incluida): juez 5 llamadas, p50 2,6 s, 5 rechazadas (muy conservador, como en la PoC); explicación del error 4/4 válidas, p50 3,2 s; preguntas del modelo 13,8 s para el lote; Teacher 1 de 4 válida, 3 rechazadas, p50 2,6 s; Coach 3/3, p50 3,7 s; notas desordenadas 1/1; Grammar más sencilla 3/3, p50 4,2 s; frase de práctica 3/3, p50 2,9 s; Dashboard 1/1, 2,8 s. Coreano inventado en pantalla: 0. Juez aprobando coreano: 0. Salir con una llamada en curso: no se cae (AICore corta con error y sale la versión sin modelo). Con un PDF en la cola: la puerta no llama.
**Pendiente / a vigilar:** el lote de preguntas del modelo tarda ~14 s (se ve *Creating your quiz…*); la fase 7 puede bajarlo (menos preguntas o una llamada por dos).

## 2026-11-11 — Datos de prueba en la app del OPPO: se quedan
**Decisión (del dueño):** el quiz respondido (un fallo), el mazo *Your mistakes* (Made by Teacher) y el historial de pruebas de la fase 5 se quedan en la app. Se reinician solo cuando el dueño lo diga, al final. Desde ahora, en el OPPO no se responden quizzes, no se califican tarjetas ni se crean mazos; si una prueba escribe datos, usa una copia de la base y la restaura.

## 2026-11-11 — Teacher con el teclado abierto (`design/teacher.md`, punto 6) y gramática en coreano en el OPPO
**Causa de los 26 px:** `imePadding` de la pantalla sumaba la barra inferior (que queda bajo el teclado) a la altura del teclado. `AppScaffold` ahora hace `consumeWindowInsets(padding)`: el composer queda justo encima del teclado en todas las pantallas.
**Punto 6:** con el teclado abierto, JsTopBar plegado (64 dp, sin subtítulos), sin selector de modo, *Clear answers*, la línea de documentos ni las plantillas (se eligen antes de abrirlo: al tocar una, el campo toma el texto y se abre el teclado); vuelven con un fundido de 150 ms; la conversación baja al último mensaje. Prueba de Compose con el tamaño del OPPO menos Gboard (411 × 526 dp): ≥ 300 dp.
**En el OPPO (Gboard, 333 dp; pantalla 905 dp a 420 dpi):** conversación de **394 dp con el campo vacío** y **399 dp con texto** (559 dp con el teclado cerrado). Antes, con las plantillas visibles con el campo vacío, eran 294 dp: por eso diseño las oculta también.
**Gramática en coreano:** `TeacherGrammarTest` (instrumentada, `performTextInput`, sin teclado ni ajustes) toma la primera regla de tus notas y la pregunta: responde por `grammar.v1` con fuente (78 ms, primera pregunta con el índice). Tu base, 18/18 tablas iguales (filas y sha256 de la exportación) antes y después.

## 2026-11-11 — R-010 descartada: sin vibración
**Decisión (del dueño):** el teléfono no vibra nunca (`design/shell.md`, *Sin vibración*). Se quitan las cuatro llamadas a `performHapticFeedback` (`RatingButtons`, `QuizOption`, `TypedResult`, `StudyScreen`): se revierte el commit de R-010. El *wiggle* de editar mazos es solo animación y no cambia.
**Por qué:** en el OPPO la vibración táctil del sistema está apagada y el dueño no quiere vibración en la app.

## 2026-11-12 — Fase 5: Teacher y Coach sin modelo
**Respondedores (tarea 1):** `:core:korean/teacher` porta `rag/tutor.py` (las respuestas sin modelo), `rag/entries.py` y `conjugation.lemmas` como una lista ordenada de `Responder`, en el orden de `Tutor.ask_stream`: punto con ese nombre exacto (partículas), comparar, entrada de la palabra (o su verbo, o los ejemplos donde sale), forma conjugada, gramática (o tus frases que la usan), frase en inglés, revisar frase, palabra solo en ejemplos, palabra coreana sin entrada, nada cercano (`no_context`) y, al final, lo que respondería el modelo (fase 6): hasta entonces sale el mensaje de la web (`not_found`, marcado `NEEDS_MODEL`). Se agrupan en cuatro archivos (`Search`, `Entries`, `NotesIndex`, `Teacher`) en vez de uno por respondedor: comparten estado de la pregunta (`Turn`, perezoso).
**Búsqueda (R-014):** `NotesSearch` sobre `notes_fts` sustituye a Chroma: las raíces de la pregunta como prefijos (`"학교*" OR "공부해*"`; en FTS4 el `*` va dentro de las comillas) y un fragmento solo cuenta para una raíz coreana si una de sus palabras, sin terminación ni partícula, es esa raíz (`학` no encuentra `학생`). Sin búsqueda por significado en el teléfono: se ordena por raíces distintas y luego por `rowid`. Con las doradas el prefijo basta (no hizo falta cambiar de tokenizador); con tus notas también: el selfcheck en el OPPO (tarea 4, `SelfCheckTest` sobre una copia de tu base en el paquete de pruebas) hizo 248 preguntas sobre 141 fragmentos, 0 fallos (ni coreano inventado ni citas inválidas). Teacher p50 45 ms y p90 109 ms (la primera, en frío, 1,3 s); Coach p50 75 ms y p90 111 ms; índice en 438 ms. Tu base, con el mismo sha256 antes y después. **En frío** (app cerrada con `force-stop`, abierta y primera pregunta de Teacher, 3 veces): la app abre en 301–310 ms y la primera respuesta de una palabra tarda 14–18 ms (`teacher_answer` en logcat: solo tiempos y ruta, nunca texto). El índice de gramática y frases se construye solo cuando una pregunta lo necesita (438 ms en el selfcheck), así que la primera pregunta de gramática tras abrir en frío puede tardar ~0,5 s.
**Doradas:** 454/454 respuestas iguales en texto y fuentes con el almacén del selfcheck de la web (`MemoryStore`, md5) y también 454/454 con `notes_fts` (`TeacherFtsGoldenTest`): no hubo diferencias de fuentes que revisar.
**Coach (tarea 2):** `:core:korean/coach` (intención, herramientas planeadas, categorías y los textos que escribe el código) y `CoachEngine` en `:core:data` (las 6 herramientas sobre los repositorios). Sin modelo hace lo mismo que la web cuando el modelo no responde: las herramientas planeadas (para *How I decided*) y el plan determinista (`_fallback`). Doradas nuevas `golden/coach.json` (`tools/golden/export_coach_golden.py`: el `StudyAgent` de la web con el modelo siempre caído sobre la base sintética de `golden/db`): 53/53 frases iguales.
**Para la fase 6 (necesitan embeddings):** temas libres sin categoría fija (*sports*, *quantum physics*) y temas en coreano que no son palabras de tus notas (*가족*, *음식*, *날씨*): 5 de 58 frases, marcadas `needsEmbeddings`. En el teléfono responden con las palabras que lo dicen en su significado o "Your notes don't have words about…", nunca con el texto de la web sobre Chroma. Los tipos de palabra que la web decide con el modelo (acaban en 다 sin "to") tampoco entran.
**Pantalla (tarea 3):** según `design/teacher.md`, sin AiMark. Una pregunta clara va sola a su sitio (`route`); el modo solo decide si no está claro. *Start quiz* genera con los tipos por reglas; *Create deck* crea copias (`createCustomDeck`, de `create_custom_deck`). La nota *"AI answers are paused while a PDF is processing."* no se muestra: sin modelo no hay nada en pausa (fase 6).

## 2026-11-12 — Fase 5, tarea 0: el resto de índices de R-002 (base v4) y el control en el teléfono
**Decisión:** `tools/data/gen_room_entities.py` toma los índices de las columnas `index=True` del web (menos `deck_places`, tabla pequeña) y añade `question_attempts(question_id)` y `flashcard_reviews(flashcard_id, reviewed_at)`. `MIGRATION_3_4` solo crea esos 11 índices; `MigrationTest` va de v1 a v4 con las filas iguales y valida contra `schemas/4.json`.
**Control en el OPPO (v2 → v3 y v3 → v4):** copia de la base (`juststudy.db` + `-wal`, `datastore/`, `backup-files/`) en la carpeta privada de la app (`files/pre-v3-snapshot/` y `files/pre-v4-snapshot/`, con sha256 comprobados) antes de instalar. La exportación de la app solo sale por el selector del sistema, que no llega a la carpeta privada, así que la comparación la hace `PhoneMigrationCheck` (androidTest de `:core:data`, se salta si no hay copias): exporta con `BackupExporter` la copia de antes y la base ya migrada por la app (comprueba que está en la versión nueva antes de abrirla con Room), y compara filas y sha256 de las 18 tablas, más las filas leídas en crudo. Resultado: 18/18 iguales en las dos migraciones; 23 pruebas instrumentadas, 0 fallos (`ReplayGoldenTest` incluida; `PhoneMigrationCheck` saltada sin copias). Las copias se pasan entre la app y el paquete de prueba con `run-as … cat | run-as … cat >`, sin almacenamiento compartido, y se borran del paquete de prueba al terminar.
**Sin hacer:** el `EXPLAIN QUERY PLAN` de R-002 no corre con el driver de Robolectric; el índice lo valida `runMigrationsAndValidate` contra el esquema.

## 2026-11-12 — R-016: firma de release fuera del repo
**Decisión:** clave de release en `~/.android/juststudy-release.jks` (PKCS12, RSA 4096, 10 000 días, `CN=Just Study`, alias `juststudy`); ruta y contraseñas en `~/.gradle/gradle.properties` (`JUSTSTUDY_RELEASE_*`, permisos 600). `app/build.gradle.kts` crea `signingConfigs.release` solo si existe `JUSTSTUDY_RELEASE_STORE_FILE`: en el Mac `assembleRelease` da `app-release.apk` (SHA-256 del certificado `b830e028…a551e`); en el CI, sin esas propiedades, sigue saliendo `app-release-unsigned.apk`. Ni la clave ni las contraseñas entran al repo; el dueño respalda **los dos archivos juntos**: perder la clave obliga a desinstalar para actualizar.
**Paso único debug → release (lo hace el dueño; Claude Code no desinstala):** (1) *Settings → Export backup* y comprobar que el `.jsbackup.zip` se guardó; (2) desinstalar la app de debug (borra sus datos: la firma cambia y Android no deja actualizar encima); (3) `adb install app/build/outputs/apk/release/app-release.apk`; (4) *Settings → Import backup* con ese archivo: deben volver las 18 tablas con los mismos sha256. Desde entonces se actualiza siempre con release y la misma clave (`adb install -r`), sin perder datos.
**Alternativas descartadas:** firmar en el CI (pondría la clave en GitHub); usar la clave de debug para release (vive solo en este Mac y Android Studio puede regenerarla).

## 2026-11-11 — R-001: consultas dirigidas en calificar, responder y la lista de mazos
**Decisión:** `db/HotDaos.kt` (a mano; se registra desde `tools/data/gen_room_entities.py` con `APP_DAOS`) trae las consultas que sustituyen a «cargar la tabla entera y filtrar en Kotlin» en `review`, `reviewedIds`, el mapa `last` de `decksNow`, `history`, `attachSession`, `recomputeTopic`, `QuizRepository.answer` y `QuizRepository.run`. `review` y `answer` corren en `Dispatchers.IO`. Ninguna dorada cambió (18 tablas idénticas); en el OPPO, las 22 pruebas instrumentadas de `:core:data` pasan.
**Medido (JVM, `HotPathScaleTest`, 50 000 reseñas y 20 000 intentos sintéticos sobre la base dorada, mejor de 25):** calificar 1,6 ms → 1,3 ms (0,96 → 0,81 en el CI); responder, ver (2).
**Desviaciones:** (1) `recomputeTopic` no trae todos los intentos del tema: los totales los cuenta SQLite (`topicTotals`) y solo se leen los últimos 10. (2) Sin índice, responder era 8,7× en el CI (1,6 ms → 14 ms): SQLite recorría todos los intentos. Con el usuario se decidió traer a este PR el índice `question_attempts(question_id)` de R-002 (base v3, `MIGRATION_2_3`, solo `CREATE INDEX`; `INDICES` en el generador). Con él, responder 1,2 ms → 0,9 ms con los 20 000 intentos en otros temas (sin el índice, la misma prueba da 2,6× y falla). La prueba exige 2× y pone el historial sintético en otros temas: responder recuenta los intentos de su propio tema (por diseño, igual que el web), así que ese coste sí crece con el historial de ese tema; llevar totales acumulados en `user_topic_progress` lo dejaría plano si algún día importa. Los demás índices de R-002 siguen pendientes. (3) La prueba mide la mejor repetición, no la mediana: con la mediana fallaba por ruido al correr junto a las demás.
**Sin hacer:** no se midió el código anterior con los mismos datos, y la hipótesis de que el filtrado corría en el hilo principal no se comprobó (ahora da igual: va en IO).

## 2026-11-11 — Lote C: el formulario de Quiz dice la verdad y el quiz a medias sobrevive
**R-007:** `MODEL_TYPES` = *Fill in the blank* y *Short answer*. Hasta la fase 6 se ven al 50 %, sin marcar, con la nota *Needs on-device AI* y sin responder al toque; *Difficulty*, igual (muestra lo guardado). Solo cuentan los 6 tipos por reglas (`QuizForm.usableTypes()`), tanto para habilitar *Generate quiz* como al pedir el quiz. La preferencia guardada no se toca. Capturas regrabadas por el cambio de diseño (`design/quiz.md` A.1): `quiz_home`, `quiz_home_full`, `quiz_home_130`.
**R-008:** al crear un quiz, la ruta guardada pasa de `Question(null)` a `Question(id)`; al abrir un quiz por su id se cargan sus respuestas guardadas (`QuizRepository.results("run-id")`, no hizo falta `answersOf`) y se sigue en la primera pregunta sin responder. El texto a medio escribir no se guarda.

## 2026-11-11 — El lector con los 5 PDFs reales de la biblioteca
Revisado en solo lectura con el lector de referencia (el del web, igual al de Kotlin por las doradas), pasando el texto extraído en memoria y sacando solo números: los 5 documentos listos dan tarjetas o puntos de gramática en todos los fragmentos con coreano; ninguno espera al modelo. Total: 472 tarjetas de vocabulario, 23 puntos de gramática, 441 ejemplos. Patrón a vigilar: una línea corta «coreano = inglés» sin número, debajo de una entrada numerada, se guarda como ejemplo de esa entrada y no como tarjeta propia (50 casos); es la regla del web, no un fallo del port.

## 2026-11-11 — CI colgado: el wiggle se congela en las capturas
**Problema:** `:app:testDebugUnitTest` no terminaba en el CI (cancelado a los 45 min en `main` dos veces y en el PR #20; otras veces tardaba 18 o 33 min). Las capturas `decksEdit` y `mergeDialog` tienen el wiggle del modo edición, una animación infinita: antes de capturar, Roborazzi espera a que el looper quede libre y Compose sigue pidiendo fotogramas sin fin (volcado de hilos: el hilo principal en `Recomposer.runRecomposeAndApplyChanges`, llamado desde `captureScreenIfMultipleWindows` → `ShadowPausedLooper.idle`). En el Mac eran 3 min por prueba; en el CI, a veces para siempre.
**Decisión:** `LocalFrozenMotion` (`ui/theme/Motion.kt`), que solo ponen las pruebas de captura: con él, `DeckCard` dibuja el wiggle quieto en su primer fotograma (−0,6°). Las dos pruebas pasan de minutos a 1–3 s. Se regrabaron `flashcards_decks_edit.png` y `flashcards_merge.png` (con permiso del dueño): la imagen anterior estaba tomada en un momento arbitrario de la animación; solo cambia la inclinación de las tarjetas (≤ 1,2°). La tolerancia no se tocó.
**Además:** Gradle imprime cada prueba al empezar y al terminar y corta la tarea de pruebas a los 20 min; el CI corta el paso de Gradle a los 35 min y sube siempre (`if: always()`) las imágenes de diferencias y el informe de pruebas.
**Regla:** ninguna captura con una animación infinita en marcha.

## 2026-11-11 — Lote A+B de la auditoría: dos desviaciones de lo propuesto
**R-005 (wiggle de los mazos):** no se lee el ángulo en la fase de dibujo (`graphicsLayer { rotationZ }`), como proponía la auditoría: así las tarjetas se dibujan con un suavizado distinto y la captura `flashcards_decks_edit` deja de coincidir. Se mantiene `Modifier.rotate(angle)` y `DeckCard` queda como una envoltura fina que pasa el modificador a `DeckCardBody`: en cada fotograma solo se recompone la envoltura; el cuerpo (textos, botones, campo) se salta porque recibe los mismos argumentos. Sin comprobar en Layout Inspector.
**R-011 (Clear search):** el botón pasa a 48 dp y el campo conserva la altura que ya tenía con el botón de 32 dp (56 dp con texto, 48 dp vacío), para no cambiar las capturas. Ese salto de 8 dp al escribir ya existía; si diseño quiere el campo siempre a 48 dp, es un cambio de diseño aparte.
**R-006:** comprobado en el OPPO: con *Type the answer* y el teclado abierto (borde superior en y=1503 px), *Show answer* quedaba en y=2105 px, tapado. Con `imePadding()` queda en y=1348 px y el campo en 969–1095 px.
**R-003:** la APK de release baja de 53,4 MB a 18,6 MB (solo `arm64-v8a`).

## 2026-11-11 — Procesado de PDFs: tiempo medido; el segundo plano sigue sin probar con un PDF largo
**Medido en el OPPO (debug):** `Gramatica_semana_14.pdf` del repo (2 páginas, 61 KB) tarda 0,27 s desde que se copia el PDF hasta la última escritura en la base. Terminó antes de que la app pasara a segundo plano (0,7 s después), así que esta prueba **no** demuestra que el procesado siga con la app en segundo plano o con la pantalla apagada.
**Pendiente:** repetirla con un PDF de cientos de páginas (en el repo no hay ninguno; todos pesan 60–70 KB). Lo ya comprobado sigue siendo lo de la entrada del 2026-11-09: matado en EXTRACTING, al reabrir continúa y termina.

## 2026-11-11 — Pantalla de carga: un toque la cierra; no retrasa el arranque
**Decisión:** la pantalla de carga se queda (1500 ms, imagen al azar) y un toque la cierra con un desvanecido de 150 ms (`design/shell.md`, R-018). Las 11 imágenes personales se buscan por nombre (`getIdentifier`) y solo están en el APK si existen en el Mac al compilar.
**Por qué (medido en el OPPO, debug, `am start -W`, 5 arranques en frío cada uno):** con pantalla de carga 305, 291, 300, 300, 299 ms (mediana 300); sin ella 303, 299, 299, 298, 301 ms (mediana 299; una medida de 0 justo tras instalar se descartó). La pantalla se dibuja encima de la app ya compuesta, así que no retrasa el primer fotograma; lo que añade es hasta 1,5 s antes de poder tocar, y por eso el toque la cierra. En release se mide cuando el dueño haga el cambio debug → release.
**Descartado:** la API SplashScreen del sistema (no admite una imagen al azar a pantalla completa ni el texto animado).

## 2026-11-11 — Respuestas a las preguntas de la auditoría (`docs/RECOMMENDATIONS.md`, sección 7)
**Decisión (del dueño):**
1. **R-007:** hasta la fase 6, *Fill in the blank*, *Short answer* y *Difficulty* se ven deshabilitados con la nota **Needs on-device AI** (`design/quiz.md`, A.1).
2. **R-010:** vibración sí, solo en las acciones de `design/shell.md` (Respuesta háptica). *Cambiado el mismo día: sin vibración (entrada "R-010 descartada").*
3. **R-013:** la caché del modelo va solo en memoria, nunca en disco.
4. **R-014:** se cambia de tokenizador (y se reindexa) solo si la búsqueda por prefijo falla con las notas reales; antes de cambiarlo se para y se avisa.
5. **R-016:** la clave de release se crea ya, fuera del repo. El cambio debug → release lo hace el dueño después, con respaldo antes; Claude Code no desinstala nada.
6. **R-017:** sí, *Fill in the blank* por reglas, en la fase 6.
7. **R-018:** la pantalla de carga dura 1500 ms. Ni las 11 fotos personales ni las ilustraciones hechas a partir de ellas van a GitHub: `picsIcons/`, `splash_photo_*.webp` y el script que las dibuja están en `.gitignore`; la app las muestra solo si existen al compilar (se buscan por nombre) y el CI compila solo con las 3 flash.
8. **Nombre visible:** `app_name` = **Just Study 한국어**.
**Por qué:** cierran las 8 preguntas abiertas de la auditoría del 2026-11-10.

## 2026-11-09 — Procesado de PDFs sin WorkManager: servicio propio en primer plano
**Decisión:** la cola (`ProcessingQueue`) procesa un PDF a la vez por etapas (PENDING → EXTRACTING → PARSING → CHUNKING → INDEXING) dentro de `ProcessingService`, un servicio en primer plano de tipo `dataSync` con su notificación de progreso. Cada etapa guarda su resultado y la siguiente en `processing_jobs` antes de seguir; al abrir la app, si queda trabajo, continúa desde la última etapa guardada.
**Por qué:** WorkManager adquiere un wake lock para el trabajo en primer plano (`Processor.startForeground`), así que necesita `WAKE_LOCK`, que no está entre los permisos permitidos (decisión del usuario). En el OPPO: matado con `am force-stop` en EXTRACTING, al reabrir sigue desde EXTRACTING y termina. `ProcessingQueueTest` cubre la reanudación, el mismo PDF dos veces, borrar mientras procesa y el PDF rechazado.
**Descartado:** WorkManager con `WAKE_LOCK`; WorkManager sin primer plano (sin notificación de progreso y ColorOS lo corta igual).

## 2026-11-09 — Permisos de notificación y servicio en primer plano
**Decisión:** el manifiesto unido solo gana `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_DATA_SYNC` (más el permiso propio `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`). `check_manifest.py` pasa a una lista de permitidos: cualquier `android.permission.*` fuera de esos tres y `USE_BIOMETRIC` falla. Las notificaciones se piden la primera vez que subes un PDF; si las niegas, se procesa igual sin notificación. La notificación solo dice la etapa, nunca nombres de archivo ni texto.
**Por qué:** plan, punto 8. Comprobado en el manifiesto unido de debug y release.

## 2026-11-09 — Room v1 → v2: solo tablas del teléfono
**Decisión:** la v2 añade `notes_fts` (FTS4, `unicode61`, para Teacher en la fase 5) y `processing_jobs`. Ninguna de las 18 tablas del respaldo cambia, así que el formato `.jsbackup.zip` v1 sigue igual. El índice no va en el respaldo: se rehace desde `extracted/` al importar y, una vez, tras la migración.
**Por qué:** `MigrationTest` (todas las filas sobreviven, esquema v2 válido, `MATCH` en coreano). En el OPPO, con tus datos: exportar tras instalar la v2 da las mismas 1265 filas y los mismos sha256 que antes.
**Descartado:** una columna de etapa en `documents` (cambiaría una tabla del respaldo y su sha256 frente al web).

## 2026-11-09 — Tablas sin pdfplumber: cuadrícula de líneas dibujadas
**Decisión:** `:core:pdf` saca las tablas de los segmentos dibujados de MuPDF (trazos y barras finas rellenas, como exporta LibreOffice): una tabla por grupo de líneas que se tocan, celdas entre líneas consecutivas, celdas unidas donde falta la línea entre dos (el texto va a la primera celda; el resto, vacío, como el `None` de pdfplumber) y `table_to_text` del web. Solo en páginas con al menos 6 elementos de dibujo, como el web.
**Por qué:** las tablas de `Tablas_sinteticas.pdf` salen iguales a pdfplumber (filas, texto y bbox a ±1 pt). Los 6 PDFs de prueba no tienen tablas rejilladas; su texto, títulos y cuentas son idénticos a `golden/parser`.
**Notas (⚠️):** las celdas unidas y varias tablas por página no tienen dorada del web (no hay PDF de prueba con ellas). Si aparece uno real, se añade a `golden/` y se compara.

## 2026-11-09 — Diferencias aceptadas con el web en la fase 4
- **Mensaje del PDF repetido:** *"This PDF is already in your Library."* (el de `design/library.md`); el web añade el nombre entre paréntesis.
- **Error inesperado al procesar:** además de marcarlo *Failed*, se borra lo que hubiera dejado (el web solo lo hace con los `PdfError`), para no dejar nada a medias.
- **Al arrancar:** el web marca como *Failed* los documentos que quedaron procesando; aquí se reanudan desde su etapa (riesgo 1 del plan).
- **`golden/db/base`** guarda `documents.warnings` como `null` porque se escribió sin pasar por `process_document`; el pipeline real (web y teléfono) guarda `[]`. La prueba compara el resto de columnas.

## 2026-11-09 — Caché en memoria de los fragmentos de tus notas
**Decisión:** `NotesSource` (ahora `@Singleton`) guarda en memoria los fragmentos de cada documento. La clave es la fecha de modificación, el tamaño de `extracted/<id>.json` y el nombre del documento, así que se invalida sola cuando un *Import* o el procesado de un PDF reescriben ese archivo. Al abrir Quiz, `QuizRepository.warmUp` la llena en segundo plano.
**Por qué** (OPPO, APK de release, tus datos, 10 preguntas): sin caché, 330–426 ms (mediana ≈ 362), por encima de la meta de 300 ms. Con caché, del segundo quiz en adelante: 214–257 ms. El primer quiz tras abrir la app, con la precarga: 246–286 ms (6 medidas). Los resultados no cambian: las doradas siguen en verde y `NotesSourceTest` comprueba la invalidación.
**Descartado:** invalidar a mano desde `BackupImporter` (cambiaba su constructor y las pruebas, y la fase 4 tendría que acordarse de llamarlo); guardar los fragmentos en Room (más esquema para algo que se recalcula en ~115 ms).

## 2026-11-09 — Regex: UNICODE_CHARACTER_CLASS solo donde existe
**Decisión:** `:core:korean` solo usa `Pattern.UNICODE_CHARACTER_CLASS` en la JVM. En Android, la regex (ICU) no acepta esa opción y lanzaba una excepción al generar un quiz; allí `\w` y `\s` ya funcionan con Unicode.
**Por qué:** en la JVM las pruebas pasaban, pero en el OPPO la app se cerraba. Para comprobarlo, las doradas de BD también corren en el teléfono (`connectedAndroidTest`): 16/16, incluida la repetición de las 290 operaciones del web con las 18 tablas idénticas.
**Descartado:** quitar la opción en todas partes (en la JVM `\w`, `\s` y `\d` volverían a ser solo ASCII, a diferencia de Python).

## 2026-11-09 — lifecycle-viewmodel-navigation3 para un ViewModel por pantalla
**Decisión:** se añade `androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.11.0 (`rememberViewModelStoreNavEntryDecorator`): cada entrada de la pila tiene su propio ViewModel, que se borra al salir de ella.
**Por qué:** sin él, todas las pantallas compartían el ViewModel de la actividad: un segundo *Generate quiz* o un segundo mazo enseñaban el anterior. Es la librería oficial de Navigation 3, de la familia `lifecycle` que ya se usa. El manifiesto unido sigue sin permisos nuevos.
**Descartado:** gestionar a mano un `ViewModelStore` por entrada (más código para hacer lo mismo que el decorador oficial).

## 2026-11-09 — undoDelete para el Undo del Snackbar (no existe en el web)
**Decisión:** `FlashcardRepository.delete(deckId)` devuelve un `DeletedDeck` con todo lo borrado (tarjetas, repasos, mazos del coach, lugar y grupo del mazo combinado) y `undoDelete(deleted)` lo vuelve a meter tal cual, con los mismos ids.
**Por qué:** en el teléfono, borrar un mazo muestra un Snackbar con *UNDO* (sistema de diseño, Feedback: "Snackbar para lo pasajero; diálogo solo para lo que no se puede deshacer"). El web borra sin vuelta atrás, así que esta función no tiene dorada; se prueba con la pantalla de mazos (tarea 4).
**Descartado:** un diálogo de confirmación antes de borrar (el diseño lo reserva para lo irreversible) y una papelera con tablas nuevas (más esquema para algo que dura segundos).

## 2026-11-09 — Quiz history: se borran los quizzes de más de 15 días al leer, como en el web
**Decisión:** `QuizRepository.historyNow` hace lo mismo que `quiz_service.history`: antes de leer, borra los quizzes (`quiz_runs`) de más de 15 días y deja sus respuestas sin quiz (`quiz_run_id` a null). Las preguntas y tus respuestas se quedan: de ellas salen el progreso, la racha, el calendario, los 7 días de descanso y tus debilidades.
**Por qué:** mismo comportamiento que el web, comprobado con las doradas: el `fixture` de `golden/db` ya viene podado, y `ReplayGoldenTest` llama a `historyNow` antes de comparar las 18 tablas.
**Descartado:** podar en una tarea aparte (WorkManager): sería una pieza más para el mismo resultado, y la pantalla de historial es justo donde importa.

## 2026-11-08 — PyRandom (Mersenne Twister de Python) en :core:korean
**Decisión:** los constructores del quiz por reglas (`buildFromPair`, `buildBlank`, `buildGrammar`) reciben un `PyRandom`: el `random.Random` de Python portado (MT19937 con la semilla de CPython 3.12, `getrandbits`, `_randbelow`, `shuffle` y `sample`). Hereda de `kotlin.random.Random`.
**Por qué:** con la misma semilla, los constructores dan las mismas opciones y en el mismo orden que el web, así que sus pruebas doradas se pueden comparar campo a campo. Comprobado con 826 casos dorados (`golden/builders.json`) y con 6 semillas de azar puro (`golden/random.json`), incluidas semillas de más de 32 bits (tarea 1 de la fase 3, PR #15).
**Descartado:** `kotlin.random.Random`: su `nextInt` y su `shuffle` usan otros algoritmos y no coinciden con Python, así que las doradas de los constructores no se podrían comprobar.

## 2026-11-08 — Respaldo: reemplaza todo, con confirmación, y conserva extracted/ y pdfs/
**Decisión:** *Import backup* (Settings) reemplaza **todo** lo de la app por el `.jsbackup.zip` v1 elegido con SAF, tras el diálogo *"This replaces everything in the app with the backup. Continue?"* (*Cancel* gris / *Import* rojo). Primero verifica sin escribir el manifiesto y el sha256 de cada tabla, de cada `extracted/` y de cada `pdfs/`. Después, en **una sola transacción**, borra e inserta las 18 tablas. Los `extracted/` y `pdfs/` se guardan tal cual en el almacenamiento privado (`files/backup-files/`, con sus sha256) y solo sustituyen a los anteriores si la transacción termina bien. *Export backup* los vuelve a meter.
**Por qué** (tarea 5, PR #13, OPPO, build debug): `BackupTest` 6/6 (importar todo, ida y vuelta idéntica, dos importaciones sin duplicar, un PDF o un ZIP sin manifiesto da `NotABackup` sin tocar la base, sha256 cambiado da `ChecksumMismatch` y deshace todo, `extracted/` y `pdfs/` idénticos a la vuelta). Con el respaldo real, desde la app: 1265 filas de 18 tablas; la exportación pesa 579 433 bytes (el original, 579 034) y sus 26 entradas son idénticas byte a byte, con los mismos sha256.
**Descartado:** fusionar con lo que ya hay (sin claves estables entre el web y el teléfono, duplicaría); importar solo las tablas (perdía los textos extraídos y los PDF).

## 2026-11-08 — Roborazzi para las capturas de la UI
**Decisión:** capturas de los componentes en la JVM con Roborazzi 1.78.0 sobre Robolectric 4.17 (`@Config(sdk = [36])`, 411 dp, 420 dpi), en claro y oscuro, y la barra superior e inferior al 130 %. Se graban con `recordRoborazziDebug`, se commitean en `app/src/test/screenshots/` y el CI las verifica con `verifyRoborazziDebug`.
**Notas:** `roborazzi-compose` registra su propia actividad en Robolectric, así que no hace falta `ui-test-manifest` (metería una actividad exported). En JDK nuevos Robolectric necesita `--add-opens java.base/java.io` y `--add-exports java.base/jdk.internal.access` (solo en las pruebas JVM). Para verificar de verdad en local: `--rerun-tasks`, si no Gradle saca el resultado de la caché.
**Descartado:** Paparazzi (no se probó) y capturas en el teléfono para comparar (lentas y dependen del tema del sistema).

## 2026-11-08 — Room 3 para la base de la app
**Decisión:** Room 3.0.3 (`androidx.room3`, KSP 2.3.13, `BundledSQLiteDriver` de `sqlite-bundled` 2.7.1), `JustStudyDatabase` v1 con `exportSchema` (esquema en `core/data/schemas/`). Las 18 entidades, sus DAO y el esquema del respaldo se generan desde `models.py` del web (`tools/data/gen_room_entities.py`), con las mismas columnas; fechas en epoch ms UTC y JSON como texto.
**Por qué:** la PoC 3 (entrada de Room más abajo) y la tarea 5: compila con Kotlin 2.4.0 y AGP 9.5.1, y el esquema generado es idéntico al de la PoC.
**Descartado:** escribir las entidades a mano (se desincronizan del web).

## 2026-11-08 — Navigation 3 con una pila por pestaña
**Decisión:** Navigation 3 1.2.1 (`navigation3-runtime` y `-ui`), sin librerías extra: una pila por pestaña (receta *multiple back stacks*), "exit through home", volver a tocar la pestaña activa la deja en su raíz, y Grammar, Calendar y Settings se apilan encima de la pestaña actual. Las pilas se guardan con `rememberSaveable` por nombre de enum (sin kotlinx-serialization). Back predictivo activado (`enableOnBackInvokedCallback`).
**Por qué:** tarea 3: `NavigationTest` 6/6 en el OPPO.
**Notas:** el gesto de Back predictivo no se pudo ver: el OPPO usa navegación de 3 botones y cambiarlo es un ajuste del teléfono.
**Descartado:** Navigation 2 (plan, punto 5).

## 2026-11-08 — Kotlin 2.4.0
**Decisión:** Kotlin 2.4.0 en todo el proyecto (el KGP se fija desde el catálogo en el `buildscript` raíz; el plugin de Compose con la misma versión). AGP 9.5.1, Compose BOM 2026.09.00, Hilt 2.60.1, KSP 2.3.13 y Room 3.0.3 compilan con él.
**Por qué:** ML Kit genai-prompt 1.0.0-beta5 (Gemini Nano, fase 6) está compilado con metadatos de Kotlin 2.4; el Kotlin que trae AGP 9.5 (2.2) no lo lee. Se fija ya para no cambiarlo dos veces.
**Descartado:** genai-prompt beta4 con Kotlin 2.3.

## 2026-11-08 — Gemma 4 E2B no hace falta
**Decisión:** la tarea 4 de `docs/POC_PLAN.md` (Gemma 4 E2B con LiteRT-LM) se descarta. No hay nivel 2 de IA.
**Por qué:** Nano pasó la PoC 2: está disponible en el OPPO, funciona sin `INTERNET` y en modo avión, y cumple la latencia (ver la entrada siguiente). La tarea 4 solo tocaba si Nano no estaba, no funcionaba sin red o fallaba la latencia.
**Descartado:** probar Gemma solo para mejorar calificar por significado (~2.6 GB más en el teléfono para un uso que ya funciona como "solo aprobar").

## 2026-11-08 — Gemini Nano (ML Kit genai-prompt 1.0.0-beta5) como nivel 1
**Decisión:** la IA del teléfono es Gemini Nano por ML Kit Prompt API (`com.google.mlkit:genai-prompt` 1.0.0-beta5), sin `INTERNET`. Por uso (plan, punto 10.1):
- **Sí:** Coach, explicación (explicar tu error, Grammar a), preguntas Multiple choice y Teacher con fragmentos.
- **Calificar por significado** (Quiz y Type the answer): **solo para aprobar**. El umbral de aciertos no aplica: solo actúa cuando las reglas ya rechazaron, así que lo que importa son los falsos positivos.
- **Sin medir:** notas desordenadas y frase de práctica; se miden en la fase 6.

**Por qué** (PoC 2, `docs/poc/2-nano.md`, OPPO, build debug; 150 llamadas con wifi y 50 en modo avión):
- `AVAILABLE` tras la descarga por AICore; `INTERNET` y `ACCESS_NETWORK_STATE` fuera del manifiesto unido; 50/50 llamadas responden en modo avión con el wifi apagado.
- Coach: 30/30 válidos, p90 1.5 s. Explicación: 27/30 válidas, primer token p50 0.39 s, total p90 3.3 s. Multiple choice: 28/30 válidas.
- Calificar por significado: **0 % de falsos positivos** (0/30), **67 % de aciertos** (40/60): aprueba ~1/3 de los sinónimos que las reglas rechazan. p90 1.3 s.
- `BUSY`: 8/57 sin pausa; 0 con 2 s de pausa.

**Reglas obligatorias:**
- Prompt de sistema: "You are an English-speaking tutor. Write your whole answer in English". Sin él, Nano explicó en coreano (6/30 válidas).
- Pausa de 2 s entre llamadas y, con `BUSY`, esperar lo que pide AICore (5 s si nada) y reintentar una vez.
- Solo con la app en primer plano: en segundo plano o con la pantalla bloqueada AICore da el error 30 (`BACKGROUND_USE_BLOCKED`).

**Descartado:** calificar por significado como juez completo (no llega al 90 % de aciertos) y Gemma 4 E2B como nivel 2 (entrada anterior).

## 2026-11-08 — Room + formato .jsbackup.zip v1 para el respaldo
**Decisión:** la base de la app es Room 3 (`androidx.room3` 3.0.3, KSP, `BundledSQLiteDriver` de `sqlite-bundled` 2.7.0). El formato del respaldo es `.jsbackup.zip` v1 (`docs/POC_PLAN.md`, *Formato del respaldo*): un JSONL por tabla con filas y sha256 en `manifest.json`, y fechas ISO-8601 UTC con `Z`.
**Por qué** (PoC 3, `docs/poc/3-room.md`, tu respaldo real en el OPPO, build debug, dos ejecuciones):
- 18/18 tablas y 1265/1265 filas iguales al manifiesto, con 0 errores.
- Ida y vuelta 18/18 JSONL idénticos byte a byte.
- 3/3 ZIP corruptos o a medias (sha256 mal, tabla que falta, fila mala en la última tabla) dejan la base con 0 filas: la transacción se deshace.
- Importación en **247–390 ms** y PSS pico de **52–99 MB**; `.db` de 326 KB.
- 1564/1564 fechas en UTC. La racha (2 = 2) y los minutos y preguntas por día de los últimos 30 días (30/30) son iguales a la web.
**Notas:**
- La librería nativa de SQLite (`sqlite-bundled`) va en la app, no solo en las pruebas: las pruebas instrumentadas corren en el proceso de la app y, si no, falla con `UnsatisfiedLinkError`.
- El importador de la PoC (rama `poc/3-room`) no se reutiliza: se rehace limpio en `core/data` en la fase 2. Lo que se conserva es el método: verificar los sha256 antes de escribir, una sola transacción, lectura línea a línea y exportación con el formato exacto de `json.dumps` de Python.
**Descartado:** un formato propio distinto del de la tarea 0 (obligaría a otro exportador en la web) y las DAOs por tabla para importar (el SQL se genera desde el esquema).

## 2026-11-08 — MuPDF 1.28.5 para extraer PDFs
**Decisión:** el lector de PDF en Android es MuPDF (`com.artifex.mupdf:fitz` 1.28.5, de `maven.ghostscript.com`). Las tablas rejilladas se reconstruyen con un detector propio a partir de los trazos (el del spike), ampliado en la fase 4.
**Por qué** (PoC 1, `docs/poc/1-mupdf.md`, en el OPPO, build debug): frente a PyMuPDF 1.28.2 de la web, **100 %** de páginas con el mismo número de letras y el mismo texto (11/11 de los PDFs de prueba y 50/50 de tus 4 PDFs; 49 851 letras). `size`, `bold` y `font` coinciden en todas las letras, la bbox difiere 0.00 pt, imágenes y dibujos coinciden en 61/61 y 63/63 páginas, y los errores (`PdfError`) dan 8/8 con el mismo mensaje. Tiempo por página: p50 10.9 ms y p90 17.3 ms. PSS pico de 95 MB con el PDF más grande. Tablas: 2/2 y 24/24 celdas iguales a pdfplumber, pero solo en un PDF sintético, porque ningún PDF real tiene tablas. La AAR no pide permisos y trae `arm64-v8a`.
**Notas:**
- (a) La versión de MuPDF queda fija en `gradle/libs.versions.toml` y no se sube sin repasar las pruebas doradas (`golden/parser_raw`): la AAR y PyMuPDF ya llevan parches distintos (1.28.5 frente a 1.28.2), y hoy dan lo mismo.
- (b) En la fase 4 hay que medir con el perfilador la memoria al procesar 10 PDFs seguidos, con un límite de **300 MB**. En la PoC, el proceso de pruebas llegó a 335 MB con 11 PDFs seguidos (probablemente retención de la propia prueba; sin comprobar).
**Descartado:** PdfBox-Android (plan, punto 11), que no hizo falta probar porque las letras cumplieron todos los criterios.

## 2026-11-07 — La fase 2 empieza configurando Compose
**Decisión:** el proyecto se creó con la plantilla *No Activity* y no trae Compose. Lo primero de la fase 2 es configurarlo: BOM de Compose, plugin `org.jetbrains.kotlin.plugin.compose` y tema Material 3. Hasta entonces `MainActivity` es un marcador provisional (AppCompat + `TextView`).
**Por qué:** la fase 0 solo deja la base segura y que la APK abra en el OPPO; las pantallas y el tema llegan con el diseño de la fase 2.
**Descartado:** configurar Compose ya en la fase 0 (adelanta trabajo de la fase 2 sin diseño).

## 2026-11-07 — Sin `ProfileInstallReceiver` hasta la fase 7
**Decisión:** el manifiesto quita `androidx.profileinstaller.ProfileInstallReceiver` (`tools:node="remove"`, con `tools:ignore="MissingClass"` porque la clase solo está en runtime). En la fase 7 vuelve, **solo en un build type de benchmark**, para medir y generar Baseline Profiles con macrobenchmark.
**Por qué:** la librería lo declara `exported="true"` (protegido con `DUMP`) y el plan (punto 8) exige que solo la actividad principal sea exported. El perfil se sigue instalando al arrancar mediante `ProfileInstallerInitializer`.
**Descartado:** dejarlo exported en todos los builds.
