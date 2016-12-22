package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.utils.database.DataColumn;
import cat.nyaa.utils.database.DataTable;
import cat.nyaa.utils.database.PrimaryKey;

import java.time.Instant;
import java.util.UUID;

/* New deposit or loan */
@DataTable("partial_transactions")
public class PartialRecord {

    public UUID transactionId;
    public UUID bankId;
    public UUID playerId;
    public Instant startDate; // Stored as Unix timestamp ms
    @DataColumn("capital")
    public Double capital;
    public TransactionType type; // deposit or loan

    @DataColumn("transaction_id")
    @PrimaryKey
    public String getTransactionId() {
        return transactionId.toString();
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = UUID.fromString(transactionId);
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

    @DataColumn("start_date")
    public Long getStartDate() {
        return startDate.toEpochMilli();
    }

    public void setStartDate(Long startDate) {
        this.startDate = Instant.ofEpochMilli(startDate);
    }

    @DataColumn("transaction_type")
    public String getType() {
        return type.toString();
    }

    public void setType(String type) {
        this.type = TransactionType.valueOf(type);
    }
}
