# PoC 1 — MuPDF frente a lo que extrae la web

**Fecha:** 2026-11-08 · **Rama del código:** `poc/1-mupdf` (no se mergea) · **Plan:** `docs/POC_PLAN.md`, tarea 1.

**Pregunta:** ¿MuPDF para Android da, letra a letra, lo mismo que PyMuPDF (`rawdict`), y se pueden sacar las tablas sin pdfplumber?

**Respuesta corta:** sí en las letras: 100 % igual en los 6 PDFs de prueba y en tus 4 PDFs, con diferencia de bbox 0. Las tablas, en el único caso que hay (un PDF sintético), también salen iguales, pero ese caso es demasiado simple para dar el detector por resuelto.

## Montaje

| | |
|---|---|
| Teléfono | OPPO Find X8 Pro (CPH2659), Android 17 (API 37), cargando por USB (batería 49 %), pantalla encendida |
| Build | `debug`, pruebas instrumentadas (`am instrument`, sin desinstalar para leer `files/poc/`) |
| Android | `com.artifex.mupdf:fitz` **1.28.5** (MuPDF 1.28.5) desde `maven.ghostscript.com`; trae `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64` |
| Referencia | PyMuPDF **1.28.2** (MuPDF 1.28.2), pdfplumber 0.11.10, web `ed270f4` (`golden/`, tarea 0) |
| Opciones | `toStructuredText("preserve-whitespace,mediabox-clip=yes")`, las mismas `flags` que `_extract_lines` |
| Manifiesto unido | solo `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (propio, `signature`); la AAR no pide permisos; sin `INTERNET` |

Entradas:
- **6 PDFs de prueba** (11 páginas, 9050 letras).
- **Tus 4 PDFs** (50 páginas, 40 801 letras, de 93 KB a 361 KB). Pasaron a `files/poc/in/` con `run-as`, y la copia temporal de `/data/local/tmp` se borró. Del teléfono solo sale un resumen anónimo (`private-1…4`) con cifras, sin texto ni nombres.
- **`Tablas_sinteticas.pdf`:** 2 páginas, cada una con una tabla de 4 × 3. Una tiene los bordes trazados y la otra como barras rellenas finas, como las exporta LibreOffice.
- **8 PDFs de error sintéticos:** cifrado con contraseña de usuario, cifrado solo con contraseña de propietario, en blanco, escaneado (solo imagen), poco texto, texto con imagen grande, texto con imagen pequeña y corrupto. Para cada uno se guarda el veredicto de `parse_pdf` de la web.

## Métricas contra los criterios

| Métrica | Resultado | Aceptación | |
|---|---|---|---|
| Letras por página iguales (PDFs de prueba) | 11 / 11 páginas | 100 % | ✅ |
| Texto por página igual, NFC (PDFs de prueba) | 11 / 11 páginas | 100 % | ✅ |
| Texto por página igual, NFC (tus 4 PDFs) | 50 / 50 páginas (similitud mínima 1.0) | ≥ 98 % | ✅ |
| `size` por letra | 9050 / 9050 (tuyos: 40 801 / 40 801) | ≥ 99.5 % | ✅ |
| `bold` por letra | 9050 / 9050 (tuyos: 40 801 / 40 801) | ≥ 99.5 % | ✅ |
| `font` por letra (sin prefijo de subconjunto) | 9050 / 9050 (tuyos: 40 801 / 40 801) | — | ✅ |
| bbox por letra, diferencia máxima | 0.00 pt (redondeado a 2 decimales como en la web) | ≤ 0.5 pt | ✅ |
| Imágenes ≥ 10 % por página | 61 / 61 páginas iguales | todas | ✅ |
| Elementos de dibujo por página | 63 / 63 páginas iguales (incluidas las 2 con tablas: 9 = 9) | todas | ✅ |
| Tablas (spike) | 2 / 2 tablas y 24 / 24 celdas iguales a pdfplumber (solo el PDF sintético) | todas iguales | ✅ ⚠️ |
| Tiempo por página (abrir página + texto + dibujos) | p50 10.9 ms · p90 17.3 ms · máx. 28.4 ms (61 páginas) | p90 ≤ 1 s | ✅ |
| Memoria, PSS pico con el PDF más grande (22 págs., 361 KB) | 95 MB en un proceso nuevo | ≤ 300 MB | ✅ ⚠️ |
| Errores (`PdfError` y subclases): clase y mensaje | 8 / 8 iguales a la web | el mismo mensaje | ✅ |

### Notas

- **Versiones distintas, mismo resultado.** La AAR (1.28.5) y PyMuPDF (1.28.2) traen parches distintos de MuPDF, y aun así no hay ninguna diferencia. Cuando se actualice cualquiera de los dos hay que repetir esta prueba: las doradas fijan la versión en `golden/manifest.json`.
- **Tablas (⚠️).** Ningún PDF real, ni de prueba ni tuyo, tiene tablas rejilladas: en todas sus páginas `drawingItems` es 0. El spike solo se probó con tablas sencillas, sin celdas unidas, sin renglones partidos dentro de una celda y con una tabla por página. La reconstrucción busca las líneas horizontales y verticales (trazadas o como barras finas rellenas), agrupa sus coordenadas, asigna cada letra a su celda por el centro y separa con un espacio los renglones de una misma celda. Con eso basta para lo que hay hoy. Las celdas unidas y varias tablas por página quedan para la fase 4.
- **Memoria (⚠️).** 95 MB es el pico del proceso que solo abre tus PDFs, empezando por el más grande. En una sola pasada con los 11 PDFs seguidos, el PSS del proceso de pruebas sube hasta 335 MB. Lo más probable es que sean los JSON de las doradas que retiene la prueba y la memoria que el recolector aún no ha liberado, pero no se ha comprobado con el perfilador. Medido por PDF, el pico se queda en 95 MB. La fase 4 tiene que procesar un PDF por trabajo y destruir `Page`/`StructuredText` (aquí ya se destruyen), y la fase 7 medirá con el perfilador.
- **Errores.** Un PDF cifrado solo con contraseña de propietario se abre sin pedirla, en la web y en MuPDF, y se procesa. Uno con contraseña de usuario da `CorruptPdfError` ("password-protected") en los dos. El PDF corrupto falla al abrirse en los dos, porque MuPDF no lo "repara" como un documento vacío.
- **Bug de la prueba (corregido).** En `org.json`, `optString` sobre un `null` de JSON devuelve la cadena `"null"`, y eso daba 6/8 en errores. Se leen los nulos con `isNull`. Hay que tenerlo en cuenta al portar código que lea JSON.
- **Cómo se corre.** `connectedDebugAndroidTest` desinstala la app al acabar y se pierden los resultados de `files/poc/`. Hay que usar `installDebug installDebugAndroidTest` y `adb shell am instrument -w -e package com.dero.juststudykorean.poc …`. Las métricas se guardan por PDF y se reanudan si la prueba se corta (`-e fresh true` para empezar de cero).

## Decisión propuesta

**MuPDF** (`com.artifex.mupdf:fitz`) como lector de PDF en Android. Cumple todos los criterios.
- Las tablas rejilladas sencillas salen iguales a pdfplumber con el detector del spike.
- En la fase 4 el detector se amplía con celdas unidas y varias tablas por página.
- No hace falta la PoC de PdfBox-Android.

Queda pendiente de tu confirmación en la tarea 5 (`docs/DECISIONS.md` y Engram `android/poc-mupdf`).
