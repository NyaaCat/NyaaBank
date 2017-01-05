package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaabank.database.enums.TransactionType;
import cat.nyaa.utils.database.DataColumn;
import cat.nyaa.utils.database.DataTable;
import cat.nyaa.utils.database.PrimaryKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.UUID;

/* Information about the signs in world map */
@DataTable("sign_registration")
public class SignRegistration {
    public UUID signId;
    public UUID bankId;
    public TransactionType type; // DEPOSIT/WITHDRAW/LOAN/REPAY
    public Location location;
    @DataColumn("loan_amount")
    public Double loanAmount; // for LOAN sign only
    @DataColumn("commission_fee")
    public Double commissionFee;

    @PrimaryKey
    @DataColumn("sign_id")
    public String getSignId() {
        return signId.toString();
    }

    public void setSignId(String signId) {
        this.signId = UUID.fromString(signId);
    }

    @DataColumn("bank_id")
    public String getBankId() {
        return bankId.toString();
    }

    public void setBankId(String bankId) {
        this.bankId = UUID.fromString(bankId);
    }

    @DataColumn("sign_type")
    public String getType() {
        return type.toString();
    }

    public void setType(String type) {
        this.type = TransactionType.valueOf(type);
    }

    @DataColumn("location_world_name")
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

    @DataColumn("location_x")
    public Long getCoordinateX() {
        return (long)location.getBlockX();
    }

    public void setCoordinateX(Long x) {
        if (location == null) {
            location = new Location(Bukkit.getWorlds().get(0), x, 0, 0);
        } else {
            location.setX(x);
        }
    }

    @DataColumn("location_y")
    public Long getCoordinateY() {
        return (long)location.getBlockY();
    }

    public void setCoordinateY(Long y) {
        if (location == null) {
            location = new Location(Bukkit.getWorlds().get(0), 0, y, 0);
        } else {
            location.setY(y);
        }
    }

    @DataColumn("location_z")
    public Long getCoordinateZ() {
        return (long)location.getBlockZ();
    }

    public void setCoordinateZ(Long z) {
        if (location == null) {
            location = new Location(Bukkit.getWorlds().get(0), 0, 0, z);
        } else {
            location.setZ(z);
        }
    }
}
