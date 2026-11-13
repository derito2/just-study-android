# Ficha: responder hablando en el Quiz (Fase 9)

Base: `design/quiz.md` (secciones A y C) y `design/shell.md`. Arquitectura, prueba de concepto y tareas: `docs/FASE_9_PLAN.md`.

**No es una pantalla nueva.** Es un tipo de pregunta más del Quiz: misma Card, misma barra, mismo pie, mismos resultados.

**Lo que evalúa, dicho con honestidad:** el teléfono convierte tu voz en texto coreano y la app compara ese texto con la respuesta de tus notas. Comprueba **qué palabras reconoció**, no **cómo las pronunciaste**. Ningún texto de la app dice «pronunciation», ni hay porcentajes ni estrellas de pronunciación.

**Depende de la prueba de concepto** (`docs/FASE_9_PLAN.md`, tarea 1): si en el OPPO no hay un reconocedor coreano que funcione sin red, este tipo queda deshabilitado (estado *Unavailable*) y no se construye nada más.

## A · En el formulario (New quiz → Question types)

Novena casilla, al final de la rejilla (queda sola en la quinta fila, a la izquierda):

- **Say it in Korean** · 한국어로 말하기 · pista *Answer out loud*
- Sin marcar por defecto.
- **Si el reconocedor no está disponible:** como las casillas de A.1 de `quiz.md` (fila al 50 %, sin marcar, sin responder al toque), con la pista sustituida por **Needs offline Korean speech** (12 sp, `muted`). TalkBack: "Say it in Korean, needs offline Korean speech, unavailable".
- **Si el micrófono está denegado para siempre:** la casilla sigue activa; el aviso sale en la pregunta (estado *Permission denied*).

## B · La pregunta

Misma Card de `quiz.md` C. Cambia solo el bloque de respuesta.

- **Fila superior:** *Say it in Korean* · 한국어로 말하기, y el documento a la derecha.
- **La pregunta** (dos variantes, las elige el generador):
  - **Di la palabra:** el significado en inglés (20 sp, 700). Pista: *Answer out loud, in Korean.*
  - **Lee en voz alta** (solo frases de 2 palabras o más): la frase coreana (`ko-prompt`) y debajo su traducción (`body`, `muted`). Pista: *Read it out loud.*
- **Nunca se pide hablar inglés ni español.**
- **Bloque de voz**, centrado, con 16 dp de aire arriba y abajo:
  - **MicButton:** círculo de **72 dp**, fondo `action`, canto inferior de 4 dp `action-edge` (el lenguaje de StickerButton), icono `mic` blanco de 28 dp. Zona táctil: el círculo entero.
  - **Línea de estado** debajo (14 sp, 700; ver tabla).
  - **Transcripción** debajo (cuando la hay): etiqueta *We heard:* (12 sp, `muted`) y el texto (`ko-prompt`).
  - **Nota fija** al pie del bloque (12 sp, `muted`): *Checks the words your phone recognized, not your pronunciation.*
- **Enlace de salida**, bajo la nota: **Type it instead** (texto `brand-text`, 14 sp, 700, zona táctil de 48 dp). Convierte **esta** pregunta en *Write in Korean* (campo de texto y **Check**), sin penalización. Siempre visible mientras la pregunta no esté resuelta.
- **Pie:** vacío hasta que la pregunta se resuelve; entonces **Next question** / **See results**, como siempre.

## C · Estados

| Estado | MicButton | Línea de estado | Qué puede hacer el usuario |
|---|---|---|---|
| **Ready** | `action`, quieto | **Tap and speak** · 누르고 말하세요 (`ink`) | tocar el micrófono |
| **Listening** | `danger` con icono `mic` blanco; halo y onda (D) | **Listening…** · 듣고 있어요 (`danger`) | tocar otra vez = terminar ya; **Cancel** (texto `muted`, a la derecha del botón) |
| **Processing** | `action` al 60 %, anillo girando (D) | **Checking…** · 확인 중 (`muted`) | nada (como mucho 4 s; después, *Didn't catch that*) |
| **Correct** | `go` con `check` | (no se ve; lo sustituye el recuadro de resultado) | *Next question* |
| **Try again** (no coincidió, quedan intentos) | `action`, quieto | **Not a match. Try again** · 다시 말해 보세요 (`ink`) + *{k} tries left* (12 sp, `muted`) | tocar el micrófono, o **Show answer** |
| **Incorrect** | `disabled-fill` con `mic` `disabled-ink` | (lo sustituye el recuadro) | *Next question* |
| **Uncertain** (se oyó algo que no es coreano claro, o casi coincide) | `action`, quieto | **Not sure we heard you right** · 잘 못 들었어요 (`warn`) | tocar el micrófono; no gasta intento |
| **No speech** | `action`, quieto | **Didn't catch that. Tap and try again** · 못 들었어요 (`ink`) | tocar el micrófono; no gasta intento |
| **Cancelled** | vuelve a *Ready* | **Tap and speak** | — |
| **Model not downloaded** | `disabled-fill` | **Korean speech isn't downloaded on this phone** (`muted`) + *Download it in Android's speech settings, then come back.* (12 sp) | *Type it instead* |
| **Unavailable / error** | `disabled-fill` | **Speech isn't working right now** (`muted`) | **Try again** (Gray, Small) y *Type it instead* |
| **Permission needed** (primera vez) | `action` | antes de abrir el diálogo del sistema, una línea: *The microphone is used only while you answer. Nothing is recorded or sent.* (12 sp, `muted`) | tocar el micrófono abre el diálogo de Android |
| **Permission denied** | `disabled-fill` con `mic` tachado | **Microphone is off for this app** (`muted`) + *Turn it on in Android settings to answer by voice.* (12 sp) | *Type it instead* |

**Reglas de intento (para no castigar un fallo del reconocedor):**

- **Coincide** → *Correct*, al instante.
- **No coincide** → *Try again*; la transcripción se muestra (*We heard: …*). Hay **3 intentos**. Al tercero que no coincide → *Incorrect*.
- **Show answer** (texto `muted`, junto a *Type it instead*, solo desde el primer intento fallido) → *Incorrect* voluntario.
- **Uncertain, No speech, Cancelled, errores, permisos y modelo ausente nunca gastan intento ni cuentan como fallo.**
- Lo que se guarda de la pregunta es **un** resultado final (correcta o incorrecta) y, como respuesta, la última transcripción. Si se respondió con *Type it instead*, lo escrito.

**Recuadro de resultado** (el mismo de las respuestas escritas, `quiz.md` C):
- *Correct!* con `check`, y *We heard:* + la transcripción;
- *Not quite* con `x`, *We heard:* + la última transcripción (o *—* si fue *Show answer*) y *Correct answer:* + la respuesta.
- Debajo de *Correct answer*, si el teléfono tiene voz coreana sin red: botón pequeño `volume-2` (Gray, Small, **Listen** · 듣기) que la lee una vez. Si no hay voz, el botón no existe.

## D · Animaciones

Todas en fase de dibujo. Con *Quitar animaciones*: solo cambian colores, iconos y textos.

- **Ready → Listening** (180 ms): el botón pasa de `action` a `danger` (fundido de color), hace un `pop` (escala 1 → 1.08 → 1, 240 ms, curva `Spring`).
- **Listening:**
  - **Halo:** dos anillos de 2 dp `danger` salen del borde del botón: radio 36 → 64 dp, opacidad 0.5 → 0, 1200 ms cada uno, desfasados 600 ms, en bucle mientras se escucha.
  - **Onda de nivel (LevelWave):** a los dos lados del botón, 4 barras por lado (ancho 4 dp, separación 4 dp, radio 2, `danger`). Su alto sigue el **nivel real del micrófono** que da el reconocedor: de 6 dp (silencio) a 36 dp, con suavizado (cada barra persigue su objetivo con un muelle de rigidez 300) y un retardo de 40 ms por barra hacia fuera, para que la onda «viaje» desde el botón. Si el reconocedor elegido no da nivel, las barras usan el patrón fijo de `VoiceBars` (`games-minigames.md`, *Audio*).
  - **Transcripción parcial:** si el reconocedor da resultados parciales, se ven bajo la línea de estado en `ko-prompt` y `muted`, actualizándose (fundido cruzado de 90 ms).
- **Listening → Processing** (150 ms): los anillos en curso terminan; las barras bajan a 6 dp; el botón pasa a `action` al 60 % y alrededor gira un arco de 270°, 3 dp, `brand`, radio 40 dp, una vuelta cada 900 ms (lineal).
- **→ Correct:** el arco se cierra a 360° en `correct-deco` (150 ms); el botón pasa a `go` y el icono `mic` se funde a `check` (150 ms) con `pop` (1 → 1.12 → 1, 240 ms); la transcripción pasa de `muted` a `ink`. Luego entra el recuadro de resultado (opacidad 0 → 1, y: 8 dp → 0, 240 ms ease-out).
- **→ Try again:** el botón hace `shake` (±6 dp, 320 ms); la transcripción aparece en `wrong`; a los 400 ms el botón vuelve a `action`. El contador *{k} tries left* cambia con un fundido cruzado.
- **→ Uncertain / No speech:** sin `shake` (no es un error): la línea de estado cambia con fundido cruzado de 150 ms y el botón hace un latido suave (1 → 1.04 → 1, 400 ms) para invitar a repetir.
- **→ Incorrect:** el botón se apaga (a `disabled-fill`, 240 ms) y entra el recuadro de resultado.
- **Cancel:** todo vuelve a *Ready* en 150 ms.

## E · Comportamiento

- **El micrófono solo está abierto en *Listening*.** Se cierra al: terminar de hablar (silencio), tocar el botón, *Cancel*, pasar de pregunta, salir del quiz, abrir el diálogo *Leave quiz*, ir a segundo plano, apagar la pantalla o perder el foco de audio.
- **Tope de escucha:** 8 s para palabras, 12 s para frases. Al llegar, pasa a *Processing* con lo que haya.
- **Permiso:** se pide solo al tocar el micrófono por primera vez, nunca al abrir la app ni el Quiz.
- **No se guarda audio**, ni temporal: el reconocedor recibe el micrófono directamente y la app solo recibe texto.
- **Indicador del sistema:** Android muestra su punto verde de micrófono mientras se escucha; la app no lo tapa.
- **Sin vibración.**
- **Mientras se escucha no se llama al modelo** (ni juez ni explicación del error): esperan a que la pregunta se resuelva.
- **Si matan el proceso a mitad:** al volver, la pregunta se abre como *Write in Korean* (escrita). No se pierde nada guardado.

## F · En resultados e historial

- En la lista de resultados la pregunta aparece como las demás. *Your answer:* muestra la transcripción con un icono `mic` de 14 dp `muted` delante (TalkBack: "spoken answer").
- En *Quiz history* no cambia nada.

## G · Accesibilidad

- MicButton: `Role.Button`, "Answer by voice" / "Stop listening".
- La línea de estado es `liveRegion = Polite`. La transcripción se anuncia al llegar.
- **Con TalkBack activo**, al entrar en *Listening* se espera 600 ms antes de abrir el micrófono, para que la voz de TalkBack no entre en la grabación.
- *Type it instead* garantiza que nadie se queda bloqueado en una pregunta de voz.
- Letra al 130 %: el bloque de voz no cambia de tamaño; los textos bajan de línea.

## H · Capturas de prueba (Roborazzi)

En claro y oscuro, con `LocalFrozenMotion`: *Ready*, *Listening* (halo quieto en su primer fotograma, barras a media altura), *Try again* con transcripción, *Correct*, *Incorrect*, *Permission denied*, y la casilla del formulario activa y deshabilitada.
