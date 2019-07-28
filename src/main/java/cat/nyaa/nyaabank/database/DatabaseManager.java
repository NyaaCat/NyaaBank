package cat.nyaa.nyaabank.database;

import cat.nyaa.nyaabank.NyaaBank;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.*;
import cat.nyaa.nyaacore.orm.*;
import cat.nyaa.nyaacore.orm.backends.IConnectedDatabase;
import cat.nyaa.nyaacore.orm.backends.ITypedTable;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {
    private final NyaaBank plugin;
    public final IConnectedDatabase db;
    public final ITypedTable<BankAccount> tableBankAccount;
    public final ITypedTable<BankRegistration> tableBankRegistration;
    public final ITypedTable<PartialRecord> tablePartialRecord;
    public final ITypedTable<SignRegistration> tableSignRegistration;
    public final ITypedTable<TransactionLog> tableTransactionLog;

    public DatabaseManager(NyaaBank plugin) {
        this.plugin = plugin;
        try {
            db = DatabaseUtils.connect(plugin, plugin.cfg.database);
            tableBankAccount = db.getTable(BankAccount.class);
            tableBankRegistration = db.getTable(BankRegistration.class);
            tablePartialRecord = db.getTable(PartialRecord.class);
            tableSignRegistration = db.getTable(SignRegistration.class);
            tableTransactionLog = db.getTable(TransactionLog.class);
        } catch (ClassNotFoundException | SQLException ex) {
            throw new RuntimeException("Failed to connect to database", ex);
        }
    }

    public void disconnect() {
        try {
            db.close();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * get unique bank registeration from partial bank UUID
     * null returned if bank not unique or not exists
     *
     * @param partialUUID part of the uuid, including dash
     * @return unique bank
     */
    public BankRegistration getUniqueBank(String partialUUID) {
        if (partialUUID == null || partialUUID.isEmpty()) return null;
        return tableBankRegistration.selectUniqueUnchecked(
                new WhereClause(BankRegistration.N_BANK_ID, " LIKE ", "%" + partialUUID + "%")
        );
    }

    public BankRegistration getUniqueBank(UUID bankId) {
        if (bankId == null) return null;
        return tableBankRegistration.selectUniqueUnchecked(WhereClause.EQ(BankRegistration.N_BANK_ID, bankId));
    }

    /**
     * Get player's bank account
     * return null if not found
     */
    public BankAccount getAccount(UUID bankId, UUID playerId) {
        BankAccount account = null;

        List<BankAccount> l = tableBankAccount.select(
                new WhereClause()
                        .whereEq(BankAccount.N_BANK_ID, bankId.toString())
                        .whereEq(BankAccount.N_PLAYER_ID, playerId.toString())
        );

        if (!l.isEmpty()) {
            if (l.size() > 1) {
                plugin.getLogger().severe("Duplicated account: bankid:" +
                        bankId.toString() + " playerid:" + playerId.toString());
            }
            account = l.get(0);
        }
        return account;
    }

    public List<PartialRecord> getPartialRecords(UUID bankId, UUID playerId, TransactionType type) {
        return tablePartialRecord.select(new WhereClause()
                .whereEq(PartialRecord.N_BANK_ID, bankId.toString())
                .whereEq(PartialRecord.N_PLAYER_ID, playerId.toString())
                .whereEq(PartialRecord.N_TRANSACTION_TYPE, type.name())
        );
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
        Long dbMaxId = tableBankRegistration.selectSingleton(String.format("MAX(%s)", BankRegistration.N_ID_NUMBER), DataTypeMapping.LongConverter.INSTANCE);
        return dbMaxId == null ? 1 : dbMaxId + 1;
    }

    public BankRegistration getBankByIdNumber(long idNumber) {
        try {
            return tableBankRegistration.selectUnique(WhereClause.EQ(BankRegistration.N_ID_NUMBER, idNumber));
        } catch (NonUniqueResultException ex) {
            return null;
        }
    }

    /* return a new log entry */
    public TransactionLog log(TransactionType type) {
        return new TransactionLog(this, type);
    }

    public RollbackGuard acquireRollbackGuard() {
        return new RollbackGuard(db);
    }
}
