package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaabank.database.DatabaseManager;
import cat.nyaa.nyaabank.database.enums.AccountType;
import cat.nyaa.nyaabank.database.enums.TransactionType;

import javax.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transaction_log")
@Access(AccessType.FIELD)
public class TransactionLog {
    // Data column names
    public static final String N_ID = "id";
    public static final String N_CAPITAL = "capital";
    public static final String N_EXTRA = "extra";
    public static final String N_TIME = "time";
    public static final String N_FROM_ID = "from_id";
    public static final String N_TO_ID = "to_id";
    public static final String N_FROM_TYPE = "from_type";
    public static final String N_TO_TYPE = "to_type";
    public static final String N_TRANSACTION_TYPE = "transaction_type";

    @Id
    @Column(name = N_ID)
    public Long id;

    private Instant time;
    private UUID from;
    private UUID to;
    @Column(name = N_CAPITAL)
    public Double capital;
    public AccountType fromType;
    public AccountType toType;
    public TransactionType type;
    @Column(name = N_EXTRA)
    public String extra;

    @Access(AccessType.PROPERTY)
    @Column(name = N_TIME)
    public String getTime() {
        return time.toString();
    }

    public void setTime(String time) {
        this.time = Instant.parse(time);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_FROM_ID)
    public String getFrom() {
        return from.toString();
    }

    public void setFrom(String from) {
        this.from = UUID.fromString(from);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_TO_ID)
    public String getTo() {
        return to.toString();
    }

    public void setTo(String to) {
        this.to = UUID.fromString(to);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_FROM_TYPE)
    public String getFromType() {
        return fromType.toString();
    }

    public void setFromType(String fromType) {
        this.fromType = AccountType.valueOf(fromType);
    }


    @Access(AccessType.PROPERTY)
    @Column(name = N_TO_TYPE)
    public String getToType() {
        return toType.toString();
    }

    public void setToType(String toType) {
        this.toType = AccountType.valueOf(toType);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_TRANSACTION_TYPE)
    public String getType() {
        return type.toString();
    }

    public void setType(String type) {
        this.type = TransactionType.valueOf(type);
    }

    public TransactionLog() {

    }

    @Transient
    private DatabaseManager dbm = null;

    public TransactionLog(DatabaseManager dbm, TransactionType ttype) {
        this.dbm = dbm;
        switch (ttype) {
            case DEPOSIT:  // from PLAYER to BANK, PARTIAL_ID in EXTRA
            case REPAY:    // from PLAYER to BANK
            case INTEREST_LOAN:    // from PLAYER to BANK
            case PARTIAL_MOVE:    // from PLAYER to BANK, PARTIAL_ID in EXTRA
            case VAULT_CHANGE:
            case COMMISSION:
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

    @Transient
    public TransactionLog from(UUID id) {
        this.from = id;
        return this;
    }

    @Transient
    public TransactionLog to(UUID id) {
        this.to = id;
        return this;
    }

    @Transient
    public TransactionLog capital(Double capital) {
        this.capital = capital;
        return this;
    }

    @Transient
    private Map<String, String> extraMap = null;

    // TODO extra document
    public TransactionLog extra(String key, String value) {
        if (extraMap == null) extraMap = new HashMap<>();
        extraMap.put(key, value);
        return this;
    }

    public void insert() {
        if (dbm == null) throw new IllegalStateException("assert(dbm != null)");
        time = Instant.now();
        id = null;
        extra = "";
        if (extraMap != null) {
            for (String key : extraMap.keySet()) {
                if (extra.length() > 0) extra += ", ";
                extra += String.format("\"%s\": \"%s\"", key, extraMap.get(key));
            }
        }
        extra = "{" + extra + "}";
        dbm.db.query(TransactionLog.class).insert(this);
    }
}
