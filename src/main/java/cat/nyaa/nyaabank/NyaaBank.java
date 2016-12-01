package cat.nyaa.nyaabank;

import org.bukkit.plugin.java.JavaPlugin;

public class NyaaBank extends JavaPlugin {
    public static NyaaBank instance = null;
    public Configuration cfg = null;
    public I18n i18n;

    @Override
    public void onEnable() {
        instance = this;
        cfg = new Configuration(this);
        cfg.load();
        i18n = new I18n(this, cfg.language);
    }
    @Override
    public void onDisable() {
        cfg.save();
        i18n.reset();
    }
}
