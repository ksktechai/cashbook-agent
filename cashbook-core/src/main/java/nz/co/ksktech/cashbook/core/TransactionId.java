package nz.co.ksktech.cashbook.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** A stable id for a transaction, from where it is and what it says, so review answers survive re-runs. */
public final class TransactionId {

    private static final int LENGTH = 12;

    private TransactionId() {}

    public static String of(Transaction t) {
        String key = String.join("|", t.source().file(), Integer.toString(t.source().page()),
                t.transactionDate().toString(), t.amount().toPlainString(), t.source().rawText());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
