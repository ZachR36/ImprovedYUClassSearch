import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

/**
 * CatalogFetcher
 * ==============
 * Downloads every section for a semester straight from Banner's class search
 * and writes it in the same 17-column CSV layout as fall2026CoursesOriginal.csv,
 * so the result can go straight into DescriptionFetcher -> PrereqExtractor.
 *
 * WHY THIS EXISTS:
 * The catalog used to be pulled by hand: open class search in a logged-in
 * browser, copy the searchResults request out of DevTools, edit its page size,
 * replay it with the copied headers, then convert the JSON. None of that is
 * actually needed - Banner's class search is open to guests. Selecting a term
 * (the same two requests DescriptionFetcher makes) creates an anonymous
 * session cookie, and searchResults returns JSON for that session with no
 * Microsoft login involved.
 *
 * HOW IT WORKS:
 * 1. Selects the term to get a session cookie, and resets any previous search.
 * 2. Pages through /searchResults/searchResults 500 rows at a time (Banner
 *    silently caps pageMaxSize at 500) until totalCount rows are collected.
 * 3. Keeps only Wilf/Beren (in-person + online) sections whose part-of-term
 *    code belongs to an undergraduate school (see UNDERGRAD_PART_OF_TERM).
 *    This filter reproduces the original fall2026 CSV.
 * 4. Converts each section to the CSV layout Course.java parses.
 *
 * USAGE:
 *   java HelperClasses/CatalogFetcher.java <outputCsv> [term]
 *   java HelperClasses/CatalogFetcher.java --list-terms
 *
 * EXAMPLES:
 *   java HelperClasses/CatalogFetcher.java spring2027CoursesOriginal.csv 202701
 *   java HelperClasses/CatalogFetcher.java --list-terms     (shows term codes)
 *
 * OUTPUT COLUMNS (0-indexed, matching Course.java):
 *   0 CRN, 1 course number, 2 subject, 3 section, 4 campus, 5 title,
 *   6 credits, 7 instructor, 8 enrolled, 9 max enrolled, 10 seats available,
 *   11 waitlisted, 12 waitlist capacity, 13 waitlist available,
 *   14 meetings, 15 attribute codes, 16 attribute descriptions
 *
 * FIELD ENCODINGS (same as the original hand-built CSV):
 *   - Commas inside text become ";" because Course.java splits rows on ",".
 *   - Meetings: "days}begin}end}building}room", several joined by "::".
 *     Days are joined by "||" using M T W R F S U. A section with no set
 *     time uses "N/A" for begin/end; no building becomes "TBA".
 *   - Attributes: codes and descriptions are each joined by "||".
 *   - Instructor: the first listed faculty member, or "Staff" if none.
 *   - Credits: variable-credit sections (e.g. "1 TO 3") use the minimum.
 */
public class CatalogFetcher {

    private static final String BASE = "https://banner.oci.yu.edu/StudentRegistrationSsb/ssb";
    private static final int PAGE_SIZE = 500; // Banner's server-side maximum

    /** Campuses kept in the output, by Banner's campusDescription. */
    private static final Set<String> CAMPUSES = Set.of("Wilf", "Wilf (Online)", "Beren", "Beren (Online)");

    /**
     * Banner part-of-term codes start with a two-letter school prefix
     * (ST1 = Stern, YC1 = Yeshiva College, UB1/UB4 = Syms undergrad, ...).
     * Graduate schools (GP, BR, AZ, SY, FG, ...) share the Wilf/Beren campuses,
     * so campus alone is not enough to isolate undergraduate sections.
     */
    private static final Set<String> UNDERGRAD_PART_OF_TERM = Set.of("ST", "YC", "UB", "AM", "AW", "UT", "GY");

    private static final String[] DAY_FIELDS = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
    private static final String[] DAY_LETTERS = {"M", "T", "W", "R", "F", "S", "U"};

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void main(String[] args) throws Exception {
        CatalogFetcher fetcher = new CatalogFetcher();
        if (args.length >= 1 && args[0].equals("--list-terms")) {
            fetcher.listTerms();
            return;
        }
        if (args.length < 1) {
            System.out.println("Usage: java HelperClasses/CatalogFetcher.java <outputCsv> [term]");
            System.out.println("       java HelperClasses/CatalogFetcher.java --list-terms");
            return;
        }
        Path output = Path.of(args[0]);
        String term = args.length > 1 ? args[1] : "202609";

        List<Map<String, Object>> sections = fetcher.fetchAllSections(term);
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> section : sections) {
            if (keep(section)) {
                lines.add(toCsvRow(section));
            }
        }
        Files.write(output, lines);
        System.out.println("Kept " + lines.size() + " of " + sections.size()
                + " sections (Wilf/Beren undergraduate). Wrote " + output);
    }

    // ------------------------------------------------------------------ HTTP

    private void listTerms() throws Exception {
        String body = get(BASE + "/classSearch/getTerms?searchTerm=&offset=1&max=30");
        for (Object t : (List<?>) Json.parse(body)) {
            Map<?, ?> term = (Map<?, ?>) t;
            System.out.println(term.get("code") + "  " + Html.decode(String.valueOf(term.get("description"))));
        }
    }

    private List<Map<String, Object>> fetchAllSections(String term) throws Exception {
        System.out.println("Selecting term " + term + "...");
        get(BASE + "/term/termSelection?mode=search");
        post(BASE + "/term/search?mode=search", "term=" + term);
        // Banner remembers the last search per session; clear it so the
        // unfiltered query below really returns everything.
        post(BASE + "/classSearch/resetDataForm", "");

        List<Map<String, Object>> all = new ArrayList<>();
        int total = Integer.MAX_VALUE;
        for (int offset = 0; offset < total; offset += PAGE_SIZE) {
            String url = BASE + "/searchResults/searchResults?txt_term=" + term
                    + "&pageOffset=" + offset + "&pageMaxSize=" + PAGE_SIZE
                    + "&sortColumn=subjectDescription&sortDirection=asc";
            Map<?, ?> page = (Map<?, ?>) Json.parse(get(url));
            if (!Boolean.TRUE.equals(page.get("success")) || page.get("data") == null) {
                throw new IllegalStateException("Banner returned an unsuccessful search response for term "
                        + term + ". Check the term code with --list-terms.");
            }
            total = ((Number) page.get("totalCount")).intValue();
            List<?> data = (List<?>) page.get("data");
            if (data.isEmpty()) break;
            for (Object o : data) {
                @SuppressWarnings("unchecked")
                Map<String, Object> section = (Map<String, Object>) o;
                all.add(section);
            }
            System.out.println("Fetched " + all.size() + " / " + total);
        }
        if (all.size() != total) {
            System.out.println("WARNING: expected " + total + " sections but received " + all.size());
        }
        return all;
    }

    private String get(String url) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url)).GET());
    }

    private String post(String url, String form) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
    }

    private String send(HttpRequest.Builder builder) throws Exception {
        HttpRequest request = builder.header("Accept", "application/json, text/javascript, */*")
                .header("X-Requested-With", "XMLHttpRequest")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + request.uri());
        }
        if (response.uri().getHost() != null && !response.uri().getHost().contains("yu.edu")) {
            throw new IllegalStateException("Banner redirected to " + response.uri().getHost()
                    + " - guest class search may now require a login.");
        }
        return response.body();
    }

    // ------------------------------------------------------------ conversion

    private static boolean keep(Map<String, Object> s) {
        String partOfTerm = str(s.get("partOfTerm"));
        return CAMPUSES.contains(str(s.get("campusDescription")))
                && partOfTerm.length() >= 2
                && UNDERGRAD_PART_OF_TERM.contains(partOfTerm.substring(0, 2));
    }

    private static String toCsvRow(Map<String, Object> s) {
        // Variable-credit sections (indicator "TO"/"OR", e.g. 1 TO 3) carry an
        // arbitrary creditHours value inside the range; use the minimum instead.
        boolean variable = s.get("creditHourIndicator") != null;
        Object credits = !variable && s.get("creditHours") != null ? s.get("creditHours") : s.get("creditHourLow");
        List<String> fields = List.of(
                quote(str(s.get("courseReferenceNumber"))),
                quote(str(s.get("courseNumber"))),
                quote(str(s.get("subject"))),
                quote(str(s.get("sequenceNumber"))),
                quote(str(s.get("campusDescription"))),
                quote(str(s.get("courseTitle"))),
                number(credits),
                quote(instructor(s)),
                number(s.get("enrollment")),
                number(s.get("maximumEnrollment")),
                number(s.get("seatsAvailable")),
                number(s.get("waitCount")),
                number(s.get("waitCapacity")),
                number(s.get("waitAvailable")),
                quote(meetings(s)),
                quote(attributes(s, "code")),
                quote(attributes(s, "description")));
        return String.join(",", fields);
    }

    private static String instructor(Map<String, Object> s) {
        List<?> faculty = (List<?>) s.get("faculty");
        if (faculty == null || faculty.isEmpty()) return "Staff";
        return str(((Map<?, ?>) faculty.get(0)).get("displayName"));
    }

    private static String meetings(Map<String, Object> s) {
        List<?> meetings = (List<?>) s.get("meetingsFaculty");
        if (meetings == null) return "";
        List<String> encoded = new ArrayList<>();
        for (Object m : meetings) {
            Map<?, ?> time = (Map<?, ?>) ((Map<?, ?>) m).get("meetingTime");
            if (time == null) continue;
            List<String> days = new ArrayList<>();
            for (int i = 0; i < DAY_FIELDS.length; i++) {
                if (Boolean.TRUE.equals(time.get(DAY_FIELDS[i]))) days.add(DAY_LETTERS[i]);
            }
            encoded.add(String.join("||", days)
                    + "}" + orDefault(time.get("beginTime"), "N/A")
                    + "}" + orDefault(time.get("endTime"), "N/A")
                    + "}" + orDefault(time.get("building"), "TBA")
                    + "}" + str(time.get("room")));
        }
        return String.join("::", encoded);
    }

    private static String attributes(Map<String, Object> s, String key) {
        List<?> attrs = (List<?>) s.get("sectionAttributes");
        if (attrs == null) return "";
        List<String> values = new ArrayList<>();
        for (Object a : attrs) values.add(str(((Map<?, ?>) a).get(key)));
        return String.join("||", values);
    }

    private static String orDefault(Object value, String fallback) {
        String s = str(value);
        return s.isEmpty() ? fallback : s;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    /** Wraps text in quotes; commas and quotes would break Course.java's split(","). */
    private static String quote(String value) {
        return "\"" + value.replace(",", ";").replace("\"", "'").trim() + "\"";
    }

    private static String number(Object value) {
        if (value == null) return "0";
        double d = ((Number) value).doubleValue();
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    // ------------------------------------------------------------ tiny JSON

    /** Minimal JSON parser: objects -> LinkedHashMap, arrays -> ArrayList, numbers -> Double. */
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object parse(String text) {
            Json p = new Json(text);
            p.ws();
            Object v = p.value();
            p.ws();
            if (p.i != p.s.length()) throw p.error("trailing data");
            return v;
        }

        private Object value() {
            ws();
            if (i >= s.length()) throw error("unexpected end");
            char c = s.charAt(i);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return number();
            }
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; ws();
            if (peek('}')) { i++; return map; }
            while (true) {
                ws();
                String key = string();
                ws(); consume(':');
                map.put(key, value());
                ws();
                if (peek(',')) { i++; continue; }
                consume('}');
                return map;
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++; ws();
            if (peek(']')) { i++; return list; }
            while (true) {
                list.add(value());
                ws();
                if (peek(',')) { i++; continue; }
                consume(']');
                return list;
            }
        }

        private String string() {
            consume('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: sb.append(e);
                }
            }
        }

        private Double number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw error("unexpected character '" + s.charAt(i) + "'");
            return Double.parseDouble(s.substring(start, i));
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
        private void consume(char c) { if (!peek(c)) throw error("expected '" + c + "'"); i++; }
        private void expect(String word) {
            if (!s.startsWith(word, i)) throw error("expected " + word);
            i += word.length();
        }
        private IllegalStateException error(String msg) {
            return new IllegalStateException("Bad JSON from Banner at char " + i + ": " + msg
                    + " (is the response an HTML login page?)");
        }
    }

    /** Decodes the few HTML entities Banner puts in term names. */
    static final class Html {
        static String decode(String s) {
            return s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"");
        }
    }
}
