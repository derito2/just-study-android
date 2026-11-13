# Fase 8 — Games: arquitectura y plan de implementación

> **Para Claude Code:** este documento es el plan de la fase entera. Tres PR (A: núcleo sin UI; B: pantalla y juegos; C: audio, cifras y pulido), un commit por tarea, **sin mergear sin la confirmación del dueño**. Empieza cada tarea por sus pruebas (`superpowers:test-driven-development`). Skills: `jetpack-compose-performance`, `android-permissions-security`, `android-intent-security`, `edge-to-edge`, `superpowers:verification-before-completion`.
>
> **Diseño:** `design/games.md` (pantalla, escenario, movimiento) y `design/games-minigames.md` (los 13 juegos). Si algo de aquí contradice al diseño en lo visual, manda el diseño; en datos y reglas, manda este plan. Si falta algo, **para y pregunta**: no rediseñes.
>
> 📱 **Teléfono:** solo en las tareas marcadas, y siempre en la app QA (`com.dero.juststudykorean.qa`) con el respaldo del dueño. Nunca en su app.

**Objetivo:** una pantalla Games con sesiones infinitas de 13 minijuegos hechos con el contenido de los PDF del dueño, sin internet, sin modelo y sin dependencias nuevas.

**Origen:** `JustStudyKorean_Games_y_Quiz_Oral_Especificacion.md` (el encargo del dueño, partes I y III).

## 0 · Decisiones del dueño que hacen falta antes de empezar

Anótalas en `docs/DECISIONS.md` con su respuesta. **La columna *Propuesta* es lo que este plan da por hecho; si el dueño dice otra cosa, se cambia el plan antes de programar.**

| ID | Pregunta | Propuesta de diseño | Por qué |
|---|---|---|---|
| D8-1 | ¿Por dónde se entra a Games? | Fila **Games** en MoreSheet, la primera | La barra inferior ya tiene 5 destinos (el máximo de Material 3 y de 411 dp con etiquetas de 12 sp). Alternativa: cambiar un destino de la barra. |
| D8-2 | Hangman: ¿faroles en vez de horca? | Sí, tres faroles | Tres fallos = tres trazos de horca, muy pobre; los faroles salen del arte de las pantallas de carga. |
| D8-3 | ¿Las cifras de Games van en el respaldo? | No. Tablas solo del teléfono (como `notes_fts`), con clave por texto coreano | El respaldo `.jsbackup.zip` v1 es el contrato con la web y cambiarlo es condición de parada. Con clave por texto, las cifras sobreviven a reprocesar y a importar. Se pierden al desinstalar. |
| D8-4 | ¿Jugar cuenta como estudiar (racha, calendario, *Study time*)? | Sí, sumando las sesiones de Games, **después** de R1-06 | Coherente con R1-06 (las tarjetas pasan a contar). No toca las 18 tablas. |
| D8-5 | ¿Sonidos de efecto? | No. El único audio es la voz coreana | No hay vibración por decisión del dueño; los efectos pedirían recursos nuevos. La especificación los da como opcionales. |
| D8-6 | Sound Detective: ¿lista fija de pares mínimos cuando las notas no dan? | Sí, 21 grupos, marcados **Extra · not from your notes** | Las notas rara vez tienen dos palabras que cambien en un solo sonido. Sin la lista, el modo A casi nunca saldría. |
| D8-7 | Grammar Fixer: ¿solo partículas equivocadas por el 받침? | Sí en esta fase | Es el único error que una regla puede garantizar como incorrecto y con una sola corrección (ver 4.9). |
| D8-8 | Audio: el plan original decía «sin audio» | Se permite la **voz del sistema sin red** en dos juegos | Lo pide el encargo. No añade permisos; añade una entrada `<queries>` en el manifiesto. |
| D8-9 | ¿Gemini Nano en Games? | No se usa en esta fase | Nada lo necesita; el modelo no corre con un PDF en cola ni con prisa (plazos de 2–6 s por llamada). Posible más adelante: proponer categorías para Word Sort, siempre validadas. |

**Sin dependencias nuevas.** Animaciones con Compose (`Animatable`, `Canvas`, `graphicsLayer`); sin Lottie. Voz con `android.speech.tts` (plataforma). Hangul en Kotlin propio.

## 1 · Lo que ya existe y se reutiliza

| Necesidad | Lo que hay | Dónde |
|---|---|---|
| Contenido de los PDF | `NotesSource.chunks(doc)` (con caché), `extractPairs`, `extractGrammar` | `:core:data/notes`, `:core:korean/Pairs.kt`, `GrammarExtract.kt` |
| Palabras sin repetir entre documentos | `FlashcardRepository.mergedWords` (lógica a imitar; es `internal`) | `:core:data/flashcards` |
| Normalizar y comparar coreano | `norm`, `nfc`, `isCorrect("write_ko", …)`, `KOREAN_ALTERNATIVES`, `englishSenses` | `:core:korean/Text.kt` |
| Partes de una sílaba | `split` / `join` (privadas en `KoreanForms.kt`) | se sustituyen por la API pública de 3.1 y `KoreanForms` pasa a usarla, sin cambiar su comportamiento (doradas) |
| Categorías | `CATEGORIES`, `belongs`, `categoryOf` | `:core:korean/coach/Categories.kt` |
| Azar reproducible | `PyRandom` (o `kotlin.random.Random(seed)`: aquí no hay dorada del web que igualar, vale cualquiera de los dos; usa `Random(seed)`) | `:core:korean` |
| Sesiones y tiempo | `AppClock`, patrón de `attachSession` (`SESSION_IDLE` = 30 min) | `:core:data/time`, `progress` |
| Tablas solo del teléfono | patrón `AppTables.kt` + `APP_TABLES` en `tools/data/gen_room_entities.py` | `:core:data/db` |
| Navegación a pantalla completa | `Screen` + `AppNavState.push` | `app/ui/navigation` |
| Tema, tokens, botones, estados, hojas | `JustStudyTheme`, `Tokens` (generado), `StickerButton`, `States`, `JsDialog`, `MoreSheet`, `ThemeSwitch`, `LucideIcon`, `KoSide` | `app/ui/theme`, `app/ui/components` |
| Giro de carta sin recomponer | `FlipCard` | `app/ui/components/Flashcard.kt` |
| Movimiento y sus interruptores | `Motion`, `reduceMotion()`, `LocalFrozenMotion` | `app/ui/theme/Motion.kt` |
| Capturas y pruebas de pantalla | Roborazzi + `GoldenApp` | `app/src/test` |
| Preferencias | `UserPrefs` (DataStore) | `:core:data/prefs` |

**Lo que no existe y se crea:** todo lo de las secciones 2 a 6. **No hay recursos de localización:** los textos de la app están en Kotlin, en inglés (así es todo el proyecto); Games sigue igual.

## 2 · Estructura

Sin módulos Gradle nuevos (Mac de 8 GB; plan, punto 3).

```
:core:korean  (Kotlin puro, pruebas JVM)
  korean/hangul/Hangul.kt          descomponer, componer, tablas, forma del bloque
  korean/hangul/HangulIme.kt       autómata 두벌식 del teclado propio
  korean/games/GameModels.kt       GameType, Skill, Gesture, GameItem, GameBank, RoundSpec (sealed), Outcome, RoundResult
  korean/games/GameBankBuilder.kt  chunks → GameBank
  korean/games/rounds/*.kt         una fábrica pura por juego: (bank, level, history, Random) → RoundSpec?
  korean/games/Evaluators.kt       comprobaciones puras de cada juego
  korean/games/GameSelector.kt     qué juego toca
  korean/games/ItemPicker.kt       qué contenido toca (pesos por aciertos y fallos)
  korean/games/Particles.kt        reglas de Grammar Fixer
  korean/games/MinimalPairs.kt     pares de las notas + lista fija

:core:data
  data/games/GameContentProvider.kt  NotesSource → GameBank (caché en memoria)
  data/games/GameTables.kt           entidades y DAO solo del teléfono + MIGRATION_4_5
  data/games/GameRepository.kt       guardar rondas, progreso por elemento, cifras
  data/games/GameSessionEngine.kt    máquina de estados de la sesión (corrutinas, sin Compose)
  data/audio/KoreanVoice.kt          interfaz + TtsKoreanVoice

:app
  ui/games/GamesScreen.kt, GamesViewModel.kt
  ui/games/stage/   GameStage, MoonTimer, StageProps (faroles, pétalos), GameTopBar, Board,
                    FeedbackRibbon, PauseSheet, SessionSummary, Opening
  ui/games/pieces/  Tile, Slot, SyllableFrame, HangulKeyboard, ReplayButton, VoiceBars
  ui/games/minigames/  un archivo por juego (13)
```

**Reglas de arquitectura (de la especificación, punto 9):**

- Un minijuego es un composable `XGame(spec: RoundSpec.X, paused: Boolean, onResult: (RoundResult) -> Unit, stage: StageScope)`. Guarda su estado de interacción con `rememberSaveable` donde sea barato. **No sabe qué juego viene después** ni toca Room.
- La UI nunca ve Room: habla con `GamesViewModel`, que habla con `GameSessionEngine`.
- `GameSessionEngine`, el selector, las fábricas y los evaluadores **no importan Compose ni `android.*`** (el motor está en `:core:data` solo porque usa el repositorio; su lógica es Kotlin puro).
- Todo lo que usa azar recibe un `Random`. **Con la misma semilla, el mismo banco y el mismo historial, sale la misma sesión** (prueba obligatoria).
- El contenido original (los `extracted/`, las 18 tablas) **no se escribe nunca** desde Games.

## 3 · Hangul

### 3.1 · `Hangul.kt`

Fórmula: `sílaba = 0xAC00 + (inicial × 21 + vocal) × 28 + final`, con las tablas estándar de Unicode:

- **Iniciales (19):** ㄱ ㄲ ㄴ ㄷ ㄸ ㄹ ㅁ ㅂ ㅃ ㅅ ㅆ ㅇ ㅈ ㅉ ㅊ ㅋ ㅌ ㅍ ㅎ
- **Vocales (21):** ㅏ ㅐ ㅑ ㅒ ㅓ ㅔ ㅕ ㅖ ㅗ ㅘ ㅙ ㅚ ㅛ ㅜ ㅝ ㅞ ㅟ ㅠ ㅡ ㅢ ㅣ
- **Finales (28):** ∅ ㄱ ㄲ ㄳ ㄴ ㄵ ㄶ ㄷ ㄹ ㄺ ㄻ ㄼ ㄽ ㄾ ㄿ ㅀ ㅁ ㅂ ㅄ ㅅ ㅆ ㅇ ㅈ ㅊ ㅋ ㅌ ㅍ ㅎ
- **Finales compuestos:** ㄳ = ㄱㅅ · ㄵ = ㄴㅈ · ㄶ = ㄴㅎ · ㄺ = ㄹㄱ · ㄻ = ㄹㅁ · ㄼ = ㄹㅂ · ㄽ = ㄹㅅ · ㄾ = ㄹㅌ · ㄿ = ㄹㅍ · ㅀ = ㄹㅎ · ㅄ = ㅂㅅ
- **Vocales compuestas:** ㅘ = ㅗㅏ · ㅙ = ㅗㅐ · ㅚ = ㅗㅣ · ㅝ = ㅜㅓ · ㅞ = ㅜㅔ · ㅟ = ㅜㅣ · ㅢ = ㅡㅣ

API (todo sobre texto en NFC; la entrada se normaliza con `nfc`):

```kotlin
data class Block(val lead: Char, val vowel: Char, val tail: String)   // tail: "", "ㄱ" o "ㄹㄱ" (compuesto partido)
fun decompose(syllable: Char): Block?            // null si no es 가..힣
fun compose(lead: Char, vowel: Char, tail: String = ""): Char?   // null si la combinación no existe
fun blocks(text: String): List<String>           // "말하다" → [말, 하, 다]; los espacios y signos se conservan como elementos
fun jamoOf(syllable: Char, splitVowel: Boolean = false): List<Char>   // 닭 → [ㄷ, ㅏ, ㄹ, ㄱ]; 와 → [ㅇ, ㅘ] o [ㅇ, ㅗ, ㅏ]
fun joinVowels(a: Char, b: Char): Char?          // ㅗ + ㅏ → ㅘ
fun joinTails(a: Char, b: Char): Char?           // ㄹ + ㄱ → ㄺ
enum class VowelShape { Vertical, Horizontal, Mixed }
fun shapeOf(vowel: Char): VowelShape
fun hasBatchim(syllable: Char): Boolean
fun endsInRieul(syllable: Char): Boolean
fun jamoDistance(a: String, b: String): Int      // distancia de edición sobre la secuencia de jamo
```

Los jamo se manejan siempre como **jamo de compatibilidad** (U+3131–U+318E), que es lo que se muestra; nunca como jamo combinables (U+1100…).

**Pruebas (`HangulTest`):** ida y vuelta `compose(decompose(c)) == c` para las 11 172 sílabas; los ejemplos de la especificación (말 = ㅁㅏㄹ; 말하다 = 3 bloques; 하다 = 2); cada final y vocal compuestos; texto en NFD de entrada; texto con espacios, signos y latín.

### 3.2 · `HangulIme.kt` (teclado 두벌식)

Estado: los bloques ya cerrados más el bloque en composición `(lead?, vowel?, tail)`. Entradas: jamo, espacio, borrar.

| Estado del bloque | Entra | Resultado |
|---|---|---|
| vacío | consonante | `lead` = consonante |
| vacío | vocal | bloque suelto con solo la vocal (se muestra el jamo tal cual) |
| solo `lead` | vocal | `lead + vowel` |
| solo `lead` | consonante | cierra el jamo suelto; bloque nuevo con la consonante |
| `lead + vowel` | vocal que se une (`joinVowels`) | vocal compuesta |
| `lead + vowel` | vocal que no se une | cierra el bloque; bloque nuevo con la vocal suelta |
| `lead + vowel` | consonante que puede ser final | `tail` = consonante |
| `lead + vowel` | ㄸ, ㅃ o ㅉ (no pueden ser final) | cierra; bloque nuevo con esa inicial |
| `lead + vowel + tail` sencillo | consonante que se une (`joinTails`) | final compuesto |
| `lead + vowel + tail` | consonante que no se une | cierra; bloque nuevo con esa inicial |
| `lead + vowel + tail` | vocal | **la última consonante del final se va al bloque nuevo como inicial**: 간 + ㅏ → 가 · 나; 닭 + ㅣ → 달 · 기 |
| cualquiera | espacio | cierra el bloque y añade un espacio |
| en composición | borrar | quita el último jamo (deshace una vocal o un final compuesto a su primera parte) |
| nada en composición | borrar | quita el bloque (o espacio) anterior entero |

`val text: String` da el texto completo en cada momento. **Pruebas (`HangulImeTest`):** una tabla de al menos 40 secuencias → texto, incluidas 한국어, 학교에 가요, 닭, 닭이 → 달기 + corrección, 꽃, 없어요, 왜, 의사, 쌍 (con ⇧), y borrar paso a paso desde cada estado.

## 4 · Contenido

### 4.1 · `GameBank`

`GameContentProvider.bank()` lo construye una vez con los documentos `ready` y lo guarda en memoria; se invalida con la misma huella que `NotesSource` (fecha y tamaño de cada `extracted/`, y la lista de documentos). Construirlo en `Dispatchers.Default`; meta: menos de 300 ms con los datos del dueño (se precalienta al abrir Games, durante la apertura).

```kotlin
enum class ItemKind { WORD, SENTENCE }
data class GameItem(
    val key: String,               // "w:" o "s:" + norm(korean): estable entre reprocesados e importaciones
    val kind: ItemKind,
    val korean: String,            // la forma principal
    val variants: List<String>,    // "가장 / 제일" → [가장, 제일]
    val meaning: String,
    val blocks: List<String>,      // sin espacios
    val documentId: Long, val documentName: String, val page: Int,
)
class GameBank(val words: List<GameItem>, val sentences: List<GameItem>, …índices derivados)
```

- **Palabra:** un par de `extractPairs` cuyo coreano (cada variante) es solo `[가-힣]+`, de 1 a 6 bloques, con significado. Si se repite entre documentos, gana el primero (como `mergedWords`).
- **Frase:** un par cuyo coreano tiene de 2 a 8 palabras separadas por espacios, o un ejemplo **con traducción** de un par o de un punto de gramática (`GrammarPoint.translated`). Se descartan los que llevan `->`, `→`, `=`, letras latinas o cifras en el coreano.
- **Trazabilidad:** cada elemento lleva documento y página; el tablero los muestra (marca de origen).
- **Nunca se inventa coreano.** Lo único que no sale de las notas es la lista fija de pares mínimos (D8-6), marcada como tal.

### 4.2 · Qué necesita cada juego (si no llega, el juego no sale)

| Juego | Mínimo en el banco |
|---|---|
| Hangman | 10 palabras de 2–5 bloques |
| Word Match | 8 palabras con sentidos distintos |
| Sentence Builder | 5 frases de 3–7 palabras |
| Memory Flip | 10 palabras con sentidos distintos |
| Word Scramble | 10 palabras de 2–5 bloques válidas (4.5) |
| Listen & Guess | voz disponible + 10 palabras |
| Hangul Builder | 10 palabras de 2–4 bloques |
| Word Sort | 2 contenedores con 3 palabras cada uno (4.8) |
| Grammar Fixer | 3 frases válidas (4.9) |
| Word Recall | 8 palabras de 1–3 bloques |
| Hangul Typing | 10 palabras |
| Word Path | 5 palabras de 3–5 bloques y 30 bloques distintos en el banco |
| Sound Detective | voz disponible + 6 palabras (modos B y C); modo A: pares de las notas o la lista fija |

Con menos de 8 palabras en total: estado *contenido insuficiente* (`games.md`, 11).

### 4.3 · Reglas de calidad comunes

- **Sin dos respuestas válidas:** en juegos con varios significados a la vista (Match, Memory, Listen & Guess, Sort) ningún par de elementos comparte un sentido (`englishSenses(a) ∩ englishSenses(b) = ∅`) ni un coreano.
- **Comparar siempre normalizado:** `nfc` + sin espacios al comparar coreano; las variantes de `KOREAN_ALTERNATIVES` valen todas.
- **Si una fábrica no puede garantizar una ronda sin ambigüedad, devuelve `null`** y el motor elige otro juego. Nunca se «arregla» bajando el listón.
- **Enfriamiento:** un elemento usado como objetivo no vuelve en las 8 rondas siguientes de la sesión.

### 4.4 · Hangman

- Modo sílabas: teclas = bloques distintos de la palabra + distractores (bloques de otras palabras que comparten inicial o vocal con alguno del objetivo y no están en él) hasta 10 (o 12).
- Modo jamo: la palabra se descompone con `jamoOf(splitVowel = false)`; un final compuesto son dos jamo. Una tecla revela todas las posiciones con ese jamo exacto (inicial o final).

### 4.5 · Word Scramble

Se descarta una palabra si todos sus bloques son iguales o si alguna permutación distinta de sus bloques es otra palabra del banco. El orden inicial nunca es el correcto.

### 4.6 · Sentence Builder

Fichas = palabras separadas por espacios; el signo final se separa. *won* si el orden es el de las notas o el de otra frase del banco con las mismas fichas y el mismo significado normalizado. *neutral* si son las mismas fichas, en otro orden y con la misma última ficha. Si no, *lost*.

### 4.7 · Word Path

Colocar la palabra con un camino al azar de celdas contiguas (8 direcciones, sin repetir celda); rellenar con bloques de otras palabras; **contar los caminos que deletrean el objetivo: tiene que ser exactamente 1** (búsqueda en profundidad; 16 celdas, trivial). Si no, reintentar con otra semilla derivada (hasta 20 veces; después `null`). Al comprobar un trazo que no es el objetivo, mirar si es otra palabra del banco (→ aviso sin gastar intento).

### 4.8 · Word Sort

- **Tema:** `belongs(categoría, significado)` verdadero para **una sola** de las categorías de la ronda.
- **Tipo de palabra:** *Verbs* = coreano acabado en 다 y significado que empieza por «To » pero no por «To be »; *Adjectives* = acabado en 다 y significado que empieza por «To be » seguido de algo; *Nouns* = no acaba en 다 y el significado no empieza por «To ». Fuera siempre: 이다, 아니다, 있다, 없다, 되다, y cualquier palabra con varios significados de tipos distintos.
- Se elige al azar entre las combinaciones de contenedores que cumplen el mínimo.

### 4.9 · Grammar Fixer (`Particles.kt`)

Parejas: 는/은 · 가/이 · 를/을 · 와/과 · 랑/이랑 · 로/으로 (tras vocal o ㄹ / tras otra consonante).

Una frase vale si tiene **al menos una** palabra `W` tal que:
1. `W = N + P`, con `P` una de las 12 formas y `N` (lo que queda) igual, normalizado, a una palabra del banco que **no** acaba en 다, o a uno de estos pronombres: 저, 나, 너, 우리, 저희, 이것, 그것, 저것, 여기, 거기, 저기, 누구, 뭐;
2. `P` es la forma correcta para el último bloque de `N` (si no lo es, las notas ya traen una errata: descartar);
3. `W` no es, entera, una palabra del banco;
4. la forma equivocada `N + P'` tampoco lo es.

La ronda cambia `P` por su pareja `P'` en **una** de esas palabras.

**Arreglos ofrecidos:** la forma correcta `P` y 1–2 distractores que son **la forma equivocada de otras parejas para ese mismo `N`** (para *학교을*: 를 ✔, 은, 이). Así solo una opción es gramatical tras `N` y la respuesta se decide por la regla del 받침, sin ambigüedad. Nunca se ofrece la forma correcta de otra pareja (*학교는* también sería una frase válida y no se puede marcar como error). La forma equivocada que ya está en la frase no se ofrece.

Texto de la regla (plantilla): *After a vowel: {v}. After a consonant: {c}.*; para 로/으로: *After a vowel or ㄹ: 로. After another consonant: 으로.*

**Pruebas:** cada pareja con y sin 받침, ㄹ con 로, un sustantivo que no está en el banco (no vale), una palabra que acaba por casualidad en 가/이 (아이, 누가 no se parten), una frase sin candidatos.

### 4.10 · Sound Detective (`MinimalPairs.kt`)

- Pares de las notas: dos palabras del banco con el mismo número de bloques y `jamoDistance == 1`.
- Lista fija, 21 grupos de palabras coreanas reales, cada una con su significado en inglés para mostrarlo al responder (el dueño la revisa antes de cerrarla): 불/풀/뿔 · 달/탈/딸 · 자다/차다/짜다 · 살/쌀 · 방/빵 · 굴/꿀 · 비/피 · 발/팔 · 공/콩 · 사다/싸다 · 개/깨 · 도끼/토끼 · 섬/솜 · 벌/볼 · 강/간 · 밤/밥 · 산/상 · 문/물 · 곰/공 · 눈/논 · 거리/고리. Una prueba comprueba que dentro de cada grupo todas las parejas tienen `jamoDistance == 1` y que ninguna entrada va sin significado.
- La explicación del modo A sale de `jamoDistance` y de la posición del jamo que cambia: *first sound* (inicial del primer bloque), *the vowel*, *the final consonant*, o *one sound* si no es ninguno de esos casos.

### 4.11 · Voz (`KoreanVoice`)

```kotlin
interface KoreanVoice {
    val state: StateFlow<VoiceState>          // Checking, Ready, Missing
    suspend fun speak(text: String, rate: Float = 1f)   // vuelve cuando termina de sonar; cancelable
    fun stop()
    fun release()
}
```

`TtsKoreanVoice` (plataforma, `android.speech.tts.TextToSpeech`):
- **Ready** solo si hay una voz `ko-KR` con `isNetworkConnectionRequired == false` y sin `"notInstalled"` en `features`; se fija esa voz con `setVoice`. Nunca se pide síntesis por red.
- Manifiesto: `<queries><intent><action android:name="android.intent.action.TTS_SERVICE"/></intent></queries>` (visibilidad de paquetes; **no** es un permiso). Comprueba que `tools/ci/check_manifest.py` sigue en verde y que el manifiesto unido no gana permisos.
- Foco de audio: `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` mientras suena; si se pierde, el motor pausa la sesión.
- Volumen de medios a 0: la UI lo avisa (`games-minigames.md`, *Audio*); no se cambia el volumen.
- Se crea al entrar en Games y se libera (`shutdown`) al salir. En pruebas, `FakeKoreanVoice`.
- 📱 **Comprobación en el OPPO (tarea 9):** qué motor y qué voces coreanas hay, si alguna es sin red, y si suena en modo avión (lo activa el dueño). Si no hay ninguna: se informa al dueño (instalarla es un ajuste del teléfono: lo hace él) y los dos juegos quedan apagados.

## 5 · Motor de sesiones

### 5.1 · Estados

```
Preparing → Playing(round) → Feedback(result) → Transition → Playing(next) → …
     └──── Empty (sin contenido) / Failed
Playing ⇄ Paused        Paused → Ended(summary)
```

`GameSessionEngine` expone `state: StateFlow<SessionState>` y recibe `start()`, `submit(RoundResult)`, `feedbackDone()`, `pause()`, `resume()`, `end()`. Mientras se juega una ronda, **prepara la siguiente** en `Dispatchers.Default` (elige juego y contenido con el historial que tendría si la ronda actual se ganara y si se perdiera: dos candidatas; al llegar el resultado se queda con la que toca). Al salir de la pantalla: `end()` cancela todo, para la voz y guarda lo pendiente.

### 5.2 · `GameSelector`

Entrada: juegos disponibles (4.2 + voz), historial de la sesión, niveles, `Random`.

1. **Fuera** el juego de la ronda anterior.
2. **Fuera** los de la misma *familia de gesto* que la ronda anterior, si quedan al menos 3 juegos de otras familias.
3. Peso de cada candidato = `1.0`
   - × `min(1, rondasDesdeLaÚltimaVez / 5)` (si nunca salió en la sesión: × 1.5);
   - × `1.4` si su *habilidad* no ha salido en las 3 últimas rondas;
   - × `min(1, elementosVálidos / 20)` (más contenido, más veces);
   - × `2.0` si las 2 últimas rondas se perdieron **y** el juego es de los suaves (Word Match, Memory Flip, Word Scramble, Listen & Guess);
   - × `0.5` si las 2 últimas rondas se perdieron y el juego es de los exigentes (Hangul Typing, Grammar Fixer, Word Path, Hangman en jamo).
4. Sorteo ponderado con el `Random` de la sesión.

Familias y habilidades: la tabla del principio de `games-minigames.md`.

### 5.3 · Nivel (por juego)

Función pura del historial guardado de ese juego (las últimas 6 rondas, de cualquier sesión): empieza en 1; **sube** uno tras 3 *won* seguidas sin fallos; **baja** uno tras 2 *lost* seguidas. Entre 1 y 3. Además, si las 2 últimas rondas **de la sesión** se perdieron (en cualquier juego), la siguiente ronda se juega un nivel por debajo, sin guardarlo. No se enseña el nivel en pantalla.

### 5.4 · `ItemPicker`

Peso de cada elemento válido para el juego: `1.0`
- × `3.0` si es **débil**: `wrong > correct`, o su último fallo fue hace menos de 7 días;
- × `1.5` si nunca ha salido;
- × `0.3` si está **dominado**: racha propia ≥ 3 y visto hace menos de 2 días;
- × `0` si está en enfriamiento (4.3).

Sorteo ponderado sin repetición. Los distractores salen del resto del banco sin pesos.

### 5.5 · Semilla y recuperación

- Al empezar la sesión se guarda una semilla (`Long`) en la fila de la sesión y en el `SavedStateHandle` del ViewModel junto al id de la sesión y el número de ronda.
- La ronda `n` se genera con `Random(seed xor (n * 0x9E3779B97F4A7C15uL.toLong()))`.
- **Si matan el proceso:** al volver, la misma sesión (si su última actividad fue hace menos de 30 min) regenera la **misma** ronda desde cero. Lo ya colocado dentro de la ronda se pierde; las rondas terminadas ya estaban guardadas. Si pasaron más de 30 min: se cierra y se muestra el resumen.
- **Rotación:** la pantalla se queda en vertical si la app ya lo está; si no, el estado vive en el ViewModel y lo de dentro de la ronda en `rememberSaveable`.

## 6 · Datos (Room v5, solo teléfono, fuera del respaldo)

`GameTables.kt` (a mano) + `"GameSessions", "GameRounds", "GameItemProgress"` en `APP_TABLES` del generador + `MIGRATION_4_5` (solo `CREATE TABLE` y sus índices). `BackupSchema` no se toca: **la exportación debe dar los mismos sha256 que antes**.

| Tabla | Columnas |
|---|---|
| `game_sessions` | `id` PK · `seed` · `started_at` · `ended_at` NULL · `duration_seconds` · `rounds` · `won` · `best_streak` |
| `game_rounds` | `id` PK · `session_id` (índice) · `round_index` · `game` · `level` · `outcome` (`won`/`lost`/`neutral`) · `mistakes` · `response_ms` · `item_keys` (JSON) · `created_at` (índice) |
| `game_item_progress` | `item_key` PK · `seen` · `correct` · `wrong` · `streak` · `last_seen_at` · `last_wrong_at` NULL |

- Una ronda *skipped* no se guarda.
- `duration_seconds` suma solo el tiempo de juego (sin pausas), con el patrón de `attachSession`; una ronda con más de 2 min sin tocar nada cuenta 2 min (el mismo tope que R1-13).
- Cifras (precisión global y por juego, tiempo medio, mejor racha, más falladas, dominadas, última práctica) se calculan con SQL sobre estas tres tablas; no hay tabla de agregados.
- Al borrar un documento no se borra nada aquí: las claves son por texto y un elemento que ya no está en el banco simplemente no sale.
- **Nada de texto de las notas en logs.** Los logs de Games llevan juego, nivel, resultado, tiempos y cuentas.

**D8-4 (si se aprueba):** `ProgressRepository` suma `game_sessions` al tiempo de estudio, al calendario y a la racha. Va en su propia tarea (10) y **después** de que R1-06 esté mergeado. Las doradas no tienen filas de Games, así que no cambian.

## 7 · Rendimiento y ciclo de vida

- Metas en el OPPO (release): preparar una ronda en menos de 50 ms (p90); sin fotogramas lentos en las transiciones (macrobenchmark de 20 rondas con un juego falso de un toque); memoria estable tras 200 rondas.
- Animaciones leídas en dibujo o layout; ningún `.value` de animación en composición (`jetpack-compose-performance`).
- Con la pantalla fuera de `RESUMED`: pausa automática, relojes y bucles parados, voz parada.
- Al salir: se cancelan las corrutinas de la ronda, se libera la voz, se guarda la sesión. Nada queda vivo en segundo plano; Games no usa ningún servicio.

## 8 · Tareas

### PR A — núcleo sin pantalla (rama `fase-8/core`)

**Tarea 0 · Decisiones y base.** `DECISIONS.md` con D8-1…D8-9 respondidas. Grupo `games` en `design/tokens.json` y `Tokens.kt` regenerado (`tools/design/gen_tokens.py`); `Splash.kt` usa el token `night`. Iconos nuevos (`gamepad-2`, `pause`, `play`, `flame`, `volume-2`, `delete`, `mic`, `lightbulb`, y `clock`, que ya está en `design/icons/`) desde Lucide a `design/icons/` y a `Lucide`. *Hecho cuando:* `TokensTest` y las capturas existentes siguen en verde sin regrabar.

**Tarea 1 · Hangul.** 3.1 y 3.2 con sus pruebas. `KoreanForms.kt` pasa a usar `Hangul` sin cambiar resultados. *Hecho cuando:* `HangulTest`, `HangulImeTest` y todas las doradas de `:core:korean` en verde.

**Tarea 2 · Banco y fábricas.** `GameBankBuilder`, las 13 fábricas, `Evaluators`, `Particles`, `MinimalPairs`, con notas **inventadas** en las pruebas (nunca texto del dueño). Por cada fábrica: genera con contenido suficiente; devuelve `null` con contenido insuficiente; cumple sus garantías (4.3–4.10) en 500 semillas; misma semilla → misma ronda. *Hecho cuando:* `./gradlew :core:korean:test` en verde.

**Tarea 3 · Datos.** `GameTables`, `MIGRATION_4_5`, `GameRepository`. `MigrationTest` v1 → v5 con las filas iguales; prueba de que la exportación del respaldo no cambia (mismos sha256 que con v4 sobre `golden/db`). *Hecho cuando:* `:core:data:test` en verde.

**Tarea 4 · Motor.** `GameSelector`, `ItemPicker`, niveles, `GameContentProvider`, `GameSessionEngine`. Pruebas con reloj falso y `FakeKoreanVoice`: 300 rondas simuladas sin dos juegos iguales seguidos; sin el mismo elemento en 8 rondas; sin voz no salen los de audio; con un solo juego disponible la sesión funciona; tras dos derrotas pesan más los suaves (estadístico, 2000 sorteos); misma semilla → misma secuencia; pausa a mitad no pierde la ronda; `end()` deja la sesión cerrada y guardada; recuperación tras «matar» (motor nuevo con la misma semilla e índice).

### PR B — pantalla y juegos (rama `fase-8/games`)

**Tarea 5 · Escenario.** `Screen.Games` (con `encode`/`decode`), fila de MoreSheet, `GamesScreen`, `GamesViewModel`, `GameStage`, `MoonTimer`, `GameTopBar`, `Board`, `FeedbackRibbon`, `PauseSheet`, `SessionSummary`, apertura, estados vacíos, y `Tile`/`Slot`. Con **Word Match** como primer juego de punta a punta. Pruebas de comportamiento (Robolectric): avance automático tras el feedback; Back abre la pausa; *End session* guarda y muestra el resumen; con TalkBack simulado no avanza solo. Capturas de `games.md`, 13. 📱 En la app QA: 30 rondas de Word Match seguidas, pausa, llamada simulada no (no se aceptan diálogos): basta con ir a inicio y volver.

**Tarea 6 · Juegos de fichas.** Memory Flip, Word Scramble, Sentence Builder, Word Recall (con faroles y luna). Una prueba de comportamiento y una captura por juego.

**Tarea 7 · Juegos de Hangul.** `SyllableFrame`, `HangulKeyboard`, Hangman (sílabas y jamo, con faroles), Hangul Builder, Hangul Typing. Prueba: ningún juego abre el teclado del sistema (no hay `TextField` en `ui/games`; prueba que lo comprueba por código, como `NoMlKitInScreensTest`).

**Tarea 8 · Juegos de arrastrar y corregir.** Word Sort, Korean Word Path, Grammar Fixer. Cada uno con su modo de toques además del arrastre.

### PR C — audio, cifras y pulido (rama `fase-8/polish`)

**Tarea 9 · Voz.** 📱 `KoreanVoice` real, la comprobación del OPPO (4.11), `ReplayButton`, `VoiceBars`, Listen & Guess y Sound Detective. Prueba instrumentada que se salta si no hay voz.

**Tarea 10 · Cifras.** Las cifras de la pausa y del resumen desde `GameRepository`; D8-4 si se aprobó.

**Tarea 11 · Pulido y entrega.** 📱 En la app QA con el respaldo del dueño: una sesión de 60 rondas (logs solo con cuentas), letra al 130 %, *Quitar animaciones* (lo activa y lo restaura el dueño, o se prueba por Robolectric), modo oscuro, modo avión (lo activa el dueño). Tabla en el informe: cuántos elementos válidos da el banco del dueño por juego (solo números) y qué juegos quedan fuera. Macrobenchmark de transiciones. Skills de permisos e intents sobre el manifiesto unido. `versionName` 0.10.0. Instalar en la app del dueño solo con su OK, con `install -r`.

## 9 · Condiciones de parada (además de las de siempre)

- una fábrica no puede cumplir sus garantías con el contenido real del dueño y la solución sería aflojarlas;
- menos de 6 de los 13 juegos quedan disponibles con las notas del dueño;
- el manifiesto unido gana un permiso, o `INTERNET`;
- la exportación del respaldo cambia algún sha256;
- una dorada existente cambia;
- hace falta una dependencia nueva;
- la voz coreana del OPPO necesita red.

## 10 · Riesgos

| Riesgo | Cómo se controla |
|---|---|
| Hangul mal compuesto en el teclado propio | autómata con tabla de pruebas (3.2) antes de cualquier pantalla |
| Las notas dan poco para algunos juegos (frases, categorías, partículas) | umbrales de 4.2; la tarea 11 da la tabla real; el selector reparte según contenido |
| Marcar como error un orden de frase válido | resultado *neutral* en Sentence Builder (4.6) |
| «Errores» gramaticales que no lo son | Grammar Fixer solo con alomorfos (4.9) |
| No hay voz coreana sin red en el OPPO | se detecta; los dos juegos de audio se apagan con explicación |
| Jank con muchas piezas animadas | lecturas en dibujo, tope de 16 piezas, macrobenchmark |
| Caer en un Quiz disfrazado | cada juego tiene un gesto propio (tabla de familias); ninguno es «elige 1 de 4» |
| El arte de las pantallas de carga no está en GitHub | la escena se dibuja en código; no usa ninguna imagen |
