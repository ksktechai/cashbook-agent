package nz.co.ksktech.cashbook.synthetic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import nz.co.ksktech.cashbook.core.CashbookLine;
import nz.co.ksktech.cashbook.core.ForeignAmount;
import nz.co.ksktech.cashbook.core.SourceRef;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.synthetic.model.GoldenSpec;
import nz.co.ksktech.cashbook.synthetic.model.InvoiceSpec;
import nz.co.ksktech.cashbook.synthetic.model.Layout;
import nz.co.ksktech.cashbook.synthetic.model.LevySpec;
import nz.co.ksktech.cashbook.synthetic.model.SpecLine;
import nz.co.ksktech.cashbook.synthetic.model.StatementSpec;
import nz.co.ksktech.cashbook.synthetic.model.SyntheticWorld;
import nz.co.ksktech.cashbook.synthetic.pdf.FibrewaveInvoiceRenderer;
import nz.co.ksktech.cashbook.synthetic.pdf.HarbourAccountRenderer;
import nz.co.ksktech.cashbook.synthetic.pdf.HarbourVisaRenderer;
import nz.co.ksktech.cashbook.synthetic.pdf.KauriExportRenderer;
import nz.co.ksktech.cashbook.synthetic.pdf.LevyNoticeRenderer;
import nz.co.ksktech.cashbook.synthetic.pdf.RenderResult;
import nz.co.ksktech.cashbook.synthetic.pdf.RenderedLine;
import nz.co.ksktech.cashbook.synthetic.pdf.SharepotRenderer;
import nz.co.ksktech.cashbook.synthetic.truth.DocumentTruth;
import nz.co.ksktech.cashbook.synthetic.truth.Jsonl;
import nz.co.ksktech.cashbook.synthetic.truth.Manifest;
import nz.co.ksktech.cashbook.synthetic.truth.StatementTruth;
import nz.co.ksktech.cashbook.synthetic.truth.TruthTransaction;
import nz.co.ksktech.cashbook.synthetic.xlsx.GoldenWorkbookWriter;
import nz.co.ksktech.cashbook.synthetic.xlsx.TemplateBuilder;
import nz.co.ksktech.cashbook.synthetic.xlsx.Workbooks;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Generates a synthetic period into an output folder:
 *
 * <pre>
 * in/                         statement, invoice and levy PDFs (the --in folder for a run)
 * template.xlsx               sanitised template
 * truth/transactions.jsonl    every statement line with its expected extraction and classification
 * truth/statements.jsonl      balances and totals per statement
 * truth/documents.jsonl       every figure on the invoices and levy notices
 * golden/cashbook.jsonl       the expected cashbook lines
 * golden/cashbook.xlsx        the template filled with the expected lines
 * manifest.json               seed and SHA-256 of every file
 * </pre>
 *
 * The same seed always produces byte-identical files on the same JDK.
 */
public final class SyntheticGenerator {

    public static final String VERSION = "1";
    public static final String IN = "in";
    public static final String TEMPLATE = "template.xlsx";
    public static final String TRUTH_TRANSACTIONS = "truth/transactions.jsonl";
    public static final String TRUTH_STATEMENTS = "truth/statements.jsonl";
    public static final String TRUTH_DOCUMENTS = "truth/documents.jsonl";
    public static final String GOLDEN_JSONL = "golden/cashbook.jsonl";
    public static final String GOLDEN_XLSX = "golden/cashbook.xlsx";
    public static final String MANIFEST = "manifest.json";

    private static final List<String> OUTPUTS = List.of(IN, "truth", "golden", TEMPLATE, MANIFEST);

    private SyntheticGenerator() {}

    public static Manifest generate(long seed, Path out) {
        SyntheticWorld world = Scenario.build(seed);
        clean(out);
        Path in = out.resolve(IN);
        Map<String, SpecLine> specLines = new LinkedHashMap<>();
        Map<String, RenderedLine> rendered = new LinkedHashMap<>();
        Map<String, String> lineFiles = new LinkedHashMap<>();
        List<StatementTruth> statements = new ArrayList<>();
        for (StatementSpec spec : world.statements()) {
            RenderResult result = render(spec, in.resolve(spec.file()));
            for (SpecLine line : spec.lines()) {
                specLines.put(line.id(), line);
                lineFiles.put(line.id(), spec.file());
            }
            result.lines().forEach(r -> rendered.put(r.lineId(), r));
            statements.add(new StatementTruth(spec.file(), spec.layout().name(), spec.institution(), spec.accountType(),
                    spec.accountName(), spec.accountNumber(), spec.from(), spec.to(), Cents.toMoney(spec.openingCents()),
                    Cents.toMoney(spec.closingCents()), Cents.toMoney(spec.moneyInCents()),
                    Cents.toMoney(spec.moneyOutCents()), spec.lines().size(), result.pages(),
                    spec.layoutTraps().stream().sorted().toList()));
        }
        List<DocumentTruth> documents = new ArrayList<>();
        for (InvoiceSpec invoice : world.invoices()) {
            RenderResult r = FibrewaveInvoiceRenderer.render(invoice, in.resolve(invoice.file()));
            documents.add(new DocumentTruth(invoice.file(), "UTILITY_INVOICE", r.pages(), invoice));
        }
        for (LevySpec levy : world.levies()) {
            RenderResult r = LevyNoticeRenderer.render(levy, in.resolve(levy.file()));
            documents.add(new DocumentTruth(levy.file(), "LEVY_NOTICE", r.pages(), levy));
        }

        try (XSSFWorkbook template = TemplateBuilder.build()) {
            Workbooks.save(template, out.resolve(TEMPLATE));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        GoldenWorkbookWriter.write(world, specLines, out.resolve(GOLDEN_XLSX));

        Map<String, StatementSpec> owners = new LinkedHashMap<>();
        world.statements().forEach(s -> s.lines().forEach(l -> owners.put(l.id(), s)));
        List<TruthTransaction> truth = specLines.values().stream()
                .map(l -> truthFor(l, owners.get(l.id()), rendered.get(l.id())))
                .toList();
        Jsonl.writeLines(out.resolve(TRUTH_TRANSACTIONS), truth);
        Jsonl.writeLines(out.resolve(TRUTH_STATEMENTS), statements);
        Jsonl.writeLines(out.resolve(TRUTH_DOCUMENTS), documents);
        Jsonl.writeLines(out.resolve(GOLDEN_JSONL), world.golden().stream()
                .map(g -> cashbookLine(g, lineFiles, rendered))
                .toList());

        Manifest manifest = new Manifest(seed, VERSION, world.period().toString(), files(out));
        Jsonl.writeJson(out.resolve(MANIFEST), manifest);
        return manifest;
    }

    private static RenderResult render(StatementSpec spec, Path file) {
        return switch (spec.layout()) {
            case KAURI_ACCOUNT_EXPORT, KAURI_CARD_EXPORT -> KauriExportRenderer.render(spec, file);
            case HARBOUR_VISA -> HarbourVisaRenderer.render(spec, file);
            case HARBOUR_ACCOUNT -> HarbourAccountRenderer.render(spec, file);
            case SHAREPOT_SPEND -> SharepotRenderer.render(spec, file);
        };
    }

    private static TruthTransaction truthFor(SpecLine line, StatementSpec spec, RenderedLine where) {
        if (where == null) {
            throw new IllegalStateException("line " + line.id() + " was not rendered");
        }
        SourceRef source = new SourceRef(spec.file(), where.page(), where.box(), where.rawText());
        ForeignAmount foreign = line.foreign() == null ? null
                : new ForeignAmount(Cents.toMoney(line.foreign().foreignCents()), line.foreign().currency());
        Transaction t = new Transaction(source, spec.institution(), spec.accountType(), line.date(), line.processed(),
                description(line, spec), Cents.toMoney(line.cents()), foreign,
                Cents.toMoney(line.fxFeeCents()), line.highlighted());
        return new TruthTransaction(line.id(), spec.layout().name(), t,
                line.highlight() == null ? null : line.highlight().name(), line.highlighted() && spec.flattenHighlights(),
                where.highlightBox(), line.expected(), line.traps().stream().map(Enum::name).sorted().toList());
    }

    /**
     * The description a parser should produce: Westpac-style account exports add the Details column
     * (e.g. "Drawings", "Spend Card Top Up"), and whitespace is collapsed.
     */
    private static String description(SpecLine line, StatementSpec spec) {
        String text = spec.layout() == Layout.KAURI_ACCOUNT_EXPORT && !line.details().isEmpty()
                ? line.description() + " " + line.details()
                : line.description();
        return text.replaceAll("\\s+", " ").trim();
    }

    private static CashbookLine cashbookLine(GoldenSpec g, Map<String, String> files, Map<String, RenderedLine> rendered) {
        List<SourceRef> sources = g.sourceIds().stream().map(id -> {
            RenderedLine r = rendered.get(id);
            return new SourceRef(files.get(id), r.page(), r.box(), r.rawText());
        }).toList();
        return new CashbookLine(g.month(), g.code(), g.detail(), Cents.toMoney(g.cents()), sources, g.gst());
    }

    /** Removes only what a previous run wrote, never anything else in the folder. */
    private static void clean(Path out) {
        for (String name : OUTPUTS) {
            Path p = out.resolve(name);
            if (!Files.exists(p)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(p)) {
                for (Path f : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(f);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("cannot clean " + p, e);
            }
        }
    }

    private static List<Manifest.FileEntry> files(Path out) {
        try (Stream<Path> walk = Files.walk(out)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(f -> OUTPUTS.contains(out.relativize(f).getName(0).toString()))
                    .filter(f -> !f.getFileName().toString().equals(MANIFEST))
                    .sorted()
                    .toList();
            List<Manifest.FileEntry> entries = new ArrayList<>();
            for (Path f : files) {
                byte[] bytes = Files.readAllBytes(f);
                entries.add(new Manifest.FileEntry(out.relativize(f).toString().replace('\\', '/'), bytes.length,
                        sha256(bytes)));
            }
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
