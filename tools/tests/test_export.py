import hashlib
import json
import zipfile
from datetime import datetime

import export_backup
from app.database.models import Base
from sqlalchemy import Boolean, DateTime, Float, Integer, create_engine, insert
from sqlalchemy.types import JSON


def _value(column, row: int):
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


def _seeded_engine():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    with engine.begin() as conn:
        for table in Base.metadata.sorted_tables:
            rows = [{c.name: _value(c, r) for c in table.columns} for r in (1, 2)]
            conn.execute(insert(table), rows)
    return engine


def test_backup_manifest_checksums(tmp_path):
    zip_path = export_backup.export(_seeded_engine(), tmp_path, web_commit="test")
    with zipfile.ZipFile(zip_path) as zf:
        manifest = json.loads(zf.read("manifest.json"))
        expected = {t.name for t in Base.metadata.sorted_tables} - {"chunks_index"}
        assert set(manifest["tables"]) == expected
        assert manifest["format"] == "jsbackup" and manifest["version"] == 1
        assert manifest["source_time_zone"] == "UTC"
        assert manifest["exported_at"].endswith("Z")
        for name, info in manifest["tables"].items():
            data = zf.read(f"tables/{name}.jsonl")
            assert info["rows"] == 2 == len(data.decode().splitlines())
            assert info["sha256"] == hashlib.sha256(data).hexdigest()
        first = json.loads(zf.read("tables/documents.jsonl").decode().splitlines()[0])
        assert first["created_at"] == "2026-01-01T12:30:00Z"
        assert first["has_digital_text"] is True
