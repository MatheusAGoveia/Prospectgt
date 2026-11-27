# Prospectgt — MVP WeztTech

Prospecção para vender landing pages e sites de vendas de R$ 500 a R$ 2.000. O vendedor busca empresas reais por ramo e região, consulta sinais básicos, assume uma oportunidade, personaliza a mensagem e abre o WhatsApp. O envio é manual.

## Executar no Windows

Pré-requisito: Python 3 e internet na primeira preparação. Java 21 e Maven são baixados para `.tools`, com verificação do arquivo. Não é preciso instalá-los globalmente.

```powershell
python scripts/run.py
```

Também é possível abrir `Iniciar-Prospectgt.cmd`. Acesse **http://127.0.0.1:8080**. O primeiro início gera `admin` e `vendedor1` até `vendedor5`, com senhas aleatórias individuais, em **`data/acessos-iniciais.txt`**. O arquivo não entra no Git. Altere a senha em Meu perfil e entregue cada acesso somente ao respectivo vendedor.

O catálogo desta máquina já está preparado. Em uma instalação nova, a preparação baixa aproximadamente 1 GB dos extratos OSM Centro-Oeste e Sudeste e gera o catálogo automaticamente; exige memória disponível para um processo Java com limite de 1,2 GB. Não há empresas fictícias pré-cadastradas. As seis contas são os únicos registros iniciais de demonstração.

O processo fica no terminal; `Ctrl+C` encerra. Carteira, histórico e tarefas permanecem em `data/prospectgt.mv.db`. Execute somente uma instância sobre esse arquivo H2.

## O que funciona

- Busca automática por dez ramos e raio de 1 a 10 km. Há 25 atalhos de cidades, com Betim, Contagem e Belo Horizonte primeiro e mais nove cidades mineiras. Arraste o marcador para pesquisar outras áreas.
- Catálogo regional OpenStreetMap/Geofabrik, lista e mapa 2D com cores por prioridade. Fora do recorte preparado, tenta a fonte Overpass online, sujeita a indisponibilidade. Falha de fonte é exibida como falha, sem resultados inventados.
- Análise automática inicial de até cinco sites por busca e análise sob demanda de qualquer ficha: acesso HTTP, declaração de viewport e links de contato/WhatsApp. Sites dependentes de JavaScript e erros ficam inconclusivos. Não há avaliação estética, Lighthouse ou IA generativa.
- Mensagem editável com nome da empresa, vendedor e contexto observado; link de WhatsApp com texto preenchido. Telefone publicado não equivale a WhatsApp ativo confirmado. Nome de pessoa e problemas não observados não são presumidos.
- Reserva transacional de três dias úteis, carteira individual, feedback, retorno e histórico compartilhado. Segunda a sexta no fuso de São Paulo, sem calendário de feriados. Visualizar ou copiar novamente não renova prazo. Retorno combinado reserva até o fim do dia útil seguinte à data informada. Ganho, sem interesse e não contatar saem da fila automática.
- Perfis com experiência profissional e sugestões de ramo, sem restrição de setor. Gestor consulta equipe e reabre oportunidades.
- Login, senhas com BCrypt, CSRF, autorização de gestor e proteção do analisador contra destinos privados e redirecionamentos internos.

Copiar/abrir registra a ação no Prospectgt; o aplicativo não confirma envio, resposta ou contrato pelo WhatsApp. A situação de venda é informada pelo vendedor. A nota de prioridade é uma regra explicável, não probabilidade de compra.

## Cobertura e custo

O catálogo preparado cobre **Centro-Oeste + Sudeste**, incluindo Minas Gerais, com **110.255 registros OSM**, dos quais **22.189 têm algum contato publicado** na preparação de 10/11/2026. Registros não equivalem a empresas únicas ou contatos válidos; cobertura, categoria e atualização variam. O limite do recorte é verificado pelos polígonos do fornecedor. A busca retorna até 150 registros por consulta.

O mapa usa tiles públicos OSM, que precisam de internet, atribuição e uso moderado; não há SLA ou hospedagem de mapas garantida. O catálogo de empresas funciona sem uma API paga. A execução local está disponível; o pacote de publicação usa Render gratuito e PostgreSQL externo. Publicação só pode ser declarada depois de conexão, criação e verificação real do endereço externo. Não existe promessa de infraestrutura gratuita ilimitada.

Mapa inclinado, Street View/fotos integrados, ERP/SaaS, IA generativa, comissão, importação CSV, convites e gestão completa de contas ficam para versões seguintes. Esta entrega usa Leaflet para o mapa 2D; MapLibre pode ser introduzido quando o modo inclinado entrar.

## Atualizar o catálogo

O download reutiliza os PBF existentes. Para uma nova edição, use `--refresh` nos comandos de download e importação. Para preparar ou recombinar os dados disponíveis:

```powershell
python scripts/download-data.py
python scripts/maven.py -q compile dependency:build-classpath -Dmdep.outputFile=.tools/classpath.txt
python scripts/import-catalog.py
```

Cada preparação cria um snapshot imutável com JSONL, metadados e cobertura. Um ponteiro é ativado atomicamente somente quando os três componentes estão prontos, com hashes verificados na inicialização e no empacotamento. Uma falha preserva a versão anterior. A versão do catálogo faz parte da chave de cache da busca. Nova busca atualiza dados públicos por identificador OSM sem apagar carteira e histórico. O catálogo não é uma varredura completa da presença digital da empresa.

## PostgreSQL e contêineres

O modo local usa H2 persistente para iniciar sem Docker. Há migração Flyway compatível com PostgreSQL e receita de execução com PostgreSQL 17:

Encerre o modo local antes de usar o Compose; ambos usam a porta 8080.

```powershell
python scripts/run.py --prepare-only
python scripts/package-catalog.py
Copy-Item .env.example .env
# Preencha POSTGRES_PASSWORD no .env antes do próximo comando.
docker compose up --build -d
```

Essa receita usa um banco diferente do H2 local; não transfere sua carteira automaticamente. Os acessos desse banco ficam em `data-postgres/acessos-iniciais.txt`, separados dos acessos H2. A imagem contém apenas um ZIP do catálogo público, extraído como arquivos somente para leitura, sem banco, senhas ou feedback. A porta permanece somente no computador local. O Compose é fornecido, mas não foi executado neste ambiente; as migrações e a cópia H2/PostgreSQL foram verificadas em PostgreSQL real no teste automatizado.

## Publicação em nuvem

O destino preparado é um único web service Docker no plano **Free do Render**, usando PostgreSQL gratuito separado. O workspace confirmado é **Matheus's workspace**. A conexão do banco e a publicação externa ainda estão pendentes. A opção recomendada é Neon; o PostgreSQL gratuito do Render serve apenas como piloto, pois expira após 30 dias. Os dados comerciais não podem ficar no disco efêmero da hospedagem. O perfil `cloud` exige PostgreSQL com TLS e verificação de certificado/hostname; não oferece fallback H2 e não cria contas com senhas guardadas em disco efêmero.

Variáveis do serviço: `SPRING_PROFILES_ACTIVE=cloud`, `DB_URL`, `DB_USER`, `DB_PASSWORD`. A URL JDBC deve incluir `sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`. Credenciais ficam nas variáveis protegidas do provedor. `PORT` é fornecida pelo Render. A imagem limita o heap a 256 MB para o plano de 512 MB.

Antes do primeiro deploy, encerre a aplicação local, faça backup e migre o banco local para um PostgreSQL vazio:

```powershell
python scripts/migrate-local-db.py
```

A ferramenta recebe a conexão por entrada oculta, preserva IDs, hashes de senha, reservas, feedback e tarefas, e recusa copiar sobre um destino com registros. Não publique a conexão ou arquivos de acesso. Depois da migração, os mesmos usuários e senhas continuam válidos na nuvem.

O Render gratuito pausa a aplicação após inatividade, e a retomada pode demorar cerca de um minuto; os dados ficam no PostgreSQL. Os limites de uso e de cobrança devem ser acompanhados na conta. Referências: [Render Free](https://render.com/docs/free) e [Neon Free](https://neon.com/docs/introduction/plans).

## Backup

No modo H2, encerre a aplicação e copie a pasta `data` inteira para um local privado antes de atualizar. Para restaurar, mantenha o servidor parado e recoloque a cópia; não misture bancos H2 e PostgreSQL. Em PostgreSQL, use `pg_dump` e teste a restauração. O arquivo de acessos contém senhas iniciais: não publique backups, `.env` ou `data`.

## Desenvolvimento e verificações

Monólito modular: `contas`, `descoberta`, `empresas`, `analise`, `carteira`, `mensagens`, `auditoria` e `trabalho`. Java 21, Spring Boot, Spring Security, JPA e Flyway. Trabalhos persistidos em banco e recuperados após reinício; uma execução em segundo plano por instância. Sem broker ou microserviços nesta versão.

```powershell
python scripts/maven.py -q test
# Navegador: aplicação já iniciada, catálogo preparado e contas iniciais disponíveis.
npm ci
$env:PLAYWRIGHT_BROWSERS_PATH = "$PWD/.tools/browsers"
npx playwright install chromium
npm run test:e2e
node scripts/browser-check-regions.cjs
python -m unittest discover -s scripts -p test_catalog_data.py
```

Os testes de integração repetem os fluxos HTTP, a disputa de reserva, autorização, cache e feedback em H2 e PostgreSQL temporário real; também verificam migrações, contatos antigos, prazos e leitura HTTP limitada. O teste de navegador usa o catálogo real local, não envia mensagens e libera a oportunidade ao final. A ficha guarda um feedback identificado como teste funcional. Evidências visuais e resultado ficam em `docs/evidencias`.

## Fontes e licenças

Dados: [OpenStreetMap, ODbL](https://www.openstreetmap.org/copyright), extratos [Geofabrik Brasil](https://download.geofabrik.de/south-america/brazil.html). Catálogo derivado deve manter atribuição e requisitos aplicáveis da ODbL. O app mostra fonte e data de obtenção; essa data não certifica que os dados estejam atuais.

Mapa: [Leaflet 1.9.4](https://github.com/Leaflet/Leaflet), licença BSD-2-Clause incluída em `static/assets/vendor/leaflet/LICENSE`. Importador: [Osmosis](https://github.com/openstreetmap/osmosis), LGPL-3.0. Links do mapa, fonte e WhatsApp abrem em serviços externos somente por ação do usuário.

Centros municipais novos: [Municípios Brasileiros](https://github.com/kelvins/municipios-brasileiros). A configuração de fontes e atalhos é única em `src/main/resources/catalog-regions.json`.
