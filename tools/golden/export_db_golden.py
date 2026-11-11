"""Database golden files (docs/FASE_3_PLAN.md, task 2): a synthetic SQLite with the web's models, filled
through the web's own services at fixed times, then the read services called with a fixed `now`.

No personal data and no MySQL: the documents are the test PDFs (their parsed pages from golden/parser),
the "upload" folder is a temp dir, and the clock is patched in-process (standard library only).

    cd <web>/backend && PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=. .venv/bin/python \\
        <JustStudyApp>/tools/golden/export_db_golden.py --out <JustStudyApp>/golden/db

Writes fixture.jsbackup.zip (the database in format v1, fixed zip timestamps) and one JSON per function.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import sys
import tempfile
import zipfile
from dataclasses import asdict, is_dataclass
from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[2]
GOLDEN = ROOT / "golden"
ZONE = "America/Mexico_City"
LOCAL = ZoneInfo(ZONE)
# Fixed "now": 18:30 on 8 Nov 2026 in Mexico City is already 9 Nov in UTC (the day cut matters).
NOW = datetime(2026, 11, 8, 18, 30, tzinfo=LOCAL).astimezone(UTC).replace(tzinfo=None)
CREATED = NOW - timedelta(days=40)  # documents uploaded and cards created
ACTIVE_DAYS = 30
RULE_SEED = 7


def _setup(tmp: Path) -> None:
    """Settings for this process only: temp database and data dir, the zone of the fixture."""
    os.environ["DATABASE_URL_OVERRIDE"] = f"sqlite:///{tmp / 'golden.db'}"
    os.environ["DATA_DIR"] = str(tmp / "data")
    os.environ["APP_TIMEZONE"] = ZONE
    sys.path.insert(0, str(ROOT / "tools" / "export"))


class _Clock:
    """engine.utcnow (and the copy imported by quiz_service) answers this instead of the real time."""

    now = NOW


def _patch_clock() -> None:
    from app.progress import engine
    from app.services import quiz_service
    from sqlalchemy import DateTime, event
    from sqlalchemy.orm import Session, attributes

    engine.utcnow = quiz_service.utcnow = lambda: _Clock.now

    @event.listens_for(Session, "before_flush")
    def _fixed_times(session, _context, _instances):
        """Columns the database would fill with its own clock (server_default / onupdate now()) get the fixed one."""
        for obj in [*session.new, *session.dirty]:
            for col in getattr(obj, "__table__").columns:
                if not isinstance(col.type, DateTime):
                    continue
                if obj in session.new and col.server_default is not None and getattr(obj, col.key) is None:
                    setattr(obj, col.key, _Clock.now)
                elif obj not in session.new and col.onupdate is not None:
                    setattr(obj, col.key, _Clock.now)
                    attributes.flag_modified(obj, col.key)


class _NoModel:
    """The quiz generator only calls the model for non-rule types; the fixture only asks for rule types."""

    model = "rules"

    def __getattr__(self, name):
        raise AssertionError(f"the model was called ({name})")


def _local(day: date, hour: int, minute: int, second: int = 0) -> datetime:
    return datetime(day.year, day.month, day.day, hour, minute, second, tzinfo=LOCAL).astimezone(UTC).replace(tzinfo=None)


def _plain(value):
    if is_dataclass(value):
        return _plain(asdict(value))
    if isinstance(value, dict):
        return {str(k): _plain(v) for k, v in value.items()}
    if isinstance(value, (list, tuple, set)):
        items = [_plain(v) for v in value]
        return sorted(items, key=json.dumps) if isinstance(value, set) else items
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, date):
        return value.isoformat()
    if hasattr(value, "__table__"):  # an ORM row: its columns
        return {c.name: _plain(getattr(value, c.name)) for c in value.__table__.columns}
    return value


def _wrong(question) -> str:
    options = [o for o in question.options_json or [] if o != question.correct_answer]
    return options[0] if options else "nope"


def build(db, snapshot) -> list[dict]:
    """Fills the database the way the app would: upload, process, create cards, quizzes, study days.
    `snapshot()` saves the database right after processing (base.jsbackup.zip); every later write is
    recorded in the returned list (ops.json), so the phone can replay them and get the same database."""
    ops: list[dict] = []

    def op(kind: str, **args) -> None:
        ops.append({"op": kind, "at": _Clock.now.isoformat(), **args})

    from app.database.models import Document
    from app.quiz.generator import QuizGenerator
    from app.quiz.types import RULE_TYPES
    from app.rag.chunker import chunk_document
    from app.schemas.quiz import QuizGenerateIn
    from app.services import flashcard_service, indexing_service, quiz_service, reference_service

    settings_dir = Path(os.environ["DATA_DIR"])
    (settings_dir / "extracted").mkdir(parents=True)
    (settings_dir / "uploads").mkdir(parents=True)
    manifest = json.loads((GOLDEN / "manifest.json").read_text())
    names = [f.removeprefix("parser/").removesuffix(".json") for f in manifest["files"] if f.startswith("parser/")]
    order = {n: json.loads((GOLDEN / f"chunks/{n}.json").read_text()) for n in names}
    names = sorted((n for n in names if order[n]), key=lambda n: order[n][0]["document_id"])
    _Clock.now = CREATED
    for name in names:
        parsed = json.loads((GOLDEN / f"parser/{name}.json").read_text())
        stored = f"{name}.pdf"
        (settings_dir / "extracted" / f"{name}.json").write_text(json.dumps(parsed, ensure_ascii=False))
        doc = Document(
            original_name=stored, stored_name=stored, sha256=hashlib.sha256(name.encode()).hexdigest(),
            page_count=parsed["page_count"], status="ready", has_digital_text=True,
            image_count=parsed["image_count"], table_count=parsed["table_count"], char_count=parsed["char_count"],
            created_at=CREATED, processed_at=CREATED,
        )
        db.add(doc)
        db.flush()
        indexing_service._sync_topics(db, doc.id, chunk_document(doc.id, doc.original_name, parsed["pages"]))
        reference_service.store_document(db, doc, parsed["pages"])
        db.commit()
    snapshot()
    op("create_cards", document_ids=None)
    flashcard_service.generate_cards(db, None, now=CREATED)

    generator = QuizGenerator(_NoModel())
    questions = []
    for i in range(4):
        _Clock.now = CREATED + timedelta(hours=i)
        params = QuizGenerateIn(count=10, types=sorted(RULE_TYPES))
        op("generate_quiz", count=10, types=sorted(RULE_TYPES), seed=RULE_SEED + i)
        stored, _ = quiz_service.generate_quiz(db, generator, params, random.Random(RULE_SEED + i))
        questions += [q for q in stored if q not in questions]

    today = NOW.replace(tzinfo=UTC).astimezone(LOCAL).date()
    first_day = today - timedelta(days=ACTIVE_DAYS)
    for d in range(ACTIVE_DAYS + 1):
        day = first_day + timedelta(days=d)
        if d % 4 == 3 and day != today:
            continue  # a day off now and then: the streak and the calendar show it
        hour = 9 if day == today else 20  # today: in the morning, before NOW (18:30)
        _Clock.now = _local(day, hour, 0)
        run_ids = [q.id for q in questions[(d * 3) % len(questions):][:5]]
        op("create_run", question_ids=run_ids)
        run = quiz_service.create_run(db, run_ids, now=_Clock.now)
        for i, qid in enumerate(run.question_ids):
            question = next(q for q in questions if q.id == qid)
            at = _local(day, hour, i * 2, 15)
            given = question.correct_answer if (d + i) % 4 else _wrong(question)
            _Clock.now = at
            op("answer", question_id=qid, given=given, ms=4000 + 500 * i, run_id=run.id)
            quiz_service.answer_question(db, qid, given, 4000 + 500 * i, now=at, quiz_run_id=run.id)
        cards = flashcard_service.due_cards(db, 6, now=_local(day, hour + 1, 0))
        for i, (card, _new) in enumerate(cards):
            rating = ("good", "again", "hard", "easy", "good", "good")[(d + i) % 6]
            _Clock.now = _local(day, hour + 1, i, 30)
            op("review", card_id=card.id, rating=rating, ms=3000 + 250 * i)
            flashcard_service.review(db, card.id, rating, 3000 + 250 * i, now=_Clock.now)
    # Around midnight in Mexico City: one answer at 23:59:30 and one at 00:00:30 the next day.
    edge = today - timedelta(days=2)
    for at in (_local(edge, 23, 59, 30), _local(edge + timedelta(days=1), 0, 0, 30)):
        _Clock.now = at
        question = questions[0]
        op("answer", question_id=question.id, given=question.correct_answer, ms=5000, run_id=None)
        quiz_service.answer_question(db, question.id, question.correct_answer, 5000, now=at)
    _Clock.now = NOW - timedelta(hours=1)
    decks = flashcard_service.decks(db, now=_Clock.now)
    ids = [d["document_id"] for d in decks]
    op("rename_deck", deck_id=ids[0], name="My first deck")
    flashcard_service.rename_deck(db, ids[0], "My first deck")
    op("reorder_decks", deck_ids=list(reversed(ids)))
    flashcard_service.reorder_decks(db, list(reversed(ids)))
    op("merge_decks", deck_ids=ids[1:3], name="Two decks together")
    flashcard_service.merge_decks(db, ids[1:3], "Two decks together")
    _Clock.now = NOW
    return ops


def outputs(db) -> dict[str, object]:
    """The read services, at NOW."""
    from app.services import flashcard_service, progress_service, quiz_service, reference_service, study_service

    local = NOW.replace(tzinfo=UTC).astimezone(LOCAL)
    previous = (local.replace(day=1) - timedelta(days=1)).date()
    return {
        "progress_summary": progress_service.summary(db, now=NOW),
        "weekly": progress_service.weekly(db, now=NOW),
        "calendar": [progress_service.calendar(db, previous.year, previous.month), progress_service.calendar(db, local.year, local.month)],
        "timeline": {u: progress_service.timeline(db, u, now=NOW) for u in ("day", "week", "month", "year")},
        "topics": {"topics": progress_service.topics(db, limit=3), "recommended": study_service.recommended(db, 3, now=NOW)},
        "flashcard_decks": flashcard_service.decks(db, now=NOW),
        "flashcard_stats": flashcard_service.stats(db, now=NOW),
        "due_cards": [{"id": c.id, "new": new} for c, new in flashcard_service.due_cards(db, 20, now=NOW)],
        "history": flashcard_service.history(db, 50),
        "quiz_mistakes": [{"question_id": q.id, "last_at": at, "times": times} for q, at, times in quiz_service.mistakes(db, 5)],
        "quiz_history": quiz_service.history(db, 20, now=NOW),
        "saved_questions": [q.id for q in quiz_service.saved_questions(db, None, now=NOW)],
        "grammar": reference_service.grammar(db),
        "card_details": {str(k): v for k, v in sorted(flashcard_service.card_details(db, _all_cards(db)).items())},
        # Library (phase 4): All vocabulary, and what each document repeats from the others (DuplicateDialog).
        "library_vocabulary": reference_service.vocabulary(db),
        "library_duplicates": {str(d): reference_service.duplicates(db, d) for d in _document_ids(db)},
        # The test PDFs don't repeat each other: a copy of two of them, uploaded again under another name.
        "library_duplicates_copy": {name: _duplicates_of_copy(db, name) for name in ("Vocab_cafe_y_comida", "Gramatica_semana_14")},
    }


def _duplicates_of_copy(db, name: str) -> dict:
    """duplicates() for a 7th document with the same pages as `name` (rolled back: the golden database doesn't change)."""
    from app.database.models import Document
    from app.services import reference_service

    parsed = json.loads((GOLDEN / f"parser/{name}.json").read_text())
    stored = f"Copy_of_{name}.pdf"
    extracted = Path(os.environ["DATA_DIR"]) / "extracted" / f"Copy_of_{name}.json"
    extracted.write_text(json.dumps({"pages": parsed["pages"]}, ensure_ascii=False))
    nested = db.begin_nested()
    try:
        doc = Document(
            original_name=stored, stored_name=stored, sha256=hashlib.sha256(stored.encode()).hexdigest(),
            page_count=parsed["page_count"], status="ready", has_digital_text=True, image_count=0, table_count=0,
            char_count=parsed["char_count"], created_at=NOW, processed_at=NOW,
        )
        db.add(doc)
        db.flush()
        return {"document_id": doc.id, "pages": parsed["pages"], "duplicates": reference_service.duplicates(db, doc.id)}
    finally:
        nested.rollback()
        extracted.unlink()


def _document_ids(db) -> list[int]:
    from sqlalchemy import select

    from app.database.models import Document

    return list(db.scalars(select(Document.id).order_by(Document.id)))


def _all_cards(db):
    from sqlalchemy import select

    from app.database.models import Flashcard

    return list(db.scalars(select(Flashcard).order_by(Flashcard.id)))


def _fixed_zip(path: Path) -> None:
    """Same bytes on every run: entries rewritten with a fixed date, and the manifest's exported_at fixed."""
    with zipfile.ZipFile(path) as zf:
        entries = [(i.filename, zf.read(i)) for i in zf.infolist()]
    for i, (name, data) in enumerate(entries):
        if name == "manifest.json":
            manifest = json.loads(data)
            manifest["exported_at"] = NOW.strftime("%Y-%m-%dT%H:%M:%SZ")
            entries[i] = (name, json.dumps(manifest, ensure_ascii=False, indent=1).encode())
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        for name, data in entries:
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            zf.writestr(info, data)


def generate(out: Path) -> dict[str, str]:
    with tempfile.TemporaryDirectory() as tmp:
        _setup(Path(tmp))
        _patch_clock()
        import export_backup
        from app.database.models import Base
        from sqlalchemy import create_engine
        from sqlalchemy.orm import Session

        engine = create_engine(os.environ["DATABASE_URL_OVERRIDE"])
        Base.metadata.create_all(engine)
        out.mkdir(parents=True, exist_ok=True)
        shas = {}

        def save_zip(name: str) -> None:
            zip_path = export_backup.export(
                engine, Path(tmp) / name, web_commit="golden-db", extracted_dir=Path(os.environ["DATA_DIR"]) / "extracted"
            )
            target = out / f"{name}.jsbackup.zip"
            target.write_bytes(zip_path.read_bytes())
            _fixed_zip(target)
            shas[target.name] = hashlib.sha256(target.read_bytes()).hexdigest()

        with Session(engine) as db:
            ops = build(db, lambda: save_zip("base"))
            results = outputs(db)
        results["ops"] = ops
        for name, value in results.items():
            data = (json.dumps(_plain(value), ensure_ascii=False, sort_keys=True, indent=1) + "\n").encode()
            (out / f"{name}.json").write_bytes(data)
            shas[f"{name}.json"] = hashlib.sha256(data).hexdigest()
        save_zip("fixture")
        engine.dispose()
    return shas


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=GOLDEN / "db")
    args = parser.parse_args(argv)
    for name, sha in generate(args.out).items():
        print(f"{name}: {sha[:12]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
