# Plantilla de notas — cómo escribir para que la app lo lea todo

La app lee tus PDF sin internet y sin modelo, con reglas. Si escribes tus notas así, **lee todo**: el significado, todos los ejemplos, todas las notas y toda la explicación, sin tope de líneas ni de largo. Si un texto es largo, se guarda entero y cada pantalla decide cuánto enseña.

Un ejemplo completo (inventado) está en `tools/fixtures/notes_template.txt`, y su PDF en `tools/fixtures/Notes_template.pdf`. Una prueba (`NotesTemplateTest`) comprueba que se lee entero.

## 1 · Títulos

Una línea con letra más grande que el texto es un **título** (sección). Separa temas: *Words 1–25*, *Food*, *Grammar*… Las palabras de una sección comparten tema en el quiz y en Games.

## 2 · Una palabra

```
1. 사과 = Apple
사과를 한 개 샀어요. = I bought 1 apple.
사과를 두 개 먹었어요. = I ate 2 apples.
사과가 달아요
Note: buy them in autumn, they are sweeter.
```

- **La entrada:** un número, el coreano, un separador y el significado en inglés: `1. 사과 = Apple`. Separadores: `=`, `→`, `->`, `=>`, `—`, `–`, `: ` o ` - `.
  - Romanización opcional entre paréntesis tras el coreano: `1. 학교 (hakgyo) = School`.
  - Sinónimos con barra: `207. 가장 / 제일 = The most` (vale cualquiera al escribir la respuesta).
  - El significado puede ser **todo lo largo que quieras**: se guarda entero.
- **Debajo de la entrada,** hasta la siguiente entrada numerada, todo es de esa palabra, **sin límite de líneas**:
  - **Ejemplo:** una línea con una frase coreana **que use la palabra** (también conjugada: 갚다 → 갚을게요). Con traducción (`frase = traducción`, o `frase → traducción`) o sin ella.
  - **Nota:** todo lo demás. Una línea solo en inglés; `Note: …` o `Tip: …`; y una línea `X -> Y`, `X → Y`, `X - Y`, `X = Y` o `== X` cuyo coreano **no** usa la palabra (un sinónimo, una comparación: `빨리 → quickly`). La nota se ve en el reverso de la tarjeta, no como ejemplo.
  - Etiquetas opcionales: `Example: …` (o `Examples:` / `Ex:`), `Translation: …` (traduce el ejemplo de la línea anterior), `Meaning: …` (si la entrada no lo traía).
- **El ejemplo de la tarjeta** es la primera frase con traducción que usa la palabra; si ninguna tiene traducción, la primera que la usa. **El reverso** enseña todos los ejemplos y notas, de todos tus documentos.
- **Una frase entera como entrada** (`12. 비가 오니까 집에 있어요 = Since it rains, I stay home.`) es una tarjeta de frase. Lo que va debajo es su explicación.
- **Lista de pares sin números** (notas sueltas): `사과 = apple`, una por línea, con 6 palabras coreanas como mucho. Funciona solo si el documento no tiene entradas numeradas.

## 3 · Un punto de gramática

```
1. -아서/어서
Meaning: so, because; it joins a reason or a first action to what follows
Tense and structure:
- A/V + 아서 (after ㅏ or ㅗ)
- A/V + 어서 (after other vowels)
- 하다 → 해서
Explanation:
It cannot be used with commands or suggestions.
Use -(으)니까 for those.
Examples:
비가 와서 집에 있어요. = It rains, so I stay home.
배가 고파서 밥을 먹었어요.
Translation: I was hungry, so I ate.
```

- **El título** es el patrón, con número y empezando por `-`, `~`, `(으)`, `(이)`… : `1. -아서/어서`. La línea siguiente, o la de después, tiene que ser `Meaning:`.
- **Etiquetas** (cada una abre su parte hasta la siguiente etiqueta):
  - `Meaning:` el significado, entero (sin tope de largo).
  - `Tense and structure:` o `Structure:` las formas, **una por línea con guion** (`- A/V + 아서`). Una línea sin guion continúa la anterior.
  - `Explanation:` o `Usage:` la explicación: **todas sus líneas**, sin tope.
  - `Examples:` (o `Example:`) los ejemplos: `frase coreana = traducción`, o la frase sola y en la línea siguiente `Translation: …` o la traducción en inglés.
  - `Anki options:` y `Anki answer:` (opcional) las opciones de una tarjeta de Anki: `A) -거든(요)`… y la letra correcta.
- **Un punto de gramática se lee entero** aunque sea muy largo (no se parte en trozos).
- **Sus ejemplos nunca son tarjetas de vocabulario.** Tampoco las frases numeradas que ilustran una explicación (`1. 친구가 웃는 걸 봤어요 = …` dentro de un texto en inglés).
- **Una frase coreana larga** (90 caracteres o más) se lee entera. Si una línea **llena el ancho de la página y no termina** (sin `.`, `?`, `!`), se entiende que el PDF la partió y la siguiente la continúa. Una línea en inglés debajo de un ejemplo traducido continúa **la traducción**, nunca la frase coreana.
- **Tabla de partículas:** filas `이/가 | Subject | 고양이가 집 뒤에 숨었어요. — The cat hid behind the house.` (partícula, función, ejemplo con `—`).

## 4 · Consejos

- Una entrada por número; no mezcles dos palabras en una línea.
- El coreano va **antes** del separador y el inglés **después**.
- No hace falta nada más: sin límite de ejemplos, de notas ni de líneas de explicación.
