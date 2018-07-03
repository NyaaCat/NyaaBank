package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaabank.database.enums.BankStatus;
import cat.nyaa.nyaabank.database.enums.InterestType;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

/* Information about the bank */
@Entity
@Table(name = "bank_registration")
@Access(AccessType.FIELD)
public class BankRegistration {
    // Data column names
    public static final String N_ID_NUMBER = "id_number";
    public static final String N_BANK_NAME = "bank_name";
    public static final String N_REGISTERED_CAPITAL = "registered_capital";
    public static final String N_INTEREST_RATE_SAVING = "interest_rate_saving";
    public static final String N_INTEREST_RATE_DEBIT = "interest_rate_debit";
    public static final String N_INTEREST_RATE_SAVING_NEXT = "interest_rate_saving_next";
    public static final String N_INTEREST_RATE_DEBIT_NEXT = "interest_rate_debit_next";
    public static final String N_BANK_ID = "bank_id";
    public static final String N_OWNER_ID = "owner_id";
    public static final String N_ESTABLISH_DATE = "establish_date";
    public static final String N_BANK_STATUS = "bank_status";
    public static final String N_INTEREST_TYPE = "interest_type";
    public static final String N_INTEREST_TYPE_NEXT = "interest_type_next";

    public UUID bankId;
    public UUID ownerId;
    @Column(name = N_ID_NUMBER)
    public Long idNumber; // short identifier for human input
    @Column(name = N_BANK_NAME)
    public String name;
    @Column(name = N_REGISTERED_CAPITAL)
    public Double registered_capital;
    @Column(name = N_INTEREST_RATE_SAVING)
    public Double savingInterest; // default saving interest, hundred percent
    @Column(name = N_INTEREST_RATE_DEBIT)
    public Double debitInterest; // default loan interest, hundred percent
    @Column(name = N_INTEREST_RATE_SAVING_NEXT)
    public Double savingInterestNext; // saving interest for next cycle, hundred percent
    @Column(name = N_INTEREST_RATE_DEBIT_NEXT)
    public Double debitInterestNext; // loan interest for next cycle, hundred percent

    public Instant establishDate; // Stored as human readable string
    public BankStatus status;
    public InterestType interestType;
    public InterestType interestTypeNext;

    @Access(AccessType.PROPERTY)
    @Column(name = N_BANK_ID)
    @Id
    public String getBankId() {
        return bankId.toString();
    }

    public void setBankId(String bankId) {
        this.bankId = UUID.fromString(bankId);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_OWNER_ID)
    public String getOwnerId() {
        return ownerId.toString();
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = UUID.fromString(ownerId);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_ESTABLISH_DATE)
    public String getEstablishDate() {
        return establishDate.toString();
    }

    public void setEstablishDate(String establishDate) {
        this.establishDate = Instant.parse(establishDate);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_BANK_STATUS)
    public String getStatus() {
        return status.toString();
    }

    public void setStatus(String status) {
        this.status = BankStatus.valueOf(status);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_INTEREST_TYPE)
    public String getInterestType() {
        return interestType.toString();
    }

    public void setInterestType(String interestType) {
        this.interestType = InterestType.valueOf(interestType);
    }

    @Access(AccessType.PROPERTY)
    @Column(name = N_INTEREST_TYPE_NEXT)
    public String getInterestTypeNext() {
        return interestTypeNext.toString();
    }

    public void setInterestTypeNext(String interestTypeNext) {
        this.interestTypeNext = InterestType.valueOf(interestTypeNext);
    }
}
