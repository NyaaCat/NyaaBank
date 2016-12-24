package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaabank.database.DatabaseManager;
import cat.nyaa.nyaabank.database.enums.AccountType;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.utils.database.DataColumn;
import cat.nyaa.utils.database.DataTable;
import cat.nyaa.utils.database.PrimaryKey;

import java.time.Instant;
import java.util.UUID;

@DataTable("transaction_log")
public class TransactionLog {
    @PrimaryKey
    @DataColumn("id")
    public Long id;

    public Instant time;
    public UUID from;
    public UUID to;
    @DataColumn("capital")
    public Double capital;
    public AccountType fromType;
    public AccountType toType;
    public TransactionType type;
    @DataColumn("extra")
    public String extra;

    @DataColumn("time")
    public String getTime() {
        return time.toString();
    }

    public void setTime(String time) {
        this.time = Instant.parse(time);
    }

    @DataColumn("from_id")
    public String getFrom() {
        return from.toString();
    }

    public void setFrom(String from) {
        this.from = UUID.fromString(from);
    }

    @DataColumn("to_id")
    public String getTo() {
        return to.toString();
    }

    public void setTo(String to) {
        this.to = UUID.fromString(to);
    }

    @DataColumn("from_type")
    public String getFromType() {
        return fromType.toString();
    }

    public void setFromType(String fromType) {
        this.fromType = AccountType.valueOf(fromType);
    }

    @DataColumn("to_type")
    public String getToType() {
        return toType.toString();
    }

    public void setToType(String toType) {
        this.toType = AccountType.valueOf(toType);
    }

    @DataColumn("transaction_type")
    public String getType() {
        return type.toString();
    }

    public void setType(String type) {
        this.type = TransactionType.valueOf(type);
    }

    public TransactionLog() {

    }

    private DatabaseManager dbm = null;

    public TransactionLog(DatabaseManager dbm, TransactionType ttype) {
        this.dbm = dbm;
        switch (ttype) {
            case DEPOSIT:  // from PLAYER to BANK, PARTIAL_ID in EXTRA
            case REPAY:    // from PLAYER to BANK
            case INTEREST_LOAN:    // from PLAYER to BANK
            case PARTIAL_MOVE:    // from PLAYER to BANK, PARTIAL_ID in EXTRA
            case QUERY:   // from PLAYER to {random stuff}, detail in EXTRA
                fromType = AccountType.PLAYER;
                toType = AccountType.BANK;
                break;
            case WITHDRAW: // from BANK to PLAYER
            case LOAN:     // from BANK to PLAYER, PARTIAL_ID in EXTRA
            case INTEREST_DEPOSIT: // from BANK to PLAYER
                fromType = AccountType.BANK;
                toType = AccountType.PLAYER;
                break;
            default:
                throw new IllegalArgumentException("Unknown Transaction Type");
        }
        this.type = ttype;
        this.extra = "";
    }

    public TransactionLog from(UUID id) {
        this.from = id;
        return this;
    }

    public TransactionLog to(UUID id) {
        this.to = id;
        return this;
    }

    public TransactionLog capital(Double capital) {
        this.capital = capital;
        return this;
    }

    public TransactionLog extra(String template, Object... objs) {
        this.extra = String.format(template, objs);
        return this;
    }

    public void insert() {
        if (dbm == null) throw new IllegalStateException("assert(dbm != null)");
        time = Instant.now();
        id = null;
        dbm.query(TransactionLog.class).insert(this);
    }
}
