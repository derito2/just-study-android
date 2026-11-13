# Ficha: los 13 minijuegos de Games (Fase 8)

Base: `design/games.md` (escenario, tablero, piezas, movimientos `Fly`/`Pop`/`Shake`/`Flip`/`Enter`/`Exit`/`Stagger`, cinta de feedback y resultados *won / lost / neutral / skipped*). Reglas de datos y generación: `docs/FASE_8_PLAN.md`, sección 4.

En cada juego: **Contenido** (qué necesita de tus notas), **Niveles** (1 fácil → 3 difícil; el motor elige), **Pantalla**, **Cómo se juega**, **Resultado**, **Animaciones** y **Sin arrastrar** (accesibilidad). Los textos visibles van tal cual, en inglés, con su coreano.

Medidas sobre un tablero de 379 dp de ancho útil (411 − 2 × 16).

| # | Juego · coreano | Familia de gesto | Habilidad | Atrezo |
|---|---|---|---|---|
| 1 | Hangul Hangman · 행맨 | teclado | escritura | faroles (vidas) |
| 2 | Word Match · 짝 맞추기 | emparejar | vocabulario | — |
| 3 | Sentence Builder · 문장 만들기 | ordenar | estructura | — |
| 4 | Memory Flip · 카드 뒤집기 | emparejar | memoria | — |
| 5 | Word Scramble · 음절 섞기 | ordenar | vocabulario | — |
| 6 | Listen & Guess · 듣고 맞히기 | escuchar | escucha | — |
| 7 | Hangul Builder · 글자 만들기 | construir | escritura | — |
| 8 | Word Sort · 분류하기 | arrastrar | vocabulario | — |
| 9 | Grammar Fixer · 문법 고치기 | corregir | gramática | — |
| 10 | Word Recall · 순서 기억하기 | ordenar | memoria | faroles (secuencia) + luna |
| 11 | Hangul Typing · 한글 타자 | teclado | escritura | luna (reloj) |
| 12 | Korean Word Path · 단어 길 | trazar | vocabulario | — |
| 13 | Sound Detective · 소리 탐정 | escuchar | escucha | — |

---

## 0 · Hangul en pantalla (común a 1, 5, 7, 11 y 12)

- **Jamo:** las 19 consonantes iniciales (ㄱ ㄲ ㄴ ㄷ ㄸ ㄹ ㅁ ㅂ ㅃ ㅅ ㅆ ㅇ ㅈ ㅉ ㅊ ㅋ ㅌ ㅍ ㅎ) y las 21 vocales (ㅏ ㅐ ㅑ ㅒ ㅓ ㅔ ㅕ ㅖ ㅗ ㅘ ㅙ ㅚ ㅛ ㅜ ㅝ ㅞ ㅟ ㅠ ㅡ ㅢ ㅣ).
- **Una consonante doble (ㄲ ㄸ ㅃ ㅆ ㅉ) es una pieza propia**, nunca dos sencillas.
- **Un 받침 compuesto (ㄳ ㄵ ㄶ ㄺ ㄻ ㄼ ㄽ ㄾ ㄿ ㅀ ㅄ) son dos piezas sencillas** (ㄺ = ㄹ + ㄱ), en ese orden.
- **Una vocal compuesta (ㅘ ㅙ ㅚ ㅝ ㅞ ㅟ ㅢ)** es una pieza propia en los niveles 1 y 2, y dos piezas que se funden (ㅘ = ㅗ + ㅏ) en el nivel 3 de Hangul Builder y en el teclado de Hangul Typing.
- **Forma del bloque** (para dibujar huecos por posición):
  - vocal vertical (ㅏ ㅐ ㅑ ㅒ ㅓ ㅔ ㅕ ㅖ ㅣ): inicial a la izquierda, vocal a la derecha;
  - vocal horizontal (ㅗ ㅛ ㅜ ㅠ ㅡ): inicial arriba, vocal debajo;
  - vocal mixta (ㅘ ㅙ ㅚ ㅝ ㅞ ㅟ ㅢ): inicial arriba a la izquierda, vocal envolviendo por abajo y por la derecha;
  - 받침: una franja abajo (dos casillas si es compuesto).
- **SyllableFrame:** cuadrado de 96 dp (Hangul Builder) o 56 dp (Hangman en modo jamo), borde 2 dp `line`, radio 12, con las casillas de arriba dibujadas a rayas (`field`). Cuando todas sus casillas están llenas, las piezas se funden en el glifo real (ver animación *Componer*).

**Animación *Componer*** (jamo → sílaba): las piezas se acercan al centro del marco (escala 1 → 0.85, 150 ms ease-in), se funden a opacidad 0 mientras el glifo compuesto aparece (opacidad 0 → 1, escala 0.9 → 1.08 → 1, 240 ms curva `Spring`). Con *Quitar animaciones*: cambio directo.

### Teclado propio (HangulKeyboard)

Nunca se abre Gboard: ningún juego tiene un `TextField`.

- **Disposición 두벌식** (la de Gboard coreano), tres filas más la de acciones, pegado abajo del tablero:
  - fila 1: ㅂ ㅈ ㄷ ㄱ ㅅ ㅛ ㅕ ㅑ ㅐ ㅔ
  - fila 2: ㅁ ㄴ ㅇ ㄹ ㅎ ㅗ ㅓ ㅏ ㅣ
  - fila 3: **⇧** ㅋ ㅌ ㅊ ㅍ ㅠ ㅜ ㅡ **⌫**
  - fila 4: **space** (ancha) y **Check** · 확인 (`Go`, 96 dp)
- **⇧** cambia ㅂ ㅈ ㄷ ㄱ ㅅ → ㅃ ㅉ ㄸ ㄲ ㅆ y ㅐ ㅔ → ㅒ ㅖ para una sola pulsación (queda marcada con fondo `tint` y borde `brand` mientras está activa).
- **Teclas:** 34 dp de ancho × 46 dp de alto con 4 dp entre ellas (10 teclas = 376 dp); zona táctil hasta el centro del hueco; radio 8; fondo `canvas`, canto inferior de 2 dp `field`; glifo 18 sp, 700. Pulsada: baja 2 dp, 90 ms, y sale una burbuja de 44 × 52 dp encima con el glifo a 24 sp (aparece en 60 ms, se va en 90 ms).
- **⌫** (`delete`): borra el último jamo del bloque que se está componiendo; si no hay bloque a medias, borra el bloque anterior entero. Mantener pulsado repite cada 80 ms tras 400 ms.
- La composición sigue las reglas del 두벌식 (`docs/FASE_8_PLAN.md`, 3.2): 간 + ㅏ → 가나; 달 + ㄱ → 닭; 닭 + ㅣ → 달기.
- TalkBack: cada tecla dice su nombre coreano ("기역", "쌍기역", "아"…).

---

## 1 · Hangul Hangman · 행맨

Descubre la palabra antes de que se apaguen los tres faroles.

**Por qué faroles y no una horca:** con solo tres fallos, una horca dibujada serían tres trazos; tres faroles colgados del alero de la pagoda dicen lo mismo (vidas) con el lenguaje de las pantallas de carga, y sin dibujar un ahorcado.

- **Contenido:** una palabra de 2–5 bloques de tus notas (nivel 3: una expresión o frase corta de hasta 8 bloques y 2 espacios) con su significado.
- **Niveles:** 1 = **modo sílabas**; 2 = **modo jamo**; 3 = modo jamo con frase.
- **Instrucción:** *Find the word before the lanterns go out.* · 등불이 꺼지기 전에 단어를 찾으세요. Pista: el significado.

**Atrezo:** una cuerda (1.5 dp, `sky-light` al 60 %) desde el alero de la pagoda (74, 96) hasta (250, 88) con una catenaria de 10 dp, y **tres faroles** colgando en x = 120, 170, 220: cuerpo `lantern-red` de 16 × 22 dp (radio 6), tapa y base `night-deep` de 10 × 3 dp, luz interior `lantern` de 8 × 12 dp y halo radial de 36 dp `lantern` al 35 %. Encendidos se balancean ±2.5° (periodo 3.2 s, desfasados 0.4 s); el halo respira 0.30 ↔ 0.40.

**Pantalla:**
- **Arriba, la palabra:** un hueco por bloque, centrados, 8 dp entre ellos; los espacios de una frase son un hueco de 16 dp sin borde.
  - Modo sílabas: Slot de 56 dp por bloque.
  - Modo jamo: SyllableFrame de 56 dp por bloque, con sus casillas (se ve cuántos jamo tiene cada bloque y dónde van).
- **Abajo, las teclas:**
  - **Modo sílabas:** 10 bloques silábicos en 2 filas de 5 (Tiles de 64 × 56 dp): **todos** los bloques distintos de la palabra más distractores. Los distractores son bloques de otras palabras de tus notas que comparten la consonante inicial o la vocal con alguno de la palabra y no están en ella. Si la palabra tiene más de 7 bloques distintos, hay 12 teclas (2 filas de 6, a 56 dp).
  - **Modo jamo:** el teclado de jamo completo en 6 filas de 7 (Tiles de 48 × 44 dp, 4 dp entre ellas): 14 consonantes sencillas (2 filas), 5 dobles + 2 huecos vacíos (1 fila), 21 vocales (3 filas). Fondo de la fila de vocales `tint` para separar a la vista. Siempre está el teclado entero: cualquier palabra se puede resolver.

**Cómo se juega:**
- Tocar una tecla la gasta. **Acierto:** se revela en **todas** las posiciones donde aparece. En modo jamo, una consonante revela sus apariciones como inicial **y** como 받침 (también dentro de un 받침 compuesto); ㄱ no revela ㄲ; ㅗ no revela ㅘ.
- **Fallo:** se apaga un farol (de derecha a izquierda).
- No hay botón de confirmar.

**Resultado:** *won* al revelar todo; *lost* al tercer fallo (se revela la palabra). Se guarda el número de fallos.

**Animaciones:**
- **Tecla acertada:** `Pop`; queda en estado *Correcta* y deshabilitada.
- **Revelado, modo sílabas:** una copia de la tecla vuela (`Fly`) a cada hueco donde va (si son varios, con `Stagger`); al llegar, el hueco hace `Pop`.
- **Revelado, modo jamo:** el jamo vuela a su casilla dentro del marco; cuando un marco se completa, *Componer*.
- **Tecla fallada:** `Shake`; queda en estado *Incorrecta* y deshabilitada. A la vez, en la escena: el farol da un tirón (rotación a 8° y vuelta, 240 ms), su luz y su halo se apagan (opacidad → 0, 320 ms ease-out), el cuerpo pasa a `night-shade`, y sube un hilo de humo (trazo de 1.5 dp `sky-light` al 50 %, 14 dp de alto, ondulado, que sube 10 dp y se desvanece en 600 ms). Un farol apagado deja de balancearse.
- **Ganar:** los bloques hacen `Pop` en ola (`Stagger`); los faroles que quedan encendidos brillan (halo a 0.7 y de vuelta, 400 ms).
- **Perder:** el último farol se apaga como los otros; la escena se oscurece (velo `night-deep` al 35 %, 400 ms; se quita en la transición); los bloques sin revelar aparecen con fundido (240 ms) en `wrong`, con borde `wrong-border`.

**Sin arrastrar:** todo son toques. TalkBack tras cada tecla: "{letra}: yes, {n} places" o "{letra}: no. {k} lanterns left".

---

## 2 · Word Match · 짝 맞추기

- **Contenido:** 5 pares (4 con la letra al 130 %) palabra ↔ significado; nivel 2–3: 6 pares. Nivel 3: frases cortas ↔ traducción. Ningún significado de la ronda comparte sentido con otro (`englishSenses` sin intersección) ni hay dos coreanos iguales.
- **Instrucción:** *Match each word with its meaning.* · 뜻과 짝을 맞추세요.

**Pantalla:** dos columnas de 172 dp con 35 dp entre ellas (por ahí pasan las líneas). Izquierda: coreano (`ko-title`); derecha: significado (`body-bold`, hasta 2 líneas con elipsis). Tiles de alto mínimo 56 dp, 10 dp entre filas. Cada columna barajada por separado. Cada Tile tiene un **punto de anclaje** de 8 dp (`field`) en el centro de su borde interior.

**Cómo se juega:** tocar una ficha de un lado (queda *Elegida*) y luego una del otro. O **arrastrar** desde una ficha: sale una línea que sigue al dedo y se suelta sobre la otra columna. Tocar otra ficha del mismo lado cambia la elección; tocar la elegida la suelta.

**Resultado:** termina al emparejar todo. *won* con 2 fallos o menos; *lost* con 3 o más (se termina igual, porque completar las parejas es el aprendizaje). Cada fallo se apunta a las dos palabras implicadas.

**Animaciones:**
- **Línea:** curva Bézier entre los dos anclajes (puntos de control a 40 % de la distancia horizontal), 3 dp, extremos redondos. Al tocar la segunda ficha se dibuja de origen a destino en 180 ms ease-out (trim del trazo). Arrastrando, sigue al dedo en `brand`.
- **Pareja correcta:** la línea pasa a `correct-deco`; las dos fichas hacen `Pop` y quedan *Correctas*; sus anclajes se rellenan. 400 ms después, la línea y las dos fichas bajan a opacidad 0.45 (240 ms) para despejar la vista. No se mueven de sitio.
- **Pareja incorrecta:** la línea se pone `wrong-border`, las dos fichas hacen `Shake`, y la línea se retrae hacia el origen en 180 ms. Las fichas vuelven a su estado normal.
- **Última pareja:** todas las líneas brillan a opacidad 1 en ola de arriba abajo (`Stagger`), 240 ms.

**Sin arrastrar:** el modo de dos toques. TalkBack: "{palabra}, not matched" / "matched with {significado}".

---

## 3 · Sentence Builder · 문장 만들기

- **Contenido:** una frase de tus notas con traducción, de 3–7 palabras (nivel 1: 3–4; 2: 4–5; 3: 5–7). Las fichas son las palabras separadas por espacios, tal cual (con sus partículas). El signo final (. ? !) no es ficha: se ve fijo al final de la línea.
- **Instrucción:** *Build the sentence as in your notes.* · 노트에 있는 대로 문장을 만드세요.

**Pantalla:**
- **Recuadro de la traducción** (`canvas`, radio 12, relleno 12 dp): la traducción en inglés (`body`, 16 sp).
- **Línea de respuesta:** recuadro con borde 2 dp `line`, radio 12, alto mínimo 64 dp; un Slot por palabra, con salto de línea (FlowRow, 8 dp).
- **Bandeja:** las fichas barajadas (nunca en el orden correcto), en FlowRow centrado.
- **Pie:** **Check** · 확인 (`Action`, ancho completo), deshabilitado hasta llenar todos los huecos.

**Cómo se juega:** tocar una ficha de la bandeja la manda al primer hueco libre; tocar una ficha colocada la devuelve a la bandeja; **arrastrar** una colocada sobre otra las intercambia (o la inserta entre dos, desplazando). Se puede cambiar todo antes de *Check*.

**Resultado:**
- igual que en tus notas → *won*;
- mismas palabras en otro orden, **con el mismo final** (la última palabra es la de tus notas) → *neutral*, con la cinta *Your notes say it this way:* y la frase. El coreano admite más de un orden y la app no puede comprobar cuáles: por eso no cuenta como error;
- cualquier otra cosa → *lost*, con la frase correcta.
- Si tus notas tienen otra frase con las mismas palabras y la misma traducción, también vale como *won*.

**Animaciones:**
- **Colocar / devolver:** `Fly` de la ficha entre bandeja y hueco; en la bandeja queda su hueco *Usado* (opacidad 0.35), para que la bandeja no baile.
- **Reordenar arrastrando:** la ficha levantada crece a 1.06 con sombra de 8 dp; las demás se apartan con `Fly`.
- **Check correcto:** las fichas hacen `Pop` en ola de izquierda a derecha (`Stagger`), y después se juntan (los 8 dp entre ellas pasan a 4 dp, 240 ms) con el punto final en `correct`.
- **Check incorrecto:** las fichas fuera de sitio hacen `Shake` y se marcan *Incorrectas*; las que estaban bien, *Correctas*. 600 ms después, las fichas vuelan (`Fly`, `Stagger`) al orden de tus notas.
- **Neutral:** sin `Shake`; las fichas vuelan al orden de tus notas con borde `tint-border`.

**Sin arrastrar:** tocar para colocar y devolver basta para cualquier orden.

---

## 4 · Memory Flip · 카드 뒤집기

- **Contenido:** nivel 1: 6 pares (rejilla 3 × 4); nivel 2: 8 pares (4 × 4); nivel 3: 8 pares y las cartas que no coinciden se ven menos tiempo. Pares palabra ↔ significado, sin sentidos repetidos (como Word Match).
- **Instrucción:** *Find the pairs.* · 짝을 찾으세요.

**Pantalla:** rejilla centrada, 8 dp entre cartas. Cartas de 3 × 4: 118 × 96 dp; de 4 × 4: 88 × 88 dp. Con la letra al 130 % siempre 3 columnas.
- **Dorso:** fondo `action`, radio 12, y el glifo **한** en blanco al 25 % (28 sp, 800) en el centro, con un filo interior de 1 dp blanco al 15 % a 6 dp del borde.
- **Cara coreana:** fondo `tint`, borde 2 dp `tint-border`, texto `ko-title`; etiqueta **한** (10 sp, `brand-text`) arriba a la izquierda.
- **Cara del significado:** fondo `canvas`, borde 2 dp `line`, texto `body-bold` (hasta 3 líneas, se reduce a 12 sp si no cabe); etiqueta **EN** (10 sp, `muted`).

**Cómo se juega:** tocar una carta la gira; tocar la segunda la gira. Si son pareja se quedan; si no, se ven **1000 ms** (nivel 3: **700 ms**) y vuelven. Durante esa espera no se puede tocar otra carta; tocar la pantalla las cierra antes (mínimo 300 ms vistas).

**Resultado:** siempre *won* al completar. Se guardan los intentos fallidos; una palabra con 3 o más intentos fallidos se apunta como fallada.

**Animaciones:**
- **Entrada:** las cartas caen boca abajo en ola por filas (`Enter` + `Stagger` de 24 ms).
- **Giro:** `Flip`. A mitad del giro (90°) la carta está de canto y cambia de cara; sube 4 dp durante el giro y vuelve.
- **Pareja:** las dos hacen `Pop`, su borde pasa a `correct-deco` con `check`, y una chispa: 6 puntos de 3 dp `correct-deco` salen en radial 14 dp desde el centro de cada carta y se desvanecen (320 ms). Después bajan a opacidad 0.55.
- **No pareja:** `Shake` corto de las dos (240 ms, ±4 dp), espera, y `Flip` de vuelta.
- **Final:** todas las cartas dan un `Flip` completo en ola diagonal (`Stagger` de 30 ms) y quedan boca arriba.

**Sin arrastrar:** todo son toques. TalkBack: "Card {n}, face down" / "{texto}, Korean" / "matched".

---

## 5 · Word Scramble · 음절 섞기

- **Contenido:** una palabra de tus notas de 2–5 bloques (nivel 1: 2–3; 2: 3–4; 3: 4–5 **más un bloque de sobra** que no pertenece). No vale una palabra cuyos bloques, en otro orden, formen otra palabra de tus notas, ni una con todos los bloques iguales.
- **Instrucción:** *Put the syllables in order.* · 음절을 순서대로 놓으세요. Pista: el significado.

**Pantalla:** arriba, una fila de Slots de 56 dp (uno por bloque de la palabra), centrada. Debajo, a 32 dp, la fila de bloques barajados (nunca en el orden correcto).

**Cómo se juega:** tocar un bloque lo manda al primer hueco libre; tocar uno colocado lo devuelve; **arrastrar** entre huecos intercambia. **Se comprueba solo** al llenar el último hueco.

**Resultado:** correcto → *won*. Incorrecto → los bloques mal puestos vuelven a la bandeja y se puede seguir; al **tercer** intento incorrecto → *lost* y se arma sola.

**Animaciones:**
- **Colocar / devolver / intercambiar:** `Fly`.
- **Correcto:** los bloques se juntan (los huecos entre ellos se cierran, 240 ms ease-out) hasta formar una sola pieza con borde `correct-deco`, que hace `Pop`; debajo aparece el significado (`Enter`).
- **Incorrecto:** `Shake` de toda la fila; los bloques bien colocados quedan con borde `correct-deco` y se bloquean; los mal colocados vuelan de vuelta a la bandeja (`Fly`, `Stagger`). El contador de intentos son tres puntos de 6 dp bajo la fila (`field`; gastado: `wrong-border`).
- **Bloque de sobra (nivel 3):** al ganar, el que sobra se desvanece (`Exit`).
- **Perdida:** los bloques vuelan uno a uno a su hueco (`Stagger` de 80 ms) con borde `wrong-border`.

**Sin arrastrar:** tocar para colocar y devolver.

---

## Audio (común a 6 y 13)

La voz es la **voz coreana sin conexión del propio teléfono** (el motor de texto a voz de Android). Si el teléfono no tiene una voz coreana instalada que funcione sin red, **estos dos juegos no salen** y la hoja de pausa lo explica (`games.md`, 9). Nunca hay una ronda de audio sin audio.

- **ReplayButton** (56 dp, `action`, `volume-2`): mientras suena, tres ondas concéntricas salen del botón (anillos de 2 dp `brand`, radio 28 → 52 dp, opacidad 0.6 → 0, 900 ms cada una, una cada 300 ms) y el icono late (escala 1 → 1.08, 400 ms ida y vuelta). Al terminar, las ondas en curso acaban y no salen más.
- **Visualizador de voz (VoiceBars):** junto al botón, 5 barras verticales de 4 dp de ancho y 4 dp de separación, radio 2, `brand`. En reposo miden 6 dp. Mientras suena, cada barra oscila entre 6 y 28 dp con periodos distintos (310, 370, 430, 290, 350 ms, ease-in-out). No representan el audio real (el motor de voz no da su onda): son un indicador de «está sonando».
- **Repeticiones:** puntos de 6 dp bajo el botón, uno por repetición disponible (`brand`; gastado, `field`). Sin repeticiones: botón deshabilitado (`disabled-fill`).
- **La primera vez suena sola**, 400 ms después de que entren las piezas.
- **Volumen a cero:** en vez de sonar, sale una píldora `warn-fill` sobre el botón: *Turn the volume up to play.* (12 sp, `fill-warn`), y la ronda espera. No cuenta como fallo.
- **Si otra app toma el audio** o entra una llamada: pausa (`games.md`, 9).
- Con *Quitar animaciones*: sin ondas ni barras en movimiento; el botón cambia a fondo `brand` mientras suena.
- TalkBack: el botón dice "Play the audio again, {n} left".

---

## 6 · Listen & Guess · 듣고 맞히기

Tres audios seguidos sobre el mismo tablero de cartas: no es una pregunta con opciones, es ir «cazando» cada carta por el oído.

- **Contenido:** 6 palabras de tus notas con significados sin sentidos repetidos; 3 de ellas (al azar) son las que suenan. Nivel 1: las cartas muestran el **significado**. Nivel 2: las cartas muestran el **coreano** (reconocer la forma escrita de lo que oyes) y las 6 palabras se eligen parecidas (misma consonante inicial o mismo número de bloques). Nivel 3: 4 frases cortas, cartas con la traducción, suenan 2.
- **Instrucción:** *Tap the card you hear.* · 들리는 카드를 누르세요.

**Pantalla:** arriba, centrado, ReplayButton con VoiceBars a su derecha y el marcador de objetivo a su izquierda (**1 / 3**, `label-caps`, `muted`). Debajo, las cartas en rejilla de 2 columnas × 3 filas (Tiles de 185 × 72 dp, 8 dp entre ellas).

**Cómo se juega:** suena el objetivo; se toca una carta. 2 repeticiones por objetivo.
- **Acierto:** la carta queda resuelta y suena el siguiente objetivo tras 700 ms.
- **Fallo:** se puede intentar una vez más con el mismo audio; al segundo fallo se señala la carta correcta y se pasa al siguiente.

**Resultado:** *won* si al menos 2 de 3 (nivel 3: 2 de 2) salieron al primer intento; si no, *lost*. Cada objetivo fallado se apunta a su palabra.

**Animaciones:**
- **Acierto:** la carta hace `Pop`, queda *Correcta* y aparece debajo de su texto el otro idioma (`Enter`): en nivel 1, el coreano; en nivel 2, el significado. Una onda de 2 dp `correct-deco` viaja del botón a la carta (línea que se dibuja en 200 ms y se desvanece).
- **Fallo:** la carta tocada hace `Shake` y queda *Incorrecta* 600 ms, luego vuelve a normal.
- **Segundo fallo:** la correcta parpadea su borde en `brand` dos veces (2 × 240 ms) y queda con borde `wrong-border` y su otro idioma visible.
- **Cartas resueltas:** opacidad 0.55, no se pueden tocar.

**Sin arrastrar:** todo son toques.

---

## 7 · Hangul Builder · 글자 만들기

Arma con jamo el bloque que le falta a una palabra de tus notas.

- **Contenido:** una palabra de 2–4 bloques; se quita uno. Nivel 1: bloque sin 받침. Nivel 2: con 받침 sencillo, o consonante doble. Nivel 3: vocal compuesta hecha con dos vocales (ㅗ + ㅏ), o 받침 compuesto.
- **Instrucción:** *Build the missing block.* · 빠진 글자를 만드세요. Pista: el significado.

**Pantalla:**
- **La palabra** arriba, centrada: sus bloques en Tiles de 56 dp y, en el lugar del que falta, un hueco de 56 dp con borde `brand` a rayas y **?**.
- **SyllableFrame** de 96 dp en el centro, con las casillas del bloque que falta (se ve su forma: cuántas piezas y dónde).
- **Bandeja de jamo:** 6 Tiles de 56 dp (nivel 3: 8) en una o dos filas: las piezas necesarias más distractores **parecidos** (ㅏ/ㅓ, ㅗ/ㅜ, ㄱ/ㅋ/ㄲ, ㄷ/ㅌ/ㄸ, ㅂ/ㅍ/ㅃ, ㅈ/ㅊ/ㅉ, ㅅ/ㅆ, ㅐ/ㅔ). Ningún distractor puede dar otra respuesta válida: si cambiando una pieza sale otra palabra de tus notas con el mismo significado, ese distractor no entra.

**Cómo se juega:** tocar un jamo lo manda a la primera casilla libre de su tipo (consonante → inicial y, si ya está, 받침; vocal → vocal). Tocar una casilla llena devuelve la pieza. También se puede **arrastrar** a una casilla concreta. **Se comprueba solo** al llenar todas las casillas.

**Resultado:** correcto → *won*. Incorrecto → las piezas equivocadas vuelven y se sigue; al **tercer** intento incorrecto → *lost* y se arma solo.

**Animaciones:**
- **Colocar:** `Fly` a la casilla; al llegar, la pieza toma el tamaño y la posición que tiene dentro de la sílaba real (una ㄱ inicial junto a ㅏ es estrecha y alta; sobre ㅗ es ancha y baja): escala no uniforme, 150 ms ease-out.
- **Vocal compuesta (nivel 3):** al colocar la segunda vocal, las dos se funden en la casilla (150 ms).
- **Correcto:** *Componer* en el marco; el glifo resultante vuela (`Fly`) al hueco **?** de la palabra, encogiéndose de 96 a 56 dp; la palabra completa hace `Pop` y pasa a borde `correct-deco`.
- **Incorrecto:** el marco hace `Shake`; se ve 500 ms el bloque que se ha formado (para aprender del error: «eso es 멀, no 말»), y las piezas equivocadas vuelven a la bandeja con `Fly`; las correctas se quedan.
- **Perdida:** las piezas correctas vuelan solas, *Componer*, y el bloque va a la palabra con borde `wrong-border`.

**Sin arrastrar:** tocar coloca en la primera casilla libre de su tipo; con TalkBack las casillas dicen "Initial consonant, empty", "Vowel, ㅏ", "Final consonant, empty".

---

## 8 · Word Sort · 분류하기

- **Contenido:** palabras de tus notas que caen **en una sola** categoría. Dos fuentes:
  - **tema** (las 15 categorías fijas del Coach: *colors, family, animals, body, food, drinks, weather, clothes, numbers, days, emotions, transportation, jobs, house, school*): una palabra entra si su significado nombra algo de la categoría y de ninguna otra de la ronda;
  - **tipo de palabra:** *Verbs* (significado «To …» que no es «To be …»), *Adjectives* (significado «To be …»), *Nouns* (no acaba en 다 y su significado no empieza por «To»).
  - Hace falta un mínimo de 3 palabras por contenedor. Si tus notas no dan para dos contenedores, el juego no sale.
- **Niveles:** 1 = 2 contenedores y 6 palabras, con el significado visible bajo cada ficha; 2 = 3 contenedores y 8 palabras, significado oculto hasta colocarla; 3 = 3 contenedores y 9 palabras, oculto.
- **Instrucción:** *Sort each word into its group.* · 단어를 알맞은 그룹에 넣으세요.

**Pantalla:**
- **Contenedores** arriba, en fila, mismo ancho (2: 185 dp; 3: 121 dp), alto 132 dp, radio 16, fondo `canvas`, borde 2 dp `line` a rayas. Cabecera con el nombre (`label-caps`, `muted`) y su coreano (색깔, 가족, 동물, 몸, 음식, 음료, 날씨, 옷, 숫자, 날, 감정, 교통, 직업, 집, 학교; 동사, 형용사, 명사). Las fichas ya colocadas se apilan dentro, pequeñas (alto 28 dp, 12 sp), en FlowRow.
- **Bandeja** abajo: las fichas en FlowRow centrado (`ko-title`; en nivel 1, el significado debajo a 11 sp `muted`).

**Cómo se juega:** **arrastrar** una ficha a un contenedor, o tocar la ficha (*Elegida*) y luego el contenedor.

**Resultado:** termina al colocar todas. *won* con 2 fallos o menos; *lost* con 3 o más (se termina igual). Cada fallo se apunta a su palabra.

**Animaciones:**
- **Arrastrando:** la ficha crece a 1.06 con sombra de 8 dp y se inclina 3° hacia donde se mueve; el contenedor bajo el dedo pasa a borde `brand` continuo y fondo `tint`, y crece a 1.02 (150 ms).
- **Soltar bien:** la ficha vuela (`Fly`) a su sitio dentro del contenedor encogiéndose al tamaño pequeño; el contenedor hace un rebote (escala 1.02 → 0.99 → 1, 240 ms) y su borde pasa 400 ms a `correct-deco`; el contador de la cabecera (*2 / 3*) sube.
- **Soltar mal:** el contenedor hace `Shake` con borde `wrong-border`; la ficha vuelve a la bandeja con `Fly` y un arco (sube 16 dp a mitad de camino); queda 600 ms en *Incorrecta*.
- **Modo de toques:** con una ficha elegida, los contenedores laten una vez (borde `brand`, 400 ms) para indicar que se pueden tocar.
- **Final:** los contenedores pasan a borde `correct-deco` continuo en ola; las fichas dentro muestran su significado (`Enter`).

**Sin arrastrar:** tocar ficha y luego contenedor.

---

## 9 · Grammar Fixer · 문법 고치기

Una frase de tus notas con **una partícula mal puesta**. Hay que encontrarla y arreglarla.

- **Contenido (solo errores que una regla puede comprobar):** se toma una frase de tus notas y, en **una** palabra formada por *un sustantivo de tus notas + una partícula*, se cambia la partícula por su pareja equivocada según el 받침:

  | Tras vocal | Tras consonante |
  |---|---|
  | 는 | 은 |
  | 가 | 이 |
  | 를 | 을 |
  | 와 | 과 |
  | 랑 | 이랑 |
  | 로 (y tras ㄹ) | 으로 |

  Ejemplo: *학교를* → *학교을*. Ese error es **siempre** incorrecto en coreano, así que la app puede afirmarlo sin modelo. Una frase solo vale si la palabra es exactamente *sustantivo conocido + partícula* y la forma equivocada no es otra palabra de tus notas.
- **Lo que este juego no hace (y por qué):** errores de significado (에 por 를), conjugación u orden. Sin un analizador gramatical no se puede garantizar que la frase «rota» sea de verdad incorrecta ni que tenga una sola corrección; la especificación pide no generar esos casos. Queda para una fase posterior.
- **Niveles:** 1 = frases de 2–4 palabras, 2 opciones para arreglar; 2 = 4–6 palabras, 3 opciones; 3 = hasta 8 palabras, 3 opciones, y la frase puede tener **otras** partículas correctas que despistan.
- **Instrucción:** *One particle is wrong. Tap the word.* · 조사가 하나 틀렸어요. 단어를 누르세요.

**Pantalla:**
- **La frase** en el centro, palabra por palabra, en Tiles (`ko-prompt`, FlowRow, 8 dp), con el signo final fijo.
- Debajo, la **traducción** (`body`, `muted`).
- **Zona de arreglo** (vacía al empezar).

**Cómo se juega:**
1. **Tocar la palabra equivocada.** Si es la correcta, la ficha se parte en dos: *raíz* | *partícula*, con la partícula en `wrong`. Si no lo es: `Shake`, un fallo; al segundo fallo se señala sola.
2. **Elegir el arreglo:** en la zona de arreglo aparecen 2–3 fichas de partícula: la correcta y otras que **no encajan** tras esa palabra (formas de otras parejas para el otro tipo de final). Solo una es posible. Tocar una.

**Resultado:** *won* si se encontró la palabra a la primera o a la segunda **y** se eligió bien el arreglo a la primera; si no, *lost*. Siempre termina con la frase corregida a la vista.

**Animaciones:**
- **Partir la palabra:** la ficha se abre por la costura entre raíz y partícula: las dos mitades se separan 6 dp (180 ms ease-out), la partícula toma fondo `wrong-fill` y borde `wrong-border`, y tiembla ±1.5° dos veces (2 × 160 ms).
- **Aparecen los arreglos:** `Enter` con `Stagger` desde debajo de la palabra; una línea de 2 dp `field` a rayas une la partícula mala con la zona de arreglo.
- **Arreglo correcto:** la partícula mala cae (y +24 dp, rotación 12°, opacidad → 0, 240 ms ease-in); la buena vuela (`Fly`) a su sitio; las dos mitades se cierran (180 ms) y la palabra hace `Pop` con borde `correct-deco`. Debajo aparece la **regla** (`Enter`) en un recuadro `canvas` (12 sp): p. ej. *After a vowel: 를. After a consonant: 을.*
- **Arreglo incorrecto:** la ficha elegida hace `Shake` y se marca *Incorrecta*; 500 ms después ocurre la animación del arreglo correcto, con borde `wrong-border` en vez de `correct-deco`.

**Sin arrastrar:** todo son toques. TalkBack lee la frase entera al empezar y cada palabra al enfocarla.

---

## 10 · Word Recall · 순서 기억하기

- **Contenido:** nivel 1: 3 palabras; nivel 2: 4; nivel 3: 5. Palabras de 1–3 bloques, distintas entre sí en su primer bloque.
- **Instrucción al memorizar:** *Remember the order.* · 순서를 기억하세요. **Al reconstruir:** *Tap them in the same order.* · 같은 순서로 누르세요.

**Atrezo:** la cuerda de faroles de Hangman, con un farol por palabra (repartidos entre x = 110 y x = 250), apagados al empezar. La **luna** es el reloj de la fase de memorizar.

**Pantalla:** una fila de **cartas de secuencia** (ancho 379 / n menos 8 dp de hueco; alto 96 dp; con 5, alto 80 dp y `ko-body` 700), y debajo, en la fase de reconstruir, la bandeja con las palabras barajadas.

**Cómo se juega, en tres fases:**
1. **Mostrar** (n × 700 ms): las palabras aparecen de una en una, de izquierda a derecha.
2. **Memorizar** (n × 900 ms, con MoonTimer): todas a la vista con su número de orden encima (`label-caps`).
3. **Reconstruir:** las cartas se giran boca abajo (muestran su número) y aparecen las fichas barajadas. Tocar las fichas **en orden**. Cada toque se comprueba al instante.

**Resultado:** *won* al completar la secuencia con 1 fallo o menos; al **segundo** fallo, *lost* y se muestra el orden. Un fallo se apunta a la palabra que tocaba.

**Animaciones:**
- **Mostrar:** cada carta entra con `Enter` y `Pop`; a la vez su farol se enciende en la escena (luz y halo 0 → 1, 240 ms) y da un pequeño balanceo.
- **Memorizar:** MoonTimer. En el último 25 %, los números de orden laten con la luna.
- **Ocultar:** las cartas hacen `Flip` en ola de izquierda a derecha (`Stagger` de 60 ms) hasta su dorso (fondo `action`, el número en blanco, 20 sp, 800). Los faroles se apagan todos a la vez (320 ms). La bandeja entra con `Enter`.
- **Toque correcto:** la ficha vuela (`Fly`) a la carta que toca; la carta hace `Flip` a su cara con borde `correct-deco`; su farol se vuelve a encender.
- **Toque incorrecto:** la ficha hace `Shake` y queda *Incorrecta* 500 ms; el farol de la posición que toca parpadea en `lantern-red` sin luz (240 ms).
- **Ganar:** los faroles encendidos se balancean juntos una vez (±6°, 600 ms).
- **Perder:** las cartas restantes hacen `Flip` a su cara en ola, con borde `wrong-border`.

**Sin arrastrar:** todo son toques. Con TalkBack la fase de memorizar no tiene límite: termina con un botón **Ready** (`Action`).

---

## 11 · Hangul Typing · 한글 타자

- **Contenido:** una palabra de tus notas (nivel 3: también una frase corta de hasta 10 bloques).
- **Niveles:** 1 = **copiar** (la palabra está a la vista), sin reloj; 2 = **desde el significado** (solo se ve el inglés); 3 = **de memoria** (la palabra se ve 1500 ms y se oculta) o frase corta desde el significado.
- **Reloj** (niveles 2 y 3, si *Timer in typing games* está encendido): 6 s + 3 s por bloque, con MoonTimer.
- **Instrucción:** nivel 1 *Type this word.* · 이 단어를 쓰세요; nivel 2 *Type it in Korean.* · 한국어로 쓰세요; nivel 3 *Type what you saw.* · 본 것을 쓰세요.

**Pantalla:**
- **Arriba:** el modelo. Nivel 1: la palabra (`ko-hero`, 30 sp). Nivel 2: el significado (20 sp, 700). Nivel 3: la palabra y, al ocultarse, **?** con el significado debajo.
- **Línea de escritura:** un hueco de 44 dp por bloque esperado (subrayado de 3 dp `field`, sin caja), centrados, con el texto escrito encima (`ko-prompt`, 24 sp). El bloque que se está componiendo tiene el subrayado en `brand` y un cursor de 2 dp que parpadea (530 ms). Se puede escribir un bloque más de los esperados (se ve en `wrong`), no más.
- **Abajo:** HangulKeyboard (sección 0).

**Cómo se juega:** escribir y tocar **Check** (deshabilitado con la línea vacía).

**Resultado:** igual a la respuesta (normalizado NFC; los espacios no cuentan; cualquiera de las alternativas «가장 / 제일» vale) → *won*. Si no → *lost*. Tiempo agotado → *lost* con la cinta *Time's up* (se comprueba lo escrito hasta ese momento: si ya era correcto, cuenta como *won*).

**Animaciones:**
- **Tecla:** sección 0 (hundirse + burbuja).
- **Composición en vivo:** cada jamo nuevo entra en el bloque con escala 0.7 → 1 (90 ms); cuando el bloque cambia de forma (가 → 간), el glifo hace un fundido cruzado de 60 ms. Al cerrarse un bloque y empezar otro, el subrayado `brand` se desliza al siguiente hueco (150 ms ease-out).
- **Nivel 1, guía en vivo:** un bloque ya cerrado que coincide con el modelo pone su subrayado en `correct-deco`; el bloque correspondiente del modelo baja a opacidad 0.4. (En los niveles 2 y 3 no hay guía: delataría la respuesta.)
- **Nivel 3, ocultar:** la palabra se ve 1500 ms con una barra de 3 dp `brand` debajo que se vacía; luego se emborrona (opacidad → 0 y escala → 0.9, 240 ms) y aparece **?**.
- **Check correcto:** los subrayados pasan a `correct-deco` en ola; los bloques hacen `Pop`.
- **Check incorrecto:** `Shake` de la línea; los bloques equivocados pasan a `wrong`; 500 ms después, encima de la línea aparece la respuesta correcta (`Enter`) en `ko-prompt`.
- **Borrar:** el jamo sale con escala 1 → 0.7 y opacidad → 0 (90 ms).

**Sin arrastrar:** todo son toques. Con TalkBack no hay reloj y cada tecla anuncia el bloque resultante.

---

## 12 · Korean Word Path · 단어 길

- **Contenido:** una palabra de tus notas de 3–5 bloques escondida en una rejilla. Nivel 1: 3 × 3, palabra de 3 bloques; nivel 2: 4 × 4, 3–4 bloques; nivel 3: 4 × 4, 4–5 bloques, y los bloques de relleno se parecen a los de la palabra.
- **Reglas del camino:** celdas contiguas en las **8 direcciones**; ninguna celda se usa dos veces.
- **Garantías al crear la rejilla:** la palabra tiene **un único** camino posible, y el relleno son bloques de otras palabras de tus notas.
- **Instrucción:** *Trace the word through the grid.* · 글자를 이어 단어를 만드세요. Pista: el significado.

**Pantalla:** rejilla centrada; celdas de 80 dp (3 × 3) o 76 dp (4 × 4) con 12 dp entre ellas; cada celda es un círculo `canvas` con borde 2 dp `line` y su bloque (`ko-prompt`). Encima de la rejilla, la **palabra en curso** (`ko-title`, `brand-text`), que se va escribiendo. Debajo, tres puntos de intento.

**Cómo se juega:** **arrastrar** el dedo de celda en celda y soltar para comprobar. Volver a la celda anterior deshace el último paso. También con toques: tocar celdas contiguas una a una y tocar la última otra vez para comprobar.

**Resultado:**
- la palabra → *won*;
- **otra palabra de tus notas** que casualmente está en la rejilla → no cuenta como intento: cinta breve *That's a word from your notes ({significado}), but not this one.* (1200 ms, no avanza) y se sigue;
- cualquier otra cosa → un intento menos; al **tercero**, *lost* y se ilumina el camino.

**Animaciones:**
- **Trazo:** línea de 10 dp `brand` al 85 %, extremos y uniones redondos, por detrás de los bloques, de centro a centro; el último tramo sigue al dedo. Cada celda que entra en el camino crece a 1.08 (`Pop` corto, 150 ms), toma fondo `tint` y borde `brand`, y enseña su número de orden (10 sp) arriba a la derecha.
- **Deshacer un paso:** el tramo se recoge en 120 ms y la celda vuelve a normal.
- **Correcto:** un brillo recorre el trazo de principio a fin (un tramo blanco al 70 % de 40 dp que viaja en 400 ms); el trazo pasa a `correct-deco`; las celdas hacen `Pop` en orden (`Stagger`); el resto de la rejilla baja a opacidad 0.3.
- **Incorrecto:** el trazo pasa a `wrong-border`, `Shake` de las celdas del camino, y el trazo se recoge desde el final hasta el principio (300 ms).
- **Perdida:** el camino correcto se dibuja solo, celda a celda (120 ms por tramo), en `wrong-border`.

**Sin arrastrar:** el modo de toques. TalkBack: cada celda dice su bloque, su fila y su columna, y "in path, step {n}" si está en el camino.

---

## 13 · Sound Detective · 소리 탐정

Entrena el oído, no la pronunciación. Tres modos; el motor alterna.

- **Contenido:**
  - **A · Same or different?** Dos palabras que suenan casi igual (pares mínimos). Salen de tus notas cuando hay dos palabras que se diferencian en un solo jamo; si no hay bastantes, de una lista fija de pares coreanos reales incluida en la app (21 grupos: 불/풀/뿔, 달/탈/딸, 자다/차다/짜다, 살/쌀, 방/빵, 굴/꿀, 비/피, 발/팔, 공/콩, 사다/싸다, 개/깨, 도끼/토끼, 섬/솜, 벌/볼, 강/간, 밤/밥, 산/상, 문/물, 곰/공, 눈/논, 거리/고리), y entonces la marca de origen dice **Extra · not from your notes**.
  - **B · Which one came twice?** Tres palabras de tus notas; suenan cuatro audios: una se repite.
  - **C · What order?** Tres palabras de tus notas (nivel 3: cuatro) suenan seguidas; hay que ordenarlas.
- **Niveles:** 1 = voz a velocidad 0.85 y pausas de 700 ms; 2 = velocidad 1.0, pausas de 500 ms; 3 = velocidad 1.0, pausas de 350 ms y palabras parecidas entre sí.
- **Texto fijo bajo la instrucción** (12 sp, `muted`): *This trains your ear. It doesn't grade how you speak.*

### Modo A

- **Instrucción:** *Same word or different?* · 같은 단어예요, 다른 단어예요?
- **Pantalla:** dos **discos de sonido** de 96 dp (círculos `tint`, borde 2 dp `tint-border`, con **1** y **2**) con VoiceBars dentro, uno a cada lado; debajo, dos botones grandes: **Same** · 같아요 y **Different** · 달라요 (Tiles de 181 × 64 dp). ReplayButton en el centro (1 repetición: vuelven a sonar los dos).
- **Juego:** suena 1, pausa, suena 2. La mitad de las veces son la misma palabra.
- **Resultado:** acierto → *won*; fallo → *lost*.
- **Animaciones:** mientras suena cada disco, sus barras se mueven y el disco crece a 1.06. Al responder, los dos discos hacen `Flip` y enseñan la palabra escrita y su significado; si eran distintas, el jamo que cambia va en `brand-text` y subrayado en las dos (불 / **풀**); debajo, una línea: *Same word.* o *Different: only the first sound changes.* (o *the vowel*, *the final consonant*).

### Modo B

- **Instrucción:** *Which word did you hear twice?* · 두 번 들린 단어는 뭐예요?
- **Pantalla:** arriba, **cuatro puntos de sonido** en fila (círculos de 40 dp, `canvas`, numerados); debajo, las tres palabras en Tiles (`ko-title`, con el significado a 11 sp).
- **Juego:** suenan los cuatro audios; cada punto se enciende mientras suena el suyo. Tocar la palabra repetida. 1 repetición de la secuencia.
- **Resultado:** acierto → *won*; fallo → *lost*.
- **Animaciones:** punto que suena: fondo `brand`, escala 1.15, con VoiceBars en blanco dentro; al acabar vuelve a `canvas`. Al responder: debajo de cada punto aparece la palabra que sonó (`Enter`, `Stagger`), y los dos puntos de la repetida se unen con un arco `correct-deco` (o `wrong-border`) que se dibuja en 240 ms.

### Modo C

- **Instrucción:** *Put them in the order you heard.* · 들은 순서대로 놓으세요.
- **Pantalla:** una fila de Slots numerados; debajo, las palabras barajadas en Tiles. Arriba, los puntos de sonido como en B.
- **Juego:** como Sentence Builder: tocar para colocar y devolver; se comprueba solo al llenar. 1 repetición.
- **Resultado:** orden exacto → *won*; si no → *lost*.
- **Animaciones:** como B al sonar; `Fly` al colocar; al comprobar, cada ficha hace `Pop` (correcta) o `Shake` (incorrecta) y las incorrectas vuelan a su sitio tras 500 ms.

**Sin arrastrar:** todo son toques en los tres modos.
