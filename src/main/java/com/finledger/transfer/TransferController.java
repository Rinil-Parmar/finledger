package com.finledger.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        TransferService.TransferResult r = transferService.transfer(
                request.sourceAccountId(), request.destinationAccountId(), request.amount(), request.currency());
        return new TransferResponse(
                r.reference(), r.sourceAccountId(), r.destinationAccountId(),
                r.amount(), r.sourceBalance(), r.destinationBalance());
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
