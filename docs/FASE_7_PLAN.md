# Fase 7 — Pulido y APK final: plan de implementación

> **Para Claude Code:** este documento es el plan de la fase entera. Una rama (`fase-7/polish`), un commit por tarea y **un PR al final**, sin mergear sin la confirmación del dueño. Si una tarea cambia el build (R8, perfiles), puede ir en su propio PR, también sin mergear sin confirmación. Skills: `android-profiler`, `anr-audit-and-fix`, `jetpack-compose-performance`, `r8-analyzer`, `android-permissions-security`, `android-intent-security`, `edge-to-edge` y `superpowers:verification-before-completion`.
>
> 📱 **Teléfono:** sí en casi todas las tareas, por USB con la depuración activada.

**Objetivo:** la APK de release firmada, rápida y revisada, instalada en el OPPO con tus datos. Además, cierra lo que dejó abierto la fase 6: el juez demasiado estricto, la espera de las preguntas del modelo y los pares de notas desordenadas sin botón para aceptarlos.

**Base:** `docs/PLAN.md` (puntos 8, 9, 11 y 12); `docs/DECISIONS.md` (sobre todo R-016, los datos de prueba y la fase 6); `docs/RECOMMENDATIONS.md` (sección 6, *Ya planeado*); los diseños `design/quiz.md` (A.3) y `design/library.md` (E, *Aceptar pares*).

## Restricciones globales

- **Metas del plan (punto 9), medidas en release en el OPPO:**
  - abrir en menos de 1 s;
  - una respuesta por reglas en menos de 300 ms;
  - frames sin jank en Flashcards, Quiz y Teacher;
  - menos de 300 MB al procesar 10 PDFs seguidos.
- **Nada cambia de comportamiento** salvo las tareas 4 y 5. Las doradas, el `selfcheck` y las capturas siguen en verde. Si una capa de R8 o de perfiles cambia algo, se para.
- **Tus datos:**
  - el paso de debug a release lo haces tú (tarea 1);
  - Code no desinstala nada;
  - los datos de prueba se reinician solo cuando lo digas (tarea 7);
  - las pruebas que escriben datos van sobre una copia de la base.
- **En el OPPO:** las mismas reglas de siempre. El teléfono no vibra nunca.

## Riesgos que hay que probar

1. **R8 rompe algo en runtime** (MuPDF por JNI, ML Kit, Room, Hilt o la serialización del respaldo): pasada completa de humo en release (tarea 3).
2. **Perder datos al actualizar:**
   - debug → release una sola vez, con respaldo;
   - después, release → release con `install -r` conserva todo (tarea 1).
3. **ColorOS mata la app:** a mitad de procesar un PDF largo, de un quiz o de una respuesta del modelo. Al volver, todo sigue o se reanuda (tarea 6).
4. **Sin red de verdad:** en modo avión, todo funciona salvo la descarga de Nano (tarea 6).

---

## Tarea 1 — Release firmada en el OPPO

📱 Sí.

- **Code:**
  - genera la release firmada (`assembleRelease` con la clave de R-016) y comprueba con `apksigner verify`;
  - todavía no instala nada en el OPPO.
- **Tú, con los pasos que Code te da escritos** (los de DECISIONS R-016):
  1. exportar el respaldo desde *Settings*;
  2. desinstalar la debug;
  3. Code instala la release;
  4. importar el respaldo.
- **Code comprueba:**
  - 18 de 18 tablas con las mismas filas;
  - los sha256 de la exportación iguales a los del respaldo;
  - los PDFs y lo extraído siguen ahí;
  - Nano sigue en *Ready*.
- **Desde aquí todo se mide en release.** Una segunda release con `install -r` conserva todo (prueba de actualización).
- **Copias `files/pre-*-snapshot`:** desaparecen al desinstalar la debug. Se dice en el informe antes del paso 2 y se espera tu OK.

## Tarea 2 — Rendimiento: Baseline Profiles, arranque y jank

📱 Sí.

- **Build type `benchmark`:**
  - `ProfileInstallReceiver` vuelve solo ahí (DECISIONS 2026-11-07);
  - módulo `:macrobenchmark` con arranque en frío, abrir Flashcards y girar 10 tarjetas, un quiz de 5 preguntas y 3 preguntas a Teacher;
  - el benchmark corre sobre una copia de la base, nunca sobre tus datos.
- **Baseline Profile** generado en el OPPO y commiteado.
- **Medir antes y después en release:**
  - arranque en frío (5 veces);
  - p50 y p90 de los frames;
  - memoria con 10 PDFs seguidos (PDFs sintéticos del repo, sobre la copia).
- **Revisar:**
  - con `jetpack-compose-performance`, las pantallas con frames lentos;
  - con `anr-audit-and-fix`, el código. Se arregla lo que encuentren, con prueba.
- **Parar si:** una meta del punto 9 no se cumple y el arreglo cambia el comportamiento.

## Tarea 3 — R8 y tamaño

📱 Sí, para la pasada de humo.

- Revisar las reglas *keep* con `r8-analyzer`:
  - quitar las redundantes;
  - dejar las justas para MuPDF, ML Kit y Room.
- **Pasada de humo en release:**
  - importar un respaldo (en la copia);
  - procesar un PDF sintético;
  - Flashcards, Quiz (reglas y modelo), Teacher, Coach, Grammar, Calendar, Dashboard y Settings;
  - exportar el respaldo.
- **Informe:**
  - tamaño de la APK antes y después;
  - qué reglas se quitaron.

## Tarea 4 — IA: juez, preguntas sin esperar y pares de notas

📱 JVM primero; al final, sí.

- **Juez:** en la PoC 2 aprobaba ~1/3 de las respuestas parafraseadas; en la fase 6, 0/5.
  - Repetir la tanda de la PoC 2 (60 casos) por la puerta y comparar prompt, temperatura y formato de salida con los de la PoC.
  - Ajustar hasta recuperar la tasa de la PoC con **0 falsos positivos**. Si no se llega sin falsos positivos, se queda estricto y se anota.
  - El juez sigue sin aprobar nunca coreano.
- **Preguntas del modelo sin esperar:** según `design/quiz.md` A.3. Prueba con un modelo falso lento, uno que falla y uno con todo de modelo. R-008 sigue funcionando con las de reglas.
- **Aceptar pares de notas desordenadas:**
  - según `design/library.md` E (*Aceptar pares*);
  - `validate_llm_pair` y, además, el inglés literal en el fragmento;
  - al aceptar, una palabra normal del documento;
  - el formato del respaldo no cambia.
  - Pruebas con un PDF sintético `needs_llm_pairs`.
- **Parar si:** el juez aprueba un caso falso positivo de la PoC, o el respaldo cambia de formato.

## Tarea 5 — Seguridad, accesibilidad y bordes

📱 Sí.

- **Seguridad:**
  - `android-permissions-security` y `android-intent-security` sobre el manifiesto unido de release;
  - `debuggable=false`, `allowBackup=false` y reglas de extracción vacías;
  - solo `MainActivity` exported;
  - sin `INTERNET`;
  - `grep` de los logs: ninguno lleva texto de notas, preguntas ni respuestas.
  - Informe con los hallazgos. Si hay alguno, se arregla con prueba.
- **Accesibilidad:**
  - TalkBack en Dashboard, una tarjeta, una pregunta de quiz y Teacher (sin capturas: dime qué lee mal);
  - letra al 130 %;
  - contraste de los tokens con texto, sin cambiar colores: si alguno no llega a AA, se informa y lo decido yo.
- **`edge-to-edge`:** nada tapado por la barra de estado ni la de gestos, con el teclado abierto en las 3 pantallas con campo de texto.

## Tarea 6 — Robustez en el OPPO

📱 Sí.

Todo sobre la copia de la base:
- `am kill` con la app en segundo plano: a mitad de un quiz, a mitad de una respuesta del modelo y a mitad de procesar un PDF;
- **modo avión** durante una sesión completa (pídeme que lo active);
- **PDF largo con la pantalla apagada:** el dueño pone el PDF en la biblioteca de la copia o dice cuál usar; el informe dice si terminó y cuánto tardó. Cierra lo pendiente de la fase 4;
- tocar la notificación de procesado abre la app (R-015, sin comprobar hasta ahora).

## Tarea 7 — Datos de prueba, manual y versión final

📱 Sí.

- **Reiniciar los datos de prueba, solo cuando el dueño lo diga en ese momento:**
  - quitar el fallo de quiz que respondió Code;
  - borrar el mazo "Your mistakes";
  - borrar el resto del historial de pruebas que el dueño indique.
  - Antes, un respaldo; después, el informe con las filas por tabla.
- **`README.md`:** instalar y actualizar la APK, respaldo, Nano y qué hace cada pantalla. Corto y en español.
- **Versión:**
  - `versionName` `1.0.0`;
  - la release final instalada con `install -r`;
  - etiqueta `v1.0.0` después del merge, solo con la confirmación del dueño.
- **Limpiar:** sin archivos de prueba en el teléfono ni en el Mac fuera de `build/`. `build/poc/` es del dueño y no se toca.

## Al terminar la fase

- [ ] `DECISIONS.md`:
  - medidas antes y después;
  - reglas de R8;
  - el juez;
  - los hallazgos de seguridad y accesibilidad.
- [ ] `mem_save` con `topic_key` `android/fase-7`.
- [ ] PR con la tabla de metas del punto 9 (meta, medida y ✅/❌), el tamaño de la APK y ✅/⚠️/❌ por tarea.
