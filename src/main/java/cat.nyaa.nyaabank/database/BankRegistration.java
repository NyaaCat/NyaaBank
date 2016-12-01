package cat.nyaa.nyaabank.database;

import java.time.Instant;
import java.util.UUID;

/* Information about the bank */
public class BankRegistration {
    UUID bankId;
    String name;
    UUID ownerId;
    int capital;
    Instant establishDate;
    BankStatus status;
    float savingInterest;
    float debitInterest;
    // TODO
}
