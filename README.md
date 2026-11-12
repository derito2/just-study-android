# Just Study 한국어 (Android)

App para estudiar coreano con tus propios PDFs, todo en el teléfono y sin internet (no tiene el permiso `INTERNET`). Hecha para un OPPO Find X8 Pro (arm64). La interfaz está en inglés.

## Instalar y actualizar

1. La clave de firma vive fuera del repo; su ruta y contraseñas van en `~/.gradle/gradle.properties` (`JUSTSTUDY_RELEASE_*`).
2. Compilar: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
3. Con el teléfono por USB y la depuración activada:
   `adb install -r app/build/outputs/apk/release/app-release.apk`

`install -r` conserva todos los datos. **Nunca desinstales la app para actualizar**: se borraría todo. Una APK firmada con otra clave (por ejemplo, la de debug) no se instala encima de la release.

## Respaldo

*More → Settings → Export backup* guarda todo (base, PDFs y textos extraídos) en un `.jsbackup.zip` donde tú elijas. *Import backup* lo reemplaza todo con un respaldo. Haz uno antes de cada actualización. Android no hace copias en la nube ni pasa los datos a otro teléfono (`allowBackup=false`): el respaldo es la única copia.

## IA en el teléfono (Gemini Nano)

Opcional. *Settings → On-device AI* muestra su estado; si dice *Download*, se descarga una vez con Wi-Fi (varios GB). Sin Nano la app funciona igual. Con Nano: el juez de respuestas en inglés, la explicación de tus errores, algunas preguntas del quiz, Teacher, el Coach, los pares de notas desordenadas, Grammar y la frase de la semana del Dashboard. Lo que escribe el modelo lleva la marca *AI* y nunca se guarda como tuyo.

## Pantallas

- **Dashboard:** dónde estás de verdad: progreso, sesión de hoy, puntos débiles y *Ask what to do* (el Coach).
- **Library:** sube PDFs digitales (sin OCR). Muestra lo extraído (palabras, gramática, fragmentos), *Reprocess*, *Delete* y *All vocabulary*.
- **Flashcards:** mazos de tus documentos con repetición espaciada; *Continue flashcards* sigue donde ibas.
- **Quiz:** quizzes hechos solo con tus notas, historial, *Practice saved questions* y *Quiz for your weaknesses*.
- **Teacher:** preguntas sobre tus notas (palabras, gramática, frases) con la fuente; modo Coach.
- **More:** Grammar, Calendar y Settings (tema, respaldo, Nano y la versión).

## Desarrollo

`./gradlew assembleDebug` · `./gradlew test` · `./gradlew lint`. El plan está en `docs/PLAN.md` y las decisiones en `docs/DECISIONS.md`.
