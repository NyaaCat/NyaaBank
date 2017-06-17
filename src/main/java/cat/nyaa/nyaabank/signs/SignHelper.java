package cat.nyaa.nyaabank.signs;

import cat.nyaa.nyaabank.I18n;
import cat.nyaa.nyaabank.NyaaBank;
import cat.nyaa.nyaabank.database.enums.BankStatus;
import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.nyaabank.database.tables.BankRegistration;
import cat.nyaa.nyaabank.database.tables.SignRegistration;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;

public final class SignHelper {

    public static boolean stringEqIgnoreColor(String s1, String s2, boolean ignoreCase) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        String a = ChatColor.stripColor(s1);
        String b = ChatColor.stripColor(s2);
        if (ignoreCase) {
            return a.equalsIgnoreCase(b);
        } else {
            return a.equals(b);
        }
    }

    public static Double parseDouble(String s) {
        Double d = null;
        try {
            d = Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (Double.isInfinite(d) || Double.isNaN(d))
            return null;
        return d;
    }

    /**
     * get the registered sign at the given location
     * if no sign registered, return null
     */
    public static SignRegistration getSign(NyaaBank plugin, Location location) {
        List<SignRegistration> l = plugin.dbm.query(SignRegistration.class)
                .whereEq(SignRegistration.N_LOCATION_WORLD_NAME, location.getWorld().getName())
                .whereEq(SignRegistration.N_LOCATION_X, location.getBlockX())
                .whereEq(SignRegistration.N_LOCATION_Y, location.getBlockY())
                .whereEq(SignRegistration.N_LOCATION_Z, location.getBlockZ())
                .select();
        if (l.size() > 1 || l.size() <= 0) return null;
        return l.get(0);
    }

    /**
     * update the sign according to the sign registeration
     * IllegalArgumentException thrown if not valid sign block.
     */
    public static void updateSignBlock(NyaaBank plugin, Location signLocation, SignRegistration signReg) {
        if (signLocation == null || signLocation.getWorld() == null || signLocation.getBlock() == null ||
                !(signLocation.getBlock().getType() == Material.WALL_SIGN ||
                signLocation.getBlock().getType() == Material.SIGN_POST)) {
            throw new IllegalArgumentException("Not a valid sign block");
        }
        BankRegistration bank = plugin.dbm.getUniqueBank(signReg.getBankId());
        if (bank == null) {
            throw new IllegalArgumentException("Invalid bank");
        }
        Sign sign = (Sign)signLocation.getBlock().getState();
        sign.setLine(0, plugin.cfg.signMagic);
        sign.setLine(1, (bank.status == BankStatus.BANKRUPT? plugin.cfg.signColorBankrupt: plugin.cfg.signColorActive)
                +bank.name);
        switch (signReg.type) {
            case LOAN:
                sign.setLine(2, I18n.format("user.sign.text_loan"));
                sign.setLine(3, I18n.format("user.sign.hint_loan", signReg.loanAmount, bank.debitInterest));
                break;
            case DEPOSIT:
                sign.setLine(2, I18n.format("user.sign.text_saving"));
                sign.setLine(3, I18n.format("user.sign.hint_saving", bank.savingInterest));
                break;
            case WITHDRAW:
                sign.setLine(2, I18n.format("user.sign.text_withdraw"));
                sign.setLine(3, I18n.format("user.sign.hint_withdraw", signReg.commissionFee));
                break;
            case REPAY:
                sign.setLine(2, I18n.format("user.sign.text_repay"));
                sign.setLine(3, I18n.format("user.sign.hint_repay", signReg.commissionFee));
                break;
            default:
                throw new IllegalArgumentException("Invalid sign type");
        }
        sign.update();
    }

    /**
     * Parse the sign at the given location
     * If signReg given, info is stored into it, otherwise a new SignRegistration is created
     * signReg or new object is returned, null is returned if errors occur
     * The sign itself is not altered.
     */
    public static SignRegistration parseSign(NyaaBank plugin, String[] lines, SignRegistration signReg, Location signLoc) {
        if (signReg == null) {
            signReg = new SignRegistration();
            signReg.signId = UUID.randomUUID();
        }
        String magic = lines[0];
        if (!stringEqIgnoreColor(magic, plugin.cfg.signMagic, true) &&
                !stringEqIgnoreColor(magic, SignListener.SIGN_MAGIC_FALLBACK, true)) {
            return null;
        }
        BankRegistration bank = null;
        try {
            long idNumber = Long.parseLong(lines[1]);
            bank = plugin.dbm.getBankByIdNumber(idNumber);
        } catch (NumberFormatException ex) {
            bank = null;
        }
        if (bank == null) return null;
        signReg.bankId = bank.bankId;
        signReg.commissionFee = 0D;
        signReg.location = signLoc.clone();
        signReg.loanAmount = -1D; // to meet DB not null constraint
        String srv = lines[2].toUpperCase();
        switch (srv) {
            case "DEPOSIT":
                signReg.type = TransactionType.DEPOSIT;
                break;
            case "WITHDRAW":
                signReg.type = TransactionType.WITHDRAW;
                signReg.commissionFee = parseDouble(lines[3]);
                if (signReg.commissionFee == null) return null;
                break;
            case "LOAN":
                signReg.type = TransactionType.LOAN;
                signReg.loanAmount = parseDouble(lines[3]);
                if (signReg.loanAmount == null) return null;
                break;
            case "REPAY":
                signReg.type = TransactionType.REPAY;
                signReg.commissionFee = parseDouble(lines[3]);
                if (signReg.commissionFee == null) return null;
                break;
            default:
                return null;
        }
        return signReg;
    }

    /**
     * Update all signs in the given list.
     * One sign per tick, so it may take several ticks to complete the request
     */
    public static void batchUpdateSign(NyaaBank plugin, List<SignRegistration> signList) {
        if (signList == null || signList.size() == 0) return;
        new BukkitRunnable() {
            int idx = 0;
            @Override
            public void run() {
                SignRegistration sr = signList.get(idx);
                try {
                    updateSignBlock(plugin, sr.location, sr);
                } catch (IllegalArgumentException ex) { // remove invalid sign registrations
                    plugin.dbm.query(SignRegistration.class).whereEq(SignRegistration.N_SIGN_ID, sr.getSignId()).delete();
                    ex.printStackTrace();
                }
                idx++;
                if (idx >= signList.size()) this.cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Update all signs of the given bank
     */
    public static void batchUpdateSign(NyaaBank plugin, BankRegistration bank) {
        batchUpdateSign(plugin, plugin.dbm.query(SignRegistration.class)
                .whereEq(SignRegistration.N_BANK_ID, bank.getBankId()).select());
    }
}
