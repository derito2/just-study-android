# PoC 4 — Reconocimiento de voz en coreano en el dispositivo (Fase 9, tarea 1)

Rama `poc/4-speech` (no se mergea). App aparte `:speechpoc` (`.speechpoc`, «JS Speech PoC»), medida en el OPPO Find X8 Pro el 2026-11-14. Datos crudos: `docs/poc/4-speech.csv` (solo frases sintéticas; sin audio).

## Veredicto

**Ningún motor cumple el criterio 2 (acierto exacto ≥ 85 % en condición normal). Según el plan: se informa y no se integra.** El motor C (modelo propio) no se tocó.

## Paso 1 · Disponibilidad (sin hablar)

| Motor | `ko-KR` al inicio | Tras la descarga (la hizo el dueño, con red) |
|---|---|---|
| B · `SpeechRecognizer` on-device | DOWNLOADABLE (en el dispositivo, 31 idiomas descargables) | INSTALLED |
| A · ML Kit GenAI Speech, Basic | DOWNLOADABLE | AVAILABLE |
| A · ML Kit GenAI Speech, Advanced | **UNAVAILABLE** (no en este teléfono) | — |

Servicios de reconocimiento instalados: `com.google.android.as`, `com.google.android.tts`, `com.anthropic.claude`. No se comprobó cuál atiende el reconocimiento on-device de B.

## Permisos (criterio 5)

- `genai-speech-recognition:1.0.0-alpha2` **declara `INTERNET` y `ACCESS_NETWORK_STATE`** en su manifiesto (`genai-prompt`/`genai-common` beta5 no). Con el OK del dueño se quitaron con `tools:node="remove"`; el manifiesto unido de la PoC solo tiene `RECORD_AUDIO` (más el permiso interno de AndroidX). Integrar A obligaría a mantener ese `remove` y a ampliar `check_manifest.py`.
- ML Kit añade un receptor de telemetría (`datatransport`); sin `INTERNET` no puede enviar.
- La descarga del modelo de A funcionó desde la app sin `INTERNET`: la hace el sistema.
- B: sin dependencias; solo `RECORD_AUDIO` y `<queries>` de `RecognitionService`.

## Paso 2 · Sesión principal (85 locuciones por motor)

| Métrica | **B** | **A Basic** | Criterio |
|---|---|---|---|
| Acierto exacto, normal (5 frases ×2 + 10 palabras ×2 = 30) | **70 %** (21/30) | **57 %** (17/30) | ≥ 85 % ❌ |
| — solo frases (10) | 60 % | 80 % | |
| — solo palabras (20) | 75 % | 45 % | |
| Frases · despacio | 90 % | 90 % | |
| Frases · rápido | 90 % | 50 % | |
| Frases · voz baja | 60 % | 70 % | |
| Frases · ruido moderado | 70 % | 50 % | |
| Frases · a un brazo | 70 % | 60 % | |
| CER medio, normal (30) | 0,18 | 0,33 | |
| Falsos aciertos (5 errores a propósito) | 0/5 | 0/5 | ≤ 1/5 ✅ |
| Acierto en 1.ª posición (80 locuciones sin errores a propósito) | 59 | 49 | |
| Acierto en 2.ª–3.ª / fuera | 5 / 16 | sin lista de candidatos / 31 | |
| Latencia fin de habla → final, p50 / p90 | **no fiable** | 561 ms / **873 ms** ✅ | p90 ≤ 2,5 s |
| Arranque del reconocedor, p50 / p90 | 352 / 383 ms | 262 / 307 ms | |
| PSS de la app | 106–120 MB | 111–123 MB | |
| Errores del motor | 1 (`error=7`) | 3 (`NO_SPEECH_DETECTED`) | |

Normalización: solo sílabas hangul (sin espacios ni signos). CER = distancia de edición por sílaba / sílabas esperadas. Las repeticiones con *Back* cuentan una vez (la última).

Fallos típicos (palabras sueltas, voz baja, rápido): 친구→중국, 마시다→맛있다, 크다→끄다 (B); 사과→사고와, 도서관→도소권, 학교→«» vacío (A). Frases largas: ambos cortan o cambian una sílaba (`저는 한국어를 공부하고 있어요` → `저는 한국어를`, `저는 광고를 공부하고 있어요`). A añade puntuación (`.`, `?`) que la normalización descarta.

## Paso 3 · Sin red (10 locuciones normales por motor)

| | **B** | **A Basic** |
|---|---|---|
| **Modo avión** (activado por el dueño) | 9/10 exactas, 0 errores ✅ | 7/10 exactas, 0 errores ✅ (latencia p50 415 ms, máx 819 ms) |
| **Tras reiniciar** | 8/10 exactas, 0 errores ✅ (arranque en frío 462 ms) | 7/10 exactas, 0 errores ✅ (arranque en frío 320 ms; 1.ª latencia 1374 ms) |

**Criterio 1 (funciona en modo avión): cumplido por los dos.** Fallan el 2 (acierto).

## Recomendación

1. **No integrar ninguno de los dos tal cual.** Con estos números, un quiz que acepte la respuesta hablada si coincide exactamente rechazaría 3 de cada 10 (B) o 4 de cada 10 (A) respuestas bien dichas.
2. Si el dueño quiere seguir con voz, las opciones son, en orden de coste:
   - **B con un umbral de CER** (no exacto), p. ej. aceptar CER ≤ 0,2 en frases y usar la lista de candidatos; solo con palabras/frases ya vistas. Es 0 dependencias y 0 permisos nuevos. **No medido** cuánto sube el acierto: hay que recalcular sobre el CSV y decidir si un umbral así sigue dando 0/5 falsos aciertos.
   - **Motor C** (whisper.cpp / sherpa-onnx): es lo único de este plan que puede subir el acierto de forma real y es 100 % local, pero añade librería nativa y un modelo de decenas a cientos de MB. **Pide permiso expreso del dueño** para medirlo.
   - A no aporta más que B en palabras (45 % vs 75 %) y arrastra la dependencia alpha con el `remove` de `INTERNET`: **descartarlo**.
3. B es mejor base que A: mejor en palabras, sin dependencia, sin permisos que quitar.

## Lo que no se pudo medir o con límites

- **Latencia de B**: `onEndOfSpeech` llega junto con el resultado en este reconocedor (p50 ≈ 2 ms), así que no hay fin de habla real; medir requeriría marcar el fin de voz con `onRmsChanged`. No se hizo.
- **Latencia de A**: estimada (último nivel de audio por encima de la mitad del máximo → resultado final). A no emite fin de habla. Una fila en frío salió negativa y se descartó.
- **Confianza**: B devolvió `0.0`/vacío en `CONFIDENCE_SCORES`; A no tiene. Sin dato útil.
- **Modo avión**: lo activó y quitó el dueño; no se verificó desde la app que no hubiera red (la PoC no tiene `INTERNET` ni `ACCESS_NETWORK_STATE`).
- **Un solo hablante, un solo teléfono, una sola tanda** (2 repeticiones). Con 10–30 locuciones por celda, ±10 puntos es ruido.
- **Ruido**: música o TV sin nivel medido. **Distancia y volumen** subjetivos.
- **Temperatura y CPU**: solo aplicaban al motor C (no tocado). **Tamaño del paquete de coreano** de B y A: no se midió.
- **A Advanced**: UNAVAILABLE en este teléfono; no probado. **Servicio concreto de B**: no identificado.
- **Pronunciación**: ningún motor da nota; los tres devuelven texto (plan §1).
