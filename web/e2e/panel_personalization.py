"""E2E for the reseller panel's personalization pages: banners removed (F2-015), QR code saved
for the app's Playlist screen (F2-016) and the default-logo option (F2-013). Needs the dev server
(npm run dev) on :5173, the API on :8001 and the local reseller `revenda`/`revenda123`.

    py -3.12 web/e2e/panel_personalization.py
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

from playwright.sync_api import sync_playwright

sys.stdout.reconfigure(encoding="utf-8")
BASE = "http://localhost:5173"
API = "http://localhost:8001/api/v1"
OUT = Path(__file__).resolve().parents[2] / "docs" / "screens" / "web" / "personalization"
OUT.mkdir(parents=True, exist_ok=True)
failures = []


def check(cond, label):
    print(("ok   " if cond else "FAIL ") + label)
    if not cond:
        failures.append(label)


def api_json(page, method, path, body=None):
    """Call the API from the page's session (cookies + CSRF) and return the parsed body."""
    return page.evaluate(
        """async ([method, path, body]) => {
            const csrf = document.cookie.match(/csrf_token=([^;]+)/)?.[1] ?? '';
            const res = await fetch('/api/v1' + path, {
                method, credentials: 'same-origin',
                headers: {'Content-Type': 'application/json', 'X-CSRF-Token': csrf},
                body: body == null ? undefined : JSON.stringify(body),
            });
            return {status: res.status, body: await res.json().catch(() => null)};
        }""",
        [method, path, body],
    )


with sync_playwright() as p:
    browser = p.chromium.launch()
    ctx = browser.new_context(viewport={"width": 1366, "height": 800}, locale="pt-BR")
    page = ctx.new_page()
    page.goto(f"{BASE}/painel/login", wait_until="networkidle")
    page.fill("input[name=username], input[type=text]", "revenda")
    page.fill("input[type=password]", "revenda123")
    page.click("button[type=submit]")
    page.wait_for_url(re.compile(rf"{BASE}/painel/(?!login)"), timeout=20000)

    # --- clean slate ---------------------------------------------------------------------------
    api_json(page, "PUT", "/reseller/branding", {"logo_url": None})

    # --- banners left the panel (F2-015) ---------------------------------------------------
    nav = page.get_by_role("navigation", name="Menu principal").inner_text()
    check("Banners" not in nav, "menu da revenda sem a aba Banners")
    resp = page.goto(f"{BASE}/painel/banners", wait_until="networkidle")
    check(resp is not None and resp.status == 404, f"/painel/banners não existe mais ({resp and resp.status})")

    # --- QR code: saved from the panel, shown in the app's Playlist screen (F2-016) ----------
    page.goto(f"{BASE}/painel/qrcode", wait_until="networkidle")
    check(page.locator("text=Configurações → Playlist").count() >= 1, "página do QR diz onde ele aparece no app")
    qr_input = page.get_by_label("Conteúdo do QR Code")
    qr_input.fill("https://wa.me/5511999999999")
    page.get_by_role("button", name="Salvar QR Code").click()
    page.wait_for_selector("text=QR Code salvo", timeout=15000)
    branding = api_json(page, "GET", "/reseller/branding")["body"]
    check(branding and branding.get("qr_content") == "https://wa.me/5511999999999", "qr_content salvo pela API")
    page.screenshot(path=str(OUT / "qrcode-01-salvo.png"), full_page=True)

    # --- logo: default option ------------------------------------------------------------------
    page.goto(f"{BASE}/painel/logomarca", wait_until="networkidle")
    page.wait_for_selector("text=Logomarca padrão", timeout=15000)
    btn = page.get_by_role("button", name="Usar logomarca padrão")
    check(btn.is_disabled(), "sem logo própria o botão fica desativado (padrão já em uso)")
    check(page.locator("text=Em uso. Envie a sua logo").count() == 1, "texto diz que a padrão está em uso")
    page.screenshot(path=str(OUT / "logo-01-padrao.png"), full_page=True)
    api_json(page, "PUT", "/reseller/branding", {"logo_url": "https://bixplayer.pro/uploads/backgrounds/bg2.jpg"})
    page.reload(wait_until="networkidle")
    btn = page.get_by_role("button", name="Usar logomarca padrão")
    check(btn.is_enabled(), "com logo própria o botão fica ativo")
    page.screenshot(path=str(OUT / "logo-02-propria.png"), full_page=True)
    btn.click()
    page.wait_for_selector("text=Logomarca padrão restaurada", timeout=15000)
    page.wait_for_timeout(800)
    branding = api_json(page, "GET", "/reseller/branding")["body"]
    check(branding and branding.get("logo_url") is None, "logo_url voltou a null")
    check(page.get_by_role("button", name="Usar logomarca padrão").is_disabled(), "botão desativa de novo")
    page.screenshot(path=str(OUT / "logo-03-restaurada.png"), full_page=True)

    # --- phone width: nothing overflows ------------------------------------------------------
    phone = browser.new_context(viewport={"width": 390, "height": 844}, locale="pt-BR", storage_state=ctx.storage_state())
    pp = phone.new_page()
    for path, name in (("/painel/qrcode", "qrcode"), ("/painel/logomarca", "logo")):
        pp.goto(f"{BASE}{path}", wait_until="networkidle")
        overflow = pp.evaluate("document.documentElement.scrollWidth - document.documentElement.clientWidth")
        check(overflow == 0, f"{name} a 390 px sem transbordo horizontal (overflow={overflow})")
        pp.screenshot(path=str(OUT / f"phone-{name}.png"), full_page=True)
    browser.close()

print("shots in", OUT)
if failures:
    print("FALHAS:", failures)
    sys.exit(1)
print("tudo ok")
