# Especificação — Painel de Revenda (popplayer.pro / "ATV Exclusive Manager")

Documento de engenharia reversa da área logada, para reconstrução de um sistema equivalente.

- **Alvo:** `https://popplayer.pro/meuplayer/`
- **Conta usada:** revenda `42519228` — José Lizandro Santos Diniz
- **Data da captura:** 04/09/2026
- **Método:** navegação real em Chrome controlado via CDP/Playwright, com extração de DOM, formulários, modais, scripts inline e tráfego XHR/fetch.
- **Screenshots:** [`docs/screens/`](screens/)

> **Convenção deste documento:** `[OBS]` = observado diretamente no DOM/tráfego. `[INF]` = inferido a partir do código cliente ou do comportamento da UI, **não confirmado** por execução. Nenhum formulário foi submetido, nenhuma ação destrutiva foi confirmada e nenhum crédito/pagamento foi gasto — logo, todo comportamento de resposta do servidor é `[INF]`.

> **Segredos redigidos:** token CSRF, senha da revenda e credenciais das listas M3U aparecem em texto puro no HTML da aplicação original. Foram substituídos por `<REDACTED>` aqui. Ver [Riscos de segurança](#10-riscos-de-segurança-observados-no-sistema-original).

---

## 0. Decisões de escopo da reconstrução

Definido pelo cliente — vale sobre qualquer coisa observada no original:

### ⛔ NÃO haverá sistema de créditos

O painel original tem saldo de créditos (`Créditos: 999999` no header) e, presumivelmente, débito por usuário criado. **Isso não será replicado.** O sistema novo trabalha apenas com **acessos**.

| No original | Na reconstrução |
|---|---|
| Saldo de créditos por revenda | ❌ Não existe |
| Débito de crédito ao criar usuário | ❌ Não existe |
| Bloqueio por saldo zerado | ❌ Não existe |
| Compra/recarga de créditos | ❌ Não existe |
| Contador de créditos no header | ❌ Removido da UI |

### ✅ O que existe no lugar: acessos

- **1 acesso = 1 dispositivo = 1 endereço MAC.** É a unidade do sistema.
- A revenda **cria, edita e remove acessos livremente**, sem consumir saldo algum.
- O que a revenda gerencia é a *lista de acessos*; o que limita a revenda é o **vencimento da própria revenda** (§6), não um saldo.
- Terminologia: onde este documento diz "usuário" ou "usuario/device" (por fidelidade ao original), leia **acesso**.

> **Consequência prática:** todo item de crédito sai do backlog (§8) e a entidade `revenda` perde a coluna `credits` (§5). O `vencimento` da revenda **permanece** — é ele que controla o acesso ao painel e a monetização via Pix.

> ❓ **Ponto em aberto:** não foi definido se existe teto de quantidade de acessos por revenda (ex.: plano de 50 acessos) ou se é ilimitado. Enquanto não decidido, o modelo assume **ilimitado**; se houver teto, é um campo `max_acessos` na revenda + validação na criação — nada que se pareça com saldo consumível.

---

## 1. Descobertas estruturais

| Aspecto | Achado |
|---|---|
| Arquitetura | **Não é SPA.** PHP server-rendered clássico, uma página `.php` por tela `[OBS]` |
| Comunicação | POST de formulário para a própria URL (`action` vazio) na maioria das telas `[OBS]` |
| AJAX real | Apenas 3 pontos: cobrança Pix (`billing_action.php`), migração de DNS e exclusão em lote `[OBS]` |
| Front-end | jQuery 3.7.1 + CSS próprio (`assets/js/app.js`, `assets/js/theme.js`) — sem framework `[OBS]` |
| Modais | Implementação própria: `openModal(id)` / `closeModal(id)`, overlay `.modal-overlay` `[OBS]` |
| Notificações | Objeto global `Toast` com `.success()/.error()/.warning()/.info()` `[OBS]` |
| Tema | Claro/escuro via atributo `data-theme` no `<html>`, persistido em `localStorage['ibo-theme']` `[OBS]` |
| Proteção | Cloudflare na frente (desafio de bot em acesso sem sessão) `[OBS]` |

### Autenticação `[OBS]`

- Login em `GET/POST /meuplayer/` (**não existe `/login`** — retorna 404). Campos: `Usuário`, `Senha`, botão `Entrar`.
- Sessão via cookie de sessão PHP; sem token em header (`Authorization` não é usado em lugar nenhum).
- Acesso a página interna sem sessão → redireciona para `/meuplayer/`.
- Logout: `GET /meuplayer/logout.php`.
- CSRF: existe token (`CSRF = "<REDACTED>"`, 32 hex) mas **só é usado no fluxo de cobrança**; os demais formulários não têm campo CSRF `[OBS]`.

---

## 2. Mapa de telas

Shell comum a todas as telas: sidebar + header + footer `© 2026 — ATV Exclusive Manager`.

**Sidebar**
- Card **VENCIMENTO** (`26/09/2026`) — clicável, abre o modal *Renovar Revenda* (`.js-open-billing-renew`)
- **RECURSOS:** Meus Usuários (com contador), Migrador de DNS
- **PERSONALIZAÇÃO:** Logomarca, Background, Layouts, Banners, QR Code
- **CONTA:** Perfil, Sair

**Header:** nome da revenda · ~~`Créditos: 999999`~~ · toggle de tema · dropdown (Perfil, Sair)

> Na reconstrução o contador de créditos **sai do header** (§0). Sugestão de substituto útil no mesmo espaço: total de acessos ativos.

| # | Tela | Rota | Screenshot |
|---|---|---|---|
| 1 | Meus Usuários | `users.php` | [01-users.png](screens/01-users.png) |
| 1b | Modal Renovar Revenda | (modal em qualquer tela) | [01b-users-modal-renovar.png](screens/01b-users-modal-renovar.png) |
| 1c | Modal Confirmar Exclusão | (modal em `users.php`) | [01c-users-modal-excluir.png](screens/01c-users-modal-excluir.png) |
| 2 | Adicionar Usuário | `users_create.php` | [02-users-create.png](screens/02-users-create.png) |
| 3 | Editar Usuário | `users_update.php?update=<id>` | [03-users-edit.png](screens/03-users-edit.png) |
| 4 | Migrador de DNS | `migrardns.php` | [04-migrardns.png](screens/04-migrardns.png) |
| 5 | Logomarca | `logo.php` | [05-logo.png](screens/05-logo.png) |
| 6 | Background | `bg.php` | [06-bg.png](screens/06-bg.png) |
| 7 | Layouts | `layouts.php` | [07-layouts.png](screens/07-layouts.png) |
| 8 | Banners | `ads.php` | [08-ads.png](screens/08-ads.png) |
| 8b | Modal Novo Banner | (modal em `ads.php`) | [08b-ads-modal-novo-banner.png](screens/08b-ads-modal-novo-banner.png) |
| 9 | QR Code | `qr.php` | [09-qr.png](screens/09-qr.png) |
| 10 | Perfil | `profile.php` | [10-profile.png](screens/10-profile.png) |

### Fluxos principais

```
Login (/meuplayer/) ──► users.php (home)
                          │
   ┌──────────────────────┼───────────────────────────┐
   │                      │                           │
Adicionar            Editar (lápis)              Excluir
users_create.php   users_update.php?update=id    ├─ individual: modal confirm-delete
   │                      │                      │     └─► GET users.php?...&delete=id
   └── POST self ─────────┴── POST self          └─ lote: checkboxes + "Excluir Selecionados"
        └─► redirect users.php                         └─► POST users.php {delete_selected[]}
                                                             └─► corpo "success" ► reload

Sidebar VENCIMENTO ──► modal Renovar Revenda
   └─ escolhe meses (1..60) ──► POST billing_action.php {action=create_payment}
        └─► exibe QR Pix + copia-e-cola
             └─► polling 4s GET billing_action.php?action=check_status
                  └─► pago ► novo vencimento ► reload
```

---

## 3. Telas em detalhe

### 3.1 Meus Usuários — `users.php`

**Busca/paginação (GET, form próprio)** `[OBS]`
- `search` (texto, placeholder "Pesquise por MAC ou Nome...")
- `per_page` (hidden; seletor com 10 / 25 / 50 / 100)
- `page` (na querystring dos links de paginação)
- Links: `users.php?page=1&per_page=25&search=` — controles `«  ‹  1  ›  »`
- Rodapé da tabela: `Mostrando 1 de 1`

**Tabela:** `[ ]` (checkbox) · CLIENTE · MAC · EDITAR · EXCLUIR
- Checkbox de linha: `.user-checkbox` com `value = <id do usuário>` `[OBS]`
- `#select-all` marca/desmarca a página atual, com estado `indeterminate` parcial `[OBS]`
- EDITAR → `./users_update.php?update=<id>`
- EXCLUIR → `.delete-item` com `data-href="users.php?page=1&per_page=25&delete=<id>"`; o clique **não navega**, apenas injeta o href no botão do modal `confirm-delete` `[OBS]`

**Ações**
- `Adicionar Usuário` → `./users_create.php`
- `Excluir Selecionados` → validação client-side + `confirm()` nativo + AJAX (ver §4.2)

**Mensagens** `[OBS]`
- `Selecione pelo menos um usuário.` (warning) — nenhum checkbox marcado
- `Excluir N usuário(s) selecionado(s)?` — `confirm()` nativo
- `Usuários excluídos com sucesso!` / `Erro ao excluir.` / `Falha na requisição.`

### 3.2 Adicionar Usuário — `users_create.php`

`POST` para a própria página, `application/x-www-form-urlencoded` `[OBS]`

| Campo | Tipo | Regras observadas | Rótulo na UI |
|---|---|---|---|
| `mac_address` | text | **required**, `maxlength=17`, placeholder `00:11:22:33:44:55` | MAC do Dispositivo |
| `client_name` | text | opcional, placeholder `Ex: João Silva` | Nome do Cliente |
| `title` | text | **required**, placeholder `Nome da Playlist` | Nome da Playlist |
| `url` | text | **required**, placeholder `URL da Lista M3U` | Lista M3U |
| `expire_date` | date | valor padrão `2050-01-01` | Licença MAC |
| `key` | hidden | `310728` — identificador da revenda/painel | — |
| `username` | hidden | vazio no create | — |
| `password` | hidden | vazio no create | — |
| `submit` | submit | rótulo `Enviar` | — |

Botão `Cancelar` → `users.php`.

> `[INF]` Os hidden `username`/`password` guardam as credenciais extraídas da URL M3U (ver §3.3) — no create vêm vazios, presumivelmente preenchidos no servidor a partir da `url`.

### 3.3 Editar Usuário — `users_update.php?update=<id>`

Mesmos campos, pré-preenchidos, com diferenças `[OBS]`:

| Campo | Diferença em relação ao create |
|---|---|
| `id` | hidden, com o id do registro (ex.: `328625`) |
| `expire_date` | vira `input[type=text]` com placeholder `YYYY-MM-DD` (no create é `date`) |
| `username` | hidden **preenchido** — ex.: `338679532` |
| `password` | hidden **preenchido** — ex.: `<REDACTED>` |
| `key` | hidden `310728` |
| botão | `Atualizar` |

Exemplo real de `url` (credenciais redigidas):
`http://cnplay.click/get.php?username=<REDACTED>&password=<REDACTED>&type=m3u_plus&output=hls`

> Confirma que `username`/`password` do registro **são os mesmos parâmetros da querystring da URL M3U** — ou seja, o painel faz o parsing da linha Xtream Codes e guarda os campos separadamente `[INF]`.

### 3.4 Migrador de DNS — `migrardns.php`

| Campo | Tipo | Regras |
|---|---|---|
| `dns_atual` | select | **required**; populado dinamicamente com as DNS distintas em uso pelos usuários da revenda (ex.: `http://cnplay.click`) |
| `dns_nova` | text | **required**, placeholder `https://novadns.com` |

Submit é interceptado (`e.preventDefault()`) e vira AJAX (ver §4.3) `[OBS]`.

**Mensagens** `[OBS]`: `DNS migrada com sucesso!` · `DNS iguais. Nenhuma alteração feita.` (ou `message` do servidor) · `Erro ao migrar DNS.` · `Erro de conexão.`

### 3.5 Logomarca — `logo.php` / Background — `bg.php`

Telas idênticas em estrutura `[OBS]`:

| Campo | Tipo | Regras |
|---|---|---|
| `image-url` | `input[type=url]` | **required** (validação nativa de URL pelo browser) |
| `url-submit` | submit | rótulo `Atualizar` |

- `logo.php` — placeholder `https://example.com/logo.png`; valor atual `https://i.imgur.com/GXfOayD.png`
- `bg.php` — placeholder `https://example.com/background.png`; valor atual `img/atvbackground.png` (caminho relativo — aceita valor que não passa na validação `type=url` do próprio campo, indicando que o padrão vem do servidor)

> Não há upload de arquivo: **só URL externa** `[OBS]`.

### 3.6 Layouts — `layouts.php`

`select[name=theme]` + botão `Salvar`. Tema ativo na conta: `theme_8` `[OBS]`.

| value | Rótulo | Dimensão de banner exigida |
|---|---|---|
| `theme_d` | Padrão | — |
| `theme_1` | Tema 1 | 1920×1080 |
| `theme_2` | Tema 2 | 485×345 |
| `theme_3` | Tema 3 | 1920×1080 |
| `theme_4` | Tema 4 | 240×338 |
| `theme_5` | Tema 5 | 240×338 |
| `theme_6` | Tema HTV | Banners automáticos |
| `theme_7` | Tema XC | 725×395 |
| `theme_8` | Tema P2P | 1024×418 |

A tela mostra cards de pré-visualização dos temas, com selo `Ativo` no atual.

### 3.7 Banners — `ads.php`

**Modal `newAdModal`** (form `#newAdForm`, POST para a própria página) `[OBS]`

| Campo | Tipo | Regras |
|---|---|---|
| `submit` | hidden | valor fixo `1` |
| `title` | text | **required**, placeholder `Título do Banner` |
| `url` | text | **required**, placeholder `https://exemplo.com/banner.jpg` |

Botões: `Cancelar` (fecha) e `Enviar` (`type=submit form=newAdForm`).

**Toggle "Banners Automáticos"** (form `#autoAdsForm`) `[OBS]`
- `input[hidden] name=toggleAdType value=off` + `input[checkbox] name=toggleAdType value=on`
- `onchange="this.form.submit()"` — envia na hora, sem botão
- Padrão clássico de checkbox: desmarcado envia `off`, marcado envia `on`
- Estado atual: **Desativado**

**Tabela:** Pré-visualização · Título · URL · Status · Ativo · Excluir — atualmente `Nenhum banner cadastrado.`

> ⚠️ **Lacuna:** sem banners cadastrados, as ações de linha (alternar `Ativo`, `Excluir`, e o significado de `Status`) **não puderam ser capturadas**. Ver §9.

### 3.8 QR Code — `qr.php`

| Campo | Tipo | Regras |
|---|---|---|
| `qr-content` | text | **required**, placeholder `Texto, link ou informação para o QR Code` |

Botão `Salvar QR Code`. Campo vazio na conta. `[INF]` O QR é renderizado no app do cliente, não no painel — a tela só armazena o conteúdo.

### 3.9 Perfil — `profile.php`

| Campo | Tipo | Regras |
|---|---|---|
| `name` | text | **readonly** — `José Lizandro Santos Diniz` |
| `username` | text | **readonly** — `42519228` |
| `password` | text | editável — **exibida em texto puro** (`<REDACTED>`) |
| `submit` | submit | `Atualizar` |

Única alteração possível pela revenda: a própria senha `[OBS]`.

---

## 4. Contrato de API inferido

Base: `https://popplayer.pro/meuplayer/`. Todas as chamadas dependem do cookie de sessão PHP.

### 4.1 Navegação / CRUD por formulário

| Ação | Método | Rota | Payload |
|---|---|---|---|
| Login | POST | `/meuplayer/` | `usuario`, `senha` *(nomes exatos não capturados — ver §9)* |
| Logout | GET | `logout.php` | — |
| Listar usuários | GET | `users.php` | `?page=&per_page=&search=` |
| Form de criação | GET | `users_create.php` | — |
| Criar usuário | POST | `users_create.php` | `key`, `username`, `password`, `mac_address`, `client_name`, `title`, `url`, `expire_date`, `submit` |
| Form de edição | GET | `users_update.php` | `?update=<id>` |
| Atualizar usuário | POST | `users_update.php?update=<id>` | `id`, `mac_address`, `client_name`, `title`, `url`, `expire_date`, `key`, `username`, `password`, `submit` |
| Excluir 1 usuário | GET | `users.php` | `?page=&per_page=&delete=<id>` |
| Salvar logomarca | POST | `logo.php` | `image-url`, `url-submit` |
| Salvar background | POST | `bg.php` | `image-url`, `url-submit` |
| Salvar layout | POST | `layouts.php` | `theme`, `submit` |
| Criar banner | POST | `ads.php` | `submit=1`, `title`, `url` |
| Alternar banners automáticos | POST | `ads.php` | `toggleAdType=on\|off` |
| Salvar QR Code | POST | `qr.php` | `qr-content` |
| Atualizar senha | POST | `profile.php` | `name`, `username`, `password`, `submit` |

`[INF]` Respostas: HTML da própria página re-renderizada (ou redirect 302 para a listagem), com mensagem de sucesso/erro embutida. **Nenhuma foi observada** — nada foi submetido.

> ⚠️ **Exclusão via GET** é um defeito do original (idempotência/CSRF/pré-fetch). Na reconstrução, use `DELETE`/`POST`.

### 4.2 Exclusão em lote — AJAX `[OBS]`

```
POST users.php
Content-Type: application/x-www-form-urlencoded
Body: delete_selected[]=328625&delete_selected[]=328626

Resposta: text/plain — exatamente "success" (comparado com resp.trim() === 'success')
```

### 4.3 Migração de DNS — AJAX `[OBS]`

```
POST migrardns.php
Body: ajax=true&dns_atual=<origem>&dns_nova=<destino>
Resposta (JSON): { "status": "success" | "warning" | "error", "message": "<texto opcional>" }
```

```
POST migrardns.php
Body: listar_dns=true
Resposta (JSON): ["http://cnplay.click", ...]   // array de strings
```

### 4.4 Cobrança Pix — `billing_action.php` `[OBS]`

**Gerar cobrança**
```
POST billing_action.php
Content-Type: multipart/form-data (FormData)
Campos: action=create_payment
        months=<1..60>
        csrf=<token de 32 hex, renderizado inline na página>
        package_id=<opcional, quando há pacotes promocionais>

Sucesso: { "success": true, "payment_id": "...", "qr_base64": "<PNG base64, sem prefixo>", "qr_code": "<copia-e-cola Pix>" }
Erro:    { "success": false, "error": "<mensagem exibida no modal>" }
```

**Consultar status** (polling a cada 4 s, iniciado imediatamente)
```
GET billing_action.php?action=check_status&payment_id=<id>

Aprovado: { ..., "vencimento": "YYYY-MM-DD" }   // dispara tela de sucesso + reload em 2,5 s
```

Observações `[OBS]`:
- `UNIT_PRICE = 35` e `MAX_MONTHS = 60` estão **hardcoded no JS**; o total é calculado no cliente (`R$ 35,00 × meses`).
- Pacotes promocionais: radios `input[name=billing_package]` com `data-price` e `data-months`; quando um pacote é escolhido, o campo de meses é ocultado. **Esta conta não possui pacotes** — só o modo avulso.
- Comentário no próprio código: *"Fechar o modal não cancela o polling nem o pagamento: o webhook confirma em background"* → existe **webhook do provedor Pix** no servidor.
- Mensagens: `Pagamento aprovado! Revenda renovada.` · `Não foi possível gerar o Pix.` · `Falha de conexão. Tente novamente.` · `Código Pix copiado!`

---

## 5. Modelo de dados

```
┌──────────────────────────────┐
│ revenda (reseller)           │
│──────────────────────────────│
│ id                    PK     │
│ key            310728        │◄── enviado como hidden em todo form de usuário
│ name           "José ..."    │
│ username       42519228      │  (readonly na UI)
│ password       texto puro(!) │
│ ✗ credits — NÃO replicar §0  │
│ vencimento     2026-09-26    │
│ theme          theme_8       │  FK lógica → catálogo de temas
│ logo_url       https://...   │
│ bg_url         img/...       │
│ qr_content     (vazio)       │
│ auto_ads       false         │  toggleAdType
└──────────────┬───────────────┘
               │ 1:N
      ┌────────┴─────────┬──────────────────────┐
      ▼                  ▼                      ▼
┌───────────────────┐ ┌──────────────────┐ ┌─────────────────────┐
│ acesso (device)   │ │ banner (ad)      │ │ pagamento           │
│───────────────────│ │──────────────────│ │─────────────────────│
│ id    328625  PK  │ │ id          PK   │ │ payment_id     PK   │
│ revenda_key   FK  │ │ revenda_id  FK   │ │ revenda_id     FK   │
│ mac_address       │ │ title            │ │ months  1..60       │
│   21:7A:E6:...    │ │ url              │ │ amount  = 35×months │
│ client_name       │ │ status    ?      │ │ status  pend/aprov  │
│ title (playlist)  │ │ active    bool   │ │ qr_code             │
│ url (M3U)         │ │ created_at ?     │ │ qr_base64           │
│ username  ─┐      │ └──────────────────┘ │ vencimento_result   │
│ password  ─┴─ derivados da querystring   │ created_at          │
│ expire_date  2050-01-01                  └─────────────────────┘
└───────────────────┘
```

**Entidades e relações**
- `revenda 1:N acesso` — via `key` (`310728`) `[OBS]`. Relação livre: **nada consome saldo ao inserir** (§0)
- `revenda 1:N banner` `[INF]`
- `revenda 1:N pagamento` `[INF]`
- `revenda N:1 tema` — catálogo fixo de 9 temas, não é tabela editável pela revenda `[OBS]`
- **A DNS não é entidade própria:** é o host da coluna `url` de cada usuário; o select do migrador é montado a partir dos valores distintos `[INF]`
- Personalização (logo, bg, qr, tema, auto_ads) são **colunas da revenda**, não tabelas separadas `[INF]`

**Chave natural do dispositivo:** o par (`revenda`, `mac_address`). O MAC é o que identifica a licença. `[INF]`

---

## 6. Regras de negócio observadas

### ~~Créditos~~ → Acessos

**Como era no original** (registrado só para referência histórica):
- Saldo exibido no header: `Créditos: 999999` `[OBS]`
- `[INF]` Cada usuário/MAC criado consumiria 1 crédito — nunca verificado. Não há tela de compra de créditos no menu, só renovação da revenda `[OBS]`.

**Como será na reconstrução** (decisão de escopo, §0):
- **Sem créditos.** Nenhum saldo, débito, recarga ou bloqueio por saldo.
- A revenda cria/edita/remove **acessos** (1 acesso = 1 MAC) sem limite de saldo.
- O gate comercial é o **vencimento da revenda**, não o saldo: revenda vencida perde o acesso ao painel; revenda em dia gerencia seus acessos à vontade.
- `[INF]` Se mais tarde for preciso limitar volume, use um teto declarativo (`max_acessos`) validado na criação — não um contador consumível.

### Vencimento da revenda
- Data no topo da sidebar (`26/09/2026`), com classe de estado `is-ok` — `[INF]` existem outras classes para alertar proximidade/vencido
- Renovação self-service por Pix: **R$ 35,00/mês**, 1 a 60 meses `[OBS]`
- Aprovação estende o vencimento; o servidor devolve a nova data e a tela recarrega `[OBS]`
- Confirmação é assíncrona via webhook — não depende do navegador ficar aberto `[OBS, via comentário no código]`

### Ativação por MAC
- Todo usuário é uma licença amarrada a um `mac_address` (`maxlength=17`, formato `XX:XX:XX:XX:XX:XX`) `[OBS]`
- O campo `expire_date` é rotulado **"Licença MAC"** e vem com padrão `2050-01-01` — na prática, licença vitalícia por padrão `[OBS]`
- `[INF]` O app (Android TV) consulta pelo MAC do aparelho e recebe playlist + personalização da revenda dona daquele MAC.
- Sem validação de formato de MAC no cliente além do `maxlength` — não há `pattern` `[OBS]`

### Migração de DNS
- Operação **em massa**: troca o host `dns_atual` por `dns_nova` em todos os usuários da revenda que usam aquela DNS `[INF]`
- Origem só pode ser uma DNS já em uso (select populado do servidor); destino é texto livre `[OBS]`
- DNS de origem igual à de destino → resposta `warning`, nenhuma alteração `[OBS]`
- Serve ao caso real de servidor IPTV que troca de domínio, evitando reconfigurar cliente a cliente `[INF]`

### Personalização
- Todas as imagens são **URL externa**, sem upload `[OBS]`
- O tema escolhido determina a dimensão exigida dos banners `[OBS]`
- Tema HTV (`theme_6`) usa banners automáticos, coerente com o toggle em `ads.php` `[INF]`

---

## 7. Notas de UI para replicar

- Modal próprio: overlay `.modal-overlay#<id>` + `.modal-box` (header/body/footer), API global `openModal(id)`/`closeModal(id)` `[OBS]`
- Toasts globais com 4 níveis `[OBS]`
- Tema escuro/claro com preferência do SO como padrão e override persistido em `localStorage` `[OBS]`
- Formulário do modal fica fora do `<form>` do footer, ligado pelo atributo `form="newAdForm"` no botão `[OBS]`
- Assets versionados por querystring (`?v=1781013254`) `[OBS]`
- Ícones: Boxicons (`bx bx-*`) `[OBS]`

---

## 8. O que precisa ser construído — backlog priorizado

### P0 — Núcleo (sem isso não há produto)
1. **Autenticação de revenda** — login por usuário/senha, sessão, logout, proteção de rotas. *Corrigir o original:* hash de senha (bcrypt/argon2), nunca exibir senha em texto puro.
2. **CRUD de acessos** (1 acesso = 1 MAC) — listagem com busca (MAC ou nome), paginação (10/25/50/100), criar, editar, excluir individual e em lote. **Sem consumo de crédito** (§0). *Corrigir:* exclusão por `POST`/`DELETE`, nunca `GET`.
3. **Modelo de dados + migrações** — `revenda` (**sem coluna `credits`**), `acesso`, `banner`, `pagamento`.
4. **Parser de lista M3U/Xtream** — extrair `username`/`password`/host da URL `get.php` e persistir separadamente.
5. **API de consumo do app (Android TV)** — endpoint que recebe o MAC e devolve playlist + personalização. **Não é visível no painel e precisa ser especificado do zero** — é a razão de existir do sistema.

### P1 — Diferenciais operacionais
6. **Migrador de DNS** — listar DNS distintas em uso + substituição em massa, com guarda de "DNS iguais" e contagem de registros afetados.
7. ~~**Controle de créditos**~~ — **REMOVIDO DO ESCOPO** (§0). Em substituição: **Gestão de acessos** — contador de acessos ativos, filtro por status/vencimento e ação em lote. Sem saldo, sem débito, sem histórico de movimentação de crédito.
8. **Vencimento da revenda** — data, estados visuais (ok/próximo/vencido) e bloqueio de acesso após vencer. **É este o gate comercial do sistema** (já que não há créditos).
9. **Personalização** — logomarca, background, QR Code, seleção de tema (catálogo de 9). *Melhorar:* aceitar upload além de URL.
10. **Banners** — CRUD, ativar/desativar, toggle de banners automáticos, validação de dimensão conforme o tema ativo.

### P2 — Monetização
11. **Cobrança Pix** — integração com PSP, geração de QR + copia-e-cola, polling de status, **webhook** de confirmação, extensão automática do vencimento.
12. **Pacotes promocionais** — preço/meses configuráveis por pacote (a estrutura existe no original, mas está inativa nesta conta).
13. **Preço configurável no servidor** — o original tem R$ 35 hardcoded no JS; deve vir do backend.

### P3 — Qualidade e segurança
14. **CSRF em todos os formulários** (o original só protege a cobrança).
15. **Validação server-side** de MAC, URL e datas + mensagens de erro padronizadas.
16. **Auditoria** — log de criação/exclusão/migração de DNS.
17. **Painel administrativo (dono do sistema)** — criar revendas, definir vencimento e preços (e, se adotado, o teto `max_acessos`). **Sem concessão de créditos** (§0). **Não observado** (fora do acesso desta conta), mas necessariamente existe.
18. **Tema claro/escuro + toasts + modais** como base de UI.

---

## 9. Lacunas — o que NÃO foi possível capturar

Registrado explicitamente para não virar suposição silenciosa:

1. **Respostas do servidor a submissões** — nenhum formulário foi enviado (evitando gasto de créditos e alterações na conta). Mensagens de sucesso/erro server-side, códigos de redirect e validações reais são desconhecidos.
2. **Nomes dos campos do form de login** — a captura começou já com sessão ativa.
3. **Ações de linha dos banners** — não há banner cadastrado; `Status`, `Ativo` e `Excluir` não puderam ser inspecionados.
4. ~~**Regra real de consumo de créditos**~~ — **lacuna encerrada:** deixou de importar, já que a reconstrução não terá créditos (§0). Não precisa mais ser investigada.
5. **Fluxo de cobrança além da geração** — o botão `Gerar Pix` não foi acionado (criaria cobrança real). Formato exato do `payment_id` e do payload de `check_status` são parcialmente inferidos.
6. **Paginação com múltiplas páginas** — a conta tem 1 usuário.
7. **Estados de vencimento próximo/vencido** — a conta está com vencimento distante (`is-ok`).
8. **Área administrativa e API do app de TV** — fora do escopo do acesso de revenda.

**Como fechar essas lacunas:** criar um usuário de teste descartável e observar a resposta; cadastrar um banner de teste; abrir o painel em janela anônima para capturar o form de login.

---

## 10. Riscos de segurança observados no sistema original

Não replicar — corrigir na reconstrução:

| Risco | Evidência |
|---|---|
| Senha da revenda **em texto puro** no HTML | `profile.php`, `input[name=password]` com o valor real |
| Credenciais da lista M3U em campos hidden | `users_update.php`, `username`/`password` visíveis no fonte |
| **Exclusão via GET** | `users.php?delete=<id>` — vulnerável a CSRF e pré-fetch |
| CSRF ausente em quase tudo | Token existe apenas no fluxo de cobrança |
| Preço no cliente | `UNIT_PRICE = 35` no JS; o valor cobrado precisa ser recalculado no servidor |
| Identificador de revenda em campo hidden | `key=310728` enviado pelo cliente — deve vir da sessão |

---

*Documento gerado a partir de captura automatizada em 04/09/2026. Screenshots em `docs/screens/`.*
