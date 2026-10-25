package nz.co.ksktech.cashbook.synthetic.xlsx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Saving workbooks so that the same content always gives the same bytes. */
public final class Workbooks {

    private static final String AUTHOR = "cashbook-agent synthetic generator";
    private static final Date FIXED_TIMESTAMP = Date.from(Instant.parse("2027-04-05T09:00:00Z"));
    private static final LocalDateTime FIXED_ENTRY_TIME = LocalDateTime.of(2027, 4, 5, 9, 0);

    private Workbooks() {}

    static void fixProperties(XSSFWorkbook wb) {
        POIXMLProperties.CoreProperties core = wb.getProperties().getCoreProperties();
        core.setCreator(AUTHOR);
        core.setLastModifiedByUser(AUTHOR);
        core.setCreated(Optional.of(FIXED_TIMESTAMP));
        core.setModified(Optional.of(FIXED_TIMESTAMP));
        wb.getProperties().getExtendedProperties().getUnderlyingProperties().setApplication(AUTHOR);
    }

    /** Evaluates formulas (so readers see cached values) and writes with fixed zip entry times. */
    public static void save(XSSFWorkbook wb, Path file) {
        fixProperties(wb);
        XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            wb.write(raw);
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, normalise(raw.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    /** Rewrites a zip with every entry's time fixed, keeping entry order and content. */
    static byte[] normalise(byte[] zip) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip));
                ZipOutputStream z = new ZipOutputStream(out)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                ZipEntry copy = new ZipEntry(e.getName());
                copy.setTimeLocal(FIXED_ENTRY_TIME);
                z.putNextEntry(copy);
                in.transferTo(z);
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    public static XSSFWorkbook open(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return new XSSFWorkbook(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}
