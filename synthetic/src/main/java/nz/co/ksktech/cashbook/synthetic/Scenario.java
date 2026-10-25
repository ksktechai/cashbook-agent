package nz.co.ksktech.cashbook.synthetic;

import static nz.co.ksktech.cashbook.core.GstTreatment.EXEMPT;
import static nz.co.ksktech.cashbook.core.GstTreatment.NO_GST;
import static nz.co.ksktech.cashbook.core.GstTreatment.STANDARD;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.synthetic.model.Expected;
import nz.co.ksktech.cashbook.synthetic.model.ForeignSpec;
import nz.co.ksktech.cashbook.synthetic.model.GoldenSpec;
import nz.co.ksktech.cashbook.synthetic.model.GoldenSpec.Item;
import nz.co.ksktech.cashbook.synthetic.model.GoldenSpec.Section;
import nz.co.ksktech.cashbook.synthetic.model.HighlightColour;
import nz.co.ksktech.cashbook.synthetic.model.InvoiceSpec;
import nz.co.ksktech.cashbook.synthetic.model.Layout;
import nz.co.ksktech.cashbook.synthetic.model.LevySpec;
import nz.co.ksktech.cashbook.synthetic.model.LevySpec.LevyLine;
import nz.co.ksktech.cashbook.synthetic.model.SpecLine;
import nz.co.ksktech.cashbook.synthetic.model.SplitRow;
import nz.co.ksktech.cashbook.synthetic.model.StatementSpec;
import nz.co.ksktech.cashbook.synthetic.model.SyntheticWorld;
import nz.co.ksktech.cashbook.synthetic.model.Trap;

/**
 * Builds the fictional Oct 2026 - Mar 2027 period from a seed. Trap amounts are fixed so tests can name
 * them; everyday amounts, filler spending, usage and FX rates come from the seed.
 */
public final class Scenario {

    public static final Period PERIOD = Period.parse("2026-10:2027-03");

    private static final List<String> PERSONAL_MERCHANTS = List.of(
            "HARBOUR FRESH MARKET", "KIWI FUEL FAKEVILLE", "CORNER DAIRY", "FAKEVILLE PHARMACY", "BOOKNOOK",
            "TAKEAWAY TIME", "SAMPLE ST BAKERY", "MOVIE MAGIC", "GREEN GROCER CO", "HARDWARE HUB");
    private static final List<String> LUNCH_SPOTS = List.of("HARBOURVIEW BISTRO", "THE LUNCH ROOM", "WHARF ESPRESSO");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT);
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final String CARD_REPAYMENT = "card repayment";
    private static final String NOT_HIGHLIGHTED = "personal (not highlighted)";
    private static final String CARD_PURCHASE = "PURCHASES";
    private static final String EFTPOS = "EFTPOS TRANSACTION";

    private final Random random;
    private final List<GoldenSpec> golden = new ArrayList<>();
    private final List<SplitRow> split = new ArrayList<>();
    private final Builder business = new Builder("kb");
    private final Builder cheque = new Builder("kc");
    private final Builder card = new Builder("km");
    private final Builder visa = new Builder("hv");
    private final Builder harbour = new Builder("ha");
    private final Builder spend = new Builder("sp");

    private Scenario(long seed) {
        this.random = new Random(seed);
    }

    public static SyntheticWorld build(long seed) {
        return new Scenario(seed).world(seed);
    }

    private SyntheticWorld world(long seed) {
        List<InvoiceSpec> invoices = fibrewave();
        List<LevySpec> levies = levies();
        for (YearMonth month = PERIOD.start(); !month.isAfter(PERIOD.end()); month = month.plusMonths(1)) {
            businessMonth(month);
            chequeMonth(month);
            cardMonth(month);
            visaMonth(month);
            harbourMonth(month);
            spendMonth(month);
        }
        oneOffTraps();
        LocalDate from = PERIOD.firstDay();
        LocalDate to = PERIOD.lastDay();
        List<StatementSpec> statements = List.of(
                new StatementSpec("BusinessEveryday-05Apr27.pdf", Layout.KAURI_ACCOUNT_EXPORT, Fictional.KAURI,
                        AccountType.BUSINESS, Fictional.BUSINESS_ACCOUNT_NAME, Fictional.BUSINESS_ACCOUNT, from, to,
                        Cents.of("18450.00") + random.nextInt(50_000), business.lines(), false,
                        Set.of("NEWEST_FIRST", "END_OF_DAY_BALANCE_ROWS", "TYPE_WRAPS", "OPENING_BALANCE_LAST")),
                new StatementSpec("ChequeAccount-05Apr27.pdf", Layout.KAURI_ACCOUNT_EXPORT, Fictional.KAURI,
                        AccountType.CHEQUE, Fictional.CHEQUE_ACCOUNT_NAME, Fictional.CHEQUE_ACCOUNT, from, to,
                        -Cents.of("8250.00"), cheque.lines(), true,
                        Set.of("NEWEST_FIRST", "END_OF_DAY_BALANCE_ROWS", "NEGATIVE_BALANCES", "FLATTENED_HIGHLIGHTS")),
                new StatementSpec("KauriRewardsMastercard-05Apr27.pdf", Layout.KAURI_CARD_EXPORT, Fictional.KAURI,
                        AccountType.CREDIT_CARD, Fictional.KAURI_CARD_NAME, Fictional.KAURI_CARD_LAST4, from, to,
                        0, card.lines(), false, Set.of("NEWEST_FIRST", "NO_BALANCE_COLUMN")),
                new StatementSpec("Harbourside-NetBank-Credit-visa.pdf", Layout.HARBOUR_VISA, Fictional.HARBOURSIDE,
                        AccountType.CREDIT_CARD, "Visa Statement", Fictional.HARBOUR_VISA_LAST4, from, to,
                        Cents.of("642.18"), visa.lines(), false,
                        Set.of("OLDEST_FIRST", "CR_CREDITS", "FOREIGN_THREE_LINE_BLOCK", "BROWSER_PRINT")),
                new StatementSpec("Harbourside-Online-30567890.pdf", Layout.HARBOUR_ACCOUNT, Fictional.HARBOURSIDE,
                        AccountType.CHEQUE, "Everyday", Fictional.HARBOUR_ACCOUNT, from, to,
                        Cents.of("2310.55"), harbour.lines(), false,
                        Set.of("OLDEST_FIRST", "DESCRIPTION_STRADDLES_AMOUNT")),
                new StatementSpec("spend_account_statement_2026-10-01_2027-03-31.pdf", Layout.SHAREPOT_SPEND,
                        Fictional.SHAREPOT, AccountType.SPEND, "Sharepot Spend account", Fictional.SPEND_ACCOUNT,
                        from, to, Cents.of("12.40"), spend.lines(), false,
                        Set.of("OLDEST_FIRST", "COLUMNS_SHIFT_BETWEEN_PAGES")));
        List<GoldenSpec> sortedGolden = golden.stream()
                .sorted(Comparator.comparing(GoldenSpec::month))
                .toList();
        return new SyntheticWorld(seed, PERIOD, statements, invoices, levies, sortedGolden, split);
    }

    // ---------------------------------------------------------------- Kauri business account

    private void businessMonth(YearMonth month) {
        int index = (int) PERIOD.start().until(month, ChronoUnit.MONTHS);
        String monthName = month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ROOT);

        SpecLine rent = business.at(month.atDay(2), "4B-12 (Apartment", -Cents.of("2400.00"))
                .details("Rent-4B-12 S000123").type("AUTOMATIC PAYMENT")
                .expect(Expected.coded(Codes.APARTMENT_RENT, EXEMPT, "apartment rent (residential, exempt)")).add();
        gold(month, Codes.APARTMENT_RENT, "Apartment Rent", rent.cents(), EXEMPT, rent);

        SpecLine mobile = business.at(month.atDay(5), "Tui Mobile", -Cents.of("65.00"))
                .details("TM 4400123").type("DIRECT DEBIT")
                .expect(Expected.coded(Codes.MOBILE, STANDARD, "mobile plan")).add();
        gold(month, Codes.MOBILE, "Mobile Plan - Tui Mobile NZ", mobile.cents(), STANDARD, mobile);

        business.at(month.atDay(8), "Kauri Online Saver", -Cents.of("500.00"))
                .details("Sweep 01").type("AUTOMATIC PAYMENT")
                .expect(Expected.excluded("savings sweep")).traps(Trap.SAVINGS_SWEEP).add();

        int hours = 40 + random.nextInt(41);
        String invoice = "INV-" + (1041 + index);
        SpecLine income = business.at(month.atDay(15), "Kereru Digital Limited", hours * 17_250L)
                .details(invoice + " Kereru payment").type("DIRECT CREDIT").highlight(randomColour())
                .expect(Expected.coded(Codes.INCOME, STANDARD, "client receipt")).add();
        gold(month, Codes.INCOME, "Income - " + monthName + " - Kereru Digital (Invoice " + invoice + ")",
                income.cents(), STANDARD, income);
        if (month.getMonthValue() == 1) {
            SpecLine second = business.at(month.atDay(28), "Pohutukawa Analytics Ltd", Cents.of("4140.00"))
                    .details("PA-0007 consulting").type("DIRECT CREDIT").highlight(randomColour())
                    .expect(Expected.coded(Codes.INCOME, STANDARD, "client receipt")).add();
            gold(month, Codes.INCOME, "Income - January - Pohutukawa Analytics (Invoice PA-0007)", second.cents(),
                    STANDARD, second);
        }

        long repayment = Cents.of("1200.00") + random.nextInt(120_000);
        business.at(month.atDay(18), Fictional.KAURI_CARD_NAME, -repayment)
                .details("MC Internet Transfer").type("ONLINE BANKING")
                .expect(Expected.excluded(CARD_REPAYMENT)).traps(Trap.CARD_REPAYMENT).add();
        card.at(month.atDay(18), "PAYMENT RECEIVED       THANK YOU     " + Fictional.KAURI_CARD_LAST4, repayment)
                .details(reference()).expect(Expected.excluded(CARD_REPAYMENT)).traps(Trap.CARD_REPAYMENT).add();

        business.at(month.atDay(20), Fictional.CHEQUE_ACCOUNT_NAME, -Cents.of("3000.00"))
                .details("Drawings " + month.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ROOT))
                .type("ONLINE BANKING").expect(Expected.excluded("transfer between own accounts"))
                .traps(Trap.TRANSFER).add();
        cheque.at(month.atDay(20), Fictional.BUSINESS_ACCOUNT_NAME, Cents.of("3000.00"))
                .details("Drawings").type("DIRECT CREDIT").expect(Expected.excluded("transfer between own accounts"))
                .traps(Trap.TRANSFER).add();

        if (month.getMonthValue() == 10 || month.getMonthValue() == 1) {
            business.at(month.atDay(12), "1234Sharepot Spend", -Cents.of("200.00"))
                    .details("Spend Card Top Up - 1").type("BILL PAYMENT")
                    .expect(Expected.excluded("spend account top-up (the spend lines are coded instead)"))
                    .traps(Trap.SPEND_TOP_UP).add();
            spend.at(month.atDay(12), "Top up from bank account", Cents.of("200.00"))
                    .expect(Expected.excluded("spend account top-up")).traps(Trap.SPEND_TOP_UP).add();
        }

        List<SpecLine> parks = new ArrayList<>();
        for (int day : distinctDays(2 + random.nextInt(3), month)) {
            parks.add(business.at(month.atDay(day), "Park on Quay", -(450 + random.nextInt(80) * 10L))
                    .details("412345****** " + Fictional.KAURI_CARD_LAST4 + " 0" + (1000 + random.nextInt(9000)))
                    .type(EFTPOS).expect(Expected.coded(Codes.CAR_PARKING, STANDARD, "car park (summed monthly)"))
                    .traps(Trap.MONTHLY_AGGREGATE).add());
        }
        gold(month, Codes.CAR_PARKING, "Car Park Fees", sum(parks), STANDARD, Section.CAR_PARKS,
                parks.stream().map(p -> new Item(p.date(), p.date().format(FULL_DATE), -p.cents(), "Kauri")).toList(),
                parks.toArray(SpecLine[]::new));

        if (month.getMonthValue() != 12) { // the fee is waived in December
            SpecLine fee = business.at(month.atEndOfMonth(), "Account maintenance", -Cents.of("5.00"))
                    .details("0123 0456789-00").type("Account Fee Charge")
                    .expect(Expected.coded(Codes.BANK_FEES, EXEMPT, "bank fee (exempt)")).add();
            gold(month, Codes.BANK_FEES, "Bank Fees", fee.cents(), EXEMPT, fee);
        }
    }

    // ---------------------------------------------------------------- Kauri cheque account

    private void chequeMonth(YearMonth month) {
        SpecLine insurance = cheque.at(month.atDay(7), "Kauri Insurance", -Cents.of("48.20"))
                .details("Kauri H0012345C1").type("DIRECT DEBIT").highlight(randomColour())
                .expect(Expected.coded(Codes.CONTENTS_INSURANCE, STANDARD, "contents insurance (home office share)"))
                .traps(Trap.FLATTENED_HIGHLIGHT).add();
        gold(month, Codes.CONTENTS_INSURANCE, "Home Contents Insurance", insurance.cents(), STANDARD,
                Section.INSURANCE, List.of(new Item(insurance.date(), "Contents insurance - Kauri", -insurance.cents(),
                        "Cheque")), insurance);

        cheque.at(month.atDay(9), "Kauri-OnlineSaver-Sweep", -Cents.of("100.00"))
                .details("OnlineSaver Sweep").type("AUTOMATIC PAYMENT")
                .expect(Expected.excluded("savings sweep")).traps(Trap.SAVINGS_SWEEP).add();

        long repayment = Cents.of("300.00") + random.nextInt(50_000);
        cheque.at(month.atDay(21), Fictional.KAURI_CARD_NAME, -repayment)
                .details("Mastercard pmt").type("ONLINE BANKING")
                .expect(Expected.excluded(CARD_REPAYMENT)).traps(Trap.CARD_REPAYMENT).add();
        card.at(month.atDay(21), "PAYMENT RECEIVED       THANK YOU     " + Fictional.KAURI_CARD_LAST4, repayment)
                .details(reference()).expect(Expected.excluded(CARD_REPAYMENT)).traps(Trap.CARD_REPAYMENT).add();

        for (int day : distinctDays(6 + random.nextInt(5), month)) {
            cheque.at(month.atDay(day), personalMerchant(), -(500 + random.nextInt(17_500)))
                    .details("412345****** " + Fictional.KAURI_CARD_LAST4 + " 0" + (1000 + random.nextInt(9000)))
                    .type(EFTPOS).expect(Expected.excluded(NOT_HIGHLIGHTED)).add();
        }
    }

    // ---------------------------------------------------------------- Kauri Mastercard

    private void cardMonth(YearMonth month) {
        SpecLine apple = card.at(month.atDay(3), "APPLE.COM/BILL", -Cents.of("54.98")).details(reference())
                .type(CARD_PURCHASE).highlight(randomColour())
                .expect(Expected.coded(Codes.HOME_OFFICE, STANDARD, "mixed: $40.00 business subscription, rest personal"))
                .traps(Trap.MIXED_CHARGE).add();
        String appleDetail = "iCloud+ 2TB subscription ($54.98 less $14.98 personal)";
        gold(month, Codes.HOME_OFFICE, "Home Office Expenses - " + appleDetail, -Cents.of("40.00"), STANDARD,
                Section.BUSINESS_EXPENSES, List.of(new Item(apple.date(), appleDetail, Cents.of("40.00"), "Kauri MC")),
                apple);

        List<SpecLine> lunches = new ArrayList<>();
        int count = 1 + random.nextInt(2);
        for (int day : distinctDays(count, month)) {
            lunches.add(card.at(month.atDay(day), LUNCH_SPOTS.get(random.nextInt(LUNCH_SPOTS.size())),
                            -(2500 + random.nextInt(60) * 100L))
                    .details(reference()).type(CARD_PURCHASE).highlight(randomColour())
                    .expect(Expected.coded(Codes.ENTERTAINMENT, STANDARD, "business lunch"))
                    .add());
        }
        if (month.getMonthValue() == 1) {
            lunches.add(card.at(month.atDay(29), "KAWAKAWA CAFE", -Cents.of("46.50")).details(reference())
                    .type(CARD_PURCHASE).highlight(randomColour())
                    .expect(Expected.coded(Codes.ENTERTAINMENT, STANDARD, "cafe: ask whether it was business"))
                    .traps(Trap.POLICY_QUESTION).add());
        }
        List<Item> lunchItems = lunches.stream()
                .map(l -> new Item(l.date(), "Business lunch - " + titleCase(l.description()), -l.cents(), "Kauri MC"))
                .toList();
        gold(month, Codes.ENTERTAINMENT, "Entertainment (Business Lunch/Coffee/Meals/Drinks)", sum(lunches), STANDARD,
                Section.ENTERTAINMENT, lunchItems, lunches.toArray(SpecLine[]::new));

        for (int day : distinctDays(8 + random.nextInt(6), month)) {
            card.at(month.atDay(day), personalMerchant(), -(300 + random.nextInt(14_700))).details(reference())
                    .type(CARD_PURCHASE).expect(Expected.excluded(NOT_HIGHLIGHTED)).add();
        }
    }

    // ---------------------------------------------------------------- Harbourside Visa

    private void visaMonth(YearMonth month) {
        LocalDate day = month.atDay(10);
        String rate = rate("0.5650", 450);
        long nzd = Cents.fromForeign(1200, rate);
        SpecLine charge = visa.at(day, "CLOUDNOTE.IO SAN FRANCISCO", -nzd).processed(day.plusDays(1))
                .foreign(new ForeignSpec(1200, "USD", rate, "US DOLLAR")).highlight(randomColour())
                .expect(Expected.coded(Codes.NO_GST_PURCHASES, NO_GST, "offshore subscription, no GST"))
                .traps(Trap.FOREIGN_CURRENCY).add();
        long marginCents = Cents.percent(nzd, "2.5");
        SpecLine margin = visa.at(day, "OFFSHORE SERVICE MARGINS", -marginCents).processed(day.plusDays(1))
                .highlight(randomColour())
                .expect(Expected.coded(Codes.NO_GST_PURCHASES, NO_GST, "offshore margin, paired with its charge"))
                .traps(Trap.OFFSHORE_MARGIN).add();
        gold(month, Codes.NO_GST_PURCHASES, "Online Tech Subscriptions (NO GST) - CloudNote", -(nzd + marginCents),
                NO_GST, Section.NO_GST_SUBSCRIPTIONS,
                List.of(new Item(day, "CloudNote (US$12) - " + day.format(FULL_DATE), nzd, "Harbourside Visa"),
                        new Item(day, "Offshore service margin - " + day.format(FULL_DATE), marginCents,
                                "Harbourside Visa")),
                charge, margin);

        long repayment = Cents.of("150.00") + random.nextInt(25_000);
        visa.at(month.atDay(26), "PAYMENT RECEIVED visa", repayment)
                .expect(Expected.excluded(CARD_REPAYMENT)).traps(Trap.CARD_REPAYMENT).add();
        harbour.at(month.atDay(26), "HARBOURSIDE CARD " + Fictional.HARBOUR_VISA_LAST4 + "visa", -repayment)
                .expect(Expected.excluded(CARD_REPAYMENT)).traps(Trap.CARD_REPAYMENT).add();

        for (int d : distinctDays(4 + random.nextInt(4), month)) {
            LocalDate date = month.atDay(d);
            visa.at(date, personalMerchant() + " NZ", -(800 + random.nextInt(9_200)))
                    .processed(date.plusDays(random.nextInt(3)))
                    .expect(Expected.excluded(NOT_HIGHLIGHTED)).add();
        }
    }

    // ---------------------------------------------------------------- Harbourside account

    private void harbourMonth(YearMonth month) {
        harbour.at(month.atDay(1), "AUTOMATIC PAYMENT J SAMPLE\nFLATSHARE UNIT4B", Cents.of("600.00"))
                .expect(Expected.excluded(NOT_HIGHLIGHTED)).traps(Trap.WRAPPED_TEXT).add();
        harbour.at(month.atDay(4), "FAKEVILLE GYM DEBIT\n" + month.atDay(4).format(DAY_MONTH) + " 4821", -Cents.of("55.00"))
                .expect(Expected.excluded(NOT_HIGHLIGHTED)).traps(Trap.WRAPPED_TEXT).add();
        harbour.at(month.atEndOfMonth(), "CREDIT INTEREST PAID", 50 + random.nextInt(270))
                .expect(Expected.excluded(NOT_HIGHLIGHTED)).add();
        if (month.getMonthValue() == 12) {
            harbour.at(month.atDay(14), "TRANSFER TO A L EXAMPLE\n" + Fictional.CHEQUE_ACCOUNT.replace(' ', '-'),
                            -Cents.of("500.00"))
                    .expect(Expected.excluded("transfer between own accounts"))
                    .traps(Trap.TRANSFER, Trap.WRAPPED_TEXT, Trap.REDACTION_TARGET).add();
            cheque.at(month.atDay(14), "A L EXAMPLE", Cents.of("500.00")).details("Harbourside transfer")
                    .type("DIRECT CREDIT").expect(Expected.excluded("transfer between own accounts"))
                    .traps(Trap.TRANSFER).add();
        }
    }

    // ---------------------------------------------------------------- Sharepot spend account

    private void spendMonth(YearMonth month) {
        spend.at(month.atDay(1), "Transfer from Wallet", Cents.of("150.00"))
                .expect(Expected.excluded("spend account top-up")).traps(Trap.SPEND_TOP_UP).add();
        LocalDate day = month.atDay(14);
        String rate = rate("0.5700", 400);
        long nzd = Cents.fromForeign(2000, rate);
        long fee = Cents.percent(nzd, "0.5");
        SpecLine exchange = spend.at(day, "Exchange to USD", -(nzd + fee))
                .foreign(new ForeignSpec(2000, "USD", rate, "US DOLLAR")).fee(fee).highlight(randomColour())
                .expect(Expected.coded(Codes.NO_GST_PURCHASES, NO_GST, "NZD debited for the USD charge, fee included"))
                .traps(Trap.FX_CONVERSION_FEE).add();
        SpecLine purchase = spend.at(day, "NOTEFORGE AI", 0)
                .foreign(new ForeignSpec(-2000, "USD", rate, "US DOLLAR")).highlight(randomColour())
                .expect(Expected.coded(Codes.NO_GST_PURCHASES, NO_GST, "USD charge, costed by its currency exchange"))
                .traps(Trap.FOREIGN_CURRENCY).add();
        String detail = "NoteForge AI (US$20 incl. Sharepot fee) - " + day.format(FULL_DATE);
        gold(month, Codes.NO_GST_PURCHASES, "Online Tech Subscriptions (NO GST) - NoteForge AI", exchange.cents(),
                NO_GST, Section.NO_GST_SUBSCRIPTIONS, List.of(new Item(day, detail, nzd + fee, "Sharepot")),
                exchange, purchase);
        for (int d : distinctDays(3 + random.nextInt(3), month)) {
            spend.at(month.atDay(d), titleCase(personalMerchant()), -(400 + random.nextInt(2_100)))
                    .expect(Expected.excluded(NOT_HIGHLIGHTED)).add();
        }
    }

    // ---------------------------------------------------------------- one-off traps

    private void oneOffTraps() {
        LocalDate cardDate = LocalDate.of(2026, 9, 30);
        SpecLine domain = visa.at(cardDate, "DOMAINSNZ.EXAMPLE AUCKLAND", -Cents.of("35.00"))
                .processed(LocalDate.of(2026, 10, 1)).highlight(randomColour())
                .expect(Expected.coded(Codes.HOME_OFFICE, STANDARD, "card date before the period, processed inside it"))
                .traps(Trap.PERIOD_CUT_OFF).add();
        homeOffice(YearMonth.of(2026, 10), "Domain renewal (card 30/09, processed 01/10)", domain, "Harbourside Visa");

        LocalDate refundDay = LocalDate.of(2026, 11, 9);
        card.at(refundDay, "OFFICE SUPPLY DEPOT", -Cents.of("89.50")).details(reference()).type(CARD_PURCHASE)
                .highlight(randomColour()).expect(Expected.excluded("refunded in full later this month"))
                .traps(Trap.REFUND_NETS_TO_ZERO).add();
        card.at(refundDay.plusDays(14), "OFFICE SUPPLY DEPOT REFUND", Cents.of("89.50")).details(reference())
                .type("REFUNDS").highlight(randomColour()).expect(Expected.excluded("refund of an earlier purchase"))
                .traps(Trap.REFUND_NETS_TO_ZERO).add();

        SpecLine indemnity = visa.at(LocalDate.of(2026, 11, 4), "SHIELD PROFESSIONAL INDEMNITY", -Cents.of("1240.00"))
                .processed(LocalDate.of(2026, 11, 5)).highlight(randomColour())
                .expect(Expected.coded(Codes.INDEMNITY_INSURANCE, STANDARD, "professional indemnity, annual")).add();
        gold(YearMonth.of(2026, 11), Codes.INDEMNITY_INSURANCE, "Indemnity Insurance - Shield (annual)",
                indemnity.cents(), STANDARD, Section.INSURANCE,
                List.of(new Item(indemnity.date(), "Indemnity insurance - Shield (annual)", -indemnity.cents(),
                        "Harbourside Visa")), indemnity);

        SpecLine laptop = card.at(LocalDate.of(2026, 12, 12), "TECHWORLD FAKEVILLE", -Cents.of("2899.00"))
                .details(reference()).type(CARD_PURCHASE).highlight(randomColour())
                .expect(Expected.coded(Codes.HOME_OFFICE, STANDARD, "laptop; over the threshold, may need capitalising"))
                .traps(Trap.HIGH_VALUE).add();
        homeOffice(YearMonth.of(2026, 12), "Laptop (may need capitalising)", laptop, "Kauri MC");

        SpecLine paper = visa.at(LocalDate.of(2026, 12, 31), "FAKEVILLE STATIONERY CO", -Cents.of("24.50"))
                .processed(LocalDate.of(2027, 1, 4)).highlight(randomColour())
                .expect(Expected.coded(Codes.HOME_OFFICE, STANDARD, "card date in December, processed in January"))
                .traps(Trap.MONTH_BOUNDARY).add();
        homeOffice(YearMonth.of(2026, 12), "Printer paper and toner (card 31/12, processed 04/01)", paper,
                "Harbourside Visa");

        card.at(LocalDate.of(2027, 1, 14), "Kauri Insurance", Cents.of("35.00")).details(reference())
                .type("PAYMENT").expect(Expected.excluded("personal insurance refund, not a card repayment"))
                .traps(Trap.CREDIT_NOT_REPAYMENT).add();

        LocalDate audDay = LocalDate.of(2027, 2, 16);
        String audRate = rate("0.9050", 300);
        long aud = Cents.fromForeign(1800, audRate);
        SpecLine kanbanly = visa.at(audDay, "KANBANLY PTY SYDNEY", -aud).processed(audDay.plusDays(1))
                .foreign(new ForeignSpec(1800, "AUD", audRate, "AUSTRALIAN DOLLAR")).highlight(randomColour())
                .expect(Expected.coded(Codes.NO_GST_PURCHASES, NO_GST, "offshore subscription, no GST"))
                .traps(Trap.FOREIGN_CURRENCY).add();
        long audMargin = Cents.percent(aud, "2.5");
        SpecLine kanbanlyMargin = visa.at(audDay, "OFFSHORE SERVICE MARGINS", -audMargin).processed(audDay.plusDays(1))
                .highlight(randomColour())
                .expect(Expected.coded(Codes.NO_GST_PURCHASES, NO_GST, "offshore margin, paired with its charge"))
                .traps(Trap.OFFSHORE_MARGIN).add();
        gold(YearMonth.of(2027, 2), Codes.NO_GST_PURCHASES, "Online Tech Subscriptions (NO GST) - Kanbanly",
                -(aud + audMargin), NO_GST, Section.NO_GST_SUBSCRIPTIONS,
                List.of(new Item(audDay, "Kanbanly (A$18) - " + audDay.format(FULL_DATE), aud, "Harbourside Visa"),
                        new Item(audDay, "Offshore service margin - " + audDay.format(FULL_DATE), audMargin,
                                "Harbourside Visa")),
                kanbanly, kanbanlyMargin);

        // The next visa row after a foreign block sits close enough to pick up a sliver of its highlight.
        visa.at(LocalDate.of(2027, 2, 16), "FAKEVILLE PHARMACY NZ", -Cents.of("18.40"))
                .processed(audDay.plusDays(1)).expect(Expected.excluded(NOT_HIGHLIGHTED))
                .traps(Trap.HIGHLIGHT_BLEED).add();

        business.at(LocalDate.of(2026, 11, 3), "Kauri ATM Fakeville Ctr", -Cents.of("200.00"))
                .details("************ " + Fictional.KAURI_CARD_LAST4 + "CASH").type("ATM TRANSACTION")
                .expect(Expected.excluded("ATM cash")).traps(Trap.ATM_CASH).add();
        business.at(LocalDate.of(2026, 11, 28), "1234IRD GST", -Cents.of("2345.67"))
                .details("IRD " + Fictional.IRD_NUMBER + " GST").type("BILL PAYMENT")
                .expect(Expected.excluded("GST payment to IRD")).traps(Trap.TAX_PAYMENT, Trap.REDACTION_TARGET).add();
        business.at(LocalDate.of(2027, 1, 15), "1234IRD PTAX", -Cents.of("1500.00"))
                .details("IRD " + Fictional.IRD_NUMBER + " PTAX").type("BILL PAYMENT")
                .expect(Expected.excluded("provisional tax")).traps(Trap.TAX_PAYMENT, Trap.REDACTION_TARGET).add();
        business.at(LocalDate.of(2027, 2, 10), "Inland Revenue", Cents.of("412.30"))
                .details("IRD " + Fictional.IRD_NUMBER + " GST REFUND").type("DIRECT CREDIT")
                .expect(Expected.excluded("GST refund, not income")).traps(Trap.GST_REFUND, Trap.REDACTION_TARGET).add();
        for (YearMonth month : List.of(YearMonth.of(2026, 11), YearMonth.of(2027, 2))) {
            long fee = month.getMonthValue() == 11 ? Cents.of("1150.00") : Cents.of("575.00");
            SpecLine accountant = business.at(month.atDay(22), "1234Ledger", -fee).details("L&Co Inv-55" + month.getMonthValue())
                    .type("BILL PAYMENT").highlight(randomColour())
                    .expect(Expected.coded(Codes.ACCOUNTANCY, STANDARD, "accountant fee")).add();
            gold(month, Codes.ACCOUNTANCY, "Accountant Fee (Accountancy)", accountant.cents(), STANDARD, accountant);
        }
    }

    // ---------------------------------------------------------------- Fibrewave invoices and their card payments

    private List<InvoiceSpec> fibrewave() {
        List<InvoiceSpec> invoices = new ArrayList<>();
        long previousDue = Cents.of("185.40");
        long previousPayment = previousDue;
        LocalDate previousDate = LocalDate.of(2026, 8, 20);
        for (int k = 0; k < 7; k++) {
            LocalDate date = LocalDate.of(2026, 9, 20).plusMonths(k);
            LocalDate from = previousDate.plusDays(1);
            int days = (int) ChronoUnit.DAYS.between(from, date) + 1;
            BigDecimal dailyRate = new BigDecimal("0.330");
            long daily = Cents.times(BigDecimal.valueOf(days), dailyRate);
            BigDecimal kwh = BigDecimal.valueOf(2_600_000L + random.nextInt(2_600_000), 4);
            BigDecimal usageRate = new BigDecimal("31.250");
            long usage = Cents.times(kwh, usageRate.movePointLeft(2));
            long discount = Cents.percent(daily + usage, "10");
            boolean credit = k == 2;
            InvoiceSpec invoice = new InvoiceSpec(
                    "fibrewave/" + uuid(), Fictional.FIBREWAVE_ACCOUNT, Integer.toString(412_300_100 + k * 37), date,
                    date.plusDays(14), from, date, previousDate, previousDue, credit ? Cents.of("25.00") : 0,
                    credit ? "Credit - service outage compensation" : null, days, dailyRate, daily, kwh, usageRate,
                    usage, discount, Cents.of("99.99"), Cents.of("10.00"), Cents.percent(previousPayment, "1.5"));
            invoices.add(invoice);
            LocalDate payDay = date.plusMonths(1).withDayOfMonth(5);
            if (PERIOD.contains(payDay)) {
                payInvoice(invoice, payDay);
            }
            previousDue = invoice.dueCents();
            previousPayment = invoice.dueCents();
            previousDate = date;
        }
        return invoices;
    }

    private void payInvoice(InvoiceSpec invoice, LocalDate payDay) {
        long paid = invoice.dueCents();
        SpecLine payment = card.at(payDay, "FIBREWAVE INTERNET", -paid).details(reference()).type(CARD_PURCHASE)
                .highlight(randomColour())
                .expect(Expected.coded(Codes.POWER, STANDARD, "utility: split into Power (725) and Internet (727)"))
                .traps(invoice.creditCents() > 0 ? EnumSet.of(Trap.UTILITY_SPLIT, Trap.UTILITY_CREDIT)
                        : EnumSet.of(Trap.UTILITY_SPLIT)).add();
        long power;
        long fee;
        long internet;
        String footnote = null;
        if (invoice.creditCents() == 0) {
            power = invoice.powerSubtotalCents();
            fee = invoice.cardFeeCents();
            internet = invoice.broadbandSubtotalCents();
        } else {
            long total = invoice.totalCents();
            power = Cents.ratio(paid, invoice.powerSubtotalCents(), total);
            fee = Cents.ratio(paid, invoice.cardFeeCents(), total);
            internet = paid - power - fee;
            footnote = "* " + invoice.invoiceDate().format(DAY_MONTH) + " invoice $" + Cents.format(total) + " less $"
                    + Cents.format(invoice.creditCents()) + " credit = $" + Cents.format(paid)
                    + " due; split pro-rata to the invoice charges.";
        }
        SplitRow row = new SplitRow(payDay, invoice.invoiceDate(), paid, power, fee, internet, footnote);
        split.add(row);
        YearMonth month = YearMonth.from(payDay);
        gold(month, Codes.POWER, "Power - Fibrewave", -row.power725Cents(), STANDARD, payment);
        gold(month, Codes.INTERNET, "Internet - Fibrewave", -row.internetCents(), STANDARD, payment);
    }

    // ---------------------------------------------------------------- body corporate levies

    private List<LevySpec> levies() {
        List<LevySpec> levies = new ArrayList<>();
        for (LocalDate start : List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 1, 1))) {
            long admin = Cents.of("1640.00") + random.nextInt(100) * 100L;
            long ltmf = Cents.of("420.00");
            long insurance = Cents.of("910.00") + random.nextInt(60) * 100L;
            List<LevyLine> lines = List.of(
                    new LevyLine("Admin - Operating Fund", admin * 4, admin, Cents.percent(admin, "15")),
                    new LevyLine("Long Term - Maint Fund", ltmf * 4, ltmf, Cents.percent(ltmf, "15")),
                    new LevyLine("Insurance - Fire Fund", insurance * 4, insurance, Cents.percent(insurance, "15")));
            LevySpec levy = new LevySpec("body-corp/" + start + "-Levy-Notice-Unit-4B.pdf",
                    Integer.toString(7_700_000 + start.getMonthValue()), start, start.plusDays(30), start,
                    start.plusMonths(3).minusDays(1), lines, 10, start.getMonthValue() == 1);
            levies.add(levy);
            LocalDate paid = start.plusDays(19);
            SpecLine payment = cheque.at(paid, "4BBodyCorp-Kowhai", -levy.netDueCents())
                    .details("Body-Corp L000" + levy.invoiceNumber()).type("BILL PAYMENT").highlight(randomColour())
                    .expect(Expected.coded(Codes.RATES_BODY_CORP, STANDARD, "body-corp levy, net of early-payment discount"))
                    .traps(Trap.LEVY_DISCOUNT, Trap.FLATTENED_HIGHLIGHT).add();
            String quarter = start.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ROOT) + "-"
                    + levy.periodTo().getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ROOT);
            gold(YearMonth.from(paid), Codes.RATES_BODY_CORP,
                    "Body Corp Fees - Kowhai BC levy " + quarter + " (net of early-payment discount)",
                    payment.cents(), STANDARD, payment);
        }
        return levies;
    }

    // ---------------------------------------------------------------- helpers

    private void homeOffice(YearMonth month, String detail, SpecLine line, String note) {
        gold(month, Codes.HOME_OFFICE, "Home Office Expenses - " + detail, line.cents(), STANDARD,
                Section.BUSINESS_EXPENSES, List.of(new Item(line.date(), detail, -line.cents(), note)), line);
    }

    private void gold(YearMonth month, String code, String detail, long cents, GstTreatment gst, SpecLine... sources) {
        gold(month, code, detail, cents, gst, Section.NONE, List.of(), sources);
    }

    private void gold(YearMonth month, String code, String detail, long cents, GstTreatment gst, Section section,
            List<Item> items, SpecLine... sources) {
        golden.add(new GoldenSpec(month, code, detail, cents, gst,
                java.util.Arrays.stream(sources).map(SpecLine::id).toList(), section, items));
    }

    private static long sum(List<SpecLine> lines) {
        return lines.stream().mapToLong(SpecLine::cents).sum();
    }

    private HighlightColour randomColour() {
        HighlightColour[] colours = HighlightColour.values();
        return colours[random.nextInt(colours.length)];
    }

    private String personalMerchant() {
        return PERSONAL_MERCHANTS.get(random.nextInt(PERSONAL_MERCHANTS.size()));
    }

    private String reference() {
        return Integer.toString(100_000 + random.nextInt(900_000));
    }

    /** A printed conversion rate: base plus up to {@code spread} ten-thousandths. */
    private String rate(String base, int spread) {
        return new BigDecimal(base).add(BigDecimal.valueOf(random.nextInt(spread), 4)).toPlainString();
    }

    private String uuid() {
        return new java.util.UUID(random.nextLong(), random.nextLong()) + ".pdf";
    }

    /** Distinct days 1-28 (avoids the fixed-day lines clashing at month end), sorted. */
    private List<Integer> distinctDays(int count, YearMonth month) {
        Set<Integer> days = new TreeSet<>();
        while (days.size() < count) {
            int day = 1 + random.nextInt(Math.min(28, month.lengthOfMonth()));
            if (day != 3 && day != 5 && day != 10 && day != 14) {
                days.add(day);
            }
        }
        return List.copyOf(days);
    }

    private static String titleCase(String upper) {
        StringBuilder b = new StringBuilder();
        for (String word : upper.toLowerCase(Locale.ROOT).split(" ")) {
            if (!b.isEmpty()) {
                b.append(' ');
            }
            b.append(word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return b.toString();
    }

    /** Collects one statement's lines; ids are prefix + sequence, in creation order. */
    private static final class Builder {
        private final String prefix;
        private final List<SpecLine> lines = new ArrayList<>();

        Builder(String prefix) {
            this.prefix = prefix;
        }

        Draft at(LocalDate date, String description, long cents) {
            return new Draft(this, date, description, cents);
        }

        List<SpecLine> lines() {
            return lines.stream().sorted(Comparator.comparing(SpecLine::date)).toList();
        }

        SpecLine add(Draft d) {
            SpecLine line = new SpecLine(prefix + "-" + String.format(Locale.ROOT, "%03d", lines.size() + 1), d.date,
                    d.processed == null ? d.date : d.processed, d.description, d.details, d.type, d.cents, d.foreign,
                    d.fee, d.highlight, d.expected, d.traps);
            lines.add(line);
            return line;
        }
    }

    /** Fluent builder for one line. */
    private static final class Draft {
        private final Builder owner;
        private final LocalDate date;
        private final String description;
        private final long cents;
        private LocalDate processed;
        private String details = "";
        private String type = "";
        private ForeignSpec foreign;
        private long fee;
        private HighlightColour highlight;
        private Expected expected;
        private Set<Trap> traps = EnumSet.noneOf(Trap.class);

        Draft(Builder owner, LocalDate date, String description, long cents) {
            this.owner = owner;
            this.date = date;
            this.description = description;
            this.cents = cents;
        }

        Draft processed(LocalDate value) {
            processed = value;
            return this;
        }

        Draft details(String value) {
            details = value;
            return this;
        }

        Draft type(String value) {
            type = value;
            return this;
        }

        Draft foreign(ForeignSpec value) {
            foreign = value;
            return this;
        }

        Draft fee(long value) {
            fee = value;
            return this;
        }

        Draft highlight(HighlightColour value) {
            highlight = value;
            return this;
        }

        Draft expect(Expected value) {
            expected = value;
            return this;
        }

        Draft traps(Trap... values) {
            traps = values.length == 0 ? EnumSet.noneOf(Trap.class) : EnumSet.copyOf(List.of(values));
            return this;
        }

        Draft traps(Set<Trap> values) {
            traps = values;
            return this;
        }

        SpecLine add() {
            return owner.add(this);
        }
    }
}
