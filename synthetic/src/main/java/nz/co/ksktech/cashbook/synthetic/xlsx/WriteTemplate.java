package nz.co.ksktech.cashbook.synthetic.xlsx;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Writes the sanitised template to the path given (used by the {@code updateTemplate} Gradle task). */
public final class WriteTemplate {

    private WriteTemplate() {}

    public static void main(String[] args) throws IOException {
        try (XSSFWorkbook wb = TemplateBuilder.build()) {
            Workbooks.save(wb, Path.of(args[0]));
        }
    }
}
