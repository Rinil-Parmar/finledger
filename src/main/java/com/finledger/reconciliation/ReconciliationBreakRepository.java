package com.finledger.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationBreakRepository extends JpaRepository<ReconciliationBreak, UUID> {

    List<ReconciliationBreak> findByRunId(UUID runId);
}
