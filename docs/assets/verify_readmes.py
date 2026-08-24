"""Structural checks for the eight README files.

Not a build step — a one-shot guard run after the docs rewrite. Verifies that the
translations stayed in lockstep, that nothing points at a deleted asset, and that
the facts corrected against the source did not creep back in.

Run:  python docs/assets/verify_readmes.py
"""

from __future__ import annotations

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FILES = ["README.md", "README.zh-CN.md", "README.ja.md", "README.ko.md",
         "README.fr.md", "README.es.md", "README.ar.md", "README.ru.md"]

failures = 0


def check(ok: bool, label: str, detail: str = "") -> None:
    global failures
    if not ok:
        failures += 1
    suffix = f" — {detail}" if detail and not ok else ""
    print(f"  [{'PASS' if ok else 'FAIL'}] {label}{suffix}")


def headings(text: str):
    return [(len(m.group(1)), m.group(2).strip())
            for m in re.finditer(r"^(#{2,3}) (.+)$", text, re.M)]


def slug(text: str) -> str:
    s = re.sub(r"`([^`]*)`", r"\1", text).lower()
    s = re.sub(r"[^\w\s-￿-]", "", s)
    return s.strip().replace(" ", "-")


texts = {}
for name in FILES:
    with open(os.path.join(ROOT, name), encoding="utf-8") as fh:
        texts[name] = fh.read()


print("1. heading structure (## / ###) identical across all 8 files")
base = [lvl for lvl, _ in headings(texts["README.md"])]
for name in FILES:
    got = [lvl for lvl, _ in headings(texts[name])]
    check(got == base, f"{name}: {len(got)} headings, same levels and order", f"got {got}")


print("\n2. relative link and image targets resolve on disk")
missing = []
for name in FILES:
    targets = re.findall(r'(?:src|href)="(?!https?:|#|mailto:)([^"]+)"', texts[name])
    targets += re.findall(r"\]\((?!https?:|#)([^)]+)\)", texts[name])
    for target in targets:
        target = target.split("#")[0]
        if target and not os.path.exists(os.path.join(ROOT, target.replace("/", os.sep))):
            missing.append(f"{name} -> {target}")
check(not missing, f"every relative target exists ({len(FILES)} files scanned)", "; ".join(missing))


print("\n3. superseded assets are no longer referenced")
for dead in ("sample-apk-card.png", "sample-apk-qr.png", "demo-cover.jpg"):
    hits = [n for n in FILES if dead in texts[n]]
    check(not hits, f"no reference to {dead}", ", ".join(hits))


print("\n4. language switcher completeness")
for name in FILES:
    linked = set(re.findall(r'href="(README(?:\.[a-zA-Z-]+)?\.md)"', texts[name]))
    expected = set(FILES) - {name}
    check(linked == expected, f"{name}: links exactly the other 7",
          f"missing={sorted(expected - linked)} extra={sorted(linked - expected)}")


print("\n5. in-page anchors resolve to a heading in the same file")
for name in FILES:
    slugs = {slug(h) for _, h in headings(texts[name])}
    anchors = re.findall(r"\]\(#([^)]+)\)", texts[name])
    bad = [a for a in anchors if a not in slugs]
    check(not bad, f"{name}: {len(anchors)} anchor(s) valid", f"unresolved={bad}")


print("\n6. video entry point")
BLOB = "https://github.com/Hu12037102/photo_choice/blob/master/docs/demo.mp4"
for name in FILES:
    text = texts[name]
    # `![...](docs/demo.mp4)` renders as a broken image on GitHub — it must not come back.
    check("](docs/demo.mp4)" not in text, f"{name}: no markdown-image-to-mp4 embed")
    # GitHub's HTML sanitiser strips <video>, so a hand-written one never renders.
    check("<video" not in text, f"{name}: no <video> element (GitHub strips it)")
    # blob/ shows GitHub's built-in player; raw/ would just download the file.
    check(text.count(BLOB) == 2 and "raw/master/docs/demo.mp4" not in text,
          f"{name}: poster + text link both point at the blob page",
          f"blob refs={text.count(BLOB)}")
    check("demo-poster.png" in text, f"{name}: poster image present")


print("\n7. no layout tables (GitHub borders every <td>)")
for name in FILES:
    check("<td" not in texts[name] and "<table" not in texts[name],
          f"{name}: no raw HTML table")


print("\n8. <picture> blocks well formed")
for name in FILES:
    text = texts[name]
    check(text.count("<picture>") == text.count("</picture>") == 4,
          f"{name}: 4 balanced <picture> blocks",
          f"open={text.count('<picture>')} close={text.count('</picture>')}")


print("\n9. fenced code blocks balanced")
for name in FILES:
    fences = len(re.findall(r"^```", texts[name], re.M))
    check(fences % 2 == 0, f"{name}: {fences} fences (even)")


print("\n10. Arabic RTL wrapper")
ar = texts["README.ar.md"]
check(ar.startswith('<div dir="rtl">'), 'README.ar.md opens with <div dir="rtl">')
check(ar.rstrip().endswith("</div>"), "README.ar.md closes the div")


print("\n11. facts corrected against the source have not crept back")
stale = {
    "Target SDK": "the library module declares no targetSdk",
    "older than 24 hours": "cleanup() deletes everything, not just expired files",
    "inJustDecodeBounds": "stale changelog note",
}
for phrase, why in stale.items():
    hits = [n for n in FILES if phrase in texts[n]]
    check(not hits, f"no '{phrase}' ({why})", ", ".join(hits))


print(f"\n{'ALL CHECKS PASSED' if not failures else f'{failures} CHECK(S) FAILED'}")
sys.exit(1 if failures else 0)
