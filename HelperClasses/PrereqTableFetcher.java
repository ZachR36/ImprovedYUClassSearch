import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * PrereqTableFetcher
 * ==================
 * Replaces the regex-parsed prerequisite/corequisite columns with Banner's own
 * structured requirement tables, and writes a report comparing the two.
 *
 * WHY THIS EXISTS:
 * PrereqExtractor guesses requirements from description prose, which gets
 * AND/OR wrong ("BIO 1011 and BIO 1013" becomes OR), reads "Crosslisted with X"
 * and "Previously offered as X" as prerequisites, and cannot expand phrases
 * like "foundation courses" or "or equivalent". Banner stores the real rules
 * behind two guest-accessible endpoints (same term-selection session as
 * DescriptionFetcher):
 *   POST /searchResults/getSectionPrerequisites  - And/Or rows with parentheses
 *   POST /searchResults/getCorequisites          - a flat list of courses
 *
 * HOW IT WORKS:
 * 1. Selects the term to get a session cookie.
 * 2. Builds a subject-name -> subject-code map ("Accounting" -> "ACC"). The
 *    tables only give subject names, and get_subject only lists subjects
 *    offered in the requested term, so recent terms are merged together
 *    (current term first) to cover Beren "(UW)" subjects and the like.
 * 3. Fetches both tables for every CRN, several requests at a time.
 * 4. Parses the prerequisite rows into a boolean expression (Banner gives And
 *    and Or equal precedence, left to right, plus its parentheses) and
 *    converts it to the AND-of-ORs form Course.java reads: groups joined by
 *    ";;", the alternatives inside a group joined by "|".
 * 5. Rebuilds the requirement columns for each row:
 *    - prereqGroups:    the table's groups, minus the ones below
 *    - flexiblePrereqs: table groups containing a course that the
 *                       description's "may be taken as a prerequisite or
 *                       corequisite" phrase covers (the table has no field
 *                       for that; see flexibleCodes)
 *    - coreqGroups:     every course in the corequisite table, ANDed
 *    - requirementNotes: the description's notes, plus table rows that are
 *                       not course codes (test scores, unmapped subjects)
 *    A column whose table is empty keeps PrereqExtractor's value. Minimum
 *    grades are not carried over.
 * 6. Writes a per-course report comparing table and description groups.
 *
 * USAGE:
 *   java HelperClasses/PrereqTableFetcher.java <inputCsv> <outputCsv> [term] [reportFile]
 *
 * EXAMPLE:
 *   java HelperClasses/PrereqTableFetcher.java spring2027CoursesFinal.csv \
 *        spring2027CoursesTable.csv 202701 prereq-report.txt
 *
 * The input is PrereqExtractor's 22-column output; the output has the same layout.
 */
public class PrereqTableFetcher {

    private static final String BASE = "https://banner.oci.yu.edu/StudentRegistrationSsb/ssb";
    private static final int THREADS = 8;
    private static final int TERMS_FOR_SUBJECTS = 30;
    /** Expressions whose AND-of-ORs form exceeds this many groups are left to the description. */
    private static final int MAX_GROUPS = 40;

    private static final int PREREQ = 17, COREQ = 18, FLEXIBLE = 19, NOTES = 20, DESCRIPTION = 21;
    private static final Pattern CODE_AND_DESCRIPTION =
            Pattern.compile("\"code\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"description\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PREREQ_CLAUSE = Pattern.compile("(?i)pre-?requisites?\\s*(\\(s\\))?\\s*:");
    /** "Corequisite(s)", "Corequisite (s)" or "Corequisites:", not the word inside "... or corequisite." */
    private static final Pattern COREQ_LABEL =
            Pattern.compile("(?i)co-?requisite\\s*\\(\\s*s\\s*\\)|co-?requisites?\\s*:");
    private static final Pattern FLEXIBLE_PHRASE = Pattern.compile("(?i)may be taken? as");
    private static final Pattern DESCRIPTION_CODE = Pattern.compile("\\b([A-Z]{2,5})\\s?(\\d{3,4}[A-Z]?)\\b");
    /** Syms "foundation courses", as Banner's tables expand them (honors variants included by the tables). */
    private static final Set<String> FOUNDATION_COURSES =
            Set.of("ACC1002", "FIN1001", "IDS1020", "IDS1556", "ENT1020", "MAN1020", "MAR1001");

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .build();
    private final String term;
    private final Map<String, String> subjectCodes = new HashMap<>();

    private PrereqTableFetcher(String term) {
        this.term = term;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java HelperClasses/PrereqTableFetcher.java <inputCsv> <outputCsv> [term] [reportFile]");
            return;
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String term = args.length > 2 ? args[2] : "202609";
        Path reportPath = Path.of(args.length > 3 ? args[3] : output + ".report.txt");

        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(input)) {
            if (line.isBlank()) continue;
            String[] fields = parseQuotedCsvLine(line);
            if (fields.length != 22) {
                throw new IllegalStateException("Expected PrereqExtractor's 22-column output, got "
                        + fields.length + " fields: " + line.substring(0, Math.min(80, line.length())));
            }
            rows.add(fields);
        }

        PrereqTableFetcher fetcher = new PrereqTableFetcher(term);
        fetcher.selectTerm();
        fetcher.loadSubjectCodes();

        List<String> crns = rows.stream().map(r -> r[0]).distinct().toList();
        System.out.println("Fetching requirement tables for " + crns.size() + " sections...");
        Map<String, Tables> tables = fetcher.fetchAll(crns);

        Report report = new Report();
        List<String> outputLines = new ArrayList<>();
        for (String[] row : rows) {
            Tables t = tables.get(row[0]);
            String[] rebuilt = t == null ? row : rebuild(row, t);
            report.add(row, rebuilt, t);
            outputLines.add(toCsvLine(rebuilt));
        }
        Files.write(output, outputLines);
        Files.writeString(reportPath, report.render());
        System.out.println(report.summary());
        System.out.println("Wrote " + output + " and " + reportPath);
    }

    // ------------------------------------------------------------------ HTTP

    private void selectTerm() throws Exception {
        send(HttpRequest.newBuilder(URI.create(BASE + "/term/termSelection?mode=search")).GET());
        post("/term/search?mode=search", "term=" + term);
    }

    /** Subject names -> codes, from the current term first, then recent terms. */
    private void loadSubjectCodes() throws Exception {
        List<String> terms = new ArrayList<>(List.of(term));
        String termList = send(HttpRequest.newBuilder(URI.create(
                BASE + "/classSearch/getTerms?searchTerm=&offset=1&max=" + TERMS_FOR_SUBJECTS)).GET());
        for (String[] pair : codeDescriptionPairs(termList)) {
            if (!terms.contains(pair[0])) terms.add(pair[0]);
        }
        List<String> responses = parallel(terms, t -> send(HttpRequest.newBuilder(URI.create(
                BASE + "/classSearch/get_subject?searchTerm=&term=" + t + "&offset=1&max=500")).GET()));
        for (String response : responses) {
            for (String[] pair : codeDescriptionPairs(response)) {
                subjectCodes.putIfAbsent(pair[1], pair[0]);
            }
        }
        System.out.println("Mapped " + subjectCodes.size() + " subject names across " + terms.size() + " terms.");
    }

    private Map<String, Tables> fetchAll(List<String> crns) throws Exception {
        List<Tables> fetched = parallel(crns, crn -> {
            String form = "term=" + term + "&courseReferenceNumber=" + crn;
            String prereqHtml = post("/searchResults/getSectionPrerequisites", form);
            String coreqHtml = post("/searchResults/getCorequisites", form);
            return parseTables(prereqHtml, coreqHtml);
        });
        Map<String, Tables> byCrn = new HashMap<>();
        for (int i = 0; i < crns.size(); i++) {
            if (fetched.get(i) != null) byCrn.put(crns.get(i), fetched.get(i));
        }
        return byCrn;
    }

    private interface Task<T> {
        T run(String input) throws Exception;
    }

    /** Runs task over inputs on THREADS threads; a failed input yields null and a warning. */
    private static <T> List<T> parallel(List<String> inputs, Task<T> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (String input : inputs) {
                futures.add(pool.submit(() -> {
                    try {
                        return task.run(input);
                    } catch (Exception first) {
                        return task.run(input); // one retry for transient network errors
                    }
                }));
            }
            List<T> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(futures.get(i).get());
                } catch (ExecutionException e) {
                    System.out.println("  Failed on " + inputs.get(i) + ": " + e.getCause().getMessage());
                    results.add(null);
                }
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    private String post(String path, String form) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
    }

    private String send(HttpRequest.Builder builder) throws Exception {
        HttpRequest request = builder.header("X-Requested-With", "XMLHttpRequest")
                .timeout(java.time.Duration.ofSeconds(30))
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

    private static List<String[]> codeDescriptionPairs(String json) {
        List<String[]> pairs = new ArrayList<>();
        Matcher m = CODE_AND_DESCRIPTION.matcher(json);
        while (m.find()) pairs.add(new String[] {m.group(1), decode(m.group(2))});
        return pairs;
    }

    // ------------------------------------------------------- table parsing

    /** One requirement leaf: a course code, or something that is not one (test score, unknown subject). */
    private record Leaf(String code, String other) {
        static Leaf course(String code) { return new Leaf(code, null); }
        static Leaf note(String text) { return new Leaf(null, text); }
    }

    private record Tables(List<List<Leaf>> prereqGroups, List<String> coreqs, List<String> notes, String error) {}

    private Tables parseTables(String prereqHtml, String coreqHtml) {
        if (!prereqHtml.contains("<section") || !coreqHtml.contains("<section")) {
            throw new IllegalStateException("unexpected response (not a requirements section)");
        }
        List<String> notes = new ArrayList<>();

        List<String> coreqs = new ArrayList<>();
        for (List<String> cells : tableRows(coreqHtml)) {
            Leaf leaf = courseLeaf(cells.get(0), cells.get(1));
            if (leaf.code() != null) {
                if (!coreqs.contains(leaf.code())) coreqs.add(leaf.code());
            } else {
                notes.add("Corequisite " + leaf.other());
            }
        }

        // Prereq columns: 0 And/Or, 1 "(", 2 test, 3 score, 4 subject, 5 number, 6 level, 7 grade, 8 ")"
        List<String> tokens = new ArrayList<>();
        List<Leaf> leaves = new ArrayList<>();
        for (List<String> c : tableRows(prereqHtml)) {
            if (c.size() < 9) continue;
            if (!c.get(0).isEmpty()) tokens.add(c.get(0).toUpperCase());
            for (char ch : c.get(1).toCharArray()) if (ch == '(') tokens.add("(");
            // Some rows only carry a parenthesis (no test and no course).
            if (!c.get(2).isEmpty() || !c.get(4).isEmpty()) {
                Leaf leaf = !c.get(2).isEmpty()
                        ? Leaf.note("Test alternative: " + c.get(2) + " = " + c.get(3))
                        : courseLeaf(c.get(4), c.get(5));
                tokens.add("#" + leaves.size());
                leaves.add(leaf);
            }
            for (char ch : c.get(8).toCharArray()) if (ch == ')') tokens.add(")");
        }

        List<List<Leaf>> groups = new ArrayList<>();
        String error = null;
        if (!tokens.isEmpty()) {
            try {
                Expr expr = new ExprParser(tokens, leaves).parse();
                groups = toGroups(expr);
            } catch (RuntimeException e) {
                error = e.getMessage();
            }
        }
        return new Tables(groups, coreqs, notes, error);
    }

    private Leaf courseLeaf(String subjectName, String number) {
        String code = subjectCodes.get(subjectName);
        return code == null ? Leaf.note("Unmapped subject: " + subjectName + " " + number)
                : Leaf.course(code + number.toUpperCase());
    }

    private static List<List<String>> tableRows(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher body = Pattern.compile("(?s)<tbody>(.*?)</tbody>").matcher(html);
        while (body.find()) {
            Matcher tr = Pattern.compile("(?s)<tr>(.*?)</tr>").matcher(body.group(1));
            while (tr.find()) {
                List<String> cells = new ArrayList<>();
                Matcher td = Pattern.compile("(?s)<td[^>]*>(.*?)</td>").matcher(tr.group(1));
                while (td.find()) {
                    cells.add(decode(td.group(1).replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim()));
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    // ------------------------------------------------- boolean expressions

    private sealed interface Expr permits Atom, And, Or {}
    private record Atom(Leaf leaf) implements Expr {}
    private record And(List<Expr> parts) implements Expr {}
    private record Or(List<Expr> parts) implements Expr {}

    /**
     * expr := factor ((AND | OR) factor)* ; factor := "(" expr ")" | leaf
     * Banner gives And and Or equal precedence and evaluates left to right, e.g.
     * "ACC 1002 Or ACC 1002H And (FIN 1001 Or FIN 1001H)" means
     * "(ACC 1002 Or ACC 1002H) And (FIN 1001 Or FIN 1001H)".
     */
    private static final class ExprParser {
        private final List<String> tokens;
        private final List<Leaf> leaves;
        private int pos;

        ExprParser(List<String> tokens, List<Leaf> leaves) {
            this.tokens = tokens;
            this.leaves = leaves;
        }

        Expr parse() {
            Expr e = expr();
            if (pos != tokens.size()) throw new IllegalStateException("unexpected '" + tokens.get(pos) + "'");
            return e;
        }

        private Expr expr() {
            Expr left = factor();
            while (pos < tokens.size() && (tokens.get(pos).equals("AND") || tokens.get(pos).equals("OR"))) {
                boolean and = tokens.get(pos++).equals("AND");
                Expr right = factor();
                left = and ? new And(flatten(left, right, And.class)) : new Or(flatten(left, right, Or.class));
            }
            return left;
        }

        /** Keeps a chain like A Or B Or C as one Or of three parts rather than nested pairs. */
        private static List<Expr> flatten(Expr left, Expr right, Class<? extends Expr> type) {
            List<Expr> parts = new ArrayList<>();
            if (left instanceof And a && type == And.class) parts.addAll(a.parts());
            else if (left instanceof Or o && type == Or.class) parts.addAll(o.parts());
            else parts.add(left);
            parts.add(right);
            return parts;
        }

        private Expr factor() {
            if (pos >= tokens.size()) throw new IllegalStateException("expression ended early");
            String t = tokens.get(pos++);
            if (t.equals("(")) {
                Expr e = expr();
                if (pos >= tokens.size() || !tokens.get(pos++).equals(")")) {
                    throw new IllegalStateException("unbalanced parentheses");
                }
                return e;
            }
            if (t.startsWith("#")) return new Atom(leaves.get(Integer.parseInt(t.substring(1))));
            throw new IllegalStateException("unexpected '" + t + "'");
        }
    }

    /**
     * Converts an expression to AND-of-ORs: a list of groups, each satisfied by
     * any one of its leaves. Groups that contain another group are dropped
     * (A AND (A OR B) is just A).
     */
    private static List<List<Leaf>> toGroups(Expr e) {
        List<Set<Leaf>> groups = new ArrayList<>(new LinkedHashSet<>(cnf(e))); // drop exact duplicates
        List<List<Leaf>> result = new ArrayList<>();
        for (Set<Leaf> g : groups) {
            boolean redundant = groups.stream().anyMatch(other -> other.size() < g.size() && g.containsAll(other));
            if (!redundant) result.add(new ArrayList<>(g));
        }
        return result;
    }

    private static List<Set<Leaf>> cnf(Expr e) {
        if (e instanceof Atom a) {
            return new ArrayList<>(List.of(new LinkedHashSet<>(List.of(a.leaf()))));
        }
        if (e instanceof And and) {
            List<Set<Leaf>> all = new ArrayList<>();
            for (Expr part : and.parts()) all.addAll(cnf(part));
            return all;
        }
        List<Set<Leaf>> product = List.of(new LinkedHashSet<>());
        for (Expr part : ((Or) e).parts()) {
            List<Set<Leaf>> next = new ArrayList<>();
            for (Set<Leaf> left : product) {
                for (Set<Leaf> right : cnf(part)) {
                    Set<Leaf> merged = new LinkedHashSet<>(left);
                    merged.addAll(right);
                    next.add(merged);
                }
            }
            if (next.size() > MAX_GROUPS) {
                throw new IllegalStateException("expression too large to express as ;;/| groups");
            }
            product = next;
        }
        return product;
    }

    // ------------------------------------------------------- column rebuild

    private static String[] rebuild(String[] row, Tables t) {
        String[] out = row.clone();
        List<String> notes = new ArrayList<>();
        if (!row[NOTES].isEmpty()) notes.add(row[NOTES]);
        notes.addAll(t.notes());

        if (t.error() != null) {
            notes.add("Banner prerequisite table not converted (" + t.error() + ")");
        } else if (!t.prereqGroups().isEmpty()) {
            Set<String> flexibleCodes = flexibleCodes(row[DESCRIPTION]);
            List<String> strict = new ArrayList<>();
            List<String> flexible = new ArrayList<>();
            for (List<Leaf> group : t.prereqGroups()) {
                List<String> codes = new ArrayList<>();
                for (Leaf leaf : group) {
                    if (leaf.code() != null) {
                        codes.add(leaf.code());
                    } else if (!notes.contains(leaf.other())) {
                        notes.add(leaf.other());
                    }
                }
                if (codes.isEmpty()) continue; // e.g. a required test score: kept as a note only
                String joined = String.join("|", codes);
                if (codes.stream().anyMatch(flexibleCodes::contains)) {
                    flexible.add(joined);
                } else {
                    strict.add(joined);
                }
            }
            out[PREREQ] = String.join(";;", strict);
            out[FLEXIBLE] = String.join(";;", flexible);
        }
        if (!t.coreqs().isEmpty()) {
            out[COREQ] = String.join(";;", t.coreqs());
        }
        out[NOTES] = String.join(" | ", notes);
        return out;
    }

    /**
     * Courses the description allows to be taken as a prerequisite OR corequisite.
     * Only the ";"-separated piece of the Prerequisite clause that says
     * "may be taken as ..." counts: in "FIN 1001; IDS 1131 and IDS 1020; and
     * ACC 1002 may be taken as prerequisite or corequisite" only ACC 1002 is
     * flexible. "Foundation courses" in that piece means the Syms business core.
     */
    private static Set<String> flexibleCodes(String description) {
        Set<String> codes = new LinkedHashSet<>();
        Matcher label = PREREQ_CLAUSE.matcher(description);
        if (!label.find()) return codes;
        String clause = description.substring(label.end());
        Matcher coreqLabel = COREQ_LABEL.matcher(clause);
        if (coreqLabel.find()) clause = clause.substring(0, coreqLabel.start());
        for (String piece : clause.split(";")) {
            if (!FLEXIBLE_PHRASE.matcher(piece).find()) continue;
            Matcher m = DESCRIPTION_CODE.matcher(piece);
            while (m.find()) codes.add(m.group(1) + m.group(2));
            if (piece.toLowerCase().contains("foundation course")) codes.addAll(FOUNDATION_COURSES);
        }
        return codes;
    }

    // -------------------------------------------------------------- report

    /** Per-course comparison of description-derived groups against Banner's tables. */
    private static final class Report {
        private final Map<String, Integer> counts = new TreeMap<>();
        private final Map<String, List<String>> sections = new TreeMap<>();
        private final Set<String> seenCourses = new HashSet<>();

        void add(String[] before, String[] after, Tables t) {
            String course = before[2] + before[1];
            if (!seenCourses.add(course)) return;

            String category;
            if (t == null) {
                category = "FETCH FAILED (description values kept)";
            } else if (t.error() != null) {
                category = "TABLE NOT CONVERTED (description values kept)";
            } else {
                boolean tableHas = !t.prereqGroups().isEmpty() || !t.coreqs().isEmpty();
                boolean descHas = !(before[PREREQ] + before[COREQ] + before[FLEXIBLE] + before[NOTES]).isEmpty();
                if (!tableHas && !descHas) category = "NO REQUIREMENTS EITHER WAY";
                else if (!tableHas) category = "DESCRIPTION ONLY (tables empty)";
                else if (!descHas) category = "TABLE ONLY (description has none)";
                else category = compare(before, after);
            }
            counts.merge(category, 1, Integer::sum);
            if (category.startsWith("SAME") || category.startsWith("NO REQUIREMENTS")) return;

            String requirementText = requirementText(before[DESCRIPTION]);
            sections.computeIfAbsent(category, k -> new ArrayList<>()).add(String.join("\n",
                    course + " (CRN " + before[0] + ") " + before[5],
                    "  description: " + requirementText,
                    "  extractor:   " + columns(before),
                    "  table:       " + columns(after),
                    ""));
        }

        private static String compare(String[] before, String[] after) {
            List<Set<String>> desc = groups(before[PREREQ] + ";;" + before[FLEXIBLE]);
            List<Set<String>> table = groups(after[PREREQ] + ";;" + after[FLEXIBLE]);
            boolean coreqSame = groups(before[COREQ]).equals(groups(after[COREQ]));
            if (desc.equals(table) && coreqSame) return "SAME";
            if (coreqSame && widens(desc, table)) return "TABLE ADDS ALTERNATIVES (same requirements)";
            return "DIFFERENT (review)";
        }

        /** Same number of groups, and each description group is contained in its own table group. */
        private static boolean widens(List<Set<String>> desc, List<Set<String>> table) {
            if (desc.size() != table.size()) return false;
            List<Set<String>> unused = new ArrayList<>(table);
            for (Set<String> d : desc) {
                Set<String> match = unused.stream().filter(t -> t.containsAll(d)).findFirst().orElse(null);
                if (match == null) return false;
                unused.remove(match);
            }
            return true;
        }

        private static List<Set<String>> groups(String text) {
            List<Set<String>> groups = new ArrayList<>();
            for (String g : text.split(";;")) {
                if (g.isBlank()) continue;
                Set<String> alternatives = new TreeSet<>(Arrays.asList(g.split("\\|")));
                if (!groups.contains(alternatives)) groups.add(alternatives);
            }
            groups.sort(Comparator.comparing(Object::toString));
            return groups;
        }

        private static String columns(String[] r) {
            return "pre=" + r[PREREQ] + "  co=" + r[COREQ] + "  flex=" + r[FLEXIBLE]
                    + (r[NOTES].isEmpty() ? "" : "  notes=" + r[NOTES]);
        }

        private static String requirementText(String description) {
            Matcher m = Pattern.compile("(?i)(pre|co)-?requisite").matcher(description);
            if (!m.find()) return "(no requirement text)";
            return description.substring(m.start(), Math.min(description.length(), m.start() + 220));
        }

        String summary() {
            StringBuilder sb = new StringBuilder("Courses by comparison result:\n");
            counts.forEach((k, v) -> sb.append(String.format("  %4d  %s%n", v, k)));
            return sb.toString();
        }

        String render() {
            StringBuilder sb = new StringBuilder(summary()).append('\n');
            sections.forEach((category, entries) -> {
                sb.append("==== ").append(category).append(" (").append(entries.size()).append(")\n\n");
                entries.forEach(sb::append);
            });
            return sb.toString();
        }
    }

    // ----------------------------------------------------------------- CSV

    private static String[] parseQuotedCsvLine(String line) {
        String[] fields = line.split(",", -1);
        for (int i = 0; i < fields.length; i++) {
            String f = fields[i];
            if (f.length() >= 2 && f.startsWith("\"") && f.endsWith("\"")) fields[i] = f.substring(1, f.length() - 1);
        }
        return fields;
    }

    private static String toCsvLine(String[] fields) {
        StringJoiner joiner = new StringJoiner(",");
        for (String f : fields) joiner.add("\"" + f.replace("\"", "'").replace(",", ";") + "\"");
        return joiner.toString();
    }

    private static String decode(String s) {
        return s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("&nbsp;", " ");
    }
}
