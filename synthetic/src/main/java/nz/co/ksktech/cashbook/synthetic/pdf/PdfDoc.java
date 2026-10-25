package nz.co.ksktech.cashbook.synthetic.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import java.awt.image.BufferedImage;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/**
 * A PDF being generated. Uses only the standard 14 fonts (nothing embedded) and fixes the dates and
 * document ID, so the same content always gives the same bytes.
 */
public final class PdfDoc implements AutoCloseable {

    public static final Face HELVETICA = Face.HELVETICA;
    public static final Face HELVETICA_BOLD = Face.HELVETICA_BOLD;
    public static final Face TIMES = Face.TIMES;

    /** Real statements use A4 (595x842); the Sharesies-style one is a point larger each way. */
    public static final PDRectangle A4 = new PDRectangle(595, 842);

    private static final String PRODUCER = "cashbook-agent synthetic generator";

    private final PDDocument document = new PDDocument();
    private final String name;
    private final List<PdfPage> pages = new ArrayList<>();
    private final PDRectangle size;
    private final java.util.Map<Face, PDType1Font> fonts = new java.util.EnumMap<>(Face.class);

    public PdfDoc(String name, PDRectangle size) {
        this.name = name;
        this.size = size;
    }

    public PdfPage newPage() {
        finishPages();
        PDPage page = new PDPage(size);
        document.addPage(page);
        PdfPage p = new PdfPage(document, page, pages.size() + 1, this::font);
        pages.add(p);
        return p;
    }

    private PDType1Font font(Face face) {
        return fonts.computeIfAbsent(face, Face::newFont);
    }

    public int pageCount() {
        return pages.size();
    }

    public List<PdfPage> pages() {
        return List.copyOf(pages);
    }

    /** Writes the file with fixed metadata and an ID derived from the file name. */
    public void save(Path file) {
        finishPages();
        fixMetadata(document);
        try {
            Files.createDirectories(file.getParent());
            document.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    private void fixMetadata(PDDocument target) {
        PDDocumentInformation info = target.getDocumentInformation();
        info.setProducer(PRODUCER);
        info.setCreator(PRODUCER);
        info.setTitle(name);
        Calendar fixed = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        fixed.clear();
        fixed.set(2027, Calendar.APRIL, 5, 9, 0, 0);
        info.setCreationDate(fixed);
        info.setModificationDate(fixed);
        byte[] id = digest(name);
        COSArray ids = new COSArray();
        ids.add(new COSString(id));
        ids.add(new COSString(id));
        target.getDocument().getTrailer().setItem(COSName.ID, ids);
    }

    /**
     * Writes an image-only copy, as a scanner would: each page rendered to greyscale at {@code dpi} and
     * placed as a full-page image, with no text layer.
     */
    public void saveAsScan(Path file, float dpi) {
        finishPages();
        try (PDDocument scan = new PDDocument()) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.GRAY);
                PDPage page = new PDPage(size);
                scan.addPage(page);
                PDImageXObject xobject = LosslessFactory.createFromImage(scan, image);
                try (PDPageContentStream cs = new PDPageContentStream(scan, page)) {
                    cs.drawImage(xobject, 0, 0, size.getWidth(), size.getHeight());
                }
            }
            fixMetadata(scan);
            Files.createDirectories(file.getParent());
            scan.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    private void finishPages() {
        pages.forEach(PdfPage::finish);
    }

    @Override
    public void close() {
        try {
            document.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] digest(String text) {
        try {
            byte[] full = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            byte[] id = new byte[16];
            System.arraycopy(full, 0, id, 0, id.length);
            return id;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
