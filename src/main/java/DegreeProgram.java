import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DegreeProgram
 * =============
 * Represents one major/minor/degree program's requirements, parsed from a
 * single "=== Program Name ===" - delimited chunk of majorMinorRequirements.txt.
 *
 * FILE FORMAT (indentation-based, 2 spaces per nesting level):
 *
 *   === Some Program Name ===
 *   name: Some Program Name
 *   school: YC
 *   degreeType: BA
 *   totalCredits: 33
 *
 *   COURSE_LIST: Core Courses
 *     DEPT1234
 *     DEPT5678
 *
 *   CHOOSE_N: 2
 *     OPTION: DEPT1111
 *     OPTION:
 *       CHOOSE_N: 1
 *         OPTION: DEPT2222
 *         OPTION: DEPT3333
 *
 *   RANGE: DEPT 1000-4999 | count: 3
 *
 *   TRACK_CHOICE: Concentration (pick exactly 1)
 *     TRACK: Some Track Name
 *       COURSE_LIST:
 *         DEPT4444
 *
 *   MANUAL_NOTE: Free text describing a requirement that can't be checked
 *     automatically - continuation lines are just more-indented text.
 *
 * FIVE REQUIREMENT TYPES (all implement RequirementNode):
 *   - CourseListRequirement - a flat list of course codes, ALL required (AND).
 *   - ChooseNRequirement    - pick N of the listed Options. Each Option is
 *                             EITHER a single course code, OR (if the "OPTION:"
 *                             line has nothing after the colon) a nested
 *                             RequirementNode - this is how OR-of-OR and
 *                             OR-of-RANGE compound choices are expressed.
 *   - RangeRequirement      - a department+number-range wildcard (e.g. any
 *                             course numbered COM 1000-4999). Supports
 *                             multiple departments separated by "|" (e.g.
 *                             "ACC|FIN|MAR|ENT|IDS 1000-4999"). Its "count"
 *                             is null when nested inside an Option, since the
 *                             enclosing CHOOSE_N's count already governs how
 *                             many picks are needed.
 *   - TrackChoiceRequirement - pick exactly one of several named Tracks, each
 *                             itself a full list of RequirementNodes - this is
 *                             the recursive/nested case (e.g. concentrations).
 *   - ManualNoteRequirement - free text for a requirement that isn't
 *                             course-list-based at all (credit buckets,
 *                             advisor-approved substitutions, etc.) - NEVER
 *                             treated as a blocking/checkable requirement by
 *                             downstream logic; it exists purely so the
 *                             information isn't lost.
 *
 * USAGE:
 *   Map<String, DegreeProgram> catalog = DegreeProgram.loadCatalog("majorMinorRequirements.txt");
 *   DegreeProgram program = catalog.get(DegreeProgram.makeKey("Mathematics, BA", "YC"));
 *
 * This class only loads and represents the data - it does not yet contain
 * any logic for checking a student's completed courses against it. That's a
 * separate, later design step.
 */
public class DegreeProgram {

    // The full set of loaded programs, populated once by setup(path) at
    // program startup - mirrors how SearchEngine owns its own course indexes,
    // kept separate since degree-program data is a distinct concern from
    // class search.
    private static Map<String, DegreeProgram> catalog;

    private final String name;
    private final String school;
    private final String degreeType;
    private final String totalCredits;
    private final List<RequirementNode> requirements;

    private DegreeProgram(String name, String school, String degreeType, String totalCredits,
                          List<RequirementNode> requirements) {
        this.name = name;
        this.school = school;
        this.degreeType = degreeType;
        this.totalCredits = totalCredits;
        this.requirements = requirements;
    }

    public String getName() { return name; }
    public String getSchool() { return school; }
    public String getDegreeType() { return degreeType; }
    public String getTotalCredits() { return totalCredits; }
    public List<RequirementNode> getRequirements() { return requirements; }
    /** Returns this program's declared track choice(s). Current program data
     * has at most one, but the list keeps the model extensible. */
    public List<TrackChoiceRequirement> getTrackChoices() {
        List<TrackChoiceRequirement> choices = new ArrayList<>();
        for (RequirementNode requirement : requirements) {
            if (requirement instanceof TrackChoiceRequirement trackChoice) {
                choices.add(trackChoice);
            }
        }
        return choices;
    }
    /** The canonical catalog key suitable for persistent profile storage. */
    public String getCatalogKey() { return makeKey(name, school); }

    // ===================== LOADING =====================

    /**
     * Loads every program in the file into a map keyed by "name@school" (see
     * makeKey), so a specific program can be looked up in O(1) instead of
     * scanning a list. Throws if two programs would produce the same key -
     * that would mean one silently overwrote the other, which should never
     * happen with well-formed data and is worth failing loudly on instead.
     */
    /** Loads the catalog and stores it as this class's own static state, the
     *  same way SearchEngine.setup(path) loads and holds the course data. */
    public static void setup(String path) throws IOException {
        catalog = loadCatalog(path);
    }

    public static Map<String, DegreeProgram> getCatalog() {
        return catalog;
    }

    /** Looks up a program by the canonical name@school key stored in a profile. */
    public static DegreeProgram findByCatalogKey(Map<String, DegreeProgram> catalog, String catalogKey) {
        if (catalogKey == null || catalogKey.isBlank()) {
            return null;
        }
        int separator = catalogKey.lastIndexOf('@');
        if (separator <= 0 || separator == catalogKey.length() - 1) {
            return null;
        }
        return catalog.get(makeKey(catalogKey.substring(0, separator), catalogKey.substring(separator + 1)));
    }

    /**
     * Looks up a program by name for a given student's home school, applying
     * the Sy Syms fallback: most minors are filed once under "Syms" but branch
     * internally via a TrackChoiceRequirement whose tracks are tagged for the
     * student's actual home school (e.g. "Sy Syms students" vs "Stern/YC
     * students"). Tries the direct key first; if that's absent, falls back to
     * the "Syms" filing. User setup then stores the matching tagged track.
     * Returns null if neither lookup succeeds.
     */
    public static DegreeProgram findForStudent(Map<String, DegreeProgram> catalog, String programName,
                                                String studentSchool) {
        DegreeProgram direct = catalog.get(makeKey(programName, studentSchool));
        if (direct != null) {
            return direct;
        }
        return catalog.get(makeKey(programName, "Syms"));
    }

    /**
     * Finds every catalog program whose name loosely matches a student-typed
     * query - case-insensitive, and matching in either direction (handles a
     * query that's a substring of the real name, e.g. "Computer Science" for
     * "Computer Science, BA", and the rarer case of a longer typed name). Used
     * when an exact name+school lookup fails, since students often don't know
     * a program's exact on-file spelling (degree-type suffix, punctuation).
     */
    public static List<DegreeProgram> findMatches(Map<String, DegreeProgram> catalog, String query) {
        String normalizedQuery = query.trim().toLowerCase();
        List<DegreeProgram> matches = new ArrayList<>();
        for (DegreeProgram program : catalog.values()) {
            String normalizedName = program.name.toLowerCase();
            if (normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName)) {
                matches.add(program);
            }
        }
        return matches;
    }

    /** Every loaded program's display label, sorted - for showing a student
     *  the exact on-file spellings when they don't know them. */
    public static List<String> listAllPrograms(Map<String, DegreeProgram> catalog) {
        List<String> labels = new ArrayList<>();
        for (DegreeProgram program : catalog.values()) {
            labels.add(program.getDisplayLabel());
        }
        Collections.sort(labels);
        return labels;
    }

    /** A one-line label disambiguating this program by school and degree type,
     *  e.g. "Computer Science, BA (Stern, BA)". */
    public String getDisplayLabel() {
        return name + " (" + school + (degreeType != null && !degreeType.isBlank() ? ", " + degreeType : "") + ")";
    }

    public static Map<String, DegreeProgram> loadCatalog(String path) throws IOException {
        List<String> allLines = Files.readAllLines(Path.of(path));
        Map<String, DegreeProgram> catalog = new HashMap<>();

        List<String> currentChunk = null;
        for (String line : allLines) {
            if (line.startsWith("=== ")) {
                if (currentChunk != null) {
                    addToCatalog(catalog, parseChunk(currentChunk));
                }
                currentChunk = new ArrayList<>();
            } else if (currentChunk != null) {
                currentChunk.add(line);
            }
        }
        if (currentChunk != null) {
            addToCatalog(catalog, parseChunk(currentChunk));
        }
        return catalog;
    }

    private static void addToCatalog(Map<String, DegreeProgram> catalog, DegreeProgram program) {
        String key = makeKey(program.name, program.school);
        if (catalog.containsKey(key)) {
            throw new IllegalStateException("Duplicate program key while loading catalog: " + key
                    + " - two programs produced the same name+school, one would silently overwrite the other.");
        }
        catalog.put(key, program);
    }

    /** Builds the same lookup key used internally, so callers can find a program by name + school.
     *  Case-insensitive, since User.getSchool() stores school names lowercased (e.g. "yc") while the
     *  requirements file stores them as written (e.g. "YC"). */
    public static String makeKey(String name, String school) {
        return name.trim().toLowerCase() + "@" + school.trim().toLowerCase();
    }

    // ===================== CHUNK / METADATA PARSING =====================

    private static final Pattern METADATA_LINE =
            Pattern.compile("^(name|school|degreeType|totalCredits):\\s*(.*)$");

    private static DegreeProgram parseChunk(List<String> chunkLines) {
        String name = null, school = null, degreeType = null, totalCredits = null;
        int bodyStart = 0;

        for (int i = 0; i < chunkLines.size(); i++) {
            String line = chunkLines.get(i);
            if (line.isBlank()) {
                continue;
            }
            Matcher m = METADATA_LINE.matcher(line.trim());
            if (!m.matches()) {
                bodyStart = i;
                break;
            }
            String value = m.group(2);
            switch (m.group(1)) {
                case "name" -> name = value;
                case "school" -> school = value;
                case "degreeType" -> degreeType = value;
                case "totalCredits" -> totalCredits = value;
                default -> { /* unreachable - regex only matches these four keys */ }
            }
            bodyStart = i + 1;
        }

        if (name == null || school == null) {
            throw new IllegalArgumentException("Program chunk missing required 'name' or 'school' metadata: "
                    + (chunkLines.isEmpty() ? "(empty chunk)" : chunkLines.get(0)));
        }

        LineCursor cursor = new LineCursor(chunkLines, bodyStart);
        List<RequirementNode> requirements = parseNodes(cursor, -1);
        return new DegreeProgram(name, school, degreeType, totalCredits, requirements);
    }

    // ===================== LINE CURSOR =====================

    /** A simple forward-only pointer into the chunk's lines, so nested parsing
     *  methods can share one position as they recurse. */
    private static class LineCursor {
        final List<String> lines;
        int pos;
        LineCursor(List<String> lines, int start) { this.lines = lines; this.pos = start; }
        String peek() { return pos < lines.size() ? lines.get(pos) : null; }
        String next() { return lines.get(pos++); }
        boolean hasNext() { return pos < lines.size(); }
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    private static String stripComment(String s) {
        int hashIndex = s.indexOf(" #");
        return (hashIndex >= 0 ? s.substring(0, hashIndex) : s).trim();
    }

    // ===================== GENERIC NODE PARSING =====================

    /**
     * Parses every sibling requirement node more deeply indented than
     * parentIndent, in document order, stopping at the first line indented at
     * or above parentIndent (or end of input). Blank lines are skipped and
     * never affect nesting. Used for a program's top-level body, a TRACK's
     * body, and an OPTION's single nested node.
     */
    private static List<RequirementNode> parseNodes(LineCursor cursor, int parentIndent) {
        List<RequirementNode> nodes = new ArrayList<>();
        while (cursor.hasNext()) {
            String line = cursor.peek();
            if (line.isBlank()) { cursor.next(); continue; }
            int indent = indentOf(line);
            if (indent <= parentIndent) break;
            cursor.next();
            nodes.add(parseOneNode(line.trim(), cursor, indent));
        }
        return nodes;
    }

    private static RequirementNode parseOneNode(String trimmedLine, LineCursor cursor, int myIndent) {
        if (trimmedLine.startsWith("COURSE_LIST:")) {
            String label = stripComment(trimmedLine.substring("COURSE_LIST:".length()));
            List<String> codes = collectIndentedTextLines(cursor, myIndent);
            return new CourseListRequirement(label.isEmpty() ? null : label, codes);
        }
        if (trimmedLine.startsWith("CHOOSE_N:")) {
            String rest = stripComment(trimmedLine.substring("CHOOSE_N:".length()));
            int n = Integer.parseInt(rest.trim());
            List<Option> options = collectOptions(cursor, myIndent);
            return new ChooseNRequirement(n, options);
        }
        if (trimmedLine.startsWith("TRACK_CHOICE:")) {
            String label = stripComment(trimmedLine.substring("TRACK_CHOICE:".length()));
            List<Track> tracks = collectTracks(cursor, myIndent);
            return new TrackChoiceRequirement(label.isEmpty() ? null : label, tracks);
        }
        if (trimmedLine.startsWith("RANGE:")) {
            return parseRange(stripComment(trimmedLine.substring("RANGE:".length())));
        }
        if (trimmedLine.startsWith("MANUAL_NOTE:")) {
            String text = trimmedLine.substring("MANUAL_NOTE:".length()).trim();
            StringBuilder full = new StringBuilder(text);
            for (String continuation : collectIndentedTextLines(cursor, myIndent)) {
                full.append("\n").append(continuation);
            }
            return new ManualNoteRequirement(full.toString());
        }
        // Unrecognized line - surface it as a note rather than silently dropping it.
        return new ManualNoteRequirement("UNRECOGNIZED LINE: " + trimmedLine);
    }

    /** Collects lines more deeply indented than parentIndent as plain trimmed
     *  text, with no tag interpretation - used for COURSE_LIST's bare course
     *  codes and for MANUAL_NOTE's continuation lines. */
    private static List<String> collectIndentedTextLines(LineCursor cursor, int parentIndent) {
        List<String> result = new ArrayList<>();
        while (cursor.hasNext()) {
            String line = cursor.peek();
            if (line.isBlank()) { cursor.next(); continue; }
            if (indentOf(line) <= parentIndent) break;
            cursor.next();
            result.add(line.trim());
        }
        return result;
    }

    private static List<Option> collectOptions(LineCursor cursor, int parentIndent) {
        List<Option> options = new ArrayList<>();
        while (cursor.hasNext()) {
            String line = cursor.peek();
            if (line.isBlank()) { cursor.next(); continue; }
            int indent = indentOf(line);
            if (indent <= parentIndent) break;
            cursor.next();
            String trimmed = line.trim();
            if (!trimmed.startsWith("OPTION:")) {
                options.add(new Option(null, new ManualNoteRequirement("Malformed option line: " + trimmed)));
                continue;
            }
            String rest = trimmed.substring("OPTION:".length()).trim();
            if (!rest.isEmpty()) {
                options.add(new Option(rest, null));
            } else {
                List<RequirementNode> nested = parseNodes(cursor, indent);
                RequirementNode content = nested.isEmpty() ? null : nested.get(0);
                options.add(new Option(null, content));
            }
        }
        return options;
    }

    private static List<Track> collectTracks(LineCursor cursor, int parentIndent) {
        List<Track> tracks = new ArrayList<>();
        while (cursor.hasNext()) {
            String line = cursor.peek();
            if (line.isBlank()) { cursor.next(); continue; }
            int indent = indentOf(line);
            if (indent <= parentIndent) break;
            cursor.next();
            String trimmed = line.trim();
            String header = trimmed.startsWith("TRACK:") ? trimmed.substring("TRACK:".length()).trim() : trimmed;
            String[] headerParts = header.split("\\s*\\|\\s*school:\\s*", 2);
            String name = stripComment(headerParts[0]);
            List<String> schools = headerParts.length == 2 && !headerParts[1].isBlank()
                    ? Arrays.asList(stripComment(headerParts[1]).split("\\|")) : List.of();
            List<RequirementNode> body = parseNodes(cursor, indent);
            tracks.add(new Track(name, schools, body));
        }
        return tracks;
    }

    // Matches e.g. "DEPT 1000-4999" or "ACC|FIN|MAR 1000-4999 | count: 3"
    private static final Pattern RANGE_PATTERN =
            Pattern.compile("^([A-Za-z|]+)\\s+(\\d+)-(\\d+)(?:\\s*\\|\\s*count:\\s*(\\d+))?$");

    private static RangeRequirement parseRange(String rangeText) {
        Matcher m = RANGE_PATTERN.matcher(rangeText.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Malformed RANGE line: " + rangeText);
        }
        List<String> departments = Arrays.asList(m.group(1).split("\\|"));
        int start = Integer.parseInt(m.group(2));
        int end = Integer.parseInt(m.group(3));
        Integer count = m.group(4) != null ? Integer.parseInt(m.group(4)) : null;
        return new RangeRequirement(departments, start, end, count);
    }

    // ===================== REQUIREMENT NODE TYPES =====================

    /** Marker interface for the five requirement-node shapes described above. */
    public interface RequirementNode {
    }

    public static class CourseListRequirement implements RequirementNode {
        private final String label;
        private final List<String> courseCodes;
        public CourseListRequirement(String label, List<String> courseCodes) {
            this.label = label;
            this.courseCodes = courseCodes;
        }
        public String getLabel() { return label; }
        public List<String> getCourseCodes() { return courseCodes; }
    }

    public static class ChooseNRequirement implements RequirementNode {
        private final int n;
        private final List<Option> options;
        public ChooseNRequirement(int n, List<Option> options) {
            this.n = n;
            this.options = options;
        }
        public int getN() { return n; }
        public List<Option> getOptions() { return options; }
    }

    /** One choice within a ChooseNRequirement - exactly one of courseCode or
     *  nestedContent is non-null. A simple option ("OPTION: DEPT1234") has a
     *  course code; a compound option ("OPTION:" followed by an indented
     *  block) has nested content instead. */
    public static class Option {
        private final String courseCode;
        private final RequirementNode nestedContent;
        public Option(String courseCode, RequirementNode nestedContent) {
            this.courseCode = courseCode;
            this.nestedContent = nestedContent;
        }
        public String getCourseCode() { return courseCode; }
        public RequirementNode getNestedContent() { return nestedContent; }
        public boolean isSimple() { return courseCode != null; }
    }

    public static class RangeRequirement implements RequirementNode {
        private final List<String> departments;
        private final int rangeStart;
        private final int rangeEnd;
        private final Integer count; // null when nested inside an Option - the
        // enclosing CHOOSE_N's count governs instead
        public RangeRequirement(List<String> departments, int rangeStart, int rangeEnd, Integer count) {
            this.departments = departments;
            this.rangeStart = rangeStart;
            this.rangeEnd = rangeEnd;
            this.count = count;
        }
        public List<String> getDepartments() { return departments; }
        public int getRangeStart() { return rangeStart; }
        public int getRangeEnd() { return rangeEnd; }
        public Integer getCount() { return count; }
    }

    public static class TrackChoiceRequirement implements RequirementNode {
        private final String label;
        private final List<Track> tracks;
        public TrackChoiceRequirement(String label, List<Track> tracks) {
            this.label = label;
            this.tracks = tracks;
        }
        public String getLabel() { return label; }
        public List<Track> getTracks() { return tracks; }
    }

    public static class Track {
        private final String name;
        private final List<String> schools;
        private final List<RequirementNode> requirements;
        public Track(String name, List<String> schools, List<RequirementNode> requirements) {
            this.name = name;
            this.schools = schools;
            this.requirements = requirements;
        }
        public String getName() { return name; }
        /** Empty means this track is not automatically limited to a school. */
        public List<String> getSchools() { return schools; }
        public List<RequirementNode> getRequirements() { return requirements; }
    }

    public static class ManualNoteRequirement implements RequirementNode {
        private final String text;
        public ManualNoteRequirement(String text) { this.text = text; }
        public String getText() { return text; }
    }
}
