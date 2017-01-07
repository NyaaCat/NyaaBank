package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaabank.database.enums.BankStatus;
import cat.nyaa.nyaabank.database.enums.InterestType;
import cat.nyaa.utils.database.DataColumn;
import cat.nyaa.utils.database.DataTable;
import cat.nyaa.utils.database.PrimaryKey;

import java.time.Instant;
import java.util.UUID;

/* Information about the bank */
@DataTable("bank_registration")
public class BankRegistration {
    public UUID bankId;
    public UUID ownerId;
    @DataColumn("id_number")
    public Long idNumber; // short identifier for human input
    @DataColumn("bank_name")
    public String name;
    @DataColumn("registered_capital")
    public Double registered_capital;
    @DataColumn("capital")
    public Double capital; // actual capital left
    @DataColumn("interest_rate_saving")
    public Double savingInterest; // default saving interest, hundred percent
    @DataColumn("interest_rate_debit")
    public Double debitInterest; // default loan interest, hundred percent
    @DataColumn("interest_rate_saving_next")
    public Double savingInterestNext; // saving interest for next cycle, hundred percent
    @DataColumn("interest_rate_debit_next")
    public Double debitInterestNext; // loan interest for next cycle, hundred percent

    public Instant establishDate; // Stored as human readable string
    public BankStatus status;
    public InterestType interestType;
    public InterestType interestTypeNext;

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

    @DataColumn("interest_type")
    public String getInterestType() {
        return interestType.toString();
    }

    public void setInterestType(String interestType) {
        this.interestType = InterestType.valueOf(interestType);
    }

    @DataColumn("interest_type_next")
    public String getInterestTypeNext() {
        return interestTypeNext.toString();
    }

    public void setInterestTypeNext(String interestTypeNext) {
        this.interestTypeNext = InterestType.valueOf(interestTypeNext);
    }
}
