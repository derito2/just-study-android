"""Export the web MySQL database to a `.jsbackup.zip` v1 (docs/POC_PLAN.md, "Formato del respaldo").

Read-only: one SELECT per table inside a read-only transaction. Never writes to MySQL or to the
web project. Run from the web backend with its venv:

    PYTHONPATH=. .venv/bin/python <JustStudyApp>/tools/export/export_backup.py --out <dir>
"""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import zipfile
from datetime import UTC, date, datetime
from decimal import Decimal
from pathlib import Path

from app.database.models import Base
from sqlalchemy import Engine, inspect, select, text

SKIP_TABLES = {"chunks_index"}  # Chroma's index: rebuilt when indexing
UTC_NAMES = {"UTC", "+00:00", "GMT", "Etc/UTC", "Z"}


class NotUtcError(RuntimeError):
    pass


def _web_root() -> Path:
    import app

    return Path(app.__file__).resolve().parents[2]


def source_time_zone(conn) -> str:
    """MySQL zone the naive DATETIMEs were written in. Only UTC is accepted for now."""
    if conn.dialect.name != "mysql":
        return "UTC"
    session_tz, system_tz = conn.execute(
        text("SELECT @@session.time_zone, @@system_time_zone")
    ).one()
    zone = system_tz if session_tz == "SYSTEM" else session_tz
    if zone not in UTC_NAMES:
        raise NotUtcError(f"MySQL time zone is {session_tz}/{system_tz}, not UTC")
    return "UTC"


def _json_value(value):
    if isinstance(value, datetime):
        # Naive DATETIMEs are UTC (checked by source_time_zone).
        value = value.replace(tzinfo=UTC) if value.tzinfo is None else value.astimezone(UTC)
        return value.strftime("%Y-%m-%dT%H:%M:%S") + (
            f".{value.microsecond:06d}Z" if value.microsecond else "Z"
        )
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, Decimal):
        return float(value)
    return value


def _dump(obj) -> bytes:
    return json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _alembic_head(conn) -> str | None:
    if not inspect(conn).has_table("alembic_version"):  # tests on sqlite
        return None
    return conn.execute(text("SELECT version_num FROM alembic_version")).scalar()


def _git_head(root: Path) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "HEAD"], capture_output=True, text=True
    )
    return result.stdout.strip()


def export(
    engine: Engine,
    out: Path,
    *,
    web_commit: str,
    extracted_dir: Path | None = None,
    uploads_dir: Path | None = None,
) -> Path:
    out.mkdir(parents=True, exist_ok=True)
    now = datetime.now(UTC)
    tables: dict[str, dict] = {}
    files: dict[str, bytes] = {}
    with engine.connect() as conn:
        if conn.dialect.name == "mysql":
            conn.execute(text("SET SESSION TRANSACTION READ ONLY"))
            conn.commit()  # closes the autobegun transaction; the next one is read-only
        with conn.begin():
            zone = source_time_zone(conn)
            head = _alembic_head(conn)
            stored = {}
            for table in Base.metadata.sorted_tables:
                if table.name in SKIP_TABLES:
                    continue
                columns = [c.name for c in table.columns]
                rows = conn.execute(select(table).order_by(*table.primary_key.columns)).all()
                body = b"".join(
                    _dump({c: _json_value(v) for c, v in zip(columns, row, strict=True)}) + b"\n"
                    for row in rows
                )
                files[f"tables/{table.name}.jsonl"] = body
                tables[table.name] = {"rows": len(rows), "sha256": _sha(body), "columns": columns}
                if table.name == "documents":
                    stored = {row.stored_name: row.id for row in rows}

    extracted, pdfs = {}, {}
    for stored_name, doc_id in sorted(stored.items(), key=lambda kv: kv[1]):
        stem = Path(stored_name).stem
        if extracted_dir and (path := extracted_dir / f"{stem}.json").is_file():
            files[f"extracted/{doc_id}.json"] = data = path.read_bytes()
            extracted[str(doc_id)] = _sha(data)
        if uploads_dir and (path := uploads_dir / f"{stem}.pdf").is_file():
            files[f"pdfs/{stem}.pdf"] = data = path.read_bytes()
            pdfs[f"{stem}.pdf"] = _sha(data)

    manifest = {
        "format": "jsbackup",
        "version": 1,
        "web_commit": web_commit,
        "alembic_head": head,
        "exported_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source_time_zone": zone,
        "tables": tables,
        "extracted": extracted,
        "pdfs": pdfs,
    }
    zip_path = out / f"juststudy-backup-{now:%Y-%m-%d}.jsbackup.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=1))
        for name in sorted(files):
            zf.writestr(name, files[name])
    return zip_path


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--no-pdfs", action="store_true", help="leave the PDFs out of the zip")
    args = parser.parse_args(argv)

    from app.config.settings import get_settings
    from sqlalchemy import create_engine

    root = _web_root()
    engine = create_engine(get_settings().database_url, pool_pre_ping=True)
    try:
        zip_path = export(
            engine,
            args.out,
            web_commit=_git_head(root),
            extracted_dir=root / "data" / "extracted",
            uploads_dir=None if args.no_pdfs else root / "data" / "uploads",
        )
    except NotUtcError as exc:
        print(f"STOP: {exc}. Confirm the conversion before exporting.", file=sys.stderr)
        return 3
    finally:
        engine.dispose()
    with zipfile.ZipFile(zip_path) as zf:
        manifest = json.loads(zf.read("manifest.json"))
    rows = {name: info["rows"] for name, info in manifest["tables"].items()}
    print(f"{zip_path}\nrows: {json.dumps(rows)}")
    print(f"extracted: {len(manifest['extracted'])}, pdfs: {len(manifest['pdfs'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
