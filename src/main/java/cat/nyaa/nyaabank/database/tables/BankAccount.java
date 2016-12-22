package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.utils.database.DataColumn;
import cat.nyaa.utils.database.DataTable;
import cat.nyaa.utils.database.PrimaryKey;

import java.util.UUID;

@DataTable("bank_accounts")
public class BankAccount {
    public UUID accountId;
    public UUID bankId;
    public UUID playerId;
    @DataColumn("deposit")
    public Double deposit;
    @DataColumn("deposit_interest")
    public Double deposit_interest;
    @DataColumn("loan")
    public Double loan;
    @DataColumn("loan_interest")
    public Double loan_interest;


    @DataColumn("account_id")
    @PrimaryKey
    public String getAccountId() {
        return accountId.toString();
    }

    public void setAccountId(String accountId) {
        this.accountId = UUID.fromString(accountId);
    }

    @DataColumn("bank_id")
    public String getBankId() {
        return bankId.toString();
    }

    public void setBankId(String bankId) {
        this.bankId = UUID.fromString(bankId);
    }

    @DataColumn("player_id")
    public String getPlayerId() {
        return playerId.toString();
    }

    public void setPlayerId(String playerId) {
        this.playerId = UUID.fromString(playerId);
    }
}
