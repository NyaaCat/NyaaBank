package cat.nyaa.nyaabank;

import cat.nyaa.utils.PluginConfigure;
import org.bukkit.plugin.java.JavaPlugin;

public class Configuration extends PluginConfigure {
    private final NyaaBank plugin;

    /* Basic plugin & database config */
    @Serializable
    public String language = "en_US";
    @Serializable(name = "interest_cycle")
    public long interestCycle = 30L * 24 * 60 * 60 * 1000; // ms
    @Serializable(name = "interest_cycle_offset")
    public long interestCycleOffset = 0L; // ms

    /* Interest limit */
    @Serializable(name = "saving_interest_limit.enabled")
    public boolean savingInterestLimitEnabled = false;
    @Serializable(name = "saving_interest_limit.min")
    public double savingInterestLimitMin = -1D; // hundred percent
    @Serializable(name = "saving_interest_limit.max")
    public double savingInterestLimitMax = 2D; // hundred percent
    @Serializable(name = "debit_interest_limit.enabled")
    public boolean debitInterestLimitEnabled = false;
    @Serializable(name = "debit_interest_limit.min")
    public double debitInterestLimitMin = 0D; // hundred percent
    @Serializable(name = "debit_interest_limit.max")
    public double debitInterestLimitMax = 5D; // hundred percent

    @Serializable(name = "sign.sign_magic")
    public String signMagic = "[BANK]";
    @Serializable(name = "sign.color_active")
    public String signColorActive = "§l§a";
    @Serializable(name = "sign.color_bankrupt")
    public String signColorBankrupt = "§l§c";
    @Serializable(name = "sign.timeout")
    public long signTimeout = 7; // seconds

    /* Data stored in config file. Do not edit these entries manually */
    @Serializable(name = "data.last_check_point")
    public long lastCheckPoint = -1L; // unix timestamp ms. negative if no check point have been reached.

    public Configuration(NyaaBank pl) {
        this.plugin = pl;
    }

    @Override
    protected JavaPlugin getPlugin() {
        return plugin;
    }
}
