# Fase 4 — Library y PDF en el teléfono: plan de implementación

> **Para Claude Code:** este documento es el plan de la fase entera. Se trabaja en **una rama** (`fase-4/library`), con un commit por tarea y **un PR al final**, sin mergear sin la confirmación del usuario. Sub-skills: `superpowers:executing-plans` y `test-driven-development`. 📱 **Teléfono: sí en toda la fase**, por USB con la depuración activada; se pide al usuario que lo desbloquee y no se hacen capturas salvo que sean imprescindibles.

**Objetivo:** subir tus PDFs desde el teléfono y procesarlos en él, igual que la web:
- extraer el texto con MuPDF, partirlo en fragmentos, sacar la gramática y los temas, e indexarlo para buscar;
- un procesado por etapas que se puede reanudar si se corta;
- avisos de palabras y gramática repetidas;
- All vocabulary;
- reprocesar, borrar y guardar el PDF.

**Arquitectura:**
- **`:core:pdf`** (nuevo, librería Android): MuPDF 1.28.5, fijado y sin subirlo (DECISIONS), y el port de `rag/parser.py`.
- **`:core:data`:** el pipeline con WorkManager, el FTS de Room para la fase 5 y el repositorio de documentos.
- **`:app`:** la pantalla Library según `design/library.md`.

**Base:** `docs/PLAN.md` (puntos 8, 9 y 11), `design/library.md`, `docs/poc/1-mupdf.md` (el spike de tablas) y `golden/parser*` + `golden/db/`.

## Restricciones globales

- **Mismo resultado que la web.** Para los PDFs de prueba, el texto por página, los títulos, las tablas, los fragmentos, la gramática y los temas son idénticos a las doradas. Si no coinciden, se arregla el Kotlin.
- **Los PDFs entran solo por SAF.** Se copian al almacenamiento privado de la app y nunca se lee nada fuera del archivo que eligió el usuario.
- **Permisos:** solo `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_DATA_SYNC` (punto 8). Sin `INTERNET` ni permisos de almacenamiento. `check_manifest.py` se actualiza para permitir solo esos tres.
- **Procesar puede tardar,** pero tiene que ser correcto. No se procesa más de un PDF a la vez, y el modelo no se usa mientras se procesa.
- **Memoria:** PSS ≤ 300 MB procesando 10 PDFs seguidos. Se mide con el perfilador (DECISIONS de MuPDF).
- **Logs sin texto de tus PDFs.** No se cambian ajustes del teléfono. No se lee nada fuera del proyecto ni de su archivo.
- **Las notas desordenadas con el modelo son de la fase 6.** Aquí, si las reglas no entienden un fragmento, se marca y no se hace nada más.

## Riesgos que hay que probar

1. **ColorOS mata la app a mitad del procesado.** Al volver, sigue desde la última etapa guardada sin duplicar fragmentos, gramática ni temas. Prueba con `adb shell am kill` a mitad de proceso (tarea 3).
2. **Se sube el mismo PDF dos veces** (mismo SHA-256): se avisa y no se procesa otra vez (tarea 3).
3. **PDF escaneado, con imágenes, cifrado, vacío o corrupto:** el mismo mensaje que la web, el estado *Failed* o *Not supported*, y nada a medias en la base (tarea 1 y tarea 3).
4. **Borrar un documento mientras se procesa otro, o borrar el que se procesa:** el trabajo se cancela limpio y no quedan filas huérfanas (tarea 3).
5. **Un PDF grande** (el de 22 páginas, y 10 seguidos): memoria y tiempo dentro de los límites, la UI fluida mientras procesa y la notificación con el progreso (tarea 4).

---

## Tarea 1 — `:core:pdf`: el parser de la web en Kotlin

- **Archivos:** `core/pdf/` con:
  - `PdfParser.kt`: `fun parse(file: File): ParsedDocument`, que lanza `PdfError` (`Corrupt`, `Empty`, `NoDigitalText` o `ImagesNotSupported`, con los mensajes de la web);
  - `Lines.kt`: `_rows` y `_row_line`;
  - `Headings.kt`: tamaño del cuerpo, títulos y `_demote_header_rows`;
  - `Tables.kt`: cuadrícula sobre los segmentos dibujados, partiendo del spike de la PoC 1, y `table_to_text`.
- **Pruebas:**
  - `ParserGoldenTest`, instrumentada, porque MuPDF es nativo: los 6 PDFs de prueba contra `golden/parser/*.json`, comparando texto, títulos, sección, tablas, cuentas y avisos;
  - `RawGoldenTest`: las letras contra `golden/parser_raw`;
  - un caso por cada `PdfError`.
- [ ] Pruebas en rojo → implementar → verde en el OPPO → commit.
- **Parar si:** una tabla de los PDFs de prueba no coincide con pdfplumber después de portar el spike. Hay que decir cuál y por qué antes de aceptar una diferencia.

## Tarea 2 — Datos de documentos e índice FTS

- **Archivos** en `core/data/`:
  - `documents/DocumentRepository.kt`: lista, estado, avisos, `extracted(docId)`, borrar (con la opción de borrar también las tarjetas), reprocesar y `vocabulary(query)`;
  - `search/NotesFts.kt`: tabla FTS4 de Room sobre los fragmentos, para la fase 5;
  - la migración de Room de la versión 1 a la 2, con su prueba `MigrationTest`, porque ya hay datos reales importados.
- **Doradas:** `golden/db/` ya trae la base justo después de procesar los PDFs de prueba (Tarea 2 de la Fase 3). El pipeline de Kotlin, aplicado a los mismos PDFs, tiene que dejar `documents` (sin fechas ni rutas), `grammar_points`, `topics` y `document_topics` iguales, y los fragmentos iguales a `golden/chunks`.
- **`analyze_extracted`** (*What the app extracted*): una dorada nueva desde `tools/golden` si no existe.
- [ ] Pruebas en rojo → implementar → verde → commit.

## Tarea 3 — Pipeline con WorkManager

- **Archivos:**
  - `core/data/.../processing/ProcessDocumentWorker.kt`, con las etapas `PENDING → EXTRACTING → PARSING → CHUNKING → INDEXING → READY | FAILED`;
  - `ProcessingQueue.kt`, que procesa un PDF a la vez.
- **Comportamiento:**
  - cada etapa guarda su resultado y el estado en Room antes de pasar a la siguiente;
  - al reanudar, se empieza en la primera etapa sin terminar;
  - mientras procesa: foreground service de tipo `dataSync` con la notificación de progreso. `POST_NOTIFICATIONS` se pide la primera vez que subes un PDF; si lo niegas, el PDF se procesa igual, sin notificación.
- **SHA-256** al copiar. Si ya existe: aviso *"This PDF is already in your Library."* y no se crea otro documento.
- **Repetidos:** tras procesar, la lista de palabras y gramática repetidas que da `DuplicateDialog` en la web. Se comprueba con una dorada nueva desde `tools/golden` (procesar un PDF de prueba que repite palabras de otro).
- **Pruebas:**
  - en la JVM, `WorkManagerTestInitHelper`: `resumesFromLastStage`, `samePdfTwiceIsRejected`, `deleteWhileProcessingCancelsCleanly` y `failedPdfLeavesNoRows`;
  - en el OPPO: matar el proceso a mitad (riesgo 1) y comprobar que termina igual.
- [ ] Pruebas en rojo → implementar → verde → commit.

## Tarea 4 — Pantalla Library

- **Archivos** en `app/.../ui/library/`:
  - `LibraryScreen.kt` y `LibraryViewModel.kt`;
  - `DocumentCard.kt`, `ExtractedView.kt` y `DuplicateDialog.kt`;
  - `AllVocabularyScreen.kt`, con LazyColumn y buscador fijo encima del teclado.
- **Según** `design/library.md`, sin la sección E (fase 6). SAF:
  - `OpenMultipleDocuments` con `application/pdf` para subir;
  - `CreateDocument` para *Save PDF*.
- **Capturas Roborazzi:** `libraryEmpty`, `libraryProcessing`, `libraryReady`, `libraryFailed`, `detailsWords`, `duplicateWord`, `deleteDialog`, `allVocabulary`, `allVocabularyNoMatch` y `libraryAt130Percent`.
- **En el OPPO:**
  - subir los 5 PDFs de prueba de una vez desde Descargas: el usuario los copia allí, o se hace con `adb push` solo de esos archivos y se borran al terminar;
  - procesar los 10 seguidos (los 5 dos veces, reprocesando): PSS ≤ 300 MB con el perfilador;
  - la UI se mantiene fluida mientras procesa, con el percentil 90 de fotogramas por debajo de 16 ms al desplazar la lista;
  - la notificación de progreso aparece.
- **Tu base:** antes, exportar un respaldo a la carpeta privada de la app; al final, restaurarlo y comprobar las 1265 filas y los mismos sha256, como en la Fase 3.
- [ ] Capturas en rojo → implementar → grabarlas → comprobar en el OPPO → commit.

## Al terminar la fase

- [ ] `DECISIONS.md`: la detección de tablas sin pdfplumber, la migración v1→v2, los permisos de notificación y foreground, y cualquier diferencia aceptada con la web.
- [ ] `mem_save` con `topic_key` `android/fase-4`.
- [ ] PR con la tabla de metas: memoria con 10 PDFs, tiempo por página (p50 y p90), fotogramas con el procesado en marcha y reanudación tras matar el proceso. ✅/⚠️/❌ por tarea.
