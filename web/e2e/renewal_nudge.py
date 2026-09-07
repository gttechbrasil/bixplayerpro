"""E2E for the reseller expiration nudges. Needs the dev server (npm run dev), the API on :8001 and
Python 3 + playwright (`py -3.12 -m pip install --user playwright && py -3.12 -m playwright install chromium`).
Run: py -3.12 web/e2e/renewal_nudge.py — it changes the test reseller expiration and restores it at the end."""

import datetime as dt
import json
import re
import sys
import urllib.request
import http.cookiejar
from pathlib import Path

from playwright.sync_api import sync_playwright, expect

BASE = "http://localhost:5173"
API = "http://localhost:8001/api/v1"
OUT = Path(r"C:\Users\gustavo\Desktop\Projetos\projeto_lizandro\docs\screens\web")
ENV = Path(r"C:\Users\gustavo\Desktop\Projetos\projeto_lizandro\backend\.env").read_text(encoding="utf-8")
ADMIN_PASS = re.search(r"^ADMIN_PASSWORD=(.*)$", ENV, re.M).group(1).strip().strip('"')
RESELLER_ID = 1
ORIGINAL = "2027-09-04"

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def api(path, body=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method or ("POST" if data else "GET"))
    req.add_header("Content-Type", "application/json")
    csrf = next((c.value for c in jar if c.name == "csrf_token"), None)
    if csrf:
        req.add_header("X-CSRF-Token", csrf)
    with opener.open(req, timeout=30) as r:
        return json.loads(r.read() or b"null")


def set_expiration(date_str):
    api(f"/admin/resellers/{RESELLER_ID}/expiration", {"expires_at": date_str}, method="PATCH")
    print("expires_at ->", date_str)


def login_reseller(browser):
    ctx = browser.new_context(viewport={"width": 1366, "height": 800}, locale="pt-BR")
    page = ctx.new_page()
    page.goto(f"{BASE}/painel/login", wait_until="networkidle")
    page.fill("input[name=username], input[type=text]", "revenda")
    page.fill("input[type=password]", "revenda123")
    page.click("button[type=submit]")
    page.wait_for_url(re.compile(rf"{BASE}/painel/(?!login)"), timeout=20000)
    page.wait_for_load_state("networkidle")
    return ctx, page


api("/auth/admin/login", {"username": "admin", "password": ADMIN_PASS})
today = dt.date.today()
failures = []


def check(cond, label):
    print(("ok   " if cond else "FAIL ") + label)
    if not cond:
        failures.append(label)


try:
    with sync_playwright() as p:
        browser = p.chromium.launch()

        # --- trigger 1: 3 days -> banner, no modal ------------------------------------
        set_expiration(str(today + dt.timedelta(days=3)))
        ctx, page = login_reseller(browser)
        banner = page.get_by_test_id("renewal-banner")
        check(banner.is_visible(), "banner visible at 3 days")
        check("vence em 3 dias" in banner.inner_text(), "banner text says 3 days")
        check(page.get_by_role("dialog").count() == 0, "no modal at 3 days")
        page.screenshot(path=str(OUT / "painel-12-aviso-vencimento.png"))
        banner.get_by_role("button", name="Renovar agora").click()
        page.wait_for_timeout(800)
        dialog = page.get_by_role("dialog")
        check(dialog.count() > 0 and "Renovar revenda" in dialog.inner_text(), "banner button opens the Pix modal")
        check("Pix" in dialog.inner_text() or "Gerar" in dialog.inner_text(), "Pix modal shows the renewal form")
        page.screenshot(path=str(OUT / "painel-13-aviso-modal-pix.png"))
        ctx.close()

        # --- 4 days -> nothing ---------------------------------------------------------
        set_expiration(str(today + dt.timedelta(days=4)))
        ctx, page = login_reseller(browser)
        check(page.get_by_test_id("renewal-banner").count() == 0, "no banner at 4 days")
        ctx.close()

        # --- trigger 2: 1 day -> modal once per session + banner ----------------------
        set_expiration(str(today + dt.timedelta(days=1)))
        ctx, page = login_reseller(browser)
        page.wait_for_timeout(500)
        dialog = page.get_by_role("dialog")
        check(dialog.count() > 0 and "vence em 1 dia" in dialog.inner_text(), "modal at 1 day with the right text")
        page.screenshot(path=str(OUT / "painel-14-modal-vencimento.png"))
        dialog.get_by_role("button", name="Depois").click()
        page.wait_for_timeout(300)
        check(page.get_by_role("dialog").count() == 0, "'Depois' closes the modal")
        check(page.get_by_test_id("renewal-banner").is_visible(), "banner still shown at 1 day")
        page.goto(f"{BASE}/painel/dns", wait_until="networkidle")
        page.wait_for_timeout(500)
        check(page.get_by_role("dialog").count() == 0, "modal not repeated in the same session")
        # 'Renovar agora' in the modal opens the Pix modal (fresh session)
        ctx2, page2 = login_reseller(browser)
        page2.wait_for_timeout(500)
        d2 = page2.get_by_role("dialog")
        check(d2.count() > 0, "modal shown again in a new session")
        d2.get_by_role("button", name="Renovar agora").click()
        page2.wait_for_timeout(800)
        d3 = page2.get_by_role("dialog")
        check(d3.count() > 0 and "Renovar revenda" in d3.inner_text(), "modal button opens the Pix modal")
        ctx2.close()
        ctx.close()

        # --- today -> 'vence hoje' ------------------------------------------------------
        set_expiration(str(today))
        ctx, page = login_reseller(browser)
        page.wait_for_timeout(500)
        d = page.get_by_role("dialog")
        check(d.count() > 0 and "vence hoje" in d.inner_text(), "modal says 'vence hoje' on the last day")
        ctx.close()
        browser.close()
finally:
    set_expiration(ORIGINAL)

print("failures:", failures or "none")
sys.exit(1 if failures else 0)
