# Minas Gerais e publicação do Prospectgt

**Pedido autorizado:** ampliar os locais, priorizando Betim, Contagem e Belo Horizonte, e publicar o aplicativo. Continuação do MVP existente, mantendo Java/Spring, as contas e a carteira. Execução direta nesta sessão.

**Arquitetura:** catálogo público gerado dos extratos Geofabrik Centro-Oeste e Sudeste, combinado por identificador OSM com cobertura geográfica explícita; cidades disponibilizadas pela configuração da API. A publicação mantém o backend e usa PostgreSQL externo em vez de disco efêmero para a carteira. Nenhum recurso pago foi autorizado.

## 1. Dados e cidades

- Criar configuração única `src/main/resources/catalog-regions.json` com fontes oficiais e cidades; Betim, Contagem e BH aparecem primeiro.
- Generalizar `scripts/download-data.py` e `scripts/import-catalog.py` para preparar os dois extratos, com download parcial/validação e arquivos substituídos somente após sucesso. Preservar catálogo anterior se houver falha.
- Corrigir metadados do importador para identificar a fonte regional processada. Combinar JSONL, polígonos e contagens sem duplicar o mesmo identificador OSM.
- Expor cidades no `/api/config` e consumir a mesma configuração na interface; conservar seleção e ajuste no mapa.
- Verificar busca real de estabelecimentos nas três cidades e conservação da cobertura Centro-Oeste. Não presumir contatos ausentes.

## 2. Pacote e publicação

- Produzir pacote comprimido contendo apenas o catálogo público, metadados e cobertura. Credenciais, banco local e backups ficam fora do pacote e do Git.
- Preparar a imagem Docker com catálogo somente leitura, bind configurável, cookie seguro sob HTTPS e PostgreSQL externo.
- Descobrir conexão de hospedagem disponível e plano gratuito. Render ainda não conectado no início; sugestão de conexão enviada. Vercel está conectado, mas não presumir plano comercial autorizado. Sites executa Workers JS, portanto não executa este backend Java diretamente.
- Publicar somente o aplicativo funcional com banco durável, sem contratar plano pago. Se a conexão faltar, concluir e testar tudo que for independente dela e informar precisamente a ação restante. Túnel temporário não equivale a hospedagem em nuvem permanente.

## 3. Verificação e entrega

- Testar configuração e cobertura combinada, deduplicação por chave OSM e falha durante preparação.
- Executar testes pertinentes de Java e a busca real pelo navegador em Minas; conferir carteira e mensagens sem enviar abordagem.
- Documentar novas contagens, limites e instruções de execução/publicação. Verificar o endereço público e login antes de declarar publicação concluída.
- Revisão final independente das mudanças, seguida de commit local e entrega dos resultados reais.

## Situação verificada em 10/11/2026

- Implementação dos dados e cidades concluída: catálogo combinado de 110.255 registros e 25 atalhos, com Betim como seleção inicial.
- Passaram 48 testes Java, quatro testes Python, buscas reais nas três cidades e nove verificações completas do vendedor em Betim.
- Pacote Docker preparado, catálogo público empacotado e configuração Compose validada; imagem ainda não executada neste ambiente.
- Revisão independente concluída; corrigidos TLS cloud, bootstrap em disco efêmero e publicação atômica do catálogo.
- Workspace Render confirmado pelo usuário: Matheus's workspace. Banco gratuito ainda precisa ser escolhido/conectado. Não houve migração nem criação de serviço; publicação e verificação HTTPS permanecem pendentes.
