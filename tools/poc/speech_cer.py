#!/usr/bin/env python3
"""PoC 4 (phase 9): accept a spoken answer when the CER is at or under a threshold, instead of an exact match.

Reads docs/poc/4-speech.csv (the main session only). Text is normalized to Hangul syllables (no spaces, no
punctuation); CER = edit distance by syllable / syllables expected. A repetition redone with Back counts once (the
last row). "Trap" rows (kind=error) were said wrong on purpose: accepting one is a false accept.
It prints counts only, never the phrases.

    python3 tools/poc/speech_cer.py [docs/poc/4-speech.csv]
"""
import csv
import sys
import unicodedata

THRESHOLDS = [0.0, 0.10, 0.15, 0.20, 0.25, 0.30]


def hangul(text: str) -> str:
    text = unicodedata.normalize("NFC", text or "")
    return "".join(c for c in text if "가" <= c <= "힣")


def distance(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def cer(expected: str, transcript: str) -> float:
    e, t = hangul(expected), hangul(transcript)
    return distance(e, t) / len(e) if e else 1.0


def rows(path: str):
    last = {}
    for r in csv.DictReader(open(path, encoding="utf-8")):
        if r["session"] != "main" or r["condition"] != "normal":
            continue
        last[(r["engine"], r["kind"], r["expected"], r["rep"])] = r  # the last try of each repetition
    return list(last.values())


def table(path: str):
    data = rows(path)
    print("engine | threshold | normal (all) | words | phrases | false accepts (traps)")
    for engine in sorted({r["engine"] for r in data}, reverse=True):  # B first
        mine = [r for r in data if r["engine"] == engine]
        normal = [r for r in mine if r["kind"] in ("word", "phrase")]
        words = [r for r in normal if r["kind"] == "word"]
        phrases = [r for r in normal if r["kind"] == "phrase"]
        traps = [r for r in mine if r["kind"] == "error"]
        for t in THRESHOLDS:
            ok = lambda rs: sum(1 for r in rs if cer(r["expected"], r["transcript"]) <= t + 1e-9)
            print(f"{engine} | {t:.2f} | {ok(normal)}/{len(normal)} {100 * ok(normal) / len(normal):.0f}% | "
                  f"{ok(words)}/{len(words)} | {ok(phrases)}/{len(phrases)} | {ok(traps)}/{len(traps)}")
        # How far each trap is from the expected text: the margin a threshold has before it accepts one.
        print(f"{engine} | trap CERs: " + ", ".join(f"{cer(r['expected'], r['transcript']):.2f}" for r in traps))


if __name__ == "__main__":
    table(sys.argv[1] if len(sys.argv) > 1 else "docs/poc/4-speech.csv")
