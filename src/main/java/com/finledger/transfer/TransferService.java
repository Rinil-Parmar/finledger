package com.finledger.transfer;

import com.finledger.account.Account;
import com.finledger.account.AccountService;
import com.finledger.account.AccountStatus;
import com.finledger.common.BadRequestException;
import com.finledger.ledger.EntryDirection;
import com.finledger.ledger.JournalEntry;
import com.finledger.ledger.JournalType;
import com.finledger.ledger.LedgerAccount;
import com.finledger.ledger.LedgerService;
import com.finledger.ledger.PostingLine;
import com.finledger.outbox.OutboxService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves money between two customer accounts. Both wallets are liabilities, so the
 * pooled CASH does not move: we debit the source wallet (owe them less) and credit the
 * destination wallet (owe them more) in one balanced journal.
 */
@Service
public class TransferService {

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final TransferRepository transfers;
    private final OutboxService outbox;

    public TransferService(AccountService accountService, LedgerService ledgerService,
                           TransferRepository transfers, OutboxService outbox) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.transfers = transfers;
        this.outbox = outbox;
    }

    @Transactional
    public TransferResult transfer(String sourceExternalId, String destExternalId,
                                   BigDecimal amount, String currency) {
        if (sourceExternalId != null && sourceExternalId.equals(destExternalId)) {
            throw new BadRequestException("Source and destination accounts must be different");
        }

        // Lock both accounts before checking the balance, so concurrent transfers on the
        // same account are serialized and can never overdraw it.
        accountService.lockAccountsInOrder(sourceExternalId, destExternalId);

        Account source = accountService.requireAccount(sourceExternalId);
        Account dest = accountService.requireAccount(destExternalId);
        validate(source, dest, amount, currency);

        LedgerAccount sourceWallet = accountService.requireWallet(sourceExternalId);
        LedgerAccount destWallet = accountService.requireWallet(destExternalId);

        BigDecimal sourceBalance = ledgerService.creditPositiveBalance(sourceWallet.getId());
        if (amount.compareTo(sourceBalance) > 0) {
            throw new BadRequestException(
                    "Insufficient funds: balance is " + sourceBalance + " but " + amount + " was requested");
        }

        JournalEntry journal = ledgerService.post(
                JournalType.TRANSFER,
                "Transfer from " + sourceExternalId + " to " + destExternalId,
                List.of(
                        new PostingLine(sourceWallet.getId(), EntryDirection.DEBIT, amount, currency),
                        new PostingLine(destWallet.getId(), EntryDirection.CREDIT, amount, currency)));

        Transfer transfer = transfers.save(new Transfer(
                newReference(), source.getId(), dest.getId(), amount, currency,
                TransferStatus.COMPLETED, journal.getId()));

        // Same transaction as the money movement: the event can never be lost or orphaned.
        outbox.append("transfer", transfer.getReference(), "transfer.completed", Map.of(
                "reference", transfer.getReference(), "source", sourceExternalId,
                "destination", destExternalId, "amount", amount, "currency", currency));

        return new TransferResult(
                transfer.getReference(), sourceExternalId, destExternalId, amount,
                ledgerService.creditPositiveBalance(sourceWallet.getId()),
                ledgerService.creditPositiveBalance(destWallet.getId()));
    }

    private void validate(Account source, Account dest, BigDecimal amount, String currency) {
        if (source.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Source account is not active: " + source.getExternalId());
        }
        if (dest.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Destination account is not active: " + dest.getExternalId());
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be positive");
        }
        if (amount.scale() > 4) {
            throw new BadRequestException("Amount supports at most 4 decimal places");
        }
        if (!source.getCurrency().equals(currency) || !dest.getCurrency().equals(currency)) {
            throw new BadRequestException("Currency must match both accounts (" + currency + ")");
        }
    }

    private static String newReference() {
        return "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public record TransferResult(String reference, String sourceAccountId, String destinationAccountId,
                                 BigDecimal amount, BigDecimal sourceBalance, BigDecimal destinationBalance) {
    }
}
