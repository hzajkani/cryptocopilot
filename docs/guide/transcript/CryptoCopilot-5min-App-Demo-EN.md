# CryptoCopilot — 5-Minute Live App Demo

**Spoken transcript for a live walkthrough at `http://localhost:3000`.**
Level: English B2 · Style: story-telling, human-friendly · Length: ~5 minutes (~780 words).

**How to use this:** the plain text is what you *say*. The **bold bracket lines** are
*stage directions* — what to click or point at. Speak slowly, pause when you click, and let the
screen do half the work. The whole talk follows the app's own menu, top to bottom.

---

### [0:00] Opening — the problem

**[SCREEN: app already open on the *Markets* page.]**

Hi everyone, I'm Kamran, and this is **CryptoCopilot** — a personal assistant for someone who is
brand new to crypto.

Here's the problem. Imagine you open a crypto app for the very first time. You see ten coins,
hundreds of prices, news everywhere, numbers jumping every second. It's just **noise**. You have no
idea what to think.

CryptoCopilot takes all of that noise and turns it into **one clear opinion** for each coin. Let me
show you — and everything you see here is live, running on my own machine right now.

---

### [0:35] Markets — the data

**[Point at the list of coins and the small source badges.]**

This first page is the **Markets** view — the ten biggest coins: Bitcoin, Ethereum, Solana, and so
on. The prices come from Binance.

But behind every row, the app is quietly pulling from **five free, public sources** at the same time
— prices, market data, news headlines, and real *on-chain* blockchain activity. That's around two
hundred thousand rows of data sitting in one database. And if any source goes down, the app simply
**skips it and keeps going**. It never crashes.

---

### [1:05] Signals — the machine-learning brain

**[CLICK → *Signals* in the menu.]**

Now, the brain. This is the **Signals** page. For every coin, a machine-learning model — trained on
about **two years** of price history — answers one simple question: over the next **twenty-four
hours**, will this coin go **up**, stay **flat**, or go **down**?

**[Point at the coloured probability bars.]**

See these bars? Those are real probabilities, and they are **calibrated** — when it says seventy
percent, it honestly means about seventy percent. I'm not promising magic here. The edge is small,
and I'll be honest about that at the end.

---

### [1:40] Analyst — four opinions become one

**[CLICK → *Analyst* in the menu.]**

But one signal is never enough. This is the **heart** of the app — the **Analyst**. For each coin,
it brings together **four** different opinions: the machine-learning signal you just saw, a classic
**technical-analysis** verdict from the charts, a **fundamental** health check, and the latest
**news**.

It fuses all four into one view — a **direction**, how strong the **conviction** is, and whether the
four sources actually **agree** with each other.

**[CLICK → the *Bitcoin* card to open its detail.]**

Let's open Bitcoin. Now I can see the whole story in one place. If the model, the charts, the
fundamentals, *and* the news all point the same way — that's a strong signal. If they disagree, the
Analyst tells me to be careful. This is the *"so what?"* that a beginner actually needs.

---

### [2:35] Researcher — ask, and get cited answers

**[CLICK → *Researcher* in the menu.]**

Still not sure? You can just **ask**. This is the **Researcher** — a chat you talk to in plain
English.

**[Type a question, e.g. "What is driving Ethereum right now?", and send it.]**

Watch the answer. Every claim comes with a small **citation** — a real news article or data point
the app actually read. And here's the important part: if I ask about something it has **no data**
for, it **refuses** instead of inventing an answer. No made-up facts.

**[Point at the Ollama / OpenAI toggle in the sidebar.]**

And by the way — this runs on a **free, local AI** on my own laptop, at zero cost. With this one
toggle, I can switch it to OpenAI whenever I want.

---

### [3:20] Paper Trades — act on it, safely

**[CLICK → *Paper Trades* in the menu.]**

Okay — say I've decided. Now I can **act**. This is the trading desk. And let me be very clear:
this is **paper money only**. No real money, ever.

I start with ten thousand dollars. Let me buy a little Bitcoin.

**[Place a MARKET BUY for a small amount and submit.]**

Done — filled instantly, with a realistic **fee** and a little **slippage**, just like a real
exchange. And every trade is logged.

---

### [3:55] Performance — the honest scoreboard

**[CLICK → *Performance* in the menu.]**

And every trade shows up right here, on the **Performance** page. This is my **equity curve**, plus
the real numbers a trader cares about — the **Sharpe ratio**, the **maximum drawdown**, the **win
rate**, and how much I paid in fees. It's a complete, honest scoreboard.

---

### [4:20] ML Pipeline — a glass box, not a black box

**[CLICK → *ML Pipeline* in the menu.]**

Last stop. I built this to be a **glass box**, not a black box. This page shows the whole
machine-learning pipeline — and my favourite part: for each prediction, it shows **exactly which
signals pushed it up or down**. You always get to see *why*.

And under the hood, there's one engineering idea I'm really proud of. The app speaks **two
languages**: **Python** does the machine learning, and **Java** runs everything else. They never call
each other directly — they just share **one database**. Python writes, Java reads. Simple, clean,
and reliable.

---

### [4:50] Closing — the honest truth

**[Point at the small disclaimer banner at the bottom of the page.]**

So that's CryptoCopilot. Let me finish honestly: the goal was **never** to beat the market — the
model's edge is deliberately small, and every single page carries this reminder: it is
**decision-support, not financial advice**.

The real achievement is the **engineering** — turning a wall of noise into one clear, **explainable**
opinion. Thank you.

---

## Delivery tips

- **Pace:** ~150 words per minute. Pause for one full breath every time you click — it gives the page
  time to load and gives you time to think.
- **If a page is slow or empty:** keep talking about *what it does* — never wait in silence. Run
  `make demo` beforehand so Markets, Signals, Analyst, Chat, Trades and Performance are all populated.
- **If the chat (Researcher) is offline** (Ollama down): turn it into a feature — *"Even when the AI
  is offline, notice it refuses cleanly instead of breaking."* Then move on.
- **Safest live trade:** a small **MARKET BUY** on BTC fills immediately. Avoid LIMIT orders on stage
  (they stay pending and look like nothing happened).
- **Three words to land:** *noise → one clear, explainable opinion.* If they remember only that,
  you've won.
```
