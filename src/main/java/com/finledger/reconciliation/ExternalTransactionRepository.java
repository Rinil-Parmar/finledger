package com.finledger.reconciliation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalTransactionRepository extends JpaRepository<ExternalTransaction, UUID> {
}
