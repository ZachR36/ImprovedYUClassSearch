import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads the graduation-core profiles in {@code CoreRequirements.txt}.
 *
 * <p>This is deliberately separate from {@link DegreeProgram}: degree-program
 * requirements and core requirements are selected differently, and core
 * requirements include concepts such as attributes, credit totals, and subject
 * distributions that the original major/minor grammar does not need.  The
 * requirement tree is retained exactly enough for a later checker to evaluate
 * it without re-reading or re-parsing the text file.</p>
 */
public class CoreRequirementProgram {

    private static Map<String, CoreRequirementProgram> catalog;

    private final String name;
    private final String school;
    private final String campus;
    private final String degreeType;
    private final String major;
    private final String programType;
    private final List<RequirementNode> requirements;

    private CoreRequirementProgram(String name, String school, String campus, String degreeType,
                                   String major, String programType,
                                   List<RequirementNode> requirements) {
        this.name = name;
        this.school = school;
        this.campus = campus;
        this.degreeType = degreeType;
        this.major = major;
        this.programType = programType;
        this.requirements = List.copyOf(requirements);
    }

    public String getName() { return name; }
    public String getSchool() { return school; }
    public String getCampus() { return campus; }
    public String getDegreeType() { return degreeType; }
    public String getMajor() { return major; }
    public String getProgramType() { return programType; }
    public List<RequirementNode> getRequirements() { return requirements; }
    public String getCatalogKey() { return makeKey(name); }

    /** Loads and stores all core profiles for the running application. */
    public static void setup(String path) throws IOException {
        catalog = loadCatalog(path);
    }

    public static Map<String, CoreRequirementProgram> getCatalog() {
        return catalog;
    }

    /**
     * Finds the profiles whose supplied selectors match.  A missing selector
     * in a profile is intentionally treated as a wildcard; a missing input is
     * not treated as a match for a populated selector.  Selection policy (for
     * example, school-name normalization and Jewish-studies track choice) will
     * be added with the core-progress feature rather than hidden in the loader.
     */
    public static List<CoreRequirementProgram> findMatches(String school, String campus,
                                                            String degreeType, String major) {
        if (catalog == null) {
            return List.of();
        }
        List<CoreRequirementProgram> matches = new ArrayList<>();
        for (CoreRequirementProgram program : catalog.values()) {
            if (matchesSelector(program.school, school)
                    && matchesSelector(program.campus, campus)
                    && matchesSelector(program.degreeType, degreeType)
                    && matchesSelector(program.major, major)) {
                matches.add(program);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    private static boolean matchesSelector(String required, String actual) {
        return required == null || required.isBlank()
                || (actual != null && required.trim().equalsIgnoreCase(actual.trim()));
    }

    /**
     * Selects the one most-specific core profile for a student.  School and
     * campus come from the profile; degree type and major name come from the
     * canonical major-program key rather than from a second student prompt.
     * A Beren-school profile means Stern for core-requirement purposes.
     * Returns null when the profile does not yet contain enough information to
     * select exactly one core profile.
     */
    public static CoreRequirementProgram findForUser(User user) {
        if (catalog == null || user == null || DegreeProgram.getCatalog() == null) {
            return null;
        }
        DegreeProgram majorProgram = DegreeProgram.findByCatalogKey(DegreeProgram.getCatalog(), user.getMajor());
        if (majorProgram == null) {
            return null;
        }
        String school = coreSchoolForUser(user.getSchool());
        String campus = user.getCampus();
        String degreeType = majorProgram.getDegreeType();
        String majorName = withoutDegreeSuffix(majorProgram.getName(), degreeType);

        CoreRequirementProgram selected = null;
        int highestSpecificity = -1;
        boolean tied = false;
        for (CoreRequirementProgram candidate : findMatches(school, campus, degreeType, majorName)) {
            int specificity = selectorCount(candidate);
            if (specificity > highestSpecificity) {
                selected = candidate;
                highestSpecificity = specificity;
                tied = false;
            } else if (specificity == highestSpecificity) {
                tied = true;
            }
        }
        return tied ? null : selected;
    }

    private static int selectorCount(CoreRequirementProgram program) {
        int count = 0;
        if (program.school != null && !program.school.isBlank()) count++;
        if (program.campus != null && !program.campus.isBlank()) count++;
        if (program.degreeType != null && !program.degreeType.isBlank()) count++;
        if (program.major != null && !program.major.isBlank()) count++;
        return count;
    }

    private static String coreSchoolForUser(String userSchool) {
        return userSchool != null && userSchool.trim().equalsIgnoreCase("beren") ? "Stern" : userSchool;
    }

    private static String withoutDegreeSuffix(String name, String degreeType) {
        if (name == null || degreeType == null) {
            return name;
        }
        String suffix = ", " + degreeType;
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    public static Map<String, CoreRequirementProgram> loadCatalog(String path) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(path));
        Map<String, CoreRequirementProgram> result = new LinkedHashMap<>();
        List<String> currentChunk = null;
        for (String line : lines) {
            if (line.startsWith("=== ")) {
                if (currentChunk != null) {
                    addToCatalog(result, parseChunk(currentChunk));
                }
                currentChunk = new ArrayList<>();
            } else if (currentChunk != null) {
                currentChunk.add(line);
            }
        }
        if (currentChunk != null) {
            addToCatalog(result, parseChunk(currentChunk));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No core-requirement profiles were found in " + path);
        }
        return Collections.unmodifiableMap(result);
    }

    private static void addToCatalog(Map<String, CoreRequirementProgram> catalog,
                                     CoreRequirementProgram program) {
        String key = program.getCatalogKey();
        if (catalog.putIfAbsent(key, program) != null) {
            throw new IllegalArgumentException("Duplicate core-requirement profile name: " + program.name);
        }
    }

    public static String makeKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static CoreRequirementProgram parseChunk(List<String> lines) {
        Map<String, String> metadata = new LinkedHashMap<>();
        int bodyStart = 0;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator < 1 || !isMetadataKey(trimmed.substring(0, separator))) {
                bodyStart = i;
                break;
            }
            metadata.put(trimmed.substring(0, separator), trimmed.substring(separator + 1).trim());
            bodyStart = i + 1;
        }
        String name = metadata.get("name");
        String school = metadata.get("school");
        if (name == null || school == null) {
            throw new IllegalArgumentException("Core profile is missing required name or school metadata");
        }
        Cursor cursor = new Cursor(lines, bodyStart);
        return new CoreRequirementProgram(name, school, metadata.get("campus"), metadata.get("degreeType"),
                metadata.get("major"), metadata.get("programType"), parseNodes(cursor, -1));
    }

    private static boolean isMetadataKey(String key) {
        return key.equals("name") || key.equals("school") || key.equals("campus")
                || key.equals("degreeType") || key.equals("major") || key.equals("programType");
    }

    private static List<RequirementNode> parseNodes(Cursor cursor, int parentIndent) {
        List<RequirementNode> nodes = new ArrayList<>();
        while (cursor.hasNext()) {
            String line = cursor.peek();
            if (line.isBlank() || line.trim().startsWith("#")) {
                cursor.next();
                continue;
            }
            int indent = indentOf(line);
            if (indent <= parentIndent) {
                break;
            }
            cursor.next();
            String trimmed = line.trim();
            int separator = trimmed.indexOf(':');
            if (separator < 1) {
                nodes.add(new RequirementNode(NodeType.COURSE_CODE, trimmed, List.of()));
                continue;
            }
            String typeText = trimmed.substring(0, separator);
            String value = stripInlineComment(trimmed.substring(separator + 1));
            NodeType type = NodeType.fromText(typeText);
            List<RequirementNode> children = parseNodes(cursor, indent);
            nodes.add(new RequirementNode(type, value, children));
        }
        return List.copyOf(nodes);
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private static String stripInlineComment(String value) {
        int comment = value.indexOf(" #");
        return (comment < 0 ? value : value.substring(0, comment)).trim();
    }

    private static final class Cursor {
        private final List<String> lines;
        private int position;
        private Cursor(List<String> lines, int position) {
            this.lines = lines;
            this.position = position;
        }
        private boolean hasNext() { return position < lines.size(); }
        private String peek() { return lines.get(position); }
        private void next() { position++; }
    }

    /** The stored shapes in the core-requirement file. */
    public enum NodeType {
        COURSE_LIST, CHOOSE_N, OPTION, RANGE, TRACK_CHOICE, TRACK, MANUAL_NOTE,
        ATTRIBUTE_COUNT, COURSE_COUNT, COURSE_COUNT_FROM_DEPARTMENTS,
        DISTINCT_SUBJECTS, DISTRIBUTION, CREDIT_COUNT, SUBJECT, COURSE_CODE;

        private static NodeType fromText(String text) {
            try {
                return NodeType.valueOf(text.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown core-requirement node type: " + text, e);
            }
        }
    }

    /** One requirement-tree node, with its text after the colon and children. */
    public static final class RequirementNode {
        private final NodeType type;
        private final String value;
        private final List<RequirementNode> children;

        private RequirementNode(NodeType type, String value, List<RequirementNode> children) {
            this.type = type;
            this.value = value;
            this.children = List.copyOf(children);
        }

        public NodeType getType() { return type; }
        public String getValue() { return value; }
        public List<RequirementNode> getChildren() { return children; }
    }
}
