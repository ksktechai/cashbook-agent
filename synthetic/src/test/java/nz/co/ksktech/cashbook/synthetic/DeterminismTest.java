package nz.co.ksktech.cashbook.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import nz.co.ksktech.cashbook.synthetic.truth.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeterminismTest {

    @Test
    void sameSeedGivesByteIdenticalFiles(@TempDir Path first, @TempDir Path second) throws IOException {
        Manifest a = SyntheticGenerator.generate(42, first);
        Manifest b = SyntheticGenerator.generate(42, second);

        assertThat(a.files()).isNotEmpty().isEqualTo(b.files());
        assertThat(Files.readString(first.resolve(SyntheticGenerator.MANIFEST)))
                .isEqualTo(Files.readString(second.resolve(SyntheticGenerator.MANIFEST)));
    }

    @Test
    void differentSeedGivesDifferentStatements(@TempDir Path first, @TempDir Path second) {
        Manifest a = SyntheticGenerator.generate(42, first);
        Manifest b = SyntheticGenerator.generate(43, second);

        assertThat(a.files()).hasSameSizeAs(b.files()).isNotEqualTo(b.files());
        String business = "in/BusinessEveryday-05Apr27.pdf";
        assertThat(sha(a, business)).isNotEqualTo(sha(b, business));
    }

    @Test
    void regeneratingIntoTheSameFolderReplacesThePreviousRun(@TempDir Path out) throws IOException {
        Files.writeString(out.resolve("keep-me.txt"), "not ours");
        SyntheticGenerator.generate(43, out);
        Manifest again = SyntheticGenerator.generate(42, out);

        assertThat(out.resolve("keep-me.txt")).exists();
        try (var walk = Files.walk(out.resolve(SyntheticGenerator.IN))) {
            assertThat(walk.filter(Files::isRegularFile).count())
                    .isEqualTo(again.files().stream().filter(f -> f.path().startsWith("in/")).count());
        }
    }

    private static String sha(Manifest m, String path) {
        return m.files().stream().filter(f -> f.path().equals(path)).findFirst().orElseThrow().sha256();
    }
}
