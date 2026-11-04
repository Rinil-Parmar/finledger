package com.finledger.reversal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reversals")
public class ReversalController {

    private final ReversalService reversalService;

    public ReversalController(ReversalService reversalService) {
        this.reversalService = reversalService;
    }

    /** Reverse a journal by its reference (e.g. a deposit's jnl_… or a transfer's journal). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReversalResponse reverse(@Valid @RequestBody ReversalRequest request) {
        ReversalService.ReversalResult result = reversalService.reverse(request.reference());
        return new ReversalResponse(result.originalReference(), result.reversalReference());
    }

    public record ReversalRequest(
            @NotBlank(message = "reference is required") String reference) {
    }

    public record ReversalResponse(String originalReference, String reversalReference) {
    }
}
