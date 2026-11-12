"""Phase 7, task 6: a long PDF (at most 30 pages) made only by repeating the pages of the synthetic PDFs of the repo
(vocabulary, grammar and examples). No new text. Usage: python3 -I tools/fixtures/long_pdf.py <out.pdf>"""
import sys
from pathlib import Path
from pypdf import PdfReader, PdfWriter

SRC = Path(__file__).resolve().parents[2] / "core/pdf/src/androidTest/assets/pdfs"
NAMES = ["Vocab_cafe_y_comida.pdf", "Gramatica_semana_14.pdf", "Vocab_emociones_redes.pdf", "Frases_dramas_y_redes.pdf", "Vocab_viajes_irregulares.pdf"]
MAX_PAGES = 30

pages = [p for n in NAMES for p in PdfReader(SRC / n).pages]
out = PdfWriter()
while len(out.pages) < MAX_PAGES:
    out.add_page(pages[len(out.pages) % len(pages)])
out.compress_identical_objects()
with open(sys.argv[1], "wb") as f:
    out.write(f)
print(len(out.pages), "pages")
