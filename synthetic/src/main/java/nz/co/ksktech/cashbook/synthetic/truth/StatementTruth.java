package nz.co.ksktech.cashbook.synthetic.truth;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import nz.co.ksktech.cashbook.core.AccountType;

/** Ground truth for one statement file: the figures a parser must read and reconcile. */
public record StatementTruth(
        String file,
        String layout,
        String institution,
        AccountType accountType,
        String accountName,
        String accountNumber,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal moneyIn,
        BigDecimal moneyOut,
        int transactions,
        int pages,
        List<String> layoutTraps) {}
