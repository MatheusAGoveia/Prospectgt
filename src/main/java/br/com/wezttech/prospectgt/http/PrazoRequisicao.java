package br.com.wezttech.prospectgt.http;

import java.util.concurrent.*;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

/** Deadline independente do progresso de leitura dos cabeçalhos e corpo. */
public final class PrazoRequisicao implements AutoCloseable {

  private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
    r -> {
      var t = new Thread(r, "prospectgt-http-deadline");
      t.setDaemon(true);
      return t;
    }
  );
  private final ScheduledFuture<?> cancellation;

  public PrazoRequisicao(HttpUriRequestBase request, long deadlineNanos) {
    cancellation = TIMER.schedule(
      request::cancel,
      Math.max(0, deadlineNanos - System.nanoTime()),
      TimeUnit.NANOSECONDS
    );
  }

  public void close() {
    cancellation.cancel(false);
  }
}
