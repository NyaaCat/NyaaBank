package cat.nyaa.nyaabank.database;

import java.time.Instant;
import java.util.UUID;

/* Recorded transaction */
public class TransactionRecord {
    UUID bankId;
    UUID playerId;
    Instant timestamp;
    TransactionType type;
    float amount;
    // TODO
}
