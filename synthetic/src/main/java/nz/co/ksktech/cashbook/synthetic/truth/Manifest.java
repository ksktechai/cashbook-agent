package nz.co.ksktech.cashbook.synthetic.truth;

import java.util.List;

/** What a run produced: the seed, the period and every file with its SHA-256. */
public record Manifest(long seed, String generatorVersion, String period, List<FileEntry> files) {

    public record FileEntry(String path, long bytes, String sha256) {}
}
