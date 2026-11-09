package br.com.wezttech.prospectgt.carteira;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
  List<Feedback> findTop50ByLeadIdOrderByCreatedAtDesc(UUID lead);
}
