# ANEXO I — ESPECIFICAÇÃO FUNCIONAL (v1.0)

Este anexo detalha e delimita o objeto do contrato de desenvolvimento firmado entre a CONTRATANTE e GTT Tecnologia Digital LTDA (CONTRATADO). Funcionalidades não listadas na Seção 2 estão fora do escopo da entrega inicial e serão tratadas conforme a Seção 6.

O IBO Player/IBO Revenda é usado apenas como referência conceitual de experiência. A solução terá identidade visual, código e arquitetura próprios.

---

## 1. Componentes entregues

| Componente | Descrição |
|---|---|
| **Backend + API** | Servidor da plataforma: banco de dados, autenticação, regras de negócio e API consumida pelo painel, dashboard e app |
| **Painel Administrativo** | Uso exclusivo da CONTRATANTE: gestão de revendedores, créditos, vencimentos, preços e configurações globais |
| **Dashboard do Revendedor** | Uso dos clientes/revendedores da CONTRATANTE: gestão dos próprios dispositivos e personalização do app |
| **Aplicativo Player** | App Android (celular/tablet) e Android TV (TV box, Smart TV Android) |

---

## 2. Funcionalidades incluídas

### 2.1 Painel Administrativo
- Login do administrador com senha criptografada
- Cadastro, edição, bloqueio e exclusão de revendedores
- Atribuição e ajuste manual de créditos por revendedor
- Definição da data de vencimento de cada revendedor
- Configuração do preço mensal de renovação e de pacotes promocionais (meses × valor)
- Visão geral: total de revendedores, dispositivos ativos, pagamentos recebidos
- Histórico de pagamentos e de movimentação de créditos
- Configurações globais: nome da plataforma, chaves do gateway de pagamento, versão mínima do app e link de atualização

### 2.2 Dashboard do Revendedor
- Login do revendedor; alteração da própria senha
- Exibição de créditos disponíveis e data de vencimento
- **Dispositivos (usuários):** listagem com busca por MAC ou nome, paginação, cadastro, edição, exclusão individual e em lote
  - Campos: MAC, nome do cliente, nome da playlist, URL da playlist (M3U ou Xtream), data de validade da licença
  - Cada dispositivo cadastrado consome 1 crédito
- **Migrador de DNS:** substituição em lote do endereço do servidor em todas as playlists do revendedor
- **Personalização (white label) do app:** logotipo, imagem de fundo, conteúdo do QR Code, banners (cadastro por URL, ativar/desativar), escolha de layout
- **Renovação por Pix:** geração de QR Code e código copia-e-cola, confirmação automática via webhook, extensão do vencimento
- Tema claro/escuro

### 2.3 Aplicativo Player
- Versões: **Android (celular/tablet)** e **Android TV** com navegação completa por controle remoto (D-pad)
- **Ativação:** ao abrir, o app exibe o MAC/código do dispositivo; após cadastro no dashboard do revendedor, carrega automaticamente as playlists e a personalização
- Exibição de status (ativo/expirado) e data de vencimento
- Suporte a playlists **Xtream Codes API** e **M3U/M3U8**
- **TV ao vivo:** categorias, lista de canais, favoritos, busca, reprodução em tela cheia
- **Filmes (VOD):** categorias, grade com capas, busca, tela de detalhes, reprodução
- **Séries:** categorias, temporadas e episódios, reprodução, continuar assistindo
- **EPG (guia de programação):** programa atual e próximo por canal, quando fornecido pela playlist
- **Player:** reprodução de HLS, TS e MP4; seleção de faixa de áudio e legenda embutidas; player secundário de fallback para streams incompatíveis
- Múltiplas playlists por dispositivo com troca dentro do app
- Controle parental por PIN (bloqueio de playlist e de categorias)
- Configurações: idioma (português/inglês/espanhol), ocultar categorias, limpar cache, período de atualização automática
- **Layouts:** 2 layouts de tela inicial selecionáveis pelo revendedor
- Exibição de logotipo, fundo, banners e QR Code configurados pelo revendedor
- Aviso de atualização quando houver nova versão

### 2.4 Backend
- API própria com HTTPS, JSON e autenticação por token por dispositivo
- Vinculação dispositivo → revendedor → playlists
- Integração com gateway de pagamento Pix (a definir entre Mercado Pago, Efí ou Asaas) com webhook
- Validação de dados, proteção CSRF, senhas com hash, registro de auditoria (criação/exclusão de dispositivos, migração de DNS, pagamentos)
- Hospedagem em VPS indicado pela CONTRATANTE, com instalação e documentação de implantação

---

## 3. Fora do escopo desta entrega

Podem ser contratados como fase 2 (Seção 6):
- Layouts adicionais além dos 2 incluídos
- Catch-up / TV de replay
- Legendas externas (OpenSubtitles) e metadados de filmes (TMDB)
- Compra de licença dentro do app (in-app purchase)
- Versões para iOS, Samsung Tizen, LG webOS, Fire TV, Roku ou web player
- Multi-idioma além dos 3 idiomas listados
- Sistema de afiliados, subrevenda em múltiplos níveis, relatórios avançados
- Fornecimento, hospedagem ou intermediação de qualquer conteúdo audiovisual, listas ou servidores de streaming

---

## 4. Publicação em lojas

O CONTRATADO entregará os pacotes (APK/AAB) assinados e preparados para publicação. A distribuição primária será por **APK direto** (download via QR Code/link). A submissão à Google Play será realizada quando solicitada, porém **a aprovação e permanência do app na loja dependem exclusivamente das políticas do Google** e não constituem obrigação de resultado do CONTRATADO.

---

## 5. Marcos de entrega e aceite

| Marco | Entrega | Prazo estimado |
|---|---|---|
| M1 | Backend + API + Painel Administrativo | Semanas 1–3 |
| M2 | Dashboard do Revendedor completo (incl. Pix) | Semanas 4–6 |
| M3 | App: ativação, playlists, TV ao vivo, player, layout 1 (Android TV) | Semanas 7–10 |
| M4 | App: filmes, séries, EPG, favoritos, busca, PIN, layout 2, versão celular | Semanas 11–14 |
| M5 | Testes integrados, ajustes, pacotes assinados, documentação e implantação | Semanas 15–16 |

- Cada marco é apresentado à CONTRATANTE, que terá **5 dias úteis** para homologar ou apontar desconformidades em relação a este anexo. Sem manifestação no prazo, o marco é considerado aceito.
- Ajustes de conformidade com este anexo estão incluídos. Alterações ou adições em relação ao anexo seguem a Seção 6.
- Garantia de correção de defeitos: **60 dias** após o aceite do M5.

---

## 6. Alterações de escopo

Solicitações de funcionalidades não previstas na Seção 2, ou mudanças em funcionalidades já homologadas, serão orçadas separadamente pelo CONTRATADO, com prazo e valor, e executadas somente após aprovação por escrito da CONTRATANTE. Referências ao IBO ou a outros aplicativos não ampliam automaticamente o escopo aqui definido.

---

## 7. Responsabilidades da CONTRATANTE

- Fornecer, em até 5 dias úteis do início: nome da plataforma e do app, logotipo, paleta de cores, conta do gateway Pix, acesso ao VPS e conta de desenvolvedor Google (se desejar publicação)
- Fornecer ao menos uma playlist de teste válida para homologação do player
- Homologar os marcos nos prazos da Seção 5
- Responsabilizar-se integralmente pelo conteúdo, listas e servidores utilizados por seus revendedores e usuários, conforme parágrafo segundo do contrato

---

Piracicaba, ___ de _______________ de 2026.

_______________________________          _______________________________
CONTRATANTE                                GTT Tecnologia Digital LTDA
