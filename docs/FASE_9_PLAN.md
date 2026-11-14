# Fase 9 — Responder hablando en coreano dentro del Quiz: arquitectura y plan

> **Para Claude Code:** primero la **prueba de concepto** (tarea 1), en su propia rama, que **no se mergea**. Con su informe, **para**: el dueño elige el motor. Solo entonces siguen las tareas 2–7 (rama `fase-9/speaking`, un commit por tarea, un PR, sin mergear sin confirmación). Empieza cada tarea por sus pruebas.
>
> **Diseño:** `design/quiz-speaking.md`. No es una pantalla nueva: es un tipo de pregunta del Quiz.
>
> 📱 **Teléfono:** sí en las tareas 1 y 7. **El dueño habla; Code no puede.** El dueño acepta él mismo el diálogo del micrófono y cualquier descarga del sistema.
>
> **Independiente de la fase 8.** Solo comparten `Hangul.jamoDistance` (fase 8, tarea 1); si la fase 9 va antes, esa función se hace aquí.

> **Cambio tras la prueba de concepto (2026-11-14, `docs/poc/4-speech.md`):** ningún motor llega al 85 % (B: 70 % exacto y 77 % con umbral de CER; A: 57 %). Decisión del dueño: **práctica hablada sin calificar** con el motor **B** (`SpeechRecognizer` de Android, sin dependencias). El motor C no se hace. Esta versión sustituye a las secciones 0 (D9-3 y D9-6), 4, 5 y 7 originales. El diseño es `design/quiz-speaking.md`, reescrito.

**Origen:** `JustStudyKorean_Games_y_Quiz_Oral_Especificacion.md` (el encargo del dueño, partes II y III).

## 0 · Decisiones del dueño

| ID | Pregunta | Propuesta | Por qué |
|---|---|---|---|
| D9-1 | **Permiso `RECORD_AUDIO`.** Hoy está **prohibido** (`CLAUDE.md`, *Seguridad*; `tools/ci/check_manifest.py`, lista `FORBIDDEN`) | Permitirlo solo para este tipo de pregunta, pedido al tocar el micrófono | Sin él no hay voz. Es una decisión de privacidad del dueño: hace falta su sí explícito, anotado en DECISIONS. |
| D9-2 | ¿Cómo se guarda una pregunta hablada? | Como una pregunta **`write_ko`** normal; «hablada» es solo la forma de responderla en ese quiz | El tipo `write_ko` ya existe en la web y en el respaldo. Un tipo nuevo en la tabla `questions` llegaría a la web por el respaldo sin que ella lo conozca. Así no cambia ni una tabla ni una dorada. |
| D9-3 | Motor de reconocimiento | **Decidido: B** (2026-11-14). Antes: el que ganara la prueba de concepto. Orden de prueba: **B** (plataforma, sin dependencia) → **A** (ML Kit, alpha) → **C** (modelo propio) solo si los otros fallan | Ver la sección 1. |
| D9-4 | Si el motor elegido es ML Kit: dependencia `com.google.mlkit:genai-speech-recognition` (alpha) | Solo si B no sirve y A sí | Alpha: puede cambiar sin aviso. |
| D9-5 | Si el motor elegido es un modelo propio (opción C): librería nativa nueva en la APK y un archivo de modelo de decenas o cientos de MB que **el dueño carga a mano** (la app no tiene `INTERNET`) | Solo si A y B fallan, y tras ver tamaños y medidas | Es el coste mayor: NDK, tamaño, y un paso manual de carga del modelo. |
| D9-6 | Intentos | **Ilimitados y sin calificar** (2026-11-14): la pregunta hablada es práctica | Encargo, punto 16. |

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
  korean/speech/SpokenCompare.kt     normalizar y comparar: (esperada, candidatos) → SpokenComparison (sin veredicto)

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

## 4 · Comparación (`SpokenCompare.kt`), sin veredicto

**Normalizar** las dos partes con NFC, sin espacios y sin `. , ! ? ~ … " '`.

`compare(expected, candidates) → SpokenComparison(heard, matches: Boolean, diff: List<SyllableSpan>)`:
- `heard`: el **primer** candidato, tal cual (sin normalizar, para mostrarlo).
- `matches`: el primer candidato normalizado es igual a la respuesta o a una de sus alternativas (`KOREAN_ALTERNATIVES`, « / »). Es la única señal positiva y no tiene falsos aciertos en la PoC.
- `diff`: alineación de Levenshtein por sílaba entre `heard` y la respuesta más cercana, para subrayar lo que no coincide (diseño, B).
- **Nada devuelve "incorrecto"**: no hay `Mismatch`, ni `Uncertain`, ni umbral de CER, ni intentos.

**Pruebas (`SpokenCompareTest`):**
- igual con y sin espacios, y con signos;
- alternativas « / »;
- NFD;
- partícula distinta (`학교에` frente a `학교를` → `matches = false` con una sílaba subrayada);
- cifras y latín (`matches = false`, sin fallo);
- candidatos vacíos → no se llama (es *No speech*).

## 5 · Integración con el Quiz

- **Formulario:**
  - tipo `speak_ko` (*Say it in Korean* · 한국어로 말하기 · *Practice out loud · not scored*) en `QUIZ_TYPES`, guardado en `UserPrefs` y sin marcar por defecto;
  - deshabilitado si `availability()` no es `Ready` ni `NeedsDownload`.
- **Al generar:**
  - `speak_ko` **no** va al generador. Si está marcado, la petición lleva `write_ko`;
  - de las `write_ko` cuya respuesta sea solo hangul y tenga al menos 2 bloques: si solo está marcado `speak_ko`, todas son habladas; si están marcados los dos, alternan (la primera, hablada);
  - los ids hablados van al `SavedStateHandle`.
- **No se guarda nada de las habladas:**
  - sin `QuizRepository.answer`, así que no entran en `question_attempts`, ni en las debilidades, ni en el tiempo de respuesta;
  - la pregunta sí existe en `questions`, porque la crea el generador como siempre;
  - la puntuación y *Quiz history* solo cuentan las normales;
  - un quiz solo hablado no se lista en *Quiz history* (diseño, F).
- **Sin cambios en `QuizGenerator`, `QuizBuilders` ni las tablas.** Ninguna dorada cambia.
- **Tras matar el proceso:** las habladas se saltan al volver.
- **Preguntas del modelo:** nunca son habladas. El juez y la explicación no se usan en las habladas.
- **Listen:** usa `KoreanVoice` de la fase 8 si está *Ready*.

## 6 · Permiso, privacidad y ciclo de vida

- **Manifiesto:** `RECORD_AUDIO` y, para B, `<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>`.
- **`check_manifest.py`:** `RECORD_AUDIO` pasa de `FORBIDDEN` a `ALLOWED_PERMISSIONS`, con su comentario y la fecha de la decisión; su prueba se actualiza. `CLAUDE.md` (*Seguridad*) se actualiza en el mismo commit.
- **Petición:** `ActivityResultContracts.RequestPermission`, solo al tocar el micrófono. Antes, la línea de explicación del diseño. Denegado dos veces (o «no volver a preguntar») → estado *Permission denied*; la app no abre los ajustes del sistema.
- **El micrófono se cierra** (cancelando la corrutina de `listen`) al: resolver, cancelar, pasar de pregunta, abrir *Leave quiz*, salir, `ON_STOP`, o perder el foco de audio. Prueba para cada caso con el motor falso.
- **Nunca** se escribe audio a disco. **Nunca** va la transcripción a los logs (solo veredicto, intento, tiempos y código de error).
- Skills antes del PR: `android-permissions-security` y `android-intent-security` sobre el manifiesto unido.

## 7 · Tareas

1. ~~PoC~~ **Hecha** (`docs/poc/4-speech.md`).
2. **Decisiones:**
   - D9-1 (sí a `RECORD_AUDIO`, con la fecha), D9-2, D9-3 = B y D9-6 = sin calificar, en DECISIONS;
   - manifiesto (`RECORD_AUDIO` + `<queries>` de `RecognitionService`), `check_manifest.py` y `CLAUDE.md` (*Seguridad*).
3. **Comparación:** `SpokenCompare.kt` con sus pruebas (sección 4).
4. **Motor:**
   - `KoreanSpeech` + `PlatformKoreanSpeech` (B) + `FakeKoreanSpeech`;
   - prueba instrumentada de disponibilidad, que se salta si no hay motor.
5. **ViewModel:** los estados de `design/quiz-speaking.md` C como máquina de estados, probada con el motor falso. Pruebas:
   - *Try again* ilimitado y *Show answer*;
   - cada causa de cierre del micrófono;
   - que ninguna pregunta hablada llama a `answer`;
   - que la puntuación excluye las habladas.
6. **Pantalla:**
   - casilla del formulario y `SpeakingBlock` con el recuadro de comparación y los subrayados;
   - animaciones (D), resultados (F), accesibilidad (G) y capturas (H);
   - *Listen* con `KoreanVoice`.
7. **OPPO:** 📱 en la app QA, con el dueño hablando:
   - un quiz de 10 con *Say it in Korean*, en modo avión (lo activa el dueño);
   - denegar el permiso y ver el estado;
   - ir a inicio mientras escucha (el punto verde se apaga);
   - matar el proceso a mitad.
   Informe con ✅/⚠️/❌. `versionName` 0.11.0; instalar en la app del dueño solo con su OK, con `install -r`.

## 8 · Condiciones de parada

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
