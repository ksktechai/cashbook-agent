package nz.co.ksktech.cashbook.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.synthetic.model.HighlightColour;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Reads the generated PDFs back and checks them against the truth files. */
class StatementPdfTest {

    @Test
    void everyTruthLineReadsBackFromItsPage() throws IOException {
        Map<String, Map<Integer, String>> pageText = new HashMap<>();
        for (JsonNode line : Generated.jsonl(SyntheticGenerator.TRUTH_TRANSACTIONS)) {
            JsonNode source = line.at("/transaction/source");
            String text = pageText.computeIfAbsent(source.get("file").asText(), StatementPdfTest::textByPage)
                    .get(source.get("page").asInt());
            for (String token : source.get("rawText").asText().split("\\s+")) {
                assertThat(text).as("line %s token '%s'", line.get("id").asText(), token).contains(token);
            }
        }
    }

    @Test
    void balancesAndTotalsReconcileAndArePrinted() throws IOException {
        List<JsonNode> statements = Generated.jsonl(SyntheticGenerator.TRUTH_STATEMENTS);
        assertThat(statements).hasSize(6);
        for (JsonNode s : statements) {
            BigDecimal opening = s.get("openingBalance").decimalValue();
            BigDecimal net = s.get("moneyIn").decimalValue().subtract(s.get("moneyOut").decimalValue());
            BigDecimal closing = s.get("closingBalance").decimalValue();
            boolean card = s.get("accountType").asText().equals("CREDIT_CARD");
            assertThat(card ? opening.subtract(net) : opening.add(net)).as(s.get("file").asText()).isEqualByComparingTo(closing);

            String text = String.join("\n", textByPage(s.get("file").asText()).values());
            String layout = s.get("layout").asText();
            if (layout.startsWith("KAURI")) {
                assertThat(text).contains("Total Money In " + money(s.get("moneyIn").decimalValue()));
                assertThat(text).contains("Total Money Out - " + money(s.get("moneyOut").decimalValue()));
            }
            if (!layout.equals("KAURI_CARD_EXPORT")) {
                assertThat(text).as("closing balance printed in %s", s.get("file").asText())
                        .contains(money(closing.abs()));
            }
        }
    }

    @Test
    void highlightsMatchTheTruth() throws IOException {
        Map<String, List<JsonNode>> byFile = new HashMap<>();
        for (JsonNode line : Generated.jsonl(SyntheticGenerator.TRUTH_TRANSACTIONS)) {
            byFile.computeIfAbsent(line.at("/transaction/source/file").asText(), k -> new ArrayList<>()).add(line);
        }
        int highlighted = 0;
        for (Map.Entry<String, List<JsonNode>> file : byFile.entrySet()) {
            try (PDDocument doc = Loader.loadPDF(pdf(file.getKey()).toFile())) {
                PDFRenderer renderer = new PDFRenderer(doc);
                Map<Integer, BufferedImage> images = new HashMap<>();
                for (JsonNode line : file.getValue()) {
                    int pageNo = line.at("/transaction/source/page").asInt();
                    PDPage page = doc.getPage(pageNo - 1);
                    BoundingBox box = box(line.at("/transaction/source/box"));
                    String colour = line.get("highlightColour").isNull() ? null : line.get("highlightColour").asText();
                    if (line.get("flattenedHighlight").asBoolean()) {
                        BufferedImage image = images.computeIfAbsent(pageNo, p -> render(renderer, p));
                        BoundingBox h = box(line.get("highlightBox"));
                        int rgb = image.getRGB(Math.round(h.left() + 1), Math.round(h.top() + h.height() / 2));
                        assertThat(closeTo(rgb, HighlightColour.valueOf(colour).rgb()))
                                .as("flattened highlight of %s", line.get("id").asText()).isTrue();
                        highlighted++;
                        continue;
                    }
                    float coverage = 0;
                    float best = 0;
                    String found = null;
                    for (PDAnnotation a : page.getAnnotations()) {
                        if (a instanceof PDAnnotationHighlight h) {
                            float shared = box.intersectionArea(quad(h.getQuadPoints(), page));
                            coverage += shared / box.area();
                            if (shared > best) {
                                best = shared;
                                found = colourName(h.getColor().getComponents());
                            }
                        }
                    }
                    if (colour == null) {
                        assertThat(coverage).as("unhighlighted %s", line.get("id").asText()).isLessThan(0.05f);
                    } else {
                        assertThat(found).as("highlight colour of %s", line.get("id").asText()).isEqualTo(colour);
                        highlighted++;
                    }
                }
            }
        }
        assertThat(highlighted).isGreaterThan(50);
    }

    // ---------------------------------------------------------------- helpers

    static Path pdf(String file) {
        return Generated.out().resolve(SyntheticGenerator.IN).resolve(file);
    }

    private static Map<Integer, String> textByPage(String file) {
        try (PDDocument doc = Loader.loadPDF(pdf(file).toFile())) {
            Map<Integer, String> pages = new HashMap<>();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                pages.put(p, stripper.getText(doc).replaceAll("\\s+", " "));
            }
            return pages;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static BufferedImage render(PDFRenderer renderer, int page) {
        try {
            return renderer.renderImage(page - 1, 1f);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String money(BigDecimal amount) {
        return "$" + Cents.format(Cents.of(amount.toPlainString()));
    }

    private static BoundingBox box(JsonNode n) {
        return new BoundingBox((float) n.get("left").asDouble(), (float) n.get("top").asDouble(),
                (float) n.get("width").asDouble(), (float) n.get("height").asDouble());
    }

    /** The highlighted area itself; PDFBox widens the annotation rectangle when it builds the appearance. */
    private static BoundingBox quad(float[] q, PDPage page) {
        float height = page.getMediaBox().getHeight();
        float left = Math.min(q[0], q[4]);
        float right = Math.max(q[2], q[6]);
        float top = Math.max(q[1], q[3]);
        float bottom = Math.min(q[5], q[7]);
        return new BoundingBox(left, height - top, right - left, top - bottom);
    }

    private static String colourName(float[] rgb) {
        for (HighlightColour c : HighlightColour.values()) {
            float[] ref = c.rgb();
            if (Math.abs(ref[0] - rgb[0]) < 0.01 && Math.abs(ref[1] - rgb[1]) < 0.01 && Math.abs(ref[2] - rgb[2]) < 0.01) {
                return c.name();
            }
        }
        return "unknown";
    }

    private static boolean closeTo(int argb, float[] rgb) {
        int r = (argb >> 16) & 0xff;
        int g = (argb >> 8) & 0xff;
        int b = argb & 0xff;
        return Math.abs(r - rgb[0] * 255) < 8 && Math.abs(g - rgb[1] * 255) < 8 && Math.abs(b - rgb[2] * 255) < 8;
    }
}
