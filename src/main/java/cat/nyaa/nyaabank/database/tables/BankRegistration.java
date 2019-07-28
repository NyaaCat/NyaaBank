package cat.nyaa.nyaabank.database.tables;

import cat.nyaa.nyaabank.database.enums.BankStatus;
import cat.nyaa.nyaabank.database.enums.InterestType;
import cat.nyaa.nyaacore.orm.annotations.Column;
import cat.nyaa.nyaacore.orm.annotations.Table;

import java.time.Instant;
import java.util.UUID;

/* Information about the bank */
@Table("bank_registration")
public class BankRegistration {
    public static final String N_BANK_ID = "bank_id";
    @Column(name = N_BANK_ID, primary = true)
    public UUID bankId;

    public static final String N_OWNER_ID = "owner_id";
    @Column(name = N_OWNER_ID)
    public UUID ownerId;

    public static final String N_ID_NUMBER = "id_number";
    @Column(name = N_ID_NUMBER)
    public Long idNumber; // short identifier for human input

    public static final String N_BANK_NAME = "bank_name";
    @Column(name = N_BANK_NAME)
    public String name;

    public static final String N_REGISTERED_CAPITAL = "registered_capital";
    @Column(name = N_REGISTERED_CAPITAL)
    public Double registered_capital;

    public static final String N_INTEREST_RATE_SAVING = "interest_rate_saving";
    @Column(name = N_INTEREST_RATE_SAVING)
    public Double savingInterest; // default saving interest, hundred percent

    public static final String N_INTEREST_RATE_DEBIT = "interest_rate_debit";
    @Column(name = N_INTEREST_RATE_DEBIT)
    public Double debitInterest; // default loan interest, hundred percent

    public static final String N_INTEREST_RATE_SAVING_NEXT = "interest_rate_saving_next";
    @Column(name = N_INTEREST_RATE_SAVING_NEXT)
    public Double savingInterestNext; // saving interest for next cycle, hundred percent

    public static final String N_INTEREST_RATE_DEBIT_NEXT = "interest_rate_debit_next";
    @Column(name = N_INTEREST_RATE_DEBIT_NEXT)
    public Double debitInterestNext; // loan interest for next cycle, hundred percent

    public static final String N_ESTABLISH_DATE = "establish_date";
    @Column(name = N_ESTABLISH_DATE)
    public Instant establishDate; // Stored as human readable string

    public static final String N_BANK_STATUS = "bank_status";
    @Column(name = N_BANK_STATUS)
    public BankStatus status;

    public static final String N_INTEREST_TYPE = "interest_type";
    @Column(name = N_INTEREST_TYPE)
    public InterestType interestType;

    public static final String N_INTEREST_TYPE_NEXT = "interest_type_next";
    @Column(name = N_INTEREST_TYPE_NEXT)
    public InterestType interestTypeNext;
}
