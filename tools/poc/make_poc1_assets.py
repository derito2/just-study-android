"""PoC 1 assets (branch only): the 6 test PDFs + golden parser_raw/parser, and synthetic error
PDFs with the web's own verdict. Run from the web backend with its venv:

    PYTHONPATH=. .venv/bin/python <JustStudyApp>/tools/poc/make_poc1_assets.py
"""

import json
import shutil
import sys
from pathlib import Path

import pymupdf
from app.rag.parser import parse_pdf

APP = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(APP / "tools" / "golden"))
import export_golden  # noqa: E402

OUT = APP / "app" / "src" / "androidTest" / "assets" / "poc"
TEXT = "Korean notes 한국어 공부. " * 20  # > 50 chars on a page


def _png(path: Path) -> None:
    pix = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, 200, 200), False)
    pix.clear_with(180)
    pix.save(path)


def errors(folder: Path) -> dict:
    folder.mkdir(parents=True, exist_ok=True)
    png = folder / "img.png"
    _png(png)
    font = {"fontname": "korea", "fontsize": 11}

    def page_doc(name, text=None, image=None, **save):
        doc = pymupdf.open()
        page = doc.new_page()
        if text:
            page.insert_textbox(pymupdf.Rect(40, 40, 560, 400), text, **font)
        if image:
            page.insert_image(image, filename=str(png))
        doc.save(folder / name, **save)

    page_doc("blank.pdf")
    page_doc("short_text.pdf", text="Hi 안녕")
    page_doc("scanned.pdf", image=pymupdf.Rect(0, 0, 595, 842))
    page_doc("text_and_image.pdf", text=TEXT, image=pymupdf.Rect(40, 420, 400, 780))
    page_doc("small_image.pdf", text=TEXT, image=pymupdf.Rect(40, 700, 90, 750))
    perm = pymupdf.PDF_PERM_PRINT
    page_doc("user_password.pdf", text=TEXT, encryption=pymupdf.PDF_ENCRYPT_AES_256,
             user_pw="x", owner_pw="y", permissions=perm)  # fmt: skip
    page_doc("owner_password.pdf", text=TEXT, encryption=pymupdf.PDF_ENCRYPT_AES_256,
             owner_pw="y", permissions=perm)  # fmt: skip
    (folder / "corrupt.pdf").write_bytes(b"%PDF-1.7\nthis is not a pdf\n")
    png.unlink()
    expected = {}
    for pdf in sorted(folder.glob("*.pdf")):
        try:
            parse_pdf(pdf)
            expected[pdf.name] = {"error": None, "message": None}
        except Exception as exc:
            expected[pdf.name] = {"error": type(exc).__name__, "message": str(exc)}
    return expected


def tables(path: Path) -> None:
    """Two ruled tables: stroked lines (page 1) and thin filled bars as borders (page 2)."""
    rows = [["Particle", "Function", "Example"], ["은/는", "Topic", "저는 학생이에요"],
            ["이/가", "Subject", "친구가 와요"], ["을/를", "Object", "밥을 먹어요"]]  # fmt: skip
    doc = pymupdf.open()
    for filled in (False, True):
        page = doc.new_page()
        page.insert_text((40, 60), "Particles table", fontname="korea", fontsize=14)
        xs, ys = [40, 160, 300, 540], [80 + 30 * i for i in range(len(rows) + 1)]
        for y in ys:
            if filled:
                page.draw_rect(pymupdf.Rect(xs[0], y - 0.5, xs[-1], y + 0.5), fill=(0, 0, 0), width=0)
            else:
                page.draw_line((xs[0], y), (xs[-1], y), width=1)
        for x in xs:
            if filled:
                page.draw_rect(pymupdf.Rect(x - 0.5, ys[0], x + 0.5, ys[-1]), fill=(0, 0, 0), width=0)
            else:
                page.draw_line((x, ys[0]), (x, ys[-1]), width=1)
        for r, row in enumerate(rows):
            for c, cell in enumerate(row):
                page.insert_text((xs[c] + 4, ys[r] + 20), cell, fontname="korea", fontsize=11)
        page.insert_textbox(pymupdf.Rect(40, 260, 540, 400), TEXT, fontname="korea", fontsize=11)
    doc.save(path)


def main() -> None:
    for pdf in export_golden.default_pdfs():
        shutil.copy(pdf, OUT / pdf.name)
        for kind in ("parser_raw", "parser"):
            shutil.copy(APP / "golden" / kind / f"{pdf.stem}.json", OUT / f"{pdf.stem}.{kind}.json")
    table = OUT / "Tablas_sinteticas.pdf"
    tables(table)
    for kind, fn in (("parser_raw", lambda p: {"pages": export_golden.parser_raw(p)}),
                     ("parser", export_golden.parsed)):  # fmt: skip
        (OUT / f"{table.stem}.{kind}.json").write_text(
            json.dumps(export_golden.plain(fn(table)), ensure_ascii=False, sort_keys=True, indent=1)
        )
    expected = errors(OUT / "errors")
    (OUT / "errors" / "expected.json").write_text(
        json.dumps(expected, ensure_ascii=False, indent=1, sort_keys=True) + "\n"
    )
    print(json.dumps({k: v["error"] for k, v in expected.items()}, indent=1))


if __name__ == "__main__":
    main()
