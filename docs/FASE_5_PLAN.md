# Fase 5 — Teacher y Coach sin modelo: plan de implementación

> **Para Claude Code:** este documento es el plan de la fase entera. Se trabaja en **una rama** (`fase-5/teacher`), con un commit por tarea y **un PR al final**, sin mergear sin la confirmación del usuario. Sub-skills: `superpowers:executing-plans` y `test-driven-development`.
>
> 📱 **Teléfono:** no hace falta en las tareas 1 y 2 (JVM); sí en las tareas 3 y 4, por USB con la depuración activada. Se pide al usuario que lo desbloquee y no se hacen capturas salvo que sean imprescindibles.

**Objetivo:** Teacher responde por reglas igual que en la web y el Coach funciona dentro de Teacher sin modelo. El `selfcheck` de la web corre en el teléfono sobre tus notas y da OK. Las funciones con modelo y la marca AI son de la fase 6: aquí, si las reglas no tienen respuesta, salen los mensajes de la web (*Your notes don't cover this* y *Nothing close enough in your notes*).

**Arquitectura:**
- **`:core:korean`:** los respondedores de `rag/tutor.py` y `rag/entries.py`, como una lista ordenada de respondedores pequeños, cada uno con sus pruebas (plan, punto 7.2). También la intención del Coach (`agent/practice.py`: `intent`, `planned_tools` y los filtros de palabras) y sus textos deterministas (`agent/loop.py`: `_forced_reply`, `_proposal` y `drop_missing_buttons`).
- **`:core:data`:** la búsqueda sobre el FTS de la fase 4 (sustituye a Chroma), `NotesIndex` y las herramientas del Coach sobre los repositorios de la fase 3: temas débiles, progreso reciente, tarjetas pendientes, proponer quiz y crear mazo de práctica.
- **`:app`:** la pantalla Teacher según `design/teacher.md`, sin AiMark (fase 6).

**Base:** `docs/PLAN.md` (puntos 4, 7 y 10.1), `design/teacher.md`, `golden/teacher/*.jsonl`, `golden/index/` y `golden/db/`.

## Restricciones globales

- **Mismas respuestas que la web.** Las doradas de Teacher se comparan **por pregunta**, no por orden de línea. Primero el texto de la respuesta; después, las fuentes. Una diferencia **solo en las fuentes** (porque la búsqueda ahora es FTS y no la de `SelfCheck`) se revisa a mano y se anota. Una diferencia en el texto se arregla en Kotlin.
- **Teacher nunca inventa:** todo el coreano de una respuesta por reglas sale de tus notas (lo comprueba `selfcheck`).
- **Coach sin modelo:**
  - la intención y las herramientas, por reglas;
  - el texto, con las plantillas deterministas de la web (`_forced_reply`);
  - los temas por significado que en la web usaban embeddings (`meanings.py`, BGE-M3) se resuelven con `categories.py` y FTS. Si una intención solo funcionaba con embeddings, se anota en DECISIONS y queda para la fase 6.
- **Meta:** una respuesta por reglas en menos de 300 ms en el OPPO (plan, punto 9).
- **Logs sin preguntas, respuestas ni texto de tus notas.** No se cambian ajustes del teléfono. No se leen archivos fuera del proyecto. `adb pull` y `adb push` solo con rutas de archivo explícitas y comprobadas.

## Riesgos que hay que probar

1. **Preguntas mezcladas** (*"What does 근처 mean? also give me a quiz"*): una sola ruta clara, la misma que en la web (tarea 2).
2. **Coreano conjugado o con partícula** (바빠서, 학교에서, 가는데요): Teacher lo reconoce igual que la web, porque usa las mismas `korean_cores`, `lemmas` y `has_form` portadas (tarea 1).
3. **Una pregunta vacía, de un carácter o de 2 000 caracteres:** el botón Send se deshabilita con menos de 2 caracteres, el campo se corta en el máximo de la web y nada se cuelga (tarea 3).
4. **Notas sin el tema que pides:** el mensaje de la web, nunca una respuesta vacía, y el Coach no propone un quiz sin preguntas (tareas 1 y 2).
5. **Cambiar de pestaña con una respuesta en curso:** la conversación se conserva al volver (estado en el ViewModel; tarea 3).

---

## Tarea 1 — Respondedores de Teacher

📱 No hace falta.

- **Archivos** en `core/korean/.../teacher/`:
  - `Responder.kt`: `fun interface Responder { fun answer(q: Question, ctx: TeacherContext): TeacherAnswer? }` y `TeacherEngine(responders: List<Responder>)`;
  - un archivo por respondedor, con el orden de `Tutor.ask_stream`: palabra, gramática, ejemplo, comparar, frase en inglés (`_said_in_notes`), revisar frase (`_compare`), forma conjugada (`_from_verb`) y sin entrada (`_no_entry` y `_grammar_missing`);
  - `NotesIndexBuilder.kt` (`build_index`);
  - `Sources.kt` (`select_sources` y `format_sources`).
- **`core/data/.../teacher/NotesSearch.kt`:** la búsqueda FTS que devuelve `Hit` con la misma forma que la del web.
- **Pruebas:**
  - `IndexGoldenTest` contra `golden/index/*.json`;
  - `TeacherGoldenTest`: las 454 preguntas de `golden/teacher/*.jsonl`, por pregunta. Se informa cuántas coinciden en el texto y cuántas además en las fuentes;
  - una prueba por respondedor con sus casos.
- [ ] Pruebas en rojo → implementar respondedor a respondedor → verde → commit.
- **Parar si:** menos del 100 % de las respuestas coinciden en el texto, o una diferencia de fuentes cambia lo que se responde.

## Tarea 2 — Coach determinista

📱 No hace falta.

- **Archivos:**
  - `core/korean/.../coach/Intent.kt` (`intent` y `planned_tools`);
  - `coach/Replies.kt` (`_forced_reply`, `_proposal` y `drop_missing_buttons`);
  - `core/data/.../coach/CoachTools.kt` (las 6 herramientas de `agent/tools.py` sobre los repositorios) y `CoachEngine.kt`, que devuelve `CoachAnswer(text, steps, quiz: QuizProposal?, deck: DeckProposal?)`.
- **Doradas nuevas** desde `tools/golden/export_coach_golden.py`, sobre la base sintética de `golden/db/` con la hora fija:
  - unas 60 frases de prueba (*"What should I study?"*, *"I have 20 minutes"*, *"teach me something"*, *"make me a quiz of my mistakes"*, *"words that start with ㅂ"*, *"practice -아서"*, en inglés y en coreano);
  - para cada una: la intención, las herramientas planeadas, el resultado de las herramientas, el texto de `_forced_reply` y las propuestas.

  Las frases que en la web dependen de embeddings se marcan como `needsEmbeddings: true` y no se comparan.
- **Pruebas:** `CoachGoldenTest`, más `mixedQuestionTakesOneRoute` (riesgo 1) y `noQuizWithoutQuestions` (riesgo 4).
- [ ] Doradas → pruebas en rojo → implementar → verde → commit.

## Tarea 3 — Pantalla Teacher

📱 Sí, al final.

- **Archivos:** `app/.../ui/teacher/TeacherScreen.kt` y `TeacherViewModel.kt`, más `ChatBubble.kt`, `AnswerCard.kt`, `SourceList.kt`, `CoachCard.kt`, `QuizProposalCard.kt`, `DeckProposalCard.kt`, `HowIDecided.kt` y `Composer.kt`.
- **Según:** `design/teacher.md` y el componente TeacherChat del sistema, **sin** AiMark ni modelo:
  - el selector de modo;
  - las 3 plantillas;
  - *Thinking… N s* (las reglas apenas tardan, así que casi no se ve);
  - Clear answers.
  - *Ask what to do* del Dashboard abre el modo Coach.
  - *Start quiz* abre Quiz con esas preguntas, y *Create deck* crea el mazo (con el icono de Teacher en Flashcards) y pasa a *Open flashcards*.
- **Capturas Roborazzi:** `teacherEmpty`, `answerFromNotes`, `answerWithSourcesOpen`, `notCovered`, `coachWithQuizAndDeck`, `howIDecidedOpen`, `composerKeyboard` y `teacherAt130Percent`.
- **Pruebas:** `sendDisabledUnderTwoChars` y `maxLengthCut` (riesgo 3), y `conversationSurvivesTabSwitch` (riesgo 5).
- [ ] Capturas y pruebas en rojo → implementar → grabarlas → commit.

## Tarea 4 — `selfcheck` en el teléfono y metas

📱 Sí.

- **Archivos:** `core/data/src/androidTest/.../SelfCheckTest.kt`, que porta `rag/selfcheck.py`: preguntas generadas a partir de tus notas, en la cantidad de la web (semilla 7 y muestra 40), con las comprobaciones de la web (que no responda el modelo, citas válidas y ningún coreano fuera de tus notas).
- **En el OPPO:**
  - tu base primero exportada a la carpeta privada de la app;
  - `SelfCheckTest` sobre tus documentos reales: debe dar **OK**. El informe solo lleva cuántas preguntas, cuántas por tipo y cuántos fallos, sin el texto de las preguntas ni de las respuestas;
  - medir una respuesta por reglas (p50 y p90, menos de 300 ms) y el Coach;
  - restaurar tu base y comprobar las 1265 filas y los mismos sha256.
- [ ] Ejecutar → arreglar lo que falle (sin cambiar las comprobaciones) → commit.
- **Parar si:** `selfcheck` encuentra coreano inventado o citas inválidas. Se dice cuántas y de qué tipo antes de tocar nada.

## Al terminar la fase

- [ ] `DECISIONS.md`: FTS en lugar de la búsqueda de la web y las diferencias de fuentes aceptadas; las intenciones del Coach que necesitaban embeddings, para la fase 6; el orden de los respondedores.
- [ ] `mem_save` con `topic_key` `android/fase-5`.
- [ ] PR con la tabla:
  - doradas de Teacher (texto y fuentes);
  - doradas del Coach;
  - `selfcheck` en el teléfono;
  - p50 y p90 de una respuesta.

  Y ✅/⚠️/❌ por tarea.
