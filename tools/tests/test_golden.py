import json
import os
import subprocess
import sys
from dataclasses import asdict
from pathlib import Path

import export_golden
import pytest
from app.rag.parser import parse_pdf

CAFE = export_golden.FIXTURES / "Vocab_cafe_y_comida.pdf"


@pytest.fixture(scope="module")
def golden(tmp_path_factory) -> Path:
    out = tmp_path_factory.mktemp("golden_a")
    assert export_golden.main(["--out", str(out)]) == 0
    return out


def _run_cli(out: Path) -> dict:
    """A fresh process each time: set order changes with PYTHONHASHSEED between processes."""
    env = {k: v for k, v in os.environ.items() if k != "PYTHONHASHSEED"}
    subprocess.run(
        [sys.executable, "-W", "ignore", str(export_golden.__file__), "--out", str(out)],
        env=env,
        check=True,
        capture_output=True,
    )
    return json.loads((out / "manifest.json").read_text(encoding="utf-8"))


def test_golden_is_deterministic(tmp_path):
    first, second = _run_cli(tmp_path / "a"), _run_cli(tmp_path / "b")
    assert first["files"] and first == second


def test_golden_refuses_private_paths(tmp_path):
    private = export_golden.WEB / "data" / "uploads" / "anything.pdf"
    out = tmp_path / "out"
    assert export_golden.main(["--out", str(out), "--pdf", str(private)]) == 2
    assert not out.exists() or not any(out.iterdir())


def test_golden_parser_matches_parse_pdf(golden):
    data = json.loads((golden / "parser" / f"{CAFE.stem}.json").read_text(encoding="utf-8"))
    assert data["pages"][0]["text"] == asdict(parse_pdf(CAFE))["pages"][0]["text"]


def test_sm2_rounding_case(golden):
    sm2 = json.loads((golden / "sm2.json").read_text(encoding="utf-8"))
    halves = [c for c in sm2["forced"] if (c["state"]["interval_hours"] * c["factor"]) % 1 == 0.5]
    assert halves
    for case in halves:
        product = case["state"]["interval_hours"] * case["factor"]
        assert case["next"]["interval_hours"] == round(product)
    assert len(sm2["sequences"]) == 340 * len(sm2["configs"])
