package cat.nyaa.nyaabank.database;

import cat.nyaa.nyaabank.NyaaBank;
import cat.nyaa.utils.database.SQLiteDatabase;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class DatabaseManager extends SQLiteDatabase{
    private final NyaaBank plugin;
    public DatabaseManager(NyaaBank plugin) {
        super();
        this.plugin = plugin;
        connect();
    }

    @Override
    protected String getFileName() {
        return "nyaabank.db";
    }

    @Override
    protected JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    protected Class<?>[] getTables() {
        return new Class<?>[]{
                BankRegistration.class,
                BankAccount.class,
                SignRegistration.class,
                TransactionRecord.class
        };
    }

    /**
     * get unique bank registeration from partial bank UUID
     * null returned if bank not unique or not exists
     *
     * @param partialUUID part of the uuid, including dash
     * @return unique bank
     */
    BankRegistration getUniqueBank(String partialUUID) {
        List<BankRegistration> r = query(BankRegistration.class)
                .where("bank_id"," LIKE ", "%" + partialUUID + "%")
                .select();
        if (r.size() > 1 || r.size() <= 0) return null;
        return r.get(0);
    }
}
