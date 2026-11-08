# Decisiones — Just Study Android

Una entrada por decisión, la más reciente arriba: fecha, decisión, por qué y alternativas descartadas. Las de los PoC (fase 1) también van a Engram (`android/*`).

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
