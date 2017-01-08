package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.utils.database.DataColumn;
import cat.nyaa.utils.database.DataTable;
import cat.nyaa.utils.database.PrimaryKey;

import java.util.UUID;

@DataTable("bank_accounts")
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
    @DataColumn(N_DEPOSIT)
    public Double deposit;
    @DataColumn(N_DEPOSIT_INTEREST)
    public Double deposit_interest;
    @DataColumn(N_LOAN)
    public Double loan;
    @DataColumn(N_LOAN_INTEREST)
    public Double loan_interest;


    @DataColumn(N_ACCOUNT_ID)
    @PrimaryKey
    public String getAccountId() {
        return accountId.toString();
    }

    public void setAccountId(String accountId) {
        this.accountId = UUID.fromString(accountId);
    }

    @DataColumn(N_BANK_ID)
    public String getBankId() {
        return bankId.toString();
    }

    public void setBankId(String bankId) {
        this.bankId = UUID.fromString(bankId);
    }

    @DataColumn(N_PLAYER_ID)
    public String getPlayerId() {
        return playerId.toString();
    }

    public void setPlayerId(String playerId) {
        this.playerId = UUID.fromString(playerId);
    }
}
