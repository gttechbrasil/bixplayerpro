"""E2E for the reseller CPF in the admin panel (F2-020). Needs the dev server (npm run dev) and the API,
plus Python 3 + playwright. The API defaults to :8001; set E2E_API to use another port.
Run: py -3.12 web/e2e/reseller_cpf.py — it creates one test reseller and deletes it at the end."""

import http.cookiejar
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

from playwright.sync_api import expect, sync_playwright

BASE = "http://localhost:5173"
API = os.environ.get("E2E_API", "http://localhost:8001/api/v1")
OUT = Path(os.environ.get("E2E_OUT", r"C:\Users\gustavo\Desktop\Projetos\projeto_lizandro\docs\screens\web"))
ENV = Path(r"C:\Users\gustavo\Desktop\Projetos\projeto_lizandro\backend\.env").read_text(encoding="utf-8")
ADMIN_PASS = re.search(r"^ADMIN_PASSWORD=(.*)$", ENV, re.M).group(1).strip().strip('"')
USERNAME = "e2e_cpf"
VALID, MASKED = "52998224725", "529.982.247-25"
OTHER, OTHER_MASKED = "11144477735", "111.444.777-35"
SECRET = "senha-e2e-123"

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
failures = []


def api(path, body=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method or ("POST" if data else "GET"))
    req.add_header("Content-Type", "application/json")
    csrf = next((c.value for c in jar if c.name == "csrf_token"), None)
    if csrf:
        req.add_header("X-CSRF-Token", csrf)
    with opener.open(req, timeout=30) as r:
        return json.loads(r.read() or b"null")


def check(cond, label):
    print(("ok   " if cond else "FAIL ") + label)
    if not cond:
        failures.append(label)
    return cond


def find_test_reseller():
    items = api(f"/admin/resellers?q={USERNAME}&per_page=50")["items"]
    return next((r for r in items if r["username"] == USERNAME), None)


def remove_test_reseller():
    r = find_test_reseller()
    if r:
        api(f"/admin/resellers/{r['id']}", method="DELETE")
        print("removed the test reseller")


def toast(page, text):
    return page.get_by_text(text, exact=False).first


api("/auth/admin/login", {"username": "admin", "password": ADMIN_PASS})
remove_test_reseller()

try:
    with sync_playwright() as p:
        browser = p.chromium.launch()
        ctx = browser.new_context(viewport={"width": 1366, "height": 800}, locale="pt-BR")
        page = ctx.new_page()
        page.goto(f"{BASE}/admin/login", wait_until="networkidle")
        page.fill("input[type=text], input[name=username]", "admin")
        page.fill("input[type=password]", ADMIN_PASS)
        page.click("button[type=submit]")
        page.wait_for_url(re.compile(rf"{BASE}/admin(?!/login)"), timeout=20000)

        # ---- create: the mask, a wrong number, then a right one
        page.goto(f"{BASE}/admin/revendedores", wait_until="networkidle")
        page.get_by_role("button", name="+ Novo revendedor").click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("Usuário (login)").fill(USERNAME)
        dialog.get_by_label("Nome").fill("Teste CPF")
        cpf = dialog.get_by_label("CPF")
        print("   campos do formulário:", dialog.locator("label").all_inner_texts())
        cpf.press_sequentially(VALID[:-1] + "6")
        check(cpf.input_value() == MASKED[:-1] + "6", f"máscara ao digitar ({cpf.input_value()})")
        dialog.get_by_label("Senha inicial").fill(SECRET)
        page.screenshot(path=str(OUT / "admin-revendedor-cpf-novo.png"))
        dialog.get_by_role("button", name="Criar").click()
        expect(toast(page, "CPF inválido")).to_be_visible(timeout=8000)
        message = toast(page, "CPF inválido").inner_text()
        check("Confira os números" in message and "Value error" not in message, f"mensagem clara: {message}")
        check(dialog.is_visible(), "o formulário continua aberto para corrigir")
        check(find_test_reseller() is None, "CPF errado não cria o revendedor")

        cpf.fill("")
        cpf.press_sequentially(MASKED)
        check(cpf.input_value() == MASKED, "colar já com a máscara não duplica os pontos")
        dialog.get_by_role("button", name="Criar").click()
        expect(toast(page, "Revendedor criado")).to_be_visible(timeout=8000)
        created = find_test_reseller()
        check(created is not None and created["cpf"] == VALID, "só os dígitos são gravados")

        # ---- detail: shown with the login, editable, can be cleared
        page.goto(f"{BASE}/admin/revendedores/{created['id']}", wait_until="networkidle")
        check(page.get_by_text(MASKED, exact=True).first.is_visible(), "CPF aparece junto do usuário em Acesso")
        form = page.locator("form", has=page.get_by_label("Tema do app"))
        field = form.get_by_label("CPF")
        check(field.input_value() == MASKED, "formulário de edição vem preenchido com a máscara")
        theme = form.get_by_label("Tema do app")
        check(theme.input_value() == "rail", "o tema atual vem selecionado no formulário")
        check(theme.locator("option:checked").inner_text() == "Menu lateral", "e com o nome certo")
        page.screenshot(path=str(OUT / "admin-revendedor-cpf-detalhe.png"), full_page=True)

        field.fill("")
        field.press_sequentially("000.000.000-00")
        form.get_by_role("button", name="Salvar").click()
        expect(toast(page, "CPF inválido")).to_be_visible(timeout=8000)
        check(find_test_reseller()["cpf"] == VALID, "editar com CPF errado mantém o anterior")

        field.fill("")
        field.press_sequentially(OTHER)
        form.get_by_role("button", name="Salvar").click()
        page.wait_for_timeout(1500)
        check(find_test_reseller()["cpf"] == OTHER, "editar troca o CPF")
        check(find_test_reseller()["theme"] == "rail", "salvar o CPF não muda o tema")
        page.reload(wait_until="networkidle")
        check(page.get_by_text(OTHER_MASKED, exact=True).first.is_visible(), "novo CPF aparece depois de recarregar")

        form = page.locator("form", has=page.get_by_label("Tema do app"))
        field = form.get_by_label("CPF")
        field.fill("")
        form.get_by_role("button", name="Salvar").click()
        page.wait_for_timeout(1500)
        check(find_test_reseller()["cpf"] is None, "apagar o campo remove o CPF")
        page.reload(wait_until="networkidle")
        acesso = page.locator("dl", has=page.get_by_text("CPF", exact=True)).first
        check("—" in acesso.inner_text(), "sem CPF mostra um traço")

        # ---- without one: the field stays optional; narrow phone screen has no sideways scroll
        phone = browser.new_context(viewport={"width": 390, "height": 800}, locale="pt-BR", storage_state=ctx.storage_state())
        small = phone.new_page()
        small.goto(f"{BASE}/admin/revendedores", wait_until="networkidle")
        small.get_by_role("button", name="+ Novo revendedor").click()
        d = small.get_by_role("dialog")
        check(d.get_by_label("CPF").is_visible(), "campo aparece em 390 px")
        overflow = small.evaluate("document.documentElement.scrollWidth - window.innerWidth")
        check(overflow <= 0, f"sem rolagem lateral em 390 px no formulário ({overflow})")
        small.screenshot(path=str(OUT / "admin-revendedor-cpf-celular.png"))
        small.goto(f"{BASE}/admin/revendedores/{created['id']}", wait_until="networkidle")
        overflow = small.evaluate("document.documentElement.scrollWidth - window.innerWidth")
        check(overflow <= 0, f"sem rolagem lateral em 390 px no detalhe ({overflow})")
        phone.close()

        # ---- the reseller never sees it
        api(f"/admin/resellers/{created['id']}", {"cpf": VALID}, method="PATCH")
        rctx = browser.new_context(viewport={"width": 1366, "height": 800}, locale="pt-BR")
        rpage = rctx.new_page()
        rpage.goto(f"{BASE}/painel/login", wait_until="networkidle")
        rpage.fill("input[name=username], input[type=text]", USERNAME)
        rpage.fill("input[type=password]", SECRET)
        rpage.click("button[type=submit]")
        rpage.wait_for_url(re.compile(rf"{BASE}/painel/(?!login)"), timeout=20000)
        rpage.wait_for_load_state("networkidle")
        check(VALID not in rpage.content() and MASKED not in rpage.content(), "o painel do revendedor não mostra o CPF")
        rctx.close()
        browser.close()
finally:
    remove_test_reseller()

print("failures:", failures or "none")
sys.exit(1 if failures else 0)
