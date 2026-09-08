import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * ProgramRequirementsExtractor
 * =============================
 * Crawls Yeshiva University's official catalog (yu.smartcatalogiq.com) and
 * extracts the raw structure of every major/minor's requirements into one
 * consolidated, human-readable text file.
 *
 * STAGE 1 of a two-stage process (same philosophy as the prereq/coreq
 * pipeline). Faithfully captures every section, heading, course row,
 * narrative instruction, and free-text paragraph in order, clearly labeled
 * by type - it does NOT try to resolve "choose 4 of the following" groups or
 * course-range wildcards (e.g. "COM 1000-4999") into final AND/OR logic yet.
 *
 * CONFIRMED HTML STRUCTURE (verified against real page source):
 *   <h3 class="sc-RequiredCoursesHeading1">Section Name</h3>
 *   <table><tbody>
 *     <tr>
 *       <td class="sc-coursenumber ..."><a class="sc-courselink" href="...">MAT 1412</a></td>
 *       <td class="sc-coursetitle ...">Calculus I</td>
 *       <td class="sc-credits ..."><p class="credits">4</p></td>
 *     </tr>
 *   </tbody></table>
 *
 * Row types (all same <td> shape):
 *   - COURSE: column 1 has <a class="sc-courselink" href="...">CODE</a> with
 *     non-blank text.
 *   - CONNECTOR: same tag, but blank text (e.g. "And" rows).
 *   - NARRATIVE/RANGE: no <a class="sc-courselink"> at all - a <div
 *     class="narrative"> or plain text (e.g. "COM 1000-4999" range
 *     placeholders). Column 2 holds the description.
 *
 * Some programs (confirmed via Jewish Studies, AA) have NO course table at
 * all - their entire requirement is free-text paragraphs (e.g. "complete 17
 * Jewish studies courses totaling 34 credits", no specific codes given).
 * Plain <p> paragraphs are now captured too, and any program that ends up
 * with zero COURSE: rows gets an explicit "NO_COURSE_TABLE_FOUND" flag so it
 * surfaces for manual review instead of silently looking the same as a
 * successfully-parsed program.
 *
 * Parsing starts at the page's first <h1> tag, skipping the site
 * nav/sidebar that precedes real content on every page - otherwise
 * capturing every <p> tag would risk pulling in nav/boilerplate text too.
 *
 * Usage: java ProgramRequirementsExtractor <urlListFile> <outputFile> [delayMs]
 * Example: java ProgramRequirementsExtractor programUrls.txt programRequirements.txt
 */
public class ProgramRequirementsExtractor {

    private static final Pattern HEADING_TABLE_OR_PARAGRAPH = Pattern.compile(
            "(?s)<h[1234][^>]*>(.*?)</h[1234]>|<table>(.*?)</table>|<p>(.*?)</p>");

    private static final Pattern ROW = Pattern.compile("(?s)<tr>(.*?)</tr>");

    private static final Pattern COURSE_OR_CONNECTOR_CELL = Pattern.compile(
            "(?s)<a class=\"sc-courselink\"[^>]*>(.*?)</a>");

    private static final Pattern CREDITS_CELL = Pattern.compile(
            "(?s)<p class=\"credits\">(.*?)</p>");

    private static final Pattern CELL = Pattern.compile("(?s)<td[^>]*>(.*?)</td>");

    private static final Pattern TITLE_TAG = Pattern.compile("(?s)<title>(.*?)</title>");

    private static final Pattern FIRST_H1 = Pattern.compile("(?s)<h1[^>]*>");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java ProgramRequirementsExtractor <urlListFile> <outputFile> [delayMs]");
            return;
        }
        Path urlListPath = Path.of(args[0]);
        Path outputPath = Path.of(args[1]);
        int delayMs = args.length > 2 ? Integer.parseInt(args[2]) : 400;

        List<String> urls = Files.readAllLines(urlListPath).stream()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();

        HttpClient client = HttpClient.newHttpClient();
        List<String> output = new ArrayList<>();
        int total = urls.size();
        int failedCount = 0;
        int noCourseTableCount = 0;

        for (int i = 0; i < total; i++) {
            String url = urls.get(i);
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    output.add("=== FAILED (HTTP " + response.statusCode() + ") - " + url + " ===\n");
                    failedCount++;
                    System.out.println("FAILED (HTTP " + response.statusCode() + "): " + url);
                    continue;
                }
                String html = response.body();
                String pageTitle = extractTitle(html);
                output.add("=== " + pageTitle + " (" + url + ") ===");

                List<String> sectionLines = extractSections(html);
                output.addAll(sectionLines);

                boolean hasAnyCourse = sectionLines.stream().anyMatch(line -> line.startsWith("COURSE:"));
                if (!hasAnyCourse) {
                    output.add("NO_COURSE_TABLE_FOUND - this program's requirements may be free-text only (see paragraphs above) - needs manual review");
                    noCourseTableCount++;
                    System.out.println("NO COURSE TABLE: " + pageTitle);
                }
                output.add(""); // blank line between programs
            } catch (Exception e) {
                output.add("=== FAILED (" + e.getMessage() + ") - " + url + " ===\n");
                failedCount++;
                System.out.println("FAILED (" + e.getMessage() + "): " + url);
            }

            if ((i + 1) % 10 == 0 || (i + 1) == total) {
                System.out.println("Processed " + (i + 1) + " / " + total);
            }
            Thread.sleep(delayMs);
        }

        Files.write(outputPath, output);
        System.out.println();
        System.out.println("Done. Wrote " + outputPath + " (" + total + " programs, " + failedCount + " failed, "
                + noCourseTableCount + " with no course table found).");
        if (failedCount > 0) {
            System.out.println("Search the output file for \"FAILED\" to find and re-run just those URLs.");
        }
        if (noCourseTableCount > 0) {
            System.out.println("Search the output file for \"NO_COURSE_TABLE_FOUND\" to find programs needing manual review.");
        }
    }

    private static String extractTitle(String html) {
        Matcher m = TITLE_TAG.matcher(html);
        if (m.find()) {
            return m.group(1).replace("&nbsp;", " ").replace("&amp;", "&").trim();
        }
        return "(untitled)";
    }

    /**
     * Walks the page in document order starting at the first <h1> tag
     * (skipping the nav/sidebar that precedes it on every page), emitting one
     * output line per heading/row/paragraph found.
     */
    private static List<String> extractSections(String html) {
        List<String> lines = new ArrayList<>();

        int startIndex = 0;
        Matcher h1Matcher = FIRST_H1.matcher(html);
        if (h1Matcher.find()) {
            startIndex = h1Matcher.start();
        }

        Matcher m = HEADING_TABLE_OR_PARAGRAPH.matcher(html);
        m.region(startIndex, html.length());
        while (m.find()) {
            if (m.group(1) != null) {
                String headingText = stripTags(m.group(1)).trim();
                if (!headingText.isBlank()) {
                    lines.add("## " + headingText);
                }
            } else if (m.group(2) != null) {
                String tableHtml = m.group(2);
                Matcher rowMatcher = ROW.matcher(tableHtml);
                while (rowMatcher.find()) {
                    lines.add(classifyRow(rowMatcher.group(1)));
                }
            } else {
                String paragraphText = stripTags(m.group(3)).trim();
                if (!paragraphText.isBlank()) {
                    lines.add("TEXT: " + paragraphText);
                }
            }
        }
        return lines;
    }

    private static String classifyRow(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher cellMatcher = CELL.matcher(rowHtml);
        while (cellMatcher.find()) {
            cells.add(cellMatcher.group(1));
        }
        if (cells.size() < 2) {
            return "UNRECOGNIZED_ROW: " + stripTags(rowHtml).trim();
        }

        String col1 = cells.get(0);
        String col2 = stripTags(cells.get(1)).trim();
        String col3 = cells.size() > 2 ? cells.get(2) : "";

        Matcher courseLinkMatcher = COURSE_OR_CONNECTOR_CELL.matcher(col1);
        if (courseLinkMatcher.find()) {
            String linkText = stripTags(courseLinkMatcher.group(1)).trim();
            if (!linkText.isBlank()) {
                String credits = extractCredits(col3);
                return "COURSE: " + linkText.replace(" ", "") + " | " + col2 + " | " + credits;
            } else {
                return "CONNECTOR: " + col2;
            }
        }

        String col1Text = stripTags(col1).trim();
        return "NARRATIVE: " + col1Text + " | " + col2;
    }

    private static String extractCredits(String creditsCellHtml) {
        Matcher m = CREDITS_CELL.matcher(creditsCellHtml);
        if (m.find()) {
            return stripTags(m.group(1)).trim();
        }
        return "";
    }

    private static String stripTags(String html) {
        return html.replaceAll("(?s)<[^>]*>", " ")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replace("&rsquo;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }
}