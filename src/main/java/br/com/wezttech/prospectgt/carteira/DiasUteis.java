package br.com.wezttech.prospectgt.carteira;

import java.time.*;

public final class DiasUteis {

  public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

  private DiasUteis() {}

  public static Instant somar(Instant base, int dias) {
    var d = base.atZone(ZONE);
    for (int i = 0; i < dias; ) {
      d = d.plusDays(1);
      if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) i++;
    }
    return d.toInstant();
  }

  public static Instant depoisDoRetorno(LocalDate date) {
    return somar(date.atTime(23, 59).atZone(ZONE).toInstant(), 1);
  }
}
