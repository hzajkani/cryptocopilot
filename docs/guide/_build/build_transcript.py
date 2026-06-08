"""Build the Demo Day speaker script (.docx) — simple, casual English (B1–B2),
one section per slide, sized for a ~10-minute talk.
    python3 build_transcript.py
"""
import os
from docx.shared import Pt
from docxlib import Doc, BLUE, TEAL, GREY

HERE = os.path.dirname(__file__)
OUT = os.path.normpath(os.path.join(HERE, "..", "presentation", "CryptoCopilot-DemoDay-Script.docx"))

# (number, title, seconds, on-screen cue, [script paragraphs])
SLIDES = [
    (1, "Title — CryptoCopilot", 20, "Title slide: the project name and the disclaimer.", [
        "Hi everyone, I’m **Kamran Zajkani**, and this is **CryptoCopilot**. It’s a smart assistant for people "
        "who are **new to the crypto world**.",
        "In the next ten minutes, I’ll show you what it does, how I built it, and what it can — and "
        "can’t — do. One quick note first: this is **not financial advice**, and it never touches real "
        "money. So, let’s start with a simple question: who really controls your money?",
    ]),
    (2, "The hook — money, control, and a different idea", 55, "Two cards: traditional money vs crypto.", [
        "Think about normal money, like euros or dollars. A **central bank** controls it. Your money "
        "sits in a bank, and the bank makes the rules. You even pay **fees** just to hold and move your "
        "own money. Someone else is always in charge.",
        "**Crypto is a different idea. It is decentralized.** That means no single boss and no central "
        "bank. You hold your own money directly. That gives real freedom — it’s open and global.",
        "But it also has downsides. It’s complex, the prices jump up and down a lot, and for a beginner "
        "it’s honestly **confusing**. So that’s the problem I wanted to solve. A newcomer looks at the "
        "crypto market and just sees noise. **CryptoCopilot turns that scattered data and news into "
        "clear information and simple signals** about each coin.",
    ]),
    (3, "Architecture — one system, two languages", 55, "The architecture diagram: five containers, one database.", [
        "Let’s look under the hood. CryptoCopilot is built from **five small parts**, called containers, "
        "around **one shared database**.",
        "At the front is the website you see, built with **React**. It talks to the **backend** — the "
        "brain — written in **Java with Spring Boot**. The backend does the technical analysis, the AI "
        "chat, the Analyst, and the trading. On the left, a **Python** service does the machine learning, "
        "and a second Python service lets me run those jobs on demand.",
        "Here’s the key idea, and the part I’m most proud of. The two languages, Python and Java, "
        "**never call each other directly**. They only share one database. **Python writes; Java reads.** "
        "It’s simple, clean, and very reliable.",
    ]),
    (4, "The data — 10 coins, 5 free sources", 50, "Coin list + two cards of data sources.", [
        "Good signals need good data. CryptoCopilot follows the **ten biggest coins** — Bitcoin, "
        "Ethereum, Solana, and so on.",
        "The data comes from **five free, public sources**. Binance gives the prices. CoinGecko gives "
        "market and community data. Five news sites give headlines. And two more give **on-chain** "
        "activity — what’s really happening on the blockchain.",
        "In total, that’s about **two hundred and thirty thousand rows** of data in one database. And "
        "it’s built to be safe: if any source goes down, the system just **skips it and keeps going**. "
        "It never crashes.",
    ]),
    (5, "Machine learning — an honest edge", 55, "The ML pipeline strip + a signal card.", [
        "Now the machine learning. This is the heart of the project.",
        "I start with raw price candles. From them I build **forty-six features** — useful numbers that "
        "describe the market. Then I train a model called **XGBoost**. It learns from about two years of "
        "history to predict one thing: will the price go **up, down, or stay flat** over the next "
        "twenty-four hours?",
        "Two things I really care about. First, the probabilities are **calibrated** — when it says "
        "seventy percent, it really means about seventy percent. Second, a tool called **SHAP** explains "
        "the top reasons behind every prediction.",
        "And I’m honest about the score. Its accuracy, called **AUC, is zero point five-eight**. That’s a "
        "**small but real edge** above pure luck. No small model can beat the market, and I don’t pretend "
        "it does.",
    ]),
    (6, "Technical analysis — signals you can audit", 45, "The Signals page.", [
        "The model is just one opinion. So I added a second, very different one: classic **technical "
        "analysis**.",
        "Using a Java library called **ta4j**, the backend reads the same price charts and applies famous "
        "indicators — **Ichimoku, RSI, MACD**, and more. From clear rules, it gives a simple verdict: "
        "**bullish, neutral, or bearish**.",
        "And the best part — it shows you **exactly which rules fired**. Nothing is hidden. You can check "
        "every step yourself.",
    ]),
    (7, "The Researcher — AI that cites its sources", 55, "The Analyst page with cited headlines.", [
        "Next, the part that makes it feel smart: the **Researcher**. It’s a chat you can ask questions.",
        "Normally, AI chatbots **make things up**. I didn’t want that. So I use a method called **RAG**. "
        "In simple words: before the AI answers, it **searches my real data** — news, on-chain data, and "
        "a knowledge base. Then it answers using **only those sources**, and it shows the **citations**.",
        "If the answer isn’t in the sources, it just says so. And it **never gives trading advice**. The "
        "nice thing: by default it runs **for free, on my own computer**. But with one click, I can "
        "switch to a stronger OpenAI model.",
    ]),
    (8, "The Analyst — four views, one opinion", 45, "The Analyst page, full view.", [
        "So now I have **four different views** on each coin: the machine learning signal, the technical "
        "analysis, the fundamentals, and the news.",
        "The **Analyst** brings them together into **one clear opinion** — a direction, how strong it is, "
        "and how much the four views agree.",
        "It’s fully rule-based, so it’s predictable and you can **audit** it. And a safety check makes "
        "sure the summary only uses **real numbers** — it can’t invent things.",
    ]),
    (9, "Paper trading — practice with no real money", 45, "Paper Trades + Performance pages.", [
        "What if you want to **act** on a signal? You can — safely. CryptoCopilot has **paper trading**. "
        "That means real trading mechanics, but with **fake money**. Nothing is ever at risk.",
        "You place an order, and the system fills it realistically, with a small fee, just like a real "
        "exchange. Then it **tracks your account** and shows your performance — your equity curve and "
        "your risk numbers.",
        "And again, I keep it honest. The simple test strategy **doesn’t make money**. The point was to "
        "build the engine **correctly**, not to get rich.",
    ]),
    (10, "The app — clean and simple", 40, "Markets + ML Pipeline screenshots.", [
        "All of this lives in a clean **web app**, built with React and TypeScript, so it’s fast and "
        "reliable.",
        "There are **seven pages** — markets, signals, the Analyst, the chat, your trades, and "
        "performance. There’s even a page where I can run the **whole pipeline** — get the data, train "
        "the model, make predictions — right from the browser, with one click.",
        "And every single page reminds you: this is **decision-support, not financial advice**.",
    ]),
    (11, "Honest results", 45, "Three result cards: ML, RAG, engineering.", [
        "Let me be straight about the results.",
        "The machine learning has a **small, real edge** — an AUC of zero point five-eight. The AI search "
        "finds the right source **ninety percent** of the time, and always with citations. And the whole "
        "thing — five services, three languages — is **tested and automated**.",
        "My goal was never to beat the market. My goal was to build a **real, production-grade system, "
        "end to end**, and to report the numbers **honestly**. The rules I never broke: **paper money "
        "only**, and always **decision-support, not advice**.",
    ]),
    (12, "Thank you", 15, "Closing slide.", [
        "And that’s **CryptoCopilot** — four views on every coin, joined into **one clear, explainable "
        "opinion**. Thank you so much for listening. I’d love to take any questions.",
    ]),
]


def build():
    d = Doc(rtl=False)
    d.title_page(
        "CryptoCopilot — Demo Day",
        "Speaker Script",
        "Friendly, simple English (B1–B2) · about 10 minutes · one section per slide.",
        ["Read it out loud a few times — the bold words are the ones to stress.",
         "Total speaking time ≈ 10 minutes · 12 slides"],
    )
    # how to use
    d.h2("How to use this script")
    d.bullet("Each section matches one slide. The grey line shows what is on the screen.")
    d.bullet("**Bold** words are the key points — say them slowly and with energy.")
    d.bullet("You don’t need to memorise word-for-word — keep the order and the **bold** points, and "
             "speak naturally.")
    d.bullet("The small time tag (e.g. ~55s) is a guide, not a rule. If you run long, the data and "
             "results slides are the easiest to shorten.")
    d.spacer(6)

    total = 0
    for num, title, secs, cue, paras in SLIDES:
        total += secs
        d.h2(f"Slide {num} · {title}   (~{secs}s)")
        d.note(f"On screen: {cue}")
        for para in paras:
            p = d.p(para, size=12.5)
            p.paragraph_format.space_after = Pt(8)
            p.paragraph_format.line_spacing = 1.3
    d.save(OUT)
    mins = total // 60
    print(f"wrote {OUT}  (12 slides, ~{total}s ≈ {mins} min {total - mins*60}s of script)")


if __name__ == "__main__":
    build()
