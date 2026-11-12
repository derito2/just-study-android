"""Coach golden files (docs/FASE_5_PLAN.md, task 2): the web's StudyAgent without a model, over golden/db's synthetic
base at its fixed `now`. For each test phrase: the intent, the planned tools, the steps it ran, the reply the code
wrote and the quiz / deck proposals. The model is always "down" (RagError), so every answer is the web's deterministic
one: the same the phone gives in phase 5. Phrases that reach the embeddings (meanings.py, BGE-M3 on the web) are marked
`needsEmbeddings` and not compared (phase 6).

    cd <web>/backend && PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=. .venv/bin/python \\
        <JustStudyApp>/tools/golden/export_coach_golden.py --out <JustStudyApp>/golden/coach.json
"""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import export_db_golden as base  # noqa: E402  (same folder: the synthetic base and its clock)

# {topic}: the first topic of the fixture with material; {grammar}: its first grammar point.
PHRASES = [
    # common questions: planned tools, then the deterministic plan
    "What should I study?",
    "What should I focus on today?",
    "teach me something",
    "What are my weakest topics?",
    "How am I doing?",
    "How's my progress?",
    "Make me a study plan for this week",
    "I have 20 minutes",
    "I only have 10 minutes, what should I do?",
    "I have 1 hour",
    "How many flashcards are due?",
    "Do I have cards to review?",
    "오늘 뭐 공부할까요?",
    "hello",
    "Thanks!",
    "What is the capital of France?",
    # quick buttons and made-to-measure material
    "Quiz on my mistakes",
    "Deck from my mistakes",
    "make me a quiz of my mistakes",
    "Give me 3 questions on my mistakes",
    "a deck with the words I got wrong",
    "words that start with ㅂ",
    "Quiz me on words that start with ㄱ",
    "Make a deck of words starting with ㅎ",
    "a quiz with adjectives",
    "make a verbs deck",
    "quiz me on nouns",
    "a quiz with my latest words",
    "flashcards with the words I studied today",
    "quiz with the words I reviewed this week",
    "food words",
    "a colors deck",
    "family quiz",
    "Quiz me on animals",
    "practice weather vocabulary",
    "sports vocabulary",
    "Quiz me on quantum physics",
    "틀린 단어 퀴즈",
    "ㄱ으로 시작하는 단어 카드",
    "형용사 퀴즈",
    "최근 단어 퀴즈 만들어 줘",
    "가족에 대한 퀴즈",
    "음식 단어 카드 만들어 줘",
    "날씨 단어 카드",
    # your topics and grammar points
    "Quiz me on {topic}",
    "Make a deck from {topic}",
    "What do my notes say about {topic}?",
    "Quiz me on {grammar}",
    "practice -아/어 보다",
    "Quiz me on -아서",
    # a word: its entry, without the model
    "What does 근처 mean?",
    "What does 혼자 mean?",
    "Example with 혼자",
    "How do I say nearby in Korean?",
    "What does 바빠요 mean?",
    # mixed: one clear route (risk 1)
    "What does 근처 mean? also give me a quiz",
    "Quiz me on my mistakes and tell me what 근처 means",
    "What do my notes say about particles?",
]


class _NoModel:
    """The model is always down: the agent falls back to what the code writes."""

    model = "none"

    def __init__(self):
        self.calls: list[str] = []

    def _down(self, name):
        from app.rag.errors import RagError

        self.calls.append(name)
        raise RagError("no model")

    def chat_tools(self, messages, tools):
        self._down("chat_tools")

    def chat(self, system, user, response_format=None):
        self._down("chat")


def _phrases(db) -> list[str]:
    from app.database.models import DocumentTopic, Topic
    from sqlalchemy import select

    from app.quiz.grammar import extract_grammar
    from app.services import quiz_service

    topic = db.scalars(select(Topic).join(DocumentTopic, DocumentTopic.topic_id == Topic.id).order_by(Topic.id)).first()
    point = next(
        (p.pattern for c in quiz_service.load_chunks(db, None) for p in extract_grammar(c) if p.kind == "pattern"), "-아서"
    )
    return [p.replace("{topic}", topic.name).replace("{grammar}", point) for p in PHRASES]


def generate(out: Path) -> int:
    with tempfile.TemporaryDirectory() as tmp:
        base._setup(Path(tmp))
        base._patch_clock()
        from app.agent import loop, meanings, practice
        from app.database.models import Base
        from app.rag.errors import RagError
        from app.rag.selfcheck import _Embedder, _Store
        from app.rag.retriever import Retriever
        from app.services import quiz_service, study_service
        from sqlalchemy import create_engine
        from sqlalchemy.orm import Session

        engine = create_engine(base.os.environ["DATABASE_URL_OVERRIDE"])
        Base.metadata.create_all(engine)
        used = {"embeddings": False}

        def no_embeddings(*_args, **_kwargs):
            used["embeddings"] = True
            raise RagError("no embeddings")

        meanings.lifts = meanings.closest_name = no_embeddings  # BGE-M3 on the web: phase 6 on the phone

        rows = []
        with Session(engine) as db:
            base.build(db, lambda: None)
            base._Clock.now = base.NOW
            chunks = quiz_service.load_chunks(db, None)
            store = _Store()
            embedder = _Embedder()
            store.upsert([c.chroma_id for c in chunks], [c.text for c in chunks],
                         embedder.embed_documents([c.text for c in chunks]), [c.metadata() for c in chunks])
            retriever = Retriever(embedder, store, max_distance=0.55)
            for message in _phrases(db):
                llm = _NoModel()
                used["embeddings"] = False
                agent = loop.StudyAgent(llm, lambda: retriever)
                result = agent.run(db, message, now=base.NOW)
                db.rollback()  # nothing is written, but never let one phrase change the next
                rows.append({
                    "message": message,
                    "intent": practice.intent(message, lambda name: study_service.find_topic(db, name) is not None),
                    "planned": [[name, args] for name, args in practice.planned_tools(message)],
                    "steps": [{"tool": s.tool, "arguments": s.arguments, "ok": s.ok, "summary": s.summary} for s in result.steps],
                    "reply": result.reply,
                    "quiz": result.quiz_proposal,
                    "deck": result.deck_proposal,
                    "fallback": result.fallback,
                    "needsEmbeddings": used["embeddings"],
                    "modelCalls": llm.calls,
                })
        engine.dispose()
    out.write_text(json.dumps({"now": base.NOW.isoformat(), "phrases": rows}, ensure_ascii=False, indent=1, sort_keys=True) + "\n")
    return len(rows)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=base.GOLDEN / "coach.json")
    args = parser.parse_args(argv)
    print(f"coach: {generate(args.out)} phrases")
    return 0


if __name__ == "__main__":
    sys.exit(main())
