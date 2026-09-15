package com.finledger.transfer;

import com.finledger.common.Hashing;
import com.finledger.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;

    public TransferController(TransferService transferService, IdempotencyService idempotencyService) {
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Creates a transfer. Requires an Idempotency-Key header so retries are safe: the
     * same key returns the original response instead of moving money again.
     */
    @PostMapping
    public ResponseEntity<String> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        String requestHash = Hashing.sha256(String.join("|",
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount().toPlainString(),
                request.currency()));

        IdempotencyService.StoredResponse response = idempotencyService.process(
                idempotencyKey, requestHash, HttpStatus.CREATED.value(),
                () -> {
                    TransferService.TransferResult r = transferService.transfer(
                            request.sourceAccountId(), request.destinationAccountId(),
                            request.amount(), request.currency());
                    return new TransferResponse(
                            r.reference(), r.sourceAccountId(), r.destinationAccountId(),
                            r.amount(), r.sourceBalance(), r.destinationBalance());
                });

        return ResponseEntity.status(response.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    public record TransferRequest(
            @NotBlank(message = "sourceAccountId is required") String sourceAccountId,
            @NotBlank(message = "destinationAccountId is required") String destinationAccountId,
            @NotNull(message = "amount is required")
            @Positive(message = "amount must be positive") BigDecimal amount,
            @NotBlank(message = "currency is required") String currency) {
    }

    public record TransferResponse(String reference, String sourceAccountId, String destinationAccountId,
                                   BigDecimal amount, BigDecimal sourceBalance, BigDecimal destinationBalance) {
    }
}
