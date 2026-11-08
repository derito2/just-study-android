import hashlib
import json
import zipfile

import export_backup
from synthetic_backup import seeded_engine
from app.database.models import Base


def test_backup_manifest_checksums(tmp_path):
    zip_path = export_backup.export(seeded_engine(), tmp_path, web_commit="test")
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
