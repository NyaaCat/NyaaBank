package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaabank.database.DatabaseManager;
import cat.nyaa.nyaabank.database.enums.AccountType;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaacore.orm.annotations.Column;
import cat.nyaa.nyaacore.orm.annotations.Table;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Table("transaction_log")
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

    @Column(name = N_ID, primary = true, unique = true, nullable = false)
    public Long id;

    @Column(name = N_TIME)
    private Instant time;
    @Column(name = N_FROM_ID)
    private UUID from;
    @Column(name = N_TO_ID)
    private UUID to;
    @Column(name = N_CAPITAL)
    public Double capital;
    @Column(name = N_FROM_TYPE)
    public AccountType fromType;
    @Column(name = N_TO_TYPE)
    public AccountType toType;
    @Column(name = N_TRANSACTION_TYPE)
    public TransactionType type;
    @Column(name = N_EXTRA)
    public String extra;


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
        dbm.tableTransactionLog.insert(this);
    }
}
