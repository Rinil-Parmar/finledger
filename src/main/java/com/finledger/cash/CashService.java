package com.finledger.cash;

import com.finledger.account.Account;
import com.finledger.account.AccountService;
import com.finledger.account.AccountStatus;
import com.finledger.common.BadRequestException;
import com.finledger.ledger.EntryDirection;
import com.finledger.ledger.JournalEntry;
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
 * Cash movements into and out of the pooled bank account.
 *
 * A deposit debits CASH (pool grows) and credits the customer wallet (we owe more).
 * A withdrawal is the mirror: debit the wallet (we owe less) and credit CASH (pool
 * shrinks) -- but only if the customer actually has the funds.
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

    @Transactional
    public CashMovementResult deposit(String externalId, BigDecimal amount, String currency) {
        Account account = accountService.requireAccount(externalId);
        validateAmount(account, amount, currency);

        LedgerAccount cash = requireCash();
        LedgerAccount wallet = accountService.requireWallet(externalId);

        JournalEntry journal = ledgerService.post(
                JournalType.DEPOSIT,
                "Deposit to " + externalId,
                List.of(
                        new PostingLine(cash.getId(), EntryDirection.DEBIT, amount, currency),
                        new PostingLine(wallet.getId(), EntryDirection.CREDIT, amount, currency)));

        return toResult(journal, externalId, amount, wallet);
    }

    @Transactional
    public CashMovementResult withdraw(String externalId, BigDecimal amount, String currency) {
        Account account = accountService.requireAccount(externalId);
        validateAmount(account, amount, currency);

        LedgerAccount cash = requireCash();
        LedgerAccount wallet = accountService.requireWallet(externalId);

        // You cannot take out more than you have. Check first; if it fails, nothing is posted.
        BigDecimal balance = ledgerService.creditPositiveBalance(wallet.getId());
        if (amount.compareTo(balance) > 0) {
            throw new BadRequestException(
                    "Insufficient funds: balance is " + balance + " but " + amount + " was requested");
        }

        JournalEntry journal = ledgerService.post(
                JournalType.WITHDRAWAL,
                "Withdrawal from " + externalId,
                List.of(
                        new PostingLine(wallet.getId(), EntryDirection.DEBIT, amount, currency),
                        new PostingLine(cash.getId(), EntryDirection.CREDIT, amount, currency)));

        return toResult(journal, externalId, amount, wallet);
    }

    private CashMovementResult toResult(JournalEntry journal, String externalId, BigDecimal amount,
                                        LedgerAccount wallet) {
        BigDecimal newBalance = ledgerService.creditPositiveBalance(wallet.getId());
        return new CashMovementResult(journal.getReference(), externalId, amount, newBalance);
    }

    private LedgerAccount requireCash() {
        return ledgerAccounts.findByCode(CASH_CODE)
                .orElseThrow(() -> new IllegalStateException("CASH ledger account is missing"));
    }

    private void validateAmount(Account account, BigDecimal amount, String currency) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Account is not active: " + account.getExternalId());
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be positive");
        }
        if (amount.scale() > 4) {
            throw new BadRequestException("Amount supports at most 4 decimal places");
        }
        if (!account.getCurrency().equals(currency)) {
            throw new BadRequestException(
                    "Currency " + currency + " does not match account currency " + account.getCurrency());
        }
    }

    public record CashMovementResult(String journalReference, String accountExternalId,
                                     BigDecimal amount, BigDecimal newBalance) {
    }
}
