import java.net.*;
import java.net.http.*;
import java.nio.file.*;

/**
 * Diagnostic script - NOT the real extractor. Fetches one program page's
 * raw HTML and saves it to a file so we can inspect the actual table
 * structure before writing a parser against it. This is a public catalog
 * page (no login/session required), unlike the Banner registration system.
 *
 * Usage: java CatalogPageDump <url> <outputFile>
 * Example: java CatalogPageDump https://yu.smartcatalogiq.com/en/current/university-catalog/undergraduate-programs/yeshiva-college/mathematics-ba mathpage.html
 */
public class CatalogPageDump {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java CatalogPageDump <url> <outputFile>");
            return;
        }
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(args[0]))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("HTTP status: " + response.statusCode());
        Files.writeString(Path.of(args[1]), response.body());
        System.out.println("Saved raw HTML to " + args[1]);
    }
}