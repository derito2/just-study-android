"""Builds app/src/androidTest/assets/poc/nano-cases.json for PoC 2 (Gemini Nano).

Only the public golden files (test PDFs), never golden/private. Run from the repo root:
    python3 tools/poc/nano_cases.py
"""
import glob
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/androidTest/assets/poc/nano-cases.json"


def load(kind, key):
    out = []
    for f in sorted(glob.glob(str(ROOT / f"golden/{kind}/*.json"))):
        for chunk in json.load(open(f)):
            out += [dict(p, chunk=chunk["chunk"]) for p in chunk[key]]
    return out


pairs = load("pairs", "pairs")
grammar = load("grammar", "points")
chunks = {c["chroma_id"]: c for f in sorted(glob.glob(str(ROOT / "golden/chunks/*.json"))) for c in json.load(open(f))}
by_ko = {p["korean"]: p for p in pairs}

# Coach: facts the app already computed (weak topic, due cards...) plus 2 words copied from that topic.
topics = {}
for p in pairs:
    topics.setdefault(chunks[p["chunk"]]["section"], []).append(p["korean"])
topic_names = sorted(topics)
coach = []
for i in range(30):
    t = topic_names[i % len(topic_names)]
    words = topics[t][(i // len(topic_names)) % len(topics[t]):][:2] or topics[t][:2]
    coach.append({"id": f"coach-{i}", "weakTopic": t, "words": words, "dueCards": 3 + (i * 7) % 40,
                  "accuracyPct": 45 + (i * 11) % 50, "streakDays": (i * 3) % 15})

# Explanation: 17 grammar points in the public notes, cycled to 30 calls.
explain = []
for i in range(30):
    g = grammar[i % len(grammar)]
    entry = "\n".join(filter(None, [
        f"Pattern: {g['pattern']}", f"Meaning: {g['meaning']}",
        f"Note: {g['explanation']}" if g.get("explanation") else None,
        *[f"Example: {ko} = {en}" for ko, en in g["examples"]]]))
    explain.append({"id": f"explain-{i}", "entry": entry})

# Multiple choice: 30 pairs spread over all chunks; the source is the pair's chunk text.
step = len(pairs) / 30
mcq = [{"id": f"mcq-{i}", "korean": p["korean"], "english": p["english"], "source": chunks[p["chunk"]]["text"]}
       for i, p in enumerate(pairs[int(i * step)] for i in range(30))]

# Grade by meaning: 30 positives, 30 negatives (label true = same meaning).
POS = [  # one part of a multi-meaning answer
    ("포장하다", "to wrap up"), ("예약하다", "to book"), ("자리", "spot"), ("굽다", "to bake"),
    ("설레다", "to have butterflies"), ("서운하다", "to feel let down"), ("무섭다", "to be scared"),
    ("그립다", "to long for"), ("힘들다", "to be tiring"), ("놀다", "to hang out"),
] + [  # hand-written paraphrases
    ("근처", "close by"), ("혼자", "by myself"), ("걷다", "to go on foot"), ("계산하다", "to settle the bill"),
    ("외롭다", "to feel lonesome"), ("고맙다", "to be grateful"), ("걱정하다", "to be worried"),
    ("대박", "amazing"), ("짐", "baggage"), ("길을 잃다", "to lose one's way"), ("돕다", "to assist"),
    ("쉽다", "to be simple"), ("빠르다", "to be quick"), ("낫다", "to recover"), ("밥 먹었어?", "Have you eaten?"),
    ("할 수 있어, 포기하지 마", "You can do this, don't quit"),
    ("다음에 또 놀러 오세요", "Come over again another time"), ("사진 잘 나왔네요", "The picture turned out nice"),
    ("괜찮아, 다 잘될 거야", "It's alright, it will all work out"), ("짱", "number one"),
]
NEG = [  # another pair's answer
    ("근처", "Inside"), ("구름", "Bag"), ("감자", "Mirror"), ("맵다", "To be salty"), ("울다", "To be funny"),
    ("여권", "Airport"), ("데우다", "To get cold"), ("공항", "Passport"), ("밥 먹었어?", "Let's eat together sometime"),
    ("짐", "Receipt"),
] + [  # antonym
    ("행복하다", "to be sad"), ("쉽다", "to be difficult"), ("빠르다", "to be slow"), ("열다", "to close"),
    ("가깝다", "to be far"), ("춥다", "to be hot"), ("혼자", "together"), ("귀엽다", "to be ugly"),
    ("낫다", "to get worse"), ("달다", "to be bitter"),
] + [  # close but different meaning
    ("그립다", "to like"), ("걷다", "to run"), ("실망하다", "to be angry"), ("서운하다", "to be jealous"),
    ("짜증나다", "to be sad"), ("묻다", "to say"), ("메뉴판", "recipe"), ("영수증", "ticket"), ("거울", "window"),
    ("눈물", "sweat"),
]
assert len(POS) == 30 and len(NEG) == 30
grade = [{"id": f"grade-{'p' if label else 'n'}{i}", "korean": ko, "correct": by_ko[ko]["english"], "given": given,
          "label": label}
         for label, rows in ((True, POS), (False, NEG)) for i, (ko, given) in enumerate(rows)]

text = json.load(open(ROOT / "golden/text.json"))
OUT.write_text(json.dumps({"coach": coach, "explain": explain, "mcq": mcq, "grade": grade,
                           "is_grounded": text["is_grounded"], "leaks_answer": text["leaks_answer"]},
                          ensure_ascii=False, indent=1) + "\n")
print(OUT, len(coach), len(explain), len(mcq), len(grade))
