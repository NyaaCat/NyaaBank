package cat.nyaa.nyaabank;

import cat.nyaa.nyaabank.database.enums.BankStatus;
import cat.nyaa.nyaabank.database.enums.InterestType;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.BankAccount;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.PartialRecord;
import cat.nyaa.nyaabank.database.tables.SignRegistration;
import cat.nyaa.nyaabank.signs.SignHelper;
import cat.nyaa.utils.CommandReceiver;
import cat.nyaa.utils.Internationalization;
import cat.nyaa.utils.database.BaseDatabase;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.*;

import static cat.nyaa.nyaabank.database.enums.TransactionType.REPAY;
import static cat.nyaa.nyaabank.database.enums.TransactionType.VAULT_CHANGE;
import static cat.nyaa.nyaabank.database.enums.TransactionType.WITHDRAW;

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
        double savingInter = args.nextDouble();
        double debitInter = args.nextDouble();
        InterestType interestType = args.nextEnum(InterestType.class);

        if (playerName == null || bankName == null || capital < 0) {
            throw new BadCommandException("manual.reg.usage");
        }
        bankName = ChatColor.translateAlternateColorCodes('&', bankName);

        List<BankRegistration> q = plugin.dbm.query(BankRegistration.class).select();
        for (BankRegistration b : q) {
            if (SignHelper.stringEqIgnoreColor(bankName, b.name, true)) {
                msg(sender, "command.reg.name_duplicate");
                return;
            }
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
        reg.idNumber = plugin.dbm.getNextIdNumber(); // TODO print idNumber EVERYWHERE
        reg.name = bankName;
        reg.ownerId = p.getUniqueId();
        reg.capital = capital;
        reg.registered_capital = capital;
        reg.establishDate = Instant.now();
        reg.status = BankStatus.ACTIVE;
        reg.interestType = interestType;
        reg.interestTypeNext = interestType;
        reg.savingInterest = savingInter;
        reg.savingInterestNext = savingInter;
        reg.debitInterest = debitInter;
        reg.debitInterestNext = debitInter;
        plugin.dbm.query(BankRegistration.class).insert(reg);
        msg(sender, "command.reg.established", reg.idNumber, reg.name, reg.bankId.toString());
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
                .whereEq(BankAccount.N_PLAYER_ID, pid.toString()).select();
        List<PartialRecord> partials = plugin.dbm.query(PartialRecord.class)
                .whereEq(PartialRecord.N_PLAYER_ID, pid.toString()).select();

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
        if (args.top() == null) throw new BadCommandException();
        String type = args.next().toLowerCase();
        if (args.top() == null) throw new BadCommandException();
        if ("player".equals(type)) {
            String id = args.next();
            OfflinePlayer p = plugin.getServer().getOfflinePlayer(id);
            if (p == null || p.getUniqueId() == null) {
                throw new BadCommandException("command.bankrupt.player_not_found");
            }
            UUID pid = p.getUniqueId();
            List<PartialRecord> partials = plugin.dbm.query(PartialRecord.class)
                    .whereEq(PartialRecord.N_PLAYER_ID, pid.toString()).select();
            List<BankAccount> accounts = plugin.dbm.query(BankAccount.class)
                    .whereEq(BankAccount.N_PLAYER_ID, pid.toString()).select();

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
                    plugin.dbm.log(REPAY).from(pid).to(bank.bankId).capital(r.capital)
                            .extra("partialId", r.getTransactionId())
                            .extra("bankrupt", "PLAYER").insert();
                } else if (r.type == TransactionType.DEPOSIT) {
                    plugin.eco.depositPlayer(p, r.capital);
                    bank.capital -= r.capital;
                    plugin.dbm.log(WITHDRAW).to(pid).from(bank.bankId).capital(r.capital)
                            .extra("partialId", r.getTransactionId())
                            .extra("bankrupt", "PLAYER").insert();
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
                    plugin.dbm.log(REPAY).from(pid).to(bank.bankId).capital(-netDeposit)
                            .extra("bankrupt", "PLAYER").insert();
                } else {
                    plugin.eco.depositPlayer(p, netDeposit);
                    plugin.dbm.log(WITHDRAW).to(pid).from(bank.bankId).capital(netDeposit)
                            .extra("bankrupt", "PLAYER").insert();
                }
            }

            plugin.dbm.query(PartialRecord.class).whereEq(PartialRecord.N_PLAYER_ID, pid.toString()).delete();
            plugin.dbm.query(BankAccount.class).whereEq(BankAccount.N_PLAYER_ID, pid.toString()).delete();
            for (BankRegistration b : updatedBanks.values()) {
                plugin.dbm.query(BankRegistration.class)
                        .whereEq(BankRegistration.N_BANK_ID, b.getBankId())
                        .update(b, BankRegistration.N_CAPITAL);
            }
        } else if ("bank".equals(type)) {
            /* STEP 1: Force acquire all loan
             * STEP 2: Return all deposits
             * STEP 3: Clear vault and balance with banker
             * STEP 4: Update signs
             */
            BankRegistration bank = plugin.dbm.getBankByIdNumber(args.nextInt());
            if (bank == null) throw new BadCommandException("command.bankrupt.bank_not_found");

            // STEP 1 & 2
            List<PartialRecord> partials = plugin.dbm.query(PartialRecord.class)
                    .whereEq(PartialRecord.N_BANK_ID, bank.getBankId()).select();
            List<BankAccount> accounts = plugin.dbm.query(BankAccount.class)
                    .whereEq(BankAccount.N_BANK_ID, bank.getBankId()).select();
            for (PartialRecord r : partials) {
                if (r.type == TransactionType.LOAN) {
                    bank.capital += r.capital;
                    plugin.eco.withdrawPlayer(plugin.getServer().getOfflinePlayer(r.playerId), r.capital);
                    plugin.dbm.log(REPAY).from(r.playerId).to(bank.bankId).capital(r.capital)
                            .extra("partialId", r.getTransactionId())
                            .extra("bankrupt", "BANK").insert();
                } else if (r.type == TransactionType.DEPOSIT) {
                    bank.capital -= r.capital;
                    plugin.eco.depositPlayer(plugin.getServer().getOfflinePlayer(r.playerId), r.capital);
                    plugin.dbm.log(WITHDRAW).to(r.playerId).from(bank.bankId).capital(r.capital)
                            .extra("partialId", r.getTransactionId())
                            .extra("bankrupt", "BANK").insert();
                }
            }
            for (BankAccount r : accounts) {
                double netDeposit = r.deposit + r.deposit_interest - r.loan - r.loan_interest;
                bank.capital -= netDeposit;
                if (netDeposit < 0) {
                    plugin.eco.withdrawPlayer(plugin.getServer().getOfflinePlayer(r.playerId), -netDeposit);
                    plugin.dbm.log(REPAY).from(r.playerId).to(bank.bankId).capital(-netDeposit)
                            .extra("bankrupt", "BANK").insert();
                } else {
                    plugin.eco.depositPlayer(plugin.getServer().getOfflinePlayer(r.playerId), netDeposit);
                    plugin.dbm.log(WITHDRAW).to(r.playerId).from(bank.bankId).capital(netDeposit)
                            .extra("bankrupt", "BANK").insert();
                }
            }
            plugin.dbm.query(PartialRecord.class).whereEq(PartialRecord.N_BANK_ID, bank.getBankId()).delete();
            plugin.dbm.query(BankAccount.class).whereEq(BankAccount.N_BANK_ID, bank.getBankId()).delete();

            // STEP 3
            plugin.dbm.log(VAULT_CHANGE).from(bank.ownerId).to(bank.bankId).capital(-bank.capital)
                    .extra("bankrupt", "BANK").insert();
            if (bank.capital >= 0) {
                plugin.eco.depositPlayer(plugin.getServer().getOfflinePlayer(bank.ownerId), bank.capital);
            } else {
                plugin.eco.withdrawPlayer(plugin.getServer().getOfflinePlayer(bank.ownerId), -bank.capital);
            }
            bank.capital = 0D;
            bank.status = BankStatus.BANKRUPT;
            plugin.dbm.query(BankRegistration.class).whereEq(BankRegistration.N_BANK_ID, bank.getBankId()).update(bank);

            // STEP 4
            SignHelper.batchUpdateSign(plugin, bank);

        } else {
            throw new BadCommandException();
        }
    }

    @SubCommand(value = "deposit", permission = "nb.deposit_cmd")
    public void commandDeposit(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        Double amount = args.nextDouble();
        if (amount <= 0) throw new BadCommandException("user.deposit.invalid_amount");
        if (!plugin.eco.has(p, amount)) throw new BadCommandException("user.deposit.not_enough_money");
        BankRegistration bank = plugin.dbm.getBankByIdNumber(args.nextInt());
        if (bank == null) throw new BadCommandException("user.deposit.bank_not_found");

        try {
            CommonAction.deposit(plugin, p, bank, amount);
        } catch (CommonAction.TransactionException ex) {
            throw new BadCommandException(ex.getMessage());
        }
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
            args.next();
        } else {
            withdrawAll = false;
            amount = args.nextDouble();
        }
        BankRegistration bank = plugin.dbm.getBankByIdNumber(args.nextInt());
        if (bank == null) throw new BadCommandException("user.withdraw.bank_not_found");

        try {
            CommonAction.withdraw(plugin, p, bank, amount, withdrawAll);
        } catch (CommonAction.TransactionException ex) {
            throw new BadCommandException(ex.getMessage());
        }
    }

    @SubCommand(value = "loan", permission = "nb.loan_cmd")
    public void commandLoan(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        double amount = args.nextDouble();
        if (amount <= 0) throw new BadCommandException("user.loan.invalid_amount");
        BankRegistration bank = plugin.dbm.getBankByIdNumber(args.nextInt());
        if (bank == null) throw new BadCommandException("user.loan.bank_not_found");

        try {
            CommonAction.loan(plugin, p, bank, amount);
        } catch (CommonAction.TransactionException ex) {
            throw new BadCommandException(ex.getMessage());
        }
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
            args.next();
        } else {
            repayAll = false;
            amount = args.nextDouble();
        }
        BankRegistration bank = plugin.dbm.getBankByIdNumber(args.nextInt());
        if (bank == null) throw new BadCommandException("user.repay.bank_not_found");

        try {
            CommonAction.repay(plugin, p, bank, amount, repayAll);
        } catch (CommonAction.TransactionException ex) {
            throw new BadCommandException(ex.getMessage());
        }
    }

    @SubCommand(value = "reload", permission = "nb.reload")
    public void commandReload(CommandSender sender, Arguments args) {
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.doReload();
                sender.sendMessage(I18n._("command.reload.complete"));
            }
        }.runTaskLater(plugin, 1L);
    }

    @SubCommand(value = "_check", permission = "nb.debug") // for debug only
    public void forceCheckPoint(CommandSender sender, Arguments args) {
        plugin.cycle.updateDatabaseInterests(plugin.cycle.getNextCheckpoint(), plugin.cfg.interestCycle);
        SignHelper.batchUpdateSign(plugin, plugin.dbm.query(SignRegistration.class).select());
    }

    @SubCommand(value = "_benchmark", permission = "nb.debug") // for debug only
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
            reg.idNumber = (long)i;
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
        plugin.dbm.query(BankRegistration.class).delete();
        plugin.dbm.query(PartialRecord.class).delete();
        plugin.dbm.query(BankAccount.class).delete();
    }
}
