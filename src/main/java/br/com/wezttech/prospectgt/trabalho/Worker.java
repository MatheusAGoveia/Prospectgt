package br.com.wezttech.prospectgt.trabalho;

import br.com.wezttech.prospectgt.analise.AnaliseService;
import br.com.wezttech.prospectgt.carteira.ReservaService;
import br.com.wezttech.prospectgt.descoberta.BuscaService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
  name = "prospectgt.worker-enabled",
  havingValue = "true",
  matchIfMissing = true
)
public class Worker {

  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Worker.class);

  private final TarefaService jobs;
  private final BuscaService search;
  private final AnaliseService analyses;
  private final ReservaService reservas;

  public Worker(TarefaService j, BuscaService b, AnaliseService a, ReservaService r) {
    jobs = j;
    search = b;
    analyses = a;
    reservas = r;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    jobs.recuperar();
  }

  @Scheduled(fixedDelay = 1000, initialDelay = 3000)
  public void execute() {
    var job = jobs.proxima();
    if (job == null) return;
    try {
      jobs.concluir(
        job.id,
        switch (job.kind) {
          case "SEARCH" -> search.executar(job);
          case "ANALYZE" -> analyses.executar(job);
          default -> throw new IllegalStateException("Tipo de tarefa desconhecido.");
        }
      );
    } catch (Exception e) {
      LOG.warn("Falha ao executar tarefa {} ({})", job.id, job.kind, e);
      jobs.falhar(
        job.id,
        job.kind.equals("SEARCH")
          ? "A fonte de empresas está indisponível ou a região não está preparada. Tente novamente ou escolha uma região com catálogo local."
          : e.getMessage()
      );
    }
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void expire() {
    reservas.expirarTodas();
  }
}
