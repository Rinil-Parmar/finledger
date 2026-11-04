package com.finledger.reconciliation;

import com.finledger.ledger.EntryDirection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationBreakRepository breaks;

    public ReconciliationController(ReconciliationService reconciliationService,
                                    ReconciliationBreakRepository breaks) {
        this.reconciliationService = reconciliationService;
        this.breaks = breaks;
    }

    /** Simulate the external institution reporting a settlement. */
    @PostMapping("/external")
    @ResponseStatus(HttpStatus.CREATED)
    public ExternalResponse recordExternal(@Valid @RequestBody ExternalRequest request) {
        ExternalTransaction e = reconciliationService.recordExternal(
                request.reference(), request.direction(), request.amount(), request.currency());
        return new ExternalResponse(e.getId().toString(), e.getReference(),
                e.getDirection().name(), e.getAmount(), e.getCurrency());
    }

    /** Run a reconciliation and return its summary. */
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public RunResponse run() {
        ReconciliationRun run = reconciliationService.run();
        return new RunResponse(run.getId().toString(), run.getStatus().name(),
                run.getTotalMatched(), run.getTotalBreaks());
    }

    /** List the breaks found by a run. */
    @GetMapping("/runs/{runId}/breaks")
    public List<BreakResponse> breaks(@PathVariable String runId) {
        return breaks.findByRunId(UUID.fromString(runId)).stream()
                .map(BreakResponse::from)
                .toList();
    }

    public record ExternalRequest(
            @NotBlank(message = "reference is required") String reference,
            @NotNull(message = "direction is required") EntryDirection direction,
            @NotNull(message = "amount is required")
            @Positive(message = "amount must be positive") BigDecimal amount,
            @NotBlank(message = "currency is required") String currency) {
    }

    public record ExternalResponse(String id, String reference, String direction,
                                   BigDecimal amount, String currency) {
    }

    public record RunResponse(String runId, String status, int totalMatched, int totalBreaks) {
    }

    public record BreakResponse(String id, String breakType, String reference,
                                BigDecimal internalAmount, BigDecimal externalAmount, String detail) {
        static BreakResponse from(ReconciliationBreak b) {
            return new BreakResponse(b.getId().toString(), b.getBreakType().name(), b.getReference(),
                    b.getInternalAmount(), b.getExternalAmount(), b.getDetail());
        }
    }
}
