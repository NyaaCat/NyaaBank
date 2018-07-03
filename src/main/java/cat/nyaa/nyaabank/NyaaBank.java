package cat.nyaa.nyaabank;

import cat.nyaa.nyaabank.database.CycleManager;
import cat.nyaa.nyaabank.database.DatabaseManager;
import cat.nyaa.nyaabank.signs.SignListener;
import cat.nyaa.nyaacore.utils.VaultUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class NyaaBank extends JavaPlugin {
    public static NyaaBank instance = null;
    public Configuration cfg = null;
    public I18n i18n;
    public DatabaseManager dbm;
    public CommandHandler commandHandler;
    public Economy eco;
    public CycleManager cycle;
    public SignListener signListener;

    @Override
    public void onEnable() {
        instance = this;
        cfg = new Configuration(this);
        cfg.load();
        i18n = new I18n(this, cfg.language);
        dbm = new DatabaseManager(this);
        commandHandler = new CommandHandler(this, i18n);
        eco = VaultUtils.getVaultEconomy();
        cycle = new CycleManager(this);
        signListener = new SignListener(this);
        getCommand("nyaabank").setExecutor(commandHandler);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        getCommand("nyaabank").setExecutor(null);
        cfg.save();
        dbm.db.close();
    }

    // Reload plugin simply disable the plugin without saving config
    public void doReload() {
        HandlerList.unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        getCommand("nyaabank").setExecutor(null);
        dbm.db.close();
        i18n.load();
        onEnable();
    }
}
