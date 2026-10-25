package nz.co.ksktech.cashbook.core.reconcile;

import java.math.BigDecimal;
import java.time.LocalDate;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.core.Classification;
import nz.co.ksktech.cashbook.core.ClassificationMethod;
import nz.co.ksktech.cashbook.core.ForeignAmount;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.SourceRef;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.core.UtilityInvoice;

/** Builders for reconciliation tests. */
final class Lines {

    private Lines() {}

    static Classified coded(String id, String file, AccountType type, String date, String amount, String description,
            String code) {
        return line(id, file, type, date, date, amount, description, code, GstTreatment.STANDARD, null, null, null, false);
    }

    static Classified excluded(String id, String file, AccountType type, String date, String amount, String description) {
        return line(id, file, type, date, date, amount, description, null, GstTreatment.EXCLUDED, null, null, null, false);
    }

    static Classified line(String id, String file, AccountType type, String date, String processed, String amount,
            String description, String code, GstTreatment gst, String special, ForeignAmount foreign,
            BigDecimal businessAmount, boolean pending) {
        Transaction t = new Transaction(new SourceRef(file, 1, new BoundingBox(0, 0, 1, 1), description), "TEST", type,
                LocalDate.parse(date), LocalDate.parse(processed), description, Money.of(amount), foreign, null, true);
        Classification c = new Classification(code, gst, 1.0, ClassificationMethod.RULE, "test", "test");
        return new Classified(id, t, c, special, null, businessAmount, pending);
    }

    static UtilityInvoice invoice(String date, String power, String broadband, String fee, String credit) {
        BigDecimal total = Money.of(power).add(Money.of(broadband)).add(Money.of(fee));
        return new UtilityInvoice("inv.pdf", "1", LocalDate.parse(date), LocalDate.parse(date).plusDays(14),
                Money.of(power), Money.of(broadband), Money.of(fee), Money.of(credit), total,
                total.subtract(Money.of(credit)), false);
    }
}
