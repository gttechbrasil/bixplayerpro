"""E2E guard for the table search box (M5-027): typing must never lose a character.

Every keystroke arms a 350 ms debounce that navigates; the reload then hands `search` back to
the component. Before the fix that echo was copied into the input, wiping whatever had been
typed during the round trip — "josefina" became "jsfn", and a backspace looked like it deleted
two letters. The race only shows up with latency, so this test injects 500 ms into every API
call, which is what a real reseller on mobile data sees.

Needs the dev server (npm run dev), the API on :8001 and Python 3 + playwright.
Run: py -3.12 web/e2e/search_race.py
"""

import os
import re
import sys
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
SEL = "input#table-search"
LATENCY_S = float(os.environ.get("SEARCH_LATENCY", "0.5"))
ENV = Path(__file__).resolve().parents[2] / "backend" / ".env"
PAGES = (("painel", "/painel/dispositivos"), ("admin", "/admin/revendedores"))


def login(pg, which):
    if which == "painel":
        pg.goto(BASE + "/painel/login"); pg.wait_for_timeout(2000)
        pg.fill("input[name=username]", "revenda"); pg.fill("input[type=password]", "revenda123")
    else:
        password = re.search(r"^ADMIN_PASSWORD=(.*)$", ENV.read_text(encoding="utf-8"), re.M)
        pg.goto(BASE + "/admin/login"); pg.wait_for_timeout(2000)
        pg.fill("input[name=username]", "admin")
        pg.fill("input[type=password]", password.group(1).strip().strip('"').strip("'"))
    pg.click("button[type=submit]"); pg.wait_for_timeout(3000)


def slow_api(pg):
    def slow(route):
        time.sleep(LATENCY_S)
        route.continue_()

    pg.route("**/api/v1/**", slow)


def type_slowly(pg, text, delay_ms):
    pg.click(SEL)
    for ch in text:
        pg.keyboard.type(ch)
        pg.wait_for_timeout(delay_ms)
    pg.wait_for_timeout(1800)
    return pg.input_value(SEL)


def main() -> int:
    failures: list[str] = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        for which, path in PAGES:
            ctx = browser.new_context(viewport={"width": 1280, "height": 900})
            pg = ctx.new_page()
            login(pg, which)
            pg.goto(BASE + path); pg.wait_for_timeout(2500)
            slow_api(pg)

            def check(label, got, want):
                if got != want:
                    failures.append(f"{which}: {label} want {want!r} got {got!r}")
                print(f"[{which}] {label}: {'OK ' if got == want else 'FAIL'} {got!r}")

            # A keystroke every 400 ms fires the debounce between letters: the worst case.
            for delay in (400, 250, 120):
                pg.fill(SEL, ""); pg.wait_for_timeout(1500)
                check(f"type {delay}ms/key", type_slowly(pg, "josefina", delay), "josefina")

            pg.fill(SEL, ""); pg.wait_for_timeout(1500)
            type_slowly(pg, "josefina", 60)
            for _ in range(3):
                pg.keyboard.press("Backspace")
                pg.wait_for_timeout(400)
            pg.wait_for_timeout(1800)
            check("3 backspaces", pg.input_value(SEL), "josef")
            check("url follows the box", "search=josef" in pg.url, True)

            pg.goto(BASE + path + "?search=jose"); pg.wait_for_timeout(2500)
            check("deep link fills the box", pg.input_value(SEL), "jose")
            pg.fill(SEL, ""); pg.wait_for_timeout(2500)
            check("clearing drops the param", "search=" not in pg.url, True)
            ctx.close()
        browser.close()

    print("\nFAILURES:\n  " + "\n  ".join(failures) if failures else "\nall good")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
