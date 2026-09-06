import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

/**
 * DescriptionFetcher
 * ===================
 * One-time batch script that reads the course CSV, fetches the course
 * description for every row from the school's Banner registration system,
 * and writes a NEW csv file with an extra column appended to the end of
 * every row containing that course's description.
 *
 * It does not modify the original input file - it writes a separate output file.
 *
 * WHY THIS EXISTS:
 * The course CSV (exported separately) does not include course descriptions.
 * Prerequisite/corequisite information is usually only found inside those
 * descriptions, so this script's job is purely to go fetch that missing text
 * and attach it to each row, so a later step can parse prereqs/coreqs out of it.
 *
 * HOW IT WORKS, STEP BY STEP:
 * 1. Makes two initial requests to the registration site to "select" the
 *    correct semester/term, the same way a person visiting the site would
 *    have to pick a term before searching for classes. This sets some
 *    session state (via cookies) that the description endpoint expects.
 * 2. Reads every line of the input CSV and pulls out each course's CRN
 *    (a unique 5-digit course reference number - it's always the first
 *    column, wrapped in quotes, e.g. "90495").
 * 3. For each CRN, sends a request to Banner's "getCourseDescription"
 *    endpoint (the same request the site's own Javascript makes when a
 *    student clicks "Course Description" on a class listing).
 * 4. The response comes back as a chunk of HTML. This script strips out
 *    all the HTML tags/comments and leaves just the plain description text.
 * 5. Writes out a new CSV: every original line, unchanged, with the
 *    fetched description appended as one new final column.
 *
 * USAGE:
 *   java DescriptionFetcher <inputCsv> <outputCsv> [term] [delayMs]
 *
 * EXAMPLE:
 *   java DescriptionFetcher fall2026Courses.csv fall2026CoursesWithDescriptions.csv
 *
 * ARGUMENTS:
 *   inputCsv   - path to the existing course CSV (required)
 *   outputCsv  - path to write the new CSV with descriptions added (required)
 *   term       - Banner's semester code, e.g. 202609 for Fall 2026 (optional,
 *                defaults to 202609)
 *   delayMs    - milliseconds to pause between each request, to avoid
 *                hammering the school's server (optional, defaults to 300)
 */
public class DescriptionFetcher {

    // The base URL for the school's course registration system.
    private static final String BASE = "https://banner.oci.yu.edu/StudentRegistrationSsb/ssb";

    // Visiting this page (and the one below) establishes a valid session for
    // the chosen term - the site expects this to happen before any course data
    // can be requested.
    private static final String TERM_SELECTION_URL = BASE + "/term/termSelection?mode=search";
    private static final String TERM_SEARCH_URL = BASE + "/term/search?mode=search";

    // The actual endpoint that returns a single course's description.
    private static final String DESCRIPTION_URL = BASE + "/searchResults/getCourseDescription";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java DescriptionFetcher <inputCsv> <outputCsv> [term] [delayMs]");
            return;
        }
        Path inputPath = Path.of(args[0]);
        Path outputPath = Path.of(args[1]);
        String term = args.length > 2 ? args[2] : "202609";
        int delayMs = args.length > 3 ? Integer.parseInt(args[3]) : 300;

        // A single HttpClient is reused for every request so that the session
        // cookies set in step 1 (term selection) stay attached to every
        // following request automatically.
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();

        System.out.println("Selecting term " + term + "...");
        selectTerm(client, term);

        List<String> lines = Files.readAllLines(inputPath);
        String[] descriptions = new String[lines.size()];
        int total = lines.size();
        int blankCount = 0;

        for (int i = 0; i < total; i++) {
            String line = lines.get(i);

            if (line.isBlank()) {
                descriptions[i] = "";
                continue;
            }

            String crn = extractCrn(line);
            if (crn == null) {
                // Couldn't find a valid CRN on this line (e.g. an unexpected format) -
                // leave it blank rather than guessing.
                descriptions[i] = "";
                continue;
            }

            String description = fetchDescription(client, term, crn);
            descriptions[i] = description;
            if (description.isBlank()) {
                blankCount++;
            }

            if ((i + 1) % 25 == 0 || (i + 1) == total) {
                System.out.println("Fetched " + (i + 1) + " / " + total + " (blank so far: " + blankCount + ")");
            }

            // Small pause between requests so we're not hammering the server.
            Thread.sleep(delayMs);
        }

        writeOutputCsv(lines, descriptions, outputPath);

        System.out.println("Done. Wrote " + outputPath);
        System.out.println(blankCount + " / " + total + " rows came back with a blank description.");
        if (blankCount > 0) {
            System.out.println("If that number seems high, something may still be off - let's look into it");
            System.out.println("before trusting the rest of the file.");
        }
    }

    /**
     * Visits the two pages the registration site normally requires before it
     * will return any course data for a given term. This just sets cookies on
     * the shared HttpClient - it doesn't return anything we need directly.
     */
    private static void selectTerm(HttpClient client, String term) throws Exception {
        HttpRequest visitTermPage = HttpRequest.newBuilder()
                .uri(URI.create(TERM_SELECTION_URL))
                .GET()
                .build();
        client.send(visitTermPage, HttpResponse.BodyHandlers.ofString());

        String formData = "term=" + term + "&studyPath=&studyPathText=&startDatepicker=&endDatepicker=";
        HttpRequest chooseTerm = HttpRequest.newBuilder()
                .uri(URI.create(TERM_SEARCH_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();
        client.send(chooseTerm, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Requests one course's description from Banner and returns the cleaned,
     * plain-text result. Returns an empty string if anything goes wrong
     * (network error, unexpected response, etc.) rather than throwing, so one
     * bad course doesn't stop the whole batch.
     */
    private static String fetchDescription(HttpClient client, String term, String crn) {
        try {
            String formData = "term=" + term + "&courseReferenceNumber=" + crn;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DESCRIPTION_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return extractPlainText(response.body());
        } catch (Exception e) {
            System.out.println("  Failed on CRN " + crn + ": " + e.getMessage());
            return "";
        }
    }

    /**
     * The response body is a chunk of HTML (an example: an outer <section> tag,
     * an HTML comment, then the actual description sentence, then more
     * comments/placeholders). Rather than guessing which line number the real
     * text is on, this strips ALL HTML comments and tags out of the whole
     * response and returns whatever plain text is left over.
     */
    private static String extractPlainText(String rawHtml) {
        if (rawHtml == null) return "";

        // Remove HTML comments like <!--this-->  ("(?s)" lets "." match newlines too)
        String noComments = rawHtml.replaceAll("(?s)<!--.*?-->", "");

        // Remove any remaining HTML tags, e.g. <section>, <br/>, </section>
        String noTags = noComments.replaceAll("<[^>]*>", " ");

        // Decode the handful of HTML entities that show up in course descriptions
        String decoded = noTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");

        // Commas would break the simple CSV parsing used elsewhere in this project
        // (Course.java splits each line on ","), so swap commas for semicolons.
        String noCommas = decoded.replace(",", ";");

        // Collapse all the leftover whitespace/newlines down to single spaces.
        return noCommas.replaceAll("\\s+", " ").trim();
    }

    /**
     * Pulls the CRN (course reference number) out of the first column of a CSV
     * line. Every field in this CSV is wrapped in quotes, e.g. "90495", so this
     * strips those quotes off and checks that what's left is a real number.
     * Returns null for lines that don't look right (e.g. blank/header lines),
     * so the caller can skip them instead of crashing.
     */
    private static String extractCrn(String line) {
        String[] fields = line.split(",", -1);
        if (fields.length == 0) return null;

        String field = fields[0].trim();
        if (field.length() >= 2 && field.startsWith("\"") && field.endsWith("\"")) {
            field = field.substring(1, field.length() - 1);
        }

        try {
            Integer.parseInt(field);
            return field;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Writes the final output file: every original line exactly as it was,
     * with one new column appended at the end containing that row's
     * description (wrapped in quotes, matching the format of every other
     * column in this CSV).
     */
    private static void writeOutputCsv(List<String> originalLines, String[] descriptions, Path outputPath)
            throws Exception {
        List<String> outputLines = new ArrayList<>();
        for (int i = 0; i < originalLines.size(); i++) {
            outputLines.add(originalLines.get(i) + ",\"" + descriptions[i] + "\"");
        }
        Files.write(outputPath, outputLines);
    }
}