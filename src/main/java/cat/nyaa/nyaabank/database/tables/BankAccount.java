package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaacore.orm.annotations.Column;
import cat.nyaa.nyaacore.orm.annotations.Table;

import java.util.UUID;

@Table("bank_accounts")
public class BankAccount {
    public static final String N_ACCOUNT_ID = "account_id";
    @Column(name = N_ACCOUNT_ID, primary = true)
    public UUID accountId;

    public static final String N_BANK_ID = "bank_id";
    @Column(name = N_BANK_ID)
    public UUID bankId;

    public static final String N_PLAYER_ID = "player_id";
    @Column(name = N_PLAYER_ID)
    public UUID playerId;


    public static final String N_DEPOSIT = "deposit";
    @Column(name = N_DEPOSIT)
    public Double deposit;

    public static final String N_DEPOSIT_INTEREST = "deposit_interest";
    @Column(name = N_DEPOSIT_INTEREST)
    public Double deposit_interest;

    public static final String N_LOAN = "loan";
    @Column(name = N_LOAN)
    public Double loan;

    public static final String N_LOAN_INTEREST = "loan_interest";
    @Column(name = N_LOAN_INTEREST)
    public Double loan_interest;
}
