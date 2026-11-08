# Ficha: Dashboard (Fase 3)

**Capturas:**
- `design/png/dashboard.png`: lo que se ve al abrir;
- `design/png/dashboard-full.png`: la página entera;
- `design/png/dashboard-dark.png`: oscuro;
- `design/png/dashboard-empty.png`: sin estudiar hoy y sin datos de quiz.

Base: `design/shell.md` (tema, JsTopBar, JsBottomBar, StickerButton y estados). Valores exactos en `design/tokens.json`.

**Pantalla de referencia:** 411 × 905 dp. Margen lateral de 16 dp, 12 dp entre bloques y desplazamiento vertical. La barra inferior y la del sistema (3 botones) son las de `shell.md`.

## Orden

1. **JsTopBar:** Dashboard · 대시보드; *No excuses. This is where you really stand.* · *변명은 없어. 이게 진짜 네 실력이야.*
2. **Cuatro KPI** en una cuadrícula de 2 × 2, con 12 dp entre ellos.
3. **Today's session**, sobre tu foto.
4. **Calendario del mes**, compacto.
5. **Progress:** la gráfica.

## 1 · KPI (StatTile)

Card con borde de 2 dp `line`, radio 16 y relleno 14/12/12 dp, todo centrado. Título `label-caps` en `muted`, valor de 22 sp (800) y, debajo, la comparación (11 sp, 800) con su icono de 16 dp.

| KPI | Valor | Comparación |
|---|---|---|
| Study streak | `"{n} day"` / `"{n} days"` | Ninguna. Borde y título según la racha: `streak-7` desde 7 días, `streak-15`, `streak-30`, `streak-50` y `streak-70`, cada uno con su `*-title`. A partir de 100 días, 🔥 bajo el número (🔥🔥 a los 150 y 🔥🔥🔥 a los 200), del mismo tamaño que la comparación. Si la racha sube respecto a la última visita, el número da un salto (`bump`: escala 1.15, 280 ms, spring). |
| Study time | `"6 h 40 m"` (formato de la web, `studyTime`) | Ninguna. |
| Words learned this week | `"{n} word(s)"` | `trending-up` en `correct` / `trending-down` en `wrong` / `minus` en `muted`, más el porcentaje frente a la semana anterior. |
| Quiz accuracy this week | `"84%"` | Igual que la anterior. |

- **Cargando:** cada valor muestra `—`.
- **Error:** el recuadro de error de `shell.md` encima de los KPI, con *Try again*.

TalkBack lee *"Up/Down vs last week: +12%"*.

## 2 · Today's session (TodayHero)

- **Fondo:** tu foto `dashboard.jpeg`, recortada (Crop) con alineación vertical 0.72 y el velo `photo-veil` encima. Radio 12, relleno de 16 × 20 dp y texto blanco. Si no hay foto, el degradado `hero-indigo → hero-violet (30 %) → hero-sky`.
- **Título:** `label-caps` en `on-hero-soft`: *Today's session* · 오늘의 공부.
- **Con estudio hoy:** cuatro columnas, *Questions 문제 · Accuracy 정확도 · Cards 카드 · Time 시간*. Etiqueta de 12 sp en `on-hero-soft` y valor de 24 sp (800).
- **Sin estudio hoy:** una línea de 16 sp (700): *You haven't studied today yet?? That's mediocre.*
- **Fix this next** · 다음에 고칠 것 (`label-caps`, `on-hero-soft`):
  - El tema más débil, en coreano: 20 sp, 800.
  - Etiqueta de prioridad (`label-caps`, radio 12):
    - `! High priority`: fondo `priority-high-on-photo`, texto `wrong`;
    - `~ Medium priority`: fondo `priority-medium-on-photo`, texto `fill-warn`;
    - `✓ Low priority`: fondo `priority-low-on-photo`, texto `correct`.
  - Debajo, los motivos separados por " · " (14 sp, `on-hero-soft`) y *"Also: …"* con los temas siguientes (12 sp).
  - Botones Small: **Practice this** · 연습하기 (`Go`; crea un quiz de 5 preguntas de ese tema y abre Quiz), el separador 아니면 (16 sp, 700, `on-hero-soft`, decorativo) y **Ask what to do** · 뭘 할지 묻기 (`Action`; abre Teacher en modo *What should I study?*).
  - Sin datos de quiz: *Answer some quiz questions first. I can't read your mind.* y ningún botón.

## 3 · Calendario del mes (compacto)

- **Contenedor:** Card con relleno de 12 × 16 dp.
- **Título:** *November 2026* (14 sp, 700) y el mes en coreano, *11월*, en `muted`.
- **Días de la semana:** 월화수목금토일 (12 sp, `muted`). La semana empieza en lunes.
- **Celdas:** 26 dp de alto, radio 8 y 4 dp de separación. Número de 12 sp (500) y símbolo de 10 sp al 75 %:
  - ✓ (80 % o más): `correct-fill` y `correct`;
  - ~ (50–79 %): `warn-fill` y `fill-warn`;
  - ! (menos del 50 %): `wrong-fill` y `wrong`;
  - sin estudio: `canvas` y `muted`.
- **Hoy:** aro interior de 2 dp `brand`.
- **Leyenda:** 11 sp en `muted`.

## 4 · Progress (gráfica, como en la web)

Captura: `design/png/dashboard-full.png`. La pantalla Progress no existe en el móvil (tampoco está en el menú de la web): todo el progreso se ve aquí.

- **Contenedor:** Card con relleno de 16 dp y título *Progress* · 진도.
- **Selector:** SelectedGroup Small *Day 일 · Week 주 · Month 월 · Year 년*. El periodo elegido se guarda en DataStore.
- **Gráfica:** 347 × 170 dp. Los cálculos por periodo son los mismos que en la web (`ProgressChart`).
  - **Barras** (eje izquierdo): por periodo, tres barras juntas con la suma de todos tus mazos al cierre del periodo. New flashcards en `count-new`, Learning en `fix` y Due en `wrong`. Radio 2; no se dibuja una barra si su valor es 0.
  - **Líneas:**
    - Study time (min) en `action`, de 2.5 dp con puntos de 2.5 dp;
    - Quiz accuracy (%) en `orange`, de 2.5 dp con puntos de 3 dp, contra el eje derecho (0–100 %);
    - un periodo sin respuestas corta la línea de precisión.
  - **Ejes:** 10 sp, `muted`. La escala izquierda se redondea hacia arriba como en la web (`niceMax`). Las etiquetas de periodo se aclaran para que no se monten (`every`). Rejilla de 1 dp `line`.
  - **Al tocar un periodo:** tooltip (fondo `tooltip`, texto blanco, 12 sp) con el mismo texto que el `<title>` de la web: *"{periodo}: {min} min, quiz {xx} % ({n} answers), flashcards {a} new, {b} learning, {c} due"*.
- **Leyenda** (*Chart legend*, 12 sp, `muted`, puede bajar de línea): *Study time (min)* y *Quiz accuracy (%)* con una raya de 3 dp; *New flashcards*, *Learning* y *Due* con un cuadrado de 12 dp.
- **Sin datos:** el texto centrado *"No study yet in this period."* en `muted`.

## Datos

Los mismos cálculos que la web: el resumen del progreso, el resumen semanal, el calendario del mes y el tema recomendado. Todo sale de Room y se calcula en `:core:data`, fuera del hilo principal; la pantalla solo lee un `StateFlow`. Los días van en la zona del teléfono.
