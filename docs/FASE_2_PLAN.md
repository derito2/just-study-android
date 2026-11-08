# Fase 2 — Diseño + base: plan de implementación

> **Para quien lo ejecute (Claude Code):** cada tarea llega como un prompt corto de la sesión de diseño; este documento es el plan. Sub-skill: `superpowers:executing-plans`. Pasos en casillas `- [ ]`. **Nada se mergea sin la confirmación del usuario en esa tarea.** Cada tarea indica si hace falta el teléfono (📱).

**Objetivo:** un esqueleto navegable con el tema de Just Study Mobile en el OPPO: Compose, la barra inferior con sus 5 destinos, More y su hoja, modo oscuro guardado, Hilt, DataStore, Room v1 con importar y exportar el respaldo, y CI que vigila permisos y pruebas.

**Arquitectura:** `:app` (Compose, navegación, pantallas, Hilt), `:core:data` (Room, DataStore, respaldo; librería Android) y `:core:korean` (Kotlin puro, JVM). `:core:pdf` y `:core:ai` se crean en sus fases (4 y 6), no antes. La UI lee el diseño de `design/` (`shell.md`, `tokens.json`, `icons/` y `png/`).

**Tecnología:**
- Kotlin **2.4.0** (lo pide ML Kit genai-prompt beta5 en la fase 6; se fija ya para no cambiarlo dos veces) y el plugin `org.jetbrains.kotlin.plugin.compose` con la misma versión;
- Compose BOM estable, Material 3, Navigation 3, Hilt con KSP, Room 3 con KSP (el de la PoC 3) y DataStore Preferences;
- Roborazzi (capturas en la JVM) y JUnit4; las pruebas con teléfono, con AndroidX Test.

**Base:** `docs/PLAN.md` (puntos 3, 4, 5, 8 y 9), `docs/DECISIONS.md` y `design/shell.md`.

## Restricciones globales

- minSdk 31, targetSdk 37 y compileSdk 37.2, como están. Versiones solo en `gradle/libs.versions.toml`.
- Permisos (plan, punto 8): el manifiesto unido de debug y de release **sin** `INTERNET`, `ACCESS_NETWORK_STATE` ni ningún permiso prohibido; solo `MainActivity` exported.
- Sin colores ni tamaños que no estén en `design/tokens.json` o en `design/shell.md`. Si algo no está, se para y se pregunta.
- Textos de la UI en inglés, con el coreano tal como viene en `shell.md`. Los textos marcados *pendiente de aprobar* no se usan hasta que el usuario los apruebe; mientras, se pone el de la web.
- No cambiar ningún ajuste del teléfono sin preguntar (CLAUDE.md).
- Mac de 8 GB: Gradle con `-Xmx2g`; antes de compilar, parar la web, Colima y Ollama.
- Logs sin texto de tus notas ni de tus respuestas.

## Riesgos que hay que probar (y en qué tarea)

1. **Letra al 130 %:** "Flashcards" se corta en la barra inferior o el título grande se monta sobre el botón More → captura Roborazzi a `fontScale = 1.3` (tarea 2).
2. **Back del sistema con la hoja More abierta:** debe cerrar la hoja, no salir de la app; desde Grammar abierto sobre Quiz, Back vuelve a Quiz (tarea 3).
3. **Volver a tocar la pestaña activa:** vuelve al inicio de su pila sin perder las pilas de las otras pestañas (tarea 3).
4. **Importar dos veces el mismo respaldo:** no duplica nada; las filas quedan iguales (tarea 5).
5. **Elegir en el selector un archivo que no es un respaldo** (un PDF, un ZIP cualquiera): error claro y la base no cambia (tarea 5).

---

## Tarea 1 — Compose, tema y fuentes

📱 **Teléfono: no hace falta** (pruebas JVM). Solo al final, para ver la app abierta con el tema: **sí**, por USB con la depuración activada.

**Archivos:**
- Modificar: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts` (Kotlin 2.4.0, plugin de Compose, BOM, Material 3, `activity-compose`; quitar AppCompat y Material Components si ya no hacen falta).
- Crear: `tools/design/gen_tokens.py`: lee `design/tokens.json` y escribe `app/src/main/java/com/dero/juststudykorean/ui/theme/Tokens.kt` (generado; cabecera "do not edit").
- Crear: en `app/src/main/java/com/dero/juststudykorean/ui/theme/`:
  - `Color.kt`: `lightScheme` y `darkScheme`, según la tabla de `shell.md`;
  - `JustStudyColors.kt`: `data class JustStudyColors` + `LocalJustStudyColors`;
  - `Type.kt`: `JustStudyTypography` + `KoreanTypography`;
  - `Shape.kt`;
  - `Motion.kt`: duraciones y curvas;
  - `Theme.kt`: `@Composable fun JustStudyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`.
- Crear: `app/src/main/res/font/poppins_regular.ttf`, `poppins_bold.ttf`, `poppins_extrabold.ttf` y `OFL.txt` (de google/fonts, `ofl/poppins`).
- Modificar: `MainActivity.kt`, que pasa a `ComponentActivity` + `enableEdgeToEdge()` + `JustStudyTheme`, y `res/values/themes.xml`.
- Test: `app/src/test/java/com/dero/juststudykorean/ui/theme/TokensTest.kt`.

**Produce:** `JustStudyTheme`, `JustStudyTheme.colors` (acceso a `LocalJustStudyColors`), `JustStudyTheme.korean` (estilos coreanos) y `Motion.Fast/Base/Slow/Press`.

- [ ] **Paso 1: prueba que falla.** `TokensTest.everyColorMatchesTokensJson`: carga `design/tokens.json` (ruta `../design/tokens.json`) y comprueba que cada token de color tiene en `Tokens.kt` el mismo valor ARGB, en light y en dark. Las cifras rgba (por ejemplo `scrim`) se convierten con el alfa redondeado a 255. `TokensTest.schemeMapping`: `lightScheme.primary == Tokens.Action`, `darkScheme.surface == Tokens.SurfaceDark`, `darkScheme.primary == Tokens.Action` (los botones no cambian).
- [ ] **Paso 2:** correrla y verla fallar.
- [ ] **Paso 3:** `gen_tokens.py` + `Tokens.kt` + el tema.
- [ ] **Paso 4:** `./gradlew :app:testDebugUnitTest` en verde; `assembleDebug`; manifiesto unido sin permisos prohibidos.
- [ ] **Paso 5:** 📱 `android run`: la app abre en el OPPO con fondo `background`, de borde a borde y en claro u oscuro según el sistema. Commit.

**Parar si:** Kotlin 2.4.0 no es compatible con AGP 9.5.1 o con Hilt o Room (anotar el error y no bajar versiones por tu cuenta).

---

## Tarea 2 — Componentes del esqueleto

📱 **Teléfono: no hace falta** (Roborazzi en la JVM).

**Archivos:**
- Crear en `app/src/main/java/com/dero/juststudykorean/ui/components/`:
  - `StickerButton.kt`: `fun StickerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, ko: String? = null, color: StickerColor = StickerColor.Action, size: StickerSize = StickerSize.Default, enabled: Boolean = true)`, con `enum class StickerColor { Action, Go, Fix, Danger, Gray }` y `enum class StickerSize { Default, Small }`;
  - `JsTopBar.kt`: `fun JsTopBar(title: String, ko: String, subtitle: String?, subtitleKo: String?, scrollBehavior: TopAppBarScrollBehavior, onMore: (() -> Unit)?, onBack: (() -> Unit)?)`;
  - `JsBottomBar.kt`: `fun JsBottomBar(current: TopLevel, onSelect: (TopLevel) -> Unit)`;
  - `MoreSheet.kt`: `fun MoreSheet(darkMode: Boolean, onDarkMode: (Boolean) -> Unit, onOpen: (MoreDestination) -> Unit, onDismiss: () -> Unit)`;
  - `ThemeSwitch.kt`;
  - `States.kt`: `EmptyState`, `LoadingState` y `ErrorState(message, onRetry)`;
  - `LucideIcon.kt`: `fun LucideIcon(icon: Lucide, contentDescription: String?, size: Dp = 24.dp, strokeWidth: Float = 2f, animateKey: Any? = null)`, con `enum class Lucide` para los 11 iconos de `shell.md`. Con `animateKey` cambiado, el icono se anima una vez como en el README del sistema; sin animación si la escala de animación del sistema es 0.
- Test: `app/src/test/java/com/dero/juststudykorean/ui/components/ShellScreenshotTest.kt` (Roborazzi), con sus capturas en `app/src/test/screenshots/`.

**Consume:** el tema de la tarea 1. **Produce:** los componentes de arriba. `TopLevel` y `MoreDestination` se definen aquí (`ui/navigation/Destinations.kt`) y la tarea 3 los usa:
```kotlin
enum class TopLevel { Dashboard, Library, Flashcards, Quiz, Teacher }
enum class MoreDestination { Grammar, Progress, Calendar, Settings }
```

- [ ] **Paso 1: capturas que fallan.** `ShellScreenshotTest` con un caso por componente y estado: los 5 colores × {normal, pulsado, deshabilitado}, Small con coreano, la barra superior abierta y plegada, la barra inferior con cada destino activo, MoreSheet, ThemeSwitch on y off, y los tres estados. Todo en claro y oscuro, a 411 dp de ancho. Más `bottomBarAt130Percent` y `topBarAt130Percent` con `fontScale = 1.3`.
- [ ] **Paso 2:** `./gradlew :app:recordRoborazziDebug` y comparar a ojo con `design/png/shell-*.png`. Las diferencias de medida o color se corrigen; si no se sabe qué está bien, se para y se pregunta.
- [ ] **Paso 3:** a 130 %, "Flashcards" no se corta (se permite que la etiqueta ocupe dos líneas) y el título no tapa el botón More.
- [ ] **Paso 4:** `./gradlew :app:verifyRoborazziDebug` en verde. Commit con las capturas.

---

## Tarea 3 — Navegación (Navigation 3), pantallas provisionales y modo oscuro

📱 **Teléfono: sí**, por USB con la depuración activada (pruebas de navegación y Back predictivo en el OPPO).

**Archivos:**
- Crear en `app/src/main/java/com/dero/juststudykorean/ui/navigation/`:
  - `AppNavigation.kt`: una pila por cada `TopLevel`; `MoreDestination` se apila sobre la pestaña actual; volver a tocar la pestaña activa la deja en su raíz;
  - `AppScaffold.kt`: el PhoneLayout de `shell.md`.
- Crear: `app/src/main/java/com/dero/juststudykorean/ui/screens/PlaceholderScreen.kt` con los textos de `shell.md` (Teacher, con el subtítulo de la web hasta que se apruebe el nuevo).
- Modificar: `AndroidManifest.xml` (`android:enableOnBackInvokedCallback="true"`).
- Test: `app/src/androidTest/java/com/dero/juststudykorean/ui/NavigationTest.kt`.

**Consume:** `TopLevel`, `MoreDestination`, `JsBottomBar`, `JsTopBar`, `MoreSheet` y `EmptyState`. **Produce:** `AppScaffold`, sobre el que se montan las pantallas de la fase 3.

- [ ] **Paso 1: pruebas que fallan** (`createAndroidComposeRule<MainActivity>`):
  - `startsOnDashboard`: se ve "Dashboard" y la pestaña tiene `selected`;
  - `eachTabShowsItsTitle`: tocar cada pestaña muestra su título;
  - `moreOpensSheetAndBackClosesIt`: More → se ve "Grammar" en la hoja → `Espresso.pressBack()` → la hoja se cierra y sigue la pantalla anterior;
  - `grammarStacksOverCurrentTab`: en Quiz → More → Grammar → Back → título "Quiz";
  - `reselectTabPopsToRoot`: en Quiz → Grammar → tocar Quiz → título "Quiz", sin flecha atrás;
  - `otherTabsKeepTheirStack`: en Flashcards → Progress → Dashboard → Flashcards → se ve "Progress".
- [ ] **Paso 2:** 📱 correrlas en el OPPO y verlas fallar.
- [ ] **Paso 3:** implementar la navegación, el scaffold y las pantallas provisionales. El modo oscuro, de momento, con estado en memoria (la tarea 4 lo guarda).
- [ ] **Paso 4:** 📱 pruebas en verde. Comprobar a mano en el OPPO: el gesto Back predictivo deja ver la pantalla anterior al deslizar, y la barra de estado cambia el color de sus iconos al pasar a oscuro. Commit.

---

## Tarea 4 — Módulos, Hilt, DataStore y `:core:korean`

📱 **Teléfono: no hace falta** (pruebas JVM). Al final, para comprobar que el modo oscuro se guarda al cerrar la app: **sí**.

**Archivos:**
- Modificar: `settings.gradle.kts` (`:core:data` y `:core:korean`) y los `build.gradle.kts`. Hilt en `:app` y en `:core:data`.
- Crear: `app/src/main/java/com/dero/juststudykorean/JustStudyApp.kt` (`@HiltAndroidApp`) y el manifiesto (`android:name`).
- Crear: `core/data/src/main/java/com/dero/juststudykorean/data/prefs/UserPrefs.kt`: `class UserPrefs @Inject constructor(...)` con estas propiedades y sus `suspend fun set…`:
  - `val themeMode: Flow<ThemeMode>`, con `enum class ThemeMode { System, Light, Dark }`;
  - `val cardDirection: Flow<CardDirection>`, con `enum class CardDirection { KoToEn, EnToKo, Both }`;
  - `val typeAnswer: Flow<Boolean>`.
- Crear: `core/korean/`, módulo JVM puro. Traer de la rama `poc/2-nano` lo ya portado y comprobado contra las doradas: `korean_cores`, `has_form`, `aeo_forms`, `past`, `is_grounded` y `leaks_answer`, con sus pruebas. Las pruebas leen `../../golden/`.
- Modificar: `MainActivity` / `AppScaffold`: el modo oscuro sale de `UserPrefs.themeMode`; el interruptor de MoreSheet guarda `Light` o `Dark`.
- Test: `core/data/src/test/.../UserPrefsTest.kt` y las de `core/korean` que vienen de la PoC.

- [ ] **Paso 1: pruebas que fallan.**
  - `UserPrefsTest.defaultsAreSystemKoToEnFalse`: sin datos, `ThemeMode.System`, `CardDirection.KoToEn` y `false`.
  - `UserPrefsTest.themeModePersists`: `setThemeMode(Dark)` → nueva instancia sobre el mismo archivo → `Dark`.
  - Las pruebas doradas de `core/korean`, movidas sin cambiar sus casos.
- [ ] **Paso 2:** verlas fallar o no compilar.
- [ ] **Paso 3:** implementar y conectar Hilt.
- [ ] **Paso 4:** `./gradlew test` en verde. 📱 Elegir Dark → cerrar la app a la fuerza → abrirla → sigue en oscuro. Commit.

**Parar si:** traer el código de `poc/2-nano` arrastra algo de ML Kit o de la PoC (solo entran esas 6 funciones y sus pruebas).

---

## Tarea 5 — Room v1 y respaldo (importar y exportar)

📱 **Teléfono: sí**, por USB con la depuración activada (pruebas instrumentadas y prueba con tu respaldo real).

**Archivos:**
- Crear: `core/data/src/main/java/com/dero/juststudykorean/data/db/`:
  - `JustStudyDatabase.kt`: versión 1 y `exportSchema = true`, con el esquema en `core/data/schemas/`;
  - las entidades y DAO de las 18 tablas del respaldo, partiendo de `PocDatabase` (rama `poc/3-room`), con nombres en camelCase y las mismas columnas.
- Crear: `core/data/src/main/java/com/dero/juststudykorean/data/backup/`:
  - `BackupImporter.kt`: `suspend fun import(uri: Uri): ImportResult`, que reemplaza todo en una sola transacción;
  - `BackupExporter.kt`: `suspend fun export(uri: Uri): ExportResult`;
  - `BackupFormat.kt`: lectura y escritura del `.jsbackup.zip` v1 según `docs/POC_PLAN.md` (*Formato del respaldo*);
  - `sealed interface ImportResult { data class Ok(val tables: Int, val rows: Int); data class Failed(val reason: BackupError) }`, con `enum class BackupError { NotABackup, WrongVersion, ChecksumMismatch, Corrupt }`.
- Crear: `app/src/main/java/com/dero/juststudykorean/ui/screens/SettingsScreen.kt` según `shell.md` (*Settings*): SAF con `OpenDocument` y `CreateDocument`, el diálogo de confirmación y los Snackbar.
- Crear: `core/data/src/androidTest/assets/backup/synthetic.jsbackup.zip`. Lo genera `tools/export` con la base SQLite de prueba de `tools/tests` (sin datos tuyos) y lo regenera un comando que queda en `tools/README.md`.
- Test: `core/data/src/androidTest/.../BackupTest.kt`.

- [ ] **Paso 1: pruebas que fallan** (en el OPPO):
  - `importsEveryRow`: el ZIP sintético importa todas las filas de su manifiesto;
  - `roundTripIsIdentical`: importar y exportar da JSONL iguales byte a byte;
  - `importTwiceDoesNotDuplicate`: importar dos veces deja las mismas filas;
  - `notABackupLeavesDbUntouched`: un PDF o un ZIP sin `manifest.json` da `NotABackup` y deja las filas de antes;
  - `checksumMismatchRollsBack`: con una fila cambiada y el sha256 viejo, da `ChecksumMismatch` y deja las filas de antes (no una base vacía: la base ya tenía datos).
- [ ] **Paso 2:** 📱 verlas fallar.
- [ ] **Paso 3:** implementar, pasando la PoC a código limpio con streaming, el sha256 antes de escribir y una sola transacción.
- [ ] **Paso 4:** 📱 pruebas en verde. Después, a mano en el OPPO: copiar tu respaldo real a Descargas con `adb push` (sin tocar ajustes), Settings → Import backup → 1265 filas de 18 tablas; Export backup → el archivo se guarda. En el informe, solo cifras.
- [ ] **Paso 5:** commit, con el esquema JSON de Room incluido.

**Parar si:** el esquema de la PoC no encaja con el formato v1, o una tabla del respaldo no tiene entidad.

---

## Tarea 6 — CI en GitHub Actions

📱 **Teléfono: no hace falta.**

**Archivos:**
- Crear: `.github/workflows/ci.yml`. En cada PR y en cada push a `main`, en `ubuntu-latest` con JDK 21: `./gradlew test verifyRoborazziDebug lint assembleDebug assembleRelease`, y después `tools/ci/check_manifest.py`.
- Crear: `tools/ci/check_manifest.py`: lee los manifiestos unidos de debug y release. Sale con código 1 si hay algún permiso de la lista prohibida del plan (punto 8), alguna actividad, servicio, receptor o proveedor `exported="true"` que no sea `MainActivity`, o `android:debuggable="true"` en release.
- Test: `tools/tests/test_check_manifest.py`, con dos manifiestos de ejemplo (uno limpio y uno con `INTERNET`).

- [ ] **Paso 1:** la prueba Python, que falla.
- [ ] **Paso 2:** el script; la prueba en verde.
- [ ] **Paso 3:** el workflow. Un PR de prueba pasa en verde.
- [ ] **Paso 4:** el README del proyecto (o `tools/README.md`) explica cómo correr lo mismo en local. Commit.

---

## Al terminar la fase

- [ ] Una entrada en `docs/DECISIONS.md` por cada versión fijada (Kotlin 2.4.0, Navigation 3, Room 3, Roborazzi) y la del respaldo, que reemplaza todo con confirmación.
- [ ] `mem_save` con `topic_key` `android/fase-2`.
- [ ] Un PR por tarea (o uno por dos tareas pequeñas), sin mergear sin confirmación.
