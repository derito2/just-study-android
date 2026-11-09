# Ficha: Teacher con el Coach (Fases 5 y 6)

Sin captura propia. La referencia visual es el componente **TeacherChat** del Design System, con **AiMark**. Base: `design/shell.md`. Regla: **primero tus notas; si no está en ellas, el modelo (Gemini Nano, en el teléfono y sin internet) responde con la marca *AI · not from your notes*** (`docs/PLAN.md` 10.1).

## Estructura (destino Teacher)

1. **JsTopBar:** Teacher · 선생님; *Your teacher answers from your own notes first. For anything else, AI is used.* · *선생님은 먼저 네 노트로 대답해. 나머지는 AI를 사용해.* Al desplazar la conversación, se pliega a 64 dp.
2. **Modo:** SelectedGroup Small de dos opciones: **Ask my notes** · **What should I study?** Solo ayuda cuando la intención no está clara: una pregunta clara va sola a su sitio. *Ask what to do* del Dashboard abre Teacher en el segundo modo.
3. **Conversación:** columna con 16 dp entre mensajes; los más nuevos, abajo. Si hay mensajes, arriba a la derecha aparece **Clear answers** · 답변 지우기 (Gray, Small), que la vacía.
4. **Plantillas**, encima del composer, en una fila que se desplaza en horizontal (Chip), cada una con su coreano debajo:
   - *How do I say…?* · …을 어떻게 말해요?
   - *What does … mean?* · …은 무슨 뜻이에요?
   - *Example with…* · …로 예문

   Al tocar una, su texto (*"How do I say ___ in Korean?"*, *"What does ___ mean?"*, *"Give me an example sentence with ___."*) se pone en el campo con el cursor en el hueco.
5. **Composer** fijo encima del teclado (`imePadding`), sobre un divisor de 2 dp `line`:
   - TextField de 1 a 4 líneas: *Ask something worth asking. For example: how do I say school?*
   - **Send** · 보내기 (`Go`): deshabilitado con menos de 2 caracteres; `pop` al habilitarse.
   - Debajo, *Answers come from all your documents.* (12 sp, `muted`).

## Mensajes

- **Tuyo:** burbuja `brand` con texto blanco, radio 12, relleno de 8 × 16 dp, a la derecha y como máximo al 80 % del ancho.
- **Respuesta de tus notas:** Card a como máximo el 90 % del ancho, con relleno de 16 dp.
  - Arriba, tu pregunta (`body-bold`, `muted`).
  - La respuesta, con el coreano en `font-hangul`.
  - **Sources from your notes**, bajo un divisor de 2 dp `line`:
    - una fila por fuente, con `file-text` de 16 dp y *documento · page n* (12 sp, `muted`);
    - **Show the whole passage**, que despliega el fragmento;
    - **Also read, not used in the answer**, plegado.
- **Respuesta del modelo** (sin respaldo en tus notas):
  - la misma Card, con **AiMark** arriba;
  - si usó fragmentos, lleva sus fuentes y sus citas válidas;
  - si no hay ninguno, no lleva fuentes.
- **Sin modelo disponible** (AICore no está o da error): los mensajes de la web.
  - *Your notes don't cover this*: *Your teacher read the closest passages and the answer isn't there. Add it to your notes.*
  - *Nothing close enough in your notes*: *Nothing in your documents is close enough. Try other words, or go write better notes.*
- **Coach** (modo *What should I study?*):
  - la misma Card, con la etiqueta `coach-tag` (*COACH*, `warn-fill`);
  - el texto del Coach: lo calculan las reglas y lo redacta el modelo; si el modelo añade algo que no calcularon las reglas, lleva AiMark;
  - debajo, sus propuestas:
    - **Quiz:** Card `tint`, etiqueta *QUIZ* (`label-caps`, `brand-text`), *Your words* con la lista y **Start quiz** · 퀴즈 시작 (`Go`), que abre Quiz con esas preguntas.
    - **New deck:** Card `tint`, etiqueta *NEW DECK*, las palabras y **Create deck** · 덱 만들기 (`Action`). Al crearlo, el botón pasa a **Open flashcards** · 플래시카드 열기. El mazo lleva el icono de Teacher en Flashcards.
    - Errores, con los textos de la web: *None of these words are in your notes anymore, so the deck wasn't created.* y *The deck couldn't be created.*
  - **How I decided**: plegado, con los pasos que dieron las reglas (temas débiles, tarjetas pendientes).
- **Escribiendo:**
  - *Thinking… N s* con el contador, mientras no llega texto;
  - después, el texto en vivo con un cursor ▍ en `muted` que parpadea cada 1 s (sin parpadeo con *reducir movimiento*);
  - cada respuesta nueva entra subiendo 8 dp (240 ms).
- **Error:** el recuadro de error de `shell.md` dentro de la conversación, con *Try again*.

## Estados

- **Vacío:** las plantillas y la línea de ayuda; nada más.
- **Mientras se procesa un PDF:** el modelo no se usa (plan, punto 9). Las respuestas salen por reglas, con esta nota en 12 sp `muted`: *"AI answers are paused while a PDF is processing."*

## Datos

- Respondedores por reglas, con sus pruebas doradas (`golden/teacher/*.jsonl`, comparadas por pregunta).
- Coach determinista en `:core:korean` y `:core:data`.
- El modelo, en `:core:ai` (fase 6): pausa de 2 s y un reintento si AICore da BUSY, solo con la app en primer plano, y el prompt de sistema *"You are an English-speaking tutor. Write your whole answer in English"*.
- La conversación vive en memoria; Clear answers la borra.
