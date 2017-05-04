package cat.nyaa.nyaabank.database;

import cat.nyaa.nyaabank.NyaaBank;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.*;
import cat.nyaa.nyaacore.database.SQLiteDatabase;
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
                SignRegistration.class,
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
        if (partialUUID == null || "".equals(partialUUID)) return null;
        List<BankRegistration> r = query(BankRegistration.class)
                .where(BankRegistration.N_BANK_ID, " LIKE ", "%" + partialUUID + "%")
                .select();
        if (r.size() > 1 || r.size() <= 0) return null;
        return r.get(0);
    }

    /**
     * Get player's bank account
     * return null if not found
     */
    public BankAccount getAccount(UUID bankId, UUID playerId) {
        BankAccount account = null;
        List<BankAccount> l = query(BankAccount.class)
                .whereEq(BankAccount.N_BANK_ID, bankId.toString())
                .whereEq(BankAccount.N_PLAYER_ID, playerId.toString())
                .select();
        if (l.size() > 0) {
            if (l.size() > 1) {
                plugin.getLogger().severe("Duplicated account: bankid:" +
                        bankId.toString() + " playerid:" + playerId.toString());
            }
            account = l.get(0);
        }
        return account;
    }

    public List<PartialRecord> getPartialRecords(UUID bankId, UUID playerId, TransactionType type) {
        return query(PartialRecord.class)
                .whereEq(PartialRecord.N_BANK_ID, bankId.toString())
                .whereEq(PartialRecord.N_PLAYER_ID, playerId.toString())
                .whereEq(PartialRecord.N_TRANSACTION_TYPE, type.name())
                .select();
    }

    /**
     * Get total deposit: deposit+interest+partial
     *
     * @param bankId bank id
     * @param playerId player id
     * @return total deposit
     */
    public double getTotalDeposit(UUID bankId, UUID playerId) {
        double ret = 0;
        BankAccount account = getAccount(bankId, playerId);
        if (account != null) {
            ret += account.deposit + account.deposit_interest;
        }
        for (PartialRecord rec : getPartialRecords(bankId, playerId, TransactionType.DEPOSIT)) {
            ret += rec.capital;
        }
        return ret;
    }

    /**
     * Get total loan: loan+interest+partial
     *
     * @param bankId bank id
     * @param playerId player id
     * @return total loan
     */
    public double getTotalLoan(UUID bankId, UUID playerId) {
        double ret = 0;
        BankAccount account = getAccount(bankId, playerId);
        if (account != null) {
            ret += account.loan + account.loan_interest;
        }
        for (PartialRecord rec : getPartialRecords(bankId, playerId, TransactionType.LOAN)) {
            ret += rec.capital;
        }
        return ret;
    }

    /**
     * Return next unused bank id number
     */
    public long getNextIdNumber() {
        long id = 1;
        for (BankRegistration b : query(BankRegistration.class).select()) {
            if (b.idNumber >= id) id = b.idNumber + 1;
        }
        return id;
    }

    public BankRegistration getBankByIdNumber(long idNumber) {
        try {
            return query(BankRegistration.class).whereEq(BankRegistration.N_ID_NUMBER, idNumber).selectUnique();
        } catch (RuntimeException ex) {
            // TODO ResultNotUniqueException
            if (ex.getMessage() != null && ex.getMessage().startsWith("SQL Selection")) {
                return null;
            } else {
                throw ex;
            }
        }
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
