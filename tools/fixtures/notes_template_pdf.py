"""Phase 8, task 0b: the PDF of docs/NOTES_TEMPLATE.md, from tools/fixtures/notes_template.txt (invented notes; a form
feed starts a page; "## " lines are headings). Needs PyMuPDF (the web project's venv has it):
    <python with pymupdf> -I tools/fixtures/notes_template_pdf.py tools/fixtures/notes_template.txt tools/fixtures/Notes_template.pdf
"""
import sys

import pymupdf

FONT = "korea"  # PyMuPDF's built-in CJK font: Hangul without a font file
BODY, HEADING = 10, 16
WIDTH, HEIGHT = 2400, 1000  # the CJK font is wide: every line fits whole (checked after writing)


def main(src, out):
    doc = pymupdf.open()
    for page_text in open(src, encoding="utf-8").read().split("\f"):
        page = doc.new_page(width=WIDTH, height=HEIGHT)
        y = 50
        for line in [ln for ln in page_text.split("\n") if ln.strip()]:
            heading = line.startswith("## ")
            size = HEADING if heading else BODY
            page.insert_text((40, y), line[3:] if heading else line, fontname=FONT, fontsize=size)
            y += size + 8
            if y > HEIGHT - 40:
                raise SystemExit("page too long: add a form feed")
    written = [ln[3:] if ln.startswith("## ") else ln for ln in open(src, encoding="utf-8").read().replace("\f", "\n").split("\n") if ln.strip()]
    read = [ln for page in doc for ln in page.get_text().splitlines()]
    if written != read:
        raise SystemExit("the PDF does not give back the same lines: widen the page")
    doc.save(out, garbage=4, deflate=True)
    print(len(doc), "pages")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
