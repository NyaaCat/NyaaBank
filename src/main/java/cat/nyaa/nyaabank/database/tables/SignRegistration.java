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
    @DataColumn
    @PrimaryKey
    Integer id = null;
    UUID bankId;
    TransactionType type;
    Location location;
    // TODO

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
    public Integer getCoordinateX() {
        return location.getBlockX();
    }

    public void setCoordinateX(Integer x) {
        if (location == null) {
            location = new Location(Bukkit.getWorlds().get(0), x, 0, 0);
        } else {
            location.setX(x);
        }
    }

    @DataColumn("location_y")
    public Integer getCoordinateY() {
        return location.getBlockY();
    }

    public void setCoordinateY(Integer y) {
        if (location == null) {
            location = new Location(Bukkit.getWorlds().get(0), 0, y, 0);
        } else {
            location.setY(y);
        }
    }

    @DataColumn("location_z")
    public Integer getCoordinateZ() {
        return location.getBlockZ();
    }

    public void setCoordinateZ(Integer z) {
        if (location == null) {
            location = new Location(Bukkit.getWorlds().get(0), 0, 0, z);
        } else {
            location.setZ(z);
        }
    }
}
