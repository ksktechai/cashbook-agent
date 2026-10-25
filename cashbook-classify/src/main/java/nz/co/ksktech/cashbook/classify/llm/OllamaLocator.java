package nz.co.ksktech.cashbook.classify.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds a reachable Ollama server from a list of URLs, tried in order (e.g. a Bonjour name first, then a
 * last-known IP), and checks the model is installed there. Fails with a clear message rather than
 * silently running without the LLM.
 */
public final class OllamaLocator {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VERSION = "/api/version";
    private static final String TAGS = "/api/tags";

    private OllamaLocator() {}

    /** Thrown when no server answers or the model is missing. */
    public static final class UnavailableException extends RuntimeException {
        public UnavailableException(String message) {
            super(message);
        }
    }

    /** The first URL that answers, after checking it has {@code model}. */
    public static String locate(List<String> urls, String model, Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        List<String> tried = new ArrayList<>();
        for (String raw : urls) {
            String url = raw.strip().replaceAll("/+$", "");
            if (url.isEmpty()) {
                continue;
            }
            tried.add(url);
            if (get(http, url + VERSION, timeout) == null) {
                continue;
            }
            String tags = get(http, url + TAGS, timeout);
            List<String> models = new ArrayList<>();
            try {
                for (JsonNode m : JSON.readTree(tags == null ? "{}" : tags).path("models")) {
                    models.add(m.path("name").asText());
                }
            } catch (Exception e) {
                throw new UnavailableException("Ollama at " + url + " returned an unreadable model list");
            }
            if (!models.contains(model)) {
                throw new UnavailableException("model '" + model + "' is not on the Ollama server at " + url
                        + "; it has: " + String.join(", ", models));
            }
            return url;
        }
        throw new UnavailableException("no Ollama server reachable at " + String.join(", ", tried)
                + ". Start Ollama, fix cashbook.llm.ollama.urls, or run with --llm none for rules only.");
    }

    private static String get(HttpClient http, String url, Duration timeout) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 ? r.body() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
