"""Synthetic `.jsbackup.zip` v1 (no personal data): every table with 2 rows of made-up values (plus one
extracted JSON and one tiny PDF for document 1), from an
in-memory SQLite with the web's models. Used by tools/tests and by :core:data's BackupTest:

    cd <web>/backend && PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=. .venv/bin/python \
        <JustStudyApp>/tools/export/synthetic_backup.py --out <JustStudyApp>/core/data/src/androidTest/assets/backup
"""

from __future__ import annotations

import argparse
import sys
import tempfile
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import export_backup  # noqa: E402
from app.database.models import Base  # noqa: E402
from sqlalchemy import Boolean, DateTime, Float, Integer, create_engine, insert  # noqa: E402
from sqlalchemy.types import JSON  # noqa: E402


def value(column, row: int):
    if column.foreign_keys or column.primary_key:
        return row
    kind = column.type
    if isinstance(kind, Boolean):
        return row == 1
    if isinstance(kind, Integer):
        return row * 10
    if isinstance(kind, Float):
        return row / 4
    if isinstance(kind, DateTime):
        return datetime(2026, 1, row, 12, 30)
    if isinstance(kind, JSON):
        return [row, "x"]
    return f"{column.name}-{row}"


def seeded_engine():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with engine.begin() as conn:
        for table in Base.metadata.sorted_tables:
            rows = [{c.name: value(c, r) for c in table.columns} for r in (1, 2)]
            conn.execute(insert(table), rows)
    return engine


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, required=True)
    out = parser.parse_args().out
    # One extracted JSON and one tiny made-up PDF for document 1 (stored_name "stored_name-1").
    with tempfile.TemporaryDirectory() as tmp:
        extracted, uploads = Path(tmp, "extracted"), Path(tmp, "uploads")
        extracted.mkdir()
        uploads.mkdir()
        (extracted / "stored_name-1.json").write_text('{"pages": [{"number": 1, "text": "synthetic"}]}')
        (uploads / "stored_name-1.pdf").write_bytes(b"%PDF-1.4\n% synthetic test file\n%%EOF\n")
        zip_path = export_backup.export(
            seeded_engine(), out, web_commit="synthetic", extracted_dir=extracted, uploads_dir=uploads
        )
    final = zip_path.with_name("synthetic.jsbackup.zip")
    zip_path.replace(final)
    print(final)
    return 0


if __name__ == "__main__":
    sys.exit(main())
