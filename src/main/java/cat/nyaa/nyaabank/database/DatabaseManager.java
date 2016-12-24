package cat.nyaa.nyaabank.database;

import cat.nyaa.nyaabank.NyaaBank;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.BankAccount;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.PartialRecord;
import cat.nyaa.nyaabank.database.tables.TransactionLog;
import cat.nyaa.utils.database.SQLiteDatabase;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class DatabaseManager extends SQLiteDatabase {
    private final NyaaBank plugin;

    public DatabaseManager(NyaaBank plugin) {
        super();
        this.plugin = plugin;
        connect();
    }

    @Override
    protected String getFileName() {
        return "nyaabank.db";
    }

    @Override
    protected JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    protected Class<?>[] getTables() {
        return new Class<?>[]{
                BankRegistration.class,
                BankAccount.class,
                //SignRegistration.class,
                PartialRecord.class,
                TransactionLog.class
        };
    }

    /**
     * get unique bank registeration from partial bank UUID
     * null returned if bank not unique or not exists
     *
     * @param partialUUID part of the uuid, including dash
     * @return unique bank
     */
    public BankRegistration getUniqueBank(String partialUUID) {
        if (partialUUID == null) return null;
        List<BankRegistration> r = query(BankRegistration.class)
                .where("bank_id", " LIKE ", "%" + partialUUID + "%")
                .select();
        if (r.size() > 1 || r.size() <= 0) return null;
        return r.get(0);
    }

    /**
     * get unique bank account for one player in one bank.
     *
     * @param bankId
     * @param playerId
     * @return
     */
    public BankAccount getBankAccount(UUID bankId, UUID playerId) {
        return null; // todo
    }

    public void disableAutoCommit() {
        try {
            getConnection().setAutoCommit(false);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void enableAutoCommit() {
        try {
            getConnection().commit();
            getConnection().setAutoCommit(true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /* return a new log entry */
    public TransactionLog log(TransactionType type) {
        return new TransactionLog(this, type);
    }

    @Override
    protected DatabaseManager clone() {
        try {
            return (DatabaseManager) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException(ex);
        }
    }
}
