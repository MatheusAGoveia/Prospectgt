package br.com.wezttech.prospectgt.trabalho;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface TarefaRepository extends JpaRepository<Tarefa, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
    "select t from Tarefa t where t.status='PENDING' order by case when t.kind='SEARCH' then 0 else 1 end, t.createdAt"
  )
  List<Tarefa> pendentes(org.springframework.data.domain.Pageable page);

  List<Tarefa> findByStatus(String status);
  Optional<Tarefa> findFirstByCacheKeyAndStatusOrderByCreatedAtDesc(String key, String status);
}
