"""Build the English CryptoCopilot guide (.docx).

    python3 build_guides.py        # build the guide
"""
import os
from docxlib import Doc

HERE = os.path.dirname(__file__)
OUT_EN = os.path.normpath(os.path.join(HERE, "..", "project", "en", "CryptoCopilot-Guide-EN.docx"))


def build_english():
    from content_en import build_en
    d = Doc()
    build_en(d)
    d.save(OUT_EN)
    print(f"[en] wrote {OUT_EN}")


if __name__ == "__main__":
    build_english()
