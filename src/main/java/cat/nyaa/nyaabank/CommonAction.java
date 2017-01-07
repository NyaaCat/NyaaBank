package cat.nyaa.nyaabank;

import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.BankAccount;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.PartialRecord;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommonAction {
    public static class TransactionException extends Exception {
        public TransactionException(String msg) {
            super(msg);
        }
    }

    public static void deposit(NyaaBank plugin, Player player, BankRegistration bank,
                               double amount) throws TransactionException {
        if (amount <= 0) throw new TransactionException("user.deposit.invalid_amount");
        if (!plugin.eco.has(player, amount)) throw new TransactionException("user.deposit.not_enough_money");
        if (bank == null) throw new TransactionException("user.deposit.bank_not_found");

        plugin.eco.withdrawPlayer(player, amount);
        PartialRecord partial = new PartialRecord();
        partial.transactionId = UUID.randomUUID();
        partial.bankId = bank.bankId;
        partial.playerId = player.getUniqueId();
        partial.capital = amount;
        partial.type = TransactionType.DEPOSIT;
        partial.startDate = Instant.now();
        plugin.dbm.query(PartialRecord.class).insert(partial);
        plugin.dbm.log(TransactionType.DEPOSIT)
                .from(player.getUniqueId())
                .to(bank.bankId)
                .capital(amount)
                .extra("{\"partialId\": \"%s\"}", partial.transactionId.toString())
                .insert();

        bank.capital += amount;
        plugin.dbm.query(BankRegistration.class)
                .whereEq("bank_id", bank.bankId)
                .update(bank, "capital");
    }

    public static void withdraw(NyaaBank plugin, Player player, BankRegistration bank,
                                double amount, boolean withdrawAll) throws TransactionException {
        if (bank == null) throw new TransactionException("user.withdraw.bank_not_found");
        double totalDeposit = plugin.dbm.getTotalDeposit(bank.bankId, player.getUniqueId());
        if (withdrawAll) {
            if (totalDeposit >= bank.capital) throw new TransactionException("user.withdraw.bank_run");
            BankAccount account = plugin.dbm.getAccount(bank.bankId, player.getUniqueId());
            if (account != null) {
                account.deposit = 0D;
                account.deposit_interest = 0D;
                plugin.dbm.query(BankAccount.class)
                        .whereEq("account_id", account.accountId)
                        .update(account, "deposit", "deposit_interest");
            }
            plugin.dbm.query(PartialRecord.class)
                    .whereEq("bank_id", bank.bankId.toString())
                    .whereEq("player_id", player.getUniqueId())
                    .whereEq("transaction_type", TransactionType.DEPOSIT.name())
                    .delete();
            bank.capital -= totalDeposit;
            plugin.dbm.query(BankRegistration.class).whereEq("bank_id", bank.bankId.toString())
                    .update(bank, "capital");
            plugin.dbm.log(TransactionType.WITHDRAW).from(bank.bankId).to(player.getUniqueId())
                    .capital(totalDeposit).insert();
            plugin.eco.depositPlayer(player, totalDeposit);
        } else {
            if (amount <= 0) throw new TransactionException("user.withdraw.invalid_amount");
            if (amount > totalDeposit) throw new TransactionException("user.withdraw.not_enough_deposit");
            if (amount >= bank.capital) throw new TransactionException("user.withdraw.bank_run");
            double realAmount = 0;
            List<PartialRecord> l = plugin.dbm.getPartialRecords(bank.bankId, player.getUniqueId(), TransactionType.DEPOSIT);
            l.sort((a, b) -> a.capital.equals(b.capital) ? 0 : (a.capital < b.capital ? -1 : 1));
            int idx = 0;
            while (amount > 0 && idx < l.size()) {
                PartialRecord r = l.get(idx);
                if (amount > r.capital) {
                    amount -= r.capital;
                    realAmount += r.capital;
                    plugin.dbm.query(PartialRecord.class).whereEq("transaction_id", r.transactionId.toString()).delete();
                    idx++;
                } else {
                    realAmount += amount;
                    r.capital -= amount;
                    amount = -1;
                    plugin.dbm.query(PartialRecord.class).whereEq("transaction_id", r.transactionId.toString())
                            .update(r, "capital");
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
                plugin.dbm.query(BankAccount.class).whereEq("account_id", account.getAccountId())
                        .update(account, "deposit", "deposit_interest");
            }
            bank.capital -= realAmount;
            plugin.dbm.query(BankRegistration.class).whereEq("bank_id", bank.bankId.toString())
                    .update(bank, "capital");
            plugin.dbm.log(TransactionType.WITHDRAW).from(bank.bankId).to(player.getUniqueId())
                    .capital(realAmount).insert();
            plugin.eco.depositPlayer(player, realAmount);
        }
    }

    public static void loan(NyaaBank plugin, Player player, BankRegistration bank,
                            double amount) throws TransactionException {
        if (amount <= 0) throw new TransactionException("user.loan.invalid_amount");
        if (bank == null) throw new TransactionException("user.loan.bank_not_found");
        if (bank.capital <= amount) throw new TransactionException("user.loan.not_enough_money_bank");
        BankAccount account = plugin.dbm.getAccount(bank.bankId, player.getUniqueId());
        if (account != null && (account.loan > 0 || account.loan_interest > 0)) {
            throw new TransactionException("user.loan.has_loan");
        }
        if (plugin.dbm.query(PartialRecord.class)
                .whereEq("player_id", player.getUniqueId().toString())
                .whereEq("bank_id", bank.getBankId())
                .whereEq("transaction_type", TransactionType.LOAN.name())
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
        plugin.dbm.query(PartialRecord.class).insert(partial);
        bank.capital -= amount;
        plugin.dbm.query(BankRegistration.class).whereEq("bank_id", bank.getBankId()).update(bank, "capital");
        plugin.eco.depositPlayer(player, amount);
        plugin.dbm.log(TransactionType.LOAN)
                .to(player.getUniqueId())
                .from(bank.bankId)
                .capital(amount)
                .extra("{\"partialId\": \"%s\"}", partial.transactionId.toString())
                .insert();
    }

    public static void repay(NyaaBank plugin, Player player, BankRegistration bank,
                             double amount, boolean repayAll) throws TransactionException {
        if (bank == null) throw new TransactionException("user.repay.bank_not_found");

        double totalLoan = plugin.dbm.getTotalLoan(bank.bankId, player.getUniqueId());
        if (repayAll) {
            if (!plugin.eco.has(player, totalLoan)) throw new TransactionException("user.repay.not_enough_money");
            BankAccount account = plugin.dbm.getAccount(bank.bankId, player.getUniqueId());
            if (account != null) {
                account.loan = 0D;
                account.loan_interest = 0D;
                plugin.dbm.query(BankAccount.class)
                        .whereEq("account_id", account.accountId)
                        .update(account, "loan", "loan_interest");
            }
            plugin.dbm.query(PartialRecord.class)
                    .whereEq("bank_id", bank.bankId.toString())
                    .whereEq("player_id", player.getUniqueId())
                    .whereEq("transaction_type", TransactionType.LOAN.name())
                    .delete();
            bank.capital += totalLoan;
            plugin.dbm.query(BankRegistration.class).whereEq("bank_id", bank.bankId.toString())
                    .update(bank, "capital");
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
                    plugin.dbm.query(PartialRecord.class).whereEq("transaction_id", r.transactionId.toString()).delete();
                    idx++;
                } else {
                    realAmount += amount;
                    r.capital -= amount;
                    amount = -1;
                    plugin.dbm.query(PartialRecord.class).whereEq("transaction_id", r.transactionId.toString())
                            .update(r, "capital");
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
                plugin.dbm.query(BankAccount.class).whereEq("account_id", account.getAccountId())
                        .update(account, "loan", "loan_interest");
            }
            bank.capital += realAmount;
            plugin.dbm.query(BankRegistration.class).whereEq("bank_id", bank.bankId.toString())
                    .update(bank, "capital");
            plugin.dbm.log(TransactionType.REPAY).to(bank.bankId).from(player.getUniqueId())
                    .capital(realAmount).insert();
            plugin.eco.withdrawPlayer(player, realAmount);
        }
    }
}