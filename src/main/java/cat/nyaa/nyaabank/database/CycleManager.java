package cat.nyaa.nyaabank.database;

import cat.nyaa.nyaabank.NyaaBank;
import cat.nyaa.nyaabank.database.enums.BankStatus;
import cat.nyaa.nyaabank.database.tables.BankAccount;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.PartialRecord;
import cat.nyaa.nyaabank.database.tables.SignRegistration;
import cat.nyaa.nyaabank.signs.SignHelper;
import cat.nyaa.nyaacore.database.Query;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static cat.nyaa.nyaabank.database.enums.TransactionType.*;

public class CycleManager {
    private final NyaaBank plugin;

    public CycleManager(NyaaBank plugin) {
        this.plugin = plugin;
        onEnabled();
    }

    private class CheckPointTask extends BukkitRunnable {
        CheckPointTask(long delay_ms) {
            long delay_ticks = delay_ms / 50 + 1; // ms to ticks
            this.runTaskLater(plugin, delay_ticks);
        }

        @Override
        public void run() {
            try {

                plugin.dbm.db.beginTransaction();
                long len = plugin.cfg.interestCycle;
                long offset = plugin.cfg.interestCycleOffset;
                long now = System.currentTimeMillis() - offset;

                // update db
                long idxB = Math.floorDiv(now, len);
                updateDatabaseInterests(idxB * len + offset, len);

                // update lastCheckPoint
                plugin.cfg.lastCheckPoint = idxB * len + offset + 1;
                plugin.cfg.save();

                // schedule next timer
                long idxC = idxB + 1;
                long nextCheckpoint = idxC * len + offset + 1;
                long delay = nextCheckpoint - now;
                new CheckPointTask(delay);

                SignHelper.batchUpdateSign(plugin, plugin.dbm.db.query(SignRegistration.class).select());
                plugin.dbm.db.commitTransaction();
            } catch (Exception e) {
                plugin.dbm.db.rollbackTransaction();
                throw e;
            }
            // TODO we may not need to update all signs
        }
    }

    public long getNextCheckpoint() {
        long len = plugin.cfg.interestCycle;
        long offset = plugin.cfg.interestCycleOffset;
        long now = System.currentTimeMillis() - offset;
        long idxB = Math.floorDiv(now, len);
        long idxC = idxB + 1;
        return idxC * len + offset;
    }

    /**
     * 1. check any missed check points
     * 2. setup timer to next check point
     */
    private void onEnabled() {
        long len = plugin.cfg.interestCycle;
        long offset = plugin.cfg.interestCycleOffset;
        long last = plugin.cfg.lastCheckPoint - offset;
        long now = System.currentTimeMillis() - offset;

        /* compute # of missed check points */
        if (last >= 0) {
            long idxA = Math.floorDiv(last, len) + 1;
            long idxB = Math.floorDiv(now, len);
            for (long i = idxA; i <= idxB; i++) {
                updateDatabaseInterests(i * len + offset, len); // immediately compute missed check points
            }
            plugin.cfg.lastCheckPoint = idxB * len + offset + 1;
        } else {
            long idxB = Math.floorDiv(now, len);
            plugin.cfg.lastCheckPoint = idxB * len + offset + 1;
        }
        plugin.cfg.save();

        /* setup next checkpoint timer */
        long idxC = Math.floorDiv(now, len) + 1;
        long nextCheckpoint = idxC * len + offset + 1;
        long delay = nextCheckpoint - now;
        this.new CheckPointTask(delay);
    }

    /**
     * Actually check & update the database.
     * Since the timer won't tick exactly precise, there might be several seconds error.
     * Thus designatedTimestamp is used and the function acts like it's been called
     * exactly at that time.
     * <p>
     * It compute the interest for deposit & loan in BankAccounts
     * It compute the interest for deposit & loan in PartialRecord
     * It updates interest := interestNext
     * It won't change the lastCheckPoint in config file.
     *
     * @param designatedTimestamp unix timestamp ms
     * @param cycleLength         unix timestamp ms
     */
    public void updateDatabaseInterests(long designatedTimestamp, long cycleLength) {
        // TODO maybe we can write this as a big SQL query?
        // TODO run in another thread?
        try {
            plugin.dbm.db.beginTransaction();

            Map<UUID, Map<UUID, BankAccount>> accountMap = new HashMap<>(); // Map<bankId, Map<playerId, Account>>
            Map<UUID, BankRegistration> bankMap = new HashMap<>(); // Map<bankId, BankReg>
            for (BankAccount a : plugin.dbm.db.query(BankAccount.class).select()) {
                if (!accountMap.containsKey(a.bankId)) accountMap.put(a.bankId, new HashMap<>());
                accountMap.get(a.bankId).put(a.playerId, a);
            }
            for (BankRegistration r : plugin.dbm.db.query(BankRegistration.class).select()) {
                bankMap.put(r.bankId, r);
            }
            // compute BankAccounts
            for (UUID bankId : accountMap.keySet()) {
                if (bankMap.get(bankId).status == BankStatus.BANKRUPT) continue; // skip bankrupted banks
                for (UUID playerId : accountMap.get(bankId).keySet()) {
                    BankAccount account = accountMap.get(bankId).get(playerId);
                    BankRegistration bank = bankMap.get(bankId);
                    OfflinePlayer banker = plugin.getServer().getOfflinePlayer(bank.ownerId);

                    // Deposit interest
                    double deposit_interest = 0;
                    switch (bank.interestType) {
                        case SIMPLE:
                            deposit_interest = account.deposit * bank.savingInterest / 100D;
                            break;
                        case COMPOUND:
                            deposit_interest = (account.deposit + account.deposit_interest) * bank.savingInterest / 100D;
                    }
                    deposit_interest = Math.round(deposit_interest * 1000D) / 1000D; // round to 10^-3

                    if (deposit_interest >= 0) {
                        account.deposit_interest += deposit_interest;
                    } else if (deposit_interest + account.deposit_interest > 0) { // negative interest i.e. money transferred from player to bank
                        account.deposit_interest += deposit_interest;
                    } else {
                        account.deposit += deposit_interest + account.deposit_interest;
                        account.deposit_interest = 0D;
                    }
                    plugin.dbm.log(INTEREST_DEPOSIT).from(bankId).to(playerId).capital(deposit_interest).insert();

                    // Loan interest
                    double loan_interest = 0;
                    switch (bank.interestType) {
                        case SIMPLE:
                            loan_interest = account.loan * bank.debitInterest / 100D;
                            break;
                        case COMPOUND:
                            loan_interest = (account.loan + account.loan_interest) * bank.debitInterest / 100D;
                    }
                    loan_interest = Math.round(loan_interest * 1000D) / 1000D;

                    if (loan_interest >= 0) {
                        account.loan_interest += loan_interest;
                    } else if (loan_interest + account.loan_interest > 0) { // negative interest i.e. money transferred from bank to player
                        account.loan_interest += loan_interest;
                    } else {
                        account.loan += loan_interest + account.loan_interest;
                        account.loan_interest = 0D;
                    }
                    plugin.dbm.log(INTEREST_LOAN).from(playerId).to(bankId).capital(loan_interest).insert();
                }
            }

            // compute Partial Records
            for (PartialRecord partial : plugin.dbm.db.query(PartialRecord.class).select()) {
                BankRegistration bank = bankMap.get(partial.bankId);
                if (bank.status == BankStatus.BANKRUPT) continue; // skip bankrupted banks
                OfflinePlayer banker = plugin.getServer().getOfflinePlayer(bank.ownerId);
                BankAccount account = null;
                if (accountMap.containsKey(partial.bankId)) {
                    account = accountMap.get(partial.bankId).get(partial.playerId);
                }
                boolean newAccount = false;
                if (account == null) {
                    newAccount = true;
                    account = new BankAccount();
                    account.accountId = UUID.randomUUID();
                    account.bankId = partial.bankId;
                    account.playerId = partial.playerId;
                    account.deposit = 0D;
                    account.deposit_interest = 0D;
                    account.loan = 0D;
                    account.loan_interest = 0D;
                }

                switch (partial.type) {
                    case DEPOSIT: { // Deposit interest
                        double deposit_interest = partial.capital * bank.savingInterest / 100D;
                        deposit_interest *= (designatedTimestamp - partial.startDate.toEpochMilli()) / (double) cycleLength;
                        deposit_interest = Math.round(deposit_interest * 1000D) / 1000D;

                        if (deposit_interest >= 0) {
                            account.deposit += partial.capital;
                            account.deposit_interest += deposit_interest;
                            plugin.dbm.log(INTEREST_DEPOSIT).from(partial.bankId).to(partial.playerId).capital(deposit_interest)
                                      .extra("partialId", partial.transactionId.toString()).insert();
                            plugin.dbm.log(PARTIAL_MOVE).from(partial.playerId).to(partial.bankId).capital(partial.capital)
                                      .extra("partialId", partial.transactionId.toString())
                                      .extra("target", "DEPOSIT").insert();
                        } else if (partial.capital + deposit_interest > 0) { // negative interest i.e. money transferred from player to bank
                            account.deposit += partial.capital + deposit_interest;
                            plugin.dbm.log(INTEREST_DEPOSIT).from(partial.bankId).to(partial.playerId).capital(deposit_interest)
                                      .extra("partialId", partial.transactionId.toString()).insert();
                            plugin.dbm.log(PARTIAL_MOVE).from(partial.playerId).to(partial.bankId).capital(partial.capital + deposit_interest)
                                      .extra("partialId", partial.transactionId.toString())
                                      .extra("target", "DEPOSIT").insert();
                        } else { // bank take all the money
                            plugin.dbm.log(INTEREST_DEPOSIT).from(partial.bankId).to(partial.playerId).capital(-partial.capital)
                                      .extra("partialId", partial.transactionId.toString()).insert();
                        }
                        break;
                    }
                    case LOAN: { // Loan interest
                        double loan_interest = partial.capital * bank.debitInterest / 100D;
                        loan_interest *= (designatedTimestamp - partial.startDate.toEpochMilli()) / (double) cycleLength;
                        loan_interest = Math.round(loan_interest * 1000D) / 1000D;

                        if (loan_interest >= 0) {
                            account.loan += partial.capital;
                            account.loan_interest += loan_interest;
                            plugin.dbm.log(INTEREST_LOAN).from(partial.playerId).to(partial.bankId).capital(loan_interest)
                                      .extra("partialId", partial.transactionId.toString()).insert();
                            plugin.dbm.log(PARTIAL_MOVE).from(partial.playerId).to(partial.bankId).capital(partial.capital)
                                      .extra("partialId", partial.transactionId.toString())
                                      .extra("target", "LOAN").insert();
                        } else if (partial.capital + loan_interest > 0) { // negative interest i.e. money transferred from bank to player
                            account.loan += partial.capital + loan_interest;
                            plugin.dbm.log(INTEREST_LOAN).from(partial.playerId).to(partial.bankId).capital(loan_interest)
                                      .extra("partialId", partial.transactionId.toString()).insert();
                            plugin.dbm.log(PARTIAL_MOVE).from(partial.playerId).to(partial.bankId).capital(partial.capital + loan_interest)
                                      .extra("partialId", partial.transactionId.toString())
                                      .extra("target", "LOAN").insert();
                        } else {
                            plugin.dbm.log(INTEREST_LOAN).from(partial.playerId).to(partial.bankId).capital(-partial.capital)
                                      .extra("partialId", partial.transactionId.toString()).insert();
                            // give you the money, nothing need to be done.
                        }
                        break;
                    }
                }

                // insert new account to table
                if (newAccount) {
                    if (!accountMap.containsKey(account.bankId))
                        accountMap.put(account.bankId, new HashMap<>());
                    accountMap.get(account.bankId).put(account.playerId, account);
                    plugin.dbm.db.query(BankAccount.class).insert(account);
                }
            }

            // update interest := interestNext
            for (BankRegistration bank : bankMap.values()) {
                if (bank.status == BankStatus.BANKRUPT) continue; // skip bankrupted banks
                bank.debitInterest = bank.debitInterestNext;
                bank.savingInterest = bank.savingInterestNext;
                bank.interestType = bank.interestTypeNext;
            }

            // write to database
            Query<BankRegistration> query1 = plugin.dbm.db.query(BankRegistration.class);
            for (BankRegistration bank : bankMap.values()) {
                query1.reset().whereEq(BankRegistration.N_BANK_ID, bank.getBankId()).update(bank);
            }
            Query<BankAccount> query2 = plugin.dbm.db.query(BankAccount.class);
            for (Map<UUID, BankAccount> m : accountMap.values()) {
                for (BankAccount account : m.values()) {
                    query2.reset().whereEq(BankAccount.N_ACCOUNT_ID, account.getAccountId()).update(account);
                }
            }
            plugin.dbm.db.query(PartialRecord.class).delete();

            // Transaction finish
            plugin.dbm.db.commitTransaction();

        } catch (Exception e) {
            plugin.dbm.db.rollbackTransaction();
            throw e;
        }
    }
}
