# PoC 2 — Gemini Nano con ML Kit Prompt API, sin `INTERNET`

**Fecha:** 2026-11-08 · **Rama del código:** `poc/2-nano` (no se mergea) · **Plan:** `docs/POC_PLAN.md`, tarea 2.

**Respuesta corta:** sí para Coach, explicaciones y preguntas Multiple choice: Nano está en el OPPO, funciona sin `INTERNET` y en modo avión, y cumple la latencia. **Calificar por significado no pasa:** nunca aprobó una respuesta mal (0 falsos positivos), pero rechazó 2 de cada 3 sinónimos correctos (67 % de aciertos frente al 90 % pedido).

## Montaje

| | |
|---|---|
| Teléfono | OPPO Find X8 Pro (CPH2659), ColorOS `CPH2659_17.0.0.101(EX01)`, por USB, build `debug`, pruebas instrumentadas (`am instrument`) con `MainActivity` en primer plano y la pantalla encendida (`FLAG_KEEP_SCREEN_ON` de la ventana de la prueba) |
| Modelo | AICore `26.10.15.7` (versionCode 545246). Estado `DOWNLOADABLE` → la app llamó a `download()` tras pulsar el botón y AICore descargó el modelo → `AVAILABLE` |
| Librería | `com.google.mlkit:genai-prompt` 1.0.0-beta5 (compilada con Kotlin 2.4: el proyecto usa KGP 2.4.0) |
| Casos | `tools/poc/nano_cases.py` → `nano-cases.json`, solo de las doradas públicas (PDFs de prueba): 30 Coach, 30 explicaciones (17 puntos de gramática, repetidos hasta 30), 30 Multiple choice, 60 de calificar (30 positivos: 10 partes de una respuesta con varios significados y 20 paráfrasis a mano; 30 negativos: 10 respuestas de otro par, 10 antónimos y 10 sentidos cercanos) |
| Validadores | `is_grounded`, `leaks_answer`, `korean_cores`, `has_form`, `aeo_forms` y `past` portados a Kotlin; iguales a las doradas (`golden/text.json`: 760 casos; `golden/korean.json`: 414 casos) |
| Ritmo | 2 s de pausa entre llamadas; con `BUSY`, espera lo que pide AICore (5 s si no dice nada) y reintenta una vez. Calentamiento: 1 llamada que no cuenta |

Del teléfono solo salen cifras y códigos (`files/poc/nano-results-*.jsonl`: id, latencias, tokens, válido y motivo). Ni logcat ni los resultados llevan texto del modelo ni de las notas.

## Métricas contra los criterios

Con wifi, 150 llamadas (30 × 3 + 60); en modo avión, 50 (10 × 3 + 20).

| Métrica | Con wifi | Modo avión | Aceptación | |
|---|---|---|---|---|
| Estado de la función | `AVAILABLE` | `AVAILABLE` | `AVAILABLE` sin que la app pida red | ✅ |
| `INTERNET` / `ACCESS_NETWORK_STATE` en el manifiesto unido | ausentes (`tools:node="remove"`); solo `MainActivity` exportada | — | ausente, y todo funciona igual | ✅ |
| Modo avión (y wifi apagado aparte: en ColorOS el modo avión lo deja encendido) | — | 50/50 responden, red activa: ninguna | 100 % | ✅ |
| Coach: latencia total | p50 1.2 s · p90 1.5 s | p50 1.2 s · p90 1.5 s | p90 ≤ 3 s | ✅ |
| Coach: válidos | 30/30 | 10/10 | — | ✅ |
| Explicación: primer token / total | p50 0.39 s / p90 3.3 s | p50 0.39 s / p90 3.3 s | p50 ≤ 1.5 s / p90 ≤ 6 s | ✅ |
| Explicación: válidas (prompt v2) | 27/30 (90 %) | 9/10 | — | ✅ |
| Multiple choice válidas | 28/30 (93 %) | 10/10 | ≥ 80 % | ✅ |
| Coreano inventado antes de validar (llamadas con al menos una palabra fuera de la entrada) | Coach 0/30 · explicación 3/30 (1 palabra) · MC 3/30 | Coach 0/10 · explicación 1/10 · MC 2/10 | se mide y se informa | ℹ️ |
| Calificar por significado: aciertos | 40/60 (67 %) | 15/20 (75 %) | ≥ 90 % | ❌ |
| Calificar por significado: falsos positivos | 0/30 (0 %) | 0/10 | ≤ 3 % | ✅ |
| Calificar por significado: latencia | p90 1.3 s | p90 1.3 s | p90 ≤ 2 s | ✅ |
| `BUSY` brutos / tras el reintento | 0 / 0 | 0 / 0 | ≤ 5 % tras el reintento | ✅ |
| Tokens de salida (p50) | Coach 37 · explicación 81 · MC 49 · calificar 34 | parecido | — | — |

**Calificar por significado, en detalle:** acertó los 30 negativos (otro par, antónimo y sentido cercano) y 10 de 30 positivos: 5 de las 10 partes de una respuesta con varios significados ("to book" para "To reserve / To book") y 5 de las 20 paráfrasis a mano (rechazó, por ejemplo, "to be grateful" para "To be thankful"). Es un juez muy conservador: seguro (nunca aprueba algo mal), pero aporta poco.

## Lo que pasó por el camino

1. **Cuota:** la primera tanda, sin pausa, dio `BUSY` (código 9) a partir de la llamada 50 y en 8 de 57 llamadas (14 %), al instante. Con 2 s de pausa entre llamadas: 0 `BUSY` en todo lo demás (wifi y modo avión), sin necesitar el reintento.
2. **Pantalla bloqueada:** con la pantalla apagada o bloqueada AICore responde `BACKGROUND_USE_BLOCKED` (código 30), aunque la actividad esté en primer plano y se muestre sobre el bloqueo (`setShowWhenLocked`). En la app no afecta (el modelo solo se usa con el teléfono en la mano). La prueba se para con el primer código 30 y se reanuda desde ahí.
3. **Explicación en coreano:** con el prompt v1 ("Explain … in simple English") Nano contestó en coreano: 6/30 válidas. El prompt v2 ("You are an English-speaking tutor. Write your whole answer in English…") lo arregla: 27/30. Los prompts de la fase 6 tienen que pedir el inglés de forma explícita y validarlo.
4. **Validador del coreano inventado:** la comparación literal marcaba `커피를` como inventado si la entrada tenía `커피`. Ahora una palabra cuenta como de la entrada si su forma base (sin partículas ni terminaciones, con `korean_cores`, `has_form`, `aeo_forms` y `past`) está en la entrada. Sigue contando como inventado un verbo nuevo con una terminación enseñada (`갈게요` sin `가다` en la entrada). Las 19 explicaciones v1 ya guardadas no se pudieron volver a evaluar sin el modelo, porque no se guarda el texto; se repitieron las 30 con el prompt v2.
5. **Telemetría:** `genai-prompt` trae `datatransport` (Clearcut), que pide `INTERNET`; al quitarlo del manifiesto la app no puede enviar nada. Lo que haga AICore (app del sistema) queda fuera de la app.

## Decisión uso por uso (plan, punto 10.1)

| # | Uso | Qué lo mide | Resultado | Decisión |
|---|---|---|---|---|
| 1 | Quiz: calificar por significado | Calificar | 67 % de aciertos, 0 % falsos positivos | ❌ por el umbral → versión sin modelo. Se podría activar solo para aprobar (nunca aprobó algo mal), pero eso cambia el criterio: lo decides tú |
| 2 | Quiz: explicar tu error | Explicación (aproximado) | 90 % válidas, latencia dentro | ✅ con prompt en inglés explícito; falta medir la frase de error en sí |
| 3 | Flashcards: Type the answer | Calificar | igual que el 1 | ❌ igual que el 1 |
| 4 | Teacher sin respuesta | Explicación (aproximado) | 90 % válidas | ✅ provisional; citas sin medir |
| 5 | Coach | Coach | 30/30 y 10/10 válidos, p90 1.5 s | ✅ |
| 6 | Library: notas desordenadas | — | sin medir (no estaba en la PoC) | ⏸️ pendiente |
| 7 | Grammar (a) explicación / (b) frase de práctica | Explicación / — | (a) 90 % válidas; (b) sin medir | (a) ✅ · (b) ⏸️ pendiente |
| 8 | Dashboard: resumen semanal | Coach (aproximado: hechos → frases) | igual que Coach | ✅ provisional |
| — | Preguntas Multiple choice del modelo | Multiple choice | 93 % válidas | ✅ (las inválidas se descartan) |

**Nano queda como nivel 1** para los usos ✅. La tarea 4 (Gemma 4 E2B) no hace falta por disponibilidad, red ni latencia; solo tendría sentido si se quiere un juez de significado mejor para los usos 1 y 3.
