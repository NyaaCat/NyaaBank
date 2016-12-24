package cat.nyaa.nyaabank;

import cat.nyaa.nyaabank.database.enums.BankStatus;
import cat.nyaa.nyaabank.database.enums.InterestType;
import cat.nyaa.nyaabank.database.enums.TransactionType;
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
import java.util.List;
import java.util.UUID;

public class CommandHandler extends CommandReceiver<NyaaBank> {
    @Override
    public String getHelpPrefix() {
        return "";
    }

    private final NyaaBank plugin;

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

    @SubCommand(value = "list", permission = "nb.list_self")
    public void listBanks(CommandSender sender, Arguments args) {
        if (sender.hasPermission("nb.list_all")) {
            String playerName = args.next();
            List<BankRegistration> banks;
            if (playerName == null) {
                banks = plugin.dbm.query(BankRegistration.class).select();
                msg(sender, "command.list.list_all");
            } else {
                UUID id = plugin.getServer().getOfflinePlayer(playerName).getUniqueId();
                banks = plugin.dbm.query(BankRegistration.class)
                        .whereEq("owner_id", id.toString())
                        .select();
                msg(sender, "command.list.list_player", playerName);
            }
            if (banks.size() <= 0) {
                msg(sender, "command.list.list_empty");
                return;
            }
            banks.sort((a, b) -> a.ownerId.compareTo(b.ownerId));
            for (BankRegistration r : banks) {
                OfflinePlayer p = plugin.getServer().getOfflinePlayer(r.ownerId);
                msg(sender, "command.list.list_item", r.name, p.getName(), r.bankId.toString());
            }
        } else {
            Player p = asPlayer(sender);
            List<BankRegistration> banks = plugin.dbm.query(BankRegistration.class)
                    .whereEq("owner_id", p.getUniqueId().toString())
                    .select();

            msg(sender, "command.list.list_player", p.getName());
            if (banks.size() <= 0) {
                msg(sender, "command.list.list_empty");
                return;
            }

            banks.sort((a, b) -> a.name.compareTo(b.name));
            for (BankRegistration r : banks) {
                msg(sender, "command.list.list_item", r.name, p.getName(), r.bankId.toString());
            }
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
        // TODO log
    }

    @SubCommand(value = "_check", permission = "nb.debug") // TODO: for debug only
    public void forceCheckPoint(CommandSender sender, Arguments args) {
        plugin.cycle.updateDatabaseInterests(System.currentTimeMillis(), plugin.cfg.interestCycle);
    }
}
