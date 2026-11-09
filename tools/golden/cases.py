"""Golden cases generated from the test PDFs plus fixed mutations (docs/POC_PLAN.md).

No hand-written cases that go stale. Every case keeps its input and the web's output. Inputs
with `"transform": "nfd"` are stored in NFC (the golden files are NFC); the consumer must apply
NFD to the input before calling the function, as Python did.
"""

from __future__ import annotations

import itertools
import random
import re
import unicodedata
from dataclasses import asdict
from datetime import timedelta

from app.flashcards.scheduler import RATINGS, CardState, Scheduling, label, schedule
from app.quiz import text
from app.quiz.builders import GeneratedBlank, build_blank, build_from_pair
from app.quiz.generator import widen_answer
from app.quiz.grammar import VARIANTS, build_grammar, grammar_items
from app.rag import conjugation, entries
from app.rag.selfcheck import SelfCheck

HANGUL = re.compile(r"[가-힣]")
PARTICLES = {"은": "는", "는": "은", "이": "가", "가": "이", "을": "를", "를": "을"}
NBSP, ZWSP = " ", "​"


# ---------------------------------------------------------------- mutations
def _middle(s: str) -> int | None:
    letters = [i for i, ch in enumerate(s) if not ch.isspace()]
    return letters[len(letters) // 2] if letters else None


def _changed(ch: str) -> str:
    if "가" <= ch <= "힣":
        return chr(ord(ch) + 1) if ch < "힣" else "가"
    if ch.isalpha():
        return "x" if ch.lower() != "x" else "y"
    return "?"


def variants(answer: str, other: str) -> list[tuple[str, str, str | None]]:
    """(kind, input, transform) for an answer: the plan's fixed mutations."""
    out = [
        ("same", answer, None),
        ("upper_spaces", f"  {answer.upper().replace(' ', '  ')}  ", None),
    ]
    lowered = answer.lower()
    article = next((a for a in ("a ", "an ", "the ") if lowered.startswith(a)), None)
    out.append(("article", answer[len(article) :] if article else f"the {answer}", None))
    i = _middle(answer)
    if i is not None:
        out += [
            ("letter_changed", answer[:i] + _changed(answer[i]) + answer[i + 1 :], None),
            ("letter_missing", answer[:i] + answer[i + 1 :], None),
            ("letter_extra", answer[:i] + answer[i] + answer[i:], None),
            ("zero_width", answer[:i] + ZWSP + answer[i:], None),
        ]
    swap = next((j for j, ch in enumerate(answer) if ch in PARTICLES), None)
    if swap is not None:
        swapped = answer[:swap] + PARTICLES[answer[swap]] + answer[swap + 1 :]
        out.append(("particle", swapped, None))
    out += [
        ("other_answer", other, None),
        ("empty", "", None),
        ("nbsp", answer.replace(" ", NBSP, 1) if " " in answer else answer + NBSP, None),
    ]
    if unicodedata.normalize("NFD", answer) != answer:
        out.append(("nfd", answer, "nfd"))
    return out


def _input(value: str, transform: str | None) -> str:
    return unicodedata.normalize("NFD", value) if transform == "nfd" else value


def _case(kind: str, transform: str | None, **fields) -> dict:
    case = {"kind": kind, **fields}
    if transform:
        case["transform"] = transform
    return case


# ---------------------------------------------------------------- answers
def answer_inputs(pairs: list, points: list) -> list[tuple[str, str, bool]]:
    """(answer, other answer, is Korean) from the pairs and grammar points, in a stable order."""
    items: list[tuple[str, bool]] = []
    for p in pairs:
        items += [(p.korean, True), (p.english, False)]
    for g in points:
        items += [(g.pattern, True), (g.meaning, False)]
    out = []
    for i, (answer, korean) in enumerate(items):
        same_lang = [a for a, k in items[i + 1 :] + items[:i] if k == korean and a != answer]
        out.append((answer, same_lang[0] if same_lang else "", korean))
    return out


def text_cases(docs: list[dict]) -> dict:
    """docs: [{"name", "chunks": [Chunk], "pairs": [(chunk, Pair)], "points": [(chunk, GP)]}]."""
    pairs = [p for d in docs for _, p in d["pairs"]]
    points = [g for d in docs for _, g in d["points"]]
    out: dict[str, list] = {k: [] for k in ("is_correct", "english_answers", "english_senses")}
    norm_inputs: set[tuple[str, str | None]] = set()
    initials: set[tuple[str, str | None]] = set()
    for answer, other, korean in answer_inputs(pairs, points):
        types = ("write_ko", "multiple_choice") if korean else ("ko_to_en", "short_answer")
        for kind, given, transform in variants(answer, other):
            norm_inputs.add((given, transform))
            if korean:
                initials.add((given, transform))
            for qtype in types:
                result = text.is_correct(qtype, _input(given, transform), answer)
                out["is_correct"].append(
                    _case(kind, transform, type=qtype, given=given, correct=answer, out=result)
                )
        if not korean:
            for split in (True, False):
                result = text.english_answers(answer, split)
                out["english_answers"].append({"correct": answer, "split": split, "out": result})
            senses = sorted(text.english_senses(answer))
            out["english_senses"].append({"in": answer, "out": senses})
    out["norm"] = [
        _case("norm", t, **{"in": s, "out": text.norm(_input(s, t))})
        for s, t in sorted(norm_inputs, key=lambda x: (x[0], x[1] or ""))
    ]
    out["initial_consonant"] = [
        _case("initial", t, **{"in": s, "out": text.initial_consonant(_input(s, t))})
        for s, t in sorted(initials, key=lambda x: (x[0], x[1] or ""))
    ]

    out["is_grounded"], out["leaks_answer"], out["widen_answer"] = [], [], []
    for d, doc in enumerate(docs):
        other_doc = docs[(d + 1) % len(docs)]
        other_text = "\n".join(c.text for c in other_doc["chunks"])
        pool = [p for _, p in doc["pairs"]]
        for chunk, p in doc["pairs"]:
            for answer in (p.korean, p.english):
                for source, own in ((chunk.text, True), (other_text, False)):
                    out["is_grounded"].append(
                        {
                            "answer": answer,
                            "source": source,
                            "own_chunk": own,
                            "out": text.is_grounded(answer, source),
                        }
                    )
            prompts = (
                f"What does '{p.korean}' mean?",
                f"What is the Korean word for '{p.english}'?",
            )
            for prompt in prompts:
                for answer in (p.korean, p.english):
                    out["leaks_answer"].append(
                        {
                            "prompt": prompt,
                            "answer": answer,
                            "out": text.leaks_answer(prompt, answer),
                        }
                    )
            asks = ((p.english, prompts[0]), (p.korean, prompts[1]), (p.english, prompts[1]))
            for answer, prompt in asks:
                result = widen_answer(answer, pool, prompt, "")
                out["widen_answer"].append(
                    {
                        "answer": answer,
                        "prompt": prompt,
                        "text": "",
                        "pool": doc["name"],
                        "out": result,
                    }
                )
    out["pools"] = {d["name"]: [asdict(p) for _, p in d["pairs"]] for d in docs}
    return out


# ---------------------------------------------------------------- Korean
def korean_cases(index, points: list) -> dict:
    """index: NotesIndex of all the test PDFs; points: [(chunk id, GrammarPoint)]."""
    verbs = sorted(index.verbs)
    aeo = index.aeo
    out: dict = {"verbs": verbs, "aeo": sorted(aeo)}
    out["aeo_forms"] = [{"in": v, "out": sorted(conjugation.aeo_forms(v))} for v in verbs]
    forms = sorted({f for v in verbs for f in conjugation.aeo_forms(v)})
    out["past"] = [{"in": f, "out": conjugation.past(f)} for f in forms]
    words = sorted({w for line, _ in index.sentences for w in line.split() if HANGUL.search(w)})
    out["lemmas"] = [{"in": w, "out": conjugation.lemmas(w, set(verbs))} for w in words]

    by_chunk: dict[str, list[str]] = {}
    for line, hit in index.sentences:
        by_chunk.setdefault(hit.id, []).append(line)
    with_sentences = [c for c in by_chunk if by_chunk[c]]
    patterns = list(dict.fromkeys(g.pattern for _, g in points))
    out["pattern_forms"] = [{"in": p, "out": sorted(entries.pattern_forms(p))} for p in patterns]
    out["has_form"], out["same_grammar"] = [], []
    for n, (chunk_id, point) in enumerate(points):
        others = [c for c in with_sentences if c != chunk_id]
        other = others[n % len(others)] if others else None
        groups = ((by_chunk.get(chunk_id, []), True), (by_chunk.get(other, []), False))
        for form in sorted(f for f in entries.pattern_forms(point.pattern) if len(f) >= 2):
            for sentences, own in groups:
                for s in sentences:
                    out["has_form"].append(
                        {
                            "text": s,
                            "form": form,
                            "own_chunk": own,
                            "out": entries.has_form(s, form, aeo),
                        }
                    )
        other_point = points[(n + 1) % len(points)][1].pattern
        for name in [point.pattern, *SelfCheck._other_names(point.pattern), other_point]:
            out["same_grammar"].append(
                {
                    "word": name,
                    "pattern": point.pattern,
                    "out": entries.same_grammar(name, point.pattern),
                }
            )
    cores_in = patterns + [p for p in dict.fromkeys(index_pair_korean(index))]
    out["korean_cores"] = [
        {"in": c, "out": [list(x) for x in entries.korean_cores(c)]} for c in cores_in
    ]
    return out


def index_pair_korean(index) -> list[str]:
    """The Korean side of every "korean = english" line in the notes (for korean_cores)."""
    out = []
    for hit in index.hits:
        for line in hit.text.splitlines():
            left = line.split("=", 1)[0].strip(" 0123456789.)●•-")
            if "=" in line and HANGUL.search(left):
                out.append(left)
    return out


# ---------------------------------------------------------------- SM-2
FORCED = [  # interval × factor ending in .5: Python rounds half to even
    (CardState(2.5, 5, 1), "good", 2.5),  # 12.5 -> 12
    (CardState(2.5, 3, 1), "good", 2.5),  # 7.5 -> 8
]


def _next(n) -> dict:
    return {
        "ease": round(n.ease, 4),
        "interval_hours": n.interval_hours,
        "repetitions": n.repetitions,
        "due_in_minutes": int(n.due_in / timedelta(minutes=1)),
        "label": label(n.due_in),
    }


def sm2_cases(configs: dict[str, Scheduling]) -> dict:
    sequences = []
    for name, cfg in configs.items():
        for length in range(1, 5):
            for ratings in itertools.product(RATINGS, repeat=length):
                state, steps = CardState(), []
                for rating in ratings:
                    n = schedule(state, rating, cfg)
                    steps.append({"rating": rating, **_next(n)})
                    state = CardState(n.ease, n.interval_hours, n.repetitions)
                sequences.append({"config": name, "steps": steps})
    forced = [
        {
            "config": "default",
            "state": asdict(state),
            "rating": rating,
            "factor": factor,
            "next": _next(schedule(state, rating)),
        }
        for state, rating, factor in FORCED
    ]
    return {
        "configs": {k: asdict(v) for k, v in configs.items()},
        "sequences": sequences,
        "forced": forced,
    }


# ---------------------------------------------------------------- compareAnswer
def compare_inputs(pairs: list) -> list[dict]:
    out = []
    for answer, other, korean in answer_inputs(pairs, []):
        for kind, typed, transform in variants(answer, other):
            out.append(_case(kind, transform, typed=typed, answer=answer, korean=korean))
    return out


# ---------------------------------------------------------------- random and quiz builders
BUILDER_SEED = 7  # a fresh random.Random(BUILDER_SEED) per case: each case stands alone
RANDOM_SEEDS = [0, 1, 7, 42, 2**32 + 5, 2**40 + 3]


def random_cases() -> list[dict]:
    """Python's random.Random as the builders use it (Mersenne Twister): Kotlin ports it."""
    out = []
    for seed in RANDOM_SEEDS:
        rng = random.Random(seed)
        bits = [rng.getrandbits(k) for k in (32, 1, 5, 31, 17)]
        shuffled = list(range(10))
        rng.shuffle(shuffled)
        out.append(
            {
                "seed": seed,
                "getrandbits": bits,
                "shuffle10": shuffled,
                "sample30_3": rng.sample(range(30), 3),
                "sample5_3": rng.sample(list(range(5)), 3),
                "sample40_3": rng.sample(list(range(40)), 3),
            }
        )
    return out


def _candidate(result) -> dict:
    candidate, reason = result
    if candidate is None:
        return {"reason": reason}
    return {
        "type": candidate.type,
        "prompt": candidate.prompt,
        "answer": candidate.answer,
        "options": candidate.options,
        "explanation": candidate.explanation,
        "chunk": candidate.chunk.chroma_id,
        "prompt_version": candidate.prompt_version,
        "model": candidate.model,
    }


def _blanks(pairs: list) -> list[GeneratedBlank]:
    """Blanks like the model proposes them, made from the examples in your notes (and some broken)."""
    koreans = [p.korean for _, p in pairs]
    out = []
    for _, pair in pairs:
        if not pair.example_ko:
            continue
        words = HANGUL_WORD.findall(pair.example_ko)
        if len(words) < 2:
            continue
        answer = words[-1]
        others = [k for k in koreans if k != answer][:3]
        out.append(GeneratedBlank(sentence=pair.example_ko.replace(answer, "___", 1), answer=answer,
                                  distractors=others, explanation="Because."))
        out.append(GeneratedBlank(sentence=pair.example_ko, answer=answer, distractors=others))
        out.append(GeneratedBlank(sentence=pair.example_ko.replace(answer, "___", 1), answer=answer,
                                  distractors=others[:1]))
    out.append(GeneratedBlank(sentence="___", answer="x", distractors=["a", "b", "c"]))
    out.append(GeneratedBlank(sentence="저는 ___ 학생 ___", answer="은", distractors=["는", "이", "가"]))
    return out


HANGUL_WORD = re.compile(r"[가-힣]+")


def builder_cases(docs: list[dict]) -> dict:
    """build_from_pair, build_blank and build_grammar over the test PDFs, as the generator calls them:
    pool = the document's pairs or points, grounding = the document's text."""
    pairs_out, blanks_out, grammar_out = [], [], []
    for doc in docs:
        grounding = "\n".join(c.text for c in doc["chunks"])
        pool = [p for _, p in doc["pairs"]]
        for i, (chunk, pair) in enumerate(doc["pairs"]):
            for kind in ("ko_to_en", "vocab_recognition", "en_to_ko", "write_ko"):
                result = build_from_pair(kind, pair, chunk, pool, grounding, random.Random(BUILDER_SEED))
                pairs_out.append({"doc": doc["name"], "pair": i, "kind": kind, "out": _candidate(result)})
        by_id = {c.chroma_id: c for c in doc["chunks"]}
        for j, gb in enumerate(_blanks(doc["pairs"])):
            chunk = by_id[doc["pairs"][0][0].chroma_id] if doc["pairs"] else doc["chunks"][0]
            for source in {c.chroma_id: c for c, _ in doc["pairs"]}.values():
                if gb.answer in source.text or gb.sentence.replace("___", gb.answer) in source.text:
                    chunk = source
                    break
            for kind in ("fill_blank", "grammar_selection"):
                result = build_blank(kind, gb, chunk, grounding, random.Random(BUILDER_SEED))
                blanks_out.append(
                    {"doc": doc["name"], "blank": j, "chunk": chunk.chroma_id, "kind": kind,
                     "in": gb.model_dump(), "out": _candidate(result)}
                )
        points = [g for _, g in doc["points"]]
        for kind in VARIANTS:
            for chunk, point, variant in grammar_items(kind, doc["chunks"]):
                result = build_grammar(kind, variant, point, chunk, points, grounding,
                                       random.Random(BUILDER_SEED))
                grammar_out.append(
                    {"doc": doc["name"], "chunk": chunk.chroma_id, "point": points.index(point),
                     "kind": kind, "variant": variant, "out": _candidate(result)}
                )
    return {"seed": BUILDER_SEED, "from_pair": pairs_out, "blank": blanks_out, "grammar": grammar_out}

