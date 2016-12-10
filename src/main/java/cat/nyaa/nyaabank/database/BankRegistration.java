package cat.nyaa.nyaabank.database;

import cat.nyaa.utils.database.DataColumn;
import cat.nyaa.utils.database.DataTable;
import cat.nyaa.utils.database.PrimaryKey;

import java.time.Instant;
import java.util.UUID;

/* Information about the bank */
@DataTable("bank_registration")
public class BankRegistration {
    public UUID bankId;
    @DataColumn("bank_name")
    public String name;
    public UUID ownerId;
    @DataColumn("registered_capital")
    public Integer capital;
    public Instant establishDate;
    public BankStatus status;
    @DataColumn("interest_rate_saving")
    public Double savingInterest;
    @DataColumn("interest_rate_debit")
    public Double debitInterest;
    // TODO

    @DataColumn("bank_id")
    @PrimaryKey
    public String getBankId() {
        return bankId.toString();
    }

    public void setBankId(String bankId) {
        this.bankId = UUID.fromString(bankId);
    }

    @DataColumn("owner_id")
    public String getOwnerId() {
        return ownerId.toString();
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = UUID.fromString(ownerId);
    }

    @DataColumn("establish_date")
    public String getEstablishDate() {
        return establishDate.toString();
    }

    public void setEstablishDate(String establishDate) {
        this.establishDate = Instant.parse(establishDate);
    }

    @DataColumn("bank_status")
    public String getStatus() {
        return status.toString();
    }

    public void setStatus(String status) {
        this.status = BankStatus.valueOf(status);
    }
}
