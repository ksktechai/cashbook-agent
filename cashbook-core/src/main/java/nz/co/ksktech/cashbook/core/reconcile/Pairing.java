package nz.co.ksktech.cashbook.core.reconcile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pairs lines that belong together: refunds with purchases, offshore margins and FX conversions with charges. */
public final class Pairing {

    public static final String OFFSHORE_MARGIN = "offshoreMargin";
    public static final String FX_CONVERSION = "fxConversion";
    public static final String FX_PURCHASE = "fxPurchase";

    private Pairing() {}

    /**
     * Refunds that cancel a purchase: same account, the refund's description starts with the purchase's
     * merchant, equal and opposite amounts, refund on or after the purchase. Returns refund id -> purchase id.
     */
    public static Map<String, String> refunds(List<Classified> coded) {
        Map<String, String> pairs = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (Classified refund : coded) {
            var r = refund.transaction();
            if (r.amount().signum() <= 0) {
                continue;
            }
            for (Classified purchase : coded) {
                var p = purchase.transaction();
                String merchant = Merchants.name(p.description()).toUpperCase(Locale.ROOT);
                if (used.contains(purchase.id()) || p.amount().signum() >= 0 || merchant.isEmpty()
                        || p.accountType() != r.accountType() || !p.source().file().equals(r.source().file())
                        || p.amount().negate().compareTo(r.amount()) != 0 || r.transactionDate().isBefore(p.transactionDate())
                        || !r.description().toUpperCase(Locale.ROOT).startsWith(merchant)) {
                    continue;
                }
                pairs.put(refund.id(), purchase.id());
                used.add(purchase.id());
                break;
            }
        }
        return pairs;
    }

    /** Each offshore margin line -> the foreign charge it belongs to (same file and date, a foreign amount). */
    public static Map<String, String> margins(List<Classified> coded) {
        Map<String, String> pairs = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < coded.size(); i++) {
            Classified margin = coded.get(i);
            if (!OFFSHORE_MARGIN.equals(margin.special())) {
                continue;
            }
            // the charge comes just before its margin; search backwards first, then anywhere
            List<Classified> candidates = new ArrayList<>(coded.subList(0, i).reversed());
            candidates.addAll(coded.subList(i + 1, coded.size()));
            for (Classified charge : candidates) {
                var c = charge.transaction();
                if (!used.contains(charge.id()) && c.foreign() != null && !OFFSHORE_MARGIN.equals(charge.special())
                        && c.source().file().equals(margin.transaction().source().file())
                        && c.transactionDate().equals(margin.transaction().transactionDate())) {
                    pairs.put(margin.id(), charge.id());
                    used.add(charge.id());
                    break;
                }
            }
        }
        return pairs;
    }

    /** Each foreign-currency purchase -> the conversion that paid for it (same file, date and foreign amount). */
    public static Map<String, String> conversions(List<Classified> coded) {
        Map<String, String> pairs = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (Classified purchase : coded) {
            var p = purchase.transaction();
            if (!FX_PURCHASE.equals(purchase.special()) || p.foreign() == null) {
                continue;
            }
            for (Classified conversion : coded) {
                var c = conversion.transaction();
                if (!used.contains(conversion.id()) && FX_CONVERSION.equals(conversion.special()) && c.foreign() != null
                        && c.source().file().equals(p.source().file()) && c.transactionDate().equals(p.transactionDate())
                        && c.foreign().currency().equals(p.foreign().currency())
                        && c.foreign().amount().abs().compareTo(p.foreign().amount().abs()) == 0) {
                    pairs.put(purchase.id(), conversion.id());
                    used.add(conversion.id());
                    break;
                }
            }
        }
        return pairs;
    }
}
