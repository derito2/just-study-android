> Referencia técnica. Lo que manda es docs/FASE_11_PLAN.md.

# Plan de funciones de estudio — Just Study 한국어 (Android)

Fecha: 2026-11-23 · Revisado sobre `main` en `c61a793` (versión 0.13.0, base de datos v5).

Este documento se basta solo: quien lo implemente no necesita la conversación de la que salió. Propone **10 funciones nuevas para estudiar** y cómo encajan, archivo por archivo, en la arquitectura actual. Las fichas de diseño (`design/*.md`) y los planes de fase los hace después la sesión de diseño. Aquí solo se fija **qué** y **dónde**.

Las 10 respetan las reglas del proyecto:
- sin `INTERNET`;
- sin costos;
- **primero las reglas**: el modelo solo redacta en inglés o aprueba, siempre detrás de `NanoGate` y con su validador;
- el respaldo `.jsbackup.zip` v1 no cambia;
- el teléfono no vibra.

**Convenciones**
- **Rutas:**
  - `A/` = `app/src/main/java/com/dero/juststudykorean/`
  - `D/` = `core/data/src/main/java/com/dero/juststudykorean/data/`
  - `K/` = `core/korean/src/main/kotlin/com/dero/juststudykorean/korean/`
  - `I/` = `core/ai/src/main/java/com/dero/juststudykorean/ai/`
- **Esfuerzo:** S (pequeño), M (mediano), L (grande).
- **🔒 Decisión:** algo que tiene que aprobar el dueño antes de empezar. Se anota en `docs/DECISIONS.md`.
- **Firmas de código:** son orientativas. El nombre final lo fija el PR.

---

## Índice

0. [Lo que ya existe y se reutiliza](#0--lo-que-ya-existe-y-se-reutiliza)
1. [Base común (B1–B5): antes de cualquier función](#1--base-común-b1b5)
2. [F1 — Recordatorios y widget](#f1--recordatorios-y-widget)
3. [F2 — Flashcards con audio, modos de tarjeta y pista de memoria](#f2--flashcards-con-audio-modos-de-tarjeta-y-pista-de-memoria)
4. [F3 — Grammar Lab: gimnasio de conjugación](#f3--grammar-lab-gimnasio-de-conjugación)
5. [F4 — Listening Lab: dictado, shadowing y manos libres](#f4--listening-lab-dictado-shadowing-y-manos-libres)
6. [F5 — My mistakes: cuaderno de errores y resumen semanal](#f5--my-mistakes-cuaderno-de-errores-y-resumen-semanal)
7. [F6 — Journal: diario en coreano con corrección](#f6--journal-diario-en-coreano-con-corrección)
8. [F7 — Juegos nuevos: Hangul Wordle, Crossword, Conjugation Runner, Speak It](#f7--juegos-nuevos)
9. [F8 — Reader: lector interactivo de tus apuntes](#f8--reader-lector-interactivo-de-tus-apuntes)
10. [F9 — Situations: práctica de situaciones](#f9--situations-práctica-de-situaciones)
11. [F10 — Goals y Exam: metas con fecha y simulacro](#f10--goals-y-exam-metas-con-fecha-y-simulacro)
12. [Orden, dependencias y migraciones](#12--orden-dependencias-y-migraciones)
13. [Decisiones que hacen falta](#13--decisiones-que-hacen-falta)
14. [Riesgos](#14--riesgos)

---

## 0 · Lo que ya existe y se reutiliza

| Pieza | Dónde | Para qué se reutiliza |
|---|---|---|
| Voz coreana sin red | `D/audio/KoreanVoice.kt` (`speak(text, rate)`, `state`, `muted`, `onInterrupted`), `TtsKoreanVoice` | F2, F4, F7, F8, F10 |
| Reconocimiento de voz sin red | `D/speech/KoreanSpeech.kt` (`availability()`, `listen(maxMs): Flow<SpeechEvent>`, `finish()`), `PlatformKoreanSpeech` | F4, F6, F7, F9 |
| Comparar lo dicho | `K/speech/SpokenCompare.kt` (`compareSpoken`, `normalizeSpoken`) | F4, F7 |
| Comparar lo escrito | `K/CompareAnswer.kt` (`Comparison`, `Letter`, `Mark`, `Verdict`) | F3, F4, F5, F10 |
| Conjugación | `K/KoreanForms.kt` (`aeoForms`, `past`, `hasForm`, `lemmas`, `koreanCores`) | F3, F7, F8 |
| Hangul | `K/hangul/Hangul.kt` (`indexesOf`, `syllableOf`), `K/hangul/HangulIme.kt` (두벌식) | F3, F7 |
| Teclado coreano en pantalla | `A/ui/games/pieces/HangulKeys.kt` | F3, F7 |
| Partículas por 받침 | `K/games/Particles.kt` (`correctAfter`, `wrongAfter`, `particleSpots`) | F5, F6, F9 |
| Banco de palabras y frases (con todos sus ejemplos) | `K/games/GameBankBuilder.kt`, `D/games/GameContentProvider.kt` (`BankSource.bank()`), claves `"w:"`/`"s:"` + `norm(coreano)` | F2, F4, F6, F7, F9 |
| Búsqueda en tus notas | `D/teacher/NotesSearch.kt` (FTS), `K/teacher/NotesIndex.kt`, `K/teacher/Search.kt` | F6, F8, F9 |
| Revisar una frase | `K/teacher/Teacher.kt` (*"Is this sentence correct?"*: `checkedSentence`, `compare`) | F6, F9 |
| Temas por categoría | `K/coach/Categories.kt` (`categoryOf`, `belongs`), `K/coach/Practice.kt` (`byTheme`, `wordType`) | F9 |
| Coach determinista y su redacción | `D/coach/CoachEngine.kt`, `D/ai/CoachAi.kt` (`validFromFacts`) | F5, F10 |
| Puerta única de Nano | `I/NanoGate.kt` (`AiUse`, `AiRequest`, `ask(request, validate)`, caché en memoria, 2 s entre llamadas, un reintento con `BUSY`) | F2, F3, F5, F6, F8, F9 |
| Validadores y prompts versionados | `K/ai/Validators.kt`, `K/ai/*Prompts.kt` | todas las funciones con IA |
| Marca *AI · not from your notes* | `A/ui/components/AiMark.kt` | todas las funciones con IA |
| Generador de quizzes | `D/quiz/QuizGenerator.kt`, `K/QuizBuilders.kt` | F5, F10 |
| Cifras y racha | `D/progress/ProgressRepository.kt` (`weekly()`, `games()`, `attachSession`) | B2, F5, F10 |
| Tablas solo del teléfono | `D/db/GameTables.kt` + `tools/data/gen_room_entities.py` (`APP_TABLES`) | B1 |
| Crear mazos desde palabras | `FlashcardRepository.createCustomDeck(name, words)` | F5, F8, F9 |
| Dirección de tarjeta | `UserPrefs.cardDirection` (`KoToEn`, `EnToKo`, `Both`): **ya existe** | F2 (no se duplica) |
| Navegación | `A/ui/navigation/AppNavigation.kt` (`Route`, `sealed interface Screen`), `Destinations.kt` (`TopLevel`, `MoreDestination`), `A/ui/components/MoreSheet.kt` | B3 |

---

## 1 · Base común (B1–B5)

Cinco piezas que usan varias funciones. Van en un PR propio, **sin pantallas nuevas visibles**, para que los PR de cada función sean pequeños.

### B1 — Tablas de práctica solo del teléfono

Se sigue el mismo patrón que Games (D8-3):
- archivo `D/db/PracticeTables.kt` con sus entidades y DAO;
- los nombres se añaden a `APP_TABLES` en `tools/data/gen_room_entities.py` y se regenera `JustStudyDatabase.kt`;
- **no se añaden a `BackupSchema`**, así que `.jsbackup.zip` v1 no cambia;
- las claves de palabra son texto (`norm(coreano)`), igual que `game_item_progress`, y sobreviven a reprocesar e importar.

Tabla común a todas las funciones:

```kotlin
@Entity(tableName = "practice_sessions", indices = [Index("started_at"), Index("kind")])
data class PracticeSessionsEntity(
    @PrimaryKey val id: Long,
    val kind: String,            // "grammar_lab", "listening", "journal", "reader", "situations", "exam"
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long,
    val items: Long,             // ejercicios hechos
    val correct: Long,           // aciertos (0 donde no se califica: shadowing, journal)
)
```

Cada función añade **sus** tablas en su PR, en este mismo archivo (lista en la sección 12). Cada PR que añade tablas sube la versión de la base y añade su `MIGRATION_n_n+1` en `D/db/AppTables.kt`, solo con `CREATE TABLE IF NOT EXISTS`. Se registra en `DbModule.addMigrations` y se exporta `core/data/schemas/…/n+1.json`.

> H-03 fase B (`word_state`, auditoría 2) también planea una migración (v6). **Si H-03 va antes**, estas tablas empiezan en v7. Si no, se coordina para que no compitan por el mismo número.

**Pruebas:**
- `MigrationTest` desde la versión anterior, con todas las filas iguales;
- extender `BackupUnchangedTest`: exportar con filas en las tablas nuevas da el mismo `.jsbackup.zip` (sha256 por tabla);
- `IntegrityTest` sigue en verde.

### B2 — Practicar cuenta como estudiar

Igual que D8-4 con Games:
- `ProgressRepository` gana `private suspend fun practice(): List<Pair<Long, Long>>`, que lee `practice_sessions` con `items > 0`;
- se suma donde hoy se suma `games()`: tiempo de estudio, calendario, gráfica y racha;
- las doradas no tienen filas de práctica, así que no cambian.

`D/practice/PracticeSessions.kt` (nuevo):

```kotlin
@Singleton
class PracticeSessions @Inject constructor(private val db: JustStudyDatabase, private val clock: AppClock) {
    suspend fun start(kind: String): Long
    suspend fun record(id: Long, items: Int, correct: Int)   // suma al cerrar cada ejercicio
    suspend fun end(id: Long)                                 // fija la duración (tiempo activo, como en Games)
}
```

🔒 **D-B2:** *practicar en Grammar Lab, Listening, Journal, Reader, Situations y Exam cuenta como estudiar.* Sale del mismo razonamiento que D8-4.

**Pruebas:**
- `ProgressRepository` con una sesión de práctica de ayer: la racha sube;
- con `items == 0` no cuenta.

### B3 — Navegación

En `A/ui/navigation/AppNavigation.kt`, `Screen` gana estas entradas, todas sin barra inferior, como `Study` y `Games`:

```kotlin
data class GrammarLab(val pointKey: String?) : Screen      // F3 (null = todos los puntos)
data object Listening : Screen                              // F4
data object Mistakes : Screen                               // F5
data object Journal : Screen                                // F6
data class Reader(val documentId: Long) : Screen            // F8
data object Situations : Screen                             // F9
data object Goals : Screen                                  // F10
data class Exam(val goalId: Long?) : Screen                 // F10
```

Además:
- `Screen.encode` y la restauración de la pila: cada entrada nueva tiene su prueba de ida y vuelta, igual que las existentes, para que ColorOS pueda matar el proceso sin perder la pantalla;
- **puntos de entrada** (los decide diseño): una sección *Practice* en `MoreSheet` (Listening, Journal, Situations, Mistakes, Goals), *Practice* en cada `GrammarCard` (Grammar Lab), *Read* en `DocumentCard` (Reader) y tarjetas en el Dashboard (F5, F10).

### B4 — `NanoGate` para otras funciones de ML Kit (solo para F6)

Hoy `NanoGate.ask` llama siempre a `NanoModel.generate`. La Proofreading API es otro cliente del mismo AICore, así que tiene que pasar **por el mismo `Mutex`**, con la misma pausa de 2 s, el mismo reintento y el mismo bloqueo si hay un PDF en cola o la app no está delante. Si no, vuelven los `BUSY` de la PoC 2.

Cambio mínimo en `I/NanoGate.kt`: separar el «cómo se llama» del «cuándo se puede llamar».

```kotlin
// antes: private suspend fun call(request: AiRequest): String?
// después: la misma lógica de lock/pausa/BUSY/abandono, pero con el trabajo como parámetro
private suspend fun <R> guarded(use: AiUse, work: suspend () -> R): R?

suspend fun <T : Any> ask(request: AiRequest, validate: (String) -> T?): T?          // sin cambios por fuera
suspend fun <T : Any> proofread(text: String, validate: (List<String>) -> T?): T?   // nuevo, usa guarded()
```

`NanoModel` gana `suspend fun proofread(text: String): List<String>` (sugerencias ordenadas por confianza). Su implementación va en `MlKitNano`, y el modelo falso de las pruebas devuelve listas fijas.

**Pruebas** (como las de R-013):
- una corrección seguida de un `ask` respeta los 2 s;
- con un PDF en cola, `proofread` devuelve `null` sin llamar al modelo.

### B5 — `AiUse` nuevos

En `enum class AiUse(val timeoutMs: Long)` se añaden estos usos. Los plazos son provisionales y se miden en el OPPO como en la PoC 2.

| Uso | Plazo | Función |
|---|---|---|
| `Mnemonic(5_000)` | 5 s | F2 |
| `Weekly(5_000)` | 5 s | F5 |
| `JournalExplain(6_000)` | 6 s | F6 |
| `Proofread(4_000)` | 4 s | F6 |
| `SentenceExplain(6_000)` | 6 s | F8 |
| `SituationTask(4_000)` | 4 s | F9 |
| `SituationJudge(3_000)` | 3 s | F9 |

Cada uso tiene:
- su prompt en inglés, versionado en `K/ai/<Función>Prompts.kt` (`const val X_VERSION = "x-1"`);
- su validador en `K/ai/Validators.kt`;
- pruebas JVM con salidas buenas y malas del modelo.

**Regla común de los validadores** (la misma de hoy):
- respuesta en inglés;
- **cada palabra coreana tiene que estar en la entrada** (`input`);
- sin markdown (`plainText` ya lo quita);
- longitud máxima.

Lo que no pasa el validador no se muestra, y en su lugar sale la versión sin modelo, sin mensaje de error.

---

## F1 — Recordatorios y widget

**Objetivo:** constancia. La app hoy no avisa nunca: `POST_NOTIFICATIONS` solo se usa para el procesado de PDF.
**Esfuerzo:** S (recordatorio) + M (widget). **Depende de:** nada.

### Recordatorio diario

**UI:** en *Settings*, una sección *Reminder* con un interruptor y la hora (por defecto, apagado; diseño fija los textos).

**Datos:** `UserPrefs` gana:

```kotlin
val reminder: Flow<Reminder>      // data class Reminder(val on: Boolean = false, val minuteOfDay: Int = 20 * 60)
suspend fun setReminder(r: Reminder)
```

**Archivos nuevos:**
- `D/reminders/ReminderScheduler.kt`:

  ```kotlin
  @Singleton
  class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context, private val clock: AppClock) {
      /** Inexacta: setWindow(RTC, próxima hora, 15 min). No pide SCHEDULE_EXACT_ALARM ni ningún permiso. */
      fun schedule(r: Reminder)
      fun cancel()
  }
  ```

- `A/reminders/ReminderReceiver.kt`: un `BroadcastReceiver` con **`android:exported="false"`**, al que solo llega el `PendingIntent` explícito de la alarma. Al sonar:
  1. lee las cifras (`FlashcardRepository` pendientes, racha de `ProgressRepository`);
  2. si hay algo que hacer, publica la notificación;
  3. reprograma la del día siguiente.

  Usa `goAsync()` y lee la base en `Dispatchers.IO`.

**Notificación:**
- canal propio `reminders`, con `enableVibration(false)` y `setSound(null, null)`: **el teléfono no vibra** (R-010, descartada);
- texto con **cifras solamente**, sin texto de tus notas en la pantalla bloqueada: *"12 cards due · 8-day streak"*. `VISIBILITY_PRIVATE`;
- al tocarla, abre `MainActivity` con un extra que lleva a `Screen.Study(null, "All decks")`. Mismo patrón que `ProcessingService.setContentIntent`: `PendingIntent` explícito e inmutable;
- si no hay tarjetas pendientes y la racha ya se cumplió hoy, no se publica nada.

**Reprogramar:** sin `RECEIVE_BOOT_COMPLETED` (sería un permiso nuevo), la alarma se pierde al reiniciar el teléfono. Para compensarlo, se reprograma en `JustStudyApp.onCreate` (cada vez que abres la app) y al cambiar el ajuste. Así basta con abrir la app una vez después de reiniciar.

🔒 **D-F1a:** recordatorio sin permiso de arranque (aceptar que tras reiniciar hay que abrir la app una vez), **o** añadir `RECEIVE_BOOT_COMPLETED` (permiso normal, sin diálogo, pero hay que añadirlo a `ALLOWED_PERMISSIONS` en `tools/ci/check_manifest.py` y a `CLAUDE.md`).

**Pruebas:**
- `ReminderSchedulerTest` (Robolectric, `ShadowAlarmManager`): con el interruptor encendido hay una alarma en la ventana correcta; con el interruptor apagado no queda ninguna;
- el receptor con 0 pendientes y la racha hecha no publica nada (`ShadowNotificationManager`);
- `check_manifest.py` en verde (ningún permiso nuevo con D-F1a = sin permiso).

### Widget

**Qué muestra:** *Word of the day* (una palabra de tus notas con el significado oculto; un toque abre la app en esa tarjeta), la racha y las pendientes.

**Cómo, sin dependencias:**
- `AppWidgetProvider` con `RemoteViews` (el layout es XML en `res/layout/widget_today.xml`);
- se actualiza al terminar cualquier sesión de estudio (`AppWidgetManager.updateAppWidget` desde `PracticeSessions.end`, `FlashcardRepository.review` y `GameRepository.endSession`) y una vez al día desde la misma alarma de F1;
- la palabra del día se elige con una semilla por fecha (`roundRandom(epochDay, 0)`) entre las palabras con tarjeta; hace falta `FlashcardRepository.randomWord(seed)`.

**Alternativa:** Jetpack Glance (AndroidX, Apache-2.0, sin red). Permite escribir el widget en Compose a cambio de añadir una dependencia (~1 MB). Se recomienda **RemoteViews**: es un widget pequeño y así no se añade nada.

🔒 **D-F1b:** el proveedor de un widget **tiene que ser `exported="true"`**, porque el launcher le manda `APPWIDGET_UPDATE`. Eso rompe la regla *«solo la actividad principal es exported»* (`CLAUDE.md`, *Seguridad*), así que hace falta el sí del dueño y pasar la skill *Android Intent security*. Mitigación:
- el receptor solo atiende `android.appwidget.action.APPWIDGET_UPDATE`;
- ignora cualquier extra;
- no lee datos del `Intent`.

**Pruebas:**
- Robolectric: `onUpdate` pinta los tres valores (con una base dorada);
- prueba de manifiesto: el único receptor exportado es el del widget y solo tiene ese `intent-filter`.

**Qué NO hacer:** WorkManager (DECISIONS 2026-11-09 lo descartó), alarmas exactas, vibrar, o enseñar texto de tus notas en la notificación.

---

## F2 — Flashcards con audio, modos de tarjeta y pista de memoria

**Objetivo:** que la sesión de tarjetas también entrene el oído y que las palabras difíciles tengan un gancho.
**Esfuerzo:** M. **Depende de:** B1 y B5 (solo la pista).

> **Ya existe y no se toca:** la dirección de la tarjeta (`UserPrefs.cardDirection`: `KoToEn`, `EnToKo`, `Both`) y *Type the answer*.

### Audio en la tarjeta

**Problema:** `A/ui/flashcards/StudyScreen.kt` y `StudyViewModel.kt` no usan `KoreanVoice`. Solo lo hacen Games y Quiz.

**Cambio:**
- `StudyViewModel` recibe `KoreanVoice` por Hilt, igual que `GamesViewModel`;
- botón 🔊 (icono Lucide `volume-2`, objetivo de 48 dp) en la cara que lleva el coreano;
- `UserPrefs.cardAutoplay: Flow<Boolean>` (por defecto `false`): al girar la tarjeta, suena el coreano;
- el botón solo aparece si `voice.state == VoiceState.Ready`. Si `voice.muted`, se muestra la pista *Turn the volume up to hear it* (mismo texto que Games);
- `onInterrupted` detiene la voz, pero la sesión **no** se pausa (no es un juego con tiempo);
- `voice.stop()` en `onCleared` y al calificar.

### Modos de tarjeta, sin cambiar el esquema

En vez de crear tarjetas nuevas (nuevas filas en `flashcards`, que cambiarían el respaldo y las doradas de SM-2), **la misma tarjeta se presenta de otra forma**. Cada tarjeta sigue teniendo **un solo estado SM-2**.

`K/flashcards/CardModes.kt` (nuevo, puro):

```kotlin
enum class CardMode { Classic, Cloze, Listen }

/** Un hueco en uno de TUS ejemplos de la palabra; null si ningún ejemplo la usa (hasForm + aeoForms). */
fun clozeFor(word: String, examples: List<Pair<String, String>>, rng: Random): Cloze?
data class Cloze(val before: String, val after: String, val answer: String, val translation: String)

/** Qué modo toca a esta tarjeta: alterna entre los permitidos y los que esta tarjeta admite (Cloze necesita ejemplo, Listen voz). */
fun modeFor(cardId: Long, allowed: Set<CardMode>, hasCloze: Boolean, voice: Boolean, rng: Random): CardMode
```

Cómo se usa:
- los ejemplos salen del banco de palabras (`GameBank`: cada palabra ya lleva **todos** sus ejemplos de todos los documentos, fase 8 0b), con `BankSource.bank()` y la clave `"w:" + norm(korean)`;
- `UserPrefs.cardModes: Flow<Set<CardMode>>` (por defecto, `{Classic}`; así el comportamiento de hoy no cambia);
- en *Flashcards*, el menú de ajustes del mazo gana *Card modes*;
- **Cloze:** el frente es tu frase con `____` y su traducción; el dorso es la palabra y la frase completa (con 🔊). Con *Type the answer*, se escribe la palabra y se compara con `CompareAnswer`, igual que hoy;
- **Listen:** el frente es solo 🔊 (suena solo al aparecer); el dorso es la palabra y su significado. Solo se ofrece si `VoiceState.Ready`;
- la calificación (*Again/Hard/Good/Easy*) va a `FlashcardRepository.review` **sin cambios**.

### Pista de memoria con Nano (opcional, bajo demanda)

**UI:** en el dorso, un enlace *Memory hint*. Pide la pista a Nano, la muestra con `AiMark` y la guarda.

**IA:**
- uso `AiUse.Mnemonic`;
- prompt en `K/ai/MnemonicPrompts.kt`: *"Give one short memory hint in English for the Korean word X (meaning: Y). You may use the sound of the word. Do not write any other Korean word."*;
- validador `validMnemonic(text, word)`: inglés; el único coreano permitido es la palabra (o sus sílabas, si la pista juega con el sonido); como mucho 200 caracteres.

**Datos:** tabla solo del teléfono `ai_hints(word_key TEXT PK, text TEXT, prompt_version TEXT, created_at INTEGER)`, en B1:
- se lee antes de llamar al modelo;
- si `prompt_version` es distinta, se vuelve a pedir;
- **no está en el respaldo**, y no es tuya: nunca cuenta como fuente ni sale en Teacher.

**Pruebas:**
- `CardModesTest`: `clozeFor` con 받침, verbos conjugados (`가다` → `가요` en el ejemplo) y sin ejemplo → `null`; `modeFor` nunca devuelve `Listen` sin voz ni `Cloze` sin ejemplo;
- `FlashcardsBehaviourTest`: con `{Classic}` la sesión es idéntica a hoy; con `Cloze`, calificar actualiza el mismo `flashcard_id`;
- Roborazzi: frente y dorso de Cloze y Listen, en claro y oscuro;
- doradas de Flashcards sin cambios.

**Qué NO hacer:**
- tarjetas nuevas en `flashcards`;
- un SM-2 por modo;
- que el modelo escriba frases de ejemplo en coreano (el ejemplo es siempre tuyo).

---

## F3 — Grammar Lab: gimnasio de conjugación

**Objetivo:** pasar de **leer** la gramática (pantalla Grammar) a **producir** las formas con tus verbos. Es la función de más valor para estudiar.
**Esfuerzo:** L (motor) + M (pantalla). **Depende de:** B1, B2 y B3.

### Motor de conjugación en `:core:korean`

Archivo nuevo `K/grammar/Conjugator.kt`. Amplía `KoreanForms.kt`; `aeoForms` y `past` ya están probados con `golden/korean.json`.

```kotlin
enum class Ending(val label: String) {
    PoliteAeo("-아요/어요"), PastPolite("-았어요/었어요"), Formal("-습니다/ㅂ니다"),
    And("-고"), SoBecause("-아서/어서"), If("-(으)면"), Because("-(으)니까"),
    Future("-(으)ㄹ 거예요"), Want("-고 싶어요"), Progressive("-고 있어요"),
    LetUs("-(으)ㅂ시다"), Please("-(으)세요"), Can("-(으)ㄹ 수 있어요"), But("-지만"),
}

enum class Irregular { None, B, D, S, Reu, H, L, Eu }

/** Todas las formas correctas (puede haber más de una: 하여/해). Vacío = no se sabe conjugar con seguridad. */
fun conjugate(verb: String, ending: Ending, irregular: Irregular = irregularOf(verb)): Set<String>

/** Lista cerrada de verbos irregulares comunes (돕다, 춥다, 듣다, 걷다, 짓다, 낫다, 모르다, 빠르다, 그렇다, 하얗다…). */
fun irregularOf(verb: String): Irregular

/** Formas equivocadas plausibles para opciones: sin contracción, 아/어 cambiado, irregular aplicado como regular. */
fun commonMistakes(verb: String, ending: Ending): Set<String>
```

**Regla de oro:** **nunca se pregunta lo que no se puede comprobar.**
- `ㅂ`, `ㄷ`, `ㅅ` y `르` son irregulares según el verbo (léxico), no según la forma: `입다` es regular, `돕다` no. Si un verbo tiene una de esas finales y no está en la lista cerrada (ni marcado como regular en una segunda lista), `conjugate` devuelve vacío y ese verbo no entra en ejercicios de esas terminaciones.
- `ㄹ` y `으` se pueden deducir por reglas.

**Correspondencia con tus puntos de gramática.** `K/grammar/EndingMatch.kt`:

```kotlin
/** El Ending de un punto de tus notas, leído de su patrón y su Structure ("A/V + 아서 (after ㅏ or ㅗ)"). */
fun endingOf(pattern: String, structure: List<String>): Ending?
```

Usa una tabla de patrones normalizados (`아서/어서`, `-아서`, `어서`, `아/어서` → `SoBecause`, …). Un punto sin correspondencia no tiene ejercicios de producción: solo los de reconocer, que ya hacen el Quiz (`grammar_selection`, `fill_blank`) y Games (Grammar Fixer).

**Verbos de tus notas:**
- `noteWords(chunks)` (`K/coach/Practice.kt`) filtrados con `wordType(it) == "verb"` o `"adjective"` y que terminan en `다`;
- más los lemas de tus frases (`lemmas`, `Teacher.kt` ya construye `index.lemmasOf`);
- con su significado en inglés para el enunciado.

**Generador de ejercicios.** `K/grammar/Drills.kt`:

```kotlin
sealed interface Drill {
    val pointKey: String; val verb: String; val ending: Ending
    /** "가다 + -아서 → ?" se escribe con el teclado. */
    data class Produce(override val pointKey: String, override val verb: String, override val ending: Ending, val answers: Set<String>) : Drill
    /** Elegir la forma correcta entre la buena y 2-3 de commonMistakes. */
    data class Choose(override val pointKey: String, override val verb: String, override val ending: Ending, val options: List<String>, val answer: String) : Drill
    /** Uno de TUS ejemplos del punto con el hueco donde va la forma (hasForm encuentra dónde). */
    data class FillExample(override val pointKey: String, override val verb: String, override val ending: Ending, val before: String, val after: String, val answers: Set<String>, val translation: String) : Drill
}

fun drills(point: GrammarPoint, verbs: List<Word>, count: Int, level: Int, rng: Random): List<Drill>
```

Por nivel:
- **nivel 1:** `Choose` con verbos regulares;
- **nivel 2:** `Produce`, mezclando regulares e irregulares conocidos;
- **nivel 3:** `FillExample` y `Produce` con irregulares.

Determinista con semilla, igual que Games (`roundRandom`).

**Corrección:**
- exacta contra `answers` (normalizada con `normalizeSpoken`, que quita espacios y puntuación);
- si falla, la diferencia letra a letra con `CompareAnswer` (`Letter`, `Mark`);
- además, una **explicación por reglas** (`K/grammar/Explain.kt`), sin modelo: *"가 ends in ㅏ, so -아서: 가 + 아서 → 가서 (contracted)"*. Se construye con el paso que siguió `conjugate`, que guarda su traza (`ConjugationTrace`: raíz, vocal, contracción, irregular aplicado).

**IA (opcional):** solo si quieres más explicación: `AiUse.ExplainMistake`, que ya existe, con `QuizAi.explainMistake(asked, correct, given, point)`. No hace falta un uso nuevo.

### Datos

Tabla solo del teléfono (B1):

```kotlin
@Entity(tableName = "grammar_drill_attempts", indices = [Index("point_key"), Index("created_at")])
data class GrammarDrillAttemptsEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "point_key") val pointKey: String,   // norm(patrón del punto)
    val verb: String, val ending: String, val kind: String, // Produce/Choose/FillExample
    val given: String, val correct: Boolean,
    @ColumnInfo(name = "session_id") val sessionId: Long,   // practice_sessions
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
```

Aquí `given` sí se guarda (es tu respuesta, no tus notas). **No se escribe en logs.**

### Capa de datos y UI

- `D/grammar/GrammarLabRepository.kt`: junta los puntos (`GrammarRepository`), los verbos y los intentos, y elige qué verbo y terminación tocan dando más peso a lo que fallaste (la misma idea que `Selection.itemWeight` de Games).
- `A/ui/grammar/GrammarLabScreen.kt` + `GrammarLabViewModel.kt`:
  - **cabecera:** el punto (patrón y significado) o *All grammar*; nivel 1–3;
  - **cuerpo:** el ejercicio. `Produce` usa `HangulKeys` (`A/ui/games/pieces/`) o el teclado del sistema, según un ajuste. `Choose` usa `QuizOption`. `FillExample` usa la frase con hueco;
  - **resultado:** `TypedResult` (la comparación letra a letra que ya existe) y la explicación por reglas;
  - **final:** resumen con aciertos por terminación y *Practice the ones I missed*;
  - entrada desde `GrammarCard` (botón *Practice*) y desde `MoreSheet` → *Practice* → *Grammar Lab*.

### Pruebas

- `ConjugatorTest`: tabla de casos (regulares, contracciones `ㅏ+아→ㅏ`, `ㅗ+아→ㅘ`, `ㅜ+어→ㅝ`, `ㅣ+어→ㅕ`, `하다`, `ㄹ` delante de `ㄴ/ㅂ/ㅅ`, `으`, los 5 irregulares de la lista y los regulares que se les parecen). **Doradas:** si el web tiene `rag/conjugation.py` con más formas que `aeo_forms`/`past`, exportar sus salidas con `tools/golden/` como en las demás doradas;
- `EndingMatchTest`: los patrones de los 6 PDFs de `golden/grammar/*.json`, para ver cuántos se reconocen (objetivo ≥ 70 %);
- `DrillsTest`: con la misma semilla salen los mismos ejercicios; ningún `Produce` tiene `answers` vacío; las opciones de `Choose` no repiten la buena;
- `GrammarLabBehaviourTest` sobre la base dorada en memoria;
- Roborazzi de los tres tipos de ejercicio y del resultado, en claro y oscuro.

**Qué NO hacer:**
- conjugar con el modelo;
- preguntar verbos cuya irregularidad no se conoce;
- escribir intentos en `question_attempts` (sus doradas son del web).

---

## F4 — Listening Lab: dictado, shadowing y manos libres

**Objetivo:** entrenar el oído y la pronunciación con **frases enteras de tus notas**. Listen & Guess y Sound Detective solo trabajan con reconocer palabras.
**Esfuerzo:** M. **Depende de:** B1, B2 y B3. **Sin dependencias ni permisos nuevos** (`RECORD_AUDIO` ya está, D9-1; ver la decisión).

### Contenido

`BankSource.bank()` (`GameBank`) da las frases (`ItemKind.SENTENCE`, con traducción) y las palabras con sus ejemplos. `K/listening/ListeningPicker.kt` elige:
- **dictado:** frases de 2 a 8 palabras (por nivel);
- **shadowing:** frases cortas (≤ 20 sílabas, porque el reconocedor falla más con frases largas; PoC de la fase 9, `docs/poc/4-speech.md`);
- **manos libres:** las tarjetas **pendientes** (`FlashcardRepository.dueCards`), y si no hay, las débiles.

### Tres modos (`A/ui/listening/ListeningScreen.kt` + `ListeningViewModel.kt`)

1. **Dictado**
   - Suena la frase con `voice.speak(text, rate)`, con velocidad 0,7×, 1× o 1,2× (`UserPrefs.listeningRate`).
   - Botones *Replay* y *Slower*: la segunda vez suena a 0,7× si estaba a 1×.
   - Escribes lo que oyes y se compara con `CompareAnswer` (modo coreano, `plainKorean`): la diferencia letra a letra se ve con `TypedResult`, y suena otra vez la frase.
   - Se califica: `correct` si el `Verdict` es `Exact`; `Almost` cuenta como medio para la estadística, pero no como acierto.
2. **Shadowing**
   - Suena la frase, tocas el micrófono y repites.
   - `KoreanSpeech.listen(maxMs = 8_000)` → `compareSpoken(expected, candidates)` → `SpokenComparison.heardMarks/notesMarks` se pintan igual que en `SpeakingBlock.kt` (Quiz).
   - **Sin nota y con intentos ilimitados** (D9-6): solo celebra cuando coincide.
   - Se reutiliza la máquina de estados de `A/ui/quiz/Speaking.kt` (permiso pedido al tocar, *Processing*, errores `SpeechFailure`). **Antes, sacar lo común a `A/ui/components/speech/`**, sin cambiar el Quiz, comprobado con sus capturas.
3. **Manos libres**
   - Bucle: coreano → 3 s de pausa → inglés → 2 s → siguiente.
   - Se marca `FLAG_KEEP_SCREEN_ON` mientras dura (sin permiso). Botones grandes de pausa y *siguiente*.
   - Al bloquear la pantalla o salir de la app, se pausa. Las tarjetas **no** se califican (solo cuenta el tiempo).

### Datos

Tabla `listening_attempts(id, mode, item_key, correct, created_at, session_id)`, solo del teléfono, en B1:
- en shadowing, `correct` = coincidió;
- **nunca se guarda lo que dijiste ni el audio** (D9-1), solo el booleano.

Las cifras por frase alimentan F5.

### Decisión

🔒 **D-F4:** D9-1 permite el micrófono *«solo para la práctica hablada del Quiz»*. Shadowing (aquí), Speak It (F7), Journal por voz (F6) y Situations por voz (F9) lo usan con las mismas condiciones: se pide al tocar, nunca se guarda audio ni transcripción, y no va a logs. Hay que ampliar D9-1 y el comentario del manifiesto. **No es un permiso nuevo.**

### Pruebas

- `ListeningPickerTest`: longitudes por nivel; sin frases → el modo sale deshabilitado con su motivo;
- `ListeningViewModelTest` con `KoreanVoice` y `KoreanSpeech` falsos (los de las pruebas de Games y Quiz): *Replay* llama a `speak` con la velocidad correcta; la interrupción pausa; en manos libres no se llama a `review`;
- Roborazzi de los tres modos (claro y oscuro);
- Robolectric con la escala de animación a 0 (quitar animaciones).

**Qué NO hacer:**
- servicio en primer plano para el audio (pediría `FOREGROUND_SERVICE_MEDIA_PLAYBACK`);
- voces por red (`TtsKoreanVoice` ya lo impide);
- calificar el shadowing.

---

## F5 — My mistakes: cuaderno de errores y resumen semanal

**Objetivo:** que tus errores de todas partes se conviertan en **patrones** con un botón para practicarlos. También se cierra el uso 8 del plan (resumen semanal), que no está hecho: no hay uso `Weekly` en `D/ai/`.
**Esfuerzo:** M–L. **Depende de:** B1, B3 y B5; **mejor después de H-03** (`WordProgress`/`word_state`), que da la clave común de palabra.

### Clasificador por reglas

`K/mistakes/Patterns.kt` (nuevo, puro):

```kotlin
enum class PatternKind {
    ParticleBatchim,     // 은/는, 이/가, 을/를, 와/과, (으)로 cambiados por el 받침 (Particles.correctAfter)
    VowelHarmony,        // 아/어 equivocado (aeoForms)
    Contraction,         // 가아서 en vez de 가서
    Irregular,           // irregular aplicado como regular (Conjugator.commonMistakes)
    Tense,               // presente/pasado cambiado (past)
    Batchim,             // consonante final cambiada por otra parecida (lista de MinimalPairs.kt)
    Vowel,               // ㅓ/ㅗ, ㅐ/ㅔ… (MinimalPairs.kt)
    Spacing,             // solo espacios (CompareAnswer: Verdict.Almost con diferencias de espacio)
    WrongWord,           // otra palabra entera (la confusión se guarda: 비싸다 ↔ 싸다)
    Meaning,             // inglés equivocado (en ko_to_en)
}
data class Mistake(val source: String, val expected: String, val given: String, val wordKey: String?, val pointKey: String?, val at: Long)
data class Pattern(val kind: PatternKind, val label: String, val examples: List<Mistake>, val count: Int, val lastAt: Long)

fun classify(m: Mistake): PatternKind
fun patterns(mistakes: List<Mistake>, since: Long): List<Pattern>   // agrupa y ordena por frecuencia y recencia
```

El clasificador funciona sobre la descomposición en jamo (`indexesOf`) de las dos cadenas alineadas (`CompareAnswer.align`; hay que exponerla como `internal`/`public` con su prueba).

### Fuentes (lectura, sin tablas nuevas salvo las de F3, F4 y F6)

`D/mistakes/MistakesRepository.kt` junta `Mistake`s de:

| Fuente | Qué se lee |
|---|---|
| Quiz | `question_attempts` con `is_correct = 0`, más la pregunta (respuesta correcta, tipo y tema) |
| Flashcards | reseñas *Again* de `flashcard_reviews` (sin `given`: solo cuenta para `WrongWord` del propio término) y, con *Type the answer*, lo que se escribió si se guarda (ver la nota) |
| Games | `game_rounds` con `outcome = 'lost'` y sus `item_keys`; `game_item_progress.wrong` |
| Grammar Lab (F3) | `grammar_drill_attempts` con `correct = 0` |
| Listening (F4) | `listening_attempts` del dictado con `correct = 0` |
| Journal (F6) | correcciones aceptadas |

Todo con consultas dirigidas (SQL con `WHERE created_at >= :since`, siguiendo R-001), nunca con `.all()`.

> **Nota:** hoy *Type the answer* no guarda lo que escribiste. Para `Patterns`, una tabla solo del teléfono `typed_answers(flashcard_id, given, created_at)` (B1) es opcional; sin ella, Flashcards solo aporta *Again*.

### UI

`A/ui/mistakes/MistakesScreen.kt` + `MistakesViewModel.kt`:
- filtro de periodo: *7 days · 30 days · All*;
- tarjetas por patrón: título (*"은/는 after a consonant"*), contador, 2 o 3 ejemplos tuyos (`expected` frente a `given`, con `TypedResult`) y un botón **Practice this**, que según el tipo:
  - `ParticleBatchim` → ronda de Grammar Fixer (Games) con esas palabras, o un quiz `grammar_selection` del tema;
  - `VowelHarmony`, `Contraction`, `Irregular`, `Tense` → Grammar Lab (F3) con esos verbos y terminaciones;
  - `Batchim`, `Vowel` → Sound Detective o dictado (F4) con esas palabras;
  - `WrongWord`, `Meaning` → mazo nuevo con `createCustomDeck("Mistakes · <fecha>", words)`, o un quiz de esas palabras (`QuizProposal(words = …)`, como el Coach);
- en el Dashboard, una tarjeta *Your top mistake this week* en `WeaknessPanel`, con *See all*.

### Resumen semanal (uso 8 del plan)

- **Sin modelo:** `K/mistakes/WeeklyText.kt` hace una frase con plantilla a partir de `ProgressRepository.weekly()` (`Weekly`: palabras, acierto y tendencia) y el patrón principal: *"This week: 34 words, 82 % right (+6). Most common mistake: 은/는 after a consonant."*
- **Con Nano:** `D/ai/WeeklyAi.kt`, uso `AiUse.Weekly`. El prompt recibe **solo** esos hechos. El validador es `validFromFacts(text, facts, topics)`, el mismo de `CoachAi`: ningún número ni coreano que no esté en los hechos. Lleva caché por semana (`input` = hechos) y la marca `AiMark`.
- Va arriba del Dashboard, en *Today*, una vez por semana (lunes) o siempre visible (lo decide diseño).

### Pruebas

- `PatternsTest`: una tabla de pares `(expected, given) → PatternKind`, con ejemplos reales sacados de `golden/db/quiz_mistakes.json`;
- `MistakesRepositoryTest` sobre la base dorada (quiz) más filas sintéticas de Games y F3;
- `WeeklyAiTest` con modelo falso: un número inventado → rechazado → sale la plantilla;
- Roborazzi.

**Qué NO hacer:**
- cambiar cómo calcula hoy el Quiz su *Fix these weaknesses* (sus doradas);
- que Nano clasifique errores (lo hacen las reglas).

---

## F6 — Journal: diario en coreano con corrección

**Objetivo:** **escribir** en coreano, lo que hoy casi no se practica: *Write in Korean* del Quiz es palabra a palabra. Cada día, 3 a 5 frases propias con las palabras que te tocan.
**Esfuerzo:** L. **Depende de:** B1, B2, B3, B4 y B5. **Añade una dependencia:** `com.google.mlkit:genai-proofreading`.

### Por qué la Proofreading API

- Es de ML Kit GenAI y corre sobre el mismo Gemini Nano de AICore. Es gratis y no necesita `INTERNET` en la app: el adaptador LoRA lo descarga AICore, como a Nano.
- **Soporta coreano**, según su documentación (inglés, japonés, francés, alemán, italiano, español y coreano; entrada de menos de 256 tokens).
- Devuelve **una o más sugerencias ordenadas por confianza**, y tiene `InputType.KEYBOARD` y `InputType.VOICE`.
- La versión que documenta Google es `1.0.0-beta1`. **Fijarla** en `libs.versions.toml`, como `genaiPrompt`.

**Comprobar antes (tarea 0, en el OPPO, app QA):**
1. Tras añadir la dependencia, `python3 tools/ci/check_manifest.py` en debug y release: si mete `INTERNET` o `ACCESS_NETWORK_STATE`, ya se quitan con `tools:node="remove"` (manifiesto actual), pero hay que comprobar que **sigue funcionando en modo avión**.
2. `checkFeatureStatus()` con `Language.KOREAN`: `AVAILABLE`, `DOWNLOADABLE` o `UNAVAILABLE`.
3. Una tanda de 30 frases (10 bien, 10 con error de partícula y 10 con error de conjugación), igual que la PoC 2: aciertos, **falsos positivos** (cambia una frase que estaba bien), latencia p50/p90 y `BUSY`.
4. Resultado en `docs/poc/5-proofreading.md` y una decisión en `DECISIONS.md`. **Si los falsos positivos pasan del 10 %, la corrección de Nano no se muestra** y Journal queda solo con las reglas.

### 🔒 Decisión de principio

**D-F6:** hoy la regla es *«todo el coreano que se muestra sale de tus notas»*. Una corrección es coreano que escribe el modelo. Propuesta para aceptarla sin romper el espíritu:
- la corrección **solo** se muestra como **diff sobre tu frase**: qué sílabas cambia, no un texto nuevo suelto;
- validador `validCorrection(original, suggestion)`:
  - la distancia de edición en jamo es ≤ 30 % de la frase;
  - cada palabra de la sugerencia está en tu frase, o en tus notas (`NotesIndex` + `lemmas`), o se diferencia de una palabra tuya solo en la partícula o la terminación;
  - si no, se descarta;
- lleva siempre `AiMark` y el texto *Suggestion · not from your notes*;
- **nunca** se guarda como nota ni como fuente: solo en la entrada del diario, y solo si la aceptas.

### Retos del día

`K/journal/Prompts.kt` (puro):

```kotlin
data class JournalTarget(val wordKey: String, val korean: String, val english: String, val why: String) // "due today", "weak", "new"
fun targets(due: List<Word>, weak: List<Word>, recent: List<Word>, count: Int, seed: Long): List<JournalTarget>
```

Las palabras salen de las tarjetas pendientes, de las débiles (F5 o H-03) y de las palabras nuevas de la semana. Si hay un punto de gramática débil, se añade *"Try using -아서/어서"*.

### Corrección por capas (`D/journal/JournalChecker.kt`)

Para cada frase:
1. **Reglas (siempre, en menos de 100 ms):**
   - **partículas:** `particleSpots` sobre las palabras de la frase → `correctAfter` → aviso *"After 책 (consonant) use 은, not 는"*;
   - **palabras desconocidas:** cada palabra pasa por `lemmas(word, verbs)` y `NotesIndex`; si no está en tus notas, se marca *not in your notes* (no es un error, solo información);
   - **reto cumplido:** `hasForm(sentence, targetWord, aeoForms(target))`;
   - **frases parecidas de tus notas:** el mismo camino que Teacher *"Is this sentence correct?"* (`Teacher.compare`), con sus 1 o 2 frases más cercanas y su fuente.
2. **Proofreading (si D-F6 y la tarea 0 dicen que sí):**
   - `NanoGate.proofread(text) { suggestions -> suggestions.firstOrNull { validCorrection(text, it) } }`;
   - si la sugerencia es igual a tu frase (normalizada), se muestra **✓ Looks right**.
3. **Explicación (Prompt API, `AiUse.JournalExplain`):**
   - solo si hay sugerencia; prompt con tu frase y la sugerencia: *"Explain in English, in one or two sentences, what changed and why."*;
   - validador: inglés y coreano solo de las dos frases (la regla de `validMistake`).

### Datos

Tablas solo del teléfono (B1):

```kotlin
@Entity(tableName = "journal_entries", indices = [Index("day")])
data class JournalEntriesEntity(@PrimaryKey val id: Long, val day: Long /* epochDay local */, @ColumnInfo(name = "created_at") val createdAt: Long)

@Entity(tableName = "journal_sentences", indices = [Index("entry_id")])
data class JournalSentencesEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    val text: String,                       // tu frase (o la corregida, si la aceptaste)
    val original: String?,                  // tu frase antes de aceptar la sugerencia
    @ColumnInfo(name = "target_keys") val targetKeys: String,   // JSON con las palabras del reto que usó
    @ColumnInfo(name = "ai_corrected") val aiCorrected: Boolean,
)
```

> 🔒 **D-F6b:** el diario es **texto tuyo**. Fuera del respaldo se pierde al desinstalar. Hay dos opciones: que entre en el respaldo (habría que subir el formato a v2, con su prueba de ida y vuelta, y que el web lo ignore) o dejarlo fuera como Games. **Recomendación:** fuera por ahora, con un botón *Export journal* (`.txt` por SAF, como *Export backup*).

### UI

`A/ui/journal/JournalScreen.kt` + `JournalViewModel.kt`:
- **arriba:** los retos de hoy como chips (al usar una palabra, su chip se marca ✓);
- **campo de texto** de varias líneas, con `imePadding()` (lección de R-006), y micrófono opcional (`KoreanSpeech`; D-F4) que inserta lo reconocido para que lo edites;
- *Check*: debajo de cada frase, sus avisos (reglas), la sugerencia (diff con `Letter`/`Mark`, como `TypedResult`) y los botones *Accept* / *Keep mine*;
- **historial por días** (calendario compacto, reutiliza `CalendarMonthCompact`), con cada entrada de solo lectura;
- *Hear it*: `voice.speak` de tu frase (útil para oír cómo suena, no como modelo de pronunciación).

### Pruebas

- `JournalPromptsTest` (retos deterministas con semilla);
- `JournalCheckerTest`: partícula mal con aviso; palabra fuera de tus notas con aviso; con la corrección falsa, `validCorrection` rechaza una sugerencia que reescribe la frase entera y acepta un cambio de partícula;
- con el gate falso, un PDF en cola deja solo las reglas;
- `MigrationTest` y `BackupUnchangedTest`;
- Roborazzi.

**Qué NO hacer:**
- que el modelo escriba frases de ejemplo nuevas;
- guardar la sugerencia sin que la aceptes;
- mandar el texto a logs;
- llamar a la Proofreading API saltándose `NanoGate`.

---

## F7 — Juegos nuevos

**Objetivo:** cuatro juegos que cubren lo que los 13 de hoy no tocan: escribir por jamo con deducción, vocabulario en cruz, conjugar rápido y **hablar**.
**Esfuerzo:** M cada uno. **Depende de:** la arquitectura de Games (fase 8); Conjugation Runner depende de F3; Speak It, de D-F4. **D8-9 sigue: Games no usa Nano.**

### Cómo se añade un juego (lo común a los cuatro)

1. `K/games/GameModels.kt`: nueva entrada en `enum class GameType(gesture, skill, gentle, demanding, needsVoice)`. Para Speak It: `Gesture.Speak` y `Skill.Speaking` nuevos, y un campo `needsMic: Boolean = false`.
2. `K/games/rounds/<Juego>Rounds.kt`: genera una `RoundSpec` determinista con `roundRandom(seed, round)`.
3. `K/games/Evaluators.kt`: cómo se gana o se pierde la ronda.
4. `K/games/Games.kt`: `validItems(game, bank)` y `available(game, bank, voice)` (más `mic` para Speak It), con su mínimo de elementos. Si no llega, el juego no sale (regla de la fase 8, 4.2).
5. `K/games/Planner.kt` y `Selection.kt`: peso del juego y elección de elementos, sin cambios de lógica (solo datos).
6. `A/ui/games/minigames/<Juego>Game.kt` y sus textos en `A/ui/games/GameTexts.kt`; los personajes (Beom, Deung, Dal) reaccionan igual (`StageDirector`).
7. Pruebas: `FactoriesTest` (que genera bien), `EvaluatorsTest` y Roborazzi del juego en claro y oscuro. **Macrobenchmark de Games:** CPU por fotograma P90 ≤ 8 ms y P99 ≤ 12 ms (objetivo de la fase 8).

### Hangul Wordle (`GameType.HangulWordle`, `Gesture.Keyboard`, `Skill.Writing`)

- **Elemento:** una palabra tuya de 2 o 3 sílabas. Se descompone en jamo con `indexesOf` (sin la consonante final vacía) y todas las rondas de una sesión usan el mismo largo (por ejemplo, 5 o 6 jamo), para que la rejilla sea fija.
- **Pista inicial:** el significado en inglés (si no, sería adivinar a ciegas).
- **Intentos:** 6. Se escribe con `HangulKeys` en modo jamo (ya existe para Hangman) y el intento tiene que **formar sílabas válidas** (`HangulIme`).
- **Colores por jamo:** verde (en su sitio), amarillo (está en otra posición) y gris. Los colores salen del grupo `games` de `tokens.json`, y además del color va un icono o patrón (accesibilidad: no solo color).
- **Nivel:** 1 = muestra la primera sílaba; 2 = solo el significado; 3 = el significado con 5 intentos.
- **Evaluador:** el algoritmo de Wordle con letras repetidas (el amarillo cuenta las que quedan). Probarlo con casos de jamo repetidos (`ㅇ` inicial y final).

### Crossword (`GameType.Crossword`, `Gesture.Keyboard`, `Skill.Vocabulary`)

- **Generador** (`K/games/rounds/CrosswordRounds.kt`): 4 a 7 palabras tuyas en una cuadrícula de **sílabas** (no de jamo) de como mucho 7×7.
  - Algoritmo voraz con semilla: la palabra más larga en el centro y luego se cruzan otras por **sílaba compartida**; si en 200 intentos no caben 4, la ronda no se ofrece.
  - Pistas: el inglés de cada palabra, numeradas.
- **UI:** tocas una casilla y escribes la sílaba con `HangulKeys`. Al completar una palabra, se comprueba y suena (D10-3).
- **Nivel:** 1 = 4 palabras y una sílaba de pista en cada una; 3 = 7 palabras sin pistas.
- **Rendimiento:** la cuadrícula se dibuja en un solo `Canvas` y no con 49 `Box`.

### Conjugation Runner (`GameType.ConjugationRunner`, `Gesture.Correct`, `Skill.Grammar`, `demanding = true`)

- Usa el motor de F3: arriba están el verbo y la terminación (*"먹다 · -아요/어요"*) y caen 3 o 4 formas (`conjugate` y `commonMistakes`); tocas la buena antes de que lleguen abajo.
- 10 formas por ronda: se gana con ≥ 8 y la velocidad sube con el nivel.
- **Quitar animaciones:** sin caída; las formas aparecen quietas con un contador (`reduceMotion()` ya existe).
- Solo con verbos y terminaciones que `conjugate` sabe (regla de F3).

### Speak It (`GameType.SpeakIt`, `Gesture.Speak`, `Skill.Speaking`, `needsMic = true`)

- Aparece el inglés y lo dices en coreano. `KoreanSpeech.listen(maxMs = 5_000)` → `compareSpoken(expected, candidates)`.
- **D9-6 manda:** sin «fallo». Si coincide, se celebra y suena la palabra. Si no, se ve **lo que entendió el teléfono** frente a lo de tus notas (marcas de `SpokenComparison`) y puedes repetir. La ronda acaba con acierto o con *Skip*.
- `outcome = "neutral"` cuando acabas con *Skip*, para que no cuente como derrota (ya existe ese valor: `graded()` lo excluye).
- `available` exige `SpeechAvailability.Ready` (o `NeedsDownload`, mostrando cómo descargarlo, como en el Quiz).
- El permiso se pide al tocar el micrófono por primera vez. Si lo niegas, el juego sale del sorteo hasta que lo concedas en Ajustes.

**Qué NO hacer:**
- efectos de sonido (D8-5);
- vibrar;
- Nano en Games (D8-9);
- calificar como fallo lo hablado (D9-6).

---

## F8 — Reader: lector interactivo de tus apuntes

**Objetivo:** leer tus PDFs dentro de la app con **ayuda al tocar**: tocas una palabra y ves qué es según tus notas. Es una forma de repasar que no es pregunta y respuesta.
**Esfuerzo:** M. **Depende de:** B1, B2 y B3; F5/H-03 para colorear por estado (opcional).

### Fuente del texto

`D/documents/DocumentFiles.extracted(documentId)` ya guarda el texto extraído por página (JSON, `writeExtracted`). `D/reader/ReaderRepository.kt` lo lee y lo pasa a `ReaderPage(page: Int, lines: List<ReaderLine>)`, con los títulos marcados (`:core:pdf` ya distingue títulos con `Headings.kt`; si el JSON no los guarda, se usan los `topic` de los fragmentos de `Chunker`).

### Búsqueda al tocar (`K/reader/Lookup.kt`, puro)

```kotlin
data class Token(val text: String, val start: Int, val end: Int, val hangul: Boolean)
fun tokens(line: String): List<Token>                         // HANGUL_WORDS, como Teacher

sealed interface Gloss {
    data class Entry(val korean: String, val english: String, val source: String, val examples: List<Pair<String, String>>) : Gloss
    data class Inflected(val surface: String, val lemma: String, val entry: Entry?, val particle: String?, val ending: String?) : Gloss
    data class Grammar(val pattern: String, val meaning: String) : Gloss
    data class NotFound(val surface: String, val sentences: List<String>) : Gloss   // como R1-01: dónde más aparece
}
fun lookup(token: String, index: NotesIndex, verbs: Set<String>, grammar: List<GrammarPoint>): Gloss
```

El orden de búsqueda es:
1. entrada exacta en `NotesIndex`;
2. quitar la partícula final (`Particles`: 은/는/이/가/을/를/에/에서/의/도/만/와/과/로/으로…) y buscar;
3. `lemmas(word, verbs)` para verbos conjugados;
4. un punto de gramática cuyo patrón termine la palabra (`hasForm`);
5. si nada encaja, `NotFound` con las frases donde aparece (el camino de R1-01 en Teacher).

**Rendimiento:** el índice se construye una vez por documento (`NotesIndex` ya se cachea en Teacher; reutilizar la misma instancia, ver DECISIONS 2026-11-09 *Caché en memoria de los fragmentos*).

### UI (`A/ui/reader/ReaderScreen.kt` + `ReaderViewModel.kt`)

- `LazyColumn` por página, con `key = page`.
- Cada línea es un `Text` con un `AnnotatedString` y una `LinkAnnotation.Clickable` por palabra coreana (Compose BOM 2026.09 lo soporta). **No** un `Text` por palabra, para no recomponer cientos de nodos.
- Al tocar: hoja inferior (`ModalBottomSheet`, como `DocumentsSheet`) con la `Gloss`, 🔊 de la palabra y de la frase, y *Add to deck* (`createCustomDeck` o añadir a uno existente; hace falta `FlashcardRepository.addWords(deckId, words)` si no existe).
- Pulsación larga en una línea: *Explain this sentence* (abajo), *Hear sentence* o *Copy*.
- **Opcional (con H-03):** subrayado suave según el estado de la palabra (nueva, aprendida o difícil), con tokens de diseño.
- Accesibilidad: TalkBack lee la línea entera; cada palabra es un enlace con su `contentDescription`.

### IA: *Explain this sentence*

- `AiUse.SentenceExplain`, `D/ai/ReaderAi.kt`;
- el prompt lleva la frase, su traducción de tus notas (si la hay) y las `Gloss` encontradas: *"Explain in English the grammar and word order of this Korean sentence for a learner."*;
- validador: inglés; coreano **solo** de la frase; ≤ 3 frases; caché por frase;
- sin modelo: se muestran las `Gloss` de cada palabra en lista (ya es una explicación).

### Datos

Ninguna tabla obligatoria. `practice_sessions(kind = "reader")` cuenta el tiempo (B2), y se cuenta como estudio solo si hubo al menos 3 búsquedas, para no sumar tiempo con la pantalla abierta.

### Pruebas

- `LookupTest`: `학교에서` → `Entry(학교)` más la partícula `에서`; `먹었어요` → `Inflected(lemma = 먹다)`; una palabra que no está → `NotFound` con sus frases. Con los 6 PDFs de `golden/`;
- `ReaderViewModelTest` (carga, página, búsqueda);
- macrobenchmark corto: desplazarse por un PDF de 30 páginas (`tools/fixtures/long_pdf.py`) sin saltos de fotogramas por encima del objetivo;
- Roborazzi.

**Qué NO hacer:**
- volver a leer el PDF con MuPDF (se usa el texto ya extraído);
- traducir con el modelo palabras que no están en tus notas.

---

## F9 — Situations: práctica de situaciones

**Objetivo:** usar lo que sabes **en contexto** (café, metro, presentarte, compras, médico…) sin que el modelo invente coreano.
**Esfuerzo:** L. **Depende de:** B1, B2, B3 y B5; D-F4 si se responde hablando. **Necesita una medición previa en el OPPO** (abajo).

### Catálogo de situaciones fijo y sin modelo

`K/situations/Scenarios.kt`: unas 20 situaciones escritas a mano, en inglés:

```kotlin
data class Scenario(
    val id: String, val category: String,          // categorías de K/coach/Categories.kt (food, transport, shopping…)
    val title: String,                              // "At a café"
    val tasks: List<TaskSpec>,                      // 3–5 pasos
)
data class TaskSpec(
    val id: String,
    val english: String,                            // "Order two iced americanos."
    val needs: List<Need>,                          // qué tiene que llevar tu frase para contar
)
sealed interface Need {
    data class WordOfCategory(val category: String) : Need          // una palabra tuya de esa categoría (byTheme/belongs)
    data class Pattern(val forms: List<String>) : Need              // "주세요", "얼마예요", "-고 싶어요"… (hasForm)
    data object Number : Need                                       // número coreano (lista fija 하나/둘/한/두…, 일/이/삼…)
}
```

Una situación **solo sale si tus notas la cubren**: hay al menos una palabra de su categoría (`byTheme`) y sus patrones aparecen en tus notas (`NotesSearch`). Así nunca se te pide algo que no has estudiado.

### Una vuelta de la práctica

1. Se ve la tarea en inglés. Con Nano (`AiUse.SituationTask`), opcionalmente una variación, solo en inglés: *"Now the barista asks if you want it hot. Say you want it iced."*. El validador no acepta **ningún** carácter hangul; sin modelo, se usa el texto fijo.
2. Respondes escribiendo (teclado del sistema) o hablando (`KoreanSpeech`, D-F4).
3. **Comprobación por reglas** (`K/situations/Check.kt`):
   - cada `Need` se busca en tu frase (`hasForm` + `aeoForms`, `belongs` para la categoría, la lista de números);
   - partículas con `particleSpots` + `correctAfter`;
   - resultado: lista de ✓/✗ por `Need` y avisos de partícula.
4. **Referencia de tus notas:** la frase más cercana de tus notas para esa tarea (`NotesSearch` con las palabras y patrones del `TaskSpec`), con su fuente. *"From your notes: 아이스 아메리카노 두 잔 주세요. [Vocab café y comida, p. 2]"*.
5. **Juez (opcional):** solo si las reglas dicen que falta algo, `AiUse.SituationJudge` puede **aprobar**, igual que el juez actual. Prompt: la tarea en inglés y tu frase, con *"Does the Korean sentence do the task? Answer YES or NO on the first line, then one short reason in English."*. Validador:
   - primera línea `YES`/`NO`;
   - razón en inglés;
   - el coreano solo puede ser de tu frase;
   - solo puede convertir un ✗ en *Accepted · AI*, **nunca** al revés.

### 🔒 Medición previa (antes de activar el juez)

El juez actual se midió solo con inglés (PoC 2: 0 % de falsos positivos). Juzgar **coreano** es otra cosa:
- tanda de 40 casos en el OPPO (20 frases que cumplen la tarea con otras palabras y 20 que no);
- si los falsos positivos pasan del 5 %, el juez no se activa y Situations funciona solo con reglas;
- resultado en `docs/poc/6-situations-judge.md`.

### Datos y UI

- tabla `situation_attempts(id, scenario_id, task_id, passed, by_ai, created_at, session_id)`, solo del teléfono. **No** se guarda tu frase (no hace falta para nada);
- `A/ui/situations/SituationsScreen.kt` (lista por categoría, con las que no tienes cubiertas en gris y el motivo: *Add notes about food to unlock*) y `SituationScreen.kt` (la conversación como burbujas, reutilizando el estilo de `TeacherScreen`: tarea a la izquierda y tu frase a la derecha);
- al terminar: resumen y *Make a deck from this situation's words* (`createCustomDeck`).

### Pruebas

- `ScenariosTest`: con la base dorada, qué situaciones salen y cuáles no;
- `CheckTest`: tabla de frases → `Need` cumplidos;
- `SituationAiTest` con modelo falso: una tarea con hangul se rechaza; el juez nunca convierte ✓ en ✗;
- Roborazzi.

**Qué NO hacer:**
- diálogos en coreano escritos por el modelo;
- calificar con el modelo sin la medición previa;
- guardar lo que dijiste.

---

## F10 — Goals y Exam: metas con fecha y simulacro

**Objetivo:** estudiar hacia una fecha (*«Gramática semana 14 lista para el 15 de diciembre»*) y comprobarlo con un simulacro cronometrado.
**Esfuerzo:** M (Goals) + M (Exam). **Depende de:** B1, B2 y B3; **H-03** para «dominada» (si no, se usa una definición provisional); F4 para la sección de escucha.

### Goals

**Datos** (solo teléfono, B1):

```kotlin
@Entity(tableName = "goals")
data class GoalsEntity(
    @PrimaryKey val id: Long,
    val title: String,
    @ColumnInfo(name = "document_ids") val documentIds: String,   // JSON; vacío = todos
    @ColumnInfo(name = "target_day") val targetDay: Long,         // epochDay local
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "done_at") val doneAt: Long?,
)
```

**Plan (`K/goals/GoalPlan.kt`, puro):**

```kotlin
data class GoalState(val total: Int, val mastered: Int, val learning: Int, val unseen: Int, val daysLeft: Int)
data class DailyPlan(val newCards: Int, val reviews: Int, val onTrack: Boolean, val message: String)
fun plan(state: GoalState, avgReviewsPerNewCard: Double = 4.0): DailyPlan
```

- **«Mastered»:** la definición de H-03 (`interval_hours ≥ 504` y sin *Again* en las últimas 5). Sin H-03, se usa provisionalmente `repetitions ≥ 3`. 🔒 Se anota.
- `newCards = ceil(unseen / max(1, daysLeft - 3))`: se dejan 3 días finales solo de repaso.
- `onTrack`: compara el avance de los últimos 7 días con el necesario.
- **Dónde se ve:**
  - en el Dashboard, en *Today* (`TodayHero`), una línea por meta activa: *"Week 14 grammar · 9 days left · 6 new cards/day · on track"*;
  - en `CoachEngine`: si hay meta, *What should I study?* la prioriza (los hechos de la meta entran en los `facts` del Coach, y `CoachAi` los redacta con `validFromFacts`).
- **UI:** `A/ui/goals/GoalsScreen.kt`: lista de metas y *New goal* (título, documentos con `DocumentsSheet`, fecha con el `DatePicker` de Material 3). Al crear una meta, si no hay tarjetas de esos documentos, se ofrece *Create cards* (`createCards`).

### Exam

**Estructura (`K/exam/ExamSpec.kt`):**

```kotlin
data class Section(val name: String, val types: List<String>, val count: Int, val seconds: Int)
val DEFAULT_EXAM = listOf(
    Section("Vocabulary", listOf("vocab_recognition", "en_to_ko", "ko_to_en"), 10, 300),
    Section("Grammar",    listOf("grammar_selection", "fill_blank"), 8, 300),
    Section("Listening",  listOf("dictation"), 5, 300),       // F4; sin voz, la sección no sale
    Section("Writing",    listOf("write_ko"), 5, 300),
)
```

- **Preguntas:** las secciones de vocabulario, gramática y escritura usan **`QuizGenerator` tal cual**, con los documentos de la meta. Cada sección es un `quiz_run` normal, así que el Quiz, su historial y *Fix these weaknesses* las ven como siempre, sin cambiar doradas.
- **Escucha:** no es un tipo del Quiz (no se toca `QUIZ_TYPES`). Es el componente de dictado de F4 con 5 frases de esos documentos, y su resultado va a `listening_attempts`.
- **Tiempo:**
  - por sección, en el `ExamViewModel`; al acabar el tiempo, las preguntas sin responder cuentan como no respondidas (sin intento guardado);
  - con la pantalla en segundo plano, el reloj se pausa (nada de penalizar por una llamada);
  - el estado sobrevive a la muerte del proceso, como R-008: `Screen.Exam` guarda `examRunId` y `ExamViewModel.open` reanuda en la sección y pregunta exactas.
- **Datos** (solo teléfono):

  ```kotlin
  @Entity(tableName = "exam_runs")
  data class ExamRunsEntity(
      @PrimaryKey val id: Long,
      @ColumnInfo(name = "goal_id") val goalId: Long?,
      @ColumnInfo(name = "quiz_run_ids") val quizRunIds: String,      // JSON, una por sección del Quiz
      @ColumnInfo(name = "section_scores") val sectionScores: String, // JSON {name: [correct, total]}
      @ColumnInfo(name = "started_at") val startedAt: Long,
      @ColumnInfo(name = "ended_at") val endedAt: Long?,
  )
  ```

- **Resultados:** nota por sección, comparación con el simulacro anterior de la misma meta, *Ready for the exam* (todas las secciones ≥ 80 %) y botones a F5 (*Practice your mistakes from this exam*).
- **UI:** `A/ui/exam/ExamScreen.kt`. Reutiliza `QuestionScreen` dentro de cada sección (su contenido, sin su barra) y añade la cabecera con sección y tiempo. Termina con `ExamResultsScreen.kt`.

### Pruebas

- `GoalPlanTest`: tabla de estados → plan, con el último día, sin días y meta cumplida;
- `ExamViewModelTest`: secciones en orden; el tiempo agotado cierra la sección; la reanudación tras recrear el ViewModel sigue en la misma pregunta;
- las doradas de Quiz siguen en verde (las secciones son quizzes normales);
- Roborazzi.

**Qué NO hacer:**
- un tipo de pregunta nuevo en `QUIZ_TYPES` (rompería la paridad con el web y el respaldo);
- penalizar el tiempo con la app en segundo plano;
- mezclar los números de la meta con los del Dashboard antes de que H-03 fije «dominada».

---

## 12 · Orden, dependencias y migraciones

| # | PR | Contenido | Esfuerzo | Depende de | 📱 OPPO |
|---|---|---|---|---|---|
| 0 | `docs/funciones` | Este plan, fichas de diseño y decisiones (sección 13) | S | — | no |
| 1 | `practica/base` | B1 (`practice_sessions`), B2, B3 (entradas sin pantallas), B5 | M | 0 | no |
| 2 | `funciones/recordatorio` | F1, recordatorio | S | 1 | sí (notificación real) |
| 3 | `funciones/flashcards-audio` | F2: audio y modos (sin pista) | M | 1 | sí (voz) |
| 4 | `funciones/conjugador` | F3: motor y doradas, sin pantalla | L | — | no |
| 5 | `funciones/grammar-lab` | F3: pantalla y `grammar_drill_attempts` | M | 1, 4 | sí |
| 6 | `funciones/listening` | F4 | M | 1 | sí (voz y micrófono) |
| 7 | `funciones/juegos-1` | F7: Hangul Wordle y Crossword | M | — | sí (benchmark) |
| 8 | `funciones/juegos-2` | F7: Conjugation Runner y Speak It | M | 4, D-F4 | sí |
| 9 | `funciones/errores` | F5 y resumen semanal | M–L | 1, 5, 6 (mejor tras H-03) | sí (Nano) |
| 10 | `funciones/reader` | F8 | M | 1 | sí |
| 11 | `funciones/widget` | F1, widget | M | 2, D-F1b | sí |
| 12 | `poc/proofreading` | F6, tarea 0 (medición) | S | B4 | sí |
| 13 | `funciones/journal` | F6 | L | 1, 12, D-F6 | sí |
| 14 | `funciones/pista` | F2: pista de memoria (`ai_hints`) | S | 1 | sí (Nano) |
| 15 | `poc/situations-judge` | F9, medición del juez | S | — | sí |
| 16 | `funciones/situations` | F9 | L | 1, 15 | sí |
| 17 | `funciones/metas-examen` | F10 | M+M | 1, 6, H-03 | sí |

**Por qué este orden:**
- primero lo barato que más ayuda a la constancia (recordatorio y audio en tarjetas);
- luego lo que más enseña (Grammar Lab y Listening);
- luego lo que junta todo (errores);
- al final lo que depende de mediciones o decisiones (Journal, Situations y metas).

**Tablas nuevas (todas solo del teléfono, en `D/db/PracticeTables.kt`, fuera de `BackupSchema`):**

| Tabla | PR | Migración |
|---|---|---|
| `practice_sessions` | 1 | v n → n+1 |
| `grammar_drill_attempts` | 5 | +1 |
| `listening_attempts` | 6 | +1 |
| `typed_answers` (opcional) | 9 | +1 |
| `journal_entries`, `journal_sentences` | 13 | +1 |
| `ai_hints` | 14 | +1 |
| `situation_attempts` | 16 | +1 |
| `goals`, `exam_runs` | 17 | +1 |

`n` = la versión vigente al empezar (hoy es 5; si H-03 fase B entra antes, 6). Cada migración:
- solo hace `CREATE TABLE IF NOT EXISTS` y sus `CREATE INDEX`;
- su esquema se exporta;
- su `MigrationTest` comprueba que las 18 tablas del respaldo siguen idénticas (sha256, como `PhoneMigrationCheck`);
- extiende `BackupUnchangedTest`.

**Dependencias nuevas:**

| Dependencia | Para | Offline | Coste | Licencia | Alternativa nativa |
|---|---|---|---|---|---|
| `com.google.mlkit:genai-proofreading` (beta, fijada) | F6 | Sí (AICore; el adaptador lo baja el sistema) | gratis | Términos de ML Kit (los mismos que `genai-prompt`) | Solo reglas, sin sugerencia de corrección |
| `androidx.glance:glance-appwidget` (**opcional**) | F1, widget | Sí | gratis, ~1 MB | Apache-2.0 | `RemoteViews` (recomendado) |

Nada más: voz, reconocimiento, alarmas, notificaciones, Room, Compose y Nano ya están.

---

## 13 · Decisiones que hacen falta

| ID | Pregunta | Recomendación |
|---|---|---|
| D-B2 | ¿La práctica de las pantallas nuevas cuenta como estudio (racha, calendario y tiempo)? | Sí, como Games (D8-4) |
| D-F1a | ¿Recordatorio sin `RECEIVE_BOOT_COMPLETED` (abrir la app una vez tras reiniciar) o con él? | Sin el permiso |
| D-F1b | ¿Se acepta un receptor `exported` para el widget? | Sí, solo con `APPWIDGET_UPDATE` y revisado con la skill de *intents* |
| D-F4 | ¿Ampliar D9-1: el micrófono también en Listening, Speak It, Journal y Situations, con las mismas condiciones? | Sí |
| D-F6 | ¿Se aceptan correcciones de Nano (coreano del modelo) como diff sobre tu frase, validadas y marcadas? | Sí, tras la medición (falsos positivos ≤ 10 %) |
| D-F6b | ¿El diario entra en el respaldo (formato v2) o queda fuera con *Export journal*? | Fuera, con exportación a `.txt` |
| D-F9 | ¿Juez de Situations con Nano sobre coreano? | Solo si la medición da ≤ 5 % de falsos positivos; si no, solo reglas |
| D-F10 | Definición de «dominada» para las metas | La de H-03 (pregunta 2 de `AUDIT_2.md`) |
| D-F7 | ¿Speak It cuenta para «mastered» en `game_item_progress`? | No: solo suma `seen` (D9-6, sin nota) |

---

## 14 · Riesgos

| Riesgo | Cómo se cubre |
|---|---|
| La Proofreading API (beta) cambia, no soporta bien el coreano o mete `INTERNET` | Tarea 0 con medición, versión fijada, `check_manifest.py` en CI y modo avión; Journal funciona solo con reglas |
| Nano muestra coreano inventado en las funciones nuevas | Todo pasa por `NanoGate` y por un validador con la regla común (B5); pruebas JVM con salidas malas |
| `BUSY` de AICore al usar más funciones | La puerta única (B4) también para Proofreading; las pruebas de R-013 se extienden |
| El conjugador da formas equivocadas con irregulares | Lista cerrada; «si no se sabe, no se pregunta»; tabla de casos y doradas del web |
| Demasiadas pantallas nuevas, y la app se vuelve confusa | Puntos de entrada agrupados (*Practice* en `MoreSheet`) y fichas de diseño antes de cada PR; ninguna función obligatoria |
| Rendimiento en Reader (PDF largo) y Crossword | `AnnotatedString` con enlaces en lugar de un nodo por palabra; un `Canvas` para la cuadrícula; macrobenchmark |
| Números contradictorios entre Dashboard, metas y errores | F5 y F10 después de H-03; hasta entonces, definiciones provisionales anotadas en DECISIONS |
| El respaldo crece o cambia sin querer | Todas las tablas fuera de `BackupSchema`; `BackupUnchangedTest` en cada PR |
| Batería con manos libres y la pantalla encendida | Solo con la app delante y `FLAG_KEEP_SCREEN_ON`; se para al bloquear; medir con `batterystats` (pendiente desde el README) |
