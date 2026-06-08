"""Smoke test: exercise every docxlib block in LTR and RTL, then validate."""
import os, zipfile
from docx import Document
from docxlib import Doc

def build(rtl):
    d = Doc(rtl=rtl)
    d.title_page("CryptoCopilot", "Smoke Test", "a quick check of the builder",
                 ["Line A", "Line B"])
    d.h1("First heading", num=1)
    d.toc("Contents")
    d.page_break()
    d.h2("A sub heading")
    if rtl:
        d.p("این یک پاراگراف آزمایشی فارسی است که باید از راست به چپ نمایش داده شود "
            "و کلمه‌ی **پررنگ** و کد `XGBoost` را نیز شامل می‌شود.")
        d.bullet("یک مورد فهرست فارسی")
        d.bullet("مورد دوم با عدد ۰٫۹۰")
    else:
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
    out = f"_smoke_{'fa' if rtl else 'en'}.docx"
    d.save(os.path.join(os.path.dirname(__file__), out))
    return os.path.join(os.path.dirname(__file__), out)

for rtl in (False, True):
    path = build(rtl)
    # validate: valid zip, reopenable, and (for RTL) contains bidi/rtl markers
    assert zipfile.is_zipfile(path), "not a valid docx zip"
    Document(path)  # reopen
    with zipfile.ZipFile(path) as z:
        xml = z.read("word/document.xml").decode("utf-8")
        has_img = any(n.startswith("word/media/") for n in z.namelist())
    checks = {
        "has image": has_img,
        "has table": "<w:tbl>" in xml,
        "has TOC field": "TOC" in xml,
        "bidi present" if rtl else "no bidi (ltr)": ("w:bidi" in xml) == rtl,
        "rtl run present" if rtl else "ltr ok": ("w:rtl" in xml) == rtl,
    }
    print(f"[{'RTL/fa' if rtl else 'LTR/en'}] {os.path.basename(path)}  "
          + "  ".join(f"{k}={'OK' if v else 'FAIL'}" for k, v in checks.items()))
print("smoke ok")
