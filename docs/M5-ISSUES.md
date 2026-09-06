# M5 — Issues

Registro único de correções do marco 5. Nada aqui é funcionalidade nova: são defeitos,
endurecimento ou resíduos encontrados na revisão, na homologação e nos testes em hardware.

Gravidade: **alta** (segurança, perda de dados, app inutilizável), **média** (funcionalidade do
Anexo I com desvio), **baixa** (cosmético, melhoria de robustez). Status: `aberta`, `corrigida`
(commit indicado), `aceita` (risco documentado, sem correção nesta entrega).

| Id | Origem | Gravidade | Descrição | Status |
|---|---|---|---|---|
| M5-001 | Revisão de segurança | baixa | `GET /api/openapi.json` respondia em produção (a UI `/api/docs` já estava desativada), expondo o esquema completo da API. | corrigida — `openapi_url=None` quando `APP_ENV=production` |
| M5-002 | Revisão de segurança | média | `POST /device/register` (público, cria linha no banco) e `GET /device/config` não tinham limite de requisições; um cliente podia inflar a tabela `devices` ou martelar o banco. | corrigida — `app/core/ratelimit.py`: por IP e por dispositivo, com `Retry-After`; variáveis `DEVICE_*` no `.env` |
| M5-003 | Revisão de segurança | baixa | `PUT /reseller/branding` e `PATCH /admin/resellers/{id}` aceitavam `logo_url`/`bg_url` com qualquer esquema (ex.: `javascript:`), embora banners já fossem validados. | corrigida — só `http(s)://`, com teste |
| M5-004 | Revisão de segurança | baixa | Trocar/redefinir a senha de uma revenda não invalida sessões já abertas (JWT sem revogação; expira em até 12 h). Bloquear a revenda **é** imediato, pois cada requisição confere `is_blocked`. | aceita — mitigação: bloquear a revenda quando houver suspeita de sessão roubada; revogação por versão de senha fica para a fase 2 |
| M5-005 | Revisão de segurança | baixa | Excluir uma revenda no admin deixa os dispositivos dela desvinculados (`reseller_id = NULL`) em vez de apagá-los; eles podem ser reivindicados por outra revenda. | aceita — comportamento intencional (o MAC pertence ao aparelho, não à revenda); documentado no manual do admin |
| M5-006 | Endurecimento | média | Sem HSTS, sem `Permissions-Policy` e sem CSP no painel; sem limite de tamanho de corpo no proxy. | corrigida — `deploy/Caddyfile` |
| M5-007 | Teste de carga | média | Com 2.000 dispositivos chamando `config` em 60 s, p50 = 14 ms, mas p95 = 618 ms e p99 = 8 s no ambiente local (Windows, 1 worker). Ver `SECURITY-REVIEW.md` §4 para o diagnóstico e o que foi ajustado. | aberta — ver seção de carga |
| M5-008 | Limpeza (bloco 0) | baixa | Dois dispositivos avulsos de teste no banco de produção (`02:50:50:2B:BC:8A`, `02:50:50:07:22:08`, ambos sem revenda e sem playlist). | aberta — o comando de exclusão pelo `psql` precisa ser rodado por você (a sessão do Claude Code não tem permissão para escrever no banco de produção); ver `PLANO-M5.md` bloco 0 |
| M5-009 | Limpeza (bloco 0) | baixa | Resíduos de demonstração em produção: revenda `revenda01`, dispositivo `AA:BB:CC:00:11:22` ("Cliente Demo") com playlist `http://servidor.exemplo`, 2 Pix pendentes do sandbox (R$ 35,00, 05/09/2026). | aberta — remover junto com a revenda de teste no bloco 3 (com você) |
| M5-010 | Validação M4 (emulador) | baixa | A tecla **GUIDE** do controle não chega ao app no emulador (`KEYCODE_GUIDE` é interceptado pelo sistema); o guia abre pelo chip "Guia". Confirmar em TV box real com controle físico. | aberta — depende do bloco 1 |
| M5-011 | Validação M4 (emulador) | baixa | PiP, recorte da câmera (notch) e o giro de 180° do player no celular só foram testados no emulador; decodificação libVLC só em x86_64. | aberta — depende do bloco 1 |

## Como abrir uma issue

1. Reproduza e anote: aparelho/navegador, passos, resultado esperado × obtido, `adb logcat` ou
   resposta da API quando houver.
2. Acrescente uma linha na tabela com o próximo id e a origem (`Homologação`, `Hardware`,
   `Revisão`, `Carga`).
3. Correções entram em commits pequenos (`fix(escopo): …`) que citam o id.
