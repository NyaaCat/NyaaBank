package cat.nyaa.nyaabank.database.enums;

public enum TransactionType {
    DEPOSIT,  // from PLAYER to BANK, PARTIAL_ID in EXTRA
    WITHDRAW, // from BANK to PLAYER
    LOAN,     // from BANK to PLAYER, PARTIAL_ID in EXTRA
    REPAY,    // from PLAYER to BANK
    INTEREST_DEPOSIT, // from BANK to PLAYER
    INTEREST_LOAN,    // from PLAYER to BANK
    PARTIAL_MOVE,     // from PLAYER to BANK, PARTIAL_ID in EXTRA
    VAULT_CHANGE,     // from PLAYER to BANK
    QUERY;   // from PLAYER to {random stuff}, detail in EXTRA
}
