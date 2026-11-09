# Fichas: Grammar, Calendar y Settings

Sin capturas propias. Usan los componentes del Design System: FilterPill, GrammarCard, TextField, Card, StickerButton, SelectedGroup y AiMark. Base: `design/shell.md`. Las tres se abren **encima** del destino actual, con flecha atrás, desde More.

---

## Grammar (Fase 3)

1. **JsTopBar** con flecha atrás: Grammar · 문법; *Explore Korean grammar with clear explanations and examples.* (sin subtítulo coreano, como en la web).
2. **Filtros:** FilterPill en una fila que se desplaza en horizontal, *All · Particles · Endings · Tenses · Others*. El elegido va en `action`.
3. **Buscador:** TextField *Search in Korean or English*, con `search` a la izquierda y `x` (*Clear search*) a la derecha cuando hay texto. Filtra mientras escribes, sobre el filtro elegido.
4. **Lista:** GrammarCard en LazyColumn, con 12 dp entre ellas.
   - **Cerrada:** el patrón (`ko-title`, 18 sp, 800) y el significado (14 sp, una línea con elipsis), con `chevron-left` de 20 dp en `muted`.
   - **Abierta:** fondo `tint`, borde `tint-border` (en oscuro, `line`) y el chevron girado −90°; el cuerpo baja animando la altura (240 ms). Dentro, las secciones que existan, cada una con su título (`body-bold`): *Meaning · Structure · Explanation · Examples · Notes*. El coreano va con `font-hangul` y las traducciones en `muted`.
   - Solo hay una abierta a la vez.
5. **Fase 6**, dentro de la tarjeta abierta y bajo un divisor, dos botones Small:
   - **Explain it simpler** (`Action`): una explicación más sencilla, en inglés.
   - **Practice sentence** (`Go`): una frase de práctica que usa solo palabras de tus notas.

   Lo que crea el modelo sale debajo, en un recuadro `canvas`, con **AiMark**. La frase de práctica lleva además la etiqueta *PRACTICE* (`badge-tint`, MAYÚSCULAS) y nunca se guarda. Sin modelo, los botones no aparecen.
6. **Estados:**
   - cargando: *Loading grammar…*;
   - error: *Could not load grammar.* con *Try again*;
   - sin resultados: *Nothing matches "{texto}".*;
   - sin gramática en tus notas: EmptyState con `languages`, *No grammar in your notes yet.* y **Open Library** (`Action`).

---

## Calendar (Fase 3)

1. **JsTopBar** con flecha atrás: Calendar · 달력; *Every day you studied, and every day you didn't.* · *공부한 날도, 안 한 날도 전부.*
2. **Cambiar de mes:** dos StickerButton Small (`Action`): **← {mes anterior}** y **{mes siguiente} →**, con el mes en coreano (*10월*, *12월*). El siguiente se deshabilita en el mes actual.
3. **Mes:** Card con relleno de 16 dp.
   - Título: *November 2026* · 11월.
   - Días de la semana: 월화수목금토일 (12 sp, `muted`). La semana empieza en lunes.
   - Celdas: ancho repartido entre 7 (unos 46 dp), 52 dp de alto, radio 8 y 4 dp de separación. El número arriba (12 sp, 500) y el símbolo debajo (✓ ~ !, 12 sp al 75 %). Colores como en el calendario compacto del Dashboard (`design/dashboard.md`, sección 3). Hoy lleva un aro de 2 dp `brand`.
   - **Tocar un día:** abre debajo del mes una Card con el detalle que en la web estaba en el `title`: *"11월 8일: 15 questions, 80 % accuracy, 25 min, topics: particles, -아서/어서"*, o *"11월 9일: not studied"*. El día elegido lleva un aro de 2 dp `action`.
   - Leyenda (12 sp, `muted`): *✓ accuracy 80 % or more · ~ accuracy 50–79 % · ! accuracy under 50 % · not studied*.
   - Debajo: *Days are counted in {zona del teléfono}.* (12 sp, `muted`).
4. **Estados:** *Loading…* y el recuadro de error.

---

## Settings (Fases 2 y 6)

1. **JsTopBar** con flecha atrás: Settings · 설정 (sin subtítulo).
2. **Filas** como las de MoreSheet: 56 dp como mínimo, con icono de 24 dp `control`, el título en `body-bold` y una línea `muted` de 12 sp. Un divisor de 2 dp `line` entre los grupos.

| Grupo | Fila | Línea `muted` | Control |
|---|---|---|---|
| Appearance | **Theme** (`moon`) | System follows your phone. | SelectedGroup Small *System · Light · Dark* |
| Backup | **Import backup** (`download`) | Replace everything with a .jsbackup.zip. | Abre SAF → diálogo de confirmación (*Cancel* / *Import*) → ProgressBar → Snackbar |
| Backup | **Export backup** (`download`, girado 180°) | Save everything to a .jsbackup.zip. | Abre SAF (CreateDocument) → Snackbar *Backup saved.* |
| AI (fase 6) | **On-device AI** (`sparkles`) | Gemini Nano runs on your phone. No internet. | Estado y acción, abajo |
| About | **Licenses** (`file-text`) | Poppins (OFL), Lucide (MIT), MuPDF (AGPL). | Abre una pantalla con los textos de las licencias |

**On-device AI**, a la derecha de la fila según el estado de AICore:

| Estado | Qué se ve |
|---|---|
| Available | píldora *Ready* (`correct-fill` / `correct`) |
| Downloadable | **Download** (Small, `Action`): la descarga solo empieza al pulsarlo. La hace AICore; la app sigue sin internet. Debajo, *Needs Wi-Fi and a few GB of space.* |
| Downloading | ProgressBar con el porcentaje, si AICore lo da |
| Unavailable | píldora *Not available* (`canvas` / `muted`) y la línea *The app works the same without it.* |

Las funciones con modelo (plan 10.1) solo aparecen si el estado es *Available*.
