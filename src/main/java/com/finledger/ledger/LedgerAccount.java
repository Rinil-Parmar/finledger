package com.finledger.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One "bucket" in the chart of accounts, e.g. CASH (asset) or
 * CUSTOMER_CASH:acc_001 (a liability owed to a specific customer).
 */
@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private LedgerAccountType accountType;

    /** The side on which this account increases: assets on DEBIT, liabilities on CREDIT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false)
    private EntryDirection normalBalance;

    protected LedgerAccount() {
        // required by JPA
    }

    public LedgerAccount(String code, String name, LedgerAccountType accountType, EntryDirection normalBalance) {
        this.code = code;
        this.name = name;
        this.accountType = accountType;
        this.normalBalance = normalBalance;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public LedgerAccountType getAccountType() {
        return accountType;
    }

    public EntryDirection getNormalBalance() {
        return normalBalance;
    }
}
