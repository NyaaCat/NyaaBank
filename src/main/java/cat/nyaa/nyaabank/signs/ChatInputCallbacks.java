package cat.nyaa.nyaabank.signs;

import cat.nyaa.nyaabank.I18n;
import cat.nyaa.nyaabank.NyaaBank;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class ChatInputCallbacks {
    interface InputCallback {
        void onDoubleInput(Player p, double input, boolean isAll);
    }

    class InputTimeoutTimer extends BukkitRunnable{
        private final UUID playerId;

        InputTimeoutTimer(UUID id) {
            playerId = id;
        }

        @Override
        public void run() {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                p.sendMessage(I18n.format("user.sign.input_timeout"));
            }
            callbacks.remove(playerId);
            timers.remove(playerId);
            if (callbacks.isEmpty() && listener != null) {
                HandlerList.unregisterAll(listener);
                listener = null;
            }
        }
    }

    class InputListener implements Listener {
        @EventHandler(priority = EventPriority.LOW)
        public void onPlayerInput(AsyncPlayerChatEvent ev) {
            UUID playerId = ev.getPlayer().getUniqueId();
            if (!callbacks.containsKey(playerId)) return;


            ev.setCancelled(true);
            String msg = ev.getMessage();
            if ("ALL".equalsIgnoreCase(msg) || "CONFIRM".equalsIgnoreCase(msg)) {
                callbacks.get(playerId).onDoubleInput(ev.getPlayer(), -1, true);
            } else {
                Double number = null;
                try {
                    number = Double.parseDouble(msg);
                    if (Double.isInfinite(number) || Double.isNaN(number))
                        number = null;
                } catch (NumberFormatException ex) {
                    number = null;
                }
                if (number == null) {
                    ev.getPlayer().sendMessage(I18n.format("user.sign.invalid_number"));
                } else {
                    callbacks.get(playerId).onDoubleInput(ev.getPlayer(), number, false);
                }
            }

            if (timers.containsKey(playerId)) {
                timers.get(playerId).cancel();
            }
            callbacks.remove(playerId);
            timers.remove(playerId);
            assert(listener == this);
            if (callbacks.isEmpty()) {
                HandlerList.unregisterAll(listener);
                listener = null;
            }
        }
    }

    final Map<UUID, InputCallback> callbacks = new HashMap<>();
    private final Map<UUID, BukkitRunnable> timers = new HashMap<>();
    private InputListener listener = null;
    private final NyaaBank plugin;

    ChatInputCallbacks(NyaaBank plugin) {
        this.plugin = plugin;
    }

    public void register(UUID player, InputCallback callback) {
        callbacks.put(player, callback);
        InputTimeoutTimer timer = new InputTimeoutTimer(player);
        timer.runTaskLater(plugin, plugin.cfg.signTimeout*20);
        timers.put(player, timer);
        if (listener == null) {
            listener = new InputListener();
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }
}
