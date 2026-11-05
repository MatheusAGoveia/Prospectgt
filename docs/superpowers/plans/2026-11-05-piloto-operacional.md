# Piloto operacional V0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que cinco vendedores encontrem empresas automaticamente e trabalhem oportunidades reais em uma versão enxuta enquanto os modos avançados do mapa evoluem.

**Architecture:** Uma aplicação Spring Boot com módulos por domínio, PostgreSQL e interface responsiva renderizada no servidor. Reservas e auditoria são transacionais; a análise básica de sites é uma tarefa persistente em segundo plano. A V0 não exige broker de mensagens nem serviços distribuídos.

**Tech Stack:** Java LTS, Spring Boot, Spring Security, Thymeleaf, Spring Data JPA, Flyway, PostgreSQL, Maven e MapLibre com mapa 2D básico; testes JUnit e integração PostgreSQL com Testcontainers.

**Spec:** `docs/superpowers/specs/2026-11-04-prospectgt-design.md`.

## Global Constraints

- "Uso em celular, tablet e computador; interface limpa e funcional."
- "A oferta inicial é landing page/site de vendas, com projetos entre R$ 500 e R$ 2.000."
- "Não acompanhar mensagens ou respostas dentro do WhatsApp."
- "Meta de R$ 0 de mensalidade em servidor, mapas, IA e análise. Cadastro de cartão é aceitável; isso não autoriza despesas."
- "Ao assumir uma oportunidade, a empresa fica reservada por três dias úteis."
- "Visualizar a ficha ou clicar repetidamente não renova a reserva."
- "O evento se chama “clique para abrir WhatsApp”."
- Descoberta automática é essencial desde a primeira entrega, conforme esclarecimento do usuário em 05/11/2026. Cadastro/importação manual não substitui esse requisito.
- O piloto operacional não pode ser confundido com a implementação completa da especificação: mapa inclinado, rua/fotos e análise visual sofisticada são propostos para a entrega seguinte.

## Recorte proposto para a primeira entrega

O vendedor informa cidade/região e ramo. A ferramenta busca estabelecimentos reais em uma fonte automática, salva origem/data, exibe lista e pontos em um mapa 2D, consulta sinais básicos, permite assumir a oportunidade e abre uma mensagem personalizada no WhatsApp. Depois o vendedor acrescenta feedback e a data de retorno. A equipe compartilha a carteira; o gestor acompanha o histórico. Filtros iniciais: cidade, ramo, responsável e situação.

Cadastro e importação CSV são complementos administrativos. Não podem ser o único caminho de entrada e não podem ser usados para afirmar que a busca automática está entregue.

Excluídos desta V0 proposta: mapa inclinado/3D, navegação de rua integrada, coleta nacional antecipada, análise visual sofisticada, geração por IA e gestão de comissões. Nenhum desses itens deve aparecer como já entregue.

## Prazo de referência

Proposta preliminar de 2–3 semanas de trabalho dos dois desenvolvedores, com 4 horas por dia cada e apoio de IA, para o recorte operacional acima. Não é prazo contratual. Um primeiro fluxo de busca pode ser demonstrado antes, sem confundi-lo com um piloto compartilhado pronto. A disponibilidade de contatos na fonte e de hospedagem gratuita pode alterar a estimativa. O sistema completo continua com estimativa separada de 6–9 semanas.

## Review Focus

1. Tentativas simultâneas de assumir a mesma empresa: um único vencedor, sem abordagem liberada ao concorrente.
2. Horários próximos ao fim de semana e retorno agendado: expiração coerente em America/Sao_Paulo.
3. URLs internas, redirecionamentos e respostas grandes: o analisador não pode acessar a rede privada nem prender o servidor.
4. Dados incompletos e texto externo: não inventar WhatsApp, responsável ou problema; não executar HTML importado.
5. Reinício/limite de serviço: preservar carteira e histórico; tarefas inconclusivas recuperáveis, sem acionamento pago.

## Estrutura de arquivos proposta

- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`: compilação reproduzível e dependências mínimas.
- `src/main/java/br/com/wezttech/prospectgt/ProspectgtApplication.java`: inicialização.
- `src/main/java/br/com/wezttech/prospectgt/contas/`: autenticação, convites e perfis.
- `src/main/java/br/com/wezttech/prospectgt/empresas/`: cadastro, importação e consulta.
- `src/main/java/br/com/wezttech/prospectgt/descoberta/`: busca automática, geocodificação limitada, fonte externa e controle de consultas.
- `src/main/java/br/com/wezttech/prospectgt/carteira/`: reserva, situação e feedback.
- `src/main/java/br/com/wezttech/prospectgt/analise/`: tarefas, acesso externo limitado e sinais objetivos.
- `src/main/java/br/com/wezttech/prospectgt/mensagens/`: templates e abertura de contato.
- `src/main/java/br/com/wezttech/prospectgt/auditoria/`: histórico de ações.
- `src/main/resources/db/migration/`: esquema versionado.
- `src/main/resources/templates/` e `static/`: telas e recursos locais.
- `src/test/java/br/com/wezttech/prospectgt/`: testes das regras e fronteiras HTTP/banco.
- `compose.yaml`, `.env.example`, `README.md`: execução e operação, sem credenciais reais no Git.

## Task 1: Contas e cadastro compartilhado de empresas

**Files:**
- Create: `pom.xml`, Maven Wrapper, `ProspectgtApplication.java`, `application.yml`.
- Create: `contas/Conta.java`, `contas/SegurancaConfig.java`, `contas/ConviteService.java`.
- Create: `empresas/Empresa.java`, `empresas/EmpresaService.java`, `empresas/EmpresaController.java`, `empresas/ImportacaoCsvService.java`.
- Create: `db/migration/V1__contas_empresas.sql`.
- Test: `CadastroEmpresaIntegrationTest.java`, `ImportacaoCsvIntegrationTest.java`, `AcessoIntegrationTest.java`.

**Interfaces:**
- Produces: `EmpresaService.cadastrar(UUID vendedorId, CadastroEmpresa entrada): UUID`.
- Produces: `EmpresaService.importarDaFonte(UUID atorId, RegistroFonte entrada): UUID`, com atualização idempotente por namespace/identificador da fonte, sem apagar histórico ou reserva.
- Produces: `EmpresaService.consultar(FiltroEmpresa filtro, UUID usuarioId): Page<EmpresaResumo>`.
- Produces: `ImportacaoCsvService.importar(UUID adminId, InputStream csv): ResultadoImportacao`.
- `CadastroEmpresa`: nome obrigatório; cidade/UF/ramo, site, telefone, tipo de contato, nome do responsável e URL da fonte opcionais. Campos de endereço e contato não são evidência de WhatsApp confirmado.
- `RegistroFonte`: namespace/identificador da fonte obrigatórios; nome, categoria, coordenadas, endereço, site, contato publicado, URL e instante de obtenção, mantendo campos desconhecidos explicitamente ausentes.
- `ResultadoImportacao`: linhas criadas, rejeitadas e erros por linha; importações repetidas usam identificador da fonte quando fornecido, sem unir empresas apenas por nome.

- [ ] Escrever testes: usuário anônimo não consulta carteira; vendedor não cria/desativa outra conta; cadastro válido persiste; nome vazio é rejeitado; CSV malformado devolve erro de linha e não cria registros falsos; strings com HTML permanecem texto na renderização.
- [ ] Executar os testes e registrar a falha inicial esperada.
- [ ] Implementar login, cadastro/importação, autorização e migração; criar administrador somente a partir de configuração segura. Não distribuir senha fixa de produção.
- [ ] Executar `./mvnw test` ou `mvnw.cmd test`, com Docker disponível para os testes PostgreSQL; confirmar os casos acima e persistência em banco real.
- [ ] Fazer commit do cadastro funcional e seus testes.

## Task 2: Descoberta automática e pontos no mapa

**Files:**
- Create: `descoberta/BuscaEmpresaService.java`, `descoberta/FonteEmpresa.java`, `descoberta/OverpassFonteEmpresa.java`, `descoberta/BuscaController.java`.
- Create: `db/migration/V2__buscas_fontes.sql`.
- Test: `BuscaEmpresaIntegrationTest.java`, `OverpassFonteEmpresaTest.java`.

**Interfaces:**
- Consumes: `EmpresaService.importarDaFonte(UUID, RegistroFonte)`, cadastro/consulta de empresas e contas da Task 1. O conector transforma `EmpresaEncontrada` em `RegistroFonte`, sem acessar repositórios privados de outro módulo.
- Produces: `BuscaEmpresaService.solicitar(UUID vendedorId, BuscaEntrada entrada): UUID` e `BuscaEmpresaService.consultar(UUID buscaId, UUID vendedorId): BuscaResultado`.
- Produces: `FonteEmpresa.buscar(AreaBusca area, Categoria categoria): List<EmpresaEncontrada>`.
- `BuscaEntrada`: área selecionada no mapa e categoria permitida. `AreaBusca`: limites geográficos válidos, com tamanho máximo configurado. `EmpresaEncontrada`: identificador da fonte, nome publicado, coordenadas, campos de endereço/contato/site existentes, origem e data.
- Proposta de conector de piloto: Overpass de um operador que permita esse uso, como Private.coffee, com cache, uma consulta externa simultânea e resultados limitados; condicionado à validação real de disponibilidade/contatos. Não depender da instância OSM principal como infraestrutura permanente. Alternativa prevista na especificação: catálogo regional ingerido de extratos OSM, ainda com descoberta automática para o vendedor.
- A seleção de região usa o mapa e alguns centros previamente configurados; não incorporar automaticamente um serviço genérico público de geocodificação na plataforma de vibe coding.
- A UI pode exibir resultados em lista e mapa 2D. O vendedor não precisa copiar empresas de outro serviço para iniciar a prospecção.

- [ ] Antes de construir o restante, executar uma busca real pequena por região/categoria e conferir empresas únicas, nome, coordenadas, site e contato. Não declarar a fonte validada se ocorrer falha de transporte/timeout; falta de WhatsApp não vira contato inventado.
- [ ] Escrever testes: consulta válida cria trabalho e resultados persistidos; pesquisa repetida usa cache; reinício não duplica empresa com o mesmo identificador de fonte; categoria inválida e área excessiva são rejeitadas; timeout/erro do provedor gera fonte indisponível, não zero empresas.
- [ ] Escrever teste de resposta com `remark`/resultado parcial e registrar a condição como incompleta, sem afirmar cobertura integral da área.
- [ ] Executar testes e observar falhas iniciais.
- [ ] Implementar conector e trabalho persistente, limites/configuração de provedor, normalização conservadora e mapa 2D com atribuição. Não realizar varredura nacional ou consulta externa a cada movimento do mapa.
- [ ] Executar os testes e repetir a verificação real somente se uma mudança ou falha justificar; registrar qualidade/limites dos contatos. Sem fonte viável, não substituir a entrega automática por entrada manual sem voltar ao usuário.
- [ ] Fazer commit da busca automática funcional e seus testes.

## Task 3: Reserva, feedback e histórico

**Files:**
- Create: `carteira/ReservaService.java`, `carteira/FeedbackService.java`, `carteira/DiasUteis.java`, `carteira/CarteiraController.java`.
- Create: `auditoria/AuditoriaService.java`, `db/migration/V3__carteira_auditoria.sql`.
- Test: `ReservaConcorrenteIntegrationTest.java`, `PrazoReservaTest.java`, `FeedbackIntegrationTest.java`.

**Interfaces:**
- Consumes: identificadores de conta e empresa da Task 1.
- Produces: `ReservaService.assumir(UUID empresaId, UUID vendedorId): ReservaResultado`.
- Produces: `ReservaService.exigirResponsavel(UUID empresaId, UUID vendedorId): void`.
- Produces: `FeedbackService.registrar(UUID empresaId, UUID vendedorId, FeedbackEntrada entrada): void`.
- Produces: `AuditoriaService.registrar(UUID atorId, UUID empresaId, Acao acao, Instant instante): void`, na mesma transação de alterações comerciais.
- `FeedbackEntrada`: situação, comentário e próximo retorno opcional. Dias úteis: segunda a sexta; fuso America/Sao_Paulo, sem feriados no piloto, conforme proposta da especificação.

- [ ] Escrever teste concorrente com dois vendedores e a mesma empresa; afirmar exatamente uma reserva e negar a ação comercial ao outro.
- [ ] Escrever testes com relógio fixo: sexta-feira mais três dias úteis; retorno agendado mais um dia útil; visualizar/copiar não renova; vendedor alheio não altera; ganho/não contatar saem da fila; histórico permanece após liberação.
- [ ] Executar os testes e registrar as falhas iniciais.
- [ ] Implementar reserva atômica no PostgreSQL, expiração, feedback e histórico. Ações puramente de leitura não alteram o prazo.
- [ ] Rodar os testes pertinentes e confirmar rollback sem reserva/auditoria parcial.
- [ ] Fazer commit da carteira operacional.

## Task 4: Mensagem personalizada e análise básica verificável

**Files:**
- Create: `mensagens/MensagemService.java`, `mensagens/ContatoController.java`.
- Create: `analise/AnaliseService.java`, `analise/AnaliseWorker.java`, `analise/LeitorSitePublico.java`, `analise/PoliticaUrlPublica.java`.
- Create: `db/migration/V4__tarefas_analise.sql`.
- Test: `MensagemServiceTest.java`, `ContatoIntegrationTest.java`, `PoliticaUrlPublicaTest.java`, `AnaliseWorkerIntegrationTest.java`.

**Interfaces:**
- Consumes: empresa, responsável, reserva válida e `AuditoriaService` das Tasks 1–3.
- Produces: `MensagemService.preparar(UUID empresaId, UUID vendedorId): MensagemPreparada`, com texto editável e contexto comprovado.
- Produces: `AnaliseService.solicitar(UUID empresaId, UUID vendedorId): UUID` e `AnaliseService.obter(UUID tarefaId): AnaliseResultado`.
- `AnaliseResultado`: estado pendente/em execução/concluído/inconclusivo, URL/fonte/data, sinais observáveis e razões de prioridade; não é nota estética nem probabilidade de compra.
- A V0 observa acesso ao site, links de contato/WhatsApp publicados e apresentação básica de oferta/contato. Não atribui nota de desempenho ou responsividade a partir de HTML insuficiente.
- O comando de abrir WhatsApp exige/obtém reserva, registra “clique para abrir WhatsApp” e devolve o link com mensagem preenchida. Número não confirmado deve ser apresentado como tal.

- [ ] Escrever testes: nome desconhecido usa saudação à empresa; URL e texto são codificados; não inventa diagnóstico; vendedor concorrente não obtém ação comercial; evento não se chama mensagem enviada.
- [ ] Escrever testes para bloquear localhost, IP privado/IPv6 local e redirecionamento a destino interno; limitar corpo/tempo; timeout gera inconclusivo; repetição/reinício de tarefa não duplica os efeitos.
- [ ] Executar testes e observar falhas iniciais.
- [ ] Implementar templates e tarefas persistentes com tentativas limitadas. Usar cliente HTTP com resolução/destino validados; se não houver proteção suficiente contra acesso interno, manter a análise externa desabilitada, sem fingir que foi concluída.
- [ ] Executar os testes de mensagens, autorização, rede e recuperação de tarefa.
- [ ] Fazer commit do fluxo de preparação de contato e análise limitada.

## Task 5: Interface responsiva, execução e piloto

**Files:**
- Create: `templates/login.html`, `templates/empresas.html`, `templates/empresa.html`, `templates/carteira.html`.
- Create: `static/css/app.css`, `static/js/app.js`, `compose.yaml`, `.env.example`, `README.md`.
- Test: `FluxoVendedorIntegrationTest.java` e roteiro de verificação de navegador em `docs/piloto-operacional-checklist.md`.

**Interfaces:**
- Consumes: serviços das Tasks 1–4; formulários protegidos por autenticação/CSRF e respostas de validação explícitas.
- Produces: fluxo login → cidade/região e categoria → busca automática → lista/mapa → ficha → assumir/copiar/abrir WhatsApp → feedback → carteira/retorno.

- [ ] Escrever teste HTTP do fluxo com dois vendedores, isolamento da reserva e renderização escapada. A interface não deve ter botão visível que a API permita executar sem autorização.
- [ ] Implementar busca automática, lista/mapa 2D, filtros, ficha e carteira em português, com estados vazios e de erro claros. Exibir a diferença entre sem site informado, não localizado e falha ao consultar. O caminho principal não exige cadastro manual de empresa.
- [ ] Criar execução reproduzível; a máquina atual tem Docker, Node e Python disponíveis no PATH, mas Java/Maven não foram encontrados. Preferir build Java por Docker ou instalar runtime local em etapa autorizada da execução; não pressupor ambiente pronto.
- [ ] Rodar testes e verificar no navegador em larguras de celular, tablet e desktop. Conferir dados persistidos, mensagens de conflito e histórico, além da aparência.
- [ ] Reiniciar aplicação/banco e confirmar preservação das oportunidades e recuperação das tarefas. Verificar recuperação de backup em ambiente de teste.
- [ ] Preparar hospedagem gratuita disponível e adequada, sem criar contas/planos pagos automaticamente. Até existir acesso externo autenticado validado, identificar a entrega como piloto local, sem afirmar que os cinco vendedores já conseguem acessar de qualquer lugar.
- [ ] Fazer commit e entregar instruções reais de acesso, limites e configuração, sem senhas no repositório.

## Execução e revisão

Proposta: execução direta nesta sessão, com validação de cada tarefa e revisão final. Não iniciar o produto completo em paralelo. A descoberta automática está confirmada como essencial pelo usuário e permanece na primeira liberação. Antes da execução, revisar o recorte e este plano. Nenhum código de produto foi criado neste documento.

Autorrevisão: o plano cobre apenas o recorte declarado, incluindo descoberta automática e mapa 2D, sem representar os modos avançados da especificação como entregues. As cinco condições de Review Focus possuem testes/checagens nas Tasks 1–5. Nomes de serviços consumidos foram definidos nas Interfaces anteriores. Uma conta gratuita e o alcance dos dados continuam validações práticas, sem cobrança autorizada ou prazo garantido.

## Evidência preliminar — 05/11/2026

- Consulta exploratória de padarias e cafés em raio de 1,5 km ao redor de um ponto de Pinheiros/São Paulo: `[out:json][timeout:20];(nwr(around:1500,-23.5660,-46.6930)["shop"="bakery"];nwr(around:1500,-23.5660,-46.6930)["amenity"="cafe"];);out center tags;`.
- A instância `https://overpass-api.de/api/interpreter` devolveu HTTP 502. A consulta posterior em `https://overpass.private.coffee/api/interpreter` excedeu o tempo de espera local de 30 segundos. Não foi possível medir cobertura ou qualidade de contatos. Essas falhas não demonstram que não existem empresas na região e não validam a fonte para a liberação do piloto.
- [O operador Private.coffee](https://overpass.private.coffee/) documenta acesso para projetos comerciais, com uso responsável e sem garantia de disponibilidade. A documentação por si só não substitui um teste bem-sucedido da fonte.
- Java/Maven não foram encontrados no PATH. Docker está instalado, mas o daemon Linux não respondeu porque o pipe não existe. O ambiente de compilação/execução precisa ser preparado; nenhum servidor foi iniciado e nenhum teste de produto foi executado nesta etapa.
