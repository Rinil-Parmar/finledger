package com.finledger.account;

import com.finledger.cash.CashService;
import com.finledger.ledger.LedgerLineView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final CashService cashService;

    public AccountController(AccountService accountService, CashService cashService) {
        this.accountService = accountService;
        this.cashService = cashService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(request.ownerName(), request.currency());
        return AccountResponse.from(account);
    }

    @GetMapping
    public List<AccountSummaryResponse> list() {
        return accountService.listAccountSummaries().stream().map(AccountSummaryResponse::from).toList();
    }

    @GetMapping("/{externalId}/entries")
    public List<LedgerLineResponse> entries(@PathVariable String externalId) {
        accountService.requireAccount(externalId);   // 404 if the account does not exist
        return accountService.getLedgerLines(externalId).stream().map(LedgerLineResponse::from).toList();
    }

    @GetMapping("/{externalId}/balance")
    public BalanceResponse balance(@PathVariable String externalId) {
        Account account = accountService.requireAccount(externalId);
        BigDecimal balance = accountService.balanceOf(account);
        return new BalanceResponse(account.getExternalId(), balance, account.getCurrency());
    }

    @PostMapping("/{externalId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse deposit(@PathVariable String externalId, @Valid @RequestBody MoneyRequest request) {
        return MovementResponse.from(cashService.deposit(externalId, request.amount(), request.currency()));
    }

    @PostMapping("/{externalId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse withdraw(@PathVariable String externalId, @Valid @RequestBody MoneyRequest request) {
        return MovementResponse.from(cashService.withdraw(externalId, request.amount(), request.currency()));
    }

    // --- request / response bodies ---

    public record CreateAccountRequest(
            @NotBlank(message = "ownerName is required") String ownerName,
            @NotBlank(message = "currency is required") String currency) {
    }

    public record AccountResponse(String externalId, String ownerName, String currency, String status) {
        static AccountResponse from(Account a) {
            return new AccountResponse(a.getExternalId(), a.getOwnerName(), a.getCurrency(), a.getStatus().name());
        }
    }

    /** Shared body for deposits and withdrawals. */
    public record MoneyRequest(
            @NotNull(message = "amount is required")
            @Positive(message = "amount must be positive") BigDecimal amount,
            @NotBlank(message = "currency is required") String currency) {
    }

    public record MovementResponse(String journalReference, String externalId,
                                   BigDecimal amount, BigDecimal newBalance) {
        static MovementResponse from(CashService.CashMovementResult r) {
            return new MovementResponse(r.journalReference(), r.accountExternalId(), r.amount(), r.newBalance());
        }
    }

    public record BalanceResponse(String externalId, BigDecimal balance, String currency) {
    }

    public record AccountSummaryResponse(String externalId, String ownerName, String currency,
                                         String status, BigDecimal balance) {
        static AccountSummaryResponse from(AccountService.AccountSummary s) {
            return new AccountSummaryResponse(s.externalId(), s.ownerName(), s.currency(), s.status(), s.balance());
        }
    }

    public record LedgerLineResponse(String reference, String direction, BigDecimal amount, Instant postedAt) {
        static LedgerLineResponse from(LedgerLineView v) {
            return new LedgerLineResponse(v.getReference(), v.getDirection(), v.getAmount(), v.getPostedAt());
        }
    }
}
