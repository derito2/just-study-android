# Ficha: practicar hablando en el Quiz (Fase 9)

Base: `design/quiz.md` (secciones A y C) y `design/shell.md`. Arquitectura y tareas: `docs/FASE_9_PLAN.md`.

**Cambio tras la prueba de concepto (2026-11-14, `docs/poc/4-speech.md`):** el reconocedor del OPPO acierta un 70–77 %, así que **no califica**. Este tipo de pregunta es **práctica**:
- dices la respuesta;
- la app enseña lo que entendió el teléfono junto a la respuesta de tus notas;
- no cuenta como acierto ni fallo y no se guarda en el historial.

**No es una pantalla nueva:** es un tipo de pregunta del Quiz, con la misma Card, la misma barra y el mismo pie.

**Lo que hace, dicho con honestidad:** el teléfono convierte tu voz en texto coreano. Ese texto puede fallar aunque lo hayas dicho bien. La app nunca dice que pronunciaste mal, y ningún texto habla de "pronunciation", porcentajes ni estrellas.

## A · En el formulario (New quiz → Question types)

Novena casilla, al final de la rejilla (queda sola en la quinta fila, a la izquierda):

- **Say it in Korean** · 한국어로 말하기 · pista *Practice out loud · not scored*
- Sin marcar por defecto.
- **Si el reconocedor no está disponible:**
  - como las casillas de A.1 de `quiz.md`: fila al 50 %, sin marcar y sin responder al toque;
  - la pista se sustituye por **Needs offline Korean speech** (12 sp, `muted`);
  - TalkBack: "Say it in Korean, needs offline Korean speech, unavailable".
- **Si solo está marcada esta casilla**, el quiz es solo de práctica: los resultados no tienen puntuación (F).

## B · La pregunta

Misma Card de `quiz.md` C; cambia solo el bloque de respuesta.

- **Fila superior:** *Say it in Korean* · 한국어로 말하기 con la etiqueta **PRACTICE** (`badge-tint`, MAYÚSCULAS, como la frase de práctica de Grammar), y el documento a la derecha.
- **La pregunta** (dos variantes):
  - **Di la palabra:** el significado en inglés (20 sp, 700). Pista: *Say it out loud, in Korean.*
  - **Lee en voz alta** (solo frases de 2 palabras o más): la frase coreana (`ko-prompt`) y debajo su traducción (`body`, `muted`). Pista: *Read it out loud.*
- **Nunca se pide hablar inglés ni español.**
- **Bloque de voz**, centrado, con 16 dp de aire arriba y abajo:
  - **MicButton:** círculo de **72 dp**, fondo `action`, canto inferior de 4 dp `action-edge` e icono `mic` blanco de 28 dp. Zona táctil: el círculo entero.
  - **Línea de estado** debajo (14 sp, 700; ver C).
  - **Nota fija** al pie del bloque (12 sp, `muted`): *Practice only. Your phone's speech recognition can mishear you, so this isn't scored.*
- **Comparación**, después del primer intento (recuadro `canvas`, radio 12, relleno de 12 × 16 dp):
  - **We heard** (12 sp, `muted`) y la transcripción (`ko-prompt`);
  - **Your notes** (12 sp, `muted`) y la respuesta (`ko-prompt`);
  - **Diferencias:** en las dos líneas, las sílabas que no coinciden van subrayadas con 2 dp `warn` (alineación por sílaba). Sin rojo y sin `x`: no es un error.
  - **Si coincide exactamente** (normalizado: sin espacios ni signos), arriba del recuadro sale **Matches your notes** con `check` (14 sp, 700, `correct`). Es lo único que se celebra; si no coincide, no se dice nada negativo;
  - **Listen** · 듣기 (Gray, Small, `volume-2`) lee la respuesta de tus notas con la voz coreana sin red de la fase 8. Si no hay voz, el botón no existe.
- **Debajo del recuadro**, dos acciones de texto (14 sp, 700, zona táctil de 48 dp):
  - **Try again** (`brand-text`): vuelve a *Ready* sin borrar el recuadro;
  - **Show answer** (`muted`), solo antes del primer intento: muestra *Your notes* sin hablar.
- **Pie:** **Next question** / **See results**, activo desde el primer intento o desde *Show answer*. Intentos ilimitados.

## C · Estados

| Estado | MicButton | Línea de estado | Qué puede hacer el usuario |
|---|---|---|---|
| **Ready** | `action`, quieto | **Tap and speak** · 누르고 말하세요 (`ink`) | tocar el micrófono |
| **Listening** | `danger` con `mic` blanco; halo y onda (D) | **Listening…** · 듣고 있어요 (`danger`) | tocar otra vez = terminar ya; **Cancel** (texto `muted`, a la derecha del botón) |
| **Processing** | `action` al 60 %, anillo girando (D) | **Checking…** · 확인 중 (`muted`) | nada (como mucho 4 s; después, *No speech*) |
| **Compared** | `action`, quieto | **Compare below** (`ink`), o nada si coincide (sale *Matches your notes*) | *Try again*, *Next question* |
| **No speech** | `action`, quieto | **Didn't catch that. Tap and try again** · 못 들었어요 (`ink`) | tocar el micrófono |
| **Cancelled** | vuelve a *Ready* | **Tap and speak** | — |
| **Model not downloaded** | `disabled-fill` | **Korean speech isn't downloaded on this phone** (`muted`) + *Download it in Android's speech settings, then come back.* (12 sp) | *Next question* |
| **Unavailable / error** | `disabled-fill` | **Speech isn't working right now** (`muted`) | **Try again** (Gray, Small) y *Next question* |
| **Permission needed** (primera vez) | `action` | Antes del diálogo del sistema: *The microphone is used only while you answer. Nothing is recorded or sent.* (12 sp, `muted`) | tocar el micrófono abre el diálogo de Android |
| **Permission denied** | `disabled-fill` con `mic` tachado | **Microphone is off for this app** (`muted`) + *Turn it on in Android settings to practice out loud.* (12 sp) | *Next question* |

## D · Animaciones

Todas en fase de dibujo. Con *Quitar animaciones*, solo cambian colores, iconos y textos.

- **Ready → Listening** (180 ms): el botón pasa de `action` a `danger` con un fundido de color y hace un `pop` (escala 1 → 1.08 → 1, 240 ms, `Spring`).
- **Listening:**
  - **Halo:** dos anillos de 2 dp `danger`, de radio 36 → 64 dp y opacidad 0.5 → 0, en 1200 ms cada uno, desfasados 600 ms y en bucle.
  - **LevelWave:**
    - 4 barras a cada lado del botón (4 dp de ancho, 4 dp de separación, radio 2, `danger`), de 6 dp a 36 dp de alto;
    - el alto sigue el nivel real del micrófono (`onRmsChanged`), con un muelle de rigidez 300 y 40 ms de retardo por barra hacia fuera;
    - sin nivel: el patrón de `VoiceBars`.
  - **Parciales:** si llegan, se ven bajo la línea de estado en `ko-prompt` `muted`, con un fundido cruzado de 90 ms.
- **Listening → Processing** (150 ms): las barras bajan a 6 dp y el botón queda al 60 %, con un arco de 270° (3 dp, `brand`, radio 40 dp) que da una vuelta cada 900 ms.
- **→ Compared:**
  - el arco desaparece (150 ms);
  - el recuadro entra con opacidad 0 → 1 y desplazamiento de 8 dp → 0 (240 ms, ease-out);
  - los subrayados se dibujan de izquierda a derecha en 240 ms;
  - si coincide: *Matches your notes* con `pop` (1 → 1.12 → 1, 240 ms).
  - **Sin `shake` nunca:** no hay fallos.
- **→ No speech:** latido suave del botón (1 → 1.04 → 1, 400 ms).
- **Cancel:** todo vuelve a *Ready* en 150 ms.

## E · Comportamiento

- **El micrófono solo está abierto en *Listening*.** Se cierra cuando:
  - terminas de hablar (silencio) o tocas el botón;
  - pulsas *Cancel*;
  - pasas de pregunta, sales del quiz o abres *Leave quiz*;
  - la app va a segundo plano o se apaga la pantalla;
  - se pierde el foco de audio.
- **Tope de escucha:** 8 s para palabras y 12 s para frases.
- **Permiso:** se pide solo al tocar el micrófono por primera vez.
- **No se guarda audio, ni temporal.** No se guarda la transcripción ni un resultado: la pregunta hablada no escribe en `question_attempts`.
- Android muestra su punto verde de micrófono; la app no lo tapa.
- **Sin vibración.** **Sin modelo:** el juez y la explicación del error no se usan en estas preguntas.
- **Si matan el proceso a mitad:** al volver, esa pregunta se salta (es práctica). No se pierde nada guardado.

## F · En resultados e historial

- **La puntuación del quiz** solo cuenta las preguntas normales.
- **Debajo de la puntuación:** *{k} spoken practice, not scored.* (12 sp, `muted`, con un `mic` de 14 dp).
- **Quiz solo de práctica:** en lugar de la puntuación, *Spoken practice · {k} questions* y la lista de preguntas con *Your notes* y *We heard* (la última). No aparece en *Quiz history*.
- **En la lista de resultados**, las preguntas habladas llevan PRACTICE y no tienen `check` ni `x`.

## G · Accesibilidad

- MicButton: `Role.Button`, "Practice out loud" / "Stop listening".
- La línea de estado es `liveRegion = Polite`. Al llegar la comparación se anuncia: "We heard … Your notes …" y, si coincide, "matches your notes".
- **Con TalkBack activo**, se esperan 600 ms antes de abrir el micrófono.
- Siempre se puede seguir con *Next question*: nadie se queda bloqueado.
- Con la letra al 130 %, el bloque no cambia de tamaño; los textos bajan de línea.

## H · Capturas de prueba (Roborazzi)

En claro y oscuro, con `LocalFrozenMotion`:
- *Ready*;
- *Listening* (halo en su primer fotograma, barras a media altura);
- *Compared* con diferencias;
- *Compared* con *Matches your notes*;
- *Permission denied*;
- los resultados con *{k} spoken practice*;
- el quiz solo de práctica;
- la casilla del formulario activa y deshabilitada.
