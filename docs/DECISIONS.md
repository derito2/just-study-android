# Decisiones — Just Study Android

Una entrada por decisión, la más reciente arriba: fecha, decisión, por qué y alternativas descartadas. Las de los PoC (fase 1) también van a Engram (`android/*`).

## 2026-11-07 — La fase 2 empieza configurando Compose
**Decisión:** el proyecto se creó con la plantilla *No Activity* y no trae Compose. Lo primero de la fase 2 es configurarlo: BOM de Compose, plugin `org.jetbrains.kotlin.plugin.compose` y tema Material 3. Hasta entonces `MainActivity` es un marcador provisional (AppCompat + `TextView`).
**Por qué:** la fase 0 solo deja la base segura y que la APK abra en el OPPO; las pantallas y el tema llegan con el diseño de la fase 2.
**Descartado:** configurar Compose ya en la fase 0 (adelanta trabajo de la fase 2 sin diseño).

## 2026-11-07 — Sin `ProfileInstallReceiver` hasta la fase 7
**Decisión:** el manifiesto quita `androidx.profileinstaller.ProfileInstallReceiver` (`tools:node="remove"`, con `tools:ignore="MissingClass"` porque la clase solo está en runtime). En la fase 7 vuelve, **solo en un build type de benchmark**, para medir y generar Baseline Profiles con macrobenchmark.
**Por qué:** la librería lo declara `exported="true"` (protegido con `DUMP`) y el plan (punto 8) exige que solo la actividad principal sea exported. El perfil se sigue instalando al arrancar mediante `ProfileInstallerInitializer`.
**Descartado:** dejarlo exported en todos los builds.
