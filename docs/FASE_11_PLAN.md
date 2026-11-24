# Fase 11 — Palabra única (H-03) y funciones de estudio en BETA

> **Para Claude Code:** PR pequeños, en el orden de la sección 3. Un commit por tarea; cada tarea empieza por sus pruebas. **No se mergea ningún PR sin la confirmación del dueño.** Tras cada PR: informe corto con ✅/⚠️/❌ y **para**.
>
> **Diseño:** `design/beta.md`. Manda sobre las demás fichas donde las contradiga.
> **Referencia técnica:** `docs/PLAN_FUNCIONES_ESTUDIO.md` (el plan largo de las 10 funciones). **Este plan manda sobre él:** de allí solo entra lo que se nombra aquí, y con los cambios que se dicen aquí.
>
> 📱 **Teléfono:** en los PR que lo dicen. Siempre con la app QA (`.qa`) y el respaldo del dueño. La app del dueño solo se toca con su OK y con `install -r`.

---

## 0 · Decisiones (dueño, 2026-11-23)

| ID | Decisión |
|---|---|
| D11-1 | **«Dominada»** (*Learned*) significa lo mismo en toda la app: una tarjeta con `interval_hours ≥ 504` (21 días) y sin *Again* en sus últimas 5 valoraciones, **o** una racha de juegos ≥ 3 en ≥ 2 días distintos. **«Difícil»**: ≥ 3 *Again* en las últimas 10 valoraciones, **o** `wrong > correct` en juegos, **o** el último intento de Quiz de esa palabra fallido. Las cifras de *Learned* van a bajar: es lo esperado. |
| D11-2 | **No se hace B2:** el tiempo en las pantallas BETA (Listening Lab, Grammar Lab, My Mistakes y Reader) **no** cuenta para racha, calendario ni *Study time*. Lo que lanzan esas pantallas en otras partes (un quiz, un mazo) cuenta como siempre. |
| D11-3 | **No se hace B4** (no hay Journal). `NanoGate` no se toca, salvo para añadir el uso `SentenceExplain` (B5) del Reader. |
| D11-4 | **Entran:** F2 sin pista de memoria; F3; F4; F5 sin resumen semanal; F7 (los 4 juegos); F8; y F1 solo como recordatorio, **al final**. **No entran:** F6 (Journal), F9 (Situations), F10 (Goals y Exam), el widget de F1 ni la pista de memoria de F2. |
| D11-5 | Listening Lab, Grammar Lab, My Mistakes y Reader viven en una pantalla **BETA**, que se abre desde More. La barra inferior no cambia. Library, Dashboard, Quiz y las demás pantallas no se tocan, salvo lo que nombra la sección 2. |
| D11-6 | **Micrófono (amplía D9-1):** también en el shadowing de Listening Lab y en el juego Speak It, con las mismas condiciones: se pide al tocar; nunca se guardan ni el audio ni lo dicho; nada va a los logs. **No es un permiso nuevo.** |
| D11-7 | **Recordatorio sin `RECEIVE_BOOT_COMPLETED`:** tras reiniciar el teléfono, basta con abrir la app una vez para que el recordatorio vuelva. Sin permisos nuevos. El teléfono no vibra (canal sin vibración). |
| D11-8 | Speak It **no** cuenta para «mastered» de Games: solo suma `seen` (D9-6, sin nota). |

En el PR 0 se anotan D11-1…D11-8 en `docs/DECISIONS.md`.

---

## 1 · Reglas de la fase

- **Sin `INTERNET`, sin permisos nuevos y sin dependencias nuevas.** El manifiesto unido tiene que quedar igual que en 0.13.0.
- **El respaldo `.jsbackup.zip` v1 no cambia:** todas las tablas nuevas son solo del teléfono y no entran en `BackupSchema`. `BackupUnchangedTest` pasa en cada PR.
- **Las doradas no se tocan:** Flashcards (SM-2), Quiz, lector y Teacher.
- **Primero las reglas.** El modelo solo explica en inglés, siempre por `NanoGate`, con su validador y con `AiMark`. **Nunca escribe coreano** que no esté en la entrada.
- **No se mueve nada fuera de la sección 2.** Las pantallas nuevas usan los componentes que ya existen: StickerButton, Card, Pill, ResultBanner, TypedResult, HangulKeys, JsTopBar, StatStrip y SheetHeader. Sin colores ni tokens nuevos.
- **No se crea ninguna tarjeta nueva en `flashcards`**, ni un SM-2 por modo.
- El teléfono no vibra. Ningún texto de las notas en los logs.

---

## 2 · Qué cambia en pantallas que ya existen (solo esto)

| Pantalla | Cambio | Ficha |
|---|---|---|
| **More** | Una fila nueva, **Beta** `flask-conical` 베타, entre *Quiz Guide* y el divisor | `beta.md` 1 |
| **Sesión de Flashcards** | 🔊 en la cara coreana; dos interruptores nuevos, **Fill the Gap** y **Listen Only**, junto a *Type the answer* | `beta.md` 2 |
| **Flashcards (mazos) y Dashboard** | Solo los números de *Learned* / *Words learned this week*, por D11-1. La interfaz no cambia | — |
| **Games** | 4 juegos más en el sorteo. La pantalla no cambia | `beta.md` 7 |
| **Settings** | Una fila **Daily Reminder** (último PR) | `beta.md` 8 |

---

## 3 · PR, en orden

| # | Rama | Contenido | 📱 | Migración |
|---|---|---|---|---|
| 0 | `docs/fase-11` | Este plan, `design/beta.md`, `docs/PLAN_FUNCIONES_ESTUDIO.md` y D11-1…8 en DECISIONS | NO | — |
| 1 | `fase-11/palabra-a` | H-03 fase A: `WordProgress` de lectura y las definiciones de D11-1 | SÍ (cifras con el respaldo) | — |
| 2 | `fase-11/palabra-b` | H-03 fase B: tablas `word_state` y `question_words`, relleno y escritura desde Quiz y Games | SÍ (migración en el OPPO) | v5 → v6 |
| 3 | `fase-11/base` | Tablas `grammar_drill_attempts` y `listening_attempts` + `Screen` nuevas + pantalla **BETA** vacía (botones a pantallas *Coming soon*) + uso `SentenceExplain` | NO | v6 → v7 |
| 4 | `fase-11/flashcards` | F2: 🔊, Fill the Gap y Listen Only | SÍ (voz) | — |
| 5 | `fase-11/conjugador` | F3: motor (`Conjugator`, `EndingMatch`, `Drills`, `Explain`), sin pantalla | NO | — |
| 6 | `fase-11/grammar-lab` | F3: pantalla Grammar Lab | SÍ | — |
| 7 | `fase-11/listening` | F4: Listening Lab (dictado, shadowing y manos libres) | SÍ (voz y micrófono) | — |
| 8 | `fase-11/reader` | F8: Reader | SÍ | — |
| 9 | `fase-11/errores` | F5: My Mistakes, sin resumen semanal | SÍ | — |
| 10 | `fase-11/juegos-1` | F7: Hangul Wordle y Crossword | SÍ (benchmark) | — |
| 11 | `fase-11/juegos-2` | F7: Conjugation Runner y Speak It | SÍ (micrófono y benchmark) | — |
| 12 | `fase-11/recordatorio` | F1: recordatorio diario | SÍ (notificación real) | — |
| 13 | `fase-11/entrega` | 0.14.0, manifiesto, ronda corta en la app QA e instalar con OK | SÍ | — |

**Por qué en este orden:**
- H-03 va primero porque F5 lo necesita.
- La base crea todas las tablas en **una sola migración** (v7), para no encadenar seis migraciones.
- El conjugador va antes que Grammar Lab y Conjugation Runner, que lo usan.
- El recordatorio va al final, como pidió el dueño.

---

## 4 · Tareas por PR

### PR 1 · H-03 fase A (`docs/AUDIT_2.md`, H-03)

1. `D/progress/WordProgress.kt`: se calcula al vuelo desde Flashcards (`flashcards`, `flashcard_reviews`), Games (`game_item_progress`, quitando el `w:`) y Quiz (las preguntas cuya respuesta o enunciado es esa palabra; si no se puede atribuir, no cuenta). Clave: `norm(coreano)`. Devuelve, para cada palabra, `mastered`, `hard`, `masteredAt` (el primer momento en que cumplió D11-1) y sus contadores.
2. Las definiciones de D11-1 van en una sola función pura, en `K/progress/WordRules.kt`, con su tabla de casos.
3. Se usan en:
   - *Learned* de Flashcards (`FlashcardRepository` 218–226, hoy `repetitions >= 1`);
   - *Words learned this week* del Dashboard (palabras con `masteredAt` dentro de la semana).
   Los dos sitios dan el mismo número para el mismo periodo.
4. **Pruebas:**
   - `WordRulesTest`: 504 h justas, una *Again* en las 5 últimas, racha de 3 en un mismo día frente a 2 días;
   - `WordProgressTest` sobre la base dorada: Flashcards y Dashboard coinciden;
   - las doradas de SM-2 no cambian.
5. 📱 En la app QA con el respaldo: anota *Learned* antes y después. Bajará, y tiene que ser el mismo en las dos pantallas.

### PR 2 · H-03 fase B

1. Tablas solo del teléfono, en `D/db/WordTables.kt` y en `APP_TABLES`:
   - `word_state(word_key PK, first_seen_at, last_event_at, again_count, wrong_count, correct_count, source_mask, mastered_at)`;
   - `question_words(question_id, word_key)`.
2. `MIGRATION_5_6`: `CREATE TABLE IF NOT EXISTS` y relleno desde `flashcards`/`flashcard_reviews` y `game_item_progress`. El Quiz solo cuenta desde la migración, porque las preguntas antiguas no llevan palabra.
3. Quiz (al generar y al responder), Flashcards (al valorar) y Games (al acabar la ronda) escriben `word_state`. `WordProgress` pasa a leer de ahí y da los mismos resultados que en el PR 1. Pruébalo con las dos implementaciones a la vez.
4. **Pruebas:**
   - `MigrationTest` 5 → 6: las 18 tablas del respaldo, idénticas (sha256);
   - `BackupUnchangedTest`;
   - `IntegrityTest`;
   - `PhoneMigrationCheck` en el OPPO con datos sintéticos.

### PR 3 · Base

1. Tablas solo del teléfono, en `D/db/PracticeTables.kt` y en `APP_TABLES` (`PLAN_FUNCIONES_ESTUDIO.md`, F3 y F4):
   - `grammar_drill_attempts`, sin `session_id`;
   - `listening_attempts`, sin `session_id`. Nunca guarda lo dicho.

   `MIGRATION_6_7`, con las mismas pruebas que en el PR 2. **No se crea `practice_sessions`** (D11-2).
2. `Screen.Beta`, `Screen.GrammarLab(pointKey: String?)`, `Screen.Listening`, `Screen.Mistakes` y `Screen.Reader(documentId: Long?)`, con su `encode` y su prueba de ida y vuelta.
3. La fila **Beta** en More y la pantalla BETA (`beta.md` 1). En este PR, sus cuatro botones abren una pantalla *Coming soon*; cada PR siguiente sustituye la suya.
4. `AiUse.SentenceExplain(6_000)`, con su prompt versionado y su validador (inglés; el coreano, solo de la frase; ≤ 3 frases) y sus pruebas con salidas malas.

### PR 4 · F2 (`PLAN_FUNCIONES_ESTUDIO.md` F2, sin la pista de memoria)

1. `K/flashcards/CardModes.kt`: `clozeFor` y `modeFor`, como en el plan largo. Los ejemplos salen de `BankSource.bank()` con la clave `"w:" + norm(korean)`.
2. `UserPrefs`: `gapCards: Flow<Boolean>` y `listenCards: Flow<Boolean>`, los dos `false` por defecto, como `typeAnswer`. Con los dos apagados, la sesión es idéntica a hoy.
3. `StudyViewModel` recibe `KoreanVoice`. 🔊 y los dos interruptores, según `beta.md` 2.
4. La valoración va a `FlashcardRepository.review` sin cambios. Es siempre la misma tarjeta.
5. **Pruebas:**
   - `CardModesTest` (받침, verbo conjugado, sin ejemplo → clásica; sin voz nunca sale Listen);
   - `FlashcardsBehaviourTest`: con todo apagado, igual que hoy; con Gap, la valoración va al mismo `flashcard_id`;
   - Roborazzi del frente y el dorso de Gap y Listen, en claro y oscuro;
   - doradas de SM-2 sin cambios.

### PR 5 · F3, motor

Lo dice `PLAN_FUNCIONES_ESTUDIO.md` F3, «Motor de conjugación»: `Conjugator`, `irregularOf` (lista cerrada), `commonMistakes`, `EndingMatch`, `Drills` y `Explain` (con su `ConjugationTrace`).

**Regla de oro:** si no se sabe conjugar con seguridad, no se pregunta.

**Pruebas:**
- `ConjugatorTest`, con la tabla de casos del plan largo;
- `EndingMatchTest` con los puntos de `golden/grammar/*.json` y los 18 de `WW1_Grammar`: informa del porcentaje reconocido (objetivo ≥ 70 %);
- `DrillsTest`.

Si el web tiene más formas de conjugación, se exportan como doradas.

### PR 6 · Grammar Lab (`beta.md` 3)

`GrammarLabRepository`, `GrammarLabScreen` y `GrammarLabViewModel`. Los intentos van a `grammar_drill_attempts`, y lo que escribes no va nunca a los logs.

**Pruebas:**
- `GrammarLabBehaviourTest` sobre la base dorada;
- Roborazzi de los 3 tipos de ejercicio y del resultado, en claro y oscuro.

### PR 7 · Listening Lab (`beta.md` 4)

1. Antes de nada, saca lo común de la voz del Quiz (`Speaking.kt` y `SpeakingBlock.kt`) a `A/ui/components/speech/`. **Las capturas del Quiz no cambian.** Si cambian, PARA.
2. `ListeningPicker`, `ListeningScreen` y `ListeningViewModel`, con los tres modos. **No se toca el Quiz.**
3. **Pruebas:**
   - `ListeningPickerTest`;
   - `ListeningViewModelTest` con voz y reconocimiento falsos;
   - Roborazzi.

### PR 8 · Reader (`beta.md` 5)

`ReaderRepository` (lee `DocumentFiles.extracted`; no vuelve a pasar MuPDF), `Lookup` (puro), `ReaderScreen` y `ReaderViewModel`, y `ReaderAi` con `SentenceExplain`.

Rendimiento: un `AnnotatedString` con `LinkAnnotation` por línea, nunca un `Text` por palabra.

**Pruebas:**
- `LookupTest` con los PDF de `golden/`;
- macrobenchmark corto de desplazamiento por un PDF de 30 páginas;
- Roborazzi.

### PR 9 · My Mistakes (`beta.md` 6)

1. `K/mistakes/Patterns.kt` (clasificador por reglas) y `MistakesRepository`. Lee del Quiz, de Flashcards (*Again*), de Games, de `grammar_drill_attempts` y de `listening_attempts`, siempre con SQL dirigido y nunca con `.all()`. La palabra común la da `word_state` (PR 2).
2. **Sin resumen semanal** y sin tocar el Dashboard. No se crea `typed_answers`.
3. *Practice this* (`beta.md` 6) solo abre cosas que ya existen o que crea esta fase: Grammar Lab, el dictado de Listening Lab, un quiz con esas palabras (`QuizProposal`, como el Coach) o un mazo nuevo (`createCustomDeck`). **No abre Games.**
4. **Pruebas:**
   - `PatternsTest` (con pares de `golden/db/quiz_mistakes.json`);
   - `MistakesRepositoryTest`;
   - Roborazzi.

### PR 10 y 11 · Juegos nuevos (`PLAN_FUNCIONES_ESTUDIO.md` F7 y `beta.md` 7)

Los 4 juegos siguen los 7 pasos de «Cómo se añade un juego». Por PR:
- **PR 10:** Hangul Wordle y Crossword.
- **PR 11:** Conjugation Runner (con el motor del PR 5) y Speak It (D11-6, D11-8). Speak It solo entra en el sorteo con el reconocimiento de voz listo. Si niegas el micrófono, sale del sorteo.

Cada juego necesita:
- `FactoriesTest` y `EvaluatorsTest`;
- Roborazzi en claro y oscuro;
- **macrobenchmark de Games: P90 ≤ 8 ms y P99 ≤ 12 ms.**

Sin Nano (D8-9), sin efectos de sonido (D8-5) y sin vibración.

### PR 12 · Recordatorio (`beta.md` 8)

`PLAN_FUNCIONES_ESTUDIO.md` F1, «Recordatorio diario», **sin widget** y con D11-7. Además:
- `ReminderReceiver` con `exported="false"`;
- canal `reminders` con `enableVibration(false)`;
- texto solo con cifras y `VISIBILITY_PRIVATE`;
- se reprograma en `JustStudyApp.onCreate`.

**Pruebas:** `ReminderSchedulerTest` y la del receptor sin pendientes (no publica nada).

📱 El dueño activa el recordatorio para dentro de 2 minutos en la app QA y confirma que llega sin vibrar. **Code no toca el diálogo del permiso de notificaciones**: si aparece, lo acepta el dueño.

### PR 13 · Entrega

1. `versionName` 0.14.0. El manifiesto unido, igual que en 0.13.0. Skills de permisos e *intents*.
2. 📱 Ronda corta en la app QA con el respaldo:
   - BETA y sus 4 pantallas;
   - Flashcards con Gap y Listen;
   - los 4 juegos;
   - el recordatorio;
   - `dumpsys netstats` = 0 B.
3. Instalar en la app del dueño solo con su OK.

---

## 5 · Condiciones de parada

Para y pregunta si:
- el manifiesto gana un permiso o algo pide red;
- cambia una dorada o una tabla del respaldo (sha256);
- una migración en el OPPO no deja las 18 tablas idénticas;
- cambia una captura de una pantalla que no está en la sección 2;
- el conjugador falla un caso de la tabla y la única salida es ampliar la lista de irregulares sin una fuente;
- `EndingMatch` reconoce menos del 50 % de los puntos;
- Games pasa de P90 8 ms o de P99 12 ms;
- algo pide aceptar un diálogo del sistema o cambiar un ajuste del teléfono;
- un PR pasa de ~600 líneas de código de producto (se parte);
- hace falta un color o un token nuevo, o una ficha contradice a otra.

---

## 6 · Prompt para Claude Code

```
Fase 11. Lee docs/FASE_11_PLAN.md entero y, de cada PR, solo las secciones que cita de design/beta.md y de docs/PLAN_FUNCIONES_ESTUDIO.md.
Haz los PR en orden: 0, 1, 2 … 13. Tras cada PR: verificación completa, informe con ✅/⚠️/❌ y PARA hasta que lo mergee.
Reglas: un commit por tarea; cada tarea empieza por sus pruebas; sin permisos ni dependencias nuevas; respaldo y doradas intactos;
nada fuera de la sección 2 del plan; ningún color fuera de tokens.json v2; app QA para probar; mi app solo con mi OK.
Si se cumple una condición de parada (sección 5), para y pregunta. Empieza por el PR 0.
```
