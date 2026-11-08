// Runs the web's compareAnswer (frontend/src/flashcards/compare.ts) over cases read from stdin.
// Usage: node --experimental-strip-types compare_answer.mjs <path to compare.ts> < cases.json
// Each case: {typed, answer, korean, transform?}; "nfd" means typed is NFD-normalized first.
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

const { compareAnswer } = await import(pathToFileURL(process.argv[2]).href);
const cases = JSON.parse(readFileSync(0, "utf8"));
const out = cases.map((c) => {
  const typed = c.transform === "nfd" ? c.typed.normalize("NFD") : c.typed;
  return { ...c, out: compareAnswer(typed, c.answer, c.korean) };
});
process.stdout.write(JSON.stringify(out));
