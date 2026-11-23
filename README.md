# Just Study 한국어 (Android)

App para estudiar coreano **con tus propios apuntes en PDF**, todo en el teléfono y **sin internet**: la app no tiene el permiso `INTERNET`. Está hecha para un OPPO Find X8 Pro (ColorOS, arm64). La interfaz está en inglés.

Versión actual: **0.13.0** (versionCode 7).

- [Manual de uso](#manual-de-uso)
- [Escribir notas que la app lea enteras](#escribir-notas-que-la-app-lea-enteras)
- [Privacidad y seguridad](#privacidad-y-seguridad)
- [Tecnologías](#tecnologías)
- [Arquitectura](#arquitectura)
- [Rendimiento](#rendimiento)
- [Pruebas y calidad](#pruebas-y-calidad)
- [Instalar, actualizar y respaldar](#instalar-actualizar-y-respaldar)
- [Desarrollo](#desarrollo)
- [Licencias](#licencias)

---

## Manual de uso

### Primeros pasos

1. **Library → Choose files:** elige uno o varios PDF digitales con tus notas. Los PDF escaneados no sirven, porque la app no hace OCR. La app lee el PDF y saca palabras, frases, gramática y partículas.
2. **Flashcards → Create cards:** crea un mazo por documento (o elige *All documents*).
3. Estudia: tarjetas, quiz, juegos o pregúntale a Teacher. El Dashboard te dice qué toca hoy.

Para que la app entienda bien tus notas, escríbelas como dice la plantilla (*Settings → Notes template*; ver [abajo](#escribir-notas-que-la-app-lea-enteras)).

### Barra inferior

Tiene 7 destinos. Se desliza hacia los lados: a la derecha quedan Games y Grammar.

| Destino | Para qué sirve |
|---|---|
| **Dash** | Dónde estás de verdad: palabras aprendidas y acierto de la semana (con la tendencia), la sesión de hoy (*Practice this*, *Ask what to do*), el calendario del mes y la gráfica de progreso. |
| **Library** | Tus PDF. Cada uno muestra su estado (*Processing*, *Ready*, *Failed*) y en *Details* lo que se extrajo. También *Reprocess*, *Save PDF* y *Delete*. **All vocabulary** reúne todas las palabras de todos tus documentos, con buscador. |
| **Flashcards** | Repetición espaciada, como Anki (SM-2): *Again · Hard · Good · Easy*. Los mazos se ven en **cuadrícula** o en **lista** (icono de deslizadores). En la lista, tocar el mazo lo abre. El **lápiz** activa la edición: tocar el nombre lo renombra, arrastrar mueve o combina mazos y la **✕** borra. *Type the answer* te hace escribir la respuesta en las tarjetas de palabra. |
| **Quiz** | Quizzes hechos solo con tus notas, de 1 a 25 preguntas y de 9 tipos (ver *Quiz Guide*). Se califican al momento; al fallar, verás la comparación letra a letra y, si hay voz coreana, puedes escuchar la respuesta. Desde aquí también: historial, *Practice saved questions* y *Fix these weaknesses*. |
| **Teacher** | Pregúntale a tus notas: *How do I say…?*, *What does … mean?*, *Example with…*. Responde primero con tus notas y te dice de dónde sale. **What should I study?** abre el Coach, que mira tus cifras y te dice qué repasar. |
| **Games** | 13 minijuegos con tus palabras: Hangul Hangman, Word Match, Sentence Builder, Memory Flip, Word Scramble, Listen & Guess, Hangul Builder, Word Sort, Grammar Fixer, Word Recall, Hangul Typing, Korean Word Path y Sound Detective. Tienen 3 niveles. Te acompañan Beom (tigre blanco), Deung (farolito) y Dal (la luna). Al final de cada ronda la voz dice la palabra (se apaga en la pausa). Jugar cuenta como estudiar. |
| **Grammar** | Todos tus puntos de gramática y partículas, con filtros (*Particles · Endings · Tenses · Others*) y buscador. Cada tarjeta se abre con *Meaning, Structure, Explanation* y *Examples*. |

### More (icono ☰ arriba a la derecha)

- **Calendar:** cada día estudiado con su marca (✓ 80 % o más, ~ 50–79 %, ! menos del 50 %). Al tocar un día ves sus cifras.
- **Quiz Guide:** qué pregunta cada tipo de quiz, cuáles hacen las reglas y cuáles la IA, y qué cambia *Difficulty*.
- **Dark mode:** tema oscuro (o *Theme* en Settings: *System · Light · Dark*).
- **Settings:**
  - **Import backup / Export backup:** tu respaldo `.jsbackup.zip`.
  - **Notes template:** guarda la plantilla de notas (`Korean_Notes_Template.docx`) donde elijas.
  - **On-device AI:** estado de Gemini Nano.
  - **Licenses.**

### IA en el teléfono (opcional)

Gemini Nano corre **dentro del teléfono**, por AICore. Si *Settings → On-device AI* dice *Download*, se descarga una vez con Wi-Fi (varios GB); la descarga la hace el sistema, no la app. **Sin Nano la app funciona igual.** Con Nano:

- acepta una respuesta en inglés con el mismo significado aunque tenga otras palabras;
- explica tus errores;
- escribe algunas preguntas (*Multiple choice* y *Short answer*, hasta 3 por quiz);
- responde en Teacher lo que no está en tus notas;
- redacta el consejo del Coach;
- propone pares en notas desordenadas;
- en Grammar: *Explain it simpler* y *Practice sentence*.

**Lo que escribe el modelo lleva la marca *AI · not from your notes* y nunca se guarda como tuyo.**

### Voz

- **Escuchar:** usa la voz coreana del teléfono, sin red.
- **Say it in Korean** (Quiz): práctica hablada que no cuenta en la nota. Usa el reconocimiento de voz del teléfono sin red. El micrófono se pide solo al tocarlo. **Nunca se guarda el audio ni lo que dijiste.**

### Accesibilidad

Respeta *Quitar animaciones* del sistema (todo queda quieto), el tamaño de letra (se probó al 130 %) y TalkBack (etiquetas en botones e iconos). **El teléfono no vibra nunca.**

---

## Escribir notas que la app lea enteras

La guía completa está en [`docs/NOTES_TEMPLATE.md`](docs/NOTES_TEMPLATE.md), y la plantilla de ejemplo en *Settings → Notes template*. Lo básico:

```
Vocabulary                       ← título de sección (letra más grande)
1. 사과 = Apple
사과를 한 개 샀어요. = I bought 1 apple.     ← ejemplo
Note: buy them in autumn.                    ← nota

Grammar
1. -아서/어서
Meaning: so, because
Tense and structure:
- A/V + 아서 (after ㅏ or ㅗ)
Explanation:
It cannot be used with commands.
Examples:
비가 와서 집에 있어요. = It rains, so I stay home.

Particles
이/가                                        ← partícula
Subject                                      ← función
고양이가 집 뒤에 숨었어요. — The cat hid behind the house.
```

- Todo lo que va debajo de una entrada, hasta el siguiente número, es suyo, **sin límite** de ejemplos ni de notas.
- Cada punto de gramática debe empezar y acabar en la misma página.
- Las partículas van en tres líneas, no en una tabla de Word.

---

## Privacidad y seguridad

- **Sin internet:** no tiene `INTERNET`. Se comprueba el manifiesto unido en cada versión y en CI (`tools/ci/check_manifest.py`).
- **Permisos:** `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` (para procesar PDF) y `RECORD_AUDIO` (solo *Say it in Korean*, pedido al usarlo). Nada de almacenamiento, cámara, ubicación ni contactos: los archivos se abren y guardan con el selector del sistema (SAF).
- **Datos:** base de datos, PDF y textos extraídos en el almacenamiento privado de la app. `allowBackup=false`: Android no copia nada a la nube; **tu respaldo es la única copia**.
- Sin analíticas ni informes de fallos de terceros. Los logs nunca llevan texto de tus notas.
- **Release:** `debuggable=false`, R8 activo, firmado con una clave que vive fuera del repo. Solo la actividad principal es `exported`.

---

## Tecnologías

| Área | Qué se usa |
|---|---|
| Lenguaje y build | Kotlin 2.4 · Gradle con AGP 9.5 · catálogo de versiones (`gradle/libs.versions.toml`) · KSP |
| Android | minSdk 31 · targetSdk 37 · solo `arm64-v8a` |
| UI | Jetpack Compose (BOM 2026.09) · Material 3 · Navigation 3 · iconos Lucide convertidos a `ImageVector` · fuente Poppins · tokens de diseño generados desde `design/tokens.json` |
| Estado y DI | ViewModel + `StateFlow` · Hilt 2.60 · DataStore (preferencias) |
| Datos | Room 3 (esquema v5, exportado y con migraciones probadas) · SQLite empaquetado · FTS para buscar en tus notas |
| PDF | MuPDF 1.28.5 (versión fijada: las doradas del lector se hicieron con ella) |
| IA | ML Kit GenAI Prompt API (Gemini Nano por AICore), detrás de una sola puerta (`NanoGate`) con cola, validación y caché |
| Voz | TextToSpeech y SpeechRecognizer del sistema, forzados a funcionar sin red |
| Animación | Compose `Canvas` y `graphicsLayer` (personajes de Games y pantallas de carga vectoriales en `assets/splash/`), sin Lottie ni imágenes |
| Pruebas | JUnit 4 · Robolectric · Roborazzi (capturas) · kotlinx-coroutines-test · Macrobenchmark + UI Automator · Baseline Profile |
| CI | GitHub Actions (Linux): pruebas, capturas, lint, builds debug, release y androidTest, y comprobación del manifiesto |

---

## Arquitectura

Arquitectura en capas, sin red, con la lógica del coreano en Kotlin puro (portada del backend Python de la versión web y comprobada contra sus salidas: las «doradas»).

```
:app              UI en Compose: pantallas, componentes, tema, navegación, ViewModels (Hilt)
  │
  ├── :core:data  Room, repositorios (documentos, flashcards, quiz, teacher, games, respaldo),
  │               DataStore, voz, reconocimiento de voz, servicio de procesamiento en primer plano
  │     ├── :core:korean  lógica pura sin Android: fragmentar el texto, sacar pares y gramática,
  │     │                 generar preguntas, comparar respuestas, SM-2, Coach, Teacher, Games
  │     ├── :core:pdf     extracción con MuPDF (texto por página, títulos y tablas)
  │     └── :core:ai      NanoGate: la única entrada a Gemini Nano (estado, cola, validar antes de mostrar)
  │
  └── :macrobenchmark  arranque, flujos y Games; genera el Baseline Profile
```

**Flujo de un PDF:**
1. SAF → copia privada.
2. `ProcessingService` (en primer plano) → `:core:pdf` saca el texto.
3. `:core:korean` fragmenta y saca palabras, frases, gramática y partículas.
4. Se guarda en Room (más el índice FTS).
5. *Create cards* crea tarjetas SM-2.

**Reglas primero, modelo después:** todo lo que necesita la app lo calculan las reglas. Nano solo mejora cosas puntuales: escribir mejor, aceptar sinónimos, preguntas extra. Lo que escribe se valida contra tus notas antes de enseñarlo y lleva la marca AiMark.

**Navegación:** `TopLevel` (Dashboard, Library, Flashcards, Quiz, Teacher, Grammar) con su barra. Encima se abren pantallas sin barra (sesión de tarjetas, preguntas, Games) y las de More (Calendar, Quiz Guide, Settings).

Documentos del proyecto:
- plan: [`docs/PLAN.md`](docs/PLAN.md);
- decisiones: [`docs/DECISIONS.md`](docs/DECISIONS.md);
- auditorías: [`docs/RECOMMENDATIONS.md`](docs/RECOMMENDATIONS.md) y [`docs/AUDIT_2.md`](docs/AUDIT_2.md);
- diseño por pantalla: [`design/`](design/).

---

## Rendimiento

Todo medido en el OPPO Find X8 Pro, con el build release salvo que se diga otra cosa.

| Qué | Resultado | Cuándo |
|---|---|---|
| Arranque en frío | **101–130 ms** (3 medidas) | 0.12.0 |
| Arranque en frío (antes) | 137 ms (0.11.0) · 301–318 ms (debug, fases 5 y auditoría 1) | |
| Tamaño del APK (arm64) | ≈ 25 MB | 0.11.0 |
| Games: CPU por fotograma | P50 4,7 · P90 6,4 · P99 9,6 ms (objetivo: P90 ≤ 8, P99 ≤ 12) | fase 8 |
| Leer un PDF de 30 páginas | 242–288 ms, memoria (PSS) 147–259 MB | fase 6 |
| Teacher con reglas | p50 45 ms · p90 109 ms (primera vez 1,3 s, porque crea el índice) | fase 5 |
| Gemini Nano | juez de significado p50 2,6 s · explicación p50 3,2 s · ~3 s por pregunta generada (sin bloquear el quiz) | fase 6 |
| Red | 0 bytes de la app (`dumpsys netstats`), sin `INTERNET` | ronda 3 |

**Sin medir todavía:** batería (`batterystats`), los informes de estabilidad del compilador de Compose y el Dashboard con una base muy grande (H-04).

**Cómo se mantiene rápido:**
- Baseline Profile;
- R8;
- lectura de escenas y PDF fuera del hilo principal;
- consultas SQL dirigidas e índices (R-001/R-002);
- animaciones que leen su estado solo al dibujar;
- listas con `key`;
- capturas de pantalla que protegen la UI en cada PR.

---

## Pruebas y calidad

- **557 pruebas JVM** en 133 clases, más 35 en el teléfono (androidTest y macrobenchmark): **592 en total**. Cubren lógica del coreano contra las doradas de la web (`golden/`), repositorios sobre una base dorada en memoria, ViewModels, migraciones de Room, integridad de la base, contraste de colores (WCAG) y la plantilla de notas.
- **Capturas Roborazzi** en claro y oscuro de cada pantalla y componente; un cambio visual sin querer rompe el CI.
- **En el teléfono:**
  - pruebas instrumentadas (migraciones con el SQLite real, lector MuPDF);
  - macrobenchmarks;
  - rondas de QA con una app aparte (`.qa`) y una copia del respaldo, sin tocar la app real.
- **Antes de cada versión:** el manifiesto unido sin permisos nuevos, y revisiones de seguridad de permisos e *intents*.

---

## Instalar, actualizar y respaldar

1. La clave de firma vive fuera del repo. Su ruta y contraseñas van en `~/.gradle/gradle.properties` (`JUSTSTUDY_RELEASE_*`).
2. Compilar: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
3. Con el teléfono por USB y la depuración activada:
   `adb install -r app/build/outputs/apk/release/app-release.apk`

`install -r` conserva todos los datos. **Nunca desinstales la app para actualizar: se borraría todo.** Una APK firmada con otra clave (la de debug, por ejemplo) no se instala encima de la release.

**Respaldo:** *More → Settings → Export backup* guarda en un `.jsbackup.zip`, donde tú elijas, la base de datos, los PDF y los textos extraídos. *Import backup* lo reemplaza todo. **Haz uno antes de cada actualización.**

**Borrar un documento** en Library no toca sus tarjetas salvo que marques *Also delete its flashcards and their review history* en el diálogo (desmarcada por defecto).

---

## Desarrollo

```
./gradlew assembleDebug                 # build de desarrollo
./gradlew test verifyRoborazziDebug     # pruebas y capturas
./gradlew lint
./gradlew recordRoborazziDebug          # regrabar capturas (solo si el cambio es a propósito)
```

- MacBook Air M1 con 8 GB: sin emulador, se prueba en el OPPO por USB.
- Se trabaja en dos sesiones: **diseño** (fichas en `design/` y planes en `docs/`) y **código** (Claude Code: Kotlin, pruebas, PR). Ver [`CLAUDE.md`](CLAUDE.md).
- Cada PR pasa CI y lo mergea el dueño.
- Nunca se suben: `local.properties`, claves (`*.jks`, `*.keystore`), modelos, `golden/private/`, `qa/` ni escenas privadas (`assets/splash/photo_*.svg`).

---

## Licencias

- **Poppins:** SIL Open Font License.
- **Lucide:** ISC.
- **MuPDF:** GNU AGPL v3. La app es para uso personal; si algún día se distribuyera, habría que cumplir la AGPL (publicar el código) o tener una licencia comercial de Artifex.
- Las demás bibliotecas de AndroidX, Google y Kotlin son Apache-2.0.

Los textos completos están en *Settings → Licenses*.
