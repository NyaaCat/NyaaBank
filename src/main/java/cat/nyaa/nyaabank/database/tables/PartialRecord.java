package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaacore.orm.annotations.Column;
import cat.nyaa.nyaacore.orm.annotations.Table;

import java.time.Instant;
import java.util.UUID;

/* New deposit or loan */
@Table("partial_transactions")
public class PartialRecord {
    // Data column names
    public static final String N_CAPITAL = "capital";
    public static final String N_TRANSACTION_ID = "transaction_id";
    public static final String N_BANK_ID = "bank_id";
    public static final String N_PLAYER_ID = "player_id";
    public static final String N_START_DATE = "start_date";
    public static final String N_TRANSACTION_TYPE = "transaction_type";

    @Column(name = N_TRANSACTION_ID, primary = true)
    public UUID transactionId;

    @Column(name = N_BANK_ID)
    public UUID bankId;

    @Column(name = N_PLAYER_ID)
    public UUID playerId;

    @Column(name = N_CAPITAL)
    public Double capital;

    @Column(name = N_TRANSACTION_TYPE)
    public TransactionType type; // deposit or loan

    public Instant startDate; // Stored as Unix timestamp ms

    @Column(name = N_START_DATE)
    public Long getStartDate() {
        return startDate.toEpochMilli();
    }

    public void setStartDate(Long startDate) {
        this.startDate = Instant.ofEpochMilli(startDate);
    }
}
