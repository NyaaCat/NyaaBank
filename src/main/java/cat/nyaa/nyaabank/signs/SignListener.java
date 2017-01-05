package cat.nyaa.nyaabank.signs;

import cat.nyaa.nyaabank.CommonAction;
import cat.nyaa.nyaabank.I18n;
import cat.nyaa.nyaabank.NyaaBank;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.SignRegistration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRightClickSign(PlayerInteractEvent ev) {
        // TODO log
        // TODO permission
        if (!ev.hasBlock()) return;
        if (ev.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Material blockType = ev.getClickedBlock().getType();
        if (blockType != Material.SIGN_POST && blockType != Material.WALL_SIGN) return;
        Sign sign = (Sign) ev.getClickedBlock().getState();
        String magicLine = ChatColor.stripColor(sign.getLine(0));
        if (stringEqIgnoreColor(magicLine, plugin.cfg.signMagic, false) ||
                stringEqIgnoreColor(magicLine, SIGN_MAGIC_FALLBACK, false)) {
            Player p = ev.getPlayer();
            SignRegistration sr = SignHelper.getSign(plugin, ev.getClickedBlock().getLocation());
            if (sr == null) {
                p.sendMessage(I18n._("user.sign.invalid_sign"));
                return;
            }
            BankRegistration bank = plugin.dbm.getUniqueBank(sr.bankId.toString());
            if (bank == null) {
                p.sendMessage(I18n._("user.sign.invalid_sign"));
                return;
            }
            if (sr.type == TransactionType.LOAN && sr.loanAmount <= 0) {
                p.sendMessage(I18n._("user.sign.invalid_sign"));
                return;
            }
            switch (sr.type) {
                case DEPOSIT:
                    callbacks.register(ev.getPlayer().getUniqueId(),
                            new ChatInputCallbacks.InputCallback() {
                                @Override
                                public void onDoubleInput(Player p, double input, boolean isAll) {
                                    try {
                                        if (isAll) throw new CommonAction.TransactionException("user.sign.invalid_number");
                                        CommonAction.deposit(plugin, p, bank, input);
                                    } catch (CommonAction.TransactionException ex) {
                                        p.sendMessage(I18n._(ex.getMessage()));
                                    }
                                }
                            });
                    break;
                case WITHDRAW:
                    callbacks.register(ev.getPlayer().getUniqueId(),
                            new ChatInputCallbacks.InputCallback() {
                                @Override
                                public void onDoubleInput(Player p, double input, boolean isAll) {
                                    try {
                                        CommonAction.withdraw(plugin, p, bank, input, isAll);
                                    } catch (CommonAction.TransactionException ex) {
                                        p.sendMessage(I18n._(ex.getMessage()));
                                    }
                                }
                            });
                    break;
                case LOAN:
                    callbacks.register(ev.getPlayer().getUniqueId(),
                            new ChatInputCallbacks.InputCallback() {
                                @Override
                                public void onDoubleInput(Player p, double input, boolean isAll) {
                                    try {
                                        if (!isAll) throw new CommonAction.TransactionException("user.sign.loan_cancelled");
                                        CommonAction.loan(plugin, p, bank, sr.loanAmount);
                                    } catch (CommonAction.TransactionException ex) {
                                        p.sendMessage(I18n._(ex.getMessage()));
                                    }
                                }
                            });
                    break;
                case REPAY:
                    callbacks.register(ev.getPlayer().getUniqueId(),
                            new ChatInputCallbacks.InputCallback() {
                                @Override
                                public void onDoubleInput(Player p, double input, boolean isAll) {
                                    try {
                                        CommonAction.repay(plugin, p, bank, input, isAll);
                                    } catch (CommonAction.TransactionException ex) {
                                        p.sendMessage(I18n._(ex.getMessage()));
                                    }
                                }
                            });
                    break;
                default:
                    p.sendMessage(I18n._("user.sign.invalid_sign"));
                    return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCreateSign(SignChangeEvent ev) {
        // TODO permission
        SignRegistration sr = SignHelper.getSign(plugin, ev.getBlock().getLocation());
        boolean newSign = false;
        if (sr == null) newSign = true;
        sr = SignHelper.parseSign(plugin, ev.getLines(), sr);
        if (sr == null) {
            ev.getPlayer().sendMessage(I18n._("user.sign.create_fail"));
            return;
        }
        BankRegistration bank = plugin.dbm.getUniqueBank(sr.getBankId());
        if (bank == null || !ev.getPlayer().getUniqueId().equals(bank.ownerId)) {
            ev.getPlayer().sendMessage(I18n._("user.sign.create_fail"));
            return;
        }
        if (newSign) {
            plugin.dbm.query(SignRegistration.class).insert(sr);
        } else {
            plugin.dbm.query(SignRegistration.class).whereEq("sign_id", sr.getSignId()).update(sr);
        }
        ev.getPlayer().sendMessage(I18n._("user.sign.create_success"));

        // update sign next tick
        final SignRegistration fsr = sr;
        new BukkitRunnable() {
            @Override
            public void run() {
                SignHelper.updateSignBlock(plugin, ev.getBlock().getLocation(), fsr);
            }
        }.runTaskLater(plugin, 1);
    }

    //TODO sign break
}
