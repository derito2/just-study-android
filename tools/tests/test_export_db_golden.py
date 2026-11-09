"""export_db_golden.py is deterministic: two runs (separate processes) give the same sha256 for every file."""

import os
import subprocess
import sys
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "golden" / "export_db_golden.py"


def _run(out: Path) -> str:
    env = {**os.environ, "PYTHONDONTWRITEBYTECODE": "1"}
    return subprocess.run([sys.executable, str(SCRIPT), "--out", str(out)], env=env, capture_output=True, text=True, check=True).stdout


def test_two_runs_same_sha256(tmp_path):
    first, second = _run(tmp_path / "a"), _run(tmp_path / "b")
    assert first == second
    assert "fixture.jsbackup.zip" in first and "progress_summary.json" in first
