# Just Study 한국어 para Android — plan de trabajo

> Copia de `/Users/dero/Documents/visual/korean-study-assistant/docs/ANDROID_PLAN.md` (7 nov 2026). Este proyecto es `/Users/dero/Documents/JustStudyApp`. Las rutas de Python (`rag/…`, `quiz/…`, `notes.py`, `parser.py`, `scripts/check_teacher`) son del proyecto web: `/Users/dero/Documents/visual/korean-study-assistant/backend/app/` y `/Users/dero/Documents/visual/korean-study-assistant/backend/scripts/`, solo lectura.

Plan único para crear la app Android nativa. Reúne el documento de migración del usuario (`korean_study_assistant_android_migration.md`), la revisión contra el código actual y las decisiones de noviembre de 2026. Sustituye la versión anterior de este archivo. **La app web queda en pausa** (sigue funcionando; no se cambia salvo un fallo grave).

---

## 0. Decisiones confirmadas

- App **nativa** Kotlin + Jetpack Compose (Material 3), solo para el OPPO Find X8 Pro del usuario (ColorOS 17, basado en Android 17). Sin Play Store; APK firmada e instalada a mano.
- **Todo se procesa en el teléfono**, también los PDFs. Procesar un PDF puede tardar; todo lo demás (Dashboard, Flashcards, Quiz, Grammar, Teacher, Progress, Calendar) tiene que ir fluido.
- Sin usuarios, sin cuentas, sin internet en el uso, sin OCR, sin audio ni video.
- **La pantalla Study desaparece.** El **Coach pasa a Teacher** (punto 4).
- Primero la app sin IA (determinista); la IA es una mejora opcional que la app no necesita para funcionar.
- El código Android tiene que ser **mucho más corto** que el backend actual (~11 000 líneas de Python), pero con el mismo comportamiento comprobado con **pruebas doradas** (punto 7).

---

## 1. Herramientas y lenguajes

| Qué | Herramienta | Notas |
|---|---|---|
| Lenguaje de la app | **Kotlin** (2.x, el que traiga Android Studio) | Todo el código de la app |
| UI | **Jetpack Compose + Material 3** | Tema propio con los tokens de la web (punto 5) |
| Arquitectura | MVVM + capas (UI → dominio → datos), **Hilt** para inyección | Coroutines + Flow |
| Base de datos | **Room** (SQLite) con KSP y migraciones desde la versión 1; **FTS4/FTS5** para buscar en tus notas | Sustituye a MySQL y a la búsqueda literal de Chroma |
| Preferencias | **DataStore** | Tema, sentido de las tarjetas, ajustes del quiz… |
| Trabajo largo | **WorkManager** (procesar PDFs por etapas, reanudable) | Con notificación de progreso mientras procesa |
| PDF | **MuPDF para Android** (`fitz`, el mismo motor que PyMuPDF) | PoC frente a PdfBox-Android. Licencia AGPL: sin problema para uso personal sin distribuir |
| IA de texto, nivel 1 | **Gemini Nano** con **ML Kit GenAI Prompt API** | Ya viene en el sistema del Find X8 Pro (AICore): gratis, sin archivo de modelo en la app. Está en beta |
| IA de texto, nivel 2 (si Nano no basta) | **LiteRT-LM** con **Gemma 4 E2B** (o E4B) en GPU | Sustituye a Qwen3 4B; mejor pensado para móvil |
| Embeddings (opcional) | **EmbeddingGemma** con LiteRT | Solo si la búsqueda literal (FTS) y las reglas no bastan. BGE-M3 queda descartado en el teléfono |
| Scripts | **Python 3.12** (el venv del backend) | Exportar tus datos y generar las pruebas doradas |
| Compilar e instalar | **Android Studio** (estable más reciente) + **Android CLI** + **adb** | Sin emulador: el OPPO por USB |
| Agentes | Claude Code (código) + la sesión de diseño en claude.ai (diseño y planes) | Punto 6 |

**Descartado por ahora:** llama.cpp (exige NDK, CMake y JNI; solo si LiteRT-LM falla), ONNX Runtime (LiteRT cubre lo mismo), sqlite-vec y Chroma.

---

## 2. Qué instalar (paso a paso)

### En el Mac (MacBook Air M1, 8 GB)
1. **Android Studio** (estable más reciente) desde developer.android.com/studio. Trae su propio JDK y Kotlin: no hace falta instalar Java aparte.
2. En **Settings → Languages & Frameworks → Android SDK**:
   - *SDK Platforms:* la última estable (Android 17 si ya aparece; si no, la anterior).
   - *SDK Tools:* Android SDK Build-Tools, **Android SDK Platform-Tools** (adb) y Android SDK Command-line Tools.
   - **No** instalar NDK, CMake ni imágenes de emulador (ahorran ~10 GB y RAM).
3. **Android CLI** (preview) desde developer.android.com/tools/agents. Permite que Claude Code cree el proyecto, compile, instale en el teléfono (`android run`), lea la documentación (`android docs`) e instale skills (`android skills`).
4. Comprobar adb en la terminal: `adb version`. Si no lo encuentra, añadir `~/Library/Android/sdk/platform-tools` al `PATH`.
5. Ajustes para 8 GB de RAM: en `gradle.properties` del proyecto, `org.gradle.jvmargs=-Xmx2g` y `org.gradle.caching=true`. Mientras se compila, parar la app web (`/Users/dero/Documents/visual/korean-study-assistant/scripts/study.sh stop`), Docker/Colima y Ollama.
6. Espacio libre: unos 15 GB para Android Studio, SDK y caché de Gradle, más el modelo de nivel 2 si se usa (~2.6 GB).

### En el OPPO Find X8 Pro (ColorOS 17)
1. **Activar las opciones de desarrollador:** Ajustes → Acerca del dispositivo → Versión → tocar **Número de compilación** 7 veces y poner el PIN.
2. Ajustes → **Ajustes adicionales → Opciones de desarrollador**:
   - **Depuración USB:** activar y aceptar.
   - Si aparecen: **Instalar vía USB** (en algunas versiones de ColorOS pide iniciar sesión con la cuenta de OPPO) y **Desactivar supervisión de permisos**. Si `adb install` falla con `INSTALL_FAILED_USER_RESTRICTED`, es uno de estos dos.
   - Opcional: **Depuración inalámbrica**, para no usar el cable (`adb pair`).
3. Conectar al Mac con un cable USB-C de datos, elegir **Transferencia de archivos** en la notificación y aceptar **Permitir depuración USB** (marcar "Permitir siempre desde este equipo").
4. En el Mac: `adb devices` debe mostrar el teléfono como `device`.
5. Para Gemini Nano: Ajustes → Google → **AICore** actualizado (lo hace Google Play solo). La primera vez puede necesitar descargar el modelo con wifi. Es el sistema quien lo descarga, no nuestra app.
6. Cuando la app ya exista: Ajustes → Batería → la app → **Permitir actividad en segundo plano** (ColorOS cierra procesos para ahorrar batería; sin esto, un PDF largo se pausa al salir de la app).
7. Para terminar la sesión: desactivar la depuración USB cuando no se esté desarrollando (seguridad).

---

## 3. Estructura del proyecto

**Proyecto aparte en `/Users/dero/Documents/JustStudyApp`** (su propio repo de git y su propio GitHub privado). El proyecto web (`/Users/dero/Documents/visual/korean-study-assistant`) queda como **referencia de solo lectura**: de aquí salen el contexto, el código a portar y las pruebas doradas. Las dos sesiones (diseño y Claude Code) trabajan conectadas a `JustStudyApp`. Pocos módulos (Gradle en 8 GB sufre con muchos). Dentro de `JustStudyApp` (la raíz del proyecto Gradle):

```text
JustStudyApp/
├── app/                 # Compose: pantallas, navegación, tema, ViewModels, Hilt
├── core/
│   ├── korean/          # Kotlin puro (JVM, sin Android): lector de notas, gramática, conjugación,
│   │                    #   reglas de Teacher, validación de respuestas, SM-2. Pruebas rapidísimas
│   ├── data/            # Room, DAOs, repositorios, DataStore, importador de tus datos
│   ├── pdf/             # MuPDF → páginas (texto, tamaños, negritas, posiciones, tablas)
│   └── ai/              # LlmEngine (Gemini Nano / LiteRT-LM), EmbeddingEngine, ModelManager
├── golden/              # pruebas doradas exportadas desde el backend (JSON, sin datos privados)
└── gradle/libs.versions.toml
```

**`core/korean` es la pieza clave:** es lo que hoy son `rag/entries.py`, `conjugation.py`, `tutor.py` (reglas), `quiz/text.py`, `quiz/pairs.py`, `quiz/grammar.py`, `rag/chunker.py` y `notes.py`. Al ser Kotlin puro se prueba en segundos sin teléfono.

---

## 4. Pantallas y navegación

Barra inferior con 5 destinos: **Dashboard · Library · Flashcards · Quiz · Teacher**. **Grammar, Progress, Calendar y Ajustes** van en el menú "More" (o desde el Dashboard).

**Teacher con el Coach dentro:**
- Un solo chat. Una pregunta sobre tus notas ("What does 근처 mean?", "Example with -아서", "가는데요 맞아요?") la responde Teacher con reglas, como hoy en la web.
- Una pregunta de estudio ("What should I study?", "I have 20 minutes", "teach me something", "make me a quiz of my mistakes") la resuelve el Coach con herramientas Kotlin deterministas: temas débiles, tarjetas pendientes, progreso, crear quiz, crear mazo de práctica. El modelo, si lo hay, solo redacta la respuesta.
- Un selector arriba ("Ask my notes" / "What should I study?") ayuda cuando la intención no está clara.
- "Ask what to do" del Dashboard abre Teacher en modo Coach. "Practice this" crea un quiz de 5 preguntas, como hoy.
- Todo lo que hoy hace la pantalla Study que en realidad es de Teacher (responder sobre tus notas, explicar) pasa a Teacher. Los mazos que creaba el Coach pasan a Flashcards.

**Fuera:** pantalla Study, teclado coreano propio (se usa el teclado del sistema, Gboard en coreano), fotos motivacionales, la skill `design` que usa una API de pago.

---

## 5. Diseño

**Quién lo hace:** la sesión de diseño (claude.ai) con los tipos de artefacto **Design System** y **Design**, que ya vienen en claude.ai (no hay nada que instalar).

**Pasos:**
1. **Sistema de diseño "Just Study Mobile"** (artefacto Design System), a partir de `frontend/src/index.css` y `frontend/DESIGN.md`:
   - Colores exactos de la web: acción `#0047a0`, marca `#116eee`, verde Duolingo `#58cc02`, rojo `#cd2e3a`, amarillo `#edc215`, y los de correcto/incorrecto. Claro y oscuro.
   - Poppins y la escala de texto. Con Hangul, la fuente coreana del sistema.
   - Botones con canto inferior, tarjetas, chips y el estilo gamificado.
   - Adaptado a Material 3: objetivos táctiles ≥ 48 dp, barra inferior, de borde a borde.
2. **Pantallas móviles** (artefacto Design) en este orden: Dashboard, Flashcards (anverso, reverso, Type the answer, edición de mazos), Quiz (formulario, pregunta, resultado), Teacher con Coach, Library (lista, procesando, All vocabulary), Grammar, Progress y Calendar. Con estados vacíos, cargando y error.
3. **Entrega a Claude Code:** por cada pantalla, una ficha en `JustStudyApp/design/<pantalla>.md` (componentes, tokens, medidas y estados) y una captura PNG. Claude Code programa Compose a partir de eso; no hace falta que vea el artefacto.
4. Las animaciones que ya pidió el usuario (mazos que se deslizan, combinar, latido) se describen en la ficha con su duración y curva.

**Regla:** no hay colores nuevos ni cambios de tamaño sin que el usuario lo pida.

---

## 6. Sesiones y skills

### Organización: 1 sesión de diseño + 1 Claude Code (+1 Claude Code para PoC)
| Sesión | Hace | No hace |
|---|---|---|
| **Diseño (claude.ai)** | Planes de cada fase, sistema de diseño y pantallas, análisis del Python a portar, revisión de diffs y prompts cortos para Claude Code | No compila ni instala en el teléfono, ni usa git |
| **Claude Code principal (Mac)** | Escribe y compila el Kotlin, pruebas, `android run` en el OPPO, git y PR, exportar datos y pruebas doradas | No rediseña pantallas por su cuenta |
| **Claude Code 2 (opcional)** | Solo PoC independientes, en otro worktree (`superpowers:using-git-worktrees`) | Nunca los mismos archivos que la principal |

¿Por qué no 2 Claude Code sin la de diseño? Porque se pierden los diseños visuales y la revisión con otro punto de vista, y las dos leerían el mismo contexto (doble gasto). ¿Por qué Claude Code escribe el Kotlin? Porque necesita el ciclo compilar → error → arreglar → instalar, que solo existe en el Mac.

### Skills: a cada sesión solo las suyas
Cada skill instalada añade su descripción al contexto de **cada** sesión: instalar de más gasta tokens y confunde. Instalarlas **en el proyecto** (`JustStudyApp/.claude/skills/`), no globales, salvo Superpowers. **No se programa nada hasta que todas estén instaladas y Claude Code las vea** (se cargan al abrir la sesión: reiniciar Claude Code después de instalarlas).

**Claude Code (programar):**
| Skill | Para qué |
|---|---|
| Superpowers (ya está) | `writing-plans` (si el plan no viene de la sesión de diseño), `test-driven-development` al portar reglas, `systematic-debugging`, `verification-before-completion`, `using-git-worktrees` |
| CodeGraph (ya está) | `codegraph init` en `JustStudyApp`; explorar el Python a portar y el Kotlin nuevo sin leer archivos enteros |
| Engram (ya está) | Decisiones y causas de bugs con `topic_key` `android/*` |
| **Android skills oficiales de Google** (`android skills add …`) | Use Android CLI, Set up testing strategy, Set up Navigation 3, Optimize Compose performance, Make app edge-to-edge, Use Android profilers, Audit and fix ANRs, Audit R8 configuration, Use ML Kit GenAI Prompt API, Run on-device LLMs with LiteRT-LM, Run on-device models with LiteRT, Audit and fix permission security, Android Intent security |
| **claude-android-ninja** (Drjacky) | Compose, Room, Hilt, Navigation 3 y pruebas (Roborazzi, screenshots) |

**Sesión de diseño (claude.ai):** no le hacen falta las skills de programar. Usa los artefactos Design System y Design que ya trae claude.ai, más Superpowers (`brainstorming` y `writing-plans`, que ya están aquí). Si quieres, sube en claude.ai (Ajustes → Capacidades → Skills) **una** skill de diseño para Compose (por ejemplo, *android-compose-design-skill*) para que las fichas salgan pensadas para Material 3. No subas las de programar.

**Si añades más skills:** en Claude Code, en `JustStudyApp/.claude/skills/`; en la sesión de diseño, solo de diseño. Antes de añadir una, mira si ya la cubre una oficial. Las de terceros con pocas estrellas: leer su SKILL.md antes de instalarla.

---

## 7. Cómo portar sin perder nada (y con menos código)

1. **Pruebas doradas** (`JustStudyApp/golden/`, generadas con un script Python del backend):
   - PDFs de prueba (fixtures, plantilla y los 5 de LibreOffice) → texto por página tal como lo deja `parser.py`.
   - Fragmentos, pares, puntos de gramática y entradas de cada PDF de prueba.
   - Cientos de preguntas de Teacher con su respuesta exacta (las mismas de `check_teacher`).
   - Casos de `is_correct`, `widen_answer`, `compareAnswer`, conjugación (`aeo_forms`, `lemmas`), `has_form` y `same_grammar`.
   - Intervalos de SM-2 para secuencias de Again/Hard/Good/Easy.

   Las pruebas de Kotlin tienen que dar **exactamente** lo mismo. Solo con PDFs de prueba, nunca con tus notas privadas (esas se comprueban en el teléfono con el `selfcheck` portado).
2. **Menos código, mismo comportamiento.** No se porta lo que solo existe por la web: FastAPI, esquemas, MySQL, Chroma, el cliente de Ollama, el bucle de tool calling, Study, el prompt del tutor ni los avisos de modelo caído. Lo que queda se reescribe más compacto:
   - Un solo módulo de morfología coreana (formas de gramática, conjugación, partículas), con tablas de datos en vez de regex repartidas.
   - Un solo "índice de notas" (Room + FTS) que usan Teacher, Quiz y Flashcards.
   - Las reglas de Teacher como una lista ordenada de "respondedores" pequeños (palabra, gramática, ejemplo, comparar, frase en inglés, revisar frase, conjugada, Coach), cada uno con sus pruebas.

   Objetivo: unas 4 000–6 000 líneas de Kotlin para el núcleo, sin contar la UI.
3. **TDD:** cada respondedor se porta empezando por sus pruebas doradas en rojo.

---

## 8. Seguridad (app local, sin permisos peligrosos)

**Permisos permitidos** (ninguno peligroso):
- `POST_NOTIFICATIONS`: solo para el progreso de un PDF largo; se puede negar.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`: procesar un PDF con la pantalla apagada.
- `USE_BIOMETRIC`: solo si se añade el bloqueo con huella (opcional).

**Prohibidos:** `INTERNET`, `READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `CAMERA`, `RECORD_AUDIO`, ubicación, contactos y `QUERY_ALL_PACKAGES`. Los PDFs entran con el **selector de archivos del sistema (SAF)**, que no pide permisos.

**Comprobar cada versión:** el manifiesto final que une todas las librerías (`app/build/intermediates/merged_manifest`). Si una librería añade `INTERNET` (por ejemplo ML Kit), quitarlo con `tools:node="remove"` y comprobar que todo sigue funcionando sin red. Gemini Nano lo descarga AICore, el sistema, no la app.

**Resto:**
- `android:allowBackup="false"` y reglas de extracción de datos vacías: nada de tus notas a la nube por Auto Backup.
- Solo la actividad principal `exported`; nada de `FileProvider` salvo para compartir un respaldo que tú elijas.
- APK de release con `debuggable=false`, R8 activado y la clave de firma fuera del repo.
- Base de datos, PDFs y modelos en el almacenamiento privado de la app.
- Sin analíticas ni registros de fallos de terceros. Los logs nunca llevan texto de tus notas ni tus respuestas.
- Respaldo exportable cifrado con contraseña (opcional).
- Bloqueo con huella al abrir (opcional).
- Revisión con las skills oficiales *Audit and fix permission security* e *Android Intent security* antes de cada APK.

---

## 9. Rendimiento (que todo vaya fluido)

- **Nada pesado en el hilo principal:** Room con Flow y `Dispatchers.IO`, índices en las columnas de búsqueda, Paging para All vocabulary y el historial.
- **Precalcular al procesar el PDF:** pares, gramática, índice de notas, tarjetas y FTS. Las pantallas solo leen.
- **IA bajo demanda:** Gemini Nano o el modelo solo se cargan al abrir Teacher y se liberan tras unos minutos sin uso. Nunca a la vez que un PDF se procesa.
- **Compose:** estados estables, `LazyColumn` con `key`, sin recomposiciones de más (skill *Optimize Compose performance*).
- **Arranque y fluidez:** Baseline Profiles + R8 en release; medir con los perfiladores (skill *Use Android profilers*) en el OPPO real.
- **PDF:** MuPDF por página, guardando cada etapa (`PENDING → EXTRACTING → PARSING → CHUNKING → INDEXING → READY` o `FAILED`) para reanudar si ColorOS cierra la app. Puede tardar: tiene que ser correcto, no rápido.
- **Metas:** abrir la app < 1 s; pasar de tarjeta < 16 ms por fotograma; generar un quiz por reglas < 300 ms; respuesta de Teacher por reglas < 300 ms.

---

## 10. Modelos de IA

| Nivel | Qué | Cuándo |
|---|---|---|
| 0 | **Sin modelo:** Teacher por reglas, Quiz por reglas, Coach determinista | Siempre; la app funciona entera así |
| 1 | **Gemini Nano** (ML Kit GenAI Prompt API, beta): el Find X8 Pro está en la lista de dispositivos | Los 8 usos del punto 10.1, más las preguntas Multiple choice y Short answer escritas por el modelo (validadas igual que hoy) |
| 2 | **Gemma 4 E2B** con LiteRT-LM en GPU (~2.6 GB) | No hace falta (PoC 2) |
| Embeddings | **EmbeddingGemma** con LiteRT | Solo si hace falta búsqueda por significado; si no, FTS y las reglas bastan |

Todo lo que escribe el modelo pasa por las mismas comprobaciones que hoy: nada de coreano que no esté en tus notas, citas válidas, sin frases inventadas. El soporte de coreano de Nano se mide en la PoC (las respuestas van en inglés y el coreano sale de tus notas).

### 10.1 Dónde entra el modelo (fase 6)

Sin la pantalla Study, el modelo entra en estos 8 sitios. La regla: **primero tus notas. Si lo que pides no está en ellas, el modelo puede crearlo en el teléfono, sin internet. Explica en inglés, y todo lo que no sale de tus notas lleva la marca "AI · not from your notes". Lo que crea el modelo nunca se guarda como si fuera tuyo: no entra en tus notas, ni en tus mazos sin preguntarte, ni cuenta como fuente.** Lo hace Gemini Nano (viene en tu OPPO y es gratis). Si no está disponible, cada función tiene su versión sin modelo y la app sigue funcionando igual.

| # | Dónde | Qué hace el modelo | Cómo se comprueba | Sin modelo |
|---|---|---|---|---|
| 1 | **Quiz:** calificar por significado | Si escribes la respuesta con otras palabras ("to be grateful" para "to be thankful"), juzga si significa lo mismo: sí o no, con una razón corta. Solo se pregunta cuando las reglas (`is_correct`) dicen "incorrecto" | Solo puede **aprobar** respuestas en inglés que las reglas rechazaron; nunca aprueba coreano distinto de tus notas. La razón se muestra ("accepted: same meaning"). Medido en la PoC 2: 0 % de falsos positivos; aprueba ~1/3 de los sinónimos que las reglas rechazan | Solo los sinónimos que tienen tus notas (como hoy) |
| 2 | **Quiz:** explicar tu error | Cuando fallas, una frase de por qué ("you used 는 but 은 goes after a consonant"), basada en tu punto de gramática | El prompt lleva tu punto de gramática y las dos respuestas. El coreano de la frase tiene que estar en tu respuesta, en la correcta o en el punto de gramática | La explicación del punto de gramática tal cual, si existe |
| 3 | **Flashcards:** Type the answer | Igual que el 1: si tu respuesta es otra forma correcta de decirlo | Las mismas reglas que el 1, después de `compareAnswer` | `compareAnswer` (como hoy) |
| 4 | **Teacher:** sin respuesta en las reglas | En vez de solo "your notes don't cover this", una explicación en inglés con tus fragmentos (búsqueda FTS) como fuente, con sus citas | Con fragmentos: citas válidas y el coreano solo de los fragmentos citados. Sin fragmentos: responde el modelo con la marca 'AI · not from your notes' (no hay nada con qué comprobarlo). | "Your notes don't cover this" |
| 5 | **Coach dentro de Teacher** | Redactar el plan del día y las recomendaciones a partir de lo que calculan las reglas (temas débiles, tarjetas pendientes, progreso) | El modelo solo recibe esos hechos y no puede añadir números ni coreano que no estén en ellos | El texto con plantilla del Coach determinista |
| 6 | **Library:** notas desordenadas | Sacar pares "coreano = inglés" para las tarjetas de las notas que las reglas no entienden (`needs_llm_pairs`). Ya existe en la web como respaldo; en el teléfono va como etapa opcional del procesado (WorkManager), porque puede tardar; solo con la app en primer plano: si sales, la etapa se pausa y sigue al volver (AICore no corre en segundo plano) | `validate_llm_pair`, como en la web: el coreano tiene que estar en ese fragmento | Solo los pares que sacan las reglas |
| 7 | **Grammar** | (a) Una explicación más sencilla del punto; (b) "dame una frase de práctica" usando solo palabras de tus notas | (a) Coreano solo del punto. (b) Cada palabra de la frase tiene que estar en tus notas (índice de notas + conjugación); la frase va siempre etiquetada **PRACTICE** y nunca se guarda como si fuera tuya | (a) La explicación de tus notas; (b) los ejemplos de tus notas |
| 8 | **Dashboard:** resumen semanal | Una o dos frases ("this week you improved in particles; -거든요 is still weak") a partir de las cifras de la semana | Solo con las cifras y temas que le pasan las reglas; nada de números nuevos | Una frase con plantilla a partir de las mismas cifras |

**Común a los 8:**
- El modelo se carga bajo demanda y nunca mientras se procesa un PDF (punto 9).
- Toda respuesta del modelo lleva una marca discreta ("AI") para distinguirla de lo que sale de tus notas.
- Si tarda más de lo que fija la PoC 2 o AICore da cuota o error, se muestra la versión sin modelo, sin aviso de error.
- Cada uso tiene su validador en `core/ai` con pruebas JVM (con un modelo falso) y su prompt en inglés, versionado.

---

## 11. Riesgos y cómo se cubren

| Riesgo | Plan |
|---|---|
| Se pierden comportamientos al portar | Pruebas doradas + `selfcheck` en el teléfono |
| MuPDF no da lo mismo que PyMuPDF | PoC 1 con todos los PDFs de prueba; si falla, PdfBox-Android + las reglas de unión de líneas que ya tenemos |
| Gemini Nano en beta, cambia o no está | Nivel 0 siempre funciona; nivel 2 como respaldo |
| La librería de ML Kit añade `INTERNET` | Quitarlo del manifiesto y probar en modo avión; si no funciona sin él, decidir con el usuario |
| ColorOS cierra procesos | WorkManager por etapas + permitir actividad en segundo plano |
| Mac de 8 GB | Sin emulador, pocos módulos, cerrar Docker y Ollama al compilar |
| Migración de datos | Exportador versionado (ZIP + manifiesto + checksums), importación transaccional y prueba de ida y vuelta con un respaldo real |
| Fechas y rachas | Instantes en UTC; días con la zona del teléfono (preferencia) |
| Actualizar la APK sin perder datos | Misma clave de firma; migraciones de Room probadas; respaldo antes de instalar |

---

## 12. Fases

| Fase | Qué | Salida | 📱 Teléfono por USB con depuración |
|---|---|---|---|
| **0. Preparación** (1–2 días) | Instalar (punto 2); crear el proyecto en Android Studio en `/Users/dero/Documents/JustStudyApp`; repo y GitHub privado; `CLAUDE.md` del proyecto; skills del punto 6 instaladas y comprobadas; la plantilla abre en el OPPO | La APK abre en el teléfono y Claude Code ve todas las skills | **Sí** |
| **1. PoC** (1–2 semanas, en paralelo) | (1) PDF con MuPDF frente a `/Users/dero/Documents/visual/korean-study-assistant/data/extracted`; (2) Gemini Nano: calidad, latencia y si funciona sin `INTERNET`; (3) Room: importar un respaldo real; (4) Gemma 4 E2B solo si (2) falla | Una decisión por PoC en `docs/DECISIONS.md` y Engram | Tarea 0: **no**. Tareas 1–4: **sí** |
| **2. Diseño + base** (1 semana) | Sistema de diseño y pantallas (sesión de diseño); tema, navegación, Room, Hilt, DataStore y CI de Gradle (Claude Code) | Esqueleto navegable con el tema | **Sí**, para instalar el esqueleto (`android run`) |
| **3. Núcleo sin IA** (2–3 semanas) | `core/korean` con pruebas doradas; Flashcards (SM-2, mazos, Type the answer), Quiz por reglas, Grammar, Progress, Calendar, Dashboard, Fix this next | Ya se puede estudiar con datos importados | `core/korean`: **no** (pruebas JVM). Pantallas: **sí** |
| **4. Library y PDF** (1–2 semanas) | SAF, SHA-256, pipeline por etapas, All vocabulary, duplicados, reprocesar y borrar | PDFs nuevos procesados en el teléfono | **Sí** |
| **5. Teacher + Coach** (2 semanas) | Respondedores con pruebas doradas, Coach determinista dentro de Teacher, `selfcheck` en el teléfono | Teacher igual que en la web | Respondedores: **no** (JVM). `selfcheck` en el teléfono: **sí** |
| **6. IA** (2–3 semanas) | Gemini Nano (y nivel 2 si hace falta) en los 8 usos del punto 10.1: calificar por significado (Quiz y Type the answer), explicar tu error, Teacher sin respuesta, Coach, pares de notas desordenadas, Grammar (explicación sencilla y frase de práctica) y resumen semanal; más las preguntas del modelo | Mejora opcional | **Sí** (Nano solo corre en el OPPO) |
| **7. Pulido** (1–2 semanas) | Perfiladores, ANR, R8, Baseline Profiles, modo avión, cerrar a la fuerza, actualizar la APK, auditoría de seguridad | APK final | **Sí** |

**Total:** unas 10–15 semanas a tiempo parcial.

Cada prompt para Claude Code empieza diciendo si hace falta el teléfono conectado por USB con la depuración activada.

---

## 13. Lo primero que hay que hacer

1. Instalar lo del punto 2 y configurar el OPPO.
2. En la sesión de diseño: pegar el prompt de arranque (lo pide el usuario cuando termine la instalación) para que lea este plan, el código actual y Engram.
3. Fase 0 con Claude Code.
