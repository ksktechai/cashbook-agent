package nz.co.ksktech.cashbook.core.reconcile;

import static nz.co.ksktech.cashbook.core.reconcile.Lines.coded;
import static nz.co.ksktech.cashbook.core.reconcile.Lines.excluded;
import static nz.co.ksktech.cashbook.core.reconcile.Lines.invoice;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.ForeignAmount;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.UtilityInvoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HandlersTest {

    @ParameterizedTest(name = "pay {0}: power {1} + fee {2} + broadband {3}, credit {4} -> 725 {5}, 727 {6}")
    @CsvSource({
        // no credit: Internet = broadband; Power = power + (payment - power - broadband)
        "243.47, 150.70, 2.78, 89.99,  0.00, 153.48, 89.99",
        "212.56, 119.38, 3.19, 89.99,  0.00, 122.57, 89.99",
        // credit applied: the payment is split pro-rata to the invoice's charges, and still sums exactly
        "205.79, 137.58, 3.22, 89.99, 25.00, 125.55, 80.24",
        // rounding edge: power 33.3250015 rounds up, the fee 0.009998 rounds up, Internet takes the remainder
        " 66.67,  33.33, 0.01, 33.34,  0.01,  33.34, 33.33"
    })
    void utilitySplitSumsExactlyToThePayment(String paid, String power, String fee, String broadband, String credit,
            String power725, String internet) {
        UtilityInvoice inv = invoice("2026-11-20", power, broadband, fee, credit);
        UtilitySplit split = UtilitySplitter.split(Money.of(paid), LocalDate.of(2026, 12, 5), inv);

        assertThat(split.power725()).isEqualByComparingTo(power725);
        assertThat(split.internet()).isEqualByComparingTo(internet);
        assertThat(split.power().add(split.fee()).add(split.internet())).isEqualByComparingTo(paid);
        assertThat(split.footnote() == null).isEqualTo(Money.of(credit).signum() == 0);
    }

    @Test
    void utilityPaymentSettlesTheInvoiceFromThePreviousMonth() {
        List<UtilityInvoice> invoices = List.of(invoice("2026-09-20", "1", "1", "0", "0"),
                invoice("2026-10-20", "2", "2", "0", "0"), invoice("2026-11-20", "3", "3", "0", "0"));

        assertThat(UtilitySplitter.invoiceFor(LocalDate.of(2026, 11, 5), invoices)).get()
                .extracting(UtilityInvoice::invoiceDate).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(UtilitySplitter.invoiceFor(LocalDate.of(2026, 9, 1), invoices)).isEmpty();
    }

    @Test
    void repaymentGuardPairsCardPaymentsAndFlagsACodedRepayment() {
        Classified cardIn = excluded("c1", "card.pdf", AccountType.CREDIT_CARD, "2026-10-18", "1644.10", "PAYMENT RECEIVED");
        Classified bankOut = excluded("b1", "bank.pdf", AccountType.BUSINESS, "2026-10-17", "-1644.10", "Mastercard");
        Classified cardIn2 = excluded("c2", "card.pdf", AccountType.CREDIT_CARD, "2026-11-21", "400.00", "PAYMENT RECEIVED");
        Classified codedOut = coded("b2", "chq.pdf", AccountType.CHEQUE, "2026-11-21", "-400.00", "Mastercard pmt", "734");
        Classified farAway = excluded("c3", "card.pdf", AccountType.CREDIT_CARD, "2026-12-01", "99.00", "PAYMENT RECEIVED");
        Classified tooLate = coded("b3", "chq.pdf", AccountType.CHEQUE, "2026-12-09", "-99.00", "Mastercard", "734");

        List<Flag> flags = RepaymentGuard.check(List.of(cardIn, bankOut, cardIn2, codedOut, farAway, tooLate), 3);

        assertThat(flags).singleElement().satisfies(f -> {
            assertThat(f.lineId()).isEqualTo("b2");
            assertThat(f.kind()).isEqualTo(Flag.Kind.DOUBLE_COUNT);
        });
    }

    @Test
    void refundsCancelTheirPurchaseOnlyOnTheSameAccountAndAfterIt() {
        Classified purchase = coded("p", "card.pdf", AccountType.CREDIT_CARD, "2026-11-09", "-89.50", "OFFICE SUPPLY DEPOT", "734");
        Classified refund = coded("r", "card.pdf", AccountType.CREDIT_CARD, "2026-11-23", "89.50", "OFFICE SUPPLY DEPOT REFUND", "734");
        Classified otherCard = coded("o", "visa.pdf", AccountType.CREDIT_CARD, "2026-11-23", "89.50", "OFFICE SUPPLY DEPOT REFUND", "734");
        Classified earlier = coded("e", "card.pdf", AccountType.CREDIT_CARD, "2026-11-01", "12.00", "BOOKNOOK REFUND", "734");
        Classified later = coded("l", "card.pdf", AccountType.CREDIT_CARD, "2026-11-05", "-12.00", "BOOKNOOK", "734");

        assertThat(Pairing.refunds(List.of(purchase, refund, otherCard, earlier, later))).containsExactly(
                java.util.Map.entry("r", "p"));
    }

    @Test
    void offshoreMarginsPairWithTheirChargeAndConversionsWithTheirPurchase() {
        ForeignAmount usd = new ForeignAmount(Money.of("12.00"), "USD");
        Classified charge = Lines.line("ch", "visa.pdf", AccountType.CREDIT_CARD, "2026-10-10", "2026-10-11", "-19.77",
                "CLOUDNOTE.IO", "739", GstTreatment.NO_GST, null, usd, null, false);
        Classified margin = Lines.line("m", "visa.pdf", AccountType.CREDIT_CARD, "2026-10-10", "2026-10-11", "-0.49",
                "OFFSHORE SERVICE MARGINS", "739", GstTreatment.NO_GST, Pairing.OFFSHORE_MARGIN, null, null, false);
        Classified lonely = Lines.line("m2", "visa.pdf", AccountType.CREDIT_CARD, "2026-10-12", "2026-10-12", "-0.10",
                "OFFSHORE SERVICE MARGINS", "739", GstTreatment.NO_GST, Pairing.OFFSHORE_MARGIN, null, null, false);
        Classified exchange = Lines.line("x", "spend.pdf", AccountType.SPEND, "2026-10-14", "2026-10-14", "-34.31",
                "Exchange to USD", "739", GstTreatment.NO_GST, Pairing.FX_CONVERSION, new ForeignAmount(Money.of("20.00"), "USD"), null, false);
        Classified purchase = Lines.line("p", "spend.pdf", AccountType.SPEND, "2026-10-14", "2026-10-14", "0.00",
                "NOTEFORGE AI", "739", GstTreatment.NO_GST, Pairing.FX_PURCHASE, new ForeignAmount(Money.of("-20.00"), "USD"), null, false);

        assertThat(Pairing.margins(List.of(charge, margin, lonely))).containsExactly(java.util.Map.entry("m", "ch"));
        assertThat(Pairing.conversions(List.of(exchange, purchase))).containsExactly(java.util.Map.entry("p", "x"));
    }

    @ParameterizedTest(name = "''{0}'' -> ''{1}''")
    @CsvSource(delimiter = '|', value = {
        "Park on Quay 412345****** 4821 09552 | Park on Quay",
        "APPLE.COM/BILL                       | ''",
        "KAWAKAWA CAFE                         | KAWAKAWA CAFE",
        "4B-12 (Apartment Rent-4B-12 S000123  | ''"
    })
    void merchantNames(String description, String expected) {
        assertThat(Merchants.name(description)).isEqualTo(expected);
    }
}
