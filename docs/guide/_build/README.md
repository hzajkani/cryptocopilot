# docs/guide — build scripts (personal, gitignored)

Everything in `docs/guide/` is **gitignored** (personal material — never committed).
These scripts regenerate all three deliverables from scratch, so you can edit the
content and rebuild any time.

## One-time setup
```bash
python3 -m pip install python-docx python-pptx pillow
```

## Regenerate
```bash
cd docs/guide/_build

python3 build_guides.py        # → ../project/en  (.docx)
python3 build_deck.py          # → ../presentation/CryptoCopilot-DemoDay.pptx
python3 build_transcript.py    # → ../presentation/CryptoCopilot-DemoDay-Script.docx
```

## Files
| File | What it makes |
|---|---|
| `docxlib.py` | Shared Word builder (headings, tables, images, callouts, code). |
| `content_en.py` | The guide text (English). Edit this to change the guide. |
| `build_guides.py` | Renders the guide. |
| `build_deck.py` | The 12-slide Demo Day deck (dark theme + hand-drawn architecture diagram). |
| `build_transcript.py` | The speaker script (edit the `SLIDES` list to change the words). |
| `_smoke.py` | A quick self-test of the Word engine. |

## Notes
- **Table of contents** in the guides: a real Word TOC field. It fills in when you
  open the file in Word (fields are set to auto-update); if it looks empty, press
  Ctrl/Cmd+A then F9, or right-click → *Update Field*.
- Author / presenter name is **Kamran Zajkani** (guide cover, deck slide 1, script opening).
- Screenshots are read from `../screenshots/` by friendly alias (see `SHOT`/`SHOTS`).
