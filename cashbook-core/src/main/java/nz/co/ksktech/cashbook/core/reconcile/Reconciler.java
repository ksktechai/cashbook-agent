package nz.co.ksktech.cashbook.core.reconcile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import nz.co.ksktech.cashbook.core.CashbookLine;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.SourceRef;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.core.UtilityInvoice;

/**
 * Turns classified transactions into cashbook lines. Pure: no I/O. In order: drop pending and excluded
 * lines (the double-count guard checks repayments first), net refunds, split utility payments, pair FX
 * conversions and offshore margins, apply mixed charges, flag period cut-off and high-value lines, then
 * sum monthly codes. A flag's answer ({@code decisions}: flag id -> keep) is honoured; unanswered flags
 * keep their line.
 */
public final class Reconciler {

    private static final String UTILITY_SPLIT = "utilitySplit";
    private static final String MIXED_CHARGE = "mixedCharge";
    private static final String POWER = "725";
    private static final String INTERNET = "727";
    private static final BigDecimal GST_FACTOR = new BigDecimal("1.15");

    private final ReconcileOptions options;
    private final Map<String, String> codeNames;

    /** @param codeNames code -> name from the rules file, for line details */
    public Reconciler(ReconcileOptions options, Map<String, String> codeNames) {
        this.options = options;
        this.codeNames = Map.copyOf(codeNames);
    }

    /** Lines in month order, itemised detail, the utility split table, flags, and notes on what was set aside. */
    public record Result(List<CashbookLine> lines, List<SectionItem> items, List<UtilitySplit> splits,
            List<Flag> flags, List<String> notes, List<String> unreviewed) {

        public Result {
            lines = List.copyOf(lines);
            items = List.copyOf(items);
            splits = List.copyOf(splits);
            flags = List.copyOf(flags);
            notes = List.copyOf(notes);
            unreviewed = List.copyOf(unreviewed);
        }
    }

    /** A cashbook line being built, before monthly codes are summed. */
    private record Draft(YearMonth month, String code, String detail, BigDecimal amount, GstTreatment gst,
            List<Transaction> sources, List<SectionItem> items, String lineId) {}

    public Result reconcile(List<Classified> classified, List<UtilityInvoice> invoices, Map<String, Boolean> decisions) {
        List<Flag> flags = new ArrayList<>(RepaymentGuard.check(classified, options.repaymentWindowDays()));
        List<String> notes = new ArrayList<>();
        List<String> unreviewed = new ArrayList<>();
        List<Classified> coded = new ArrayList<>();
        for (Classified c : classified) {
            if (c.pending()) {
                unreviewed.add(c.id() + " " + c.transaction().transactionDate() + " " + c.transaction().amount() + " "
                        + c.transaction().description());
            } else if (c.classification().gst() != GstTreatment.EXCLUDED && c.classification().code() != null) {
                coded.add(c);
            }
        }
        coded.removeIf(c -> Boolean.FALSE.equals(decisions.get(Flag.idFor(c.id(), Flag.Kind.DOUBLE_COUNT))));

        Set<String> consumed = new HashSet<>();
        Pairing.refunds(coded).forEach((refund, purchase) -> {
            consumed.add(refund);
            consumed.add(purchase);
            notes.add("refund " + refund + " cancels purchase " + purchase + "; both left out");
        });
        Map<String, Classified> byId = new LinkedHashMap<>();
        coded.forEach(c -> byId.put(c.id(), c));
        Map<String, String> margins = Pairing.margins(coded);
        Map<String, String> marginOf = new HashMap<>();
        margins.forEach((margin, charge) -> marginOf.put(charge, margin));
        Map<String, String> conversions = Pairing.conversions(coded);
        Set<String> pairedConversions = new HashSet<>(conversions.values());

        List<Draft> drafts = new ArrayList<>();
        List<UtilitySplit> splits = new ArrayList<>();
        for (Classified c : coded) {
            if (consumed.contains(c.id()) || margins.containsKey(c.id()) || pairedConversions.contains(c.id())) {
                continue; // folded into its partner's line
            }
            Transaction t = c.transaction();
            Optional<YearMonth> month = month(c, flags, decisions);
            if (month.isEmpty()) {
                notes.add(c.id() + " is outside the period; left out");
                continue;
            }
            if (UTILITY_SPLIT.equals(c.special())) {
                drafts.addAll(utility(c, month.get(), invoices, splits, flags));
            } else if (Pairing.FX_PURCHASE.equals(c.special())) {
                drafts.add(fxPurchase(c, month.get(), conversions.get(c.id()) == null ? null : byId.get(conversions.get(c.id())),
                        flags));
            } else if (marginOf.containsKey(c.id())) {
                drafts.add(withMargin(c, byId.get(marginOf.get(c.id())), month.get()));
            } else if (MIXED_CHARGE.equals(c.special()) && c.businessAmount() != null) {
                drafts.add(mixed(c, month.get()));
            } else {
                if (Pairing.OFFSHORE_MARGIN.equals(c.special())) {
                    flags.add(Flag.of(c.id(), Flag.Kind.UNPAIRED_FX, "Offshore margin with no matching foreign charge: keep it on its own?"));
                } else if (Pairing.FX_CONVERSION.equals(c.special())) {
                    flags.add(Flag.of(c.id(), Flag.Kind.UNPAIRED_FX, "Currency exchange with no matching purchase: keep it on its own?"));
                }
                drafts.add(plain(c, month.get(), t.amount(), Merchants.label(t.description()), List.of(t)));
            }
        }
        drafts.removeIf(d -> d.lineId() != null && highValue(d, flags, decisions));
        drafts.removeIf(d -> d.lineId() != null && Boolean.FALSE.equals(decisions.get(Flag.idFor(d.lineId(),
                Flag.Kind.UNPAIRED_FX))));

        List<CashbookLine> lines = new ArrayList<>();
        List<SectionItem> items = new ArrayList<>();
        Map<String, List<Draft>> monthly = new LinkedHashMap<>();
        for (Draft d : drafts) {
            items.addAll(d.items());
            if (options.monthlyCodes().contains(d.code()) || isMonthlyVendor(d, coded)) {
                monthly.computeIfAbsent(d.month() + "|" + d.code(), k -> new ArrayList<>()).add(d);
            } else {
                lines.add(new CashbookLine(d.month(), d.code(), d.detail(), d.amount(), refs(d.sources()), d.gst()));
            }
        }
        monthly.values().forEach(group -> {
            Draft first = group.getFirst();
            BigDecimal sum = group.stream().map(Draft::amount).reduce(Money.ZERO, BigDecimal::add);
            List<Transaction> sources = group.stream().flatMap(d -> d.sources().stream()).toList();
            lines.add(new CashbookLine(first.month(), first.code(), codeName(first.code()) + " (" + sources.size() + ")",
                    sum, refs(sources), first.gst()));
        });
        lines.sort(Comparator.comparing(CashbookLine::month).thenComparing(CashbookLine::code));
        items.sort(Comparator.comparing(SectionItem::month).thenComparing(SectionItem::date));
        splits.sort(Comparator.comparing(UtilitySplit::cardDate));
        flags.removeIf(f -> decisions.containsKey(f.id()));
        return new Result(lines, items, splits, flags, notes, unreviewed);
    }

    /** The month a line belongs to; a card date before the period but processed inside it is flagged. */
    private Optional<YearMonth> month(Classified c, List<Flag> flags, Map<String, Boolean> decisions) {
        LocalDate date = c.transaction().transactionDate();
        LocalDate processed = c.transaction().processedDate();
        if (options.period().contains(date)) {
            return Optional.of(YearMonth.from(date));
        }
        if (date.isBefore(options.period().firstDay()) && options.period().contains(processed)) {
            Flag flag = Flag.of(c.id(), Flag.Kind.PERIOD_CUT_OFF, "Card date " + date + " is before the period but it was"
                    + " processed on " + processed + ". Keep it in " + options.period().start() + "?");
            flags.add(flag);
            return Boolean.FALSE.equals(decisions.get(flag.id())) ? Optional.empty() : Optional.of(options.period().start());
        }
        return Optional.empty();
    }

    /** Flags (and, if answered "exclude", removes) purchases over the capitalisation threshold. */
    private boolean highValue(Draft d, List<Flag> flags, Map<String, Boolean> decisions) {
        if (d.amount().signum() >= 0 || d.gst() != GstTreatment.STANDARD || !options.capitaliseCodes().contains(d.code())) {
            return false;
        }
        BigDecimal exclGst = d.amount().negate().divide(GST_FACTOR, Money.SCALE, Money.ROUNDING);
        if (exclGst.compareTo(options.capitaliseThresholdExclGst()) <= 0) {
            return false;
        }
        Flag flag = Flag.of(d.lineId(), Flag.Kind.HIGH_VALUE, d.detail() + " is " + exclGst + " excl. GST, over "
                + options.capitaliseThresholdExclGst() + ": it may need capitalising. Keep it as an expense?");
        flags.add(flag);
        return Boolean.FALSE.equals(decisions.get(flag.id()));
    }

    private List<Draft> utility(Classified c, YearMonth month, List<UtilityInvoice> invoices, List<UtilitySplit> splits,
            List<Flag> flags) {
        Transaction t = c.transaction();
        Optional<UtilityInvoice> invoice = UtilitySplitter.invoiceFor(t.transactionDate(), invoices);
        String merchant = Merchants.label(t.description());
        if (invoice.isEmpty()) {
            flags.add(Flag.of(c.id(), Flag.Kind.UTILITY_MISMATCH, "No invoice found for this utility payment; it is all"
                    + " under " + c.classification().code() + ". Keep it that way?"));
            return List.of(plain(c, month, t.amount(), merchant, List.of(t)));
        }
        if (invoice.get().due().compareTo(t.amount().abs()) != 0) {
            flags.add(Flag.of(c.id(), Flag.Kind.UTILITY_MISMATCH, "Paid " + t.amount().abs() + " but the "
                    + invoice.get().invoiceDate() + " invoice asked for " + invoice.get().due() + ". Keep the split?"));
        }
        UtilitySplit split = UtilitySplitter.split(t.amount(), t.transactionDate(), invoice.get());
        splits.add(split);
        return List.of(
                new Draft(month, POWER, codeName(POWER) + " - " + merchant, split.power725().negate(), c.classification().gst(),
                        List.of(t), List.of(), c.id()),
                new Draft(month, INTERNET, codeName(INTERNET) + " - " + merchant, split.internet().negate(),
                        c.classification().gst(), List.of(t), List.of(), null));
    }

    private Draft fxPurchase(Classified purchase, YearMonth month, Classified conversion, List<Flag> flags) {
        Transaction p = purchase.transaction();
        if (conversion == null) {
            flags.add(Flag.of(purchase.id(), Flag.Kind.UNPAIRED_FX, "Foreign purchase with no currency exchange to cost it:"
                    + " it has no NZD amount. Keep it?"));
            return plain(purchase, month, p.amount(), Merchants.label(p.description()), List.of(p));
        }
        Transaction c = conversion.transaction();
        String detail = Merchants.label(p.description()) + " (" + p.foreign().currency() + " " + p.foreign().amount().abs()
                + " incl. fee " + c.fxFee() + ")";
        return plain(purchase, month, c.amount(), detail, List.of(c, p));
    }

    private Draft withMargin(Classified charge, Classified margin, YearMonth month) {
        Transaction c = charge.transaction();
        Transaction m = margin.transaction();
        String detail = Merchants.label(c.description()) + (c.foreign() == null ? ""
                : " (" + c.foreign().currency() + " " + c.foreign().amount().abs() + ")") + " incl. offshore margin";
        List<SectionItem> items = new ArrayList<>(item(charge, month, c.amount(), Merchants.label(c.description())));
        items.addAll(item(margin, month, m.amount(), "Offshore service margin"));
        return new Draft(month, charge.classification().code(), detail, c.amount().add(m.amount()),
                charge.classification().gst(), List.of(c, m), items, charge.id());
    }

    private Draft mixed(Classified c, YearMonth month) {
        Transaction t = c.transaction();
        BigDecimal business = c.businessAmount().abs();
        BigDecimal amount = t.amount().signum() < 0 ? business.negate() : business;
        String detail = Merchants.label(t.description()) + " (" + t.amount().abs() + " less "
                + t.amount().abs().subtract(business) + " personal)";
        return new Draft(month, c.classification().code(), detail, amount, c.classification().gst(), List.of(t),
                item(c, month, amount, detail), c.id());
    }

    private Draft plain(Classified c, YearMonth month, BigDecimal amount, String detail, List<Transaction> sources) {
        return new Draft(month, c.classification().code(), detail, amount, c.classification().gst(), sources,
                item(c, month, amount, detail), c.id());
    }

    private List<SectionItem> item(Classified c, YearMonth month, BigDecimal amount, String description) {
        String section = options.sections().get(c.classification().code());
        if (section == null) {
            return List.of();
        }
        return List.of(new SectionItem(month, section, c.classification().code(), c.transaction().transactionDate(),
                description, amount.negate(), c.transaction().source().file()));
    }

    private static boolean isMonthlyVendor(Draft d, List<Classified> coded) {
        return coded.stream().anyMatch(c -> c.id().equals(d.lineId()) && "MONTHLY".equals(c.aggregate()));
    }

    private String codeName(String code) {
        return codeNames.getOrDefault(code, code);
    }

    private static List<SourceRef> refs(List<Transaction> sources) {
        return sources.stream().map(Transaction::source).toList();
    }
}
