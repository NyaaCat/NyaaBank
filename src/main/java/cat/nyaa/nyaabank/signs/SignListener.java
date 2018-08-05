package cat.nyaa.nyaabank.signs;

import cat.nyaa.nyaabank.CommonAction;
import cat.nyaa.nyaabank.I18n;
import cat.nyaa.nyaabank.NyaaBank;
import cat.nyaa.nyaabank.database.enums.BankStatus;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.SignRegistration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import static cat.nyaa.nyaabank.signs.SignHelper.*;

public class SignListener implements Listener{
    public final static String SIGN_MAGIC_FALLBACK = "[BANK]";
    private final ChatInputCallbacks callbacks;
    private final NyaaBank plugin;
    public SignListener(NyaaBank plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        callbacks = new ChatInputCallbacks(plugin);
    }

    /**
     * Apply commission fee, move from player to bank.
     * amount can be negative
     */
    private void applyCommission(Player player, BankRegistration bank, double amount) {
        OfflinePlayer banker = plugin.getServer().getOfflinePlayer(bank.ownerId);
        if (amount > 0) {
            plugin.eco.withdrawPlayer(player, amount);
            plugin.eco.depositPlayer(banker, amount);
            plugin.dbm.log(TransactionType.COMMISSION).from(player.getUniqueId()).to(bank.bankId)
                    .capital(amount).insert();
        } else if (amount < 0) {
            plugin.eco.depositPlayer(player, -amount);
            plugin.eco.withdrawPlayer(banker, amount);
            plugin.dbm.log(TransactionType.COMMISSION).from(player.getUniqueId()).to(bank.bankId)
                    .capital(amount).insert();
        } // do nothing if zero commission
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRightClickSign(PlayerInteractEvent ev) {
        if (!ev.hasBlock()) return;
        if (ev.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Material blockType = ev.getClickedBlock().getType();
        if (blockType != Material.SIGN && blockType != Material.WALL_SIGN) return;
        Sign sign = (Sign) ev.getClickedBlock().getState();
        String magicLine = ChatColor.stripColor(sign.getLine(0));
        if (stringEqIgnoreColor(magicLine, plugin.cfg.signMagic, false) ||
                stringEqIgnoreColor(magicLine, SIGN_MAGIC_FALLBACK, false)) {
            Player p = ev.getPlayer();
            if (callbacks.callbacks.containsKey(p.getUniqueId())) return;
            SignRegistration sr = SignHelper.getSign(plugin, ev.getClickedBlock().getLocation());
            if (sr == null) {
                p.sendMessage(I18n.format("user.sign.invalid_sign"));
                return;
            }
            BankRegistration bank = plugin.dbm.getUniqueBank(sr.bankId.toString());
            if (bank == null) {
                p.sendMessage(I18n.format("user.sign.invalid_sign"));
                return;
            }
            if (bank.status == BankStatus.BANKRUPT) {
                p.sendMessage(I18n.format("user.sign.bankrupted"));
                return;
            }
            if (sr.type == TransactionType.LOAN && sr.loanAmount <= 0) {
                p.sendMessage(I18n.format("user.sign.invalid_sign"));
                return;
            }
            switch (sr.type) {
                case DEPOSIT:
                    if (!p.hasPermission("nb.deposit")) {
                        p.sendMessage(I18n.format("user.sign.use_no_permission"));
                        break;
                    }
                    p.sendMessage(I18n.format("user.sign.input_prompt_deposit", plugin.cfg.signTimeout));
                    callbacks.register(ev.getPlayer().getUniqueId(),
                            new ChatInputCallbacks.InputCallback() {
                                @Override
                                public void onDoubleInput(Player p, double input, boolean isAll) {
                                    try {
                                        if (isAll) throw new CommonAction.TransactionException("user.sign.invalid_number");
                                        CommonAction.deposit(plugin, p, bank, input);
                                        p.sendMessage(I18n.format("user.sign.input_accepted"));
                                    } catch (CommonAction.TransactionException ex) {
                                        p.sendMessage(I18n.format(ex.getMessage()));
                                    }
                                }
                            });
                    break;
                case WITHDRAW:
                    if (!p.hasPermission("nb.withdraw")) {
                        p.sendMessage(I18n.format("user.sign.use_no_permission"));
                        break;
                    }
                    p.sendMessage(I18n.format("user.sign.input_prompt_withdraw", plugin.cfg.signTimeout));
                    callbacks.register(ev.getPlayer().getUniqueId(),
                            new ChatInputCallbacks.InputCallback() {
                                @Override
                                public void onDoubleInput(Player p, double input, boolean isAll) {
                                    try {
                                        CommonAction.withdraw(plugin, p, bank, input, isAll);
                                        applyCommission(p, bank, sr.commissionFee);
                                        p.sendMessage(I18n.format("user.sign.input_accepted"));

                                    } catch (CommonAction.TransactionException ex) {
                                        p.sendMessage(I18n.format(ex.getMessage()));
                                    }
                                }
                            });
                    break;
                case LOAN:
                    if (!p.hasPermission("nb.loan")) {
                        p.sendMessage(I18n.format("user.sign.use_no_permission"));
                        break;
                    }
                    p.sendMessage(I18n.format("user.sign.input_prompt_loan", plugin.cfg.signTimeout));
                    callbacks.register(ev.getPlayer().getUniqueId(),
                            new ChatInputCallbacks.InputCallback() {
                                @Override
                                public void onDoubleInput(Player p, double input, boolean isAll) {
                                    try {
                                        if (!isAll) throw new CommonAction.TransactionException("user.sign.loan_cancelled");
                                        CommonAction.loan(plugin, p, bank, sr.loanAmount);
                                        p.sendMessage(I18n.format("user.sign.input_accepted"));
                                    } catch (CommonAction.TransactionException ex) {
                                        p.sendMessage(I18n.format(ex.getMessage()));
                                    }
                                }
                            });
                    break;
                case REPAY:
                    if (!p.hasPermission("nb.repay")) {
                        p.sendMessage(I18n.format("user.sign.use_no_permission"));
                        break;
                    }
                    p.sendMessage(I18n.format("user.sign.input_prompt_repay", plugin.cfg.signTimeout));
                    callbacks.register(ev.getPlayer().getUniqueId(),
                            new ChatInputCallbacks.InputCallback() {
                                @Override
                                public void onDoubleInput(Player p, double input, boolean isAll) {
                                    try {
                                        CommonAction.repay(plugin, p, bank, input, isAll);
                                        applyCommission(p, bank, sr.commissionFee);
                                        p.sendMessage(I18n.format("user.sign.input_accepted"));
                                    } catch (CommonAction.TransactionException ex) {
                                        p.sendMessage(I18n.format(ex.getMessage()));
                                    }
                                }
                            });
                    break;
                default:
                    p.sendMessage(I18n.format("user.sign.invalid_sign"));
                    return; // do not update if an invalid sign
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    SignHelper.updateSignBlock(plugin, ev.getClickedBlock().getLocation(), sr);
                }
            }.runTaskLater(plugin, 1L); // update block after click;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCreateSign(SignChangeEvent ev) {
        if (!ev.getPlayer().hasPermission("nb.sign_create")) return;
        String firstLine = ev.getLine(0);
        if (!SignHelper.stringEqIgnoreColor(firstLine, SIGN_MAGIC_FALLBACK, true) &&
                !SignHelper.stringEqIgnoreColor(firstLine, plugin.cfg.signMagic, true))
            return; // not a bank sign
        SignRegistration sr = SignHelper.getSign(plugin, ev.getBlock().getLocation());
        boolean newSign = false;
        if (sr == null) newSign = true;
        sr = SignHelper.parseSign(plugin, ev.getLines(), sr, ev.getBlock().getLocation());
        if (sr == null) {
            ev.getPlayer().sendMessage(I18n.format("user.sign.create_fail"));
            return;
        }
        if (!ev.getPlayer().hasPermission("nb.sign_create_admin")) { // skip bank owner check for admins
            BankRegistration bank = plugin.dbm.getUniqueBank(sr.getBankId());
            if (bank == null || !ev.getPlayer().getUniqueId().equals(bank.ownerId)) {
                ev.getPlayer().sendMessage(I18n.format("user.sign.create_fail"));
                return;
            }
        }
        if (newSign) {
            plugin.dbm.db.query(SignRegistration.class).insert(sr);
        } else {
            plugin.dbm.db.query(SignRegistration.class).whereEq(SignRegistration.N_SIGN_ID, sr.getSignId()).update(sr);
        }
        ev.getPlayer().sendMessage(I18n.format("user.sign.create_success"));

        // update sign next tick
        final SignRegistration fsr = sr;
        new BukkitRunnable() {
            @Override
            public void run() {
                SignHelper.updateSignBlock(plugin, ev.getBlock().getLocation(), fsr);
            }
        }.runTaskLater(plugin, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerBreakSign(BlockBreakEvent ev) {
        Material m = ev.getBlock().getType();
        if (m != Material.SIGN && m != Material.WALL_SIGN) return;
        SignRegistration sr = SignHelper.getSign(plugin, ev.getBlock().getLocation());
        if (sr == null) return;
        if (!ev.getPlayer().hasPermission("nb.sign_break")) {
            ev.getPlayer().sendMessage(I18n.format("user.sign.break_no_permission"));
            ev.setCancelled(true);
            return;
        }
        if (!ev.getPlayer().hasPermission("nb.sign_break_admin")) { // skip owner check for admin
            BankRegistration bank = plugin.dbm.getUniqueBank(sr.getBankId());
            if (bank != null && !ev.getPlayer().getUniqueId().equals(bank.ownerId)) {
                ev.getPlayer().sendMessage(I18n.format("user.sign.break_no_permission"));
                ev.setCancelled(true);
                return;
            }
        }
        plugin.dbm.db.query(SignRegistration.class).whereEq(SignRegistration.N_SIGN_ID, sr.getSignId()).delete();
        ev.getPlayer().sendMessage(I18n.format("user.sign.break_success"));
    }
}
