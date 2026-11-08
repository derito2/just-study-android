# PoC 3 — Importar un respaldo real a Room

**Fecha:** 2026-11-08 · **Rama del código:** `poc/3-room` (no se mergea) · **Plan:** `docs/POC_PLAN.md`, tarea 3.

**Respuesta corta:** sí. Tu respaldo real entra en Room en una sola transacción, sin perder ni cambiar nada, en 0.25 s.

## Montaje

| | |
|---|---|
| Teléfono | OPPO Find X8 Pro (CPH2659), Android 17, por USB, build `debug`, pruebas instrumentadas (`am instrument`) |
| Room | `androidx.room3` 3.0.3 + KSP 2.3.10, `BundledSQLiteDriver` (`sqlite-bundled` 2.7.0), kotlinx-serialization-json 1.11.0 (solo `androidTest`; el `.so` de SQLite va en el APK debug) |
| Esquema | 18 entidades generadas desde `models.py` del web (`tools/poc/gen_room_entities.py`), columnas 1:1, fechas como `Long` (ms UTC) |
| Respaldo | `.jsbackup.zip` v1 de la tarea 0: 18 tablas, 1265 filas, 566 KB; alembic `b3e8d5c1a9f4` (igual que el web); zona de MySQL UTC |
| Web | `GET /progress/summary` y `/progress/calendar` (oct. y nov.) guardados al principio con la web arriba; la web se paró antes de compilar |

Del teléfono solo salen cifras (`files/poc/room-*.json`); la exportación de ida y vuelta se borra tras compararla.

## Métricas contra los criterios

| Métrica | Resultado | Aceptación | |
|---|---|---|---|
| Filas por tabla | 18/18 tablas, 1265/1265 filas iguales al manifiesto, 0 errores | 100 % | ✅ |
| Ida y vuelta (importar → exportar) | 18/18 JSONL idénticos byte a byte | idénticos | ✅ |
| ZIP corrupto o a medias | sha256 mal, tabla que falta y fila mala en la última tabla (con sha256 válido: se insertan 17 tablas y se deshace todo): 3/3 con error y 0 filas | error y base vacía | ✅ |
| Tiempo de importación | 247 ms (verificación de sha256 + inserción) | ≤ 10 s | ✅ |
| Memoria | PSS pico 99 MB (89 MB antes de importar) | ≤ 200 MB | ✅ |
| Fechas | 1564/1564 en UTC con `Z` (el importador rechaza otra zona); racha 2 = 2; minutos y preguntas por día de los últimos 30 días: 30/30 iguales a la web (America/Monterrey) | iguales | ✅ |
| Tamaño del `.db` | 326 KB | — | — |

## Notas

- **Importador:** primero comprueba el manifiesto, las columnas y el sha256 de cada JSONL leyendo en streaming, sin escribir nada. Después inserta todo en un solo `withWriteTransaction` línea a línea; si algo falla, no queda nada.
- **Formato:** para salir byte a byte igual, el exportador imita `json.dumps(sort_keys=True, separators=(",", ":"), ensure_ascii=False)`: escapes, floats con el `repr` de Python, columnas JSON anidadas y fechas `…Z`. Floats con exponente o fechas con microsegundos no aparecen en tu respaldo; el exportador falla si aparecen (no adivina).
- **`sqlite-bundled`:** su `.so` tiene que ir en el APK de la app (las pruebas corren en su proceso); con `androidTestImplementation` da `UnsatisfiedLinkError`.
- **Minutos por día:** `round(segundos / 60)` redondea a par como Python (`Math.rint`).

## Decisión propuesta

El `.jsbackup.zip` v1 queda como formato del respaldo de la app (plan, punto 11) y el importador se rehace limpio en `core/data` en la fase 2. Pendiente de tu confirmación en la tarea 5.
