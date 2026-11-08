# Just Study 한국어 Android — guía para Claude Code

App Android nativa (Kotlin + Jetpack Compose, Material 3) para el OPPO Find X8 Pro del usuario. Todo en el teléfono, sin internet. El usuario habla español; la UI va en inglés. Plan completo: `docs/PLAN.md`; decisiones: `docs/DECISIONS.md`.

## Ahorra tokens (léelo primero)
- **No leas documentos enteros.** `docs/PLAN.md` y los docs del proyecto web son de consulta: `grep -n "^#"` y lee solo la sección que necesitas.
- **Código: primero CodeGraph** (`codegraph_explore` MCP o `codegraph explore "<pregunta>"`) aquí y en el proyecto web, antes de abrir archivos. Lee solo los rangos que vas a cambiar.
- **Memoria: Engram**, proyecto `just-study-android`, `topic_key` `android/*`. Las memorias del web están en el proyecto `korean-study-assistant` (`mem_search`).
- Respuestas cortas: resultado, ✅/⚠️/❌ y lo que falta.

## Proyecto web: referencia de solo lectura
`/Users/dero/Documents/visual/korean-study-assistant` — de ahí salen el contexto (CLAUDE.md, docs/), el Python a portar (`backend/app/`) y las pruebas doradas. **No se edita** desde aquí.

## Protocolo (dos sesiones)
1. **Diseño (claude.ai):** planes de fase, sistema de diseño, pantallas, revisión de diffs y prompts cortos para Claude Code. No compila ni usa git.
2. **Claude Code (aquí):** Kotlin, pruebas, `android run` en el OPPO, git/gh y PR. No rediseña pantallas por su cuenta.
3. El diseño llega en `design/<pantalla>.md` (componentes, tokens, medidas, estados) + PNG.
4. Nunca los mismos archivos desde las dos sesiones a la vez. **No mergear sin la confirmación del usuario en esa tarea.**

## Mac y teléfono
- MacBook Air M1, **8 GB**: **sin emulador** (el OPPO por USB). `org.gradle.jvmargs=-Xmx2g`. Al compilar, parar la web (`/Users/dero/Documents/visual/korean-study-assistant/scripts/study.sh stop`), Colima y Ollama.
- adb: `~/Library/Android/sdk/platform-tools/adb` (añadir al `PATH` si no está).

## Comandos
- `./gradlew assembleDebug` · `./gradlew test` · `./gradlew lint`
- `android run` (instala y abre en el OPPO) · `android docs <tema>` · `android skills list`
- `adb devices` · `adb install -r app/build/outputs/apk/debug/app-debug.apk` · `adb logcat`

## Seguridad (plan, punto 8)
- **Permisos permitidos:** `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`, `USE_BIOMETRIC` (opcional).
- **Prohibidos:** `INTERNET`, `READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `CAMERA`, `RECORD_AUDIO`, ubicación, contactos, `QUERY_ALL_PACKAGES`. PDFs por SAF.
- Revisar cada versión el manifiesto unido (`app/build/intermediates/merged_manifest`); si una librería mete `INTERNET`, quitarlo con `tools:node="remove"` y comprobar sin red.
- `android:allowBackup="false"` y reglas de extracción vacías. Solo la actividad principal `exported`; sin `FileProvider` salvo para compartir un respaldo elegido.
- Release: `debuggable=false`, R8 activo, clave de firma fuera del repo. Datos, PDFs y modelos en almacenamiento privado.
- Sin analíticas ni crash reporting de terceros. Los logs nunca llevan texto de las notas ni respuestas.
- Antes de cada APK: skills *Audit and fix permission security* e *Android Intent security*.
- Nunca commitear `local.properties`, `*.jks`, `*.keystore`, modelos (`*.gguf`, `*.tflite`, `*.litertlm`, `models/`), `golden/private/` ni la base de `.codegraph/`.
