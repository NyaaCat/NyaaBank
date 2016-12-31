package cat.nyaa.nyaabank;

import cat.nyaa.nyaabank.database.enums.BankStatus;
import cat.nyaa.nyaabank.database.enums.InterestType;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.BankAccount;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.PartialRecord;
import cat.nyaa.utils.CommandReceiver;
import cat.nyaa.utils.Internationalization;
import cat.nyaa.utils.database.BaseDatabase;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;

public class CommandHandler extends CommandReceiver<NyaaBank> {
    @Override
    public String getHelpPrefix() {
        return "";
    }

    private final NyaaBank plugin;

    @SubCommand("bank")
    BankManagementCommands bankCommand;

    public CommandHandler(NyaaBank plugin, Internationalization i18n) {
        super(plugin, i18n);
        this.plugin = plugin;
    }

    @SubCommand(value = "reg", permission = "nb.create_bank")
    public void createBank(CommandSender sender, Arguments args) {
        String playerName = args.next();
        String bankName = args.next();
        double capital = args.nextDouble();

        if (playerName == null || bankName == null || capital < 0) {
            throw new BadCommandException("manual.reg.usage");
        }
        bankName = ChatColor.translateAlternateColorCodes('&', bankName);

        BaseDatabase.Query<BankRegistration> q = plugin.dbm.query(BankRegistration.class);
        if (q.whereEq("bank_name", bankName).count() > 0) {
            msg(sender, "command.reg.name_duplicate");
            return;
        }

        OfflinePlayer p = plugin.getServer().getPlayer(playerName);
        if (p == null) {
            p = plugin.getServer().getOfflinePlayer(playerName);
        }

        if (!plugin.eco.has(p, capital)) {
            msg(sender, "command.reg.not_enough_capital");
            return;
        }
        plugin.eco.withdrawPlayer(p, capital);
        BankRegistration reg = new BankRegistration();
        reg.bankId = UUID.randomUUID();
        reg.name = bankName;
        reg.ownerId = p.getUniqueId();
        reg.capital = capital;
        reg.registered_capital = capital;
        reg.establishDate = Instant.now();
        reg.status = BankStatus.ACTIVE;
        reg.interestType = InterestType.COMPOUND;
        reg.interestTypeNext = reg.interestType;
        reg.savingInterest = 0D;
        reg.savingInterestNext = reg.savingInterest;
        reg.debitInterest = 0D;
        reg.debitInterestNext = reg.debitInterest;
        q.insert(reg);
        msg(sender, "command.reg.established", reg.name, reg.bankId.toString());
    }

    @SubCommand(value = "top", permission = "nb.top")
    public void topBanks(CommandSender sender, Arguments args) {
        List<BankRegistration> l = plugin.dbm.query(BankRegistration.class).select();
        l.sort((a,b)->a.capital.compareTo(b.capital));
        for (int i = l.size();i>=1;i--) {
            BankRegistration b = l.get(i-1);
            msg(sender, "command.top.list_item", i, b.name, b.capital, b.getBankId());
        }
    }

    @SubCommand(value = "my", permission = "nb.list_my")
    public void listMyAccounts(CommandSender sender, Arguments args) {
        UUID pid = null;
        if (sender.hasPermission("nb.list_my_admin") && args.top() != null) {
            pid = plugin.getServer().getOfflinePlayer(args.next()).getUniqueId();
        }
        if (pid == null) pid = asPlayer(sender).getUniqueId();
        List<BankAccount> accounts = plugin.dbm.query(BankAccount.class)
                .whereEq("player_id", pid.toString()).select();
        List<PartialRecord> partials = plugin.dbm.query(PartialRecord.class)
                .whereEq("player_id", pid.toString()).select();

        Map<UUID, Double> deposit = new HashMap<>();
        Map<UUID, Double> loan = new HashMap<>();
        for (BankAccount b : accounts) {
            deposit.put(b.bankId, deposit.getOrDefault(b.bankId, 0D) + b.deposit + b.deposit_interest);
            loan.put(b.bankId, loan.getOrDefault(b.bankId, 0D) + b.loan + b.loan_interest);
        }
        for (PartialRecord p : partials) {
            if (p.type == TransactionType.DEPOSIT) {
                deposit.put(p.bankId, deposit.getOrDefault(p.bankId, 0D) + p.capital);
            } else if (p.type == TransactionType.LOAN) {
                loan.put(p.bankId, loan.getOrDefault(p.bankId, 0D) + p.capital);
            }
        }

        Set<UUID> bankIds = new HashSet<>();
        bankIds.addAll(deposit.keySet());
        bankIds.addAll(loan.keySet());
        for (UUID bankId : bankIds) {
            BankRegistration bank = plugin.dbm.getUniqueBank(bankId.toString());
            msg(sender, "command.list_my.list_item", bank.name,
                    deposit.getOrDefault(bankId, 0D), loan.getOrDefault(bankId, 0D));
        }
        if (bankIds.isEmpty()) {
            msg(sender, "command.list_my.empty_list");
        }
    }

    @SubCommand(value = "bankrupt", permission = "nb.force_bankrupt")
    public void forceBankrupt(CommandSender sender, Arguments args) {
        // TODO log
        if (args.top() == null) throw new BadCommandException();
        String type = args.next().toLowerCase();
        if (args.top() == null) throw new BadCommandException();
        String id = args.next();
        if ("player".equals(type)) {
            OfflinePlayer p = plugin.getServer().getOfflinePlayer(id);
            if (p == null || p.getUniqueId() == null) {
                throw new BadCommandException("command.bankrupt.player_not_found");
            }
            UUID pid = p.getUniqueId();
            List<PartialRecord> partials = plugin.dbm.query(PartialRecord.class)
                    .whereEq("player_id", pid.toString()).select();
            List<BankAccount> accounts = plugin.dbm.query(BankAccount.class)
                    .whereEq("player_id", pid.toString()).select();

            Map<UUID, BankRegistration> updatedBanks = new HashMap<>();
            for (PartialRecord r : partials) {
                BankRegistration bank = updatedBanks.get(r.bankId);
                if (bank == null) {
                    bank = plugin.dbm.getUniqueBank(r.getBankId());
                    updatedBanks.put(r.bankId, bank);
                }
                if (r.type == TransactionType.LOAN) {
                    plugin.eco.withdrawPlayer(p, r.capital);
                    bank.capital += r.capital;
                } else if (r.type == TransactionType.DEPOSIT) {
                    plugin.eco.depositPlayer(p, r.capital);
                    bank.capital -= r.capital;
                }
            }
            for (BankAccount r : accounts) {
                BankRegistration bank = updatedBanks.get(r.bankId);
                if (bank == null) {
                    bank = plugin.dbm.getUniqueBank(r.getBankId());
                    updatedBanks.put(r.bankId, bank);
                }
                double netDeposit = r.deposit + r.deposit_interest - r.loan - r.loan_interest;
                bank.capital -= netDeposit;
                if (netDeposit < 0) {
                    plugin.eco.withdrawPlayer(p, -netDeposit);
                } else {
                    plugin.eco.depositPlayer(p, netDeposit);
                }
            }

            plugin.dbm.query(PartialRecord.class).whereEq("player_id", pid.toString()).delete();
            plugin.dbm.query(BankAccount.class).whereEq("player_id", pid.toString()).delete();
            for (BankRegistration b : updatedBanks.values()) {
                plugin.dbm.query(BankRegistration.class)
                        .whereEq("bank_id", b.getBankId())
                        .update(b, "capital");
            }
        } else if ("bank".equals(type)) {
            // TODO make other codes check bankrupt state
            /* STEP 1: Force acquire all loan
             * STEP 2: Return all deposits
             * STEP 3: Clear vault and balance with banker
             * STEP 4: Update signs
             */
            BankRegistration bank = plugin.dbm.getUniqueBank(id);
            if (bank == null) throw new BadCommandException("command.bankrupt.bank_not_found");

            // STEP 1 & 2
            List<PartialRecord> partials = plugin.dbm.query(PartialRecord.class)
                    .whereEq("bank_id", bank.getBankId()).select();
            List<BankAccount> accounts = plugin.dbm.query(BankAccount.class)
                    .whereEq("bank_id", bank.getBankId()).select();
            for (PartialRecord r : partials) {
                if (r.type == TransactionType.LOAN) {
                    bank.capital += r.capital;
                    plugin.eco.withdrawPlayer(plugin.getServer().getOfflinePlayer(r.playerId), r.capital);
                } else if (r.type == TransactionType.DEPOSIT) {
                    bank.capital -= r.capital;
                    plugin.eco.depositPlayer(plugin.getServer().getOfflinePlayer(r.playerId), r.capital);
                }
            }
            for (BankAccount r : accounts) {
                double netDeposit = r.deposit + r.deposit_interest - r.loan - r.loan_interest;
                bank.capital -= netDeposit;
                if (netDeposit < 0) {
                    plugin.eco.withdrawPlayer(plugin.getServer().getOfflinePlayer(r.playerId), -netDeposit);
                } else {
                    plugin.eco.depositPlayer(plugin.getServer().getOfflinePlayer(r.playerId), netDeposit);
                }
            }
            plugin.dbm.query(PartialRecord.class).whereEq("bank_id", bank.getBankId()).delete();
            plugin.dbm.query(BankAccount.class).whereEq("bank_id", bank.getBankId()).delete();

            // STEP 3
            if (bank.capital >= 0) {
                plugin.eco.depositPlayer(plugin.getServer().getOfflinePlayer(bank.ownerId), bank.capital);
            } else {
                plugin.eco.withdrawPlayer(plugin.getServer().getOfflinePlayer(bank.ownerId), -bank.capital);
            }
            bank.capital = 0D;
            bank.status = BankStatus.BANKRUPT;
            plugin.dbm.query(BankRegistration.class).whereEq("bank_id", bank.getBankId()).update(bank);

            // STEP 4
            // TODO update signs

        } else {
            throw new BadCommandException();
        }
    }

    @SubCommand(value = "deposit", permission = "nb.deposit_cmd")
    public void commandDeposit(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        Double amount = args.nextDouble();
        String partialBankId = args.next();
        if (amount <= 0) throw new BadCommandException("user.deposit.invalid_amount");
        if (!plugin.eco.has(p, amount)) throw new BadCommandException("user.deposit.not_enough_money");
        BankRegistration bank = plugin.dbm.getUniqueBank(partialBankId);
        if (bank == null) throw new BadCommandException("user.deposit.bank_not_found");

        plugin.eco.withdrawPlayer(p, amount);
        PartialRecord partial = new PartialRecord();
        partial.transactionId = UUID.randomUUID();
        partial.bankId = bank.bankId;
        partial.playerId = p.getUniqueId();
        partial.capital = amount;
        partial.type = TransactionType.DEPOSIT;
        partial.startDate = Instant.now();
        plugin.dbm.query(PartialRecord.class).insert(partial);
        plugin.dbm.log(TransactionType.DEPOSIT)
                .from(p.getUniqueId())
                .to(bank.bankId)
                .capital(amount)
                .extra("{\"partialId\": \"%s\"}", partial.transactionId.toString())
                .insert();

        bank.capital += amount;
        plugin.dbm.query(BankRegistration.class)
                .whereEq("bank_id", bank.bankId)
                .update(bank, "capital");
    }

    @SubCommand(value = "withdraw", permission = "nb.withdraw_cmd")
    public void commandWithdraw(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        String amountS = args.top();
        if (amountS == null) throw new BadCommandException();
        double amount;
        boolean withdrawAll;
        if ("ALL".equals(amountS.toUpperCase())) {
            withdrawAll = true;
            amount = -1;
        } else {
            withdrawAll = false;
            amount = args.nextDouble();
        }
        String bankIdP = args.next();
        if (bankIdP == null) throw new BadCommandException();
        BankRegistration bank = plugin.dbm.getUniqueBank(bankIdP);
        if (bank == null) throw new BadCommandException("user.withdraw.bank_not_found");

        double totalDeposit = plugin.dbm.getTotalDeposit(bank.bankId, p.getUniqueId());
        if (withdrawAll) {
            if (totalDeposit >= bank.capital) throw new BadCommandException("user.withdraw.bank_run");
            BankAccount account = plugin.dbm.getAccount(bank.bankId, p.getUniqueId());
            account.deposit = 0D;
            account.deposit_interest = 0D;
            plugin.dbm.query(BankAccount.class)
                    .whereEq("account_id", account.accountId)
                    .update(account, "deposit", "deposit_interest");
            plugin.dbm.query(PartialRecord.class)
                    .whereEq("bank_id", bank.bankId.toString())
                    .whereEq("player_id", p.getUniqueId())
                    .whereEq("transaction_type", TransactionType.DEPOSIT.name())
                    .delete();
            bank.capital -= totalDeposit;
            plugin.dbm.query(BankRegistration.class).whereEq("bank_id", bank.bankId.toString())
                    .update(bank, "capital");
            plugin.dbm.log(TransactionType.WITHDRAW).from(bank.bankId).to(p.getUniqueId())
                    .capital(totalDeposit).insert();
            plugin.eco.depositPlayer(p, totalDeposit);
        } else {
            if (amount <= 0) throw new BadCommandException("user.withdraw.invalid_amount");
            if (amount > totalDeposit) throw new BadCommandException("user.withdraw.not_enough_deposit");
            if (amount >= bank.capital) throw new BadCommandException("user.withdraw.bank_run");
            double realAmount = 0;
            List<PartialRecord> l = plugin.dbm.getPartialRecords(bank.bankId, p.getUniqueId(), TransactionType.DEPOSIT);
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
                BankAccount account = plugin.dbm.getAccount(bank.bankId, p.getUniqueId());
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
            plugin.dbm.log(TransactionType.WITHDRAW).from(bank.bankId).to(p.getUniqueId())
                    .capital(realAmount).insert();
            plugin.eco.depositPlayer(p, realAmount);
        }
    }

    @SubCommand(value = "loan", permission = "nb.loan_cmd")
    public void commandLoan(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        double amount = args.nextDouble();
        String partialId = args.next();
        if (amount <= 0) throw new BadCommandException("user.loan.invalid_amount");
        if (partialId == null) throw new BadCommandException();
        BankRegistration bank = plugin.dbm.getUniqueBank(partialId);
        if (bank == null) throw new BadCommandException("user.loan.bank_not_found");
        if (bank.capital <= amount) throw new BadCommandException("user.loan.not_enough_money_bank");
        BankAccount account = plugin.dbm.getAccount(bank.bankId, p.getUniqueId());
        if (account != null && (account.loan > 0 || account.loan_interest > 0)) {
            throw new BadCommandException("user.loan.has_loan");
        }
        if (plugin.dbm.query(PartialRecord.class)
                .whereEq("player_id", p.getUniqueId().toString())
                .whereEq("bank_id", bank.getBankId())
                .whereEq("transaction_type", TransactionType.LOAN.name())
                .count() > 0) {
            throw new BadCommandException("user.loan.has_loan");
        }
        PartialRecord partial = new PartialRecord();
        partial.transactionId = UUID.randomUUID();
        partial.bankId = bank.bankId;
        partial.playerId = p.getUniqueId();
        partial.capital = amount;
        partial.type = TransactionType.LOAN;
        partial.startDate = Instant.now();
        plugin.dbm.query(PartialRecord.class).insert(partial);
        bank.capital -= amount;
        plugin.dbm.query(BankRegistration.class).whereEq("bank_id", bank.getBankId()).update(bank, "capital");
        plugin.eco.depositPlayer(p, amount);
        plugin.dbm.log(TransactionType.LOAN)
                .to(p.getUniqueId())
                .from(bank.bankId)
                .capital(amount)
                .extra("{\"partialId\": \"%s\"}", partial.transactionId.toString())
                .insert();
    }

    @SubCommand(value = "repay", permission = "nb.repay_cmd")
    public void commandRepay(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        String amountS = args.top();
        if (amountS == null) throw new BadCommandException();
        double amount;
        boolean repayAll;
        if ("ALL".equals(amountS.toUpperCase())) {
            repayAll = true;
            amount = -1;
        } else {
            repayAll = false;
            amount = args.nextDouble();
        }
        String bankIdP = args.next();
        if (bankIdP == null) throw new BadCommandException();
        BankRegistration bank = plugin.dbm.getUniqueBank(bankIdP);
        if (bank == null) throw new BadCommandException("user.repay.bank_not_found");

        double totalLoan = plugin.dbm.getTotalLoan(bank.bankId, p.getUniqueId());
        if (repayAll) {
            if (!plugin.eco.has(p, totalLoan)) throw new BadCommandException("user.repay.not_enough_money");
            BankAccount account = plugin.dbm.getAccount(bank.bankId, p.getUniqueId());
            account.loan = 0D;
            account.loan_interest = 0D;
            plugin.dbm.query(BankAccount.class)
                    .whereEq("account_id", account.accountId)
                    .update(account, "loan", "loan_interest");
            plugin.dbm.query(PartialRecord.class)
                    .whereEq("bank_id", bank.bankId.toString())
                    .whereEq("player_id", p.getUniqueId())
                    .whereEq("transaction_type", TransactionType.LOAN.name())
                    .delete();
            bank.capital += totalLoan;
            plugin.dbm.query(BankRegistration.class).whereEq("bank_id", bank.bankId.toString())
                    .update(bank, "capital");
            plugin.dbm.log(TransactionType.REPAY).to(bank.bankId).from(p.getUniqueId())
                    .capital(totalLoan).insert();
            plugin.eco.withdrawPlayer(p, totalLoan);
        } else {
            if (amount <= 0) throw new BadCommandException("user.repay.invalid_amount");
            if (amount > totalLoan) throw new BadCommandException("user.repay.no_need_to_pay");
            if (!plugin.eco.has(p, amount)) throw new BadCommandException("user.repay.not_enough_money");
            double realAmount = 0;
            List<PartialRecord> l = plugin.dbm.getPartialRecords(bank.bankId, p.getUniqueId(), TransactionType.LOAN);
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
                BankAccount account = plugin.dbm.getAccount(bank.bankId, p.getUniqueId());
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
            plugin.dbm.log(TransactionType.REPAY).to(bank.bankId).from(p.getUniqueId())
                    .capital(realAmount).insert();
            plugin.eco.withdrawPlayer(p, realAmount);
        }
    }

    @SubCommand(value = "_check", permission = "nb.debug") // TODO: for debug only
    public void forceCheckPoint(CommandSender sender, Arguments args) {
        plugin.cycle.updateDatabaseInterests(System.currentTimeMillis(), plugin.cfg.interestCycle);
    }

    @SubCommand(value = "_benchmark", permission = "nb.debug") // TODO: for debug only
    public void checkPointDbBenchmark(CommandSender sender, Arguments args) { // WARN: will destroy database
        final int NUM_BANK = 100;
        final int NUM_ACCOUT = 500;
        sender.sendMessage(String.format("#Bank: %d\n#Account per bank: %d", NUM_BANK, NUM_ACCOUT));
        sender.sendMessage("Inserting data ...");
        long startTime = System.currentTimeMillis();
        plugin.dbm.disableAutoCommit();
        plugin.dbm.query(BankRegistration.class).delete();
        plugin.dbm.query(PartialRecord.class).delete();
        plugin.dbm.query(BankAccount.class).delete();
        List<BankRegistration> banks = new ArrayList<>();

        BaseDatabase.Query<BankRegistration> q = plugin.dbm.query(BankRegistration.class);
        for (int i = 0; i < NUM_BANK; i++) {
            BankRegistration reg = new BankRegistration();
            reg.bankId = UUID.randomUUID();
            reg.name = "Test bank " + Integer.toString(i);
            reg.ownerId = UUID.randomUUID();
            reg.capital = 10000D;
            reg.registered_capital = 10000D;
            reg.establishDate = Instant.now();
            reg.status = BankStatus.ACTIVE;
            reg.interestType = InterestType.COMPOUND;
            reg.interestTypeNext = reg.interestType;
            reg.savingInterest = 0.1D;
            reg.savingInterestNext = reg.savingInterest;
            reg.debitInterest = 0.1D;
            reg.debitInterestNext = reg.debitInterest;
            q.insert(reg);
            banks.add(reg);
        }

        BaseDatabase.Query<PartialRecord> q2 = plugin.dbm.query(PartialRecord.class);
        for (BankRegistration b : banks) {
            for (int i = 0; i < NUM_ACCOUT; i++) {
                PartialRecord partial = new PartialRecord();
                partial.transactionId = UUID.randomUUID();
                partial.bankId = b.bankId;
                partial.playerId = UUID.randomUUID();
                partial.capital = 1000D;
                partial.type = TransactionType.DEPOSIT;
                partial.startDate = Instant.now();
                q2.insert(partial);
            }
        }
        plugin.dbm.enableAutoCommit();
        long endTime = System.currentTimeMillis();
        sender.sendMessage(String.format("Finished in %.2fs", (endTime - startTime) / 1000D));

        sender.sendMessage("Round #1 ...");
        startTime = System.currentTimeMillis();
        plugin.cycle.updateDatabaseInterests(System.currentTimeMillis(), plugin.cfg.interestCycle);
        endTime = System.currentTimeMillis();
        sender.sendMessage(String.format("Finished in %.2fs", (endTime - startTime) / 1000D));

        sender.sendMessage("Round #2 ...");
        startTime = System.currentTimeMillis();
        plugin.cycle.updateDatabaseInterests(System.currentTimeMillis(), plugin.cfg.interestCycle);
        endTime = System.currentTimeMillis();
        sender.sendMessage(String.format("Finished in %.2fs", (endTime - startTime) / 1000D));
    }
}
