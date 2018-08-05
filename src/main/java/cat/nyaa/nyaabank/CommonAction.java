package cat.nyaa.nyaabank;

import cat.nyaa.nyaabank.database.enums.BankStatus;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.BankAccount;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.PartialRecord;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.librazy.nclangchecker.LangKey;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommonAction {
    public static class TransactionException extends Exception {
        public TransactionException(String msg) {
            super(msg);
        }

        @Override
        @LangKey(skipCheck = true)
        public String getMessage() {
            return super.getMessage();
        }
    }

    public static void deposit(NyaaBank plugin, Player player, BankRegistration bank,
                               double amount) throws TransactionException {
        if (amount <= 0) throw new TransactionException("user.deposit.invalid_amount");
        if (!plugin.eco.has(player, amount)) throw new TransactionException("user.deposit.not_enough_money");
        if (bank == null) throw new TransactionException("user.deposit.bank_not_found");
        if (bank.status == BankStatus.BANKRUPT) throw new TransactionException("user.deposit.bankrupted");
        OfflinePlayer banker = plugin.getServer().getOfflinePlayer(bank.ownerId);

        plugin.eco.withdrawPlayer(player, amount);
        PartialRecord partial = new PartialRecord();
        partial.transactionId = UUID.randomUUID();
        partial.bankId = bank.bankId;
        partial.playerId = player.getUniqueId();
        partial.capital = amount;
        partial.type = TransactionType.DEPOSIT;
        partial.startDate = Instant.now();
        plugin.dbm.db.query(PartialRecord.class).insert(partial);
        plugin.dbm.log(TransactionType.DEPOSIT)
                  .from(player.getUniqueId())
                  .to(bank.bankId)
                  .capital(amount)
                  .extra("partialId", partial.transactionId.toString())
                  .insert();
        plugin.eco.depositPlayer(banker, amount);
    }

    public static void withdraw(NyaaBank plugin, Player player, BankRegistration bank,
                                double amount, boolean withdrawAll) throws TransactionException {
        if (bank == null) throw new TransactionException("user.withdraw.bank_not_found");
        if (bank.status == BankStatus.BANKRUPT) throw new TransactionException("user.withdraw.bankrupted");
        OfflinePlayer banker = plugin.getServer().getOfflinePlayer(bank.ownerId);
        double totalDeposit = plugin.dbm.getTotalDeposit(bank.bankId, player.getUniqueId());
        if (withdrawAll) {
            if (!plugin.eco.has(banker, totalDeposit)) throw new TransactionException("user.withdraw.bank_run");
            BankAccount account = plugin.dbm.getAccount(bank.bankId, player.getUniqueId());
            if (account != null) {
                account.deposit = 0D;
                account.deposit_interest = 0D;
                plugin.dbm.db.query(BankAccount.class)
                             .whereEq(BankAccount.N_ACCOUNT_ID, account.getAccountId())
                             .update(account, BankAccount.N_DEPOSIT, BankAccount.N_DEPOSIT_INTEREST);
            }
            plugin.dbm.db.query(PartialRecord.class)
                         .whereEq(PartialRecord.N_BANK_ID, bank.getBankId())
                         .whereEq(PartialRecord.N_PLAYER_ID, player.getUniqueId().toString())
                         .whereEq(PartialRecord.N_TRANSACTION_TYPE, TransactionType.DEPOSIT.name())
                         .delete();
            plugin.eco.withdrawPlayer(banker, totalDeposit);
            plugin.dbm.log(TransactionType.WITHDRAW).from(bank.bankId).to(player.getUniqueId())
                      .capital(totalDeposit).insert();
            plugin.eco.depositPlayer(player, totalDeposit);
        } else {
            if (amount <= 0) throw new TransactionException("user.withdraw.invalid_amount");
            if (amount > totalDeposit) throw new TransactionException("user.withdraw.not_enough_deposit");
            if (!plugin.eco.has(banker, amount)) throw new TransactionException("user.withdraw.bank_run");
            double realAmount = 0;
            List<PartialRecord> l = plugin.dbm.getPartialRecords(bank.bankId, player.getUniqueId(), TransactionType.DEPOSIT);
            l.sort((a, b) -> a.capital.equals(b.capital) ? 0 : (a.capital < b.capital ? -1 : 1));
            int idx = 0;
            while (amount > 0 && idx < l.size()) {
                PartialRecord r = l.get(idx);
                if (amount > r.capital) {
                    amount -= r.capital;
                    realAmount += r.capital;
                    plugin.dbm.db.query(PartialRecord.class).whereEq(PartialRecord.N_TRANSACTION_ID, r.getTransactionId()).delete();
                    idx++;
                } else {
                    realAmount += amount;
                    r.capital -= amount;
                    amount = -1;
                    plugin.dbm.db.query(PartialRecord.class).whereEq(PartialRecord.N_TRANSACTION_ID, r.getTransactionId())
                                 .update(r, PartialRecord.N_CAPITAL);
                }
            }
            if (amount > 0) {
                BankAccount account = plugin.dbm.getAccount(bank.bankId, player.getUniqueId());
                if (amount > account.deposit + account.deposit_interest) {
                    realAmount += account.deposit + account.deposit_interest;
                    account.deposit = 0D;
                    account.deposit_interest = 0D;
                } else if (amount > account.deposit_interest) {
                    account.deposit -= amount - account.deposit_interest;
                    account.deposit_interest = 0D;
                    realAmount += amount;
                    amount = 0D;
                } else {
                    account.deposit_interest -= amount;
                    realAmount += amount;
                    amount = 0D;
                }
                plugin.dbm.db.query(BankAccount.class).whereEq(BankAccount.N_ACCOUNT_ID, account.getAccountId())
                             .update(account, BankAccount.N_DEPOSIT, BankAccount.N_DEPOSIT_INTEREST);
            }
            plugin.eco.withdrawPlayer(banker, realAmount);
            plugin.dbm.log(TransactionType.WITHDRAW).from(bank.bankId).to(player.getUniqueId())
                      .capital(realAmount).insert();
            plugin.eco.depositPlayer(player, realAmount);
        }
    }

    public static void loan(NyaaBank plugin, Player player, BankRegistration bank,
                            double amount) throws TransactionException {
        if (amount <= 0) throw new TransactionException("user.loan.invalid_amount");
        if (bank == null) throw new TransactionException("user.loan.bank_not_found");
        if (bank.status == BankStatus.BANKRUPT) throw new TransactionException("user.loan.bankrupted");
        OfflinePlayer banker = plugin.getServer().getOfflinePlayer(bank.ownerId);
        if (!plugin.eco.has(banker, amount)) throw new TransactionException("user.loan.not_enough_money_bank");
        BankAccount account = plugin.dbm.getAccount(bank.bankId, player.getUniqueId());
        if (account != null && (account.loan > 0 || account.loan_interest > 0)) {
            throw new TransactionException("user.loan.has_loan");
        }
        if (plugin.dbm.db.query(PartialRecord.class)
                         .whereEq(PartialRecord.N_PLAYER_ID, player.getUniqueId().toString())
                         .whereEq(PartialRecord.N_BANK_ID, bank.getBankId())
                         .whereEq(PartialRecord.N_TRANSACTION_TYPE, TransactionType.LOAN.name())
                         .count() > 0) {
            throw new TransactionException("user.loan.has_loan");
        }
        PartialRecord partial = new PartialRecord();
        partial.transactionId = UUID.randomUUID();
        partial.bankId = bank.bankId;
        partial.playerId = player.getUniqueId();
        partial.capital = amount;
        partial.type = TransactionType.LOAN;
        partial.startDate = Instant.now();
        plugin.dbm.db.query(PartialRecord.class).insert(partial);
        plugin.eco.withdrawPlayer(banker, amount);
        plugin.eco.depositPlayer(player, amount);
        plugin.dbm.log(TransactionType.LOAN)
                  .to(player.getUniqueId())
                  .from(bank.bankId)
                  .capital(amount)
                  .extra("partialId", partial.transactionId.toString())
                  .insert();
    }

    public static void repay(NyaaBank plugin, Player player, BankRegistration bank,
                             double amount, boolean repayAll) throws TransactionException {
        if (bank == null) throw new TransactionException("user.repay.bank_not_found");
        if (bank.status == BankStatus.BANKRUPT) throw new TransactionException("user.repay.bankrupted");
        OfflinePlayer banker = plugin.getServer().getOfflinePlayer(bank.ownerId);

        double totalLoan = plugin.dbm.getTotalLoan(bank.bankId, player.getUniqueId());
        if (repayAll) {
            if (!plugin.eco.has(player, totalLoan)) throw new TransactionException("user.repay.not_enough_money");
            BankAccount account = plugin.dbm.getAccount(bank.bankId, player.getUniqueId());
            if (account != null) {
                account.loan = 0D;
                account.loan_interest = 0D;
                plugin.dbm.db.query(BankAccount.class)
                             .whereEq(BankAccount.N_ACCOUNT_ID, account.getAccountId())
                             .update(account, BankAccount.N_LOAN, BankAccount.N_LOAN_INTEREST);
            }
            plugin.dbm.db.query(PartialRecord.class)
                         .whereEq(PartialRecord.N_BANK_ID, bank.getBankId())
                         .whereEq(PartialRecord.N_PLAYER_ID, player.getUniqueId().toString())
                         .whereEq(PartialRecord.N_TRANSACTION_TYPE, TransactionType.LOAN.name())
                         .delete();
            plugin.eco.depositPlayer(banker, totalLoan);
            plugin.dbm.log(TransactionType.REPAY).to(bank.bankId).from(player.getUniqueId())
                      .capital(totalLoan).insert();
            plugin.eco.withdrawPlayer(player, totalLoan);
        } else {
            if (amount <= 0) throw new TransactionException("user.repay.invalid_amount");
            if (amount > totalLoan) throw new TransactionException("user.repay.no_need_to_pay");
            if (!plugin.eco.has(player, amount)) throw new TransactionException("user.repay.not_enough_money");
            double realAmount = 0;
            List<PartialRecord> l = plugin.dbm.getPartialRecords(bank.bankId, player.getUniqueId(), TransactionType.LOAN);
            l.sort((a, b) -> a.capital.compareTo(b.capital));
            int idx = 0;
            while (amount > 0 && idx < l.size()) {
                PartialRecord r = l.get(idx);
                if (amount > r.capital) {
                    amount -= r.capital;
                    realAmount += r.capital;
                    plugin.dbm.db.query(PartialRecord.class).whereEq(PartialRecord.N_TRANSACTION_ID, r.getTransactionId()).delete();
                    idx++;
                } else {
                    realAmount += amount;
                    r.capital -= amount;
                    amount = -1;
                    plugin.dbm.db.query(PartialRecord.class).whereEq(PartialRecord.N_TRANSACTION_ID, r.getTransactionId())
                                 .update(r, PartialRecord.N_CAPITAL);
                }
            }
            if (amount > 0) {
                BankAccount account = plugin.dbm.getAccount(bank.bankId, player.getUniqueId());
                if (amount > account.loan + account.loan_interest) {
                    realAmount += account.loan + account.loan_interest;
                    account.loan = 0D;
                    account.loan_interest = 0D;
                } else if (amount > account.loan_interest) {
                    account.loan -= amount - account.loan_interest;
                    account.loan_interest = 0D;
                    realAmount += amount;
                    amount = 0D;
                } else {
                    account.loan_interest -= amount;
                    realAmount += amount;
                    amount = 0D;
                }
                plugin.dbm.db.query(BankAccount.class).whereEq(BankAccount.N_ACCOUNT_ID, account.getAccountId())
                             .update(account, BankAccount.N_LOAN, BankAccount.N_LOAN_INTEREST);
            }
            plugin.eco.depositPlayer(banker, realAmount);
            plugin.dbm.log(TransactionType.REPAY).to(bank.bankId).from(player.getUniqueId())
                      .capital(realAmount).insert();
            plugin.eco.withdrawPlayer(player, realAmount);
        }
    }
}
