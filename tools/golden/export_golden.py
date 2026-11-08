"""Write the golden files (docs/POC_PLAN.md, "Diseño del script de pruebas doradas").

Imports the web's `app.*` read-only and writes JSON that the Kotlin ports must reproduce exactly.
Run from the web backend with its venv:

    PYTHONPATH=. .venv/bin/python <JustStudyApp>/tools/golden/export_golden.py --out <dir>

Without options it only uses the test PDFs. `--private` also reads data/uploads + data/extracted
and writes only to <dir>/private/ (git-ignored). Any path under data/ without it exits with 2.
"""

from __future__ import annotations

import argparse
import dataclasses
import hashlib
import json
import os
import subprocess
import sys
import unicodedata
from dataclasses import asdict
from pathlib import Path

import app
import cases
import pdfplumber
import pymupdf
from app.flashcards.scheduler import DEFAULT, Scheduling
from app.quiz.grammar import extract_grammar
from app.quiz.pairs import extract_pairs
from app.rag import parser as web_parser
from app.rag.chunker import chunk_document
from app.rag.parser import parse_pdf
from app.rag.selfcheck import MODEL_SENTINEL, SelfCheck
from app.rag.tutor import build_index
from app.rag.vectorstore import Hit

WEB = Path(app.__file__).resolve().parents[2]
BACKEND = WEB / "backend"
FIXTURES = BACKEND / "tests" / "fixtures" / "notes_libreoffice"
TEMPLATE = WEB / "docs" / "templates" / "Korean_Notes_Example.pdf"
DATA = WEB / "data"
COMPARE_TS = WEB / "frontend" / "src" / "flashcards" / "compare.ts"
COMPARE_MJS = Path(__file__).with_name("compare_answer.mjs")
RAW_FLAGS = pymupdf.TEXT_PRESERVE_WHITESPACE | pymupdf.TEXT_MEDIABOX_CLIP  # as _extract_lines
TEACHER_SEED, TEACHER_SAMPLE = 7, 40


def default_pdfs() -> list[Path]:
    return [*sorted(FIXTURES.glob("*.pdf")), TEMPLATE]


# ---------------------------------------------------------------- writing
def plain(obj):
    """JSON-ready: dataclasses as dicts, tuples as lists, sets sorted."""
    if dataclasses.is_dataclass(obj) and not isinstance(obj, type):
        return {f.name: plain(getattr(obj, f.name)) for f in dataclasses.fields(obj)}
    if isinstance(obj, dict):
        return {str(k): plain(v) for k, v in obj.items()}
    if isinstance(obj, list | tuple):
        return [plain(v) for v in obj]
    if isinstance(obj, set | frozenset):
        return sorted(plain(v) for v in obj)
    return obj


def _nfc_bytes(text: str) -> bytes:
    return unicodedata.normalize("NFC", text).encode("utf-8")


class Writer:
    def __init__(self, root: Path):
        self.root = root
        self.files: dict[str, dict] = {}

    def _put(self, rel: str, data: bytes, count: int) -> None:
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        self.files[rel] = {"sha256": hashlib.sha256(data).hexdigest(), "cases": count}

    def json(self, rel: str, obj, count: int) -> None:
        text = json.dumps(plain(obj), ensure_ascii=False, sort_keys=True, indent=1) + "\n"
        self._put(rel, _nfc_bytes(text), count)

    def jsonl(self, rel: str, rows: list) -> None:
        text = "".join(
            json.dumps(plain(r), ensure_ascii=False, sort_keys=True) + "\n" for r in rows
        )
        self._put(rel, _nfc_bytes(text), len(rows))


# ---------------------------------------------------------------- per PDF
def parser_raw(path: Path) -> list[dict]:
    pages = []
    with pymupdf.open(path) as doc:
        for page in doc:
            letters = []
            for block in page.get_text("rawdict", flags=RAW_FLAGS)["blocks"]:
                if block.get("type") != 0:
                    continue
                for line in block["lines"]:
                    for span in line["spans"]:
                        bold = bool(span["flags"] & 16) or "bold" in span["font"].lower()
                        for ch in span["chars"]:
                            letters.append(
                                {
                                    "c": ch["c"],
                                    "bbox": [round(v, 2) for v in ch["bbox"]],
                                    "size": round(span["size"], 1),
                                    "font": span["font"],
                                    "bold": bold,
                                }
                            )
            pages.append(
                {
                    "page": page.number + 1,
                    "letters": letters,
                    "imageCount": web_parser._count_significant_images(page),
                    "drawingItems": web_parser._drawing_items(page),
                }
            )
    return pages


def parsed(path: Path) -> dict:
    try:
        return asdict(parse_pdf(path))
    except Exception as exc:  # the web's own errors (corrupt, scanned, images...)
        return {"error": type(exc).__name__, "message": str(exc)}


def teacher(doc_id: int, name: str, pages: list[dict]) -> list[dict]:
    """check_notes' questions (seed 7, sample 40) with the answer Teacher gave."""
    check = SelfCheck([(doc_id, name, pages)], sample=TEACHER_SAMPLE, seed=TEACHER_SEED)
    rows, ask = [], check._ask

    def recording(kind, question, deterministic=True):
        before = check.llm.calls
        result = ask(kind, question, deterministic)
        used = check.llm.calls > before or MODEL_SENTINEL in result.answer
        rows.append(
            {
                "check": kind,
                "question": question,
                "answer": result.answer,
                "status": result.status,
                "promptVersion": result.prompt_version,
                "sources": [{"n": s.n, "chunk": s.hit.id} for s in result.sources],
                "usedModel": used,
            }
        )
        return result

    check._ask = recording
    check.run()
    return rows


def _hits(chunks) -> list[Hit]:
    return [Hit(c.chroma_id, c.text, 0.0, c.metadata()) for c in chunks]


def _index_json(index) -> dict:
    return {
        "points": [{"chunk": h.id, "point": p} for h, p in index.points],
        "sentences": [{"text": s, "chunk": h.id} for s, h in index.sentences],
        "verbs": sorted(index.verbs),
        "aeo": sorted(index.aeo),
    }


# ---------------------------------------------------------------- main
def _versions() -> dict:
    return {
        "pymupdf": pymupdf.VersionBind,
        "mupdf": pymupdf.mupdf_version if hasattr(pymupdf, "mupdf_version") else pymupdf.version[1],
        "pdfplumber": pdfplumber.__version__,
    }


def _alembic_head() -> str | None:
    from alembic.script import ScriptDirectory

    return ScriptDirectory(str(BACKEND / "alembic")).get_current_head()


def _web_commit() -> str:
    result = subprocess.run(
        ["git", "-C", str(WEB), "rev-parse", "HEAD"], capture_output=True, text=True, check=True
    )
    return result.stdout.strip()


def _scheduling() -> dict[str, Scheduling]:
    configs = {"default": DEFAULT}
    options = DATA / "flashcard_options.json"  # the app's settings, not note text
    if options.is_file():
        configs["options"] = Scheduling(**json.loads(options.read_text(encoding="utf-8")))
    return configs


def _compare(inputs: list[dict]) -> list[dict]:
    result = subprocess.run(
        ["node", "--experimental-strip-types", "--no-warnings", str(COMPARE_MJS), str(COMPARE_TS)],
        input=json.dumps(inputs, ensure_ascii=False),
        capture_output=True,
        text=True,
        check=True,
    )
    return json.loads(result.stdout)


def generate(sources: list[tuple[str, Path | None, list[dict] | None]], root: Path) -> dict:
    """sources: (name, pdf or None, pages from data/extracted or None)."""
    w = Writer(root)
    docs, all_chunks = [], []
    for doc_id, (name, pdf, extracted_pages) in enumerate(sources, start=1):
        pages: list[dict] = []
        if pdf is not None:
            raw = parser_raw(pdf)
            w.json(f"parser_raw/{name}.json", {"pages": raw}, len(raw))
            doc = parsed(pdf)
            w.json(f"parser/{name}.json", doc, len(doc.get("pages", [])) or 1)
            pages = doc.get("pages", [])
        if extracted_pages is not None:
            pages = extracted_pages
        chunks = chunk_document(doc_id, f"{name}.pdf", pages)
        all_chunks += chunks
        w.json(
            f"chunks/{name}.json",
            [{**asdict(c), "chroma_id": c.chroma_id, "metadata": c.metadata()} for c in chunks],
            len(chunks),
        )
        pairs = [(c, p) for c in chunks for p in extract_pairs(c)]
        points = [(c, g) for c in chunks for g in extract_grammar(c)]
        w.json(
            f"pairs/{name}.json",
            [{"chunk": c.chroma_id, "pairs": extract_pairs(c)} for c in chunks],
            len(pairs),
        )
        w.json(
            f"grammar/{name}.json",
            [{"chunk": c.chroma_id, "points": extract_grammar(c)} for c in chunks],
            len(points),
        )
        index = _index_json(build_index(_hits(chunks)))
        w.json(
            f"index/{name}.json",
            index,
            len(index["points"]) + len(index["sentences"]) + len(index["verbs"]),
        )
        w.jsonl(f"teacher/{name}.jsonl", teacher(doc_id, f"{name}.pdf", pages))
        docs.append({"name": name, "chunks": chunks, "pairs": pairs, "points": points})

    text = cases.text_cases(docs)
    w.json("text.json", text, sum(len(v) for k, v in text.items() if k != "pools"))
    index = build_index(_hits(all_chunks))
    points = [(c.chroma_id, g) for d in docs for c, g in d["points"]]
    korean = cases.korean_cases(index, points)
    w.json(
        "korean.json", korean, sum(len(v) for k, v in korean.items() if k not in ("verbs", "aeo"))
    )
    sm2 = cases.sm2_cases(_scheduling())
    w.json("sm2.json", sm2, len(sm2["sequences"]) + len(sm2["forced"]))
    pairs = [p for d in docs for _, p in d["pairs"]]
    compared = _compare(cases.compare_inputs(pairs))
    w.json("compare_answer.json", compared, len(compared))

    manifest = {
        "format": 1,
        "web_commit": _web_commit(),
        "alembic_head": _alembic_head(),
        "versions": _versions(),
        "teacher": {"seed": TEACHER_SEED, "sample": TEACHER_SAMPLE},
        "files": dict(sorted(w.files.items())),
    }
    text_out = json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=1) + "\n"
    (root / "manifest.json").write_bytes(_nfc_bytes(text_out))
    return manifest


def _private_sources() -> list[tuple[str, Path | None, list[dict] | None]]:
    out = []
    for pdf in sorted((DATA / "uploads").glob("*.pdf")):
        extracted = DATA / "extracted" / f"{pdf.stem}.json"
        pages = json.loads(extracted.read_text("utf-8"))["pages"] if extracted.is_file() else None
        out.append((pdf.stem, pdf, pages))
    return out


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--private", action="store_true", help="your notes, into <out>/private/")
    ap.add_argument("--pdf", type=Path, action="append", help="use these PDFs instead")
    args = ap.parse_args(argv)

    pdfs = [p.resolve() for p in args.pdf] if args.pdf else default_pdfs()
    private = [p for p in pdfs if p.is_relative_to(DATA.resolve())]
    if private and not args.private:
        print(f"Refusing private paths without --private: {private[0]}", file=sys.stderr)
        return 2
    if args.private:
        manifest = generate(_private_sources(), args.out / "private")
    else:
        manifest = generate([(p.stem, p, None) for p in pdfs], args.out)
    for rel, info in manifest["files"].items():
        print(f"{rel}: {info['cases']}")
    return 0


if __name__ == "__main__":
    # The web's self-check iterates sets: their order depends on the hash seed of the process.
    if os.environ.get("PYTHONHASHSEED") != "0":
        os.execve(
            sys.executable, [sys.executable, *sys.argv], {**os.environ, "PYTHONHASHSEED": "0"}
        )
    sys.exit(main())
