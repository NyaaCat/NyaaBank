package cat.nyaa.nyaabank.database.tables;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts")
@Access(AccessType.FIELD)
public class BankAccount {
    // Data column names
    public static final String N_ACCOUNT_ID = "account_id";
    public static final String N_BANK_ID = "bank_id";
    public static final String N_PLAYER_ID = "player_id";
    public static final String N_DEPOSIT = "deposit";
    public static final String N_DEPOSIT_INTEREST = "deposit_interest";
    public static final String N_LOAN = "loan";
    public static final String N_LOAN_INTEREST = "loan_interest";

    public UUID accountId;
    public UUID bankId;
    public UUID playerId;
    @Column(name = N_DEPOSIT)
    public Double deposit;
    @Column(name = N_DEPOSIT_INTEREST)
    public Double deposit_interest;
    @Column(name = N_LOAN)
    public Double loan;
    @Column(name = N_LOAN_INTEREST)
    public Double loan_interest;

    @Access(AccessType.PROPERTY)
    @Column(name = N_ACCOUNT_ID)
    @Id
    public String getAccountId() {
        return accountId.toString();
    }

    public void setAccountId(String accountId) {
        this.accountId = UUID.fromString(accountId);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_BANK_ID)
    public String getBankId() {
        return bankId.toString();
    }

    public void setBankId(String bankId) {
        this.bankId = UUID.fromString(bankId);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_PLAYER_ID)
    public String getPlayerId() {
        return playerId.toString();
    }

    public void setPlayerId(String playerId) {
        this.playerId = UUID.fromString(playerId);
    }
}
