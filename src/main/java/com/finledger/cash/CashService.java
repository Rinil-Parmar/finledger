package com.finledger.cash;

import com.finledger.account.Account;
import com.finledger.account.AccountService;
import com.finledger.account.AccountStatus;
import com.finledger.common.BadRequestException;
import com.finledger.ledger.EntryDirection;
import com.finledger.ledger.JournalType;
import com.finledger.ledger.LedgerAccount;
import com.finledger.ledger.LedgerAccountRepository;
import com.finledger.ledger.LedgerService;
import com.finledger.ledger.PostingLine;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash movements into and out of the pooled bank account. Phase 1 supports deposits.
 */
@Service
public class CashService {

    private static final String CASH_CODE = "CASH";

    private final AccountService accountService;
    private final LedgerAccountRepository ledgerAccounts;
    private final LedgerService ledgerService;

    public CashService(AccountService accountService, LedgerAccountRepository ledgerAccounts,
                       LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerAccounts = ledgerAccounts;
        this.ledgerService = ledgerService;
    }

    /**
     * Records a deposit: cash enters the pooled bank account (debit CASH) and we now owe
     * that money to the customer (credit their wallet). Posted as one balanced journal.
     */
    @Transactional
    public DepositResult deposit(String externalId, BigDecimal amount, String currency) {
        Account account = accountService.requireAccount(externalId);
        validate(account, amount, currency);

        LedgerAccount cash = ledgerAccounts.findByCode(CASH_CODE)
                .orElseThrow(() -> new IllegalStateException("CASH ledger account is missing"));
        LedgerAccount wallet = accountService.requireWallet(externalId);

        var journal = ledgerService.post(
                JournalType.DEPOSIT,
                "Deposit to " + externalId,
                List.of(
                        new PostingLine(cash.getId(), EntryDirection.DEBIT, amount, currency),
                        new PostingLine(wallet.getId(), EntryDirection.CREDIT, amount, currency)));

        BigDecimal newBalance = ledgerService.creditPositiveBalance(wallet.getId());
        return new DepositResult(journal.getReference(), externalId, amount, newBalance);
    }

    private void validate(Account account, BigDecimal amount, String currency) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Account is not active: " + account.getExternalId());
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Deposit amount must be positive");
        }
        if (amount.scale() > 4) {
            throw new BadRequestException("Amount supports at most 4 decimal places");
        }
        if (!account.getCurrency().equals(currency)) {
            throw new BadRequestException(
                    "Currency " + currency + " does not match account currency " + account.getCurrency());
        }
    }

    public record DepositResult(String journalReference, String accountExternalId,
                                BigDecimal amount, BigDecimal newBalance) {
    }
}
