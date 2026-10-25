package nz.co.ksktech.cashbook.ingest.parse;

import static nz.co.ksktech.cashbook.ingest.Fixtures.cell;
import static nz.co.ksktech.cashbook.ingest.Fixtures.page;
import static nz.co.ksktech.cashbook.ingest.Fixtures.row;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.Reconciliation;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.ingest.highlight.Highlights;
import nz.co.ksktech.cashbook.ingest.text.PageText;
import org.junit.jupiter.api.Test;

/** Hand-built pages for edge cases the synthetic period does not pin down on its own. */
class ParserFixtureTest {

    /** Highlights only lines that sit at baseline 158 (the second transaction below). */
    private static final Highlights ONLY_AT_158 = (page, line, cells) -> Math.round(line.bottom()) == 158;

    @Test
    void transactionSearchAccountAcrossAPageBreakWithANegativeBalance() {
        PageText p1 = page(1,
                row(100, 7, cell(39, "Transactions for  Cheque Account 99 0123 0456790 000 between 1 Oct 2026 and 31 Oct 2026")),
                row(122, 8, cell(45, "Date"), cell(95, "Description"), cell(215, "Details"), cell(335, "Type"),
                        cell(417, "Money out"), cell(467, "Money in"), cell(512, "Balance")),
                row(140, 7, cell(45, "20 Oct 2026"), cell(95, "Business Everyday"), cell(215, "Drawings"),
                        cell(335, "DIRECT CREDIT"), cell(467, "$3,000.00")),
                row(158, 7, cell(45, "20 Oct 2026"), cell(95, "GREEN GROCER CO"), cell(215, "412345 0123"),
                        cell(335, "EFTPOS"), cell(417, "$16.60")),
                row(166, 7, cell(335, "TRANSACTION")),
                row(830, 7, cell(570, "1")));
        PageText p2 = page(2,
                row(51, 7, cell(45, "20 Oct 2026"), cell(95, "Balance at end of day"), cell(215, "Balance at end of day"),
                        cell(512, "- $4,047.35")),
                row(69, 7, cell(45, "1 Oct 2026"), cell(95, "Opening balance for"), cell(215, "Opening balance for"),
                        cell(512, "- $7,030.75")),
                row(77, 7, cell(95, "transactions"), cell(215, "transactions")),
                row(104, 7, cell(446, "Total Money In"), cell(530, "$3,000.00")),
                row(114, 7, cell(446, "Total Money Out"), cell(526, "- $16.60")));

        StatementParser parser = TransactionSearchParser.account();
        assertThat(parser.matches(List.of(p1, p2))).isTrue();
        StatementParser.Parsed parsed = parser.parse("cheque.pdf", List.of(p1, p2), ONLY_AT_158, IngestOptions.defaults());

        assertThat(parsed.transactions()).extracting(Transaction::description, t -> t.amount().toPlainString(),
                        Transaction::highlighted)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Business Everyday Drawings", "3000.00", false),
                        org.assertj.core.groups.Tuple.tuple("GREEN GROCER CO 412345 0123", "-16.60", true));
        assertThat(parsed.transactions().get(1).source().rawText()).endsWith("TRANSACTION");
        assertThat(parsed.summary().accountType()).isEqualTo(AccountType.CHEQUE);
        assertThat(parsed.summary().openingBalance()).isEqualByComparingTo("-7030.75");
        assertThat(parsed.summary().closingBalance()).isEqualByComparingTo("-4047.35");
        assertThat(Reconciliation.check(parsed.summary(), parsed.transactions()).problems()).isEmpty();
    }

    @Test
    void visaForeignBlockMarginAndCreditIgnoringTheContactLine() {
        PageText p1 = page(1,
                row(104, 11, cell(356, "Card Number:"), cell(434, "XXXX-XXXX-XXXX-7314")),
                row(200, 16, cell(28, "1 October 2026 - 31 October 2026")),
                row(232, 10, cell(32, "Card"), cell(60, "Date"), cell(124, "Description"), cell(381, "Amount"),
                        cell(431, "Date Processed"), cell(513, "Balance")),
                row(262, 10, cell(122, "CLOUDNOTE.IO SAN FRANCISCO")),
                row(272, 10, cell(29, "7314"), cell(58, "10 Oct 2026"), cell(122, "12.00 US DOLLAR"),
                        cell(395, "$20.69"), cell(450, "11 Oct 2026"), cell(513, "$120.69")),
                row(283, 10, cell(122, "at a Conversion Rate* of 0.5800 (NZ$20.69)")),
                row(300, 10, cell(29, "7314"), cell(58, "10 Oct 2026"), cell(122, "OFFSHORE SERVICE MARGINS"),
                        cell(400, "$0.52"), cell(450, "11 Oct 2026"), cell(513, "$121.21")),
                row(320, 10, cell(29, "7314"), cell(58, "26 Oct 2026"), cell(122, "PAYMENT RECEIVED visa"),
                        cell(370, "CR $50.00"), cell(450, "26 Oct 2026"), cell(513, "$71.21")),
                row(400, 9, cell(80, "CONTACT US 0800 000 000 Harbourside Bank Limited")));

        StatementParser.Parsed parsed = new VisaPrintParser()
                .parse("visa.pdf", List.of(p1), (page, line, cells) -> false, IngestOptions.defaults());

        assertThat(parsed.transactions()).hasSize(3);
        Transaction charge = parsed.transactions().getFirst();
        assertThat(charge.description()).isEqualTo("CLOUDNOTE.IO SAN FRANCISCO");
        assertThat(charge.amount()).isEqualByComparingTo("-20.69");
        assertThat(charge.foreign().amount()).isEqualByComparingTo("12.00");
        assertThat(charge.foreign().currency()).isEqualTo("USD");
        assertThat(charge.processedDate()).hasToString("2026-10-11");
        assertThat(charge.source().rawText()).contains("Conversion Rate");
        assertThat(parsed.transactions().get(2).amount()).isEqualByComparingTo("50.00");
        assertThat(parsed.summary().openingBalance()).isEqualByComparingTo("100.00");
        assertThat(parsed.summary().closingBalance()).isEqualByComparingTo("71.21");
        assertThat(Reconciliation.check(parsed.summary(), parsed.transactions()).problems()).isEmpty();
    }
}
