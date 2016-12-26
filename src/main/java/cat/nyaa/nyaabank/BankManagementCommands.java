package cat.nyaa.nyaabank;

import cat.nyaa.nyaabank.database.enums.InterestType;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.BankAccount;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.PartialRecord;
import cat.nyaa.utils.CommandReceiver;
import cat.nyaa.utils.Internationalization;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BankManagementCommands extends CommandReceiver<NyaaBank> {
    private final NyaaBank plugin;

    public BankManagementCommands(Object plugin, Internationalization i18n) {
        super((NyaaBank) plugin, i18n);
        this.plugin = (NyaaBank) plugin;
    }

    @Override
    public String getHelpPrefix() {
        return "bank";
    }

    @SubCommand(value = "list", permission = "nb.bank_list")
    public void listBanks(CommandSender sender, Arguments args) {
        if (sender.hasPermission("nb.bank_list_admin")) {  // OPs
            String playerName = args.next();
            List<BankRegistration> banks;
            if (playerName == null) {
                banks = plugin.dbm.query(BankRegistration.class).select();
                msg(sender, "command.bank_list.list_all");
            } else {
                UUID id = plugin.getServer().getOfflinePlayer(playerName).getUniqueId();
                banks = plugin.dbm.query(BankRegistration.class)
                        .whereEq("owner_id", id.toString())
                        .select();
                msg(sender, "command.bank_list.list_player", playerName);
            }
            if (banks.size() <= 0) {
                msg(sender, "command.bank_list.list_empty");
                return;
            }
            banks.sort((a, b) -> a.ownerId.compareTo(b.ownerId));
            for (BankRegistration r : banks) {
                OfflinePlayer p = plugin.getServer().getOfflinePlayer(r.ownerId);
                msg(sender, "command.bank_list.list_item", r.name, p.getName(), r.bankId.toString());
            }
        } else {   // players
            Player p = asPlayer(sender);
            List<BankRegistration> banks = plugin.dbm.query(BankRegistration.class)
                    .whereEq("owner_id", p.getUniqueId().toString())
                    .select();

            msg(sender, "command.bank_list.list_player", p.getName());
            if (banks.size() <= 0) {
                msg(sender, "command.bank_list.list_empty");
                return;
            }

            banks.sort((a, b) -> a.name.compareTo(b.name));
            for (BankRegistration r : banks) {
                msg(sender, "command.bank_list.list_item", r.name, p.getName(), r.bankId.toString());
            }
        }
    }

    @SubCommand(value = "info", permission = "nb.bank_info")
    public void bankInfo(CommandSender sender, Arguments args) {
        if (args.top() == null) throw new BadCommandException();
        BankRegistration bank = plugin.dbm.getUniqueBank(args.next());
        if (bank == null) throw new BadCommandException("nb.bank_info.no_such_bank");

        if (sender instanceof Player) {
            if (!((Player) sender).getUniqueId().equals(bank.ownerId)) {
                if (!sender.hasPermission("nb.bank_info_admin")) {
                    throw new BadCommandException("nb.bank_info.only_self");
                }
            }
        }

        String ownerName = "UNKNOWN";
        if (Bukkit.getPlayer(bank.ownerId) != null) {
            if (Bukkit.getPlayer(bank.ownerId).getName() != null) {
                ownerName = Bukkit.getPlayer(bank.ownerId).getName();
            }
        }
        msg(sender, "command.bank_info.info",
                bank.name,
                bank.bankId.toString(),
                ownerName,
                bank.ownerId.toString(),
                bank.establishDate.toString(),
                bank.status.name(),
                bank.registered_capital,
                bank.capital,
                bank.savingInterest,
                bank.debitInterest,
                bank.interestType.name(),
                bank.savingInterestNext,
                bank.debitInterestNext,
                bank.interestTypeNext.name());
    }

    @SubCommand(value = "interest", permission = "nb.bank_interest")
    public void changeInterestInfo(CommandSender sender, Arguments args) {
        String bankId = args.next();
        String op = args.next();
        if (bankId == null || op == null) throw new BadCommandException();

        BankRegistration bank = plugin.dbm.getUniqueBank(bankId);
        if (bank == null) throw new BadCommandException("nb.bank_interest.no_such_bank");
        if (sender instanceof Player) {
            if (!((Player) sender).getUniqueId().equals(bank.ownerId)) {
                if (!sender.hasPermission("nb.bank_interest_admin")) {
                    throw new BadCommandException("nb.bank_interest.only_self");
                }
            }
        }

        op = op.toUpperCase();
        switch (op) {
            case "SAVING": {
                double newSaving = args.nextDouble();
                if (plugin.cfg.savingInterestLimitEnabled) {
                    if (plugin.cfg.savingInterestLimitMax < newSaving ||
                            plugin.cfg.savingInterestLimitMin > newSaving) {
                        throw new BadCommandException("nb.bank_interest.rate_out_of_range",
                                plugin.cfg.savingInterestLimitMin,
                                plugin.cfg.savingInterestLimitMax);
                    }
                }
                bank.savingInterestNext = newSaving;
                plugin.dbm.query(BankRegistration.class)
                        .whereEq("bank_id", bank.bankId.toString())
                        .update(bank, "interest_rate_saving_next");
                break;
            }
            case "LOAN": {
                double newLoan = args.nextDouble();
                if (plugin.cfg.debitInterestLimitEnabled) {
                    if (plugin.cfg.debitInterestLimitMax < newLoan ||
                            plugin.cfg.debitInterestLimitMin > newLoan) {
                        throw new BadCommandException("nb.bank_interest.rate_out_of_range",
                                plugin.cfg.debitInterestLimitMin,
                                plugin.cfg.debitInterestLimitMax);
                    }
                }
                bank.debitInterestNext = newLoan;
                plugin.dbm.query(BankRegistration.class)
                        .whereEq("bank_id", bank.bankId.toString())
                        .update(bank, "interest_rate_debit_next");
                break;
            }
            case "TYPE": {
                InterestType newType = args.nextEnum(InterestType.class);
                bank.interestTypeNext = newType;
                plugin.dbm.query(BankRegistration.class)
                        .whereEq("bank_id", bank.bankId.toString())
                        .update(bank, "interest_type_next");
                break;
            }
            default:
                throw new BadCommandException();
        }
    }

    @SubCommand(value = "customers", permission = "nb.bank_customers")
    public void listCustomers(CommandSender sender, Arguments args) {
        String bankId = args.next();
        if (bankId == null || "".equals(bankId)) throw new BadCommandException();

        BankRegistration bank = plugin.dbm.getUniqueBank(bankId);
        if (bank == null) throw new BadCommandException("nb.bank_customers.no_such_bank");
        if (sender instanceof Player) {
            if (!((Player) sender).getUniqueId().equals(bank.ownerId)) {
                if (!sender.hasPermission("nb.bank_customers_admin")) {
                    throw new BadCommandException("nb.bank_customers.only_self");
                }
            }
        }

        Map<UUID, BankAccount> accounts = new HashMap<>();
        Multimap<UUID, PartialRecord> partials = HashMultimap.create();
        for (BankAccount a : plugin.dbm.query(BankAccount.class)
                .whereEq("bank_id", bank.bankId).select()) {
            if (accounts.containsKey(a.playerId)) {
                plugin.getLogger().severe(String.format("Multiple accounts bank-id=%s, player-id=%s",
                        bank.bankId.toString(), a.playerId));
            }
            accounts.put(a.playerId, a);
        }
        for (PartialRecord p : plugin.dbm.query(PartialRecord.class)
                .whereEq("bank_id", bank.bankId).select()) {
            partials.put(p.playerId, p);
        }

        List<UUID> ids = Stream.concat(accounts.keySet().stream(), partials.keySet().stream())
                .distinct().sorted().collect(Collectors.toList());
        for (UUID playerId : ids) {
            double saving = 0;
            if (accounts.containsKey(playerId))
                saving += accounts.get(playerId).deposit + accounts.get(playerId).deposit_interest;
            for (PartialRecord p : partials.get(playerId))
                saving += p.type == TransactionType.DEPOSIT ? p.capital : 0;
            double loan = 0;
            if (accounts.containsKey(playerId))
                loan += accounts.get(playerId).loan + accounts.get(playerId).loan_interest;
            for (PartialRecord p : partials.get(playerId))
                loan += p.type == TransactionType.LOAN ? p.capital : 0;
            String id = playerId.toString();
            if (Bukkit.getPlayer(playerId) != null && Bukkit.getPlayer(playerId).getName() != null) {
                id = Bukkit.getPlayer(playerId).getName();
            }
            msg(sender, "command.bank_customers.customer_record", id, saving, loan);
        }
        if (ids.size() <= 0) {
            msg(sender, "command.bank_customers.no_customer");
        }
    }
}
