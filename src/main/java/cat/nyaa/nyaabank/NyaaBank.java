package cat.nyaa.nyaabank;

import cat.nyaa.nyaabank.database.CycleManager;
import cat.nyaa.nyaabank.database.DatabaseManager;
import cat.nyaa.utils.VaultUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.java.JavaPlugin;

public class NyaaBank extends JavaPlugin {
    public static NyaaBank instance = null;
    public Configuration cfg = null;
    public I18n i18n;
    public DatabaseManager dbm;
    public CommandHandler commandHandler;
    public Economy eco;
    public CycleManager cycle;

    @Override
    public void onEnable() {
        instance = this;
        cfg = new Configuration(this);
        cfg.load();
        i18n = new I18n(this, cfg.language);
        dbm = new DatabaseManager(this);
        commandHandler = new CommandHandler(this, i18n);
        eco = VaultUtil.getVaultInstance();
        cycle = new CycleManager(this);
        getCommand("nyaabank").setExecutor(commandHandler);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getCommand("nyaabank").setExecutor(null);
        cfg.save();
        dbm.close();
        i18n.reset();
    }
}
