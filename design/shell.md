# Ficha: esqueleto de la app (Fase 2)

Fuente de verdad visual: el Design System **Just Study Mobile** (claude.ai, artefacto `CCALoNnouJMbMokhWMgFcT`). Aquí está lo que Claude Code necesita para construir el esqueleto sin abrir el artefacto. Los valores exactos están en `design/tokens.json` (copia del sistema) y los iconos en `design/icons/*.svg` (Lucide Animated, MIT). Las capturas de referencia están en `design/png/shell-*-{light,dark}.png`.

1 px del diseño = 1 dp (o 1 sp para texto). Pantalla de referencia: **411 × 905 dp** (OPPO Find X8 Pro).

## Tema

- **Colores:** `tokens.json → color.tokens`, con dos esquemas (light, dark). Correspondencia con `ColorScheme`:

  | Rol de `ColorScheme` | Token |
  |---|---|
  | primary / onPrimary | `action` / blanco |
  | primaryContainer / onPrimaryContainer | `tint` / `brand-text` |
  | secondary / onSecondary | `brand` / blanco |
  | tertiary / onTertiary | `go` / blanco |
  | error / onError | `danger` / blanco |
  | errorContainer / onErrorContainer | `wrong-fill` / `wrong` |
  | background, surface | `background`, `surface` |
  | onBackground, onSurface | `ink` |
  | surfaceVariant (y surfaceContainer*) / onSurfaceVariant | `canvas` / `muted` |
  | outline / outlineVariant | `field` / `line` |
  | inverseSurface / inverseOnSurface / inversePrimary | `tooltip` / blanco / `on-tooltip-action` |
  | scrim | `scrim` |

  El resto de los tokens va en `JustStudyColors` (CompositionLocal). **Dynamic Color desactivado.**
- **Tipografía:** Poppins 400 / 700 / 800 (TTF de google/fonts, OFL; licencia junto a las fuentes). El coreano usa la fuente del sistema, con interlineado 1.6 y sin tracking. Estilos con nombre y valores en `tokens.json → type.groups`; su asignación a `Typography` está en el README del sistema (sección *Material 3 en Compose*).
- **Correspondencia con Typography:** headlineLarge = `page-title` · headlineSmall = `stat-value` · titleLarge = `kpi-value` · titleMedium = `section-title` · bodyMedium = `body` · bodySmall = `caption` · labelLarge = `button` · labelMedium = `label-caps`, `button-sm` y `nav-label` · labelSmall = `ko-below`. Los estilos coreanos van en `KoreanTypography`.
- **Formas:** small 8 · medium 12 · large 16 · `CircleShape` para las píldoras.
- **Movimiento:**
  - duraciones: 150 / 240 / 400 ms; pulsar, 90 ms;
  - curvas: ease-out `(0.2, 0.8, 0.2, 1)`, spring `(0.34, 1.56, 0.64, 1)`, ease-in-out `(0.4, 0, 0.2, 1)`;
  - con *Quitar animaciones* del sistema (escala de animación 0), nada se mueve.
- **Modo oscuro:** sigue al sistema hasta que eliges en *Dark mode* (MoreSheet); la elección se guarda en DataStore. Los botones de color no cambian en oscuro.

## Componentes del esqueleto

| Componente | Captura | Medidas y reglas |
|---|---|---|
| **StickerButton** | `shell-button-*` | Colores `Action` · `Go` · `Fix` (texto `on-fix`) · `Danger` · `Gray`. Default: 44 dp visibles + canto de 4 dp. Small: 36 + 3 dp, con zona táctil de 48 dp. Radio 12. Texto `button` (13 sp, 700, MAYÚSCULAS, tracking 0.053em) y coreano opcional debajo (11 sp, 500, 85 %). Al pulsar: relleno `*-pressed`, baja lo que mide el canto y el canto desaparece (90 ms). Deshabilitado: `disabled-fill` y `disabled-ink`, sin canto. Sin ripple. |
| **JsTopBar** | `shell-topbar-*` | Large Top App Bar: `page-title` (32 sp, 800) y su coreano al 55 % y 60 % de opacidad; subtítulo `muted` 14 sp y subtítulo coreano 11 sp. Al desplazar se pliega a 64 dp con el título en `section-title`. A la derecha, **More** (icono `menu`, 48 dp), que abre MoreSheet. En pantallas de segundo nivel, flecha `chevron-left` a la izquierda. |
| **JsBottomBar** | `shell-bottombar-*` | NavigationBar de 80 dp más el inset de gestos, fondo `action`. Destinos: **Dashboard** `layout-grid` · **Library** `book-text` · **Flashcards** `layers` · **Quiz** `circle-help` · **Teacher** `graduation-cap`. Etiquetas `nav-label` (12 sp, 700) en Title Case, blancas. Activo: indicador de 64 × 32 dp `nav-active` e icono con trazo 2.5. El icono se anima una vez al elegirlo. |
| **MoreSheet** | `shell-moresheet-*` | ModalBottomSheet `surface` con radio superior 16, borde `line` y asa de 32 × 4 dp. Filas de 56 dp: icono 24 dp `control`, nombre `body-bold` y coreano `ko-side` (como en la web: 0.75 em del texto al lado, 700, a 0.45 em; aquí 10.5 sp). Orden: Grammar `languages` 문법 · Calendar `calendar-days` 달력 · divisor · Dark mode `moon` + ThemeSwitch · Settings `settings` 설정. |
| **ThemeSwitch** | `shell-themeswitch-*` | Como en la web: pista de 36 × 20 dp y círculo de 16 dp, con zona táctil de 48 dp (toda la fila de MoreSheet lo activa). Pista `line` (oscuro: `disabled-fill`); encendida, `nav-active`. Círculo blanco con la sombra de la web (Tailwind `shadow`: 0 1 dp 3 dp y 0 1 dp 2 dp −1 dp, negro al 10 %; solo en claro). En oscuro, como en la web: pista `disabled-fill` encendido o apagado y círculo `background`. 150 ms. |
| **States** | `shell-states-*` | **Vacío:** Card centrada con icono de 40 dp, `section-title` con coreano, una línea `muted` y su acción `Go` si la hay. **Cargando:** el texto de siempre ("Loading…") y LinearProgressIndicator (pista `tint`, barra `brand`) solo si tarda más de 300 ms. **Error:** recuadro `wrong-fill`, icono `badge-alert`, texto `wrong` y *Try again* (Gray, Small). |
| **Snackbar** | `shell-feedback-*` | Fondo `tooltip`, texto blanco y acción `on-tooltip-action`, encima de la barra inferior. |
| **PhoneLayout** | `shell-phonelayout-*` | Inset de la barra de estado · JsTopBar · contenido con margen lateral de 16 dp y 12 dp entre bloques · JsBottomBar · inset de gestos. De borde a borde. Ancho útil: 379 dp. |

**Iconos en Compose:** cada SVG de `design/icons/` pasa a `ImageVector`. La animación de cada uno está descrita en el README del sistema (sección *Iconos*). En la Fase 2 solo hacen falta los 5 de la barra, `menu` y los 5 de MoreSheet. Se animan una vez y nunca en bucle.

## Pantallas provisionales (Fase 2)

Cada destino muestra su JsTopBar con el título, el coreano y el subtítulo **de la web**, y debajo el estado vacío *Coming in phase 3* (icono del destino). Textos:

| Destino | Título · coreano | Subtítulo · subtítulo coreano |
|---|---|---|
| Dashboard | Dashboard · 대시보드 | No excuses. This is where you really stand. · 변명은 없어. 이게 진짜 네 실력이야. |
| Library | Library · 자료실 | Your study PDFs. No notes, no progress. · 공부용 PDF. 노트가 없으면 발전도 없어. |
| Flashcards | Flashcards · 단어 카드 | Words from your own notes. Review them before you forget them, again. · 네 노트에서 나온 단어. 또 잊어버리기 전에 복습해. |
| Quiz | Quiz · 퀴즈 | Questions built only from your own notes. No excuses if you fail. · 네 노트로만 만든 문제. 틀려도 변명은 없어. |
| Teacher | Teacher · 선생님 | Your teacher answers from your own notes first. For anything else, AI is used. · 선생님은 먼저 네 노트로 대답해. 나머지는 AI를 사용해. |
| Grammar | Grammar · 문법 | Explore Korean grammar with clear explanations and examples. (sin subtítulo coreano, como en la web) |
| Calendar | Calendar · 달력 | Every day you studied, and every day you didn't. · 공부한 날도, 안 한 날도 전부. |
| Settings | Settings · 설정 | — |

Grammar, Calendar y Settings se abren **encima** del destino actual, con la flecha atrás (no tienen sitio en la barra inferior).

## Settings (Fase 2: tema y respaldo)

Una lista con filas como las de MoreSheet (56 dp, icono, `body-bold` y una línea `muted`):

- **Theme** (`moon`): SelectedGroup Small *System · Light · Dark*, guardado en `UserPrefs.themeMode`. Es la única forma de volver a *System*. El interruptor de MoreSheet sigue eligiendo Light o Dark.
- **Import backup** (`download`). Abre el selector del sistema (SAF, `.jsbackup.zip`) y luego un diálogo: *"This replaces everything in the app with the backup. Continue?"*, con los botones *Cancel* (Gray) e *Import* (Danger). Mientras importa, LinearProgressIndicator. Al terminar, Snackbar *"Imported 1265 rows from 18 tables."* Si falla, un recuadro de error con el motivo y nada cambia.
- **Export backup** (`download`, girado 180°). Abre el selector para guardar `juststudy-backup-<fecha>.jsbackup.zip`. Al terminar, Snackbar *"Backup saved."*

Encima de la lista, el JsTopBar de Settings.


## Sin pantalla Progress

Como en la web (Progress no está en su menú), el móvil no tiene pantalla Progress. Todo el progreso se ve en la gráfica del Dashboard (`design/dashboard.md`, sección 4). `MoreDestination` queda en Grammar, Calendar y Settings.


## Respuesta háptica (R-010)

Solo en estas acciones, una vez y en el mismo fotograma en que se ve el resultado (`LocalHapticFeedback.current.performHapticFeedback`):

| Acción | Dónde | Tipo (`HapticFeedbackType`) |
|---|---|---|
| Girar la tarjeta (*Show answer* o tocar la tarjeta) | `StudyScreen`, `Flashcard` | `ContextClick` (toque corto y suave) |
| Calificar *Good*, *Easy* o *Hard* | `RatingButtons` | `Confirm` |
| Calificar *Again* | `RatingButtons` | `Reject` |
| Acierto en el quiz (opción o respuesta escrita) | `QuizOption`, `TypedResult` | `Confirm` |
| Fallo en el quiz (opción o respuesta escrita) | `QuizOption`, `TypedResult` | `Reject` |

- La respuesta escrita en Flashcards usa `TypedResult`, así que vibra igual: `Confirm` si es correcta y `Reject` si no.
- **Nunca** al navegar (barra inferior, More, atrás), al escribir, al desplazar, en casillas, interruptores, el selector − / +, *Undo*, la pantalla de carga, Teacher ni ningún otro botón.
- Sigue el ajuste de vibración táctil del sistema: apagado, no vibra nada. *Quitar animaciones* no lo apaga.
- Sin el permiso `VIBRATE` ni la API `Vibrator`: solo `performHapticFeedback`.
- Si `Confirm`, `Reject` o `ContextClick` no existen en el BOM de Compose del proyecto, **parar y avisar**. No hay que sustituirlos por otros tipos.

## Icono y nombre en el lanzador

- **Nombre visible:** `app_name` = **Just Study 한국어** (hoy `JustStudyKorean`). Lo usa también el `label` del manifiesto. Si ColorOS lo corta en la pantalla de inicio, se informa y no se cambia.
- **Icono temático (R-015):** `design/icons/ic_launcher_monochrome.svg`, el glifo 한 de trazo único, al estilo Lucide (trazo 2, extremos redondos).
  - Va como `res/drawable/ic_launcher_monochrome.xml` (vector de 108 × 108 dp).
  - Se añade `<monochrome android:drawable="@drawable/ic_launcher_monochrome" />` en `ic_launcher.xml` e `ic_launcher_round.xml`.
  - El glifo ocupa 54 dp en el centro (grupo con `translateX/Y` 27 y escala 2.25 sobre una rejilla de 24), dentro de la zona segura de 66 dp.
  - El color lo pone el sistema: el trazo va en negro opaco (`#FF000000`), sin relleno.
- **Notificación de procesado (R-015):** `design/icons/ic_stat_processing.svg`, el `file-text` de Lucide, como el de Library.
  - Va como `res/drawable/ic_stat_processing.xml` (24 × 24 dp), con trazo blanco `#FFFFFFFF` de 2 dp, extremos y uniones redondos, y sin relleno.
  - Sustituye a `ic_launcher_foreground` en `ProcessingService`. Después se borra `res/drawable/ic_launcher_foreground.xml` si nada más lo usa.

## Pantalla de carga (R-018)

Se queda como está (`design/splash-icon-context.txt`): una imagen al azar, **1500 ms**, el mismo texto y la misma animación. Solo cambia esto:

- **Un toque en cualquier parte la cierra:**
  - se desvanece en 150 ms (ease-out `(0.2, 0.8, 0.2, 1)`);
  - con *Quitar animaciones*, desaparece en el mismo fotograma;
  - ese toque no llega a la app, y los toques durante el desvanecido tampoco;
  - sin vibración.
- Si ya está desvaneciéndose sola (1230–1500 ms), el toque no hace nada.
- No sale al volver de segundo plano ni al girar el teléfono, como hoy.
