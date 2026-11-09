# Decisiones — Just Study Android

Una entrada por decisión, la más reciente arriba: fecha, decisión, por qué y alternativas descartadas. Las de los PoC (fase 1) también van a Engram (`android/*`).

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
