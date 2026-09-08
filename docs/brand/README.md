# Marca — Bix Player Pro

| Item | Valor |
|---|---|
| Nome (plataforma e app) | Bix Player Pro |
| Cor principal (fundo) | `#050404` |
| Cor de destaque | `#FF8A00` |
| Texto sobre o destaque | `#050404` (branco sobre `#FF8A00` reprova no WCAG AA: 2,4:1) |
| Texto sobre o fundo | `#F5F3EA` (19:1) e `#C9C6B5` (12:1) |

## Arquivos

| Arquivo | Uso |
|---|---|
| `logo-original.png` | Arte recebida do cliente (xadrez rasterizado + marca d'água) — não usar |
| `logo.png` | Símbolo com transparência real, 1024×1024 |
| `logo-panel-light.png` / `logo-panel-dark.png` | Lock-up símbolo + nome para o painel (tema claro / escuro) |
| `favicon.png`, `favicon-192.png`, `favicon-512.png` | Favicon e ícones web |
| `ic_launcher_foreground.png` | Foreground do adaptive icon (432 px, zona segura de 66 dp) |
| `tv-banner.png` | Banner do launcher da Android TV (640×360) |

## Como regenerar

`make_assets.py` (Python 3 + Pillow + NumPy) remove o xadrez e a marca d'água por saturação —
tudo o que é cinza vira transparente e a cor das bordas é "desmisturada" do cinza local — e
grava as variantes acima **e** os recursos consumidos pelo código:

- `android/app/src/main/res/mipmap-*/ic_launcher_foreground.png`, `ic_launcher.png`, `ic_launcher_round.png`
- `android/app/src/main/res/drawable-xhdpi/app_banner.png`, `brand_logo.png`, `brand_symbol.png`
- `web/src/lib/assets/logo-light.png`, `logo-dark.png`, `symbol.png`, `favicon.png`; `web/static/icon-192.png`

```bash
py -3.12 -m pip install --user pillow numpy
py -3.12 docs/brand/make_assets.py
```

Depois de trocar a arte, rode o script, confira `logo.png` (fundo transparente, sem resíduos) e
recompile o app e o painel. Nome, pacote e versão do app ficam em `android/gradle.properties`
(`docs/ANDROID.md` §8); as cores em `ui/theme/Color.kt` (app) e `web/src/routes/layout.css` (painel).
