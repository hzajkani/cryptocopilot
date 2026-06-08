"""Build the English and Persian CryptoCopilot guides (.docx).

    python3 build_guides.py        # build both
    python3 build_guides.py en     # build English only
    python3 build_guides.py fa     # build Persian only
"""
import os, sys
from docxlib import Doc

HERE = os.path.dirname(__file__)
OUT_EN = os.path.normpath(os.path.join(HERE, "..", "project", "en", "CryptoCopilot-Guide-EN.docx"))
OUT_FA = os.path.normpath(os.path.join(HERE, "..", "project", "fa", "CryptoCopilot-Guide-FA.docx"))


def build_english():
    from content_en import build_en
    d = Doc(rtl=False)
    build_en(d)
    d.save(OUT_EN)
    print(f"[en] wrote {OUT_EN}")


def build_persian():
    from content_fa import build_fa
    d = Doc(rtl=True)
    build_fa(d)
    d.save(OUT_FA)
    print(f"[fa] wrote {OUT_FA}")


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "both"
    if which in ("en", "both"):
        build_english()
    if which in ("fa", "both"):
        build_persian()
