# Verificação do MVP — 09/11/2026

- **42 testes Java aprovados:** 15 de fluxo em H2, 17 em PostgreSQL 14.22 real temporário, 7 de regras e 3 de leitura/rede. Build `python scripts/maven.py -q package` concluído. O limite de leitura de sites foi ajustado para 2 MB e seus testes de rede repetidos com sucesso; o JAR foi empacotado novamente.
- **9 verificações pelo navegador:** login, busca real e pontos no mapa, ficha/análise/mensagem, cópia do texto personalizado, conflito entre vendedores, persistência de feedback, isolamento da carteira durante busca lenta, layouts de computador/tablet/celular e liberação pelo gestor sem apagar histórico.
- Busca por cafés em Brasília com raio de 3 km encontrou **49 registros reais**. A oportunidade usada no teste voltou a ficar disponível. Não houve envio de mensagem pelo WhatsApp.
- Auditorias reais dos sites publicados de Los Baristas e Daniel Briand concluíram com HTTP 200; um deles apresentou link de WhatsApp no HTML lido. Evidência limitada ao conteúdo consultado, sem confirmação de conta ativa ou qualidade comercial do contato.
- Cópia do banco H2 feita com a aplicação parada e reaberta em um arquivo independente: empresas, seis contas e feedback preservados. O reinício da aplicação manteve os registros.
- Revisão independente verificou autorização, proteção de estados encerrados, cache, URLs externas, origem de contatos, rascunhos e troca de telas. Correções aplicadas; ataque de injeção de atributo repetido pelo revisor e bloqueado.
- `docker compose config --quiet` aprovado. Contêineres não executados neste ambiente; nuvem e acesso remoto não configurados.

`java-tests.json`, `browser-check.json`, `site-audit-check.json` e `backup-check.json` registram as verificações. As capturas `mvp-desktop.png` e `mvp-celular.png` mostram o fluxo com dados reais.
