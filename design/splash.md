# Ficha: pantallas de carga vectoriales (Fase 10)

Sustituye a la sección *Pantalla de carga (R-018)* de `design/shell.md` en lo que toca a la imagen. **No cambian:** el concepto (una escena al azar entre 14, encima de la app), la paleta, los textos (*Just Study Korean* / *by DERO*), los 1500 ms, el toque que la cierra, y que no retrasa el arranque.

**Por qué:** las de hoy son 3 WebP blandos de origen (varianza del laplaciano 1.4–5.9, bandas en los degradados, zoom bilineal: `docs/AUDIT_2.md` §9) y 11 escenas planas de pocas formas que además no llegan al release (H-02). Referencia de acabado: `picsIcons/flash1–3` (capas de montaña con bruma, luna con halo, reflejos, flores en primer plano, profundidad).

## 1 · Qué se entrega

**Escenas como SVG por capas**, dibujadas por la app con `Canvas` a la resolución real de la pantalla (1080 × 2376, 1264 × 2780 o la que sea): **no hay píxeles que escalar**, así que no se pixelan ni con zoom.

| Qué | Dónde | ¿En git? |
|---|---|---|
| Motor del generador y las 3 escenas públicas | `tools/design/splash_vector.py` | sí |
| Las 11 escenas hechas a partir de las fotos del dueño | `tools/design/splash_vector_private.py` | **no** (`.gitignore`) |
| SVG públicos: `flash1.svg`, `flash2.svg`, `flash3.svg` | `app/src/main/assets/splash/` | sí |
| SVG privados: `photo_01.svg` … `photo_11.svg` | `app/src/main/assets/splash/` | **no** (`.gitignore`: `app/src/main/assets/splash/photo_*.svg`) |

- Los SVG son *assets*, no recursos: **el encogido de recursos no los toca** (causa de H-02) y no hace falta `keep.xml` ni `getIdentifier`. La app lista `assets/splash/` y elige una al azar entre las que haya; CI compila con las 3 públicas.
- Las 11 privadas: **siluetas sin cara**, ningún píxel ni metadato de las fotos, ningún nombre de persona. Nunca van a GitHub (DECISIONS 2026-11-11). Una prueba de CI falla si `git ls-files` contiene `assets/splash/photo_`.
- Se borran del proyecto: `res/drawable-nodpi/splash_*.webp` (los 14), `tools/design/splash_art.py` y, en el Mac del dueño, `splash_scenes.py`. APK: −1.2 MB de WebP, + ~0.5–0.9 MB de SVG (se comprimen en el APK a ~25 %).

## 2 · Formato de escena (subconjunto de SVG)

Lienzo: `viewBox="0 0 1080 2400"`. Cualquier navegador lo abre tal cual (así se revisa). La app solo entiende esto; el generador no emite nada más:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1080 2400"
     data-title-y="0.72" data-grain="0.035">
  <defs>
    <linearGradient id="g1" gradientUnits="userSpaceOnUse" x1 y1 x2 y2> <stop offset stop-color stop-opacity/> … </linearGradient>
    <radialGradient id="g2" gradientUnits="userSpaceOnUse" cx cy r> … </radialGradient>
  </defs>
  <g id="sky"   data-depth="0.00"> <path d="…" fill="url(#g1)"/> … </g>
  <g id="stars" data-depth="0.05" data-twinkle="1"> … </g>
  <g id="bloom" data-depth="1.00" data-blur="5" data-drift="-18,26" opacity="0.9"> … </g>
</svg>
```

| Elemento | Atributos admitidos |
|---|---|
| `<svg>` | `viewBox`; `data-title-y` (centro del bloque de texto, 0–1 del alto); `data-grain` (opacidad del grano, 0–0.06) |
| `<linearGradient>`, `<radialGradient>` | solo `userSpaceOnUse`; `<stop offset stop-color stop-opacity>` |
| `<g>` (una capa; **un solo nivel**, sin grupos anidados ni `transform`) | `id`; `opacity`; `data-depth` 0–1 (0 = fondo, 1 = primer plano); `data-blur` (desenfoque en px del lienzo, 0–12; como mucho 2 capas por escena); `data-twinkle="1"` (la capa parpadea); `data-drift="dx,dy"` (deriva en px del lienzo durante la pantalla) |
| `<path>` | `d` (M L H V C S Q T A Z, absolutas y relativas); `fill` (`#RRGGBB`, `url(#id)` o `none`); `fill-opacity`; `fill-rule`; `stroke`, `stroke-width`, `stroke-opacity`, `stroke-linecap`, `stroke-linejoin` |

Sin `<text>` (las letras de una escena son trazados), sin filtros, máscaras, `clip-path`, CSS, `<use>` ni imágenes. **Colores:** solo la paleta de `design/splash-icon-context.txt` (punto 2 y 3) y los acentos que cada escena ya tenía; el generador falla si sale un color fuera de su lista.

**Presupuesto por escena:** ≤ 16 capas, ≤ 700 trazados, ≤ 250 KB de SVG.

## 3 · Cómo la dibuja la app (`SplashScene`)

- **Lectura:** `XmlPullParser` + `androidx.compose.ui.graphics.vector.PathParser` (ya está en Compose; **ninguna dependencia nueva**). En `Dispatchers.Default`, al crear la actividad. Mientras llega (objetivo < 40 ms) se ve `night` liso, que es el color base de todas las escenas; la escena entra con un fundido de 120 ms. El título no espera a la escena.
- **Encaje:** tipo *cover*, centrado: escala = max(ancho/1080, alto/2400). Lo importante de cada escena vive dentro del área segura central de 1000 × 2200.
- **Capas:** cada `<g>` se graba una vez en su `GraphicsLayer` (`rememberGraphicsLayer().record { … }`): es una lista de dibujo vectorial, no un bitmap, así que **al escalar se vuelve a trazar nítida**. `data-blur` → `BlurEffect` en esa capa (API 31; el `minSdk` es 31).
- **Grano:** tras la última capa, puntos blancos y `night-deep` de 1 px físico (densidad: 1 punto cada 14 × 14 px, posiciones con semilla fija), con la opacidad de `data-grain`, en dos llamadas a `drawPoints`. Quita las bandas de los degradados y da textura de papel. Se dibuja a resolución física, no escala.
- **Movimiento (1500 ms, lineal, sustituye al zoom único de 1.00 → 1.06):**
  - **profundidad:** cada capa escala de 1.00 a `1 + 0.02 + 0.05 × depth` (fondo 1.02, primer plano 1.07) con pivote en el centro de la pantalla: el mismo acercamiento lento, ahora con paralaje;
  - **deriva:** las capas con `data-drift` se desplazan esa distancia de principio a fin (pétalos, nubes, bruma);
  - **parpadeo:** las capas con `data-twinkle` oscilan su opacidad 0.75 ↔ 1 con periodo de 900 ms;
  - todo en fase de dibujo (`graphicsLayer {}`), como hoy.
- **Texto** (lo dibuja la app, como hoy; mismos tamaños y colores): el bloque se centra en `data-title-y`. Título: opacidad 0 → 1 y sube 14 dp entre 75 y 480 ms (sin cambio). **Subtítulo *by DERO*: entra entre 180 y 520 ms** (antes 330–750: en las capturas de ~0.5 s casi no se veía, `carga_lanzamiento_1`). Salida: 1230–1500 ms (sin cambio).
- **Con *Quitar animaciones*:** escena fija en su estado final de profundidad, sin deriva ni parpadeo, texto puesto, 1500 ms.
- **Si una escena no se puede leer** (archivo roto): se elige otra; si ninguna, `night` liso con el texto. Nunca un cierre.
- **Memoria:** no se decodifica ningún bitmap de 10.4 MB (AUDIT_2 §4).

**Presupuestos a medir en el OPPO** (release, en los dos modos de pantalla): lectura de la escena más pesada ≤ 40 ms (P90 de 10 arranques); CPU por fotograma durante la pantalla P90 ≤ 8 ms; arranque en frío (`am start -W`) no sube más de 15 ms respecto a 137 ms. Si la lectura pasa de 60 ms: el generador exporta además un binario precompilado (órdenes de trazado ya partidas) y la app lee ese; el SVG sigue siendo la fuente.

## 4 · Receta de detalle (todas las escenas)

Cada escena se construye con estas capas, de atrás adelante. Es lo que separa el acabado de flash1–3 de las escenas planas de hoy:

| # | Capa | `depth` | Qué lleva |
|---|---|---|---|
| 1 | `sky` | 0.00 | degradado vertical de 4–5 paradas (nunca 2) |
| 2 | `glow` | 0.00 | 1–2 radiales grandes: resplandor del horizonte o del astro |
| 3 | `stars` | 0.05 | 60–120 puntos en 3 tamaños + 4–8 destellos de cuatro puntas; `twinkle` |
| 4 | `orb` | 0.08 | luna o sol: disco con degradado, 2 anillos de halo, cráteres o velo |
| 5 | `clouds` | 0.12 | 2–4 nubes de lóbulos con panza en sombra y borde iluminado; `drift` |
| 6 | `far` | 0.20 | sierra lejana: relleno degradado (cima más clara → base que se funde con la bruma) |
| 7 | `mist1` | 0.25 | bandas de bruma (degradado de opacidad 0 → 12 % → 0); `drift` |
| 8 | `mid` | 0.40 | sierra media con **línea de luz** en la cresta y **textura de arbolado** (borde festoneado) |
| 9 | `mist2` | 0.45 | segunda bruma |
| 10 | `subject` | 0.55 | el motivo (pagoda, torre, puerta…): silueta, **cara en sombra y cara iluminada**, líneas de teja, aleros con contraluz, ventanas encendidas con halo, barandillas |
| 11 | `near` | 0.75 | terreno cercano: lomas, árboles con 2 tonos, rocas, hierba |
| 12 | `water` / `ground` | 0.75 | reflejos (franjas horizontales rotas con opacidad), o sombras alargadas en el suelo |
| 13 | `people` | 0.80 | siluetas sin cara, con sombra en el suelo (solo donde la escena las tiene) |
| 14 | `bloom` | 1.00 | primer plano desenfocado (`blur`): ramas, flores, hojas, faroles; `drift` |
| 15 | `motes` | 0.90 | pétalos, luciérnagas o polvo de luz; `drift` |
| 16 | `shade` | 0.00 | fundido inferior/superior a `night` para el título + viñeta radial en las esquinas |

Reglas de dibujo: curvas suaves (Bézier, nunca polígonos con picos de 3 puntos); **toda forma grande lleva degradado, no color plano**; al menos **3 planos de profundidad con bruma entre ellos**; una **fuente de luz** por escena y coherente (contraluces del mismo lado); el acento de color (rojo, dorado, rosa, lavanda, durazno) solo donde la escena ya lo tenía.

## 5 · Las 14 escenas

`title-y` = centro del bloque de texto. Lo que no se dice aquí es libertad del generador dentro de la receta.

### Públicas

| Archivo | Escena | Detalle propio | `title-y` |
|---|---|---|---|
| `flash1.svg` | Luna, montañas y pagoda junto al lago | Cielo de azul claro a `action`; luna grande con velo; 5 sierras con bruma; pagoda de 3 tejados a la derecha sobre un islote con árboles; lago con el reflejo de la luna en franjas y el de la pagoda; pinos en el borde izquierdo | 0.76 |
| `flash2.svg` | N Seoul Tower de noche con cerezo | Cielo `night` → `action` con resplandor tras la colina; torre blanca con plataforma, antena y mástil rojo; colina boscosa festoneada; rama de cerezo arriba a la derecha en primer plano (desenfocada) con flores `petal`; pétalos que caen (`drift`); sierras abajo | 0.56 |
| `flash3.svg` | Taegeuk y palacio | Taegeuk grande arriba (mitad superior del rojo `lantern-red` al rosa y al violeta azulado originales; mitad inferior `action` → `night-shade`), con borde luminoso y bruma delante; palacio de dos tejados abajo con arcos encendidos; sierras a los lados | 0.56 |

### Privadas (fuera de git; siluetas sin cara)

| Archivo | Escena (composición de la ilustración actual) | Detalle que se añade | Acento | `title-y` |
|---|---|---|---|---|
| `photo_01.svg` | Palacio de frente, patio amplio, figura con hanbok de espaldas y visitantes lejanos | Tejas, aleros con contraluz, columnas, losas del patio en perspectiva, sombras largas, nubes con volumen, ciudad lejana en bruma | lavanda (hanbok) | 0.74 |
| `photo_02.svg` | Palacio de lado: alero en primer plano, escalinata, figura con hanbok | Vigas del alero, tejas en fuga, peldaños con luz rasante, pabellón al fondo, nubes | lavanda | 0.74 |
| `photo_03.svg` | Pasillo con hilera de faroles rojos en perspectiva y farol de techo | Cada farol con costillas, tapa, borla y halo; reflejo cálido en el suelo; vigas en fuga; resplandor al fondo | rojo y dorado | 0.76 |
| `photo_04.svg` | Torre roja y blanca de noche, con escalinata | Celosía de la torre, luces de baliza con halo, nubes nocturnas con borde de luna, peldaños con brillo | rojo, durazno | 0.76 |
| `photo_05.svg` | Arroyo Cheonggyecheon entre árboles, paseantes, piedras de paso | Agua con franjas y brillo, copas en 3 tonos, muros de piedra con sillares, edificios en bruma, puente | — | 0.80 |
| `photo_06.svg` | Piedra con rótulo SKKU, figura de pie, arboleda | Veteado y volumen de la piedra, letras como trazados con sombra, copas en capas, escalones, luz entre hojas | — | 0.76 |
| `photo_07.svg` | Pabellón amplio tras un césped, cielo con nubes | Tejado con tejas y contraluz, columnata, césped con franjas de siega y sombra de nubes, setos | — | 0.72 |
| `photo_08.svg` | Edificio de cubierta curva al atardecer, pino en silueta | Bandas de la cubierta con reflejo, cielo durazno → azul con nubes iluminadas por abajo, arboleda festoneada, pino con pisos | durazno | 0.74 |
| `photo_09.svg` | N Seoul Tower vista desde abajo entre copas | Estrías del fuste, ventanas doradas, balizas rojas, haz de luz, hojas del primer plano desenfocadas | rojo, dorado | 0.78 |
| `photo_10.svg` | Gwanghwamun de noche, muralla con tres arcos y un coche | Tejas, dancheong sugerido, sillares, almenas, focos desde abajo, estelas de luz del coche, asfalto con reflejo | dorado | 0.76 |
| `photo_11.svg` | Escenario con cinco siluetas y focos | Haces de luz con polvo, truss, suelo con reflejo, pantallas de fondo en bruma; **siluetas sin cara ni rasgos** | rosa, dorado | 0.76 |

## 6 · Pruebas

- **Generador** (`tools/tests`): cada SVG cumple el subconjunto, la paleta y el presupuesto.
- **Lector** (JVM): los 3 SVG públicos se leen sin error; un SVG roto no lanza.
- **CI:** `git ls-files | grep assets/splash/photo_` vacío; `check_manifest.py` sin cambios.
- **Roborazzi:** las 3 públicas con `LocalFrozenMotion`, a 411 × 905 dp, **a densidad 2.625 y 3.07** (los dos modos del OPPO).
- **OPPO:** 14 arranques en frío con cada escena forzada (un `intent extra` solo en debug/QA), captura a resolución nativa y recorte 3× del borde de una teja y de un degradado: sin escalones ni bandas. Las medidas del punto 3.
