package cat.nyaa.nyaabank;

import cat.nyaa.utils.Internationalization;
import org.bukkit.plugin.java.JavaPlugin;

public class I18n extends Internationalization{
    private static I18n instance;
    public I18n(JavaPlugin plugin, String lang) {
        super(plugin);
        instance = this;
        load(lang);
    }

    public static String _(String key, Object... args) {
        return instance.get(key, args);
    }
}
