package cat.nyaa.nyaabank.database;

import cat.nyaa.nyaabank.NyaaBank;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.BankAccount;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.PartialRecord;
import cat.nyaa.nyaabank.database.tables.TransactionLog;
import cat.nyaa.nyaacore.database.DatabaseUtils;
import cat.nyaa.nyaacore.database.RelationalDB;

import java.util.List;
import java.util.UUID;

public class DatabaseManager implements Cloneable {
    private final NyaaBank plugin;
    public final RelationalDB db;

    public DatabaseManager(NyaaBank plugin) {
        db = DatabaseUtils.get();
        this.plugin = plugin;
        db.connect();
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
        List<BankRegistration> r = db.query(BankRegistration.class)
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
        List<BankAccount> l = db.query(BankAccount.class)
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
        return db.query(PartialRecord.class)
                 .whereEq(PartialRecord.N_BANK_ID, bankId.toString())
                 .whereEq(PartialRecord.N_PLAYER_ID, playerId.toString())
                 .whereEq(PartialRecord.N_TRANSACTION_TYPE, type.name())
                 .select();
    }

    /**
     * Get total deposit: deposit+interest+partial
     *
     * @param bankId   bank id
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
     * @param bankId   bank id
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
        for (BankRegistration b : db.query(BankRegistration.class).select()) {
            if (b.idNumber >= id) id = b.idNumber + 1;
        }
        return id;
    }

    public BankRegistration getBankByIdNumber(long idNumber) {
        try {
            return db.query(BankRegistration.class).whereEq(BankRegistration.N_ID_NUMBER, idNumber).selectUnique();
        } catch (RuntimeException ex) {
            // TODO ResultNotUniqueException
            if (ex.getMessage() != null && ex.getMessage().startsWith("SQL Selection")) {
                return null;
            } else {
                throw ex;
            }
        }
    }

    /* return a new log entry */
    public TransactionLog log(TransactionType type) {
        return new TransactionLog(this, type);
    }
}
