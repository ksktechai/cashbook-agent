package nz.co.ksktech.cashbook.ingest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import nz.co.ksktech.cashbook.core.LevyNotice;
import nz.co.ksktech.cashbook.core.Reconciliation;
import nz.co.ksktech.cashbook.core.StatementSummary;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.core.UtilityInvoice;
import nz.co.ksktech.cashbook.ingest.highlight.HighlightDetector;
import nz.co.ksktech.cashbook.ingest.highlight.Highlights;
import nz.co.ksktech.cashbook.ingest.ocr.OcrEngine;
import nz.co.ksktech.cashbook.ingest.ocr.TesseractOcr;
import nz.co.ksktech.cashbook.ingest.parse.AccountPrintParser;
import nz.co.ksktech.cashbook.ingest.parse.DocumentParsers;
import nz.co.ksktech.cashbook.ingest.parse.SpendStatementParser;
import nz.co.ksktech.cashbook.ingest.parse.StatementParser;
import nz.co.ksktech.cashbook.ingest.parse.TransactionSearchParser;
import nz.co.ksktech.cashbook.ingest.parse.VisaPrintParser;
import nz.co.ksktech.cashbook.ingest.text.PageText;
import nz.co.ksktech.cashbook.ingest.text.PdfTextExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Reads every PDF under a folder into transactions, statement summaries and supplier documents. */
public final class Ingestor {

    private static final String PDF = ".pdf";

    private final IngestOptions options;
    private final List<StatementParser> parsers = List.of(TransactionSearchParser.account(),
            TransactionSearchParser.card(), new VisaPrintParser(), new AccountPrintParser(), new SpendStatementParser());

    public Ingestor(IngestOptions options) {
        this.options = options;
    }

    /** A statement with the check of its lines against its printed figures. */
    public record StatementResult(StatementSummary summary, Reconciliation reconciliation) {}

    /** Everything read from a folder. {@code skipped} explains each file that was not read. */
    public record Result(List<Transaction> transactions, List<StatementResult> statements,
            List<UtilityInvoice> invoices, List<LevyNotice> levies, List<String> skipped) {

        public Result {
            transactions = List.copyOf(transactions);
            statements = List.copyOf(statements);
            invoices = List.copyOf(invoices);
            levies = List.copyOf(levies);
            skipped = List.copyOf(skipped);
        }

        public boolean allReconciled() {
            return statements.stream().allMatch(s -> s.reconciliation().reconciled());
        }
    }

    public Result ingest(Path folder) {
        List<Transaction> transactions = new ArrayList<>();
        List<StatementResult> statements = new ArrayList<>();
        List<UtilityInvoice> invoices = new ArrayList<>();
        List<LevyNotice> levies = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        PdfTextExtractor extractor = new PdfTextExtractor(ocrEngine(skipped), options.ocrDpi());
        for (Path pdf : pdfs(folder)) {
            String file = folder.relativize(pdf).toString().replace('\\', '/');
            try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
                List<PageText> pages = extractor.extract(doc);
                Optional<StatementParser> parser = parsers.stream().filter(p -> p.matches(pages)).findFirst();
                if (parser.isPresent()) {
                    Highlights highlights = new HighlightDetector(doc, (float) options.highlightThreshold());
                    StatementParser.Parsed parsed = parser.get().parse(file, pages, highlights, options);
                    transactions.addAll(parsed.transactions());
                    statements.add(new StatementResult(parsed.summary(),
                            Reconciliation.check(parsed.summary(), parsed.transactions())));
                } else if (DocumentParsers.isUtilityInvoice(pages)) {
                    invoices.add(DocumentParsers.utilityInvoice(file, pages));
                } else if (DocumentParsers.isLevyNotice(pages)) {
                    levies.add(DocumentParsers.levyNotice(file, pages));
                } else {
                    skipped.add(file + ": layout not recognised"
                            + (pages.stream().allMatch(p -> p.lines().isEmpty()) ? " (no text, and OCR is off)" : ""));
                }
            } catch (IOException | RuntimeException e) {
                skipped.add(file + ": " + e.getMessage());
            }
        }
        return new Result(transactions, statements, invoices, levies, skipped);
    }

    private OcrEngine ocrEngine(List<String> skipped) {
        if (!options.ocrEnabled()) {
            return null;
        }
        Optional<String> tessdata = TesseractOcr.findTessdata(options.tessdata());
        if (tessdata.isEmpty()) {
            skipped.add("OCR: no Tesseract language data found; image-only pages will not be read");
            return null;
        }
        return new TesseractOcr(tessdata.get(), options.ocrDpi());
    }

    private static List<Path> pdfs(Path folder) {
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(PDF))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + folder, e);
        }
    }
}
