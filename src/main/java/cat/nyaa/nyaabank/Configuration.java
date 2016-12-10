package cat.nyaa.nyaabank;

import cat.nyaa.utils.PluginConfigure;
import org.bukkit.plugin.java.JavaPlugin;

public class Configuration extends PluginConfigure {
    private final NyaaBank plugin;

    @Serializable
    public String language = "en_US";
    @Serializable(name = "min_capitalization")
    public int minCapitalization = 1000000;
    @Serializable(name = "interest_cycle")
    public int interestCycle = 2592000; // seconds
    @Serializable(name = "enable_signs.saving_withdraw")
    public boolean enableSignSavingWithdraw = true;
    @Serializable(name = "enable_signs.debit_repay")
    public boolean enableSignDebitRepay = true;
    @Serializable(name = "enable_signs.query_status")
    public boolean enableSignQuery = true;
    @Serializable(name = "enable_signs.query_history")
    public boolean enableSignHistory = true;
    @Serializable(name = "saving_interest_limit.enabled")
    public boolean savingInterestLimitEnabled = false;
    @Serializable(name = "saving_interest_limit.min")
    public double savingInterestLimitMin = -1F; // hundred percent
    @Serializable(name = "saving_interest_limit.max")
    public double savingInterestLimitMax = 2F; // hundred percent
    @Serializable(name = "debit_interest_limit.enabled")
    public boolean debitInterestLimitEnabled = false;
    @Serializable(name = "debit_interest_limit.min")
    public double debitInterestLimitMin = 0F; // hundred percent
    @Serializable(name = "debit_interest_limit.max")
    public double debitInterestLimitMax = 5F; // hundred percent
    @Serializable(name = "update_interval")
    public int dbUpdateInterval = 86400; // seconds
    @Serializable(name = "sign_color.active")
    public String signColorActive = "§l§a";
    @Serializable(name = "sign_color.bankrupt")
    public String signColorBankrupt = "§l§c";

    public Configuration(NyaaBank pl) {
        this.plugin = pl;
    }

    @Override
    protected JavaPlugin getPlugin() {
        return plugin;
    }
}
