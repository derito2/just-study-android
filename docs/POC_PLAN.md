# Fase 1 — Pruebas de concepto (PoC): plan de implementación

> **Para quien lo ejecute (Claude Code):** cada tarea llega como un prompt corto de la sesión de diseño; este documento es el plan. Sub-skill: `superpowers:executing-plans` (o `subagent-driven-development` para la tarea 0). Los pasos son casillas `- [ ]`. **No se mergea nada sin la confirmación del usuario en esa tarea.**

**Objetivo:** decidir con datos medidos en el OPPO, una por una, las cuatro apuestas técnicas del plan (punto 12, fase 1): MuPDF, Gemini Nano sin `INTERNET`, importar un respaldo real a Room y, solo si Nano falla, Gemma 4 E2B. Cada PoC termina en una entrada de `docs/DECISIONS.md` y en Engram (`android/poc-*`).

**Arquitectura:** las PoC son **código desechable**. Viven en ramas `poc/<n>-<nombre>` como pruebas instrumentadas (`app/src/androidTest/.../poc/`) que corren en el OPPO con `./gradlew connectedDebugAndroidTest` y escriben sus resultados en JSON dentro de la app (`files/poc/`). A `main` solo llegan las herramientas Python (`tools/`), las pruebas doradas (`golden/`), los informes (`docs/poc/`) y las decisiones. Un solo módulo `:app`: nada de módulos nuevos en la fase 1.

**Tecnología:** Kotlin + AGP 9.5 (Kotlin integrado), minSdk 31, targetSdk 37; JUnit4 + AndroidX Test en el teléfono; Python 3.12 del venv del proyecto web para las herramientas; Node 22 (`--experimental-strip-types`) para `compareAnswer`.

**Base:** `docs/PLAN.md` (puntos 1, 7, 8, 9, 10, 11 y 12) y `docs/DECISIONS.md`.

## Restricciones globales

- El proyecto web `/Users/dero/Documents/visual/korean-study-assistant` es **solo lectura**: las herramientas viven en `JustStudyApp/tools/` y lo importan con `PYTHONPATH`, sin tocarlo.
- **Nada privado en git:** las pruebas doradas salen solo de los PDFs de prueba (`backend/tests/fixtures/notes_libreoffice/*.pdf` y `docs/templates/Korean_Notes_Example.pdf`). Tus PDFs, `data/extracted`, los respaldos y lo que la PoC escribe con tus datos van a `golden/private/` o `build/poc/` (ignorados) y nunca se commitean.
- Logs sin texto de tus notas ni respuestas (punto 8). Los informes dan cifras, no contenido.
- Permisos prohibidos (punto 8), también en las ramas PoC: el manifiesto unido de `debug` no puede llevar `INTERNET`.
- Mac de 8 GB: sin emulador. Al compilar, parar la web, Colima y Ollama.
- Medir siempre en el OPPO Find X8 Pro (ColorOS 17, 411 × 905 dp), con la pantalla encendida, cargando o con más del 50 % de batería, y decir qué build era (debug).
- Para leer los resultados: `adb shell run-as com.dero.juststudykorean cat files/poc/<archivo>` > `build/poc/<archivo>`.

## Riesgos que ninguna prueba de las tareas cubre sola

1. **Redondeo y números:** Python redondea a par (`round(2.5) == 2`) y formatea floats distinto que Kotlin. Las doradas guardan los números ya calculados (enteros o con 2 decimales fijos) y la tarea 0 incluye casos `x.5` en SM-2.
2. **Unicode:** `str.isspace()`, `\w` y `.lower()` de Python no coinciden del todo con Kotlin; NFC es obligatorio a la entrada. La tarea 0 añade casos con espacio de ancho cero, NBSP, jamo sueltos y mayúsculas acentuadas.
3. **Versión de MuPDF:** PyMuPDF y la AAR de Android pueden traer versiones distintas del motor y cortar los renglones diferente. La tarea 1 registra las dos versiones y compara antes las letras sueltas que los renglones.
4. **ColorOS mata el proceso** en mitad de una medida larga (PDF o modelo). Las pruebas guardan el resultado por caso, no al final, y se pueden reanudar.
5. **Gemini Nano por cuota o en segundo plano:** AICore limita por batería y no corre en segundo plano. La tarea 2 mide con la actividad en primer plano y registra los errores de cuota como resultado, no como fallo de la prueba.

---

## Tarea 0 — Herramientas: respaldo exportable y pruebas doradas

**Rama:** `poc/0-tools` · **Se mergea** (con tu confirmación): `tools/`, `golden/` (sin `private/`), `.gitignore`.

**Archivos:**
- Crear: `tools/README.md` (cómo correr cada herramienta)
- Crear: `tools/golden/export_golden.py`, `tools/golden/cases.py` (generadores de casos), `tools/golden/compare_answer.mjs`
- Crear: `tools/export/export_backup.py`
- Crear: `tools/tests/test_golden.py`, `tools/tests/test_export.py`
- Crear: `golden/` (salida de la herramienta, ver el formato más abajo)
- Modificar: `.gitignore` (+ `golden/private/`, `build/poc/`, `*.jsbackup.zip`)

**Interfaces que producen:**
- `golden/manifest.json` y los archivos de *Formato de las doradas* (más abajo), que leen las tareas 1 y 2 y, más adelante, `core/korean`.
- `export_backup.py` → `<salida>/juststudy-backup-<AAAA-MM-DD>.jsbackup.zip`, que lee la tarea 3.

**Cómo se corren** (desde el Mac, con el venv del web):
```bash
cd /Users/dero/Documents/visual/korean-study-assistant/backend
PYTHONPATH=. .venv/bin/python /Users/dero/Documents/JustStudyApp/tools/golden/export_golden.py \
  --out /Users/dero/Documents/JustStudyApp/golden
PYTHONPATH=. .venv/bin/python /Users/dero/Documents/JustStudyApp/tools/export/export_backup.py \
  --out /Users/dero/Documents/JustStudyApp/build/poc        # necesita MySQL arriba (scripts/study.sh)
```

- [ ] **Paso 1: pruebas que fallan** (`tools/tests/`, pytest con el mismo venv):
  - `test_golden_is_deterministic`: dos ejecuciones sobre una carpeta temporal dan el mismo `manifest.json` (mismos sha256).
  - `test_golden_refuses_private_paths`: `export_golden.py --pdf <algo bajo data/>` sale con código 2 y no escribe nada.
  - `test_golden_parser_matches_parse_pdf`: para `Vocab_cafe_y_comida.pdf`, `golden/parser/Vocab_cafe_y_comida.json["pages"][0]["text"] == asdict(parse_pdf(p)).pages[0].text`.
  - `test_sm2_rounding_case`: entre los casos de `golden/sm2.json` hay uno donde `interval_hours * factor` termina en `.5`, y su resultado es el de `round()` de Python.
  - `test_backup_manifest_checksums`: con una base SQLite de prueba (los modelos SQLAlchemy del web sobre `sqlite://`, 2 filas por tabla), el ZIP trae una entrada por tabla con su número de filas y un sha256 que coincide con el JSONL.
- [ ] **Paso 2:** correrlas y ver que fallan (`pytest tools/tests -q`).
- [ ] **Paso 3:** implementar `export_golden.py` según *Diseño del script de pruebas doradas* y `export_backup.py` según *Formato del respaldo*.
- [ ] **Paso 4:** `pytest tools/tests -q` en verde; `ruff check tools && ruff format --check tools` en verde.
- [ ] **Paso 5:** generar `golden/` de verdad, revisar a ojo que no lleva nada de `data/` (`grep -rl "data/extracted\|uploads/" golden` vacío) y commitear. El ZIP del respaldo se queda en `build/poc/` (ignorado).

**Parar si:** el web no importa (`ModuleNotFoundError`), `node --version` es menor que 22.6, la base MySQL no está arriba, una salida trae texto de tus notas, o los sha256 cambian entre dos ejecuciones.

---

## Tarea 1 — PoC MuPDF frente a lo que ya extrae la web

**Rama:** `poc/1-mupdf` (después de mergear la tarea 0) · **No se mergea** el código; sí `docs/poc/1-mupdf.md` y la decisión.

**Pregunta:** ¿MuPDF para Android da, letra a letra, lo mismo que PyMuPDF (`rawdict`), y se pueden sacar las tablas sin pdfplumber?

**Archivos:**
- Modificar: `settings.gradle.kts` (repositorio `https://maven.ghostscript.com`), `gradle/libs.versions.toml` + `app/build.gradle.kts` (`com.artifex.mupdf:fitz`, la última estable; anotar la versión)
- Crear: `app/src/androidTest/java/com/dero/juststudykorean/poc/MupdfRawTest.kt`, `MupdfTablesTest.kt`, `PocFiles.kt` (copiar assets y escribir resultados)
- Crear: `app/src/androidTest/assets/poc/` ← copia de los 6 PDFs de prueba y de `golden/parser_raw/*.json`
- Crear: `docs/poc/1-mupdf.md` (informe)

**Interfaces:**
- Consume: `golden/parser_raw/<pdf>.json` y `golden/parser/<pdf>.json` (tarea 0).
- Produce: `files/poc/mupdf-<pdf>.json`, con la misma forma que `parser_raw`, y `files/poc/mupdf-summary.json`.

- [ ] **Paso 1: prueba que falla** `MupdfRawTest.rawCharsMatchGolden_<pdf>` (una por PDF de prueba): abre el PDF con `Document.openDocument`, recorre `page.toStructuredText("preserve-whitespace")` y por cada letra guarda `c`, `bbox` redondeado a 2 decimales, `size` a 1 decimal, `font` y `bold`. Compara con `parser_raw`: igual número de letras por página y el mismo texto por página tras NFC.
- [ ] **Paso 2:** correrla en el OPPO y verla fallar (todavía no hay dependencia).
- [ ] **Paso 3:** añadir la dependencia e implementar `dumpRaw(path: String): RawDoc` en `PocFiles.kt`; además, por página, `imageCount` (imágenes que cubren ≥ 10 % del área) y `drawingItems` (segmentos y rectángulos de trazo), con un `Device` propio sobre `page.run`.
- [ ] **Paso 4: tablas (spike).** `MupdfTablesTest.latticeMatchesPdfplumber`: en las páginas con `drawingItems ≥ 6`, reconstruir la cuadrícula con los segmentos horizontales y verticales del `Device`, asignar las letras a sus celdas y comparar con `golden/parser/<pdf>.json → pages[].tables[].rows`.
- [ ] **Paso 5: tus PDFs (local).** Los 4 de `data/uploads` por `adb push` a `/data/local/tmp/poc/` y `run-as … cp` a `files/poc/in/`. Comparar con `golden/private/parser_raw/*.json`, que genera `export_golden.py --private` y que **no se commitea**. Medir tiempo por página y memoria pico (`Debug.getPss()`).
- [ ] **Paso 6:** escribir `docs/poc/1-mupdf.md` con la tabla de métricas, sin contenido de tus notas.

**Métricas y criterios de aceptación**

| Métrica | Cómo | Aceptación |
|---|---|---|
| Letras por página iguales | PDFs de prueba | **100 %** de páginas |
| Texto por página igual (NFC) | PDFs de prueba / tus 4 PDFs | **100 %** / **≥ 98 %**; el resto con similitud ≥ 0.99 y su diferencia explicada |
| `size` y `bold` por letra | PDFs de prueba | ≥ 99.5 % de letras iguales (deciden los títulos) |
| bbox por letra | diferencia máxima | ≤ 0.5 pt (decide la unión de renglones) |
| Imágenes y dibujos por página | contra PyMuPDF | iguales en todas las páginas |
| Tablas (spike) | filas y celdas contra pdfplumber | todas las tablas de los PDFs de prueba iguales; si no, describir qué falla |
| Tiempo | por página, p50 / p90 | p90 ≤ 1 s |
| Memoria | PSS pico | ≤ 300 MB con el PDF más grande |
| Errores | cifrado, sin texto, con imágenes | el mismo mensaje que la web (`PdfError` y sus subclases) |

**Decisión:** MuPDF si pasa todo menos, como mucho, las tablas. Si las tablas fallan pero las letras no, MuPDF con un detector de tablas propio en la fase 4, con lo que muestre el spike. Si fallan las letras, PoC rápida con PdfBox-Android (plan, punto 11) antes de decidir.

**Parar si:** la AAR no resuelve desde `maven.ghostscript.com`, el manifiesto unido trae `INTERNET` u otro permiso prohibido, o la AAR no tiene la ABI `arm64-v8a`.

---

## Tarea 2 — PoC Gemini Nano con ML Kit Prompt API, sin `INTERNET`

**Rama:** `poc/2-nano` (después de la tarea 0; en paralelo con la 1 y la 3, en otro worktree) · **No se mergea** el código.

**Pregunta:** ¿Nano está en este OPPO, funciona sin el permiso `INTERNET` y en modo avión, y da calidad y latencia suficientes para el nivel 1 del plan (punto 10)?

**Archivos:**
- Modificar: `gradle/libs.versions.toml`, `app/build.gradle.kts` (`com.google.mlkit:genai-prompt`, la versión que indique la skill *Use ML Kit GenAI Prompt API*); `app/src/main/AndroidManifest.xml` (`<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>` si la librería lo mete)
- Crear: `app/src/androidTest/java/com/dero/juststudykorean/poc/NanoTest.kt`, `NanoTasks.kt` (los 3 encargos y sus validadores)
- Crear: `app/src/androidTest/assets/poc/nano-cases.json` ← 30 casos por encargo, sacados de `golden/pairs` y `golden/grammar`
- Crear: `docs/poc/2-nano.md`

**Interfaces:**
- Consume: `golden/pairs/*.json`, `golden/grammar/*.json`, `golden/text.json` (la tarea 0 trae `is_grounded` y `leaks_answer`, para portarlos).
- Produce: `files/poc/nano-results.jsonl`: una línea por llamada con `task`, `ok`, `latencyMs`, `firstTokenMs`, `outTokens`, `valid` y `reason`.

**Los tres encargos** (los del plan, punto 10), siempre en inglés y siempre con el coreano copiado de las notas en el prompt:
1. **Coach:** redactar 2 o 3 frases a partir de hechos ya calculados (tema débil, tarjetas pendientes). Es válido si no añade coreano que no esté en los hechos.
2. **Explicación de respaldo:** explicar en inglés una entrada de gramática dada. Es válido si todo el coreano que sale está en la entrada.
3. **Pregunta Multiple choice:** a partir de un par (coreano, inglés). Es válida si pasa `is_grounded` y `!leaks_answer`, trae 4 opciones distintas y una sola correcta.

- [ ] **Paso 1: prueba que falla** `NanoTest.featureStatusIsAvailable`: `checkStatus()` debe dar `AVAILABLE`. Si da `DOWNLOADABLE`, la descarga del modelo la hace AICore (Ajustes → Google → AICore) **con wifi, fuera de la app**, y se repite.
- [ ] **Paso 2:** correrla en el OPPO y anotar el estado tal cual. Si da `UNAVAILABLE`, se para aquí: Nano no está en este teléfono y se pasa a la tarea 4.
- [ ] **Paso 3:** manifiesto. Compilar `assembleDebug` y buscar `INTERNET` en `app/build/intermediates/merged_manifest/debug/`. Si está, quitarlo con `tools:node="remove"` y volver a compilar.
- [ ] **Paso 4:** `NanoTest.tasksMeetLatencyAndValidity`: 30 casos × 3 encargos, con `ActivityScenario` de `MainActivity` en primer plano y el resultado guardado caso a caso. Calentamiento: 1 llamada que no cuenta.
- [ ] **Paso 5: modo avión.** `adb shell cmd connectivity airplane-mode enable`, repetir el paso 4 con 10 casos por encargo y `adb shell cmd connectivity airplane-mode disable` al terminar.
- [ ] **Paso 6:** `docs/poc/2-nano.md`.

**Métricas y criterios de aceptación**

| Métrica | Aceptación |
|---|---|
| Estado de la función | `AVAILABLE` sin que la app pida red |
| `INTERNET` en el manifiesto unido | ausente, y todo funciona igual |
| Modo avión | 100 % de las llamadas responden (sin errores de red) |
| Coach: latencia total | p90 ≤ 3 s |
| Explicación: primer token / total | p50 ≤ 1.5 s / p90 ≤ 6 s |
| Multiple choice válidas | ≥ 80 % (las inválidas se descartan, como en la web) |
| Coreano inventado antes de validar | se mide y se informa (el validador lo filtra) |
| Errores de cuota (`BUSY`/cuota) | se cuentan; ≤ 5 % con la app en primer plano |

**Decisión:** Nano como nivel 1 si cumple todo. Si falla solo la calidad, Nano para el Coach y la explicación, y las preguntas siguen por reglas. Si no está disponible, no funciona sin red o falla la latencia, se hace la tarea 4.

**Parar si:** para funcionar hace falta `INTERNET` (no se añade sin hablarlo contigo), o las condiciones de ML Kit GenAI obligan a enviar métricas que no se pueden desactivar. En ese caso se informa y decides tú.

---

## Tarea 3 — PoC importar un respaldo real a Room

**Rama:** `poc/3-room` (después de la tarea 0; en paralelo con la 1) · **No se mergea** el código; sí el formato del respaldo, si se aprueba.

**Pregunta:** ¿un respaldo de tu base real entra en Room de una vez, en una transacción, sin perder ni cambiar nada, y en un tiempo razonable?

**Archivos:**
- Modificar: `gradle/libs.versions.toml`, `app/build.gradle.kts` (Room con KSP, la versión que indique la skill *claude-android-ninja*; `kotlinx-serialization-json`)
- Crear: `app/src/androidTest/java/com/dero/juststudykorean/poc/room/PocDatabase.kt` (una entidad por tabla del respaldo, con columnas 1:1 y fechas como `Long` en UTC), `BackupImporter.kt`, `BackupExporter.kt`, `RoomImportTest.kt`
- Crear: `docs/poc/3-room.md`

**Interfaces:**
- Consume: `juststudy-backup-<fecha>.jsbackup.zip` (tarea 0), copiado al teléfono con `adb push` y `run-as … cp` a `files/poc/in/`.
- Produce:
  - `BackupImporter.import(zip: File, db: PocDatabase): ImportReport` con `tables: Map<String, Int>`, `millis` y `errors: List<String>`;
  - `BackupExporter.export(db: PocDatabase, out: File)`, que escribe el mismo formato.

- [ ] **Paso 1: prueba que falla** `RoomImportTest.importsEveryRowAndChecksum`: tras importar, el número de filas de cada tabla es igual al del `manifest.json` del ZIP y no hay errores.
- [ ] **Paso 2:** correrla y verla fallar.
- [ ] **Paso 3:** implementar el importador:
  - verificar el sha256 de cada JSONL antes de escribir nada;
  - todo dentro de un solo `withTransaction`;
  - si falla algo, no queda nada;
  - leer en streaming, línea a línea, sin cargar el ZIP en memoria.
- [ ] **Paso 4:** `RoomImportTest.roundTripIsIdentical`: importar → exportar → los JSONL de cada tabla son iguales byte a byte a los de entrada (mismo orden por `id` y mismo formato de fecha).
- [ ] **Paso 5:** `RoomImportTest.corruptZipLeavesDbEmpty`: un ZIP con un sha256 mal da error y deja 0 filas en todas las tablas.
- [ ] **Paso 6:** medir con el respaldo real: tiempo total, PSS pico y tamaño del `.db`. Sin datos en el informe, solo cifras.

**Métricas y criterios de aceptación**

| Métrica | Aceptación |
|---|---|
| Filas por tabla | 100 % iguales al manifiesto |
| Ida y vuelta | JSONL idénticos byte a byte |
| ZIP corrupto o a medias | error claro y base vacía (transacción) |
| Tiempo de importación | ≤ 10 s con tu respaldo real |
| Memoria | PSS pico ≤ 200 MB |
| Fechas | todas en UTC; la racha y los minutos por día de los últimos 30 días, iguales a los de la web (`GET /progress/summary` y `/progress/calendar` con la web arriba) |

**Decisión:** si pasa, el formato `.jsbackup.zip` v1 queda como el formato del respaldo de la app (plan, punto 11) y el importador se rehace limpio en `core/data` en la fase 2.

**Parar si:** el ZIP trae tablas que no están en el manifiesto, el web está en otra versión de alembic de la que se exportó, o algo pide borrar tus datos del web.

---

## Tarea 4 — PoC Gemma 4 E2B con LiteRT-LM (solo si Nano falla)

**Rama:** `poc/4-gemma` · Solo se empieza si la tarea 2 decide "no Nano" o "Nano parcial" y tú lo confirmas.

**Pregunta:** ¿Gemma 4 E2B en la GPU del OPPO cumple lo que Nano no cumplió, con un modelo de ~2.6 GB importado sin red?

**Archivos:**
- Modificar: `gradle/libs.versions.toml`, `app/build.gradle.kts` (`com.google.ai.edge.litertlm:litertlm-android`, según la skill *Run on-device LLMs with LiteRT-LM*); `app/src/main/AndroidManifest.xml` (las entradas de GPU que pida la skill)
- Crear: `app/src/androidTest/java/com/dero/juststudykorean/poc/GemmaTest.kt`. Reutiliza `NanoTasks.kt` y `nano-cases.json` de la tarea 2 (rebasar o copiar).
- Crear: `docs/poc/4-gemma.md`

- [ ] **Paso 1:** descargar el `.litertlm` de Gemma 4 E2B de `litert-community` en el Mac (nunca en el repo), `adb push` a `/data/local/tmp/poc/` y `run-as … cp` a `files/models/`.
- [ ] **Paso 2: prueba que falla** `GemmaTest.engineLoadsOnGpu`: el `Engine` se crea con backend GPU, fuera del hilo principal.
- [ ] **Paso 3:** `GemmaTest.tasksMeetLatencyAndValidity`: los mismos 3 × 30 casos y las mismas métricas que la tarea 2.
- [ ] **Paso 4:** medir la carga, la memoria y la temperatura (`adb shell dumpsys thermalservice` antes y después).

**Criterios:** los mismos umbrales que la tarea 2, más:
- carga del modelo ≤ 10 s;
- ≥ 10 tokens/s;
- PSS pico ≤ 4 GB;
- sin estrangulamiento térmico en 90 llamadas seguidas.

**Decisión:** Gemma como nivel 2 si cumple. Si no, la app se queda en el nivel 0 (sin IA), que ya funciona entera.

**Parar si:** la librería pide `INTERNET` o el modelo no carga en GPU.

---

## Tarea 5 — Decisiones

- [ ] Una entrada por PoC en `docs/DECISIONS.md`, la más reciente arriba, con el formato del archivo: fecha, decisión, por qué (con las cifras de `docs/poc/<n>.md`) y lo descartado.
- [ ] `mem_save` en Engram con `topic_key` `android/poc-mupdf`, `android/poc-nano`, `android/poc-room` y `android/poc-gemma`.
- [ ] Un PR solo de documentación (`docs/poc/`, `docs/DECISIONS.md`) a `main`; se mergea con tu confirmación.

---

## Formato del respaldo (`.jsbackup.zip` v1)

Lo escribe `tools/export/export_backup.py` desde la base MySQL del web (con los modelos SQLAlchemy de `app.database.models`) y lo leen el importador de la tarea 3 y, después, la app.

```text
juststudy-backup-2026-11-08.jsbackup.zip
├── manifest.json        # {"format":"jsbackup","version":1,"web_commit","alembic_head","exported_at" (UTC, ISO-8601 con Z),
│                        #  "source_time_zone", "tables":{"<tabla>":{"rows":N,"sha256":"…","columns":[…]}},
│                        #  "extracted":{"<document_id>":"sha256"}, "pdfs":{"<stored_name>":"sha256"}}
├── tables/<tabla>.jsonl # una fila por línea, ordenadas por clave primaria; fechas ISO-8601 UTC con Z,
│                        # booleanos true/false, columnas JSON como JSON, nulos como null
├── extracted/<document_id>.json   # copia de data/extracted (páginas ya procesadas)
└── pdfs/<stored_name>.pdf         # tus PDFs (se puede omitir con --no-pdfs)
```

- Todas las tablas de `models.py` **menos `chunks_index`**, que es de Chroma y se rehace al indexar.
- `source_time_zone` es la zona de MySQL (`SELECT @@session.time_zone, @@system_time_zone`). Las fechas sin zona del web se pasan a UTC con ella. Si no es UTC, se para y se avisa: hay que confirmar la conversión contigo.
- Solo lee: `SELECT` en una transacción de solo lectura. Nunca escribe en MySQL.

## Diseño del script de pruebas doradas (plan, punto 7)

`tools/golden/export_golden.py` corre con el venv del web y la carpeta `backend/` en `PYTHONPATH`, importa `app.*` sin modificarlo y escribe `golden/`. Las pruebas de Kotlin (fases 3–5) leen esos JSON y tienen que dar **exactamente** lo mismo.

**Uso:** `export_golden.py --out <dir> [--private]`. Sin opciones, solo usa los PDFs de prueba. Con `--private`, además lee `data/uploads` y `data/extracted` y escribe solo en `<dir>/private/` (ignorado por git). Cualquier otra ruta bajo `data/` sin `--private` sale con código 2.

**Entradas:** los 5 PDFs de `backend/tests/fixtures/notes_libreoffice/` y `docs/templates/Korean_Notes_Example.pdf`. Cada caso se nombra por el nombre del PDF (`Vocab_cafe_y_comida`).

### Formato de las doradas

| Archivo | Qué contiene | Sale de |
|---|---|---|
| `manifest.json` | `format: 1`; `web_commit` (`git rev-parse HEAD` del web); `alembic_head`; versiones de PyMuPDF, MuPDF y pdfplumber; por archivo, `sha256` y nº de casos. Sin fecha, para que sea determinista | — |
| `parser_raw/<pdf>.json` | por página: letras (`c`, `bbox` a 2 decimales, `size` a 1 decimal, `font`, `bold` = `flags & 16` o "bold" en la fuente), `imageCount` y `drawingItems` | `page.get_text("rawdict", flags=…)` con las mismas `flags` que `_extract_lines` |
| `parser/<pdf>.json` | `ParsedDocument` entero con `asdict` (páginas con `text`, `headings`, `section`, `tables[].rows/text/bbox`, cuentas y `warnings`), o `{"error": <clase>, "message": …}` | `parse_pdf` |
| `chunks/<pdf>.json` | lista de `Chunk` (`asdict` + `chroma_id` + `metadata()`) | `chunk_document(id, nombre, pages)` |
| `pairs/<pdf>.json` | por chunk, sus `Pair` | `extract_pairs` |
| `grammar/<pdf>.json` | por chunk, sus `GrammarPoint` | `extract_grammar` |
| `index/<pdf>.json` | `NotesIndex` sin los objetos `Hit` (por id de chunk): `points`, `sentences`, `verbs` ordenados y `aeo` ordenado | `build_index` |
| `teacher/<pdf>.jsonl` | una línea por pregunta de `check_notes` (semilla 7, muestra 40): `check`, `question`, `answer`, `sources` (n, chunk id) y `usedModel` | `SelfCheck` con `Tutor.ask` envuelto para grabar |
| `text.json` | casos de `norm`, `is_correct(tipo, dado, correcto)`, `english_answers`, `english_senses`, `initial_consonant`, `is_grounded`, `leaks_answer` y `widen_answer` | `cases.py` (abajo) |
| `korean.json` | casos de `aeo_forms`, `past`, `lemmas`, `has_form`, `same_grammar`, `pattern_forms` y `korean_cores` | `cases.py` |
| `sm2.json` | todas las secuencias de 1 a 4 valoraciones desde una tarjeta nueva (340), con `Scheduling` por defecto y con el de `flashcard_options.json`: `ease` a 4 decimales, `interval_hours`, `repetitions`, `due_in_minutes` y `label` | `scheduler.schedule`, `scheduler.label` |
| `compare_answer.json` | casos de `compareAnswer(typed, answer, korean)` con su `Comparison` entero | `compare_answer.mjs` con `node --experimental-strip-types` sobre `frontend/src/flashcards/compare.ts` (no importa nada) |

### Cómo se generan los casos (`cases.py`)

Sin casos escritos a mano que se queden viejos: salen de los PDFs de prueba y de mutaciones fijas.

- **Respuestas** (`is_correct`, `compareAnswer`, `widen_answer`), por cada par y punto de gramática:
  - la respuesta correcta tal cual;
  - en mayúsculas y con espacios de más;
  - con y sin artículo ("a"/"the");
  - con una letra cambiada, una de menos y una de más;
  - con la partícula cambiada (은/는, 이/가, 을/를);
  - la respuesta de otro par;
  - vacía;
  - con NBSP, ancho cero y jamo sin componer (NFD).
- **Coreano** (`has_form`, `same_grammar`, `aeo_forms`, `lemmas`): cada verbo de `index.verbs`, cada patrón de gramática contra cada frase de ejemplo de su chunk y contra las de otro chunk (casos negativos).
- **SM-2:** las 340 secuencias, más una tarjeta forzada a `interval_hours = 5` y `ease = 2.5` (`5 × 2.5 = 12.5`) para fijar el redondeo a par.
- Cada caso guarda la entrada y la salida; el orden es estable (ordenado por PDF, chunk e índice) y los conjuntos se escriben ordenados.

### Reglas

- **Determinista:** sin fechas ni azar sin semilla. JSON con `ensure_ascii=False`, `sort_keys=True`, indentación de 1 y NFC. Dos ejecuciones dan los mismos sha256 (prueba de la tarea 0).
- **Sin modelo:** `SelfCheck` ya usa embeddings y LLM falsos; si una respuesta de Teacher llama al modelo (`usedModel: true`), el caso se marca y se excluye de la comparación exacta.
- **La búsqueda no es la de Android:** las respuestas de Teacher dependen de la búsqueda por similitud de `SelfCheck`. En Kotlin la búsqueda será FTS. Por eso `teacher/*.jsonl` guarda también las fuentes, y la fase 5 compara primero el texto de la respuesta y luego si las fuentes son las mismas; una diferencia solo de fuentes se revisa a mano.
- **Versionado:** si el web cambia (no debería: está en pausa), se regenera y el diff de `golden/` se revisa en el PR.
- **Cómo lo usa Kotlin:** `core/korean` (fase 3) carga `golden/` desde `../golden` en sus pruebas JVM; cada respondedor o función se porta empezando por sus casos en rojo (TDD, plan punto 7.3).
