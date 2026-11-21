# Fase 10 — Pulido, oscuro legible, Games con personajes y pantallas de carga vectoriales

> **Para Claude Code:** siete PR pequeños, en este orden. Un commit por tarea, cada tarea empieza por sus pruebas, **no se mergea ningún PR sin la confirmación del dueño**. Tras cada PR: informe corto con ✅/⚠️/❌ y **para**.
>
> **Diseño (manda sobre las fichas anteriores donde las contradiga):** `design/polish.md` (interfaz), `design/quiz.md` sección E (oscuro), `design/games-characters.md` (Games), `design/splash.md` (pantallas de carga), `design/tokens.json` versión 2.
>
> **Origen:** `docs/AUDIT_2.md` (rama `audit/2`) y `qa/ronda-2/RECORRIDO.md` (fuera de git).
>
> 📱 **Teléfono:** en los PR A (comprobación rápida), E, F y G. Siempre con la app QA (`.qa`); la app del dueño solo con su OK y `install -r`.

## 0 · Decisiones

| ID | Decisión | Estado |
|---|---|---|
| D10-1 | Rellenos y bordes de acierto/fallo/aviso con **valores propios en oscuro**, válidos en todo lo que usa el token. Sustituye, solo para esos tokens, a «los colores son los de la web» (2026-11-12). Fondo y azules intactos. | **Decidido** (dueño, 2026-11-15) |
| D10-2 | Games: reparto **Beom (tigre blanco), Deung (farolito) y Dal (luna)**, solo con los colores del grupo `games`. | **Decidido** (dueño, 2026-11-15) |
| D10-3 | Games: la voz coreana del teléfono **dice la palabra al final de cada ronda**, con un ajuste para apagarla. D8-5 sigue: sin efectos de sonido. Sin permisos nuevos. | Propuesta de diseño (sale del encargo: «su sonido en el momento justo»). **El dueño confirma al aprobar el PR F3.** |
| D10-4 | Pantallas de carga como **SVG en `assets/splash/`** dibujados con `Canvas`; las 11 privadas, fuera de git. Sin dependencias. Cierra H-02 y el anexo C de la auditoría. | **Decidido** (dueño, 2026-11-15) |
| D10-5 | `kotlinx-coroutines-test` solo en `testImplementation` (tabla en `AUDIT_2.md` §8.1: 0 B en la APK, Apache-2.0). | **Pendiente del sí del dueño.** Sin él, el PR B se hace sin las tareas B1–B2. |

Anotar D10-1…D10-4 en `docs/DECISIONS.md` en el PR 0.

## 1 · Qué entra de la auditoría y qué se deja

**Entra (estabilidad primero):**

| Hallazgo | Dónde | Por qué ahora |
|---|---|---|
| H-01 recuadros ilegibles en oscuro | PR A | El fallo más visible: no se lee la respuesta al fallar |
| H-11 contenedores invisibles en oscuro | PR A | Mismo sitio, mismo PR |
| H-09 errores de espacio, export truncado, Games sin estado de error | PR A | Mensajes falsos y un archivo roto en la carpeta del dueño |
| H-02 faltan 11 pantallas de carga en el release | PR A (parche de 1 archivo) y PR E (solución de fondo) | El parche es trivial y se ve ya |
| H-10 pruebas que faltan (contraste, ViewModels, capturas oscuras) | PR A y PR B | Para que el pulido de C–F no rompa nada sin que CI lo vea |
| H-06 integridad (solo la prueba, sin claves foráneas) | PR B | Barata y protege importar/borrar |
| H-08 «cero bytes» de red | PR G (paso de QA) | No es código |
| H-14 `getIdentifier` en el arranque | PR E | Desaparece con los SVG |

**Para después (fase 11, por este orden):**

| Hallazgo | Por qué espera |
|---|---|
| **H-03** modelo único de palabra (*Learned 5* frente a *0 words*) | Cambia números que el dueño ya ve y necesita su definición de «dominada» (pregunta 2 de la auditoría). Es lo primero de la fase 11. |
| **H-04** agregados SQL del Dashboard | Sin síntoma con el volumen actual; antes hay que medir con la base sintética. Necesita las pruebas del PR B. |
| **H-05** CPU dentro de transacciones | Sin síntoma hoy; toca doradas de Flashcards. Necesita el PR B. |
| **H-07** memoria del lector con PDF de 300 páginas | Solo medir; ningún PDF del dueño se acerca. |
| **H-12** `runBlocking` en la búsqueda de Teacher | Hoy no se llama desde el hilo principal. |
| **H-13** Dependabot | Necesita el sí del dueño (pregunta 5); no es de esta fase. |
| **H-15** un punto de gramática partido entre dos páginas; lo que queda en la segunda se lee como palabras | Visto con la plantilla de notas, PR fase-10/plantilla. |
| **H-16** MuPDF quita los espacios del coreano en las celdas de una tabla | Visto con la plantilla de notas, PR fase-10/plantilla. |

## 2 · PR 0 — Documentos (`docs/fase-10`)

1. Commit de: `design/polish.md`, `design/games-characters.md`, `design/splash.md`, `design/quiz.md`, `docs/FASE_10_PLAN.md`, `tools/design/splash_vector.py` y `app/src/main/assets/splash/flash*.svg` (los que existan).
2. **`design/tokens.json` v2 NO va en este PR:** entra con A1 (`gen_tokens.py` + `Tokens.kt` regenerado), porque `TokensTest` compara ambos y dejaría `main` en rojo (decisión del dueño, 2026-11-15).
3. `.gitignore`: comprobar que están `app/src/main/assets/splash/photo_*.svg` y `tools/design/splash_vector_private.py`. **Antes del commit:** `git status` no debe listar ningún `photo_*.svg`.
4. `docs/DECISIONS.md`: D10-1…D10-4.
5. Traer `docs/AUDIT_2.md` de la rama `audit/2` a este PR (o mergear su PR antes, si el dueño lo prefiere).

## 3 · PR A — Estabilidad y oscuro legible (`fase-10/oscuro`)

| # | Tarea | Ficha | Pruebas |
|---|---|---|---|
| A1 | `python3 tools/design/gen_tokens.py` con `tokens.json` v2. En oscuro, `correctFill`/`wrongFill`/`warnFill`/`wrongBorder`/`fillInk`/`fillMuted`/`fillWarn` pasan a sus valores `Dark`; tokens nuevos `correctInk`/`wrongInk`; `button-lg-h`. | `quiz.md` E | `TokensTest` |
| A2 | **`ContrastTest`** (JVM): cada par de la tabla de `quiz.md` E, en claro y oscuro (texto ≥ 4.5:1, bordes ≥ 3:1). | `quiz.md` E | es la prueba |
| A3 | Dentro de todo `*-fill`: valor y número en `fillInk`, etiquetas en `fillMuted`, título e icono en `correctInk`/`wrongInk`. Sitios: `Feedback.kt`, `QuizOption.kt`, `CalendarMonthCompact.kt`, `CalendarMonthFull.kt`, `DocumentCard.kt`, `SettingsScreen.kt`, `States.kt`, `TypedResult.kt`, `Pieces.kt`, `GamesScreen.kt`, `Audio.kt`, `TeacherScreen.kt`. Texto de acierto/fallo sobre el fondo (cifra *Correct*, *Your answer:* de resultados, contadores de mazo): `correctInk`/`wrongInk`. Borde de 2 dp en opción correcta/fallada y en el recuadro de respuesta. | `quiz.md` E | prueba de fuentes: ningún `onSurface`/`ink`/`muted` dentro de un `background(…Fill)` |
| A4 | `Modifier.softBox()` y su uso en opción sin elegir, explicación del fallo y comparación hablada. | `polish.md` P-03 | Roborazzi oscuro: `QuizOption` ×4, respuesta escrita ×2, calendario, cinta de Games ×2 |
| A5 | H-09: `IOException` de espacio con su mensaje; comprobar `usableSpace` (tamaño × 2) antes de copiar e importar; borrar el `.zip` parcial si exportar falla; volcado de exportar dentro de una transacción de lectura; estado de error con *Try again* en Games. | `polish.md` P-05 | flujo falso que lanza «No space left»; exportador contra un `OutputStream` que falla en la tabla 5 → no deja archivo; motor de Games que falla |
| A6 | H-02, parche: `res/raw/keep.xml` con `tools:keep="@drawable/splash_photo_*"`. (Se borra en el PR E.) | — | contar WebP del APK release en local |
| A7 | 📱 App QA en oscuro: un quiz con un fallo escrito y uno de opción; calendario; una ronda de Games ganada y otra perdida. 3 capturas para el informe. | | |

## 4 · PR B — Red de pruebas (`fase-10/pruebas`)

| # | Tarea | Pruebas |
|---|---|---|
| B1 | *(solo con D10-5)* `kotlinx-coroutines-test` en el catálogo, `testImplementation`. | — |
| B2 | *(solo con D10-5)* Sustituir `idleUntil` y los `delay` reales de las pruebas de ViewModel por `runTest`. | las mismas, sin tiempo real |
| B3 | `LibraryViewModel`: subida correcta, rechazada, sin espacio, duplicada. `SettingsViewModel`: importar/exportar con error. `DashboardViewModel`: carga, error + *Retry*, `practice`. Con repositorios reales sobre la base dorada en memoria (como `QuizBehaviourTest`). | son las pruebas |
| B4 | `IntegrityTest` (H-06): importar la dorada → borrar documento con y sin tarjetas → reprocesar → ninguna fila huérfana. | es la prueba |

## 5 · PR C — Componentes comunes (`fase-10/componentes`)

| # | Tarea | Ficha |
|---|---|---|
| C1 | StickerButton **Large** (52 + 4 dp, 15 sp) y su uso en los pies de pantalla. | P-01 |
| C2 | `StatStrip`, `Pill`, `ResultBanner`, `SearchField`, `SheetHeader`; migrar sus usos y borrar los duplicados. | P-02 |
| C3 | `displayName()` con elipsis en medio y sin extensión; usarlo en Library, Flashcards, Quiz y Games. | P-04 |
| C4 | Microanimaciones M-1, M-2, M-7, M-8 (las de componentes). | P-06 |
| C5 | Roborazzi de los componentes, claro y oscuro. Regrabar solo las capturas afectadas y listar cuáles en el PR. | `polish.md` 8 |

## 6 · PR D — Pantallas (tres PR cortos)

| PR | Rama | Tareas (ID de `polish.md`) |
|---|---|---|
| **D1** | `fase-10/dashboard` | P-10, P-11, P-12, P-13, P-14, M-5, M-6; Calendar P-45, P-46, P-47 |
| **D2** | `fase-10/library-flashcards` | P-20, P-21, P-22, P-23; P-25, P-26, P-27, P-28, M-4 |
| **D3** | `fase-10/quiz-teacher` | P-30, P-31, P-32, P-33, M-3; P-35…P-38; P-40…P-42; P-50…P-52; P-55 |

Cada uno: pruebas de comportamiento de lo que cambia (plegado del formulario recordado, *Start a quiz* del hero vacío, *Listen* visible con scroll, botón de voz tras fallar solo con voz disponible), capturas nuevas y **ninguna dorada tocada**.

## 7 · PR E — Pantallas de carga vectoriales (`fase-10/splash`)

| # | Tarea | Ficha `splash.md` |
|---|---|---|
| E1 | Lector del subconjunto de SVG (`XmlPullParser` + `PathParser`) → `SplashSceneData` (capas, trazados, degradados). | 2 |
| E2 | `SplashScene`: capas en `GraphicsLayer`, encaje *cover*, profundidad, deriva, parpadeo, desenfoque, grano. | 3 |
| E3 | `Splash.kt`: lista `assets/splash/`, elige una, lee fuera del hilo principal, fundido de 120 ms; subtítulo a 180–520 ms; *Quitar animaciones*. | 3 |
| E4 | Borrar los 14 WebP, `keep.xml`, `splash_art.py` y el `getIdentifier`. Prueba de CI: ningún `photo_*.svg` en `git ls-files`. | 1 |
| E5 | 📱 Medir en el OPPO, release, en 1080 × 2376 (y en 1264 × 2780 **solo si el dueño cambia él el modo**): lectura P90, CPU por fotograma, arranque en frío. Captura nativa y recorte 3× de cada escena para el informe (fuera de git si es privada). | 3 y 6 |

Los SVG los entrega la sesión de diseño. **E1–E4 se pueden hacer con las escenas que haya**; las que falten se añaden después sin tocar código.

## 8 · PR F — Games con personajes (tres PR)

| PR | Rama | Tareas | Ficha `games-characters.md` |
|---|---|---|---|
| **F1** | `fase-10/games-escena` | Escenario v2 (196 dp, capas 1–12); Beom, Deung y Dal dibujados con sus poses; A0; poses fijas con *Quitar animaciones*; `Motion.EaseIn` y `Motion.Bounce`. | 2, 3, 4, 5.1, 8 |
| **F2** | `fase-10/games-animacion` | `StageDirector` y las pistas A1–A13, racha, subir de nivel, entrada, pausa y fin; resumen con 0 rondas. | 5, 9 |
| **F3** | `fase-10/games-aprender` | LearnCard, voz al final de la ronda con su ajuste (D10-3), *NEW* y *Mastered*, *Words to review* con voz, retoques G-1…G-6. | 6, 7 |

- Pruebas: duración y pose final de cada pista; eventos encadenados; LearnCard elige la palabra correcta en juegos de varias palabras; la voz no suena con volumen 0, sin voz, con TalkBack o con el ajuste apagado; el toque corta la voz.
- 📱 Tras F2 y F3: macrobenchmark de Games (P90 ≤ 8 ms, P99 ≤ 12 ms) y una sesión de 10 rondas en modo avión (lo activa el dueño).

## 9 · PR G — Entrega (`fase-10/entrega`)

1. `versionName` **0.12.0**.
2. Manifiesto unido: los mismos permisos que 0.11.0. Skills de permisos e *intents*.
3. 📱 Ronda 3 en la app QA: recorrido corto de las pantallas cambiadas en claro y oscuro; Games en claro (quedó sin cubrir en la ronda 2); al final `dumpsys netstats` de la app QA = 0 B (H-08) y desinstalar.
4. Informe en `qa/ronda-3/` (fuera de git) y resumen sin texto de notas en el PR.
5. Instalar en la app del dueño solo con su OK.

## 10 · Condiciones de parada

Para y pregunta al dueño si:

- el manifiesto unido gana cualquier permiso, o algo necesita red;
- una prueba dorada cambia, o cambia una tabla del respaldo `.jsbackup.zip`;
- un `photo_*.svg`, una captura con notas o algo de `picsIcons/` aparece en `git status` como archivo a commitear;
- hace falta un color que no está en `tokens.json` v2, o una ficha contradice a otra y esta fase no dice cuál manda;
- hace falta una dependencia que no sea `kotlinx-coroutines-test` (D10-5);
- `ContrastTest` no pasa con los valores de la ficha (no se ajusta un color para que pase: se avisa);
- Games pasa de P90 8 ms o la pantalla de carga sube el arranque más de 15 ms, después de aplicar los recortes que dice cada ficha;
- la lectura de una escena pasa de 60 ms;
- algo pide aceptar un diálogo del sistema o cambiar un ajuste del teléfono;
- un PR pasa de ~600 líneas de código de producto: se parte.

## 11 · Riesgos

| Riesgo | Cómo se controla |
|---|---|
| Regrabar capturas esconde una regresión | Cada PR lista las capturas regrabadas y por qué; las de pantallas no tocadas deben seguir iguales |
| Los personajes distraen del juego | Viven solo en la escena, nunca tapan el tablero; todo lo que dicen está también en la tarjeta |
| La voz al final de cada ronda cansa | Ajuste en la pausa; un toque la corta; no suena al ganar en los juegos de escucha |
| SVG pesados alargan el arranque | Lectura fuera del hilo principal, presupuesto por escena, binario precompilado como salida |
| Una escena privada acaba en GitHub | `.gitignore` + prueba de CI + comprobación manual en el PR 0 y el PR E |
| El pulido rompe el comportamiento | PR B antes que C–D; cada tarea empieza por su prueba |

## 12 · Prompt para Claude Code

```
Fase 10. Lee docs/FASE_10_PLAN.md entero (es corto) y, de cada PR, solo las secciones de design/ que cita.
Haz los PR en orden: 0, A, B, C, D1, D2, D3, E, F1, F2, F3, G.
Reglas: un commit por tarea; cada tarea empieza por sus pruebas; no toques doradas ni el respaldo;
sin dependencias nuevas (B1–B2 solo si te confirmo D10-5); sin permisos nuevos; ningún color fuera de tokens.json v2.
Nunca hagas commit de app/src/main/assets/splash/photo_*.svg, de qa/ ni de picsIcons/.
Al terminar cada PR: ./gradlew test verifyRoborazziDebug lint, informe corto con ✅/⚠️/❌, y PARA. No mergees sin mi OK.
Si se cumple una condición de parada (sección 10), para y pregunta.
Empieza por el PR 0.
```
