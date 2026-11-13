# Fase 9 — Responder hablando en coreano dentro del Quiz: arquitectura y plan

> **Para Claude Code:** primero la **prueba de concepto** (tarea 1), en su propia rama, que **no se mergea**. Con su informe, **para**: el dueño elige el motor. Solo entonces siguen las tareas 2–7 (rama `fase-9/speaking`, un commit por tarea, un PR, sin mergear sin confirmación). Empieza cada tarea por sus pruebas.
>
> **Diseño:** `design/quiz-speaking.md`. No es una pantalla nueva: es un tipo de pregunta del Quiz.
>
> 📱 **Teléfono:** sí en las tareas 1 y 7. **El dueño habla; Code no puede.** El dueño acepta él mismo el diálogo del micrófono y cualquier descarga del sistema.
>
> **Independiente de la fase 8.** Solo comparten `Hangul.jamoDistance` (fase 8, tarea 1); si la fase 9 va antes, esa función se hace aquí.

**Origen:** `JustStudyKorean_Games_y_Quiz_Oral_Especificacion.md` (el encargo del dueño, partes II y III).

## 0 · Decisiones del dueño

| ID | Pregunta | Propuesta | Por qué |
|---|---|---|---|
| D9-1 | **Permiso `RECORD_AUDIO`.** Hoy está **prohibido** (`CLAUDE.md`, *Seguridad*; `tools/ci/check_manifest.py`, lista `FORBIDDEN`) | Permitirlo solo para este tipo de pregunta, pedido al tocar el micrófono | Sin él no hay voz. Es una decisión de privacidad del dueño: hace falta su sí explícito, anotado en DECISIONS. |
| D9-2 | ¿Cómo se guarda una pregunta hablada? | Como una pregunta **`write_ko`** normal; «hablada» es solo la forma de responderla en ese quiz | El tipo `write_ko` ya existe en la web y en el respaldo. Un tipo nuevo en la tabla `questions` llegaría a la web por el respaldo sin que ella lo conozca. Así no cambia ni una tabla ni una dorada. |
| D9-3 | Motor de reconocimiento | El que gane la prueba de concepto. Orden de prueba: **B** (plataforma, sin dependencia) → **A** (ML Kit, alpha) → **C** (modelo propio) solo si los otros fallan | Ver la sección 1. |
| D9-4 | Si el motor elegido es ML Kit: dependencia `com.google.mlkit:genai-speech-recognition` (alpha) | Solo si B no sirve y A sí | Alpha: puede cambiar sin aviso. |
| D9-5 | Si el motor elegido es un modelo propio (opción C): librería nativa nueva en la APK y un archivo de modelo de decenas o cientos de MB que **el dueño carga a mano** (la app no tiene `INTERNET`) | Solo si A y B fallan, y tras ver tamaños y medidas | Es el coste mayor: NDK, tamaño, y un paso manual de carga del modelo. |
| D9-6 | Intentos: 3 por pregunta; lo incierto y los fallos técnicos no gastan intento | Sí | Encargo, punto 16. |

## 1 · Motores: qué se sabe y qué hay que medir

**Comprobado en la documentación oficial el 2026-11-13** (developers.google.com/ml-kit/genai/speech-recognition/android y developer.android.com/reference/android/speech/SpeechRecognizer). **[H]** = hipótesis que solo la prueba de concepto resuelve.

| | **A · ML Kit GenAI Speech Recognition** | **B · `SpeechRecognizer` de Android, en el dispositivo** | **C · modelo propio (whisper.cpp o sherpa-onnx)** |
|---|---|---|---|
| Dependencia | `com.google.mlkit:genai-speech-recognition:1.0.0-alpha2` (nueva) | ninguna (plataforma; `minSdk` 31 ya lo cubre) | librería nativa nueva (JNI) + archivo de modelo |
| Estado | **alpha**: sin SLA, puede romper compatibilidad | estable: `createOnDeviceSpeechRecognizer` (API 31), `checkRecognitionSupport` y `triggerModelDownload` (API 33) | proyectos de código abierto activos |
| Coreano | **Basic:** `ko-KR` en beta. **Advanced:** `ko-KR` | depende del servicio de reconocimiento instalado en el teléfono **[H]** | sí (modelos multilingües o coreanos) |
| ¿En el OPPO? | Basic: «dispositivos con API 31+». Advanced: Pixel 10/11 y dispositivos con Gemini Nano en una lista de chips que incluye **MT6991**; el Find X8 Pro **no** se nombra. El Find X8 Pro sí está en la lista de la Prompt API (Nano ya funciona en él). Si su chip es el MT6991 y si Advanced está activo en ColorOS: **[H]** | qué servicio trae ColorOS y si tiene coreano sin red: **[H]** | sí (CPU arm64) |
| Sin red | modelos en el dispositivo; la **descarga** inicial la hace el sistema (AICore o el paquete de idioma), como con Nano. Que el uso funcione en modo avión: **[H]**, a probar | el reconocedor «on-device» no debe usar red; a probar en modo avión **[H]**. Hay informes de teléfonos donde el servicio falta o devuelve error de red | 100 % local por construcción |
| Descarga inicial | sí, por el sistema (con red, una vez) | puede hacer falta un paquete de idioma, por el sistema (`triggerModelDownload` abre su propia pantalla) | el modelo lo carga el dueño (por `adb push` a la carpeta privada, o un *Import speech model* por SAF) |
| Tamaño | 0 en la APK | 0 | librería: varios MB. Modelo, **de memoria y a comprobar**: Whisper tiny ≈ 75 MB, base ≈ 140 MB, small ≈ 470 MB (menos cuantizados); sherpa-onnx zipformer coreano en streaming, del orden de decenas a cientos de MB |
| Nivel de micrófono y parciales | flujo de resultados parciales | `onRmsChanged` (nivel) y `onPartialResults` | hay que capturar el audio en la app (`AudioRecord`) |
| Confianza | sin documentar | `CONFIDENCE_SCORES` (puede venir vacío) | según el modelo |
| Audio | micrófono directo o un descriptor de archivo | micrófono directo; la app nunca ve el audio | **la app captura el audio** (en memoria; nunca a disco) |
| Licencia | términos de ML Kit GenAI | plataforma | whisper.cpp MIT; sherpa-onnx Apache-2.0; cada modelo, la suya |
| Permisos | `RECORD_AUDIO` (+ lo que añada su manifiesto: revisar) | `RECORD_AUDIO` + `<queries>` de `android.speech.RecognitionService` | `RECORD_AUDIO` |

**Lo que ninguno da:** una nota de pronunciación. Los tres devuelven texto. Una evaluación fonética real (alineación por fonema) queda fuera de esta fase; si algún día se quiere, es otra investigación y otra aprobación.

**Gemini Nano (Prompt API) no reconoce voz**: no se usa aquí. Que Nano esté *Ready* en el OPPO no dice nada sobre el reconocimiento de voz.

**Nunca hay respaldo silencioso en la nube.** Si el motor local no está, el tipo de pregunta queda deshabilitado.

## 2 · Prueba de concepto (tarea 1)

Rama `poc/4-speech`, como las PoC anteriores: una app aparte (`applicationIdSuffix ".speechpoc"`, etiqueta *JS Speech PoC*), con `RECORD_AUDIO` **solo en su manifiesto**. No toca la app del dueño ni la QA. Una pantalla mínima: elegir motor, elegir frase, botón de escuchar, y una tabla de resultados que se guarda en un CSV en la carpeta privada (frase esperada, condición, transcripción, tiempos; **sin audio**).

**Paso 1 · Disponibilidad (sin hablar):**
- B: `SpeechRecognizer.isOnDeviceRecognitionAvailable`, `createOnDeviceSpeechRecognizer`, `checkRecognitionSupport` con `ko-KR` → ¿instalado, descargable o no soportado? Qué paquete da el servicio.
- A: `checkStatus` en Basic y en Advanced con `ko-KR`.
- Si alguno pide descarga: **parar y decírselo al dueño** (la acepta él, con red).

**Paso 2 · Protocolo (habla el dueño).** Por cada motor disponible:

| Frases | Condiciones (cada frase en cada una, 2 veces) |
|---|---|
| 안녕하세요 · 감사합니다 · 학교에 가요 · 오늘은 날씨가 좋아요 · 저는 한국어를 공부하고 있어요 | normal · despacio · rápido · voz baja · con ruido moderado (música o tele de fondo) · teléfono a un brazo de distancia |
| + 10 palabras sueltas de 2–3 bloques (sintéticas: 학교, 친구, 사과, 도서관, 병원, 먹다, 마시다, 크다, 예쁘다, 날씨) | normal, 2 veces |
| + 5 **errores a propósito** (decir otra palabra parecida: 사과 → 사자; 학교 → 학생…) | normal |

Son unas 85 locuciones por motor (15–20 min).

**Paso 3 · Sin red:** repetir 10 locuciones en **modo avión** (lo activa y lo quita el dueño) y **tras reiniciar** el teléfono.

**Métricas** (por motor y por condición):
- **Acierto exacto:** transcripción normalizada (sección 4) igual a la esperada.
- **CER** (tasa de error por carácter): distancia de edición entre los bloques silábicos esperados y los reconocidos, sin espacios, dividida por el número de bloques esperados. Se usa CER y no WER porque el espaciado coreano del reconocedor no es fiable y contaría como errores de palabra lo que no lo es.
- **Falsos aciertos:** de los errores a propósito, cuántos se dieron por buenos.
- **Posición del acierto** en la lista de candidatos (1.º, 2.º–3.º, fuera), si el motor da varios.
- **Latencia:** de fin de habla a resultado final (p50, p90); tiempo de arranque del reconocedor; primera vez en frío.
- **Recursos:** PSS de la app; para C, además CPU y temperatura de batería antes y después (`dumpsys battery`, solo lectura).
- **¿Funciona en modo avión? ¿Y tras reiniciar?**

**Criterio para integrar** (si ningún motor lo cumple, se informa y no se integra):
1. funciona en modo avión: **obligatorio**;
2. acierto exacto ≥ 85 % en condición normal (frases y palabras bien dichas);
3. falsos aciertos ≤ 1 de 5;
4. latencia p90 ≤ 2,5 s;
5. no añade `INTERNET` ni permisos fuera de `RECORD_AUDIO`.

**Entrega:** `docs/poc/4-speech.md` con la tabla, la recomendación y lo que no se pudo medir. Las transcripciones de las frases de prueba sí pueden ir (son sintéticas). **Parar aquí.**

## 3 · Arquitectura

```
:core:korean (puro)
  korean/speech/SpokenAnswer.kt      normalizar y evaluar: (esperada, candidatos, confianzas?) → SpokenVerdict

:core:data
  data/speech/KoreanSpeech.kt        contrato + tipos
  data/speech/PlatformKoreanSpeech.kt   si gana B
  (si gana A: la implementación va en :core:ai, el único módulo que ve ML Kit; NoMlKitInScreensTest sigue valiendo)
  (si gana C: módulo nuevo :core:speech con el JNI; se decide tras la PoC)

:app
  ui/quiz/SpeakingBlock.kt           el bloque de voz de la Card (MicButton, LevelWave, estados)
  ui/quiz/QuizViewModels.kt          SpeakingUi dentro de QuestionUi; intents listen / cancel / typeInstead / showAnswer
  ui/quiz/QuizHomeScreen.kt          la novena casilla
```

**Contrato** (independiente del motor):

```kotlin
sealed interface SpeechAvailability { Ready; NeedsDownload; Unsupported; data class Error(val code: String) }

sealed interface SpeechEvent {
    data object Listening : SpeechEvent
    data class Level(val value: Float) : SpeechEvent            // 0..1, si el motor lo da
    data class Partial(val text: String) : SpeechEvent
    data class Final(val candidates: List<String>, val confidences: List<Float>?) : SpeechEvent
    data class Failed(val reason: SpeechFailure) : SpeechEvent  // NoSpeech, NotUnderstood, Busy, Engine, Timeout
}

interface KoreanSpeech {
    suspend fun availability(): SpeechAvailability
    /** Abre el micrófono y emite hasta Final o Failed. Cancelar la corrutina cierra el micrófono. */
    fun listen(maxMs: Long): Flow<SpeechEvent>
}
```

- Una sola escucha a la vez. `SpeechRecognizer` se usa **solo desde el hilo principal** y se destruye (`destroy()`) al terminar cada escucha o al cancelar.
- Idioma `ko-KR` fijo. Hasta 5 candidatos.
- La app **no** guarda ni ve audio con A o B. Con C, el audio vive en un búfer en memoria que se borra al terminar.
- `FakeKoreanSpeech` para las pruebas (guiones de eventos).
- **Con la puerta del modelo:** mientras hay una escucha abierta, `QuestionViewModel` no llama al juez ni a la explicación; al resolverse la pregunta, siguen como siempre.

## 4 · Evaluación (`SpokenAnswer.kt`)

**Normalizar** (las dos partes): `nfc` → quitar espacios → quitar `. , ! ? ~ … " '` → nada más. **No** se quitan partículas ni terminaciones, **no** se pasa a raíz, **no** hay «parecido suficiente» que apruebe.

**Respuestas válidas:** la de la pregunta y sus alternativas (`KOREAN_ALTERNATIVES`, « / »), igual que `isCorrect("write_ko", …)`.

**Veredicto**, con los candidatos del motor en orden:

| Caso | Veredicto |
|---|---|
| el **primer** candidato, normalizado, es una respuesta válida | **Correct** |
| el 2.º o 3.º candidato lo es | **Uncertain** |
| el primero tiene caracteres que no son hangul (cifras, latín) | **Uncertain** |
| `jamoDistance(primero, válida) ≤ max(1, bloques / 4)` | **Uncertain** (casi) |
| el motor da confianza y la del primero está por debajo del umbral que fije la PoC | **Uncertain** |
| cualquier otro | **Mismatch** |
| sin candidatos o vacío | (no llega aquí: es `NotUnderstood`) |

- *Uncertain* y los fallos técnicos **no gastan intento**. *Mismatch* gasta uno de 3; al tercero, incorrecta.
- Tras 4 *Uncertain* seguidos en la misma pregunta, la línea de estado añade *You can type it instead.* (no se fuerza nada).
- **Qué se guarda:** una sola llamada a `QuizRepository.answer` al resolver la pregunta, con la última transcripción como respuesta (o lo escrito, si usó *Type it instead*; o `—` si usó *Show answer* sin haber hablado). La corrección la decide `isCorrect` como siempre: una transcripción válida es correcta por las mismas reglas que una respuesta escrita.

**Pruebas (`SpokenAnswerTest`):** igual con y sin espacios; con signos; alternativas « / »; NFD; casi (un jamo); cifras; latín; segundo candidato; confianza baja; partícula distinta (`학교에` frente a `학교를` → Mismatch, nunca Correct).

## 5 · Integración con el Quiz

- **Formulario:** tipo de formulario `speak_ko` (*Say it in Korean* · 한국어로 말하기 · *Answer out loud*) en `QUIZ_TYPES`, guardado en `UserPrefs` como los demás, **sin marcar por defecto**. Deshabilitado si `availability()` no es `Ready` ni `NeedsDownload` (diseño, A).
- **Al generar:** `speak_ko` **no** se manda al generador. Si está marcado, la petición lleva `write_ko` (se añade si no estaba). De las preguntas `write_ko` que salgan cuya respuesta sea solo hangul y tenga al menos 2 bloques: si el usuario marcó solo `speak_ko`, **todas** se responden hablando; si marcó los dos, **alternas** (la primera hablada). Los ids hablados se guardan en el `SavedStateHandle` (`LongArray`).
- **`QuizGenerator`, `QuizBuilders` y las tablas no cambian.** Ninguna dorada cambia.
- **Variante *Lee en voz alta*:** si la respuesta tiene 2 palabras o más, la Card muestra la frase coreana y su traducción (diseño, B). Si no, el significado en inglés.
- **Tras matar el proceso:** los ids hablados se recuperan del `SavedStateHandle`; si no están, la pregunta se abre escrita.
- **Preguntas del modelo:** nunca son habladas.
- **Tiempo de respuesta:** desde que se muestra la pregunta hasta que se resuelve.

## 6 · Permiso, privacidad y ciclo de vida

- **Manifiesto:** `RECORD_AUDIO` y, para B, `<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>`.
- **`check_manifest.py`:** `RECORD_AUDIO` pasa de `FORBIDDEN` a `ALLOWED_PERMISSIONS`, con su comentario y la fecha de la decisión; su prueba se actualiza. `CLAUDE.md` (*Seguridad*) se actualiza en el mismo commit.
- **Petición:** `ActivityResultContracts.RequestPermission`, solo al tocar el micrófono. Antes, la línea de explicación del diseño. Denegado dos veces (o «no volver a preguntar») → estado *Permission denied*; la app no abre los ajustes del sistema.
- **El micrófono se cierra** (cancelando la corrutina de `listen`) al: resolver, cancelar, pasar de pregunta, abrir *Leave quiz*, salir, `ON_STOP`, o perder el foco de audio. Prueba para cada caso con el motor falso.
- **Nunca** se escribe audio a disco. **Nunca** va la transcripción a los logs (solo veredicto, intento, tiempos y código de error).
- Skills antes del PR: `android-permissions-security` y `android-intent-security` sobre el manifiesto unido.

## 7 · Tareas

1. **PoC** (sección 2). 📱 **Parar al terminar.**
2. **Decisiones:** D9-1…D9-6 en DECISIONS; manifiesto, `check_manifest.py`, `CLAUDE.md`.
3. **Evaluación:** `SpokenAnswer.kt` con sus pruebas.
4. **Motor:** `KoreanSpeech` + la implementación elegida + `FakeKoreanSpeech`. Prueba instrumentada de disponibilidad (se salta si no hay motor).
5. **ViewModel:** estados de `design/quiz-speaking.md` C como máquina de estados probada con el motor falso: los 13 estados, las reglas de intento, *Type it instead*, *Show answer*, cada causa de cierre del micrófono, y que el juez y la explicación esperan.
6. **Pantalla:** casilla del formulario, `SpeakingBlock`, animaciones (D), resultados con el icono `mic` (F), accesibilidad (G), capturas (H). Voz *Listen* de la respuesta solo si `KoreanVoice` (fase 8) existe y está *Ready*; si la fase 8 no está, sin botón.
7. **OPPO:** 📱 en la app QA, con el dueño hablando: un quiz de 10 con *Say it in Korean*, en modo avión; denegar el permiso y ver el estado; ir a inicio mientras escucha (el punto verde del sistema se apaga); matar el proceso a mitad. Informe con ✅/⚠️/❌. `versionName` 0.11.0; instalar en la app del dueño solo con su OK.

## 8 · Condiciones de parada

- ningún motor pasa el criterio de la PoC;
- el motor solo funciona con red, o el manifiesto unido gana `INTERNET` u otro permiso;
- una dorada cambia o cambia una tabla del respaldo;
- hace falta guardar audio;
- algo pide aceptar un diálogo o cambiar un ajuste del teléfono: lo hace el dueño.

## 9 · Riesgos

| Riesgo | Cómo se controla |
|---|---|
| ColorOS no trae reconocimiento coreano sin red | PoC antes de escribir nada del producto; opción C solo con aprobación |
| El reconocedor «corrige» una mala pronunciación y da por buena la palabra | Es una limitación del método: la app lo dice en la propia pregunta y no promete evaluar la pronunciación |
| El reconocedor falla con palabras sueltas cortas | respuestas de 2 bloques o más; *Uncertain* no penaliza; *Type it instead* siempre a mano |
| Cifras y préstamos escritos de otra forma (3 por 세) | *Uncertain*, nunca incorrecto |
| El micrófono se queda abierto | un solo punto de cierre (cancelar la corrutina) y una prueba por cada causa |
| API alpha que cambia (opción A) | detrás del contrato `KoreanSpeech`; versión fijada en el catálogo |
