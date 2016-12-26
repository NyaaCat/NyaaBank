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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        if (bank == null) throw new BadCommandException("user.deposit.bank_not_found");

        double totalDeposit = plugin.dbm.getTotalDeposit(bank.bankId, p.getUniqueId());
        if (withdrawAll) {
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
            plugin.dbm.log(TransactionType.WITHDRAW).from(bank.bankId).to(p.getUniqueId())
                    .capital(totalDeposit).insert();
            plugin.eco.depositPlayer(p, totalDeposit);
        } else {
            if (amount <= 0) throw new BadCommandException("user.withdraw.invalid_amount");
            if (amount > totalDeposit) throw new BadCommandException("user.withdraw.not_enough_deposit");
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
            plugin.dbm.log(TransactionType.WITHDRAW).from(bank.bankId).to(p.getUniqueId())
                    .capital(realAmount).insert();
            plugin.eco.depositPlayer(p, realAmount);
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
