package nz.co.ksktech.cashbook.classify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.rules.Rules;

/**
 * Loads a rules YAML file and, if present, its learned rules ({@code <name>.learned.yaml} next to it,
 * written by the review flow). Both are validated against {@code rules.schema.json}, then checked for
 * what a schema can't express: regexes compile and every vendor code is defined.
 */
public final class RulesLoader {

    public static final String LEARNED_SUFFIX = ".learned.yaml";
    private static final String SCHEMA = "/rules.schema.json";
    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private RulesLoader() {}

    /** Thrown when a rules file is invalid; the message lists every problem with its path. */
    public static final class InvalidRulesException extends RuntimeException {
        public InvalidRulesException(String message) {
            super(message);
        }
    }

    /** The learned-rules file that belongs to a rules file: rules.yaml -> rules.learned.yaml. */
    public static Path learnedFile(Path rulesFile) {
        String name = rulesFile.getFileName().toString();
        String stem = name.endsWith(".yaml") ? name.substring(0, name.length() - 5)
                : name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
        return rulesFile.resolveSibling(stem + LEARNED_SUFFIX);
    }

    public static Rules load(Path rulesFile) {
        JsonNode main = read(rulesFile);
        validate(rulesFile, main);
        Path learned = learnedFile(rulesFile);
        JsonNode learnedNode = null;
        if (Files.exists(learned)) {
            learnedNode = read(learned);
            validate(learned, learnedNode);
        }
        return build(rulesFile, main, learnedNode);
    }

    private static JsonNode read(Path file) {
        try {
            JsonNode node = YAML.readTree(file.toFile());
            if (node == null || !node.isObject()) {
                throw new InvalidRulesException(file + ": expected a YAML mapping at the top level");
            }
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read rules file " + file, e);
        }
    }

    private static void validate(Path file, JsonNode node) {
        List<Error> errors = schema().validate(node);
        if (!errors.isEmpty()) {
            StringBuilder b = new StringBuilder(file + " is not a valid rules file:");
            errors.forEach(e -> b.append("\n  ").append(e.getInstanceLocation()).append(": ").append(e.getMessage()));
            throw new InvalidRulesException(b.toString());
        }
    }

    private static Schema schema() {
        try (InputStream in = RulesLoader.class.getResourceAsStream(SCHEMA)) {
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            return registry.getSchema(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Rules build(Path file, JsonNode main, JsonNode learned) {
        List<String> problems = new ArrayList<>();
        Map<String, Rules.Code> codes = new LinkedHashMap<>();
        main.path("codes").properties().forEach(e -> codes.put(e.getKey(), new Rules.Code(
                e.getValue().get("name").asText(), GstTreatment.valueOf(e.getValue().get("gst").asText()),
                text(e.getValue(), "hint"))));
        if (codes.isEmpty()) {
            problems.add("codes: at least one code is required");
        }
        Map<String, BigDecimal> businessUse = new LinkedHashMap<>();
        main.path("businessUse").properties().forEach(e -> businessUse.put(e.getKey(), e.getValue().decimalValue()));

        List<Rules.Vendor> vendors = new ArrayList<>();
        vendors(main.path("vendors"), "vendors", codes, vendors, problems);
        if (learned != null) {
            vendors(learned.path("vendors"), "learned", codes, vendors, problems);
        }
        List<Rules.Exclude> excludes = new ArrayList<>();
        ArrayNode exclude = main.path("exclude").isArray() ? (ArrayNode) main.path("exclude") : null;
        for (int i = 0; exclude != null && i < exclude.size(); i++) {
            JsonNode x = exclude.get(i);
            String id = "exclude[" + i + "]";
            Pattern p = regex(x.get("match").asText(), id, problems);
            if (p != null) {
                excludes.add(new Rules.Exclude(id, p, x.get("reason").asText(), accounts(x)));
            }
        }
        Map<AccountType, Rules.HighlightPolicy> policy = new EnumMap<>(AccountType.class);
        main.path("highlightPolicy").properties().forEach(e -> policy.put(AccountType.valueOf(e.getKey()),
                Rules.HighlightPolicy.valueOf(e.getValue().asText())));
        if (!problems.isEmpty()) {
            throw new InvalidRulesException(file + " is not a valid rules file:\n  " + String.join("\n  ", problems));
        }
        return new Rules(codes, businessUse, vendors, excludes, policy);
    }

    private static void vendors(JsonNode list, String prefix, Map<String, Rules.Code> codes, List<Rules.Vendor> out,
            List<String> problems) {
        for (int i = 0; list.isArray() && i < list.size(); i++) {
            JsonNode v = list.get(i);
            String id = prefix + "[" + i + "]";
            String code = v.get("code").asText();
            if (!codes.containsKey(code)) {
                problems.add(id + ".code: " + code + " is not defined under codes");
            }
            Pattern p = regex(v.get("match").asText(), id, problems);
            if (p == null || !codes.containsKey(code)) {
                continue;
            }
            out.add(new Rules.Vendor(id, p, code,
                    v.hasNonNull("gst") ? GstTreatment.valueOf(v.get("gst").asText()) : null,
                    accounts(v),
                    v.path("onlyIfHighlighted").asBoolean(false),
                    v.hasNonNull("aggregate") ? Rules.Aggregate.valueOf(v.get("aggregate").asText()) : null,
                    text(v, "special"),
                    v.hasNonNull("businessAmount") ? v.get("businessAmount").decimalValue() : null,
                    v.hasNonNull("reviewIfOverInclGst") ? v.get("reviewIfOverInclGst").decimalValue() : null,
                    text(v, "ask"),
                    text(v, "note")));
        }
    }

    private static Pattern regex(String regex, String id, List<String> problems) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            problems.add(id + ".match: not a valid regex: " + e.getDescription());
            return null;
        }
    }

    private static Set<AccountType> accounts(JsonNode node) {
        Set<AccountType> set = EnumSet.noneOf(AccountType.class);
        node.path("accounts").forEach(a -> set.add(AccountType.valueOf(a.asText())));
        return set;
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    /** Appends a vendor rule to the learned-rules file (creating it), then re-validates the result. */
    public static void appendLearned(Path rulesFile, Map<String, Object> vendor) {
        Path learned = learnedFile(rulesFile);
        try {
            ObjectNode root = Files.exists(learned) ? (ObjectNode) YAML.readTree(learned.toFile()) : YAML.createObjectNode();
            ArrayNode vendors = root.has("vendors") ? (ArrayNode) root.get("vendors") : root.putArray("vendors");
            vendors.add(YAML.valueToTree(vendor));
            validate(learned, root);
            Files.writeString(learned, "# Written by `cashbook-agent review`. Edit freely; it is validated on load.\n"
                    + YAML.writeValueAsString(root));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + learned, e);
        }
    }
}
