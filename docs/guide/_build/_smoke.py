"""Smoke test: exercise every docxlib block, then validate the output."""
import os, zipfile
from docx import Document
from docxlib import Doc

def build():
    d = Doc()
    d.title_page("CryptoCopilot", "Smoke Test", "a quick check of the builder",
                 ["Line A", "Line B"])
    d.h1("First heading", num=1)
    d.toc("Contents")
    d.page_break()
    d.h2("A sub heading")
    d.p("This is a **test** paragraph with inline `code` and normal text that "
        "should wrap across the page width nicely.")
    d.bullet("A bullet item")
    d.bullet("Another bullet with a number 0.90")
    d.h3("A small heading")
    d.callout("Note: this is a shaded callout box used for disclaimers and tips.")
    d.code("docker compose up -d db backend\ncurl -s localhost:8080/api/markets")
    d.table(["Metric", "Value", "Gate"],
            [["macro F1", "0.375", "data-limited"],
             ["ROC-AUC", "0.578", "pass"],
             ["Brier", "0.606", "pass"]])
    d.image("performance", caption="Figure: the Performance page (equity + metrics).")
    out = os.path.join(os.path.dirname(__file__), "_smoke_en.docx")
    d.save(out)
    return out

path = build()
# validate: a valid, reopenable docx that has the image, a table and the TOC field
assert zipfile.is_zipfile(path), "not a valid docx zip"
Document(path)  # reopen
with zipfile.ZipFile(path) as z:
    xml = z.read("word/document.xml").decode("utf-8")
    has_img = any(n.startswith("word/media/") for n in z.namelist())
checks = {
    "has image": has_img,
    "has table": "<w:tbl>" in xml,
    "has TOC field": "TOC" in xml,
}
print(f"[en] {os.path.basename(path)}  "
      + "  ".join(f"{k}={'OK' if v else 'FAIL'}" for k, v in checks.items()))
assert all(checks.values()), "smoke checks failed"
print("smoke ok")
