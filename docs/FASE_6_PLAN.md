# Fase 6 — IA en el teléfono (Gemini Nano): plan de implementación

> **Para Claude Code:** este documento es el plan de la fase entera. Una rama (`fase-6/ai`), un commit por tarea y **un PR al final**, sin mergear sin la confirmación del dueño. Sub-skills: `superpowers:executing-plans`, `test-driven-development` y la skill `ml-kit-genai-prompt-api` del repo.
>
> 📱 **Teléfono:** no hace falta en la parte JVM de cada tarea (modelo falso); **sí** para la comprobación final de cada tarea y para toda la tarea 7. Nano solo corre en el OPPO.

**Objetivo:** los 8 usos de `docs/PLAN.md` 10.1, más las preguntas que escribe el modelo (*Multiple choice* y *Short answer* con dificultad) y *Fill in the blank* por reglas (R-017). Todo pasa por una sola puerta (R-013). Si Nano no está, la app queda exactamente como al cerrar la fase 5.

**Base:** `docs/PLAN.md` 10 y 10.1; `docs/RECOMMENDATIONS.md` R-013 y R-017; `docs/DECISIONS.md` (Gemini Nano 2026-11-08 y las respuestas del 2026-11-11); `docs/poc/2-nano.md`; los diseños `design/shell.md` (AiMark), `teacher.md`, `quiz.md` (A.1 y A.2), `library.md` (E), `grammar-calendar-settings.md` (Grammar y On-device AI) y `dashboard.md`.

## Restricciones globales

- **Primero tus notas.** El modelo solo entra cuando las reglas no tienen respuesta, o para redactar en inglés hechos que ya calcularon las reglas.
- **Lo que crea el modelo nunca se guarda como tuyo:**
  - no entra en tus notas;
  - no entra en tus mazos sin preguntarte;
  - no cuenta como fuente;
  - no va a ninguna tabla del respaldo.
- **Caché del modelo solo en memoria** (respuesta 3).
- **Siempre se valida antes de mostrar** (R-013, punto 2): sin texto en streaming sin validar. Si un validador rechaza la salida, se muestra la versión sin modelo, sin aviso de error.
- **Las reglas siguen siendo reglas:** `isCorrect`, `compareAnswer`, SM-2, los constructores de quiz, la intención y las cifras del Coach, la conjugación y todo el coreano que se muestra. El modelo solo redacta en inglés y aprueba sinónimos en inglés.
- **Restricciones de Nano:**
  - nunca con un PDF procesándose;
  - solo con la app en primer plano;
  - 2 s entre llamadas;
  - un reintento con `BUSY`;
  - prompt de sistema *"You are an English-speaking tutor. Write your whole answer in English"*.
- **Permisos y privacidad:** sin `INTERNET` ni permisos nuevos (el manifiesto unido se revisa en cada tarea). Logs sin preguntas, respuestas, prompts ni texto de las notas: solo uso, tiempo y resultado del validador.
- **En el OPPO:**
  - no respondas quizzes, no califiques tarjetas, no crees mazos en la app real;
  - lo que escriba datos va sobre una copia de la base, que se restaura después;
  - comprueba que la app está delante antes de cada toque;
  - no cambies ajustes del teléfono;
  - la descarga del modelo la inicio yo con el botón *Download* (Settings → On-device AI). Si no está descargado, PARA y pídemelo.

## Riesgos que hay que probar

1. **Coreano inventado:** ninguna salida con una palabra coreana fuera de su entrada llega a la UI (todas las tareas, prueba por uso).
2. **Nano no disponible:**
   - en `Unavailable`, `Downloading` y `Downloadable`, cada pantalla es idéntica a la de la fase 5 (capturas sin cambios);
   - con un error de AICore a mitad de la respuesta, la app no se cae.
3. **Lentitud o `BUSY`:** el plazo vence → versión sin modelo. Nada se queda en *Thinking…* para siempre.
4. **Segundo plano:**
   - salir de la app con una llamada en curso: no se cae; al volver, la versión sin modelo o un reintento;
   - las notas desordenadas se pausan y siguen al volver (tarea 5).
5. **El modelo como fuente o como dato tuyo:** tras usar los 8 usos, la exportación del respaldo es idéntica a la de antes, salvo las tablas que tú cambiaste a propósito (tarea 7).

---

## Tarea 1 — `:core:ai`, puerta única y estado de Nano (R-013)

📱 JVM primero; al final, sí.

- **Módulo nuevo `:core:ai`:**
  - `NanoGate` (`@Singleton`): `Mutex`, 2 s entre llamadas, un reintento con `BUSY` y rechazo inmediato si hay PDFs en cola, si la app no está en primer plano o si el estado no es `AVAILABLE`;
  - plazo por uso con `withTimeoutOrNull` (Coach 3 s, juez 2 s, explicación 6 s, o lo que dé la PoC 2 más margen);
  - prefijo en caché y `countTokens`, solo si existen en `genai-prompt` 1.0.0-beta5; si no, se anota en DECISIONS y se sigue sin ellos.
- **Un archivo por uso** con su prompt en inglés versionado (`PROMPT_VERSION`) y su validador puro en `:core:korean`. Los validadores comparten `koreanOnlyFrom(input)` y `citationsValid`.
- **`AiCache`:** en memoria, con clave `(versión del prompt, hash de la entrada)`.
- **`NanoStatus`:** `Available`, `Downloadable`, `Downloading(%)`, `Unavailable`, expuesto como `StateFlow`.
- **Settings → On-device AI** según `design/grammar-calendar-settings.md`: la descarga solo empieza con *Download*.
- **AiMark** según `design/shell.md`.
- **Pruebas:** las 4 de aceptación de R-013 con un modelo falso, más la de Settings (los 4 estados en capturas Roborazzi). Ninguna pantalla importa ML Kit: lo comprueba una prueba de dependencias o un `grep` en el CI.
- **En el OPPO:** repetir las tandas de la PoC 2 a través de la puerta y obtener 0 `BUSY` tras el reintento. Informe: los p50 y p90 por tanda.

## Tarea 2 — Quiz y Flashcards: juez, explicación del error y preguntas del modelo

📱 JVM primero; al final, sí.

- **Usos 1 y 3 (juez de significado):**
  - solo cuando `isCorrect` o `compareAnswer` dicen "incorrecto" y la respuesta es inglés;
  - salida sí/no con una razón corta;
  - solo puede **aprobar**;
  - se muestra *accepted: same meaning* con AiMark.
- **Uso 2 (explicar tu error):** una frase con tu punto de gramática y las dos respuestas; el coreano solo de esas tres.
- **Preguntas del modelo (*Multiple choice* y *Short answer*):**
  - se usa la dificultad, como en la web;
  - se validan con los mismos constructores (`buildMultipleChoice`, …) que ya tienen doradas;
  - sin Nano, el formulario sigue como `design/quiz.md` A.1.
- **R-017, *Fill in the blank* por reglas:**
  - `buildBlankFromExample` según R-017;
  - las 826 doradas y `QuizGoldenTest` siguen en verde con peticiones sin `fill_blank`;
  - con `fill_blank`, toda frase aparece literal en el documento;
  - *Fill in the blank* queda habilitado siempre (`design/quiz.md` A.2).
- **Pruebas:**
  - una por uso con un modelo falso: aprueba, rechaza, coreano inventado → sin modelo, plazo vencido → sin modelo;
  - las capturas de quiz con y sin Nano.
- **Parar si:** el juez aprueba alguna respuesta en coreano, o una dorada del quiz cambia.

## Tarea 3 — Teacher sin respuesta en las reglas (uso 4)

📱 JVM primero; al final, sí.

- Cuando las reglas dan *Your notes don't cover this*, se usan como mucho 4 fragmentos de `NotesSearch` y una explicación en inglés con citas.
- **Validador:**
  - citas a fragmentos que existen;
  - el coreano solo de los fragmentos citados.
- **Sin fragmentos:** responde el modelo con AiMark y sin fuentes.
- **Sin modelo:** los mensajes de la web, como hoy.
- **`selfcheck` (tarea 4 de la fase 5) sigue en OK:** las preguntas que responden las reglas no cambian de ruta.

## Tarea 4 — Coach redactado (uso 5)

📱 JVM primero; al final, sí.

- El modelo solo recibe los hechos que calcula `CoachEngine` (temas débiles, tarjetas pendientes, progreso, propuestas) y redacta el texto.
- **Validador:** ningún número, palabra coreana ni tema que no esté en los hechos.
- **Sin modelo:** la plantilla `_forced_reply` de la fase 5.
- Las propuestas *Start quiz* y *Create deck* no cambian: las sigue haciendo el Coach por reglas.

## Tarea 5 — Library: notas desordenadas (uso 6)

📱 JVM primero; al final, sí.

- **Etapa opcional de la cola de procesado de la fase 4. Sin WorkManager**: DECISIONS 2026-11-09 corrige `docs/PLAN.md` 10.1.
- Solo con la app en primer plano: si sales, la etapa se pausa y la Card dice el texto de `design/library.md` E; al volver, sigue.
- **Validador:** `validate_llm_pair` portado, el coreano tiene que estar en ese fragmento. Doradas del web si existen; si no, casos propios.
- Los pares del modelo llevan AiMark en *Words detected* y no crean tarjetas sin que tú lo aceptes.
- Con tus 5 PDFs, hoy no hay fragmentos `needs_llm_pairs` (DECISIONS 2026-11-11): se prueba con un PDF sintético del repo en una copia de la base.

## Tarea 6 — Grammar y Dashboard (usos 7 y 8)

📱 JVM primero; al final, sí.

- **Grammar (7):**
  - (a) explicación más sencilla, con el coreano solo del punto;
  - (b) frase de práctica: cada palabra tiene que estar en tus notas (índice de notas + conjugación); va con la etiqueta *PRACTICE* y AiMark, y nunca se guarda.
- **Dashboard (8):** una o dos frases a partir de las cifras de la semana, sin números nuevos. Sin modelo: una frase con plantilla a partir de las mismas cifras.
- **Pruebas:** una por uso con un modelo falso, y capturas con y sin Nano.

## Tarea 7 — En el OPPO: metas, modo avión y respaldo

📱 Sí.

1. Exporta mi respaldo a la carpeta privada de la app y trabaja sobre una copia de la base.
2. Con Nano `Available`, en **modo avión** (lo pongo yo si hace falta: pídemelo), prueba cada uno de los 8 usos al menos una vez.
3. **Mide:**
   - p50 y p90 por uso;
   - cuántas salidas rechazó cada validador.
   Informe sin texto.
4. Sal de la app con una llamada en curso: no se cae. Prueba lo mismo con un PDF en cola: la puerta rechaza la llamada.
5. **Restaura** y comprueba 18 de 18 tablas, las mismas filas y los mismos sha256 que antes del punto 1.
6. Revisa que el manifiesto unido sigue sin `INTERNET`, y ejecuta las skills de permisos y de intents.

**Parar si:** aparece coreano inventado en la UI, `INTERNET` en el manifiesto, una tabla del respaldo con salida del modelo, o la restauración no cuadra.

## Al terminar la fase

- [ ] `DECISIONS.md`:
  - lo que exista o no en beta5 (salida estructurada, prefijo, `countTokens`);
  - los plazos finales;
  - las medidas por uso;
  - cómo quedó R-017.
- [ ] `RECOMMENDATIONS.md`: R-013 y R-017 en la tabla "Estado".
- [ ] `mem_save` con `topic_key` `android/fase-6`.
- [ ] PR con la tabla: uso, ¿pasa por la puerta?, validador, pruebas, p50 y p90 en el OPPO, rechazos. Y ✅/⚠️/❌ por tarea.
