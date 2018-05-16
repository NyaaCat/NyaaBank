package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaabank.database.enums.TransactionType;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import javax.persistence.*;
import java.util.UUID;

/* Information about the signs in world map */
@Entity
@Access(AccessType.PROPERTY)
@Table(name = "sign_registration")
public class SignRegistration {
    // Data column names
    public static final String N_LOAN_AMOUNT = "loan_amount";
    public static final String N_COMMISSION_FEE = "commission_fee";
    public static final String N_SIGN_ID = "sign_id";
    public static final String N_BANK_ID = "bank_id";
    public static final String N_SIGN_TYPE = "sign_type";
    public static final String N_LOCATION_WORLD_NAME = "location_world_name";
    public static final String N_LOCATION_X = "location_x";
    public static final String N_LOCATION_Y = "location_y";
    public static final String N_LOCATION_Z = "location_z";

    public UUID signId;
    public UUID bankId;
    public TransactionType type; // DEPOSIT/WITHDRAW/LOAN/REPAY
    public Location location;
    @Access(AccessType.FIELD)
    @Column(name = N_LOAN_AMOUNT)
    public Double loanAmount; // for LOAN sign only
    @Access(AccessType.FIELD)
    @Column(name = N_COMMISSION_FEE)
    public Double commissionFee; // for WITHDRAW & REPAY only

    @Id
    @Column(name = N_SIGN_ID)
    public String getSignId() {
        return signId.toString();
    }

    public void setSignId(String signId) {
        this.signId = UUID.fromString(signId);
    }

    @Column(name = N_BANK_ID)
    public String getBankId() {
        return bankId.toString();
    }

    public void setBankId(String bankId) {
        this.bankId = UUID.fromString(bankId);
    }

    @Column(name = N_SIGN_TYPE)
    public String getType() {
        return type.toString();
    }

    public void setType(String type) {
        this.type = TransactionType.valueOf(type);
    }

    @Column(name = N_LOCATION_WORLD_NAME)
    public String getWorldName() {
        return location.getWorld().getName();
    }

    public void setWorldName(String worldName) {
        if (location == null) {
            location = new Location(Bukkit.getWorld(worldName), 0, 0, 0);
        } else {
            location.setWorld(Bukkit.getWorld(worldName));
        }
    }

    @Column(name = N_LOCATION_X)
    public Long getCoordinateX() {
        return (long) location.getBlockX();
    }

    public void setCoordinateX(Long x) {
        if (location == null) {
            location = new Location(Bukkit.getWorlds().get(0), x, 0, 0);
        } else {
            location.setX(x);
        }
    }

    @Column(name = N_LOCATION_Y)
    public Long getCoordinateY() {
        return (long) location.getBlockY();
    }

    public void setCoordinateY(Long y) {
        if (location == null) {
            location = new Location(Bukkit.getWorlds().get(0), 0, y, 0);
        } else {
            location.setY(y);
        }
    }

    @Column(name = N_LOCATION_Z)
    public Long getCoordinateZ() {
        return (long) location.getBlockZ();
    }

    public void setCoordinateZ(Long z) {
        if (location == null) {
            location = new Location(Bukkit.getWorlds().get(0), 0, 0, z);
        } else {
            location.setZ(z);
        }
    }
}
