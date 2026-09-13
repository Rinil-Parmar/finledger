package com.finledger.account;

import com.finledger.common.NotFoundException;
import com.finledger.ledger.EntryDirection;
import com.finledger.ledger.LedgerAccount;
import com.finledger.ledger.LedgerAccountRepository;
import com.finledger.ledger.LedgerAccountType;
import com.finledger.ledger.LedgerService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerAccountRepository ledgerAccounts;
    private final LedgerService ledgerService;

    public AccountService(AccountRepository accounts, LedgerAccountRepository ledgerAccounts,
                          LedgerService ledgerService) {
        this.accounts = accounts;
        this.ledgerAccounts = ledgerAccounts;
        this.ledgerService = ledgerService;
    }

    /**
     * Creates a customer account together with its paired CUSTOMER_CASH ledger account.
     * That ledger account is a LIABILITY (money we owe the customer), so it increases on
     * the credit side.
     */
    @Transactional
    public Account createAccount(String ownerName, String currency) {
        String externalId = "acc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Account account = accounts.save(new Account(externalId, ownerName, currency));
        ledgerAccounts.save(new LedgerAccount(
                walletCode(externalId),
                "Customer cash for " + externalId,
                LedgerAccountType.LIABILITY,
                EntryDirection.CREDIT));
        return account;
    }

    @Transactional(readOnly = true)
    public Account requireAccount(String externalId) {
        return accounts.findByExternalId(externalId)
                .orElseThrow(() -> new NotFoundException("Account not found: " + externalId));
    }

    /** The customer's spendable balance, derived from their wallet's ledger entries. */
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(Account account) {
        LedgerAccount wallet = requireWallet(account.getExternalId());
        return ledgerService.creditPositiveBalance(wallet.getId());
    }

    public LedgerAccount requireWallet(String externalId) {
        return ledgerAccounts.findByCode(walletCode(externalId))
                .orElseThrow(() -> new NotFoundException("Wallet ledger account missing for " + externalId));
    }

    /** The chart-of-accounts code for a customer's wallet, e.g. CUSTOMER_CASH:acc_123. */
    public static String walletCode(String externalId) {
        return "CUSTOMER_CASH:" + externalId;
    }
}
